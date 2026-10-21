package com.zhanglang.kb.rag.retriever;

import com.zhanglang.kb.rag.config.RetrievalProperties;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索器：向量召回 + 关键词召回，用 RRF 融合。
 * <p>
 * <b>为什么用 RRF（Reciprocal Rank Fusion）而不是加权分数相加：</b>
 * 向量相似度（余弦，0~1）与关键词得分（词频归一化）量纲完全不同，
 * 硬要归一化到同一尺度就必须引入一组需要调参的系数，且换个模型就失效。
 * RRF 只用「排名」不用「分数」，天然免疫量纲问题，是目前工业界混合检索的主流做法。
 * <p>
 * 融合公式：{@code score(d) = Σ_i w_i / (k + rank_i(d))}
 * <p>
 * <b>filterExpression 不可省略</b>：向量表全局共享，
 * 缺少 kbId 过滤会跨知识库召回，属于数据越权。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStore vectorStore;

    private final KeywordRetriever keywordRetriever;

    private final RetrievalProperties properties;

    /**
     * 按配置执行混合检索（问答链路使用的入口）。
     *
     * @param kb       知识库，提供 topK 与相似度阈值
     * @param question 用户问题
     * @return 融合排序后的命中列表，长度不超过 kb.topK
     */
    public List<Hit> retrieve(KnowledgeBaseDO kb, String question) {
        List<Hit> hits = retrieve(kb, question,
                properties.isHybridEnabled(), properties.getVectorWeight(), properties.getKeywordWeight());
        log.info("混合检索完成, kbId={}, topK={}", kb.getId(), kb.getTopK());
        return hits;
    }

    /**
     * 指定权重执行检索，供检索质量评估对比不同策略。
     * <p>
     * 把权重做成参数而不是改全局配置，是因为评估要在一轮里跑「纯向量 / 纯关键词 / 混合」
     * 三种策略，如果靠改配置切换就只能一次跑一种，还需要重启或并发隔离。
     *
     * @param hybridEnabled 是否启用关键词分支
     * @param vectorWeight  向量权重，传 0 等价于纯关键词
     * @param keywordWeight 关键词权重，传 0 等价于纯向量
     */
    public List<Hit> retrieve(KnowledgeBaseDO kb, String question,
                              boolean hybridEnabled, double vectorWeight, double keywordWeight) {
        int topK = kb.getTopK() == null ? 5 : kb.getTopK();
        // 融合前必须多召回：两路结果重叠度通常只有一半左右，
        // 只各取 topK 个的话，融合后凑不满 topK 条有效内容
        int recallSize = topK * Math.max(1, properties.getRecallFactor());

        List<Hit> vectorHits = searchVector(kb, question, recallSize);
        if (!hybridEnabled) {
            return vectorHits.stream().limit(topK).toList();
        }

        List<Hit> keywordHits = keywordWeight > 0
                ? keywordRetriever.search(kb.getId(), question, recallSize)
                : List.of();

        List<Hit> fused = fuse(vectorHits, keywordHits, topK, vectorWeight, keywordWeight);
        log.debug("检索策略执行, kbId={}, vectorW={}, keywordW={}, vector={}, keyword={}, fused={}",
                kb.getId(), vectorWeight, keywordWeight, vectorHits.size(), keywordHits.size(), fused.size());
        return fused;
    }

    /**
     * 向量召回。
     * <p>
     * 失败时返回空列表而不是抛异常：检索异常不应该让整个问答 500，
     * 降级为"无上下文"让模型给出明确兜底回复，用户体验好于一个报错弹窗。
     */
    private List<Hit> searchVector(KnowledgeBaseDO kb, String question, int recallSize) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(recallSize)
                .similarityThreshold(kb.getSimilarityThreshold() == null
                        ? 0.5D : kb.getSimilarityThreshold().doubleValue())
                .filterExpression(new FilterExpressionBuilder().eq("kbId", kb.getId()).build())
                .build();

        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(request);
        } catch (Exception ex) {
            log.error("向量检索失败, kbId={}", kb.getId(), ex);
            return List.of();
        }
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Hit> hits = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            Long docId = asLong(metadata.get("docId"));
            Integer chunkNo = asInteger(metadata.get("chunkNo"));
            hits.add(Hit.builder()
                    .chunkKey(docId + ":" + chunkNo)
                    .docId(docId)
                    .docName(asString(metadata.get("docName")))
                    .pageNo(asInteger(metadata.get("pageNo")))
                    .chunkNo(chunkNo)
                    .content(doc.getText())
                    .vectorScore(doc.getScore() == null ? 0D : doc.getScore())
                    .build());
        }
        return hits;
    }

    /**
     * RRF 融合。
     * <p>
     * 注意融合顺序很关键：先写向量分再叠加关键词分，
     * 同一片段被两路命中时分数会显著高于单路命中，这正是我们想要的"双重确认"。
     * <p>
     * 权重为 0 的分支直接不参与 —— 否则它那路的命中会以 0 分挤占 topK 名额，
     * "纯关键词"模式下反而混进一堆向量片段。
     */
    private List<Hit> fuse(List<Hit> vectorHits, List<Hit> keywordHits, int topK,
                           double vectorWeight, double keywordWeight) {
        int k = properties.getRrfK();
        Map<String, Hit> merged = new LinkedHashMap<>();

        if (vectorWeight > 0) {
            for (int i = 0; i < vectorHits.size(); i++) {
                Hit hit = vectorHits.get(i);
                // 排名从 1 开始：(k + rank) 的分母不能是 0
                hit.setFusedScore(vectorWeight / (k + i + 1));
                merged.put(hit.getChunkKey(), hit);
            }
        }

        if (keywordWeight > 0) {
            for (int i = 0; i < keywordHits.size(); i++) {
                Hit keywordHit = keywordHits.get(i);
                double contribution = keywordWeight / (k + i + 1);
                Hit existed = merged.get(keywordHit.getChunkKey());
                if (existed != null) {
                    existed.setKeywordScore(keywordHit.getKeywordScore());
                    existed.setFusedScore(existed.getFusedScore() + contribution);
                    continue;
                }
                // 纯关键词命中要过门槛：中文二元切分召回偏宽，
                // 没有阈值会把"提到但根本没回答问题"的片段塞进上下文，反而干扰模型
                if (keywordHit.getKeywordScore() < properties.getMinKeywordScore()) {
                    continue;
                }
                keywordHit.setFusedScore(contribution);
                merged.put(keywordHit.getChunkKey(), keywordHit);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(Hit::getFusedScore).reversed())
                .limit(topK)
                .toList();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "未知文档" : value.toString();
    }
}
