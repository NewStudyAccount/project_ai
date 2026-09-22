# Tasks: add-blog-content-modules

## 1. 数据与文章域

- [ ] 1.1 迁移：blog_post、blog_post_content、blog_category、blog_tag、blog_post_tag、blog_comment
- [ ] 1.2 文章 CRUD：草稿保存 content_md、元数据、slug
- [ ] 1.3 发布/下线状态机；发布流水线：MD→HTML→file 写 content.md/html
- [ ] 1.4 封面与正文图片走 file 服务

## 2. 分类标签评论

- [ ] 2.1 扁平分类 CRUD
- [ ] 2.2 标签 CRUD 与 post_tag 覆盖保存
- [ ] 2.3 评论提交（默认待审）、审核通过/驳回、开关校验

## 3. 前台与管理端

- [ ] 3.1 public API：列表/详情（OSS HTML）/评论提交
- [ ] 3.2 blog-admin：md-editor-v3 写文、分类标签、评论审核
- [ ] 3.3 blog-portal：匿名列表详情评论
- [ ] 3.4 权限码接入；XSS 白名单；单测与 build

## 4. 验证

- [ ] 4.1 发布双轨一致性（失败可重试）
- [ ] 4.2 匿名不可见草稿；无权限 403
- [ ] 4.3 前端 build/lint；后端 mvn test
