package com.zhanglang.kb.rag.retriever;

import com.zhanglang.kb.rag.config.RetrievalProperties;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 混合检索器（RRF 融合）单元测试。
 * <p>
 * RRF 的排序规则是本项目的核心逻辑，也是最容易"看起来对、结果悄悄错"的部分。
 * 这里用可手工推导的固定排名验证五个行为：
 * <ol>
 *     <li>双路命中的片段排在任何单路命中之前</li>
 *     <li>纯关键词命中必须过准入门槛，否则被丢弃</li>
 *     <li>hybrid 关闭时退化为纯向量</li>
 *     <li>权重为 0 的分支不参与融合（否则会以 0 分挤占 topK 名额）</li>
 *     <li>向量检索异常时降级为关键词结果，而不是抛异常</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
class HybridRetrieverTest {

    private VectorStore vectorStore;

    private KeywordRetriever keywordRetriever;

    private RetrievalProperties properties;

    private HybridRetriever retriever;

    private KnowledgeBaseDO kb;

    @BeforeEach
    void setUp() {
        vectorStore = Mockito.mock(VectorStore.class);
        keywordRetriever = Mockito.mock(KeywordRetriever.class);
        properties = new RetrievalProperties();
        retriever = new HybridRetriever(vectorStore, keywordRetriever, properties);

        kb = new KnowledgeBaseDO();
        kb.setId(1L);
        kb.setTopK(3);
        kb.setSimilarityThreshold(new java.math.BigDecimal("0.40"));
    }

    @Test
    @DisplayName("双路命中排第一：融合分 = 向量贡献 + 关键词贡献")
    void bothBranchHitRanksFirst() {
        // 向量召回顺序：A 第 1、B 第 2
        mockVectorReturns(doc(1L, 1), doc(2L, 1));
        // 关键词召回顺序：B 第 1、C 第 2（C 不会被向量召回）
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(
                        keywordHit(2L, 1, 0.8D),
                        keywordHit(3L, 1, 0.9D)));

        List<Hit> hits = retriever.retrieve(kb, "问题");

        // B 双路命中（0.6/61 + 0.4/61），必然高于单路命中的 A（0.6/62）与 C（0.4/62）
        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).getChunkKey()).isEqualTo("2:1");
        assertThat(hits.get(0).via()).isEqualTo("BOTH");
        assertThat(hits.get(1).getChunkKey()).isEqualTo("1:1");
        assertThat(hits.get(2).getChunkKey()).isEqualTo("3:1");
    }

    @Test
    @DisplayName("纯关键词命中低于准入门槛时被丢弃")
    void lowScoreKeywordOnlyHitDropped() {
        mockVectorReturns(doc(1L, 1));
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(keywordHit(9L, 1, 0.1D)));

        List<Hit> hits = retriever.retrieve(kb, "问题");

        // 0.1 < min-keyword-score(0.25)，只剩向量命中的 A
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkKey()).isEqualTo("1:1");
        assertThat(hits.get(0).via()).isEqualTo("VECTOR");
    }

    @Test
    @DisplayName("hybrid 关闭时退化为纯向量，完全不访问关键词分支")
    void hybridDisabledFallsBackToVectorOnly() {
        properties.setHybridEnabled(false);
        mockVectorReturns(doc(1L, 1), doc(2L, 1), doc(3L, 1), doc(4L, 1));

        List<Hit> hits = retriever.retrieve(kb, "问题");

        assertThat(hits).hasSize(3);
        assertThat(hits).allSatisfy(hit -> assertThat(hit.via()).isEqualTo("VECTOR"));
        Mockito.verifyNoInteractions(keywordRetriever);
    }

    @Test
    @DisplayName("向量权重为 0 时等价纯关键词：向量命中不挤占 topK 名额")
    void zeroVectorWeightMeansPureKeyword() {
        mockVectorReturns(doc(1L, 1), doc(2L, 1), doc(3L, 1));
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(keywordHit(8L, 1, 0.7D)));

        List<Hit> hits = retriever.retrieve(kb, "问题", true, 0.0D, 1.0D);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkKey()).isEqualTo("8:1");
        assertThat(hits.get(0).via()).isEqualTo("KEYWORD");
    }

    @Test
    @DisplayName("融合结果不超过 topK")
    void fusedResultLimitedByTopK() {
        mockVectorReturns(doc(1L, 1), doc(2L, 1), doc(3L, 1), doc(4L, 1), doc(5L, 1));
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(keywordHit(8L, 1, 0.9D), keywordHit(9L, 1, 0.8D)));

        List<Hit> hits = retriever.retrieve(kb, "问题");

        assertThat(hits).hasSize(3);
    }

    @Test
    @DisplayName("向量检索抛异常时降级为关键词结果，不向外抛异常")
    void vectorFailureDegradesToKeyword() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("vector store down"));
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(keywordHit(8L, 1, 0.7D)));

        List<Hit> hits = retriever.retrieve(kb, "问题");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkKey()).isEqualTo("8:1");
    }

    @Test
    @DisplayName("展示分数：向量命中展示相似度，纯关键词命中展示关键词得分")
    void displayScorePrefersVectorScore() {
        mockVectorReturns(doc(1L, 1));
        when(keywordRetriever.search(any(), any(), anyInt()))
                .thenReturn(List.of(keywordHit(8L, 1, 0.7D)));

        List<Hit> hits = retriever.retrieve(kb, "问题", false, 1.0D, 0.0D);
        assertThat(hits.get(0).displayScore()).isZero();

        List<Hit> keywordOnly = retriever.retrieve(kb, "问题", true, 0.0D, 1.0D);
        assertThat(keywordOnly.get(0).displayScore()).isEqualTo(0.7D);
    }

    /* ---------------------- 辅助方法 ---------------------- */

    private void mockVectorReturns(Document... documents) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(documents));
    }

    /** 构造向量库返回的文档，metadata 与入库时 EmbeddingPipeline 写入的结构一致 */
    private Document doc(Long docId, int chunkNo) {
        Map<String, Object> metadata = new HashMap<>(8);
        metadata.put("kbId", 1L);
        metadata.put("docId", docId);
        metadata.put("docName", "doc-" + docId + ".txt");
        metadata.put("chunkNo", chunkNo);
        metadata.put("pageNo", 1);
        return Document.builder()
                .text("doc" + docId + " 的片段内容")
                .metadata(metadata)
                .build();
    }

    private Hit keywordHit(Long docId, int chunkNo, double score) {
        return Hit.builder()
                .chunkKey(docId + ":" + chunkNo)
                .docId(docId)
                .docName("doc-" + docId + ".txt")
                .pageNo(1)
                .chunkNo(chunkNo)
                .content("doc" + docId + " 的片段内容")
                .keywordScore(score)
                .build();
    }
}
