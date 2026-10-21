package com.zhanglang.kb.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应。
 * <p>
 * 只返回前端真正需要的字段，绝不把用户实体整个抛出去
 * （避免密码哈希、逻辑删除标记等内部字段泄露）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
@Builder
public class LoginResponse {

    /** 访问令牌，后续请求放入 Authorization: Bearer {token} */
    private String accessToken;

    /** 令牌类型，固定 Bearer，前端拼接 Authorization 头时使用 */
    private String tokenType;

    /** 有效期（秒），前端可据此提前刷新 */
    private Long expiresIn;

    /** 用户 ID */
    private Long userId;

    /** 登录名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色：USER / ADMIN */
    private String role;
}
