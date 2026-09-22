# Spec: blog-comment

## ADDED Requirements

### Requirement: 评论提交与审核状态

评论 SHALL 具有待审、通过、驳回状态；新建默认待审；前台 SHALL 仅展示通过的评论。

#### Scenario: 提交评论

- **WHEN** 对允许评论的已发布文章提交评论
- **THEN** 创建待审评论，前台暂不可见

#### Scenario: 审核通过

- **WHEN** 具备 blog:comment:moderate 的用户审核通过
- **THEN** 前台可见该评论

### Requirement: 评论开关与范围

文章 SHALL 可关闭评论；未发布文章不得接受评论。

#### Scenario: 关闭评论

- **WHEN** 文章 allow_comment 为否
- **THEN** 评论提交被拒绝
