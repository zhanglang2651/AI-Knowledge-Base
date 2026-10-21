package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.rag.dto.LoginRequest;
import com.zhanglang.kb.rag.dto.LoginResponse;
import com.zhanglang.kb.rag.dto.RegisterRequest;
import com.zhanglang.kb.rag.dto.UserVO;

/**
 * 认证服务。
 * <p>
 * 只负责「身份核验」这一件事，不掺入权限判断（那属于各业务服务的职责）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public interface AuthService {

    /**
     * 注册新用户。
     *
     * @param request 注册请求
     * @return 新用户 ID
     */
    Long register(RegisterRequest request);

    /**
     * 登录并签发 Token。
     *
     * @param request 登录请求
     * @return 登录结果，包含 Token 与基础用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 查询当前登录用户信息。
     *
     * @return 用户信息
     */
    UserVO currentUser();
}
