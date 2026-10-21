package com.zhanglang.kb.rag.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 集中声明鉴权拦截器的生效范围。白名单必须显式列出，
 * 不允许使用"排除整个路径前缀"的粗暴写法 —— 新增接口时容易漏配导致裸奔。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * 注册拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                // 只拦截业务接口，静态资源与监控端点不过鉴权
                .addPathPatterns("/api/**")
                // 注册与登录是匿名接口，必须先排除，否则用户永远无法登录
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/error"
                );
    }
}
