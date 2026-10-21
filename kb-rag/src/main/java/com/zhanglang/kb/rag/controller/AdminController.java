package com.zhanglang.kb.rag.controller;

import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.rag.dto.ModelConfigRequest;
import com.zhanglang.kb.rag.dto.ModelConfigVO;
import com.zhanglang.kb.rag.dto.RetrievalEvalCase;
import com.zhanglang.kb.rag.dto.RetrievalEvalReport;
import com.zhanglang.kb.rag.dto.StatOverviewVO;
import com.zhanglang.kb.rag.dto.StatTrendItemVO;
import com.zhanglang.kb.rag.service.AdminService;
import com.zhanglang.kb.rag.service.RetrievalEvalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台管理接口。
 * <p>
 * 整个 {@code /api/v1/admin/**} 前缀已在 {@code JwtAuthInterceptor} 中做了角色校验，
 * 非 ADMIN 角色返回 403。这里不再重复判断，避免权限逻辑散落在两处。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    private final RetrievalEvalService retrievalEvalService;

    /**
     * 查询模型配置列表。
     *
     * @param modelType 类型过滤（chat / embedding），可为空
     * @return 配置列表，密钥脱敏
     */
    @GetMapping("/models")
    public Result<List<ModelConfigVO>> listModels(@RequestParam(required = false) String modelType) {
        return Result.success(adminService.listModels(modelType));
    }

    /**
     * 新增模型配置。
     *
     * @param request 配置内容
     * @return 新配置 ID
     */
    @PostMapping("/models")
    public Result<Long> createModel(@Valid @RequestBody ModelConfigRequest request) {
        return Result.success(adminService.createModel(request));
    }

    /**
     * 更新模型配置。
     *
     * @param id      配置 ID
     * @param request 配置内容（apiKey 留空表示不修改）
     * @return 空响应
     */
    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id,
                                    @Valid @RequestBody ModelConfigRequest request) {
        adminService.updateModel(id, request);
        return Result.success();
    }

    /**
     * 删除模型配置。
     *
     * @param id 配置 ID
     * @return 空响应
     */
    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        adminService.deleteModel(id);
        return Result.success();
    }

    /**
     * 连通性测试。
     *
     * @param id 配置 ID
     * @return 测试结果描述
     */
    @PostMapping("/models/{id}/test")
    public Result<String> testModel(@PathVariable Long id) {
        return Result.success(adminService.testModel(id));
    }

    /**
     * 调用统计总览。
     *
     * @return 今日与累计指标
     */
    @GetMapping("/stat/overview")
    public Result<StatOverviewVO> overview() {
        return Result.success(adminService.overview());
    }

    /**
     * 调用趋势。
     *
     * @param days 统计天数，默认 7，最大 90
     * @return 按日期升序的统计列表
     */
    @GetMapping("/stat/trend")
    public Result<List<StatTrendItemVO>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminService.trend(days));
    }

    /**
     * 检索质量评估。
     * <p>
     * 只执行检索、不调用模型，因此不会产生 token 成本，可以随意反复运行。
     * 一次调用会横向对比多种策略（混合 / 纯向量 / 纯关键词），
     * 用数据回答"融合权重该怎么调"，而不是靠手感。
     *
     * @param kbId  知识库 ID
     * @param cases 评估用例；不传则使用内置用例
     * @param modes 评估策略：HYBRID / VECTOR / KEYWORD；不传则全部执行
     * @return 评估报告
     */
    @PostMapping("/eval/retrieval")
    public Result<RetrievalEvalReport> evaluateRetrieval(@RequestParam Long kbId,
                                                         @RequestBody(required = false) List<RetrievalEvalCase> cases,
                                                         @RequestParam(required = false) List<String> modes) {
        return Result.success(retrievalEvalService.evaluate(kbId, cases, modes));
    }
}
