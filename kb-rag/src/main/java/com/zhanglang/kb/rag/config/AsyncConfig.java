package com.zhanglang.kb.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 * <p>
 * 用于「写调用日志」这类不影响主链路的旁路任务。
 * <p>
 * 依据《阿里巴巴 Java 开发手册》并发处理强制项：
 * <ol>
 *     <li>禁止使用 {@code Executors.newXxx()} 创建线程池 —— 其队列长度无界，高负载时会 OOM</li>
 *     <li>必须显式指定核心数、最大数、队列容量与拒绝策略</li>
 *     <li>线程名必须自定义，否则线上排查无法从线程栈区分业务</li>
 * </ol>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Configuration
public class AsyncConfig {

    /** 核心线程数：日志写入是低耗时任务，2 个足够 */
    private static final int CORE_POOL_SIZE = 2;

    /** 最大线程数：突发时最多扩到 8 个 */
    private static final int MAX_POOL_SIZE = 8;

    /** 队列容量：有界队列，防止任务堆积耗尽内存 */
    private static final int QUEUE_CAPACITY = 200;

    /** 空闲线程存活时间（秒） */
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * 调用日志线程池。
     * <p>
     * 拒绝策略选用 CallerRunsPolicy：队列满时由调用线程自己执行，
     * 相当于给上游一个自然的背压信号，比直接丢弃日志（审计缺失）或抛异常（影响主流程）更合理。
     *
     * @return 线程池执行器
     */
    @Bean("aiLogExecutor")
    public ThreadPoolTaskExecutor aiLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("ai-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待已提交任务执行完再退出，避免关服时丢日志
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 问答线程池。
     * <p>
     * 与日志线程池<b>物理隔离</b>：问答任务是长耗时 IO（等模型返回），
     * 若与日志共用队列，高峰期日志任务会排在问答任务前面，
     * 用户感受到的就是"点了提问半天没反应"。
     * <p>
     * 核心线程数按 SSE 长连接特性设置：每个连接占一个线程直到回答结束，
     * 32 个并发问答已是单机合理上限；超过时走 CallerRunsPolicy 由 Tomcat 线程兜底，
     * 表现为请求变慢而不是直接拒绝（用户体验优先）。
     *
     * @return 问答线程池
     */
    @Bean("chatExecutor")
    public ThreadPoolTaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /**
     * 文档解析线程池（仅在没有 Redis 时使用）。
     * <p>
     * 正常情况下文档解析由 Redis Stream 消费者驱动；关闭队列后（{@code kb.queue.enabled=false}）
     * 改为提交到本线程池异步执行，避免上传接口被解析过程阻塞。
     * <p>
     * 单线程是为了保持与 Stream 消费者一致的「串行处理」语义：
     * 解析是 CPU + 外部 API 混合型任务，并发跑多个只会互相抢资源。
     *
     * @return 文档解析线程池
     */
    @Bean("docExecutor")
    public ThreadPoolTaskExecutor docExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
