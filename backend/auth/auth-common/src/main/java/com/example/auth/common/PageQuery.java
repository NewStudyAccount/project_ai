package com.example.auth.common;

import java.util.List;

/**
 * 分页入参契约。
 */
public record PageQuery(long current, long size, String orderBy, String order) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_SIZE = 10L;

    public PageQuery {
        if (current < 1) {
            current = DEFAULT_CURRENT;
        }
        if (size < 1 || size > 200) {
            size = DEFAULT_SIZE;
        }
    }

    public static PageQuery of(long current, long size) {
        return new PageQuery(current, size, null, null);
    }

    public long offset() {
        return (current - 1) * size;
    }

    public static List<String> allowedOrders() {
        return List.of("asc", "desc");
    }
}
