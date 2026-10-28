package com.qjj.blog.common.enums;

import lombok.Getter;

/**
 * blog 业务错误码（业务段 2xxxxx = 2 + 系统号 03 + 业务序号，系统号登记见 docs/error-code-ranges.md §2）。
 * 新增业务码先在本系统序号内分配，禁止跨系统同码不同义。
 */
@Getter
public enum BlogErrorCodeEnum implements ErrorCode {

    MENU_PERMISSION_INVALID(203010, "权限标识非法（首段必须为 blog）"),
    MENU_PERMISSION_DUPLICATE(203011, "权限标识已存在"),
    MENU_NODE_NOT_FOUND(203012, "菜单不存在"),
    MENU_HAS_CHILDREN(203013, "存在子节点，禁止删除"),
    MENU_DEPTH_EXCEEDED(203014, "菜单层级过深"),
    MENU_ID_EXHAUSTED(203015, "本层/本段 id 已用尽"),
    MENU_PARENT_INVALID(203016, "父节点非法（按钮/接口必须挂在菜单下）"),
    ROLE_CODE_EXISTS(203020, "角色编码已存在"),
    ROLE_NOT_FOUND(203021, "角色不存在"),
    FILE_NOT_FOUND(203030, "文件不存在"),
    FILE_TYPE_NOT_ALLOWED(203031, "文件类型不在白名单"),
    FILE_TOO_LARGE(203032, "文件超过大小限制"),
    FILE_UPLOAD_FAILED(203033, "文件上传失败"),
    FILE_IN_USE(203034, "文件仍被业务引用，禁止删除"),
    CATEGORY_NOT_FOUND(203040, "分类不存在"),
    CATEGORY_IN_USE(203041, "分类下存在子类或文章"),
    CATEGORY_NAME_DUPLICATE(203042, "同级分类名已存在"),
    CATEGORY_DEPTH_EXCEEDED(203043, "分类层级最多 2 级"),
    TAG_NOT_FOUND(203050, "标签不存在"),
    TAG_IN_USE(203051, "标签已被文章引用"),
    TAG_NAME_DUPLICATE(203052, "标签名已存在"),
    ARTICLE_NOT_FOUND(203060, "文章不存在"),
    ARTICLE_PUBLISHED(203061, "已发布文章须先下架再删除"),
    CONTENT_REQUIRED(203062, "无正文可发布"),
    CONTENT_TOO_LARGE(203063, "正文超过大小限制"),
    CONTENT_FORMAT_INVALID(203064, "正文格式非法"),
    DRAFT_NOT_FOUND(203065, "无未发布草稿"),
    SLUG_DUPLICATE(203066, "slug 已存在"),
    SITE_CONFIG_KEY_INVALID(203070, "站点配置键非法"),
    PROVIDER_NOT_FOUND(203080, "存储服务商不存在"),
    PROVIDER_CODE_EXISTS(203081, "服务商编码已存在"),
    PROVIDER_DISABLED(203082, "服务商已停用，禁止新上传"),
    PROVIDER_TEST_FAILED(203083, "连通性测试失败"),
    PROVIDER_IN_USE(203084, "存在引用文件，禁止删除服务商"),
    PROVIDER_CONFIG_INVALID(203085, "服务商配置非法"),
    PROVIDER_SECRET_DECRYPT_FAILED(203086, "服务商密钥解密失败"),
    NO_STORAGE_PROVIDER_AVAILABLE(203087, "无可用存储服务商配置"),
    // 知识库段（api-design.md §6：203090–203099 文档/入库；203100–203109 检索/任务；203110–203119 AI 服务商）
    KNOWLEDGE_DOC_NOT_FOUND(203090, "知识文档不存在"),
    KNOWLEDGE_DOC_ALREADY_INGESTED(203091, "该文章已入库且内容未变化"),
    KNOWLEDGE_ARTICLE_NOT_INGESTABLE(203092, "文章未发布或无正文，不可入库"),
    KNOWLEDGE_DOC_INVALID(203093, "知识文档已失效，请先重新入库"),
    KNOWLEDGE_DOC_SOURCE_NOT_MANUAL(203095, "仅手动导入的知识可编辑/删除"),
    KNOWLEDGE_SEARCH_UNAVAILABLE(203100, "检索服务暂不可用"),
    KNOWLEDGE_EMBED_FAILED(203101, "文本向量化失败"),
    KNOWLEDGE_LLM_UNAVAILABLE(203102, "AI 组织服务暂不可用"),
    KNOWLEDGE_TASK_QUEUE_FULL(203103, "任务队列已满，请稍后重试"),
    KNOWLEDGE_TASK_NOT_FOUND(203104, "任务不存在"),
    KNOWLEDGE_TASK_STATE_INVALID(203105, "仅失败任务可重跑"),
    AI_PROVIDER_NOT_FOUND(203110, "AI 服务商不存在"),
    AI_PROVIDER_CODE_EXISTS(203111, "服务商编码已存在"),
    AI_PROVIDER_TEST_FAILED(203112, "连通性测试失败"),
    AI_PROVIDER_IN_USE(203113, "生效默认服务商，禁止删除/停用"),
    AI_PROVIDER_DIM_MISMATCH(203114, "向量维度与索引不一致，需重建索引"),
    DUPLICATE_REQUEST_IN_PROGRESS(203900, "请求处理中，请勿重复提交");

    private final int code;

    private final String desc;

    BlogErrorCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
