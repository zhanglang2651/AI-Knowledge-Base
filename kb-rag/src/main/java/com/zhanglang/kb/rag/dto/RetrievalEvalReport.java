package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 检索质量评估报告。
 * <p>
 * 一次调用会对同一个知识库跑多种检索策略并横向对比，
 * 目的是回答一个具体问题：<b>"混合检索到底比纯向量好在哪、好多少"</b>。
 * 没有这组数据，权重就只能靠手感调，答辩时也说不清取舍依据。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@Builder
public class RetrievalEvalReport {

    private Long kbId;

    private String kbName;

    /** 本次评估使用的 topK */
    private Integer topK;

    /** 是否使用了内置用例（未传 cases 时为 true） */
    private Boolean builtInCases;

    /** 各策略汇总 */
    private List<EvalModeReport> modes;

    /** 逐条用例明细，按策略 × 用例展开 */
    private List<EvalCaseResult> cases;
}
