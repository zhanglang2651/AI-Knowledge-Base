package com.zhanglang.kb.rag.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 只做一件事：自动填充创建/更新时间。
 * <p>
 * <b>关于分页</b>：这里刻意没有注册 {@code PaginationInnerInterceptor}。
 * MyBatis-Plus 3.5.9 起把 JSqlParser 拆成独立依赖（mybatis-plus-jsqlparser），
 * 未引入时注册分页插件会在首次分页查询时抛 {@code ClassNotFoundException}，
 * 属于启动正常、运行才炸的坑。本项目的列表查询量级很小，
 * 直接在 Wrapper 上拼 {@code LIMIT/OFFSET}（参数为程序计算的整数，不接收用户原始输入，无注入风险）即可，
 * 少一个依赖就少一个踩坑点。若后续确实需要物理分页，再补依赖并在此注册即可。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册时间字段自动填充处理器。
     *
     * @return 元对象处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 插入时填充创建时间与更新时间。
             * 使用 strictInsertFill，字段已有值时不会覆盖，便于数据迁移场景手工指定时间。
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "gmtCreate", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "gmtModified", LocalDateTime.class, now);
            }

            /**
             * 更新时只刷新更新时间，创建时间保持原值。
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "gmtModified", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
