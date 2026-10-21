package com.zhanglang.kb.rag.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调用量按日聚合项，用于趋势图。
 * <p>
 * 字段使用包装类型而非基本类型：JdbcTemplate 映射时聚合结果可能为 null
 * （例如某天没有失败记录，SUM 返回值取决于数据库），用包装类型可避免拆箱空指针。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class StatTrendItemVO {

    /** 日期，格式 yyyy-MM-dd */
    private String statDate;

    /** 当日调用次数 */
    private Long callCount;

    /** 当日 token 总数 */
    private Long totalTokens;

    /** 当日费用（元） */
    private BigDecimal totalCost;

    /** 当日失败次数 */
    private Long failCount;
}
