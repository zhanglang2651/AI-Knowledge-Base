package com.zhanglang.kb.rag.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文档解析任务生产者。
 * <p>
 * 使用 Redis Stream 而非 MQ：
 * <ul>
 *     <li>项目已有 Redis，不引入额外中间件，部署成本为零</li>
 *     <li>消费者组天然支持多实例消费、消息确认（XACK）与失败重投（XPENDING）</li>
 *     <li>相比 Java 线程池，消息持久化在 Redis 中，服务重启后任务不丢</li>
 * </ul>
 * <p>
 * <b>可通过 {@code kb.queue.enabled=false} 关闭</b>。
 * 关闭后本 Bean 与消费者都不会装配，上传接口退化为同步解析 —— 这是为了在
 * 没有 Redis 的机器上也能跑通链路，生产环境不要关。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kb.queue.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentStreamProducer {

    /** 流名称。命名规范：stream:业务域:动作 */
    public static final String STREAM_KEY = "stream:doc:parse";

    /** 消费者组名。多实例部署时共用同一个组，实现负载均衡 */
    public static final String GROUP_NAME = "doc-parse-group";

    /** 消息字段名 */
    public static final String FIELD_DOC_ID = "docId";

    private final StringRedisTemplate redisTemplate;

    /**
     * 提交解析任务。
     * <p>
     * 该方法必须极快（毫秒级）且不抛业务异常：它在上传接口的主链路上，
     * 一旦阻塞或抛错，用户会认为上传失败。投递异常仅记日志，由定时任务补偿。
     *
     * @param docId 文档 ID
     */
    public void submit(Long docId) {
        try {
            redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of(FIELD_DOC_ID, String.valueOf(docId)))
                            .withStreamKey(STREAM_KEY));
            log.info("解析任务已投递, docId={}", docId);
        } catch (Exception ex) {
            // 不向上抛：上传动作本身已成功落库与落盘，投递失败可由重试任务补偿
            log.error("解析任务投递失败, docId={}", docId, ex);
        }
    }
}
