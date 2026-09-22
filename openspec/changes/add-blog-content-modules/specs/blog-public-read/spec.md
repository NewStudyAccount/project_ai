# Spec: blog-public-read

## ADDED Requirements

### Requirement: 匿名公开阅读

未登录用户 SHALL 能浏览已发布文章列表与详情；详情正文 SHALL 优先使用 OSS 中的 HTML 成品。

#### Scenario: 匿名打开详情

- **WHEN** 匿名用户访问已发布文章 slug
- **THEN** 返回元数据与正文 HTML（来自 OSS 或等价读路径）

#### Scenario: 草稿不可见

- **WHEN** 匿名用户访问草稿或下线文章
- **THEN** 返回不存在或无权限，不泄露正文
