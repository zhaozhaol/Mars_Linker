package com.mars.linker.broker.config.encrypt;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * JWT 密钥强度校验器：在应用启动时校验 JWT 密钥长度 >= 256 位（32 字节）且非默认弱密钥。
 * <p>
 * 校验不通过则拒绝启动，防止使用弱密钥导致 JWT Token 可被伪造。
 * </p>
 */
@Component
public class JwtSecretValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);
    private static final String DEFAULT_WEAK_KEY = "change-me-to-a-secure-random-secret-key-32bytes!";
    private static final int MIN_KEY_LENGTH_BYTES = 32;

    private final MarsLinkerUiProperties props;

    public JwtSecretValidator(MarsLinkerUiProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isAuthEnabled()) {
            log.debug("UI 鉴权未启用，跳过 JWT 密钥校验");
            return;
        }

        String jwtSecretKey = props.getJwtSecretKey();
        if (jwtSecretKey == null || jwtSecretKey.isEmpty()) {
            throw new IllegalStateException("JWT 密钥未配置，UI 鉴权启用时必须设置 jwt-secret-key");
        }

        if (jwtSecretKey.equals(DEFAULT_WEAK_KEY)) {
            throw new IllegalStateException(
                    "JWT 密钥使用默认弱密钥，存在严重安全风险！请修改 mars.linker.ui.jwt-secret-key 配置项");
        }

        byte[] keyBytes = jwtSecretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(String.format(
                    "JWT 密钥长度不足 256 位（32 字节），当前: %d 字节。请修改 mars.linker.ui.jwt-secret-key 配置项",
                    keyBytes.length));
        }

        log.info("JWT 密钥强度校验通过，长度: {} 字节", keyBytes.length);
    }
}
