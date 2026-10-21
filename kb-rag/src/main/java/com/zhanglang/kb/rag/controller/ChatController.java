package com.zhanglang.kb.rag.controller;

import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.rag.dto.ChatRequest;
import com.zhanglang.kb.rag.dto.ConversationVO;
import com.zhanglang.kb.rag.dto.MessageVO;
import com.zhanglang.kb.rag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 问答接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式问答。
     * <p>
     * {@code produces = text/event-stream} 必须显式声明，
     * 否则部分网关会按 JSON 处理并缓冲整个响应，流式效果完全失效。
     * <p>
     * 前端用 {@code EventSource} 无法携带自定义请求头（Token 放不进 Authorization），
     * 因此这里用 POST + fetch 的流式读取方式，事件格式仍为标准 SSE。
     *
     * @param request 问答请求
     * @return SSE 流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    /**
     * 查询会话列表。
     *
     * @param kbId 知识库 ID
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations(@RequestParam Long kbId) {
        return Result.success(chatService.listConversations(kbId));
    }

    /**
     * 查询会话消息。
     *
     * @param id 会话 ID
     * @return 消息列表
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> listMessages(@PathVariable Long id) {
        return Result.success(chatService.listMessages(id));
    }

    /**
     * 删除会话。
     *
     * @param id 会话 ID
     * @return 空响应
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return Result.success();
    }
}
