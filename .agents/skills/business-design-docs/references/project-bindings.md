# 项目绑定层（project bindings）——移植新项目只改这里

> **定位：** `business-design-docs` 的项目耦合**唯一清单**。skill 主流程 / 裁决机制 / 质量门保持项目无关；
> 凡**具体文件名、路径、口径节号、工具约定**，均集中登记在本文件；其余文档只用泛称 + 指向本文件。
> **本文件当前登记 = `project_ai` 仓库。** 移植动作见 §8（移植时只改本文件 + 清单列明处）。
>
> 泛称对照：下文「L0 契约」「各登记处」在 SKILL.md / references 其它文件中均以泛称出现。

## 1. L0 契约与方法论载体

| 泛称 | 本项目载体 | 用途 |
|------|-----------|------|
| L0 契约文件 | `CLAUDE.md`（`AGENTS.md` 仅指针，不复制内容） | 技术栈、架构、开发规范、协作红线、待裁决清单 |
| L0 口径节号（模板引言指针用） | `CLAUDE.md`：§5.2 鉴权/权限、§5.4 模块、§5.6 实体、§6.2 接口、§6.4 基础契约（§6.4.1 返回体 / §6.4.5 审计与发号 / §6.4.6 分页 / §6.4.7 Long→String）、§6.5 数据库、§6.9 幂等/限流、§6.11 横切 | table/api 模板「口径指针」指向的节 |
| L0 红线 / 反模式 / 待裁决 | `CLAUDE.md` §7（§7.1 反模式表）、§9 待裁决清单 | 停下来问的红线出处 |
| 文档方法论 | `docs/ai-doc-methodology.md` | 四层文档体系、六条元规则 |
| 变更制品层 | `openspec/changes/`（命令入口 `.claude/commands/opsx/`） | SDD 边界：未获明确指示不创建提案 |

## 2. 登记处（唯一登记处清单）

| 泛称 | 本项目载体 | 设计阶段用途 |
|------|-----------|-------------|
| 错误码登记处 | `docs/error-code-ranges.md` | 业务码段位/系统号防撞号 |
| 端口登记处 | `docs/port-registry.md` | 新服务/新前端端口先登记 |
| 账密登记处 | `docs/test-env.md` | 组件接入与测试账密（无生产密钥） |
| 版本基线 | `docs/version-baseline.md` | 新组件版本取用 |
| RBAC 短号规范 | `docs/template/rbac-design.md` | 权限标识与菜单/按钮短号续号 |
| 视觉 token | `docs/template/tokens-aurora.css` | 原型 token 唯一取值处 |

## 3. 目录与落点约定

> **非绑定：** 产物集自身的布局（`docs/<domain>-design/` 目录名、README/主设计/table-design/api-design/HTML 文件名）属 **skill 固定约定**，移植不改。下表登记的是**产物集之外**的仓库落点。

| 项 | 本项目约定 |
|----|-----------|
| 产物集根 | `docs/` 下（skill 固定 `<domain>-design/` 子目录名） |
| 建表 SQL | `deploy/db/migration/<db>/NN_<name>.sql`（人工执行，不引迁移框架） |
| 种子 SQL | `deploy/db/seed/<date>-<name>.sql` |
| 系统容器 | `backend/<system>/`（Maven 多模块）、`frontend/<system>/`（Vue3） |
| 接口默认 | `/api/v1` 前缀；返回体 `{code,msg,data}`；分页 `{records,total,size,current}` |
| 鉴权默认 | 纯网关鉴权，身份 `X-User-Id` / `X-User-Name`（例外须在设计中登记） |

## 4. 模板默认值投影（内容级绑定，来源 = L0 口径）

| 模板默认 | 来源口径（§1 节号） |
|----------|-------------------|
| 审计 5 列（create_time / update_time / create_by / update_by / deleted） | §6.4.5 |
| 16 位发号；`id` 出参 String | §6.4.5 / §6.4.7 |
| DDL：InnoDB + utf8mb4 + `IF NOT EXISTS` + 无物理外键 + `idx_/uk_` 命名 + 单表索引 ≤5 | §6.5 |
| 返回体 / 分页 / 校验失败 HTTP 200 + code | §6.4.1 / §5.3 |
| 幂等 `Idempotent-Key` / `X-Request-Id` | §6.11 |
| 权限标识 `system:resource:action` | §5.2 |
| 时间 `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai） | §2 / §6.2 |

## 5. 范例（exemplar）绑定

| 项 | 本项目 |
|----|--------|
| 范例产物集（唯一样板） | `docs/knowledge-base-design/` |
| 范例对照地图 | `references/exemplar-map.md`——**项目绑定内容**，移植时整体替换为新范例对照或删除 |

## 6. 模板绑定点清单（移植时逐段改；模板其余部分项目无关）

| 模板 | 绑定段 | 移植时改成 |
|------|--------|-----------|
| `README.md.tmpl` | 引言「契约口径以 CLAUDE.md 为准」；「相关既有材料」表的契约/登记处行 | 新项目契约文件名与登记处 |
| `domain-design.md.tmpl` | 引言「前置」；§1.2 密钥条款；§2 登记流程出处；§8 P0-0 登记项 | 同上 |
| `table-design.md.tmpl` | 引言口径行（审计/发号/索引 ≤5 的节号）；§1.3 落点表（`deploy/db/...`）；§4.1 短号出处；§6 执行约定 3–4 | 新项目口径节号、SQL 落点、短号规范 |
| `api-design.md.tmpl` | 引言契约口径/鉴权/路由行；§0 全局约定默认值；§0.1 登记处；§n+2 枚举名 | 新项目口径与登记处 |
| `er-diagram.html.tmpl` / `dataflow.html.tmpl` | 无绑定（页脚效力声明通用） | — |
| `prototype.html.tmpl` | token 子集注释「唯一取处处」行 | 新项目视觉 token 路径 |

## 7. 机器与工具绑定

| 项 | 本项目 | 移植动作 |
|----|--------|---------|
| `scripts/new-domain-design.ps1` 根发现 | 向上找 `CLAUDE.md` 定位仓库根 | 契约文件名不同：传 `-RootMarker <文件名>` 或改参数默认值 |
| 技能镜像同步 | `.agents/sync-skills.ps1` → `.trae/.codex/.agents` | 目标仓库无此机制：删 SKILL.md §7 |

## 8. 移植清单（新仓库按序做，skill 其余文件不动）

1. 改本文件 §1–7 为新项目取值（**主体工作**）。
2. `SKILL.md` 里的**范例名**（2 处：frontmatter `description`、§1 产物集表「范例文件」列）。
3. 按 §6 改模板绑定段。
4. `references/exemplar-map.md` 替换为新范例对照（或删除并在 SKILL.md §6 表注明）。**无范例/范例被删不影响产物生成**——可视化规范壳在 `assets/templates/`，ER/原型照常产出（范例只提升完成度）。
5. 脚本 `-RootMarker` 默认值（仅契约文件名不同才改）。
