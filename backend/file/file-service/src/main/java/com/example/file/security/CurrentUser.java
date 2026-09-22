package com.example.file.security;

/** 当前登录用户上下文（JWT 过滤器写入）。 */
public final class CurrentUser {

    private static final ThreadLocal<Long> ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Long id, String username) {
        ID.set(id);
        USERNAME.set(username == null ? "" : username);
    }

    public static Long idOrNull() {
        return ID.get();
    }

    public static long requireId() {
        Long id = ID.get();
        if (id == null) {
            throw new IllegalStateException("未登录");
        }
        return id;
    }

    public static String username() {
        String u = USERNAME.get();
        return u == null ? "" : u;
    }

    public static void clear() {
        ID.remove();
        USERNAME.remove();
    }
}
