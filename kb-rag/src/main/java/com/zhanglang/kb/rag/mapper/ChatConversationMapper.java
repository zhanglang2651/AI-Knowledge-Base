package com.zhanglang.kb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanglang.kb.rag.entity.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话数据访问接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
}
