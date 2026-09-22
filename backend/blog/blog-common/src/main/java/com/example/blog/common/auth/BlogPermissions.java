package com.example.blog.common.auth;

/** blog 权限码常量（与 seed / sys_permission 对齐，防魔法字符串）。 */
public final class BlogPermissions {

    private BlogPermissions() {
    }

    public static final String POST_CREATE = "blog:post:create";
    public static final String POST_PUBLISH = "blog:post:publish";
    public static final String POST_MANAGE = "blog:post:manage";
    public static final String CATEGORY_MANAGE = "blog:category:manage";
    public static final String TAG_MANAGE = "blog:tag:manage";
    public static final String COMMENT_MODERATE = "blog:comment:moderate";
    public static final String ROLE_MANAGE = "blog:rbac:role";
    public static final String USER_ROLE_MANAGE = "blog:rbac:user-role";
}
