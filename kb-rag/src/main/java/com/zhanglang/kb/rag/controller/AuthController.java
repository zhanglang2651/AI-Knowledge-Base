package com.zhanglang.kb.rag.controller;

import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.rag.dto.LoginRequest;
import com.zhanglang.kb.rag.dto.LoginResponse;
import com.zhanglang.kb.rag.dto.RegisterRequest;
import com.zhanglang.kb.rag.dto.UserVO;
import com.zhanglang.kb.rag.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * <p>
 * Controller 层只做三件事：接收参数、调用服务、包装响应。
 * <b>禁止在这里写业务判断</b> —— 一旦业务逻辑渗到 Controller，
 * 后续要加定时任务、消息消费等入口时就无法复用，只能复制粘贴。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册。
     *
     * @param request 注册请求
     * @return 新用户 ID
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 登录。
     *
     * @param request 登录请求
     * @return 令牌与用户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 查询当前登录用户。
     * 前端刷新页面后用它恢复登录态，避免把用户信息只存在 localStorage 里。
     *
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }
}
