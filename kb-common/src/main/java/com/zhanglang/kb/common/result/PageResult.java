package com.zhanglang.kb.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体。
 * <p>
 * 列表类接口统一使用该结构，避免每个接口各自定义 total / pages / list 字段名，
 * 导致前端需要写多套解析逻辑。
 *
 * @param <T> 列表元素类型
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码，从 1 开始 */
    private long pageNo;

    /** 每页条数 */
    private long pageSize;

    /** 总记录数 */
    private long total;

    /** 总页数，由 total 与 pageSize 计算得出，避免前端重复计算 */
    private long pages;

    /** 当前页数据。使用空集合而非 null，前端可免去判空 */
    private List<T> records;

    /**
     * 构造分页结果。
     *
     * @param pageNo   当前页码
     * @param pageSize 每页条数
     * @param total    总记录数
     * @param records  当前页数据，允许为 null，内部会转为空集合
     */
    public PageResult(long pageNo, long pageSize, long total, List<T> records) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        // 防御 pageSize 为 0 的情况，否则此处会抛 ArithmeticException
        this.pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        this.records = records == null ? Collections.emptyList() : records;
    }

    /**
     * 构造空分页结果，用于查询无数据时快速返回。
     *
     * @param pageNo   当前页码
     * @param pageSize 每页条数
     * @param <T>      列表元素类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        return new PageResult<>(pageNo, pageSize, 0L, Collections.emptyList());
    }
}
