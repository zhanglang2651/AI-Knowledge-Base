package com.zhanglang.kb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanglang.kb.rag.dto.KbDocCount;
import com.zhanglang.kb.rag.entity.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档数据访问接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentDO> {

    /**
     * 批量统计各知识库的可用文档数。
     * <p>
     * <b>为什么不缓存计数到 kb_knowledge_base.doc_count 字段：</b>
     * 计数一旦靠"解析成功就 +1"维护，就必然会在重试、启动自愈重建向量、
     * 并发上传等场景下漂移（本项目实测调参重启几次后，2 个文档被统计成 11 个）。
     * 实时 COUNT 的代价是一次 GROUP BY，换来的是"显示的永远等于真实的"。
     * 文档量到十万级再考虑冗余字段 + 定时校准。
     *
     * @param kbIds 知识库 ID 列表
     * @return 每个知识库对应的可用文档数
     */
    @Select("""
            <script>
            SELECT kb_id AS kbId, COUNT(1) AS count
            FROM kb_document
            WHERE is_deleted = 0
              AND parse_status = 'COMPLETED'
              AND kb_id IN
              <foreach collection="kbIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY kb_id
            </script>
            """)
    List<KbDocCount> countCompletedByKbIds(@Param("kbIds") List<Long> kbIds);
}
