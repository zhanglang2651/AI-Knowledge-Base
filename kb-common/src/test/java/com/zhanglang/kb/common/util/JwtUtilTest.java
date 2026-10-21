package com.zhanglang.kb.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 工具单元测试。
 * <p>
 * JWT 是鉴权的根基，这里的每个分支（签发、解析、过期、篡改）都值得一个用例：
 * 拦截器对 {@code parseToken} 返回 null 的处理是否正确，完全取决于这些约定。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
class JwtUtilTest {

    /** HS256 要求 ≥ 32 字节，测试密钥满足最低长度 */
    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 12L);

    @Test
    @DisplayName("签发后解析：userId / username / role 全部还原")
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generateToken(42L, "zhanglang", "ADMIN");

        Claims claims = jwtUtil.parseToken(token);
        assertThat(claims).isNotNull();
        assertThat(jwtUtil.extractUserId(claims)).isEqualTo(42L);
        assertThat(jwtUtil.extractUsername(claims)).isEqualTo("zhanglang");
        assertThat(jwtUtil.extractRole(claims)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("过期 Token 解析返回 null，而不是抛异常")
    void parseExpiredTokenReturnsNull() {
        // 有效期 0 小时：exp 等于签发时刻，立即过期
        JwtUtil shortLived = new JwtUtil(SECRET, 0L);
        String token = shortLived.generateToken(1L, "user", "USER");

        assertThat(shortLived.parseToken(token)).isNull();
    }

    @Test
    @DisplayName("换密钥签发的 Token 无法通过解析（签名不匹配）")
    void parseTokenSignedByOtherKeyReturnsNull() {
        JwtUtil other = new JwtUtil("another-secret-0123456789abcdef0123456789", 12L);
        String token = other.generateToken(1L, "user", "USER");

        assertThat(jwtUtil.parseToken(token)).isNull();
    }

    @Test
    @DisplayName("篡改 payload 后解析失败，防止越权伪造角色")
    void tamperedTokenReturnsNull() {
        String token = jwtUtil.generateToken(1L, "user", "USER");
        String forged = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtUtil.parseToken(forged)).isNull();
    }

    @Test
    @DisplayName("垃圾字符串解析返回 null，不抛异常")
    void parseGarbageReturnsNull() {
        assertThat(jwtUtil.parseToken("not-a-jwt")).isNull();
        assertThat(jwtUtil.parseToken("")).isNull();
    }

    @Test
    @DisplayName("密钥不足 32 字节时构造直接失败，快速暴露配置错误")
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtUtil("too-short", 12L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }
}
