package com.zhanglang.kb.rag.retriever;

import com.zhanglang.kb.rag.config.RetrievalProperties;
import com.zhanglang.kb.rag.entity.ChunkDO;
import com.zhanglang.kb.rag.mapper.ChunkMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 关键词召回器单元测试。
 * <p>
 * 重点锁住三个行为：
 * <ol>
 *     <li>切分规则：ASCII 词按标点断开、中文按二元切分、单字与超短词丢弃</li>
 *     <li>打分约束：得分落在 0~1，未命中为 0</li>
 *     <li>容错：Mapper 抛异常时返回空列表，绝不拖垮问答主链路</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
class KeywordRetrieverTest {

    private final ChunkMapper chunkMapper = Mockito.mock(ChunkMapper.class);

    private final RetrievalProperties properties = new RetrievalProperties();

    private final KeywordRetriever retriever = new KeywordRetriever(chunkMapper, properties);

    @Test
    @DisplayName("ASCII 词按标点断开且转小写：ZK-8823 → zk / 8823")
    void asciiTokenSplitByPunctuation() {
        List<String> keywords = retriever.extractKeywords("ZK-8823 错误码怎么处理");

        assertThat(keywords).contains("zk", "8823");
        // 连字符不是字母数字，不会出现"整个错误码"作为一个词
        assertThat(keywords).doesNotContain("zk-8823");
        // 中文部分按二元切分
        assertThat(keywords).contains("错误", "处理");
    }

    @Test
    @DisplayName("中文二元切分：连续汉字产生全部相邻二字组合")
    void cjkTextProducesBigrams() {
        List<String> keywords = retriever.extractKeywords("试用期多长");

        assertThat(keywords).contains("试用", "用期", "期多", "多长");
    }

    @Test
    @DisplayName("去重：重复词只保留一份")
    void duplicatedKeywordsDeduplicated() {
        List<String> keywords = retriever.extractKeywords("错误错误");

        // 产生 错误/误错/错误，去重后只剩两个
        assertThat(keywords).containsExactly("错误", "误错");
    }

    @Test
    @DisplayName("单字与超短词被丢弃：它们几乎每篇文档都命中，没有区分度")
    void shortTokensDropped() {
        assertThat(retriever.extractKeywords("假")).isEmpty();
        assertThat(retriever.extractKeywords("a b c")).isEmpty();
    }

    @Test
    @DisplayName("关键词数量受 maxKeywords 限制")
    void keywordCountLimited() {
        properties.setMaxKeywords(4);
        List<String> keywords = retriever.extractKeywords("这是一个特别长的中文问题包含了非常多的词语");

        assertThat(keywords).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("空问题返回空关键词")
    void blankQuestionReturnsEmpty() {
        assertThat(retriever.extractKeywords(null)).isEmpty();
        assertThat(retriever.extractKeywords("   ")).isEmpty();
    }

    @Test
    @DisplayName("打分：未命中为 0，命中得分在 0~1 之间且权重生效")
    void scoreStaysWithinUnitRange() {
        ChunkDO hit = chunk(1L, "错误码 ZK-8823 表示考勤同步超时，错误码详见附录");
        ChunkDO miss = chunk(2L, "这段内容与问题毫无关系");
        when(chunkMapper.selectByKeywords(any(), any(), anyInt()))
                .thenReturn(List.of(hit, miss));

        List<Hit> hits = retriever.search(1L, "zk-8823 错误", 5);

        // miss 块得分为 0，被过滤；hit 块命中两个关键词
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocId()).isEqualTo(1L);
        assertThat(hits.get(0).getKeywordScore()).isBetween(0D, 1D);
        // ASCII 词权重（2.0）高于中文二元组（1.0），两者都命中时得分应明显偏高
        assertThat(hits.get(0).getKeywordScore()).isGreaterThan(0.5D);
    }

    @Test
    @DisplayName("多关键词全部命中的块排在部分命中的块前面")
    void fullMatchRanksAbovePartialMatch() {
        ChunkDO full = chunk(1L, "错误码 zk-8823 与 kv-1107 都在本节列出");
        ChunkDO partial = chunk(2L, "错误码 zk-8823 单独出现");
        when(chunkMapper.selectByKeywords(any(), any(), anyInt()))
                .thenReturn(List.of(partial, full));

        List<Hit> hits = retriever.search(1L, "zk-8823 kv-1107", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getDocId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Mapper 抛异常时返回空列表，不向外传播")
    void mapperFailureDegradesToEmpty() {
        when(chunkMapper.selectByKeywords(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(retriever.search(1L, "任何问题", 5)).isEmpty();
    }

    @Test
    @DisplayName("关键词为空时直接短路，不访问数据库")
    void blankQuerySkipsDatabase() {
        List<Hit> hits = retriever.search(1L, "   ", 5);

        assertThat(hits).isEmpty();
        Mockito.verifyNoInteractions(chunkMapper);
    }

    private ChunkDO chunk(Long docId, String content) {
        ChunkDO chunk = new ChunkDO();
        chunk.setKbId(1L);
        chunk.setDocId(docId);
        chunk.setDocName("doc-" + docId + ".txt");
        chunk.setPageNo(1);
        chunk.setChunkNo(1);
        chunk.setContent(content);
        chunk.setIsDeleted(0);
        return chunk;
    }
}
