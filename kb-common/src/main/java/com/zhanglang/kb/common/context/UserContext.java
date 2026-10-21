package com.zhanglang.kb.common.context;

/**
 * 当前登录用户上下文。
 * <p>
 * 由鉴权拦截器在请求进入时写入，请求结束时<b>必须</b>调用 {@link #clear()} 清理。
 * 原因：Tomcat 使用线程池复用线程，若不清理，下一个请求可能读到上一个用户的身份，
 * 造成越权。这是 {@link ThreadLocal} 最典型的踩坑场景。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public final class UserContext {

    /** 当前登录用户 ID */
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /** 当前登录用户名 */
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    /** 当前登录用户角色，用于后台接口鉴权 */
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    /**
     * 工具类禁止实例化。
     */
    private UserContext() {
    }

    /**
     * 写入当前登录用户。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色（USER / ADMIN）
     */
    public static void set(Long userId, String username, String role) {
        USER_ID_HOLDER.set(userId);
        USERNAME_HOLDER.set(username);
        ROLE_HOLDER.set(role);
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID；未登录时返回 {@code null}
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名；未登录时返回 {@code null}
     */
    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    /**
     * 获取当前登录用户角色。
     *
     * @return 角色；未登录或旧 Token 缺失该字段时返回 {@code null}
     */
    public static String getRole() {
        return ROLE_HOLDER.get();
    }

    /**
     * 清理上下文。拦截器的 afterCompletion 中必须调用。
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
