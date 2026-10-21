package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 * <p>
 * 此处不对密码做复杂度校验：登录只校验"是否正确"，
 * 若在登录时校验格式，会把密码策略泄露给攻击者（等于告诉对方"你的字典格式不对"）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class LoginRequest {

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
