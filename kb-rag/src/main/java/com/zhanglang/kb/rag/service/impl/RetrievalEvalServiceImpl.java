package com.zhanglang.kb.rag.service.impl;

import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.rag.dto.EvalCaseResult;
import com.zhanglang.kb.rag.dto.EvalModeReport;
import com.zhanglang.kb.rag.dto.RetrievalEvalCase;
import com.zhanglang.kb.rag.dto.RetrievalEvalReport;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import com.zhanglang.kb.rag.mapper.KnowledgeBaseMapper;
import com.zhanglang.kb.rag.retriever.Hit;
import com.zhanglang.kb.rag.retriever.HybridRetriever;
import com.zhanglang.kb.rag.service.RetrievalEvalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索质量评估实现。
 * <p>
 * 核心指标三个，缺一不可：
 * <ul>
 *     <li><b>Recall@K</b>：该找到的找没找到</li>
 *     <li><b>MRR</b>：找到的排得靠不靠前（命中率 100% 但全排最后，等于没优化）</li>
 *     <li><b>负样本拒绝率</b>：不该召回的有没有被召回 —— 知识库问答的幻觉主要来自这里</li>
 * </ul>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEvalServiceImpl implements RetrievalEvalService {

    /** 单条评估策略的检索参数 */
    private record ModeSpec(String name, boolean hybrid, double vectorWeight, double keywordWeight) {
    }

    /**
     * 可评估的策略。权重取当前线上配置的两端做对照：
     * HYBRID 用实际配置，VECTOR / KEYWORD 退化为单路，用来看"两路各贡献了多少"。
     */
    private static final Map<String, ModeSpec> MODE_SPECS = new LinkedHashMap<>(Map.of(
            "HYBRID", new ModeSpec("混合检索", true, 0.6D, 0.4D),
            "VECTOR", new ModeSpec("纯向量", false, 1.0D, 0.0D),
            "KEYWORD", new ModeSpec("纯关键词", true, 0.0D, 1.0D)));

    /** 策略执行顺序固定，避免前端表格列顺序随请求变化 */
    private static final List<String> MODE_ORDER = List.of("HYBRID", "VECTOR", "KEYWORD");

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final HybridRetriever hybridRetriever;

    @Override
    public RetrievalEvalReport evaluate(Long kbId, List<RetrievalEvalCase> cases, List<String> modes) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || Integer.valueOf(1).equals(kb.getIsDeleted())) {
            throw new BizException(ResultCode.KB_NOT_FOUND);
        }

        boolean builtIn = CollectionUtils.isEmpty(cases);
        List<RetrievalEvalCase> evalCases = builtIn ? buildInCases() : cases;
        List<String> modeKeys = resolveModes(modes);

        List<EvalCaseResult> details = new ArrayList<>();
        List<EvalModeReport> summaries = new ArrayList<>();

        for (String mode : modeKeys) {
            ModeSpec spec = MODE_SPECS.get(mode);
            List<EvalCaseResult> modeResults = new ArrayList<>(evalCases.size());
            for (RetrievalEvalCase evalCase : evalCases) {
                modeResults.add(runSingle(kb, mode, spec, evalCase));
            }
            details.addAll(modeResults);
            summaries.add(summarize(mode, spec, modeResults));
        }

        log.info("检索评估完成, kbId={}, cases={}, modes={}", kbId, evalCases.size(), modeKeys);
        return RetrievalEvalReport.builder()
                .kbId(kbId)
                .kbName(kb.getName())
                .topK(kb.getTopK())
                .builtInCases(builtIn)
                .modes(summaries)
                .cases(details)
                .build();
    }

    /**
     * 执行单条用例。
     * <p>
     * 负样本的判定与正样本相反：只要召回了几条，就说明误召回（rank 记 1），
     * 因为用例本就没有期望命中的内容，返回任何片段都算"不该给"。
     */
    private EvalCaseResult runSingle(KnowledgeBaseDO kb, String mode, ModeSpec spec, RetrievalEvalCase evalCase) {
        boolean negative = Boolean.TRUE.equals(evalCase.getExpectMiss());
        List<Hit> hits = hybridRetriever.retrieve(kb, evalCase.getQuestion(),
                spec.hybrid(), spec.vectorWeight(), spec.keywordWeight());

        int rank = 0;
        Hit matched = null;
        if (negative) {
            rank = CollectionUtils.isEmpty(hits) ? 0 : 1;
        } else {
            for (int i = 0; i < hits.size(); i++) {
                if (covers(hits.get(i), evalCase)) {
                    rank = i + 1;
                    matched = hits.get(i);
                    break;
                }
            }
        }

        Hit top = hits.isEmpty() ? null : hits.get(0);
        boolean correct = negative ? rank == 0 : rank > 0;

        return EvalCaseResult.builder()
                .mode(mode)
                .question(evalCase.getQuestion())
                .negative(negative)
                .rank(rank)
                .correct(correct)
                .retrievedCount(hits.size())
                .topDocName(top == null ? null : top.getDocName())
                .topVia(top == null ? null : top.via())
                .topScore(top == null ? null : round(top.displayScore()))
                .note(buildNote(negative, rank, hits, matched))
                .build();
    }

    /**
     * 判断某个片段是否覆盖了用例的期望。
     * <p>
     * 两个条件都配置时是"与"关系：既要在对的文档里，也要含有关键词。
     * 只校验其一会出现"命中了错误文档里的正确关键词"这种假阳性。
     */
    private boolean covers(Hit hit, RetrievalEvalCase evalCase) {
        if (CollectionUtils.isEmpty(evalCase.getExpectKeywords())
                && CollectionUtils.isEmpty(evalCase.getExpectDocNames())) {
            // 没有判定条件的用例只能检查"是否召回了任何内容"
            return true;
        }
        if (!CollectionUtils.isEmpty(evalCase.getExpectDocNames())) {
            String docName = hit.getDocName() == null ? "" : hit.getDocName();
            boolean docMatched = evalCase.getExpectDocNames().stream()
                    .anyMatch(name -> name != null && docName.contains(name));
            if (!docMatched) {
                return false;
            }
        }
        if (!CollectionUtils.isEmpty(evalCase.getExpectKeywords())) {
            String content = hit.getContent() == null ? "" : hit.getContent();
            return evalCase.getExpectKeywords().stream()
                    .allMatch(keyword -> keyword != null && !keyword.isBlank() && content.contains(keyword));
        }
        return true;
    }

    /**
     * 汇总单策略指标。
     */
    private EvalModeReport summarize(String mode, ModeSpec spec, List<EvalCaseResult> results) {
        int positiveTotal = 0;
        int positiveHit = 0;
        double mrrSum = 0D;
        int negativeTotal = 0;
        int negativePass = 0;
        int correctCount = 0;

        for (EvalCaseResult result : results) {
            if (Boolean.TRUE.equals(result.getCorrect())) {
                correctCount++;
            }
            if (Boolean.TRUE.equals(result.getNegative())) {
                negativeTotal++;
                if (Integer.valueOf(0).equals(result.getRank())) {
                    negativePass++;
                }
                continue;
            }
            positiveTotal++;
            int rank = result.getRank() == null ? 0 : result.getRank();
            if (rank > 0) {
                positiveHit++;
                mrrSum += 1D / rank;
            }
        }

        return EvalModeReport.builder()
                .mode(mode)
                .modeName(spec.name())
                .total(results.size())
                .positiveTotal(positiveTotal)
                .negativeTotal(negativeTotal)
                .recall(ratio(positiveHit, positiveTotal))
                .negativePassRate(ratio(negativePass, negativeTotal))
                .mrr(ratio(mrrSum, positiveTotal))
                .accuracy(ratio(correctCount, results.size()))
                .build();
    }

    /**
     * 生成未命中说明，区分"没召回"与"召回了但排名靠后"。
     */
    private String buildNote(boolean negative, int rank, List<Hit> hits, Hit matched) {
        if (negative) {
            if (rank == 0) {
                return "正确：未召回任何片段";
            }
            return "误召回：" + hits.size() + " 条片段被返回";
        }
        if (rank > 0) {
            return "命中于第 " + rank + " 位";
        }
        if (CollectionUtils.isEmpty(hits)) {
            return "未召回任何片段";
        }
        return "召回了 " + hits.size() + " 条，但都不含期望内容";
    }

    /**
     * 解析请求的策略列表，非法值直接忽略并回落到全部策略。
     * <p>
     * 这里刻意不抛异常：评估是辅助工具，一个写错的名字不该让整次评估失败。
     */
    private List<String> resolveModes(List<String> modes) {
        if (CollectionUtils.isEmpty(modes)) {
            return MODE_ORDER;
        }
        List<String> valid = modes.stream()
                .filter(mode -> mode != null && MODE_SPECS.containsKey(mode.toUpperCase()))
                .map(String::toUpperCase)
                .distinct()
                .toList();
        return valid.isEmpty() ? MODE_ORDER : valid;
    }

    /**
     * 内置用例。
     * <p>
     * 前三条对应演示库里的员工手册（test-doc.txt）与运维手册（ops-manual.txt），
     * 最后一条是负样本：库里没有"股票期权"，检索器应当老实承认查不到，
     * 而不是召回一段沾边内容去拼答案。
     * <p>
     * 注意文档名必须与库里真实文件名一致，否则会被判成"召回了错误文档"。
     * 用自己的知识库时把这个列表改掉，或在页面上直接编辑用例。
     */
    private List<RetrievalEvalCase> buildInCases() {
        List<RetrievalEvalCase> cases = new ArrayList<>();
        cases.add(newCase("试用期是多久？", List.of("试用期"), List.of("test-doc.txt")));
        cases.add(newCase("年假有几天？", List.of("年假"), List.of("test-doc.txt")));
        cases.add(newCase("ZK-8823 错误码怎么处理？", List.of("ZK-8823"), List.of("ops-manual.txt")));
        cases.add(newCase("公司股票期权怎么行权？", List.of(), List.of(), Boolean.TRUE));
        return cases;
    }

    private RetrievalEvalCase newCase(String question, List<String> keywords, List<String> docNames) {
        return newCase(question, keywords, docNames, Boolean.FALSE);
    }

    private RetrievalEvalCase newCase(String question, List<String> keywords,
                                      List<String> docNames, Boolean expectMiss) {
        RetrievalEvalCase evalCase = new RetrievalEvalCase();
        evalCase.setQuestion(question);
        evalCase.setExpectKeywords(keywords);
        evalCase.setExpectDocNames(docNames);
        evalCase.setExpectMiss(expectMiss);
        return evalCase;
    }

    /** 安全除法：分母为 0 时返回 0，避免评估结果出现 NaN */
    private double ratio(double numerator, int denominator) {
        return denominator <= 0 ? 0D : Math.round(numerator / denominator * 10000D) / 10000D;
    }

    private double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }
}
