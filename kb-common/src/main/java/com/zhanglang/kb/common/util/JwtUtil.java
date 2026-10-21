package com.zhanglang.kb.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 工具类。
 * <p>
 * 采用 HS256 对称签名，密钥从配置 {@code kb.jwt.secret} 注入，<b>禁止硬编码在代码里</b>
 * （《阿里巴巴 Java 开发手册》安全规约：禁止在代码或配置文件中直接写入密钥明文）。
 * <p>
 * 生产环境请通过环境变量 {@code KB_JWT_SECRET} 注入，不要提交到 Git。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Component
public class JwtUtil {

    /** 载荷中存放用户名的键名 */
    private static final String CLAIM_USERNAME = "uname";

    /**
     * 载荷中存放角色的键名。
     * <p>
     * 把角色放进 Token 而不是每次查库，是为了让鉴权在拦截器里一次完成、零数据库往返。
     * 代价是角色变更后旧 Token 仍在有效期内保留旧权限，
     * 因此后台改角色时应同时让该用户重新登录（本项目通过短有效期 12 小时控制影响面）。
     */
    private static final String CLAIM_ROLE = "role";

    /** HS256 要求密钥长度不低于 256 位，即 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    private final long expireHours;

    /**
     * 构造 JWT 工具。
     *
     * @param secret      签名密钥，长度必须 ≥ 32 字符
     * @param expireHours Token 有效期（小时）
     * @throws IllegalStateException 密钥过短时抛出，属于启动期配置错误，应快速失败
     */
    public JwtUtil(@Value("${kb.jwt.secret}") String secret,
                   @Value("${kb.jwt.expire-hours:12}") long expireHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // 密钥过短会导致 jjwt 在签发时抛 WeakKeyException，这里提前暴露，避免运行到一半才报错
            throw new IllegalStateException("kb.jwt.secret 长度不足 32 字节，无法用于 HS256 签名");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireHours = expireHours;
    }

    /**
     * 签发 Token。
     *
     * @param userId   用户主键，写入 subject
     * @param username 用户名，写入自定义声明，便于前端直接展示
     * @param role     角色，用于后台接口鉴权
     * @return 紧凑格式的 JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 Token。
     *
     * @param token 紧凑格式的 JWT 字符串
     * @return 载荷；Token 无效、过期或签名不匹配时返回 {@code null}
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // 解析失败属于正常的鉴权失败分支，不打印堆栈，避免被恶意请求刷满日志
            return null;
        }
    }

    /**
     * 从载荷中提取用户 ID。
     *
     * @param claims 载荷
     * @return 用户 ID
     */
    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从载荷中提取用户名。
     *
     * @param claims 载荷
     * @return 用户名
     */
    public String extractUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /**
     * 从载荷中提取角色。
     *
     * @param claims 载荷
     * @return 角色；旧版本签发的 Token 中不存在该字段时返回 null
     */
    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
