package com.zhanglang.kb.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhanglang.kb.common.context.UserContext;
import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 鉴权拦截器。
 * <p>
 * 职责：校验 Authorization 头中的 Bearer Token，校验通过后把用户身份写入 {@link UserContext}，
 * 供 Service 层直接取用，避免每个方法都从请求参数里传 userId。
 * <p>
 * 为什么不用 Spring Security：本项目只需要「登录校验 + 资源归属判断」两件事，
 * 引入 Security 会带来过滤器链、配置类、匿名路径管理等额外复杂度，
 * 而收益（表单登录、OAuth2、方法级权限）在本场景用不上。
 * 若后续要做细粒度 RBAC，再迁移到 Security 也不困难 —— 业务代码只依赖 UserContext，不依赖实现。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    /** Authorization 头前缀，注意 Bearer 后必须有一个空格 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 后台接口路径前缀 */
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";

    /** 管理员角色标识 */
    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    /**
     * 请求进入 Controller 前执行鉴权。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @return true 放行，false 中断请求
     * @throws IOException 写响应体失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 预检请求不携带业务 Token，直接放行，否则浏览器跨域会全部失败
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response);
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            // Token 无效或过期，可能是正常过期也可能是伪造，统一返回 401 不区分原因
            writeUnauthorized(response);
            return false;
        }

        UserContext.set(jwtUtil.extractUserId(claims), jwtUtil.extractUsername(claims),
                jwtUtil.extractRole(claims));

        // 后台接口做二次角色校验。
        // 只靠前端隐藏菜单是不够的：任何人都能直接构造 /api/v1/admin/** 请求，
        // 权限必须在服务端每个受保护的入口上校验（手册「安全规约」：服务端必须做权限校验）。
        String uri = request.getRequestURI();
        if (uri.startsWith(ADMIN_PATH_PREFIX) && !ADMIN_ROLE.equals(UserContext.getRole())) {
            log.warn("非管理员访问后台接口被拒绝, userId={}, uri={}", UserContext.getUserId(), uri);
            writeForbidden(response);
            return false;
        }
        return true;
    }

    /**
     * 请求结束后清理线程上下文。
     * <p>
     * <b>此方法不可省略。</b> Tomcat 线程池复用线程，若不清理，
     * 下一次请求可能读到上一个用户的身份，造成越权访问，且这类问题极难复现和定位。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 输出 401 响应。
     * 使用 ObjectMapper 序列化而非手拼 JSON 字符串，避免转义问题（手册「代码格式」建议）。
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED);
    }

    /**
     * 输出 403 响应。已登录但角色不足时使用，
     * 与 401（未登录）区分开，便于前端决定是跳登录页还是提示无权限。
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN);
    }

    /**
     * 统一写出错误响应。
     *
     * @param response   响应对象
     * @param httpStatus HTTP 状态码
     * @param resultCode 业务错误码
     * @throws IOException 写响应体失败时抛出
     */
    private void writeError(HttpServletResponse response, int httpStatus, ResultCode resultCode)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> body = Result.fail(resultCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
