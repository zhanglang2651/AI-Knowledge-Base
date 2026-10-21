package com.zhanglang.kb.rag.manager;

import com.zhanglang.kb.rag.entity.DocumentDO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化管道。
 * <p>
 * 负责把「清洗后的全文」变成「向量库里的可检索片段」，是入库链路的最后一环。
 * <p>
 * <b>本类最关键的设计：metadata 必须写全。</b>
 * 引用来源展示所需的 docId / docName / pageNo / chunkNo 全部在此时写入向量库。
 * 若等到检索阶段再回查数据库补齐，就需要额外一次 DB 往返，
 * 而且一旦文档被删除或改名，历史回答的引用会错乱 —— 无法补救，只能全量重建向量。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingPipeline {

    /** 单批写入条数。批量太小会让向量化耗时成倍增加，太大则可能触发接口限制 */
    private static final int BATCH_SIZE = 32;

    /** 每页估算字符数，用于把字符偏移换算为大致页码 */
    private static final int CHARS_PER_PAGE = 800;

    private final VectorStore vectorStore;

    private final ChunkSplitter chunkSplitter;

    /**
     * 用于按 metadata 条件直接删除向量。
     * Spring AI 的 VectorStore 只提供按 id 删除，拿不到"按条件删除"的能力，
     * 而按条件查询 id 又依赖向量检索（空 query 无法召回全量），
     * 因此这里直接走 JDBC 操作 vector_store 表，语义最准确。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行分块与向量化。
     *
     * @param kb       所属知识库，提供切分参数
     * @param document 文档实体，提供元数据
     * @param text     清洗后的全文
     * @return 实际写入的块数
     */
    public int embed(KnowledgeBaseDO kb, DocumentDO document, String text) {
        List<ChunkSplitter.TextChunk> chunks = chunkSplitter.split(
                text,
                kb.getChunkSize() == null ? 600 : kb.getChunkSize(),
                kb.getChunkOverlap() == null ? 80 : kb.getChunkOverlap()
        );

        if (chunks.isEmpty()) {
            log.warn("分块结果为空, docId={}", document.getId());
            return 0;
        }

        // 先清掉该文档的全部旧痕迹（向量 + 分块正文）：重试解析会在同一 docId 上重新写入，
        // 不清理会让检索召回上一轮的旧片段，答案与引用对不上
        deleteVectorsByDocId(document.getId());
        deleteChunkRows(document.getId());

        int total = 0;
        List<Document> batch = new ArrayList<>(BATCH_SIZE);
        for (ChunkSplitter.TextChunk chunk : chunks) {
            batch.add(toVectorDocument(kb, document, chunk));
            if (batch.size() >= BATCH_SIZE) {
                vectorStore.add(batch);
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
            total += batch.size();
        }

        // 关键词召回依赖的正文副本。与向量表一一对应，是混合检索的另一半
        saveChunkRows(kb, document, chunks);

        log.info("向量化完成, docId={}, chunkCount={}", document.getId(), total);
        return total;
    }

    /**
     * 批量写入分块正文。
     * <p>
     * 用 JdbcTemplate 批量提交而不是循环调用 Mapper：
     * 一份百页文档会产生上千个分块，逐条 insert 会在网络往返上浪费大量时间。
     */
    private void saveChunkRows(KnowledgeBaseDO kb, DocumentDO document, List<ChunkSplitter.TextChunk> chunks) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(
                "INSERT INTO kb_chunk (kb_id, doc_id, doc_name, page_no, chunk_no, content, is_deleted, gmt_create, gmt_modified) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ChunkSplitter.TextChunk chunk = chunks.get(i);
                        ps.setLong(1, kb.getId());
                        ps.setLong(2, document.getId());
                        ps.setString(3, document.getFileName());
                        ps.setInt(4, resolvePageNo(chunk.charStart()));
                        ps.setInt(5, chunk.chunkNo());
                        ps.setString(6, chunk.content());
                        ps.setTimestamp(7, now);
                        ps.setTimestamp(8, now);
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
        log.info("分块正文已落库, docId={}, chunkCount={}", document.getId(), chunks.size());
    }

    /**
     * 清理某文档的分块正文。
     * <p>
     * 与向量清理同样"失败不影响主流程"：正文副本缺失只是让关键词分支少召回，
     * 不应该导致整个文档解析被判为失败。
     */
    private void deleteChunkRows(Long docId) {
        try {
            jdbcTemplate.update("DELETE FROM kb_chunk WHERE doc_id = ?", docId);
        } catch (Exception ex) {
            log.warn("清理历史分块失败，已跳过, docId={}, reason={}", docId, ex.getMessage());
        }
    }

    /**
     * 删除某文档的全部向量。
     * <p>
     * 删除文档时必须调用本方法。若不清理，已删除文档的片段仍会被检索命中，
     * 用户会看到答案引用了"已经不存在的文件"——这不是假设，本项目 h2 模式下
     * 曾因此让用户删掉的文档继续出现在问答引用里。
     * <p>
     * <b>关键前提：向量必须使用确定性 ID（docId:chunkNo），见 {@link #toVectorDocument}。</b>
     * Spring AI 默认给向量生成随机 UUID，删除时根本无法反查"这个文档有哪些向量"；
     * 指定 ID 后，删除就是一次按 ID 列表的精确操作，内存向量库与 pgvector 通吃。
     *
     * @param docId 文档 ID
     * @return 删除的向量条数
     */
    public int deleteByDocId(Long docId) {
        int deleted = deleteVectorsByDocId(docId);
        // 分块正文与向量必须一起删：只删向量会让关键词分支继续召回已删除文档
        deleteChunkRows(docId);
        return deleted;
    }

    /**
     * 删除某文档的全部向量，三步走，每步失败都降级不抛异常：
     * <ol>
     *     <li>从 kb_chunk 反查该文档的 chunkNo 列表（必须在删正文之前查）</li>
     *     <li>拼出确定性 ID 列表，调用 VectorStore.delete 精确删除</li>
     *     <li>JDBC 按 metadata 兜底：覆盖"存量数据 ID 是随机 UUID"的场景
     *         （这类行的 ID 不是 docId:chunkNo，按 ID 删不到）</li>
     * </ol>
     *
     * @param docId 文档 ID
     * @return 删除的向量条数
     */
    private int deleteVectorsByDocId(Long docId) {
        // 第一步：从正文副本反查块号，顺序不能反——正文删了就查不到了
        List<Integer> chunkNos;
        try {
            chunkNos = jdbcTemplate.queryForList(
                    "SELECT chunk_no FROM kb_chunk WHERE doc_id = ?", Integer.class, docId);
        } catch (Exception ex) {
            log.debug("反查分块块号失败，跳过按 ID 删除, docId={}, reason={}", docId, ex.getMessage());
            chunkNos = List.of();
        }

        // 第二步：确定性 ID 精确删除（内存向量库与 pgvector 都实现了 delete 接口）
        if (!chunkNos.isEmpty()) {
            List<String> vectorIds = chunkNos.stream()
                    .map(chunkNo -> docId + ":" + chunkNo)
                    .toList();
            try {
                vectorStore.delete(vectorIds);
                log.info("已按 ID 清理文档向量, docId={}, count={}", docId, vectorIds.size());
                return vectorIds.size();
            } catch (Exception ex) {
                log.debug("按 ID 删除向量失败，转 JDBC 兜底, docId={}, reason={}", docId, ex.getMessage());
            }
        }

        // 第三步：JDBC 兜底。metadata 是 jsonb 类型，用 ->> 取出文本再与字符串比较，
        // 注意必须传 String：jsonb 里的数字与 SQL 整型直接比较会匹配失败
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'docId' = ?",
                    String.valueOf(docId));
            if (deleted > 0) {
                log.info("已按 metadata 清理文档向量, docId={}, count={}", docId, deleted);
            }
            return deleted;
        } catch (Exception ex) {
            // 走到兜底还失败，几乎只有两种情况：h2 内存模式下不存在 vector_store 表（首次上传时必然发生，无害），
            // 或 pgvector 下 metadata 结构异常。按 ID 删除成功时根本不会走到这里，因此降为 debug 避免刷日志
            log.debug("JDBC 兜底清理向量失败（多为 h2 模式无 vector_store 表，无害）, docId={}, reason={}",
                    docId, ex.getMessage());
            return 0;
        }
    }

    /**
     * 把文本块转换为带元数据的向量文档。
     * <p>
     * <b>ID 是刻意指定的确定性值（docId:chunkNo），不是框架默认的随机 UUID。</b>
     * 随机 ID 意味着"删除文档"时无法反查该删哪些向量——这正是本项目
     * 曾经出现"删掉的文档还在被引用"的根因。确定性 ID 让删除变成一次精确操作，
     * 顺带让重试解析天然幂等（同 ID 覆盖旧向量，不会产生双份）。
     */
    private Document toVectorDocument(KnowledgeBaseDO kb,
                                      DocumentDO document,
                                      ChunkSplitter.TextChunk chunk) {
        Map<String, Object> metadata = new HashMap<>(8);
        // kbId 用于检索时做知识库隔离，是防止跨库越权召回的关键字段
        metadata.put("kbId", kb.getId());
        metadata.put("docId", document.getId());
        metadata.put("docName", document.getFileName());
        metadata.put("chunkNo", chunk.chunkNo());
        metadata.put("pageNo", resolvePageNo(chunk.charStart()));
        metadata.put("charStart", chunk.charStart());
        metadata.put("charEnd", chunk.charEnd());
        return Document.builder()
                .id(document.getId() + ":" + chunk.chunkNo())
                .text(chunk.content())
                .metadata(metadata)
                .build();
    }

    /**
     * 由字符偏移估算页码。用于引用定位，不要求精确。
     */
    private int resolvePageNo(int charStart) {
        return charStart / CHARS_PER_PAGE + 1;
    }
}
