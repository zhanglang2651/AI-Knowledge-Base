package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 某种检索策略的汇总指标。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@Builder
public class EvalModeReport {

    /** 策略标识 */
    private String mode;

    /** 策略中文名，直接用于前端表头 */
    private String modeName;

    /** 用例总数 */
    private Integer total;

    /** 正样本数量 */
    private Integer positiveTotal;

    /** 负样本数量 */
    private Integer negativeTotal;

    /** 正样本命中率（Recall@K），0~1 */
    private Double recall;

    /**
     * 负样本正确拒绝率，0~1。
     * <p>
     * 越低说明"不该召回却召回了"，即误召回。
     */
    private Double negativePassRate;

    /**
     * 平均倒数排名（MRR），0~1。
     * <p>
     * 命中率只回答"找没找到"，MRR 还能回答"排得靠不靠前"——
     * 排在第 1 和排在第 3 对生成质量的实际影响差别很大。
     */
    private Double mrr;

    /** 综合准确率：正样本命中 且 负样本未误召回 的比例 */
    private Double accuracy;
}
