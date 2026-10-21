package com.zhanglang.kb.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.rag.entity.DocumentDO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import com.zhanglang.kb.rag.manager.DocumentParser;
import com.zhanglang.kb.rag.manager.EmbeddingPipeline;
import com.zhanglang.kb.rag.manager.StorageManager;
import com.zhanglang.kb.rag.mapper.DocumentMapper;
import com.zhanglang.kb.rag.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档解析处理服务。
 * <p>
 * 由异步消费者调用，负责「解析 → 分块 → 向量化」的完整执行与状态流转。
 * <p>
 * <b>本类刻意不加 {@code @Transactional}。</b>
 * 整个流程包含文件 IO 与外部模型调用，耗时可达分钟级，
 * 若包在一个事务里会长时间占用数据库连接，并发几个文档就会把连接池打满。
 * 正确做法是分步提交：每完成一步单独更新状态，中间失败时状态本身就是断点，便于重试。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    /** 解析状态常量，与数据库注释保持一致 */
    private static final String STATUS_PARSING = "PARSING";
    private static final String STATUS_EMBEDDING = "EMBEDDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    /** 最大重试次数，超过后需人工介入（通常是文件损坏或格式不支持） */
    private static final int MAX_RETRY = 3;

    private final DocumentMapper documentMapper;

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final StorageManager storageManager;

    private final DocumentParser documentParser;

    private final EmbeddingPipeline embeddingPipeline;

    /**
     * 处理单个文档。
     *
     * @param docId 文档 ID
     */
    public void process(Long docId) {
        DocumentDO document = documentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getIsDeleted())) {
            log.warn("文档不存在或已删除，跳过处理, docId={}", docId);
            return;
        }
        // 幂等保护：Redis Stream 在消费者崩溃重投时会重复投递同一条消息，
        // 已完成的任务必须跳过，否则会重复写入向量，导致同一片段被召回多次
        if (STATUS_COMPLETED.equals(document.getParseStatus())) {
            log.info("文档已处理完成，跳过, docId={}", docId);
            return;
        }

        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(document.getKbId());
        if (kb == null) {
            updateStatus(document, STATUS_FAILED, "所属知识库已不存在");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            updateStatus(document, STATUS_PARSING, null);

            byte[] content = storageManager.read(document.getFileKey());
            DocumentParser.ParseResult parseResult = documentParser.parse(content, document.getFileName());

            document.setPageCount(parseResult.pageCount());
            updateStatus(document, STATUS_EMBEDDING, null);

            int chunkCount = embeddingPipeline.embed(kb, document, parseResult.text());
            if (chunkCount == 0) {
                throw new IllegalStateException("分块结果为空，文档可能没有有效文本");
            }

            document.setChunkCount(chunkCount);
            updateStatus(document, STATUS_COMPLETED, null);
            // 这里曾调用 increaseKbDocCount，现在改为实时统计，详见类末尾注释

            log.info("文档处理完成, docId={}, chunks={}, 耗时={}ms",
                    docId, chunkCount, System.currentTimeMillis() - startTime);
        } catch (Exception ex) {
            // 失败必须落库：前端要靠 errorMsg 告诉用户"为什么失败"，只说"失败"等于没说
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.error("文档处理失败, docId={}, reason={}", docId, reason, ex);
            markFailed(document, reason);
        }
    }

    /**
     * 更新解析状态。
     */
    private void updateStatus(DocumentDO document, String status, String errorMsg) {
        document.setParseStatus(status);
        document.setErrorMsg(errorMsg);
        documentMapper.updateById(document);
    }

    /**
     * 标记失败并累加重试次数。
     */
    private void markFailed(DocumentDO document, String reason) {
        Integer retry = document.getRetryCount() == null ? 0 : document.getRetryCount();
        document.setRetryCount(retry + 1);
        // 错误信息截断，避免超长堆栈信息把字段撑爆（建表时该字段为 1024）
        String trimmed = reason.length() > 500 ? reason.substring(0, 500) : reason;
        String prefix = retry + 1 >= MAX_RETRY ? "已达最大重试次数：" : "";
        updateStatus(document, STATUS_FAILED, prefix + trimmed);
    }

    /**
     * 知识库文档计数。
     * <p>
     * <b>这里刻意不做任何计数维护。</b>
     * 早期版本在解析成功时 {@code doc_count + 1}，结果失败文档、
     * 解析重试、以及每次重启的「内存向量重建」都会重复累加，
     * 实测 2 个文档被统计成 11 个，且这种漂移不会报错，只会慢慢变得离谱。
     * 现在列表与详情统一走实时 {@code COUNT}，宁可多一次 GROUP BY 也要保证
     * "显示的数字等于真实数字"。
     */
}
