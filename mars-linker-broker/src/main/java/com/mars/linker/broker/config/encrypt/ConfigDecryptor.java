package com.mars.linker.broker.config.encrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置值加解密器，支持 AES-256-CBC 对称加密和 ENC(Base64密文) 格式自动识别。
 * <p>
 * 职责：
 * - 对 ENC() 包裹的配置值透明解密
 * - 对明文配置值原样返回（向后兼容）
 * - 提供加密工具方法（用于配置迁移）
 * </p>
 * <p>
 * 密钥来源优先级：环境变量 > 配置文件
 * 密钥长度不足 128 位时启动失败
 * </p>
 */
public class ConfigDecryptor {

    private static final Logger log = LoggerFactory.getLogger(ConfigDecryptor.class);
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param encryptionKey AES 密钥（明文，至少 16 字节 / 128 位）
     * @throws IllegalStateException 密钥长度不足 128 位时抛出
     */
    public ConfigDecryptor(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.length() < 16) {
            throw new IllegalStateException("配置加密密钥长度不足 128 位（16 字节），当前: "
                    + (encryptionKey == null ? 0 : encryptionKey.length()) + " 字节");
        }
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = new byte[32];
        System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, 32));
        this.keySpec = new SecretKeySpec(aesKey, "AES");
    }

    /**
     * 若值为 ENC(Base64密文) 格式则解密，否则原样返回。
     *
     * @param value 配置值
     * @return 解密后的明文值，或原值（非 ENC 格式时）
     */
    public String decryptIfEncrypted(String value) {
        if (value == null || !value.startsWith(ENC_PREFIX) || !value.endsWith(ENC_SUFFIX)) {
            return value;
        }
        String encrypted = value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
        try {
            return decrypt(encrypted);
        } catch (Exception e) {
            log.error("配置值解密失败: {}", e.getMessage());
            throw new IllegalStateException("配置值解密失败", e);
        }
    }

    /**
     * 解密 Base64 编码的密文。
     *
     * @param encryptedBase64 Base64 编码的密文（含 IV 前缀）
     * @return 明文
     */
    public String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        if (combined.length < IV_LENGTH) {
            throw new IllegalArgumentException("密文长度不足，无法提取 IV");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] cipherText = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    /**
     * 加密明文，返回 Base64 编码的密文（IV 前缀 + 密文）。
     *
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
        System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 加密明文并包裹为 ENC() 格式（用于配置迁移）。
     *
     * @param plaintext 明文
     * @return ENC(Base64密文) 格式的字符串
     */
    public String encryptAndWrap(String plaintext) throws Exception {
        return ENC_PREFIX + encrypt(plaintext) + ENC_SUFFIX;
    }
}
