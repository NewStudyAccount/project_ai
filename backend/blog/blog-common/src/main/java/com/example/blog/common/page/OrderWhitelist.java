package com.example.blog.common.page;

import com.example.blog.common.error.BizException;
import java.util.Set;

/** orderBy 白名单，禁止把用户输入拼进 SQL。 */
public final class OrderWhitelist {

    private OrderWhitelist() {
    }

    public static String requireAllowed(String orderBy, Set<String> allowed) {
        if (orderBy == null || orderBy.isBlank()) {
            return "";
        }
        String field = orderBy.trim();
        if (!allowed.contains(field)) {
            throw BizException.badParam("排序字段不合法");
        }
        return field;
    }

    public static String direction(String order) {
        return "asc".equalsIgnoreCase(order) ? "ASC" : "DESC";
    }
}
