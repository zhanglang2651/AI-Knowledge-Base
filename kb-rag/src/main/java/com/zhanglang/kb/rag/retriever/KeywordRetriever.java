package com.zhanglang.kb.rag.retriever;

import com.zhanglang.kb.rag.config.RetrievalProperties;
import com.zhanglang.kb.rag.entity.ChunkDO;
import com.zhanglang.kb.rag.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 关键词召回器。
 * <p>
 * <b>它解决的是向量检索的结构性短板：</b>
 * 用户搜 "ERR-1042" 或 "ISO-9001" 时，embedding 会把它们编码成几乎相同的向量，
 * 因为对模型来说它们只是"长得像的两个短字符串"。这类查询必须靠字面匹配才能召回。
 * <p>
 * 中文没有空格分词，本类采用<b>二元切分（bigram）</b>：
 * 把连续汉字串切成所有相邻两字组合，例如 "试用期多长" → 试用期/期多/多长。
 * 好处是无需引入词典或额外依赖，代价是召回偏宽 —— 但这一路只作为补充，
 * 最终排序由 RRF 与向量结果共同决定，宽召回不会直接变成噪声答案。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordRetriever {

    /** 汉字区间判定：CJK 统一表意文字 */
    private static final int CJK_MIN = 0x4E00;

    private static final int CJK_MAX = 0x9FFF;

    /** ASCII 词（型号、缩写）权重高于中文二元组：它们更稀有、区分度更高 */
    private static final double ASCII_WEIGHT = 2.0D;

    private static final double CJK_WEIGHT = 1.0D;

    private final ChunkMapper chunkMapper;

    private final RetrievalProperties properties;

    /**
     * 关键词召回。
     *
     * @param kbId   知识库 ID
     * @param query  用户问题
     * @param limit  返回条数
     * @return 按关键词得分倒序的命中列表
     */
    public List<Hit> search(Long kbId, String query, int limit) {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<ChunkDO> candidates;
        try {
            // 粗筛上限放大 20 倍：打分只在候选集内做，候选太少会漏掉高分片段
            candidates = chunkMapper.selectByKeywords(kbId, keywords, limit * 20);
        } catch (Exception ex) {
            // 关键词分支失败不能影响问答主流程，降级为"没有关键词召回"
            log.warn("关键词粗筛失败, kbId={}, reason={}", kbId, ex.getMessage());
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Double> weightMap = buildWeightMap(keywords);
        List<Hit> hits = new ArrayList<>(candidates.size());
        for (ChunkDO chunk : candidates) {
            double score = score(chunk.getContent(), keywords, weightMap);
            if (score <= 0) {
                continue;
            }
            hits.add(Hit.builder()
                    .chunkKey(chunkKey(chunk.getDocId(), chunk.getChunkNo()))
                    .docId(chunk.getDocId())
                    .docName(chunk.getDocName())
                    .pageNo(chunk.getPageNo())
                    .chunkNo(chunk.getChunkNo())
                    .content(chunk.getContent())
                    .keywordScore(round(score))
                    .build());
        }

        return hits.stream()
                .sorted(Comparator.comparingDouble(Hit::getKeywordScore).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 从问题中切分关键词。
     * <p>
     * 顺序有意为之：ASCII 词（更稀有）排在前面。达到上限后截断时，
     * 优先保留区分度高的词，而不是按出现顺序把后面的一刀切掉。
     *
     * @param query 用户问题
     * @return 去重后的关键词列表
     */
    public List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        List<String> asciiWords = new ArrayList<>();
        List<String> cjkGrams = new ArrayList<>();

        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder cjkBuffer = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (isCjk(ch)) {
                flushAscii(asciiBuffer, asciiWords);
                cjkBuffer.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjkBuffer, cjkGrams);
                asciiBuffer.append(ch);
            } else {
                // 标点与空白是天然分隔符
                flushAscii(asciiBuffer, asciiWords);
                flushCjk(cjkBuffer, cjkGrams);
            }
        }
        flushAscii(asciiBuffer, asciiWords);
        flushCjk(cjkBuffer, cjkGrams);

        asciiWords.stream().filter(this::isUsableWord).forEach(result::add);
        cjkGrams.stream().filter(this::isUsableWord).forEach(result::add);

        return result.stream().limit(properties.getMaxKeywords()).toList();
    }

    /**
     * 计算候选分块的关键词得分（0~1）。
     * <p>
     * 公式：Σ (关键词权重 × ln(1 + 出现次数)) / Σ 关键词权重，最后截断到 1。
     * <p>
     * 用 {@code ln(1+n)} 而非 {@code 1+ln(n)}：前者未命中时恰好为 0，
     * 且分母是权重总和，结果天然落在 0~1；若写成 {@code 1+ln(n)}，
     * 关键词出现两次以上就会超过 1，把这个分数当成"相似度"展示给用户会立刻露馅。
     */
    private double score(String content, List<String> keywords, Map<String, Double> weightMap) {
        if (content == null || content.isEmpty()) {
            return 0D;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        double weighted = 0D;
        for (String keyword : keywords) {
            int occurrences = countOccurrences(lowerContent, keyword);
            if (occurrences > 0) {
                weighted += weightMap.getOrDefault(keyword, CJK_WEIGHT) * Math.log(1D + occurrences);
            }
        }
        if (weighted <= 0D) {
            return 0D;
        }
        return Math.min(1D, weighted / totalWeight(weightMap));
    }

    /** 统计子串出现次数；中文二元组重叠出现时用朴素匹配即可，数据量小 */
    private int countOccurrences(String content, String keyword) {
        int count = 0;
        int index = content.indexOf(keyword);
        while (index >= 0) {
            count++;
            index = content.indexOf(keyword, index + keyword.length());
        }
        return count;
    }

    private Map<String, Double> buildWeightMap(List<String> keywords) {
        Map<String, Double> weightMap = new java.util.HashMap<>(keywords.size() * 2);
        for (String keyword : keywords) {
            boolean ascii = keyword.chars().anyMatch(ch -> !isCjk((char) ch));
            weightMap.put(keyword, ascii ? ASCII_WEIGHT : CJK_WEIGHT);
        }
        return weightMap;
    }

    private double totalWeight(Map<String, Double> weightMap) {
        return weightMap.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** 攒够一个 ASCII 词就落袋 */
    private void flushAscii(StringBuilder buffer, List<String> sink) {
        if (buffer.isEmpty()) {
            return;
        }
        sink.add(buffer.toString());
        buffer.setLength(0);
    }

    /** 把一段连续汉字切成全部相邻二字组合 */
    private void flushCjk(StringBuilder buffer, List<String> sink) {
        if (buffer.isEmpty()) {
            return;
        }
        String run = buffer.toString();
        buffer.setLength(0);
        if (run.length() == 1) {
            // 单字过于宽泛（如"钱""假"），几乎每篇文档都命中，不参与召回
            return;
        }
        for (int i = 0; i + 1 < run.length(); i++) {
            sink.add(run.substring(i, i + 2));
        }
    }

    private boolean isUsableWord(String word) {
        return word.length() >= properties.getMinKeywordLength();
    }

    private boolean isCjk(char ch) {
        return ch >= CJK_MIN && ch <= CJK_MAX;
    }

    /** 保留四位小数，避免 JSON 里出现一长串浮点数 */
    private double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    /** 与向量分支保持同一套去重键 */
    private String chunkKey(Long docId, Integer chunkNo) {
        return docId + ":" + chunkNo;
    }
}
