package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.rag.dto.ChatRequest;
import com.zhanglang.kb.rag.dto.ConversationVO;
import com.zhanglang.kb.rag.dto.MessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 问答服务。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public interface ChatService {

    /**
     * 流式问答。
     * <p>
     * 返回 SseEmitter 后由容器接管响应，业务逻辑在独立线程中执行，
     * 不占用 Tomcat 工作线程 —— 否则几十个并发问答就能把线程池占满，
     * 导致其他接口（包括登录）全部无响应。
     *
     * @param request 问答请求
     * @return SSE 发射器
     */
    SseEmitter streamChat(ChatRequest request);

    /**
     * 查询指定知识库下的会话列表。
     *
     * @param kbId 知识库 ID
     * @return 会话列表，按最后活跃时间倒序
     */
    List<ConversationVO> listConversations(Long kbId);

    /**
     * 查询会话内的消息列表。
     *
     * @param conversationId 会话 ID
     * @return 消息列表，按时间正序
     */
    List<MessageVO> listMessages(Long conversationId);

    /**
     * 删除会话（逻辑删除，消息保留）。
     *
     * @param conversationId 会话 ID
     */
    void deleteConversation(Long conversationId);
}
