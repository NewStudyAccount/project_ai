# 对象存储设计原型与说明

> 本目录是对象存储能力的**设计包**：通用设计正文 + 管理端原型图/交互原型。
> 设计契约唯一裁剪源：[`object-storage-design.md`](./object-storage-design.md)（原 `docs/template/object-storage-design.md`）。

## 目录结构

| 路径 | 说明 |
|------|------|
| [`object-storage-design.md`](./object-storage-design.md) | 对象存储通用设计（独立模块 / S3 多服务商 / 管理端双页） |
| [`prototype.html`](./prototype.html) | 可交互原型（文件管理展示页 + 服务商配置页，云白风格） |
| [`assets/proto-file-list.svg`](./assets/proto-file-list.svg) | 文件管理页原型图 |
| [`assets/proto-storage-provider.svg`](./assets/proto-storage-provider.svg) | 存储服务商配置页原型图 |

## 原型页对应关系

| 原型页 | 设计章节 | 路由（示例） | 职责 |
|--------|----------|--------------|------|
| 文件管理 | §7.2 | `/files` | 上传、筛选（含服务商）、预览/下载、逻辑删除 |
| 存储服务商 | §7.3 | `/storage/providers` | 服务商 CRUD、连通性测试、启停、设默认 |

视觉基线：`docs/template/admin-ui-design.md`（云白）· 组件 Element Plus。

## 打开方式

- 浏览器直接打开 `prototype.html`（离线、无外网依赖）。
- SVG 原型图可单独预览或插入设计评审材料。

## 关联文档

- `docs/template/admin-ui-design.md` — 管理端视觉 / P-List 布局
- `docs/template/gateway-design.md` — 不经网关中转 body
- `docs/test-env.md` — 测试环境对象存储连接
- `docs/version-baseline.md` — S3/MinIO SDK 版本
