package com.zhanglang.kb.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客户端配置。
 * <p>
 * {@link ChatClient} 是 Spring AI 的门面类，相比直接注入 ChatModel 多了三层能力：
 * 默认 system 提示、Advisor 链（可挂记忆/日志/内容过滤）、流式与结构化输出。
 * <p>
 * 这里不做全局 Advisor 编排：RAG 的检索与上下文拼装已在 Service 中显式完成，
 * 目的是拿到每条引用的分数与元数据（Advisor 内部检索不暴露这些信息）。
 * 若后续要接多轮记忆 Advisor，在此处追加即可，业务代码无需改动。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 构建 ChatClient。
     *
     * @param builder Spring AI 自动配置提供的构建器，已绑定 application.yml 中的模型参数
     * @return ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 内存向量库兜底实现。
     * <p>
     * <b>只在没有其他 VectorStore 时生效</b>（{@code @ConditionalOnMissingBean}）：
     * 生产环境引入 pgvector 的 Starter 后会由 PgVectorStore 接管，本方法不参与装配。
     * 本地 h2 profile 下显式关闭 pgvector，才会走到这里。
     * <p>
     * 代价必须说清楚：
     * <ul>
     *     <li>向量存在 JVM 堆内存中，<b>重启即丢</b>，需要重新上传文档</li>
     *     <li>检索是暴力遍历算余弦相似度，数据量大时 O(n)，只适合开发验证</li>
     * </ul>
     * 优点是零外部依赖，能在没有 Docker/PostgreSQL 的机器上完整跑通 RAG 链路。
     *
     * @param embeddingModel 向量模型，由 Spring AI 自动配置提供
     * @return 内存向量库
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore inMemoryVectorStore(EmbeddingModel embeddingModel) {
        log.warn("未检测到 PgVectorStore，已启用内存向量库（重启后向量数据会丢失，仅供本地开发）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
