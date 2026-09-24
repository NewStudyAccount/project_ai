package com.qjj.user.common.util;

/**
 * 脱敏工具（CLAUDE.md 6.7：手机号/身份证等脱敏）。
 */
public final class DesensitizedUtils {

    private DesensitizedUtils() {
    }

    /** 手机号：前 3 后 4，中间打码，如 138****5678 */
    public static String phone(String phone) {
        if (isBlank(phone) || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱：保留首字符与域名，如 a***@example.com */
    public static String email(String email) {
        if (isBlank(email)) {
            return email == null ? "" : email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String head = name.length() <= 1 ? name : name.substring(0, 1);
        return head + "***" + domain;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
