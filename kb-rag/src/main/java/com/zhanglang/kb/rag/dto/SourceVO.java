package com.zhanglang.kb.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用来源。
 * <p>
 * 这是「可溯源」的载体：前端按 docId 关联文档，按 pageNo / chunkNo 定位原文片段，
 * 按 score 展示匹配度，用户能一眼判断答案是否可信。
 * <p>
 * 所有字段都来自入库时写入向量库的 metadata，检索阶段不再回查数据库，
 * 因此这些元数据在入库时必须写全 —— 漏写就无法补救（只能全量重建向量）。
 * <p>
 * <b>必须有 {@code @NoArgsConstructor}</b>：该对象要能序列化进 sources_json 再从历史消息里反序列化回来。
 * Jackson 默认需要无参构造 + setter 才能还原对象，只加 {@code @Builder} 会让反序列化直接抛异常，
 * 而异常被 catch 后表现为「历史消息引用列表为空」，curl 看接口一切正常，极难定位。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceVO {

    /** 文档 ID */
    private Long docId;

    /** 文档名，用于前端展示 */
    private String docName;

    /** 页码，PDF/Word 有效，纯文本为 null */
    private Integer pageNo;

    /** 块序号，在同一文档内从 1 开始 */
    private Integer chunkNo;

    /** 相似度分数，0-1，越高越相关 */
    private Double score;

    /** 命中片段原文，前端可折叠展示 */
    private String content;

    /**
     * 命中来源：VECTOR / KEYWORD / BOTH。
     * <p>
     * 混合检索后有必要让用户看见：一条靠语义"顺带"召回，一条是关键词精确命中，
     * 两者的可信度并不相同。老数据没有该字段（为 null），前端按未知处理。
     */
    private String via;
}
