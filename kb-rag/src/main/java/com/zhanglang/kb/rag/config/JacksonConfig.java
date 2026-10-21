package com.zhanglang.kb.rag.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置。
 * <p>
 * <b>为什么需要手动定义：</b>Spring Boot 4 已迁移到 Jackson 3（{@code tools.jackson}），
 * 不再自动提供 Jackson 2 的 {@code com.fasterxml.jackson.databind.ObjectMapper} bean。
 * 而本项目（以及 Spring AI 的部分组件）仍在使用 Jackson 2 的 API，
 * 直接注入会报 {@code No qualifying bean of type 'ObjectMapper'}。
 * <p>
 * 这里显式声明一个 Jackson 2 的实例，专供业务层序列化使用
 * （如对话消息的引用快照 {@code sources_json}），与 MVC 的消息转换器互不干扰。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Configuration
public class JacksonConfig {

    /**
     * 业务层 JSON 序列化器。
     *
     * @return Jackson 2 的 ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 反序列化遇到未知字段不报错：引用快照结构后续可能扩展，避免历史数据打不开
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 说明：本实例只用于序列化引用快照（SourceVO 列表，不含时间字段），
        // 因此不注册 JavaTimeModule，也就无需引入 jackson-datatype-jsr310 依赖。
        // 前端响应中的时间字段由 Spring MVC 的消息转换器（Jackson 3）负责格式化。
        return mapper;
    }
}
