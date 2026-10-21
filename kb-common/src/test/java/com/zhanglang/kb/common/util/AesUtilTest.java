package com.zhanglang.kb.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AES/GCM 加解密单元测试。
 * <p>
 * 这里的两个测试直接对应安全设计的两条底线：
 * 随机 IV（防密文比对）与认证标签（防篡改），一旦有人把 GCM 换回 CBC，
 * 或把随机 IV 改成固定值，测试会立刻失败。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-21
 */
class AesUtilTest {

    private static final String SECRET = "unit-test-crypto-secret";

    private final AesUtil aesUtil = new AesUtil(SECRET);

    @Test
    @DisplayName("加密后可解密还原，中文与特殊字符不丢失")
    void encryptDecryptRoundTrip() {
        String plain = "sk-abc123_测试密钥/#$%";

        String cipher = aesUtil.encrypt(plain);

        assertThat(cipher).isNotNull().isNotEqualTo(plain);
        assertThat(aesUtil.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("随机 IV：同一明文两次加密得到不同密文，但都能解回原文")
    void samePlaintextYieldsDifferentCiphertext() {
        String plain = "sk-same-plaintext";

        String c1 = aesUtil.encrypt(plain);
        String c2 = aesUtil.encrypt(plain);

        assertThat(c1).isNotEqualTo(c2);
        assertThat(aesUtil.decrypt(c1)).isEqualTo(plain);
        assertThat(aesUtil.decrypt(c2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("篡改密文后解密失败返回 null（GCM 认证标签生效）")
    void tamperedCiphertextFailsToDecrypt() {
        String cipher = aesUtil.encrypt("sk-sensitive-key");
        byte[] bytes = java.util.Base64.getDecoder().decode(cipher);
        // 翻转密文区（IV 之后）最后一个字节
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

        assertThat(aesUtil.decrypt(tampered)).isNull();
    }

    @Test
    @DisplayName("换口令后旧密文无法解密，提示重新录入")
    void wrongSecretFailsToDecrypt() {
        String cipher = aesUtil.encrypt("sk-sensitive-key");
        AesUtil other = new AesUtil("another-secret");

        assertThat(other.decrypt(cipher)).isNull();
    }

    @Test
    @DisplayName("空值直接透传，不做加解密")
    void nullAndEmptyPassThrough() {
        assertThat(aesUtil.encrypt(null)).isNull();
        assertThat(aesUtil.encrypt("")).isEmpty();
        assertThat(aesUtil.decrypt(null)).isNull();
        assertThat(aesUtil.decrypt("")).isNull();
    }

    @Test
    @DisplayName("脱敏：只保留前 4 位与后 4 位，中间打星")
    void maskKeepsHeadAndTail() {
        assertThat(aesUtil.mask("sk-abcdefgh12345678")).isEqualTo("sk-a****5678");
        assertThat(aesUtil.mask(null)).isEqualTo("");
        // 过短 key 全打星，不暴露任何字符
        assertThat(aesUtil.mask("abc")).doesNotContain("abc");
    }
}
