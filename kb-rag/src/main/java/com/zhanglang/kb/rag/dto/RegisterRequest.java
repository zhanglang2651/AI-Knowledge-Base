package com.zhanglang.kb.rag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 * <p>
 * 校验规则前移到 DTO 层，让非法请求在进入业务逻辑前就被拦截。
 * 密码复杂度在这里限制下限，上限 32 位是为了防止超长字符串拖慢 BCrypt 计算
 * （BCrypt 耗时与输入长度正相关，无上限可被用于慢速攻击）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Data
public class RegisterRequest {

    /** 登录名：4-32 位，仅允许字母、数字、下划线 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在 4-32 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /** 密码：8-32 位，必须同时包含字母和数字 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在 8-32 位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
    private String password;

    /** 昵称，可为空，为空时默认取用户名 */
    @Size(max = 32, message = "昵称长度不能超过 32 位")
    private String nickname;

    /** 邮箱，可为空 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 位")
    private String email;
}
