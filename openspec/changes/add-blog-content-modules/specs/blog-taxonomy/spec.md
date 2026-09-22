# Spec: blog-taxonomy

## ADDED Requirements

### Requirement: 扁平分类

分类 SHALL 为扁平列表（无父子树）；slug 在系统内唯一。

#### Scenario: 创建分类

- **WHEN** 创建名称与 slug 唯一的分类
- **THEN** 成功写入；重复 slug 被拒绝

### Requirement: 标签与文章关联

文章与标签 SHALL 多对多；保存标签关联可幂等覆盖。

#### Scenario: 更新标签

- **WHEN** 保存文章时提交标签列表
- **THEN** 旧关联物理删除并写入新关联
