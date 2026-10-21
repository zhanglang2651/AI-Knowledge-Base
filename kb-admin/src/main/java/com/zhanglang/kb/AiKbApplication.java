package com.zhanglang.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 应用启动类。
 * <p>
 * 三个注解各自的作用：
 * <ul>
 *     <li>{@code scanBasePackages}：三个模块同属 {@code com.zhanglang.kb} 包下，
 *         但启动类位于 {@code com.zhanglang.kb} 根部，默认扫描范围即可覆盖，
 *         这里显式声明是为了让依赖关系一眼可见，也方便后续拆包</li>
 *     <li>{@code @MapperScan}：扫描 MyBatis Mapper 接口，避免每个接口都写 {@code @Mapper}</li>
 *     <li>{@code @EnableAsync}：启用 {@code @Async}，用于日志等旁路任务</li>
 * </ul>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@EnableAsync
@MapperScan("com.zhanglang.kb.rag.mapper")
@SpringBootApplication(scanBasePackages = "com.zhanglang.kb")
public class AiKbApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiKbApplication.class, args);
    }
}
