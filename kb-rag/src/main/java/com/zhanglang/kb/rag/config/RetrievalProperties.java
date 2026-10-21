package com.zhanglang.kb.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索策略配置。
 * <p>
 * 这些参数决定"召回什么"，属于直接影响答案质量的核心参数，
 * 因此做成配置而不是写死常量 —— 换检索策略时不必改代码重新编译。
 * <p>
 * 全部字段都有默认值，未配置时即为「混合检索 + 向量权重略高」这一较稳的组合。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.retrieval")
public class RetrievalProperties {

    /**
     * 是否启用混合检索。
     * 关闭后退化为纯向量检索（等价于本项目早期版本），便于效果对比与线上回退。
     */
    private boolean hybridEnabled = true;

    /**
     * 向量召回在 RRF 融合中的权重。
     * <p>
     * 取 0.6 而非 0.5：语义相关是本系统的主诉求，
     * 关键词分支的定位是"补齐向量漏掉的精确匹配"，而不是与向量平权。
     */
    private double vectorWeight = 0.6D;

    /** 关键词召回在 RRF 融合中的权重 */
    private double keywordWeight = 0.4D;

    /**
     * RRF 常数 k。
     * <p>
     * 公式 score = Σ w / (k + rank)，k 越大越"平滑"，头部结果的优势被压缩得越厉害。
     * 取 60 是信息检索领域的经验值，能有效避免"某一路排第一就直接锁定结果"。
     */
    private int rrfK = 60;

    /**
     * 每路召回的放大倍数。
     * <p>
     * 融合前必须比最终 topK 多召回一些：两路结果的重叠度通常只有一半左右，
     * 只召回 topK 个会让融合几乎没有选择余地。
     */
    private int recallFactor = 3;

    /** 参与关键词切分的最大关键词个数，超出部分按长度优先截断 */
    private int maxKeywords = 8;

    /** 关键词最小长度，低于此长度的词（多为虚词）不参与匹配 */
    private int minKeywordLength = 2;

    /**
     * 纯关键词命中的准入阈值（关键词得分 0~1）。
     * <p>
     * 被向量召回的片段不受此限制；只有"关键词召回到、但向量没召回"的片段需要过这道门槛。
     * 中文二元切分天然召回偏宽，没有它会把大量擦边片段塞进上下文。
     */
    private double minKeywordScore = 0.25D;

    /**
     * 是否把融合得分回写为引用展示的 score。
     * <p>
     * 关闭时展示向量原始相似度（0~1，直观）；开启时展示 RRF 分（极小但反映融合排名）。
     * 默认关闭 —— 用户看到 "0.7232" 比看到 "0.0161" 更容易判断可信度。
     */
    private boolean exposeFusedScore = false;
}
