# Spec: blog-post

## ADDED Requirements

### Requirement: 文章状态机

文章 SHALL 支持草稿、已发布、下线；发布 SHALL 记录 publish_time；前台 SHALL 仅暴露已发布且未删除文章。

#### Scenario: 发布文章

- **WHEN** 作者对草稿执行发布且具备 blog:post:publish
- **THEN** 状态为已发布，写入 publish_time，并触发正文文件写入 OSS

#### Scenario: 下线

- **WHEN** 管理下线已发布文章
- **THEN** 前台不可见，后台仍可编辑

### Requirement: MD 原文存库与 OSS 成品

系统 SHALL 将 Markdown 原文保存在 `blog_post_content.content_md`；发布时 SHALL 渲染 HTML（过滤 XSS）并将 content.md 与 content.html 写入 file 服务对象存储。

#### Scenario: 发布写 OSS

- **WHEN** 发布成功
- **THEN** OSS 存在该文 content.md 与 content.html，库内保存对应 object_key

#### Scenario: 编辑读库

- **WHEN** 作者打开编辑器
- **THEN** 加载库内 content_md，而非仅 OSS HTML

### Requirement: 封面与资产引用

封面与正文图片 SHALL 通过 file 服务上传并以 object_key/URL 引用；blog SHALL NOT 持有 OSS 密钥。

#### Scenario: 设置封面

- **WHEN** 上传封面并保存文章
- **THEN** blog_post 仅保存 cover_object_key/url
