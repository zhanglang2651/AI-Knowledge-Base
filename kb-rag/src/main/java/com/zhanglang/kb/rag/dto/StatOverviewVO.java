package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调用统计总览。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class StatOverviewVO {

    /** 今日调用次数 */
    private Long todayCalls;

    /** 今日消耗 token 数 */
    private Long todayTokens;

    /** 今日费用（元） */
    private BigDecimal todayCost;

    /** 今日失败次数 */
    private Long todayFailCount;

    /** 今日失败率（百分比，保留一位小数） */
    private BigDecimal todayFailRate;

    /** 今日平均延迟（毫秒） */
    private Long todayAvgLatencyMs;

    /** 累计调用次数 */
    private Long totalCalls;

    /** 累计 token 数 */
    private Long totalTokens;
}
