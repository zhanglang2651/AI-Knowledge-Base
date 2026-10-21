package com.zhanglang.kb.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.common.context.UserContext;
import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.common.util.JwtUtil;
import com.zhanglang.kb.rag.dto.LoginRequest;
import com.zhanglang.kb.rag.dto.LoginResponse;
import com.zhanglang.kb.rag.dto.RegisterRequest;
import com.zhanglang.kb.rag.dto.UserVO;
import com.zhanglang.kb.rag.entity.SysUserDO;
import com.zhanglang.kb.rag.mapper.SysUserMapper;
import com.zhanglang.kb.rag.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证服务实现。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 普通用户角色 */
    private static final String ROLE_USER = "USER";

    /** 管理员角色 */
    private static final String ROLE_ADMIN = "ADMIN";

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 未删除 */
    private static final int NOT_DELETED = 0;

    /**
     * 用于耗时对齐的占位哈希（对应明文 "password" 的 BCrypt 结果）。
     * 账号不存在时拿它与用户输入做一次校验，使该分支耗时与"密码错误"分支接近，
     * 防止攻击者通过响应时间差枚举有效用户名。必须是合法 BCrypt 格式，否则编码器会告警。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** Token 有效期（小时），与 JwtUtil 使用同一配置项 */
    @Value("${kb.jwt.expire-hours:12}")
    private long expireHours;

    private final SysUserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    /**
     * 注册新用户。
     * <p>
     * 事务边界只覆盖"查重 + 插入"这两步数据库操作。
     * 不加事务会出现并发注册同名用户的可能（两个请求同时通过查重），
     * 最终由数据库唯一索引兜底，但用户会看到 500，体验很差。
     *
     * @param request 注册请求
     * @return 新用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        Long exists = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        SysUserDO user = new SysUserDO();
        user.setUsername(request.getUsername());
        // 存哈希，绝不存明文。BCrypt 内部已包含随机盐，无需额外加盐步骤
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // 昵称为空时用用户名兜底，避免前端展示空白
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        // 首个注册用户自动成为管理员。
        // 否则系统里永远不存在 ADMIN，后台管理接口没有任何入口可以进入
        // （没有"创建用户"的后台功能，也就无法把谁提升为管理员）。
        // 这是首次部署的引导逻辑，只在用户表为空时生效。
        Long userCount = userMapper.selectCount(null);
        boolean firstUser = userCount == null || userCount == 0;
        user.setRole(firstUser ? ROLE_ADMIN : ROLE_USER);
        user.setStatus(STATUS_ENABLED);
        user.setIsDeleted(NOT_DELETED);
        userMapper.insert(user);

        log.info("用户注册成功, userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * 登录并签发 Token。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getUsername, request.getUsername())
                .eq(SysUserDO::getIsDeleted, NOT_DELETED));

        // 用户不存在与密码错误返回同一错误码，避免攻击者通过错误信息枚举有效账号
        if (user == null) {
            // 即使账号不存在也执行一次哈希校验，使响应耗时与"密码错误"分支接近，
            // 防止攻击者通过响应时间差判断账号是否存在（时序侧信道）
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            // 禁用状态可以明确告知，因为此时用户已通过密码校验，不存在账号枚举风险
            throw new BizException(ResultCode.LOGIN_FAILED, "账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功, userId={}", user.getId());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(expireHours * 3600)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    /**
     * 查询当前登录用户。
     *
     * @return 用户信息
     */
    @Override
    public UserVO currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 正常情况下拦截器已保证 userId 存在，此处为防御性判断
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        SysUserDO user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getIsDeleted())) {
            // Token 有效但用户已被删除，视为登录态失效，强制前端重新登录
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .gmtCreate(user.getGmtCreate())
                .build();
    }
}
