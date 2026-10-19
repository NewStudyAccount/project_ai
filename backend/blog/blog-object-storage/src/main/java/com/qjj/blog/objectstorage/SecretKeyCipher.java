package com.qjj.blog.objectstorage;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SK 加密组件（object-storage-design.md §9 / design D5）。
 * AES-GCM（随机 12 字节 IV），落库形态 = Base64(IV ‖ 密文)。
 * 主密钥来源：环境变量（默认 OBJECT_STORAGE_SK_KEY，Base64 的 32 字节）。
 *
 * <p><b>懒加载：</b>主密钥在首次 encrypt/decrypt 时从环境变量解析，启动时不强校验。
 * 本地开发用 yml 引导的明文 SK（无需加解密），可不设环境变量即可启动；
 * 仅在通过管理 API 创建/更新 DB 服务商（需加密 SK 落库）或解析 DB 密文 SK 时才校验。
 *
 * <p>解密失败抛 {@link ObjectStorageException}（Reason=SECRET_DECRYPT_FAILED）；
 * 主密钥缺失抛 {@link ObjectStorageException}（Reason=MASTER_KEY_MISSING）。
 */
public class SecretKeyCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";

    private final ObjectStorageProperties.SecretCrypto cryptoConfig;
    private volatile SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public SecretKeyCipher(ObjectStorageProperties.SecretCrypto cryptoConfig) {
        this.cryptoConfig = cryptoConfig;
    }

    /**
     * 懒加载解析主密钥。仅在首次加解密时调用。
     */
    private SecretKeySpec keySpec() {
        SecretKeySpec k = this.keySpec;
        if (k != null) {
            return k;
        }
        synchronized (this) {
            k = this.keySpec;
            if (k != null) {
                return k;
            }
            if (!"env".equalsIgnoreCase(cryptoConfig.getKeySource())) {
                throw new ObjectStorageException(ObjectStorageException.Reason.MASTER_KEY_MISSING,
                        "unsupported secret-crypto.key-source: " + cryptoConfig.getKeySource()
                                + " (current only supports env)");
            }
            String envName = cryptoConfig.getKeyEnv() == null || cryptoConfig.getKeyEnv().isBlank()
                    ? "OBJECT_STORAGE_SK_KEY" : cryptoConfig.getKeyEnv();
            String raw = System.getenv(envName);
            if (raw == null || raw.isBlank()) {
                throw new ObjectStorageException(ObjectStorageException.Reason.MASTER_KEY_MISSING,
                        "环境变量 " + envName + " 未设置（SK 主密钥；本地 yml 引导可忽略，"
                                + "仅创建/更新 DB 服务商或解析密文 SK 时需要）");
            }
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(raw.trim());
            } catch (IllegalArgumentException e) {
                // 兼容：原始字符串直接按 UTF-8 字节作为密钥（要求 16/24/32 字节）
                keyBytes = raw.getBytes(StandardCharsets.UTF_8);
            }
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new ObjectStorageException(ObjectStorageException.Reason.MASTER_KEY_MISSING,
                        "SK 主密钥长度非法（需 16/24/32 字节，实际 " + keyBytes.length + "）");
            }
            k = new SecretKeySpec(keyBytes, "AES");
            this.keySpec = k;
            return k;
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            plaintext = "";
        }
        SecretKeySpec ks = keySpec();
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, ks, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (ObjectStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SK encrypt failed", e);
        }
    }

    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isBlank()) {
            // 空 SK（种子占位）直接返回空串，不触发主密钥解析
            return "";
        }
        SecretKeySpec ks = keySpec();
        try {
            byte[] all = Base64.getDecoder().decode(cipherBase64);
            if (all.length <= IV_LEN) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] cipherText = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, ks, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (ObjectStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new ObjectStorageException(ObjectStorageException.Reason.SECRET_DECRYPT_FAILED,
                    "SK decrypt failed (master key mismatch?)", e);
        }
    }
}
