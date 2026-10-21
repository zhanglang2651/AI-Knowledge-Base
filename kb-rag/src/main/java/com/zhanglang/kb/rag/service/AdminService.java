package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.rag.dto.ModelConfigRequest;
import com.zhanglang.kb.rag.dto.ModelConfigVO;
import com.zhanglang.kb.rag.dto.StatOverviewVO;
import com.zhanglang.kb.rag.dto.StatTrendItemVO;

import java.util.List;

/**
 * 后台管理服务：模型配置 + 调用统计。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public interface AdminService {

    /**
     * 查询模型配置列表。
     *
     * @param modelType 类型过滤（chat / embedding），可为空表示全部
     * @return 配置列表，密钥已脱敏
     */
    List<ModelConfigVO> listModels(String modelType);

    /**
     * 新增模型配置。
     *
     * @param request 配置内容
     * @return 新配置 ID
     */
    Long createModel(ModelConfigRequest request);

    /**
     * 更新模型配置。请求中 apiKey 为空表示不修改原密钥。
     *
     * @param id      配置 ID
     * @param request 配置内容
     */
    void updateModel(Long id, ModelConfigRequest request);

    /**
     * 删除模型配置。
     *
     * @param id 配置 ID
     */
    void deleteModel(Long id);

    /**
     * 连通性测试：用该条配置真实发起一次最小请求。
     *
     * @param id 配置 ID
     * @return 成功返回耗时描述，失败返回错误原因
     */
    String testModel(Long id);

    /**
     * 调用统计总览。
     *
     * @return 今日与累计指标
     */
    StatOverviewVO overview();

    /**
     * 调用趋势（按天）。
     *
     * @param days 统计天数，建议 7 或 30
     * @return 按日期升序的统计列表
     */
    List<StatTrendItemVO> trend(int days);
}
