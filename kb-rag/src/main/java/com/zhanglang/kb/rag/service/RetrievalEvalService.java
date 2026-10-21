package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.rag.dto.RetrievalEvalCase;
import com.zhanglang.kb.rag.dto.RetrievalEvalReport;

import java.util.List;

/**
 * 检索质量评估服务。
 * <p>
 * 只跑检索、<b>不调用大模型</b>：评估的是"找得准不准、排得靠不靠前"，
 * 与生成质量是两件事。混入生成环节会让结果受模型随机性干扰，无法归因。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
public interface RetrievalEvalService {

    /**
     * 对指定知识库执行多策略检索评估。
     *
     * @param kbId  知识库 ID
     * @param cases 评估用例；为空时使用内置用例
     * @param modes 评估策略：HYBRID / VECTOR / KEYWORD；为空时全部执行
     * @return 评估报告
     */
    RetrievalEvalReport evaluate(Long kbId, List<RetrievalEvalCase> cases, List<String> modes);
}
