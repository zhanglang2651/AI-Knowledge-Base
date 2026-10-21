package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 检索评估用例。
 * <p>
 * 一条用例 = 一个问题 + "什么算答对"的判定条件。
 * 没有判定条件的评估等于没评估：既不知道该召回什么，也无法量化调参效果。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
@Data
public class RetrievalEvalCase {

    /** 评估用的问题文本 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 位")
    private String question;

    /**
     * 期望命中的关键词，<b>全部出现</b>才算覆盖。
     * <p>
     * 用"全部"而不是"任一"：多关键词能逼出"只命中一半"的假阳性，
     * 而这正是宽召回最典型的翻车方式。
     */
    private List<String> expectKeywords;

    /**
     * 期望命中的文档名，<b>任一匹配</b>即算文档正确。
     * <p>
     * 当同一个问题在多个文档里都有相关内容时（如员工手册与考勤制度），
     * 用文档名而不是关键词来限定，能避免"命中了但命中错文档"被判为通过。
     */
    private List<String> expectDocNames;

    /**
     * 是否为负样本（期望"检索不到"）。
     * <p>
     * 只测正样本会得到一个虚高的召回率：检索器可以靠"什么都能召回"刷高分。
     * 负样本用来观察误召回 —— 这是知识库问答最容易翻车的地方，
     * 与其胡编，不如老实回答"资料中未提及"。
     */
    private Boolean expectMiss = Boolean.FALSE;
}
