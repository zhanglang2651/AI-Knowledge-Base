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
 * 会话实体。
 * <p>
 * 一个会话固定绑定一个知识库：跨库问答会让引用来源变得混乱，
 * 用户也无法判断答案依据的是哪个库的资料，因此产品上刻意不做多库混合。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@TableName("chat_conversation")
public class ChatConversationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定的知识库 ID */
    private Long kbId;

    /** 归属用户 ID */
    private Long userId;

    /** 会话标题，默认取首条提问的前 20 字 */
    private String title;

    /** 消息条数，冗余字段，避免列表页 count */
    private Integer messageCount;

    /** 最后一条消息时间，用于列表排序 */
    private LocalDateTime lastMessageAt;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}
