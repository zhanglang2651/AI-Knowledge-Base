package com.zhanglang.kb.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 * <p>
 * 选择 BCrypt 而非 MD5/SHA：
 * <ul>
 *     <li>自带随机盐，相同密码每次编码结果不同，彩虹表失效</li>
 *     <li>计算强度可调（work factor），硬件升级后调高参数即可提升破解成本</li>
 *     <li>MD5/SHA 属于快速哈希，显卡每秒可尝试数十亿次，绝不可用于存密码</li>
 * </ul>
 * 手册「安全规约」强制要求：用户敏感数据禁止明文存储，必须加盐哈希。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Configuration
public class PasswordConfig {

    /**
     * 强度参数。默认 10 表示 2^10 次迭代，单次校验约 50-100ms，
     * 在"用户无感"与"暴力破解成本"之间取得平衡。
     *
     * @return BCrypt 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
