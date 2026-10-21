package com.zhanglang.kb.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加解密工具，用于模型 API Key 的落库加密。
 * <p>
 * 依据《阿里巴巴 Java 开发手册》安全规约：
 * 配置文件中不得出现明文密钥，敏感数据入库必须加密。
 * 模型 API Key 一旦泄露，攻击者可直接消耗额度甚至滥用账号，属于高危数据。
 * <p>
 * <b>算法选择：AES/GCM/NoPadding</b>
 * <ul>
 *     <li>GCM 是带认证的加密模式，密文被篡改会在解密时直接失败，
 *         而 CBC 模式无法发现篡改（可能解出乱码却不报错）</li>
 *     <li>每次加密使用随机 IV，相同明文得到不同密文，避免密文比对</li>
 * </ul>
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Component
public class AesUtil {

    /** 变换方式：AES + GCM 模式 + 无填充（GCM 自带填充语义） */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 推荐的 IV 长度（字节）。12 字节是 NIST 推荐值，兼容性与性能最佳 */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度（位） */
    private static final int TAG_LENGTH_BIT = 128;

    /** AES-256 要求的密钥长度 */
    private static final int KEY_LENGTH = 32;

    private final SecretKeySpec secretKey;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造加密工具。
     * <p>
     * 配置的 secret 长度任意，这里用 SHA-256 派生固定 32 字节密钥，
     * 避免使用者因为长度不对导致 AES 初始化失败。
     *
     * @param secret 加密口令，来自配置 {@code kb.crypto.secret}
     * @throws IllegalStateException 派生密钥失败时抛出
     */
    public AesUtil(@Value("${kb.crypto.secret}") String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 AES 密钥失败", ex);
        }
    }

    /**
     * 加密。
     *
     * @param plainText 明文
     * @return Base64 字符串，内容为「IV + 密文」，IV 不保密，与密文一起存储即可
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // 加密失败属于系统级故障，必须抛出：继续执行会写入不可解密的脏数据
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    /**
     * 解密。
     *
     * @param cipherText Base64 字符串（IV + 密文）
     * @return 明文；解密失败返回 {@code null}
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] actual = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, actual, 0, actual.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(actual), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // 解密失败通常是换了加密口令，此时旧密文无法恢复，只能重新录入
            return null;
        }
    }

    /**
     * 脱敏展示 Key，只保留前 4 位与后 4 位。
     * <p>
     * 接口回显必须用脱敏值：前端只需要"确认这里配过 key"，
     * 不需要、也不应该拿到完整密钥（一旦前端泄露日志或截图，密钥就外流了）。
     *
     * @param apiKey 完整 key
     * @return 形如 {@code sk-a****z9xq}
     */
    public String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
