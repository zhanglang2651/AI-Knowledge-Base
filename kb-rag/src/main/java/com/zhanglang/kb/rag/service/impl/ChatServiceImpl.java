package com.zhanglang.kb.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhanglang.kb.common.context.UserContext;
import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.rag.dto.ChatRequest;
import com.zhanglang.kb.rag.dto.ConversationVO;
import com.zhanglang.kb.rag.dto.MessageVO;
import com.zhanglang.kb.rag.dto.SourceVO;
import com.zhanglang.kb.rag.entity.AiCallLogDO;
import com.zhanglang.kb.rag.entity.ChatConversationDO;
import com.zhanglang.kb.rag.entity.ChatMessageDO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import com.zhanglang.kb.rag.mapper.AiCallLogMapper;
import com.zhanglang.kb.rag.mapper.ChatConversationMapper;
import com.zhanglang.kb.rag.mapper.ChatMessageMapper;
import com.zhanglang.kb.rag.retriever.Hit;
import com.zhanglang.kb.rag.retriever.HybridRetriever;
import com.zhanglang.kb.rag.service.ChatService;
import com.zhanglang.kb.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 问答服务实现。
 * <p>
 * 这是整个项目的核心链路：检索 → 拼装上下文 → 流式生成 → 落库 → 计量。
 * <p>
 * <b>两个必须记住的坑：</b>
 * <ol>
 *     <li>{@code UserContext} 基于 ThreadLocal，异步线程读不到。
 *         用户 ID 必须在主线程取出后传参进来，否则会拿到 null 直接空指针</li>
 *     <li>SSE 写事件时客户端可能已断开（用户切走页面）。
 *         每次写入都要捕获异常并停止后续消费，否则日志会被 IOException 刷屏</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** SSE 超时时间：60 秒。超过该时长未完成，容器会主动关闭连接 */
    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    /** 多轮对话携带的历史轮数。取 5 轮是经验值：再多了会挤占检索上下文 */
    private static final int HISTORY_ROUNDS = 5;

    /** SSE 事件名，前端按此分发处理逻辑 */
    private static final String EVENT_SOURCES = "sources";
    private static final String EVENT_CONTENT = "content";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    /** 消息角色 */
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /** 未删除 */
    private static final int NOT_DELETED = 0;

    /** 检索为空时的兜底话术，不调用模型，省一次调用成本 */
    private static final String NO_CONTEXT_ANSWER =
            "知识库中未检索到与您问题相关的内容。建议换一种问法，或先上传相关资料。";

    private final HybridRetriever hybridRetriever;

    private final ChatClient chatClient;

    private final ChatConversationMapper conversationMapper;

    private final ChatMessageMapper messageMapper;

    private final AiCallLogMapper callLogMapper;

    private final KnowledgeBaseService knowledgeBaseService;

    private final ObjectMapper objectMapper;

    private final ThreadPoolTaskExecutor chatExecutor;

    /** 当前对话模型名，用于日志与计量 */
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String chatModelName;

    /**
     * 流式问答入口。
     *
     * @param request 问答请求
     * @return SSE 发射器
     */
    @Override
    public SseEmitter streamChat(ChatRequest request) {
        // 权限校验放在主线程，异常可直接由全局异常处理器返回标准 JSON
        KnowledgeBaseDO kb = knowledgeBaseService.getOwnedOrThrow(request.getKbId());
        // 必须在主线程取出用户 ID，异步线程中的 ThreadLocal 是空的
        Long userId = UserContext.getUserId();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        // 超时与错误回调：只记录日志，不再尝试写事件（此时连接通常已关闭）
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, userId={}", userId);
            emitter.complete();
        });
        emitter.onError(ex -> log.warn("SSE 连接异常, userId={}, reason={}", userId, ex.getMessage()));

        chatExecutor.execute(() -> doStream(kb, userId, request, emitter));
        return emitter;
    }

    /**
     * 执行流式问答。运行在独立线程池中。
     */
    private void doStream(KnowledgeBaseDO kb, Long userId, ChatRequest request, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        // 客户端断开标记：一旦写入失败就停止消费模型流，避免无意义的 Token 消耗
        AtomicBoolean clientAlive = new AtomicBoolean(true);
        ChatConversationDO conversation = null;

        try {
            conversation = resolveConversation(userId, kb.getId(), request);
            saveMessage(conversation.getId(), ROLE_USER, request.getQuestion(), null, 0, 0, null);

            List<Hit> documents = retrieve(kb, request.getQuestion());
            List<SourceVO> sources = toSources(documents);
            // 先推引用再推正文：用户能立刻看到"参考了哪些文档"，等待感显著降低
            clientAlive.set(send(emitter, EVENT_SOURCES, sources));

            if (CollectionUtils.isEmpty(documents)) {
                clientAlive.set(send(emitter, EVENT_CONTENT, Map.of("delta", NO_CONTEXT_ANSWER)));
                finishConversation(conversation, sources);
                saveMessage(conversation.getId(), ROLE_ASSISTANT, NO_CONTEXT_ANSWER,
                        chatModelName, 0, estimateTokens(NO_CONTEXT_ANSWER), sources);
                sendDone(emitter, conversation.getId(), 0, estimateTokens(NO_CONTEXT_ANSWER),
                        System.currentTimeMillis() - startTime);
                emitter.complete();
                return;
            }

            String prompt = buildPrompt(kb, conversation.getId(), documents, request.getQuestion());
            int promptTokens = estimateTokens(prompt);
            StringBuilder answer = new StringBuilder();

            // 阻塞式消费模型流：在本线程内顺序执行，异常处理与状态维护比响应式写法直观得多。
            // 注意 toStream() 必须在独立线程中调用，否则会阻塞 Tomcat 工作线程
            chatClient.prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .toStream()
                    .forEach(chunk -> {
                        if (!clientAlive.get() || chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        answer.append(chunk);
                        boolean ok = send(emitter, EVENT_CONTENT, Map.of("delta", chunk));
                        clientAlive.set(ok);
                    });

            String finalAnswer = answer.toString();
            int completionTokens = estimateTokens(finalAnswer);
            long latency = System.currentTimeMillis() - startTime;

            finishConversation(conversation, sources);
            saveMessage(conversation.getId(), ROLE_ASSISTANT, finalAnswer,
                    chatModelName, promptTokens, completionTokens, sources);
            recordCallLog(userId, promptTokens, completionTokens, latency, true, null);
            sendDone(emitter, conversation.getId(), promptTokens, completionTokens, latency);
            emitter.complete();

            log.info("问答完成, convId={}, promptTokens={}, completionTokens={}, latency={}ms",
                    conversation.getId(), promptTokens, completionTokens, latency);
        } catch (Exception ex) {
            log.error("问答失败, userId={}, kbId={}", userId, request.getKbId(), ex);
            recordCallLog(userId, 0, 0, System.currentTimeMillis() - startTime, false, "C0001");
            send(emitter, EVENT_ERROR, Map.of(
                    "code", ResultCode.AI_CALL_FAILED.getCode(),
                    "message", "生成回答时出现异常，请稍后重试"));
            emitter.completeWithError(ex);
        }
    }

    /**
     * 混合检索（向量 + 关键词，RRF 融合）。
     * <p>
     * 具体策略与降级处理都在 {@code HybridRetriever} 内，本层只关心"拿到哪些片段"。
     */
    private List<Hit> retrieve(KnowledgeBaseDO kb, String question) {
        List<Hit> hits = hybridRetriever.retrieve(kb, question);
        return hits == null ? Collections.emptyList() : hits;
    }

    /**
     * 把检索结果转为引用来源列表。
     * <p>
     * 分数展示 {@code displayScore}：向量命中显示相似度，
     * 纯关键词命中显示关键词得分 —— 都落在 0~1，用户能直接理解。
     */
    private List<SourceVO> toSources(List<Hit> hits) {
        if (CollectionUtils.isEmpty(hits)) {
            return Collections.emptyList();
        }
        return hits.stream()
                .map(hit -> SourceVO.builder()
                        .docId(hit.getDocId())
                        .docName(hit.getDocName())
                        .pageNo(hit.getPageNo())
                        .chunkNo(hit.getChunkNo())
                        .score(hit.displayScore())
                        .content(hit.getContent())
                        .via(hit.via())
                        .build())
                .toList();
    }

    /**
     * 组装最终 Prompt。
     * <p>
     * system 段的三条约束是抑制幻觉的关键，不要为了"回答更自然"删掉任何一条：
     * 模型在没有约束时，会优先选择"编一个通顺的答案"而不是承认不知道。
     */
    private String buildPrompt(KnowledgeBaseDO kb, Long conversationId,
                               List<Hit> documents, String question) {
        String context = documents.stream()
                .map(hit -> {
                    Object pageNo = hit.getPageNo() == null ? "-" : hit.getPageNo();
                    // 片段来源一并标注：模型据此知道"这是原文摘录"而不是自己的推断
                    return "【" + hit.getDocName() + " 第 " + pageNo + " 页】\n" + hit.getContent();
                })
                .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

        String history = buildHistory(conversationId);

        return """
                你是企业知识库问答助手，只依据下面的【参考资料】回答用户问题，并遵守以下规则：
                1. 仅使用参考资料中的信息作答，不得引入外部知识或主观推测；
                2. 若参考资料无法回答该问题，直接回复"资料中未提及"，不要编造；
                3. 回答末尾用 [1][2] 标注所依据的片段序号；
                4. 使用简体中文，表达简洁，必要时用列表或步骤呈现。

                【参考资料】
                %s

                【历史对话】
                %s

                【用户问题】
                %s
                """.formatted(context, history.isBlank() ? "（无）" : history, question);
    }

    /**
     * 构建历史对话片段。
     * <p>
     * 只取最近若干条，且按时间正序拼接。全量历史会让 Token 成本线性增长，
     * 还会稀释当前问题与检索结果的关联度，反而降低回答质量。
     */
    private String buildHistory(Long conversationId) {
        List<ChatMessageDO> messages = messageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .orderByDesc(ChatMessageDO::getId)
                .last("LIMIT " + (HISTORY_ROUNDS * 2)));
        if (CollectionUtils.isEmpty(messages)) {
            return "";
        }
        // 查询是倒序（取最近的），拼接时需要重新排回正序，否则对话顺序是反的。
        // 注意不能用 List.reversed()：那是 JDK 21 才有的方法，本项目基线为 JDK 17
        return messages.stream()
                .sorted(java.util.Comparator.comparing(ChatMessageDO::getId))
                .map(msg -> (ROLE_USER.equals(msg.getRole()) ? "用户: " : "助手: ") + msg.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 解析会话：传入 ID 则复用并校验归属，否则新建。
     */
    private ChatConversationDO resolveConversation(Long userId, Long kbId, ChatRequest request) {
        if (request.getConversationId() != null) {
            ChatConversationDO existing = conversationMapper.selectById(request.getConversationId());
            if (existing == null || Integer.valueOf(1).equals(existing.getIsDeleted())) {
                throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
            }
            if (!existing.getUserId().equals(userId)) {
                // 越权读取他人会话会泄露历史问答内容，必须严格校验
                throw new BizException(ResultCode.FORBIDDEN);
            }
            return existing;
        }

        ChatConversationDO conversation = new ChatConversationDO();
        conversation.setKbId(kbId);
        conversation.setUserId(userId);
        // 标题取问题前 20 字，超出截断加省略号，保证列表页整齐
        String question = request.getQuestion();
        conversation.setTitle(question.length() > 20 ? question.substring(0, 20) + "…" : question);
        conversation.setMessageCount(0);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setIsDeleted(NOT_DELETED);
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 会话收尾：累加消息数并刷新最后活跃时间。
     */
    private void finishConversation(ChatConversationDO conversation, List<SourceVO> sources) {
        conversation.setMessageCount(
                (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    /**
     * 保存单条消息。
     */
    private void saveMessage(Long conversationId, String role, String content, String model,
                             int promptTokens, int completionTokens, List<SourceVO> sources) {
        ChatMessageDO message = new ChatMessageDO();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(0);
        message.setSourcesJson(serializeSources(sources));
        messageMapper.insert(message);
    }

    /**
     * 序列化引用快照。序列化失败不应影响消息落库，退化为 null 即可。
     */
    private String serializeSources(List<SourceVO> sources) {
        if (CollectionUtils.isEmpty(sources)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException ex) {
            log.warn("引用快照序列化失败", ex);
            return null;
        }
    }

    /**
     * 记录调用日志。
     * 计量失败绝不能影响用户拿到答案，因此整体 try-catch 且不抛出。
     */
    private void recordCallLog(Long userId, int promptTokens, int completionTokens,
                               long latency, boolean success, String errorCode) {
        try {
            AiCallLogDO logDO = new AiCallLogDO();
            logDO.setUserId(userId);
            logDO.setScene("CHAT");
            logDO.setModel(chatModelName);
            logDO.setPromptTokens(promptTokens);
            logDO.setCompletionTokens(completionTokens);
            // 成本按 0 记录，实际单价从 ai_model_config 读取后计算（一期先保证链路通）
            logDO.setCost(BigDecimal.ZERO);
            logDO.setLatencyMs((int) latency);
            logDO.setIsSuccess(success ? 1 : 0);
            logDO.setErrorCode(errorCode);
            callLogMapper.insert(logDO);
        } catch (Exception ex) {
            log.warn("调用日志写入失败, userId={}", userId, ex);
        }
    }

    /**
     * 发送 SSE 事件。
     *
     * @return true 表示写入成功（客户端仍在），false 表示连接已断开
     */
    private boolean send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (Exception ex) {
            // 客户端关闭页面时必然触发，属于正常现象，用 debug 级别避免刷日志
            log.debug("SSE 写入失败，客户端可能已断开, event={}", event);
            return false;
        }
    }

    /**
     * 发送完成事件。
     */
    private void sendDone(SseEmitter emitter, Long conversationId,
                          int promptTokens, int completionTokens, long latency) {
        send(emitter, EVENT_DONE, Map.of(
                "conversationId", conversationId,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "latencyMs", latency));
    }

    /**
     * 查询指定知识库下的会话列表。
     *
     * @param kbId 知识库 ID
     * @return 会话列表
     */
    @Override
    public List<ConversationVO> listConversations(Long kbId) {
        // 先校验知识库归属，避免通过遍历 kbId 探测他人会话是否存在
        knowledgeBaseService.getOwnedOrThrow(kbId);
        List<ChatConversationDO> conversations = conversationMapper.selectList(
                Wrappers.<ChatConversationDO>lambdaQuery()
                        .eq(ChatConversationDO::getKbId, kbId)
                        .eq(ChatConversationDO::getUserId, UserContext.getUserId())
                        .eq(ChatConversationDO::getIsDeleted, NOT_DELETED)
                        .orderByDesc(ChatConversationDO::getLastMessageAt));
        return conversations.stream()
                .map(conv -> ConversationVO.builder()
                        .id(conv.getId())
                        .kbId(conv.getKbId())
                        .title(conv.getTitle())
                        .messageCount(conv.getMessageCount())
                        .lastMessageAt(conv.getLastMessageAt())
                        .build())
                .toList();
    }

    /**
     * 查询会话内的消息列表。
     *
     * @param conversationId 会话 ID
     * @return 消息列表，按时间正序
     */
    @Override
    public List<MessageVO> listMessages(Long conversationId) {
        getOwnedConversation(conversationId);
        List<ChatMessageDO> messages = messageMapper.selectList(Wrappers.<ChatMessageDO>lambdaQuery()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .orderByAsc(ChatMessageDO::getId));
        return messages.stream()
                .map(msg -> MessageVO.builder()
                        .id(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .sources(deserializeSources(msg.getSourcesJson()))
                        .promptTokens(msg.getPromptTokens())
                        .completionTokens(msg.getCompletionTokens())
                        .gmtCreate(msg.getGmtCreate())
                        .build())
                .toList();
    }

    /**
     * 删除会话。
     * <p>
     * 只做逻辑删除，消息记录保留：用户可能只是不想在列表里看到它，
     * 但审计与统计仍需要这些数据。
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void deleteConversation(Long conversationId) {
        ChatConversationDO conversation = getOwnedConversation(conversationId);
        conversation.setIsDeleted(1);
        conversationMapper.updateById(conversation);
        log.info("会话已删除, convId={}", conversationId);
    }

    /**
     * 查询会话并校验归属。
     */
    private ChatConversationDO getOwnedConversation(Long conversationId) {
        ChatConversationDO conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Integer.valueOf(1).equals(conversation.getIsDeleted())) {
            throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return conversation;
    }

    /**
     * 反序列化引用快照。
     * 解析失败返回空列表而非抛异常：历史数据的格式问题不应该让整个会话打不开。
     */
    private List<SourceVO> deserializeSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<SourceVO>>() {
            });
        } catch (Exception ex) {
            log.warn("引用快照反序列化失败, json={}", sourcesJson);
            return Collections.emptyList();
        }
    }

    /**
     * 估算 Token 数。
     * <p>
     * 中文场景约 1 个 Token 对应 1.5 个汉字，这里按"字符数 / 2"粗略估算。
     * 该值仅用于成本看板展示，<b>不能用它对账</b>；
     * 若需要精确值，应改用 {@code chatClient...stream().chatResponse()}，
     * 从最后一个响应的 metadata.usage 中读取。
     */
    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }
}
