package com.qjj.blog.service.enums;

import com.qjj.blog.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum AuditActionEnum implements BaseEnum<String> {

    MENU_CREATE("MENU_CREATE", "创建菜单"),
    MENU_UPDATE("MENU_UPDATE", "更新菜单"),
    MENU_DELETE("MENU_DELETE", "删除菜单"),
    ROLE_CREATE("ROLE_CREATE", "创建角色"),
    ROLE_UPDATE("ROLE_UPDATE", "更新角色"),
    ROLE_DELETE("ROLE_DELETE", "删除角色"),
    ROLE_MENU("ROLE_MENU_UPDATE", "更新角色菜单"),
    ROLE_ASSIGN("ROLE_ASSIGN", "分配用户角色"),
    FILE_UPLOAD("FILE_UPLOAD", "上传文件"),
    FILE_DELETE("FILE_DELETE", "删除文件"),
    CATEGORY_CREATE("CATEGORY_CREATE", "创建分类"),
    CATEGORY_UPDATE("CATEGORY_UPDATE", "更新分类"),
    CATEGORY_DELETE("CATEGORY_DELETE", "删除分类"),
    TAG_CREATE("TAG_CREATE", "创建标签"),
    TAG_UPDATE("TAG_UPDATE", "更新标签"),
    TAG_DELETE("TAG_DELETE", "删除标签"),
    ARTICLE_CREATE("ARTICLE_CREATE", "创建文章"),
    ARTICLE_UPDATE("ARTICLE_UPDATE", "更新文章"),
    ARTICLE_CONTENT_UPDATE("ARTICLE_CONTENT_UPDATE", "更新正文"),
    ARTICLE_PUBLISH("ARTICLE_PUBLISH", "发布文章"),
    ARTICLE_OFFLINE("ARTICLE_OFFLINE", "下架文章"),
    ARTICLE_DELETE("ARTICLE_DELETE", "删除文章"),
    ARTICLE_DRAFT_DISCARD("ARTICLE_DRAFT_DISCARD", "放弃草稿"),
    SITE_CONFIG_UPDATE("SITE_CONFIG_UPDATE", "更新站点配置");

    private final String code;

    private final String desc;

    AuditActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
