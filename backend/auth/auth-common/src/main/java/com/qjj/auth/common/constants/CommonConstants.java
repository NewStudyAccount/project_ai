package com.qjj.auth.common.constants;

public final class CommonConstants {
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_IDEMPOTENT_KEY = "Idempotent-Key";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;
    private CommonConstants() {
    }
}
