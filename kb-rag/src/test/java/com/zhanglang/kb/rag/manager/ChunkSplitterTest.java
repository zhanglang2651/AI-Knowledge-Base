package com.zhanglang.kb.rag.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块器单元测试。
 * <p>
 * 分块是检索质量的地基：切坏了，后面的向量化和 RRF 调得再好也救不回来。
 * 这里重点锁住四个不变量：
 * <ol>
 *     <li>块号从 1 连续递增（引用溯源依赖它）</li>
 *     <li>任何块的长度不超过 chunkSize</li>
 *     <li>语义边界存在时，标点归属前一块</li>
 *     <li>非法参数（overlap ≥ chunkSize）不产生死循环</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    @DisplayName("空文本返回空列表，不抛异常")
    void nullAndBlankReturnEmpty() {
        assertThat(splitter.split(null, 500, 50)).isEmpty();
        assertThat(splitter.split("   \n  ", 500, 50)).isEmpty();
    }

    @Test
    @DisplayName("短文本只切一块，块号从 1 开始")
    void shortTextSingleChunk() {
        String text = "这是一段不需要切分的短文本。";

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 500, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkNo()).isEqualTo(1);
        assertThat(chunks.get(0).content()).isEqualTo(text.strip());
    }

    @Test
    @DisplayName("无语义边界时硬切：块长不超过 chunkSize，相邻块起始偏移单调递增")
    void longTextWithoutBoundaryHardCut() {
        // 1000 个无标点字符：找不到任何边界，只能按窗口硬切
        String text = "字".repeat(1000);

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 200, 0);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(200));

        for (int i = 0; i < chunks.size(); i++) {
            ChunkSplitter.TextChunk chunk = chunks.get(i);
            assertThat(chunk.chunkNo()).isEqualTo(i + 1);
            if (i > 0) {
                assertThat(chunk.charStart())
                        .as("第 %d 块的起始偏移必须大于前一 块", i + 1)
                        .isGreaterThanOrEqualTo(chunks.get(i - 1).charEnd());
            }
        }
    }

    @Test
    @DisplayName("存在句号边界时，切点落在句号之后，标点归属前一块")
    void boundaryCutKeepsPunctuationWithSentence() {
        // 每句 6 个字符（含句号），共 30 句 = 180 字符
        String sentence = "第一句话。";
        String text = sentence.repeat(30);

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 100, 0);

        assertThat(chunks.size()).isGreaterThan(1);
        // 除最后一块外，每块都应以句号结尾（标点留在前一块，句子才完整）
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).content())
                    .as("第 %d 块应以句号结尾", i + 1)
                    .endsWith("。");
        }
    }

    @Test
    @DisplayName("overlap 生效：相邻块在原文上有重叠区间")
    void overlapCreatesSharedRegion() {
        String text = "字".repeat(1000);

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 200, 50);

        assertThat(chunks.size()).isGreaterThan(2);
        for (int i = 1; i < chunks.size(); i++) {
            // 步长 = 200 - 50 = 150，因此下一块起点 = 上一块终点 - 50（硬切场景）
            assertThat(chunks.get(i).charStart()).isLessThan(chunks.get(i - 1).charEnd());
        }
    }

    @Test
    @DisplayName("overlap ≥ chunkSize 不会死循环，仍能正常收敛")
    void illegalOverlapDoesNotLoopForever() {
        String text = "字".repeat(500);

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 10, 50);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).isNotBlank());
    }

    @Test
    @DisplayName("charStart/charEnd 与原文一致：用内容反查能对上")
    void offsetsMatchOriginalText() {
        String text = "第一段内容。\n\n第二段内容。\n\n第三段内容。".repeat(10);

        List<ChunkSplitter.TextChunk> chunks = splitter.split(text, 80, 10);

        assertThat(chunks).isNotEmpty();
        for (ChunkSplitter.TextChunk chunk : chunks) {
            String original = text.substring(chunk.charStart(), chunk.charEnd());
            assertThat(original.strip()).isEqualTo(chunk.content());
        }
    }
}
