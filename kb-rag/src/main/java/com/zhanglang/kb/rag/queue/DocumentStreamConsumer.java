package com.zhanglang.kb.rag.queue;

import com.zhanglang.kb.rag.service.DocumentProcessService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析任务消费者。
 * <p>
 * 以消费者组方式阻塞拉取 Redis Stream，单实例内串行处理。
 * 为什么串行：文档解析与向量化是 CPU + 外部 API 混合型任务，
 * 并行处理会同时打满内存和模型接口配额，反而拖慢整体吞吐；
 * 需要提升吞吐时应横向扩容实例（同一消费组自动负载均衡），而不是在本机开多线程。
 * <p>
 * 线程按手册并发规约创建：显式参数 + 有界队列 + 自定义线程名 + 守护线程。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kb.queue.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentStreamConsumer {

    /** 消费者名。同一组内必须唯一，这里用固定名，单实例部署足够 */
    private static final String CONSUMER_NAME = "consumer-1";

    /** 阻塞读取超时：无消息时最多阻塞 2 秒，避免空转打满 CPU */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(2);

    /** 异常后的重试间隔，防止 Redis 不可用时疯狂重试刷日志 */
    private static final long RETRY_BACKOFF_MILLIS = 1000L;

    private final StringRedisTemplate redisTemplate;

    private final DocumentProcessService documentProcessService;

    /** 运行标志，volatile 保证停止信号对消费线程立即可见 */
    private volatile boolean running = true;

    private ThreadPoolExecutor worker;

    /**
     * 启动消费线程。
     */
    @PostConstruct
    public void start() {
        ensureGroup();
        worker = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                runnable -> {
                    Thread thread = new Thread(runnable, "doc-parse-consumer");
                    // 设为守护线程：避免 JVM 退出时因该线程阻塞在 XREAD 而无法关闭
                    thread.setDaemon(true);
                    return thread;
                });
        worker.execute(this::loop);
        log.info("文档解析消费者已启动");
    }

    /**
     * 停止消费线程。
     */
    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
        }
        log.info("文档解析消费者已停止");
    }

    /**
     * 消费主循环。
     */
    private void loop() {
        while (running) {
            try {
                // lastConsumed 表示从"上一次已投递"的位置继续读，
                // 保证进程重启后不会重复处理已确认的消息
                var records = redisTemplate.opsForStream().read(
                        Consumer.from(DocumentStreamProducer.GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1).block(BLOCK_TIMEOUT),
                        StreamOffset.create(DocumentStreamProducer.STREAM_KEY, ReadOffset.lastConsumed()));

                if (CollectionUtils.isEmpty(records)) {
                    continue;
                }
                handleRecords(records);
            } catch (Exception ex) {
                if (!running) {
                    break;
                }
                // 常见原因：流/消费组尚未创建（首次启动先于首次上传）、Redis 短暂不可用。
                // 这里重试创建消费组并退避，属于自愈逻辑，不需要人工介入
                log.warn("消费循环异常，将重试: {}", ex.getMessage());
                ensureGroup();
                sleepQuietly();
            }
        }
    }

    /**
     * 处理一批消息。
     */
    private void handleRecords(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            try {
                Object rawDocId = record.getValue().get(DocumentStreamProducer.FIELD_DOC_ID);
                if (rawDocId == null) {
                    log.warn("消息缺少 docId 字段，丢弃, id={}", record.getId());
                    continue;
                }
                documentProcessService.process(Long.valueOf(rawDocId.toString()));
            } catch (Exception ex) {
                // 单条消息失败不能中断整个消费循环，否则一条坏数据会阻塞后面所有任务
                log.error("消息处理失败, id={}", record.getId(), ex);
            } finally {
                // 无论成功失败都要 ACK：失败信息已落库到文档状态，
                // 若不 ACK 会进入 PENDING 列表被反复重投，形成死循环
                acknowledge(record.getId().getValue());
            }
        }
    }

    /**
     * 确认消息已处理。
     */
    private void acknowledge(String recordId) {
        try {
            redisTemplate.opsForStream().acknowledge(
                    DocumentStreamProducer.STREAM_KEY,
                    DocumentStreamProducer.GROUP_NAME,
                    recordId);
        } catch (Exception ex) {
            log.warn("消息确认失败, id={}, reason={}", recordId, ex.getMessage());
        }
    }

    /**
     * 创建消费组（幂等）。
     * <p>
     * Redis 的 XGROUP CREATE 要求流已存在，首次启动时流还不存在会报错，
     * 此时忽略即可：等生产者投递第一条消息后，下一轮循环会成功建组。
     */
    private void ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    DocumentStreamProducer.STREAM_KEY,
                    ReadOffset.from("0"),
                    DocumentStreamProducer.GROUP_NAME);
            log.info("已创建消费组: {}", DocumentStreamProducer.GROUP_NAME);
        } catch (Exception ex) {
            // BUSYGROUP（组已存在）与"流不存在"都会走到这里，均属正常
            log.debug("消费组创建跳过: {}", ex.getMessage());
        }
    }

    /**
     * 安静休眠，用于异常退避。
     */
    private void sleepQuietly() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            // 恢复中断标记，让上层感知到停止信号
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
