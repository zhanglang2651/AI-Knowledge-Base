package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 单条用例在某种检索策略下的评估结果。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@Builder
public class EvalCaseResult {

    /** 评估策略：HYBRID / VECTOR / KEYWORD */
    private String mode;

    private String question;

    /** 是否为负样本 */
    private Boolean negative;

    /** 期望命中片段的实际排名，从 1 开始；0 表示未命中 */
    private Integer rank;

    /** 是否判定为正确（正样本命中 / 负样本未命中） */
    private Boolean correct;

    /** 本次召回了多少片段 */
    private Integer retrievedCount;

    /** 首位片段的文档名，用于快速判断"召回了但召回了错文档" */
    private String topDocName;

    /** 首位片段的来源：VECTOR / KEYWORD / BOTH */
    private String topVia;

    /** 首位片段的展示分数 */
    private Double topScore;

    /** 未命中时的原因，便于定位是"检索没召回"还是"召回了但排序靠后" */
    private String note;
}
