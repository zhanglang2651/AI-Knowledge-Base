package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息展示对象。
 * <p>
 * 只包含前端需要展示的字段。密码哈希、逻辑删除标记等内部字段一律不出现在这里，
 * 这是防止敏感信息泄露的第一道闸门（手册「安全规约」：敏感数据不得输出到前端）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    /** 角色：USER / ADMIN */
    private String role;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private LocalDateTime gmtCreate;
}
