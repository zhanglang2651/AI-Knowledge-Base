package com.zhanglang.kb.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息实体。
 * <p>
 * {@code sourcesJson} 保存当次回答的引用快照。之所以冗余存储而不在展示时重新检索：
 * 检索结果会随知识库内容变化，重查会导致历史回答的引用与当时不一致，
 * 用户翻历史时会发现"引用对不上了"，这是产品级缺陷。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("chat_message")
public class ChatMessageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID */
    private Long conversationId;

    /** 角色：user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** 生成该条回答所用的模型标识，用户消息为 null */
    private String model;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 生成耗时（毫秒），不含检索时间 */
    private Integer latencyMs;

    /** 引用来源快照，JSON 数组字符串 */
    private String sourcesJson;

    /** 仅需创建时间，消息不允许修改 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;
}
