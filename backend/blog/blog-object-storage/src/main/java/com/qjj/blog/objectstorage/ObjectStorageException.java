package com.qjj.blog.objectstorage;

/**
 * 对象存储能力层异常（解耦业务错误码）。
 * blog-service 调用方按 {@link Reason} 翻译为 BlogErrorCodeEnum。
 */
public class ObjectStorageException extends RuntimeException {

    public enum Reason {
        PROVIDER_NOT_FOUND,
        PROVIDER_DISABLED,
        NO_PROVIDER_AVAILABLE,
        CONFIG_INVALID,
        PUT_FAILED,
        GET_FAILED,
        REMOVE_FAILED,
        TEST_FAILED,
        PUBLIC_URL_CONFIG_MISSING,
        SECRET_DECRYPT_FAILED,
        MASTER_KEY_MISSING
    }

    private final Reason reason;

    public ObjectStorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ObjectStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}