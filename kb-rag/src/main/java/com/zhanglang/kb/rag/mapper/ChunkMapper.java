package com.zhanglang.kb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanglang.kb.rag.entity.ChunkDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分块正文 Mapper。
 * <p>
 * 关键词召回的 SQL 写在注解上而不是 XML：这条查询是本项目唯一一处
 * 需要 LIKE 拼接的语句，放在类里能让人一眼看到"到底查了什么"，
 * 也避免为了一个查询多一个 XML 文件。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Mapper
public interface ChunkMapper extends BaseMapper<ChunkDO> {

    /**
     * 按关键词模糊召回候选分块。
     * <p>
     * 这里只做"粗筛"：用 LIKE 把可能命中的行捞出来，真正的排序打分在应用层完成。
     * 之所以不用数据库全文检索：
     * <ul>
     *     <li>PG 内置分词器对中文支持有限（chinese 需 zhparser，官方 pgvector 镜像不带）</li>
     *     <li>打分规则要随业务调整，放到 Java 里改一行就能生效，不用重建索引</li>
     * </ul>
     * 代价是 {@code %} 前缀的 LIKE 无法走普通 B 树索引；
     * 数据量上万后可换成 {@code pg_trgm} GIN 索引，接口不变。
     *
     * @param kbId    知识库 ID，用于隔离
     * @param keywords 关键词列表（已由应用层切分并去重）
     * @param limit   粗筛上限，避免一次拉回过多数据
     * @return 候选分块列表
     */
    @Select("""
            <script>
            SELECT id, kb_id, doc_id, doc_name, page_no, chunk_no, content, is_deleted, gmt_create, gmt_modified
            FROM kb_chunk
            WHERE kb_id = #{kbId}
              AND is_deleted = 0
              <foreach collection="keywords" item="kw" open="AND (" separator=" OR " close=")">
                  content LIKE CONCAT('%', #{kw}, '%')
              </foreach>
            LIMIT #{limit}
            </script>
            """)
    List<ChunkDO> selectByKeywords(@Param("kbId") Long kbId,
                                   @Param("keywords") List<String> keywords,
                                   @Param("limit") int limit);

    /**
     * 按文档删除分块（逻辑删除之外直接物理删除）。
     * <p>
     * 用物理删除而不是逻辑删除：本表只服务于检索，
     * 保留已删除文档的正文没有审计价值，反而会让关键词召回命中脏数据。
     *
     * @param docId 文档 ID
     * @return 删除条数
     */
    @Delete("DELETE FROM kb_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}
