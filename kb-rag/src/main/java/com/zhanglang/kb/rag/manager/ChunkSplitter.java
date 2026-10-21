package com.zhanglang.kb.rag.manager;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器。
 * <p>
 * 分块质量直接决定检索质量，是整个 RAG 链路里最值得反复调参的环节。
 * 本实现采用「固定窗口 + 语义边界回退」策略：
 * <ol>
 *     <li>按 {@code chunkSize} 开窗，保证块大小可控（过长会稀释语义，过短会丢失上下文）</li>
 *     <li>在窗口尾部向前回溯，优先落在段落、句号、问号等自然边界上，避免把一句话拦腰截断</li>
 *     <li>相邻块保留 {@code overlap} 字符重叠，缓解"答案正好被切在边界上"的问题</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Component
public class ChunkSplitter {

    /**
     * 边界查找的候选字符，按优先级从高到低排列。
     * 段落分隔符优先级最高，其次是句末标点，最后是换行。
     */
    private static final String[] BOUNDARY_MARKS = {"\n\n", "。", "！", "？", "；", "\n", ". "};

    /**
     * 回溯比例：从窗口 60% 位置开始向前找边界。
     * 若从头找，会切出大量短块；若只从尾部找，可能找不到边界而退化为硬切。
     */
    private static final double BACKTRACK_RATIO = 0.6D;

    /**
     * 切分文本。
     *
     * @param text       清洗后的全文
     * @param chunkSize  块大小（字符数）
     * @param overlap    相邻块重叠字符数，必须小于 chunkSize
     * @return 切分结果，按原文顺序排列
     */
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        // 防御非法参数：overlap 大于等于 chunkSize 会导致步长为 0，进而死循环
        int safeChunkSize = Math.max(1, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize - 1));
        int step = safeChunkSize - safeOverlap;

        int textLength = text.length();
        int start = 0;
        int chunkNo = 1;

        while (start < textLength) {
            int maxEnd = Math.min(start + safeChunkSize, textLength);
            int end = maxEnd;

            if (maxEnd < textLength) {
                int searchFrom = start + (int) (safeChunkSize * BACKTRACK_RATIO);
                int boundary = findBoundary(text, searchFrom, maxEnd);
                if (boundary > start) {
                    end = boundary;
                }
            }

            String content = text.substring(start, end).strip();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(chunkNo, content, start, end));
                chunkNo++;
            }

            if (end >= textLength) {
                break;
            }
            // 步进时至少前进 1 个字符，保证循环必然收敛
            start = Math.max(end - safeOverlap, start + 1);
        }
        return chunks;
    }

    /**
     * 在 [from, to) 区间内从后向前查找语义边界。
     *
     * @param text 全文
     * @param from 搜索起点
     * @param to   搜索终点（不含）
     * @return 边界位置（含边界字符），找不到时返回 -1
     */
    private int findBoundary(String text, int from, int to) {
        int safeFrom = Math.max(0, from);
        for (String mark : BOUNDARY_MARKS) {
            int index = text.lastIndexOf(mark, to);
            if (index >= safeFrom) {
                // 切在分隔符之后，让标点归属前一块，语义更完整
                return index + mark.length();
            }
        }
        return -1;
    }

    /**
     * 文本块。
     *
     * @param chunkNo   块序号，从 1 开始，用于引用定位
     * @param content   块正文
     * @param charStart 在原文中的起始偏移
     * @param charEnd   在原文中的结束偏移
     */
    public record TextChunk(int chunkNo, String content, int charStart, int charEnd) {
    }
}
