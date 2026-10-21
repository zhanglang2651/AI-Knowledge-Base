package com.zhanglang.kb.rag.retriever;

import lombok.Builder;
import lombok.Data;

/**
 * 融合后的检索命中。
 * <p>
 * 一个 Hit 可能同时来自向量召回与关键词召回（此时 {@code via} 为 BOTH），
 * 也可能只来自其中一路。保留两路原始分数而不是只留一个最终分，
 * 是为了在引用面板里能说明"这条为什么被选中"。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@Builder
public class Hit {

    /** 跨路去重键：docId:chunkNo。用 chunkNo 而不是向量 id，因为两路 id 体系不同 */
    private String chunkKey;

    private Long docId;

    private String docName;

    private Integer pageNo;

    private Integer chunkNo;

    private String content;

    /** 向量相似度，未命中向量召回时为 null */
    private Double vectorScore;

    /** 关键词得分（0~1），未命中关键词召回时为 null */
    private Double keywordScore;

    /** RRF 融合得分 */
    private double fusedScore;

    /**
     * 命中来源：VECTOR / KEYWORD / BOTH。
     * 用于前端展示，让用户知道这条片段是"语义匹配"还是"关键词精确命中"。
     */
    public String via() {
        boolean byVector = vectorScore != null;
        boolean byKeyword = keywordScore != null;
        if (byVector && byKeyword) {
            return "BOTH";
        }
        return byVector ? "VECTOR" : "KEYWORD";
    }

    /**
     * 展示用得分。
     * <p>
     * 优先展示向量相似度（0~1，用户能直觉判断相关性）；
     * 纯关键词命中没有相似度，退化为展示关键词得分。
     *
     * @return 0~1 区间的分数
     */
    public double displayScore() {
        return vectorScore != null ? vectorScore : (keywordScore == null ? 0D : keywordScore);
    }
}
