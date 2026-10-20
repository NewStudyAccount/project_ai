# 通用管理页设计 · Generic CRUD Page（P-List 全量形态）

> 状态：**设计稿 v1**
> 定位：`admin-ui-design.md` §3.1 **P-List** 的工程化展开——一份**配置驱动**的通用管理页设计，新业务列表页不再重复写骨架，只写配置。
> 视觉：沿用「极光 Aurora」Design Tokens（`admin-ui-design.md` §1 / `tokens-aurora.css`），**不新增本 token 体系之外的色值**。
> 适用：各系统 `{system}-admin` 的实体管理页（账号、分类、文章、文件、Client…）。
> 不覆盖：树管理页（P-Tree，见 `admin-ui-design.md` §3.2）、登录页（§4.5）、Dashboard（§4.1）。

---

## 1. 设计目标

1. **一份骨架，处处复用**：工具栏、表格、分页、表单弹窗的布局与交互逻辑只写一次，业务页只声明差异
2. **契约即配置**：列、筛选、操作、表单字段全部声明式；新增一个管理页 ≈ 一份 config + api 路径
3. **权限内建**：按钮渲染统一走 `hasPermission`，业务页不写权限判断代码
4. **同构可复制**：三系统（auth / user / blog）使用同一组件与配置结构，换系统只换菜单与数据

## 2. 页面结构（区块定义）

```text
┌─ .page-card（圆角 12 · 轻阴影）───────────────────────────────┐
│ A. page-header   页面标题 + 一句话说明（可选）                  │
│ B. .toolbar      左：主操作钮（新建…）  右：筛选区（可换行）     │
│ C. el-table      数据表（border；操作列固定右侧）               │
│ D. .pagination   el-pagination（total, prev, pager, next, sizes）│
│ E. D-Form 弹窗   新建/编辑共用（el-dialog + el-form）           │
└──────────────────────────────────────────────────────────────┘
```

| 区块 | 必选 | 规则（对齐 `admin-ui-design.md`） |
|------|------|----------------------------------|
| A page-header | 可选 | 标题 18px/600；说明 12px `--text-secondary`；业务页通常省略（菜单即语境） |
| B toolbar | 必选 | flex + gap 8；主按钮 `type="primary"` 胶囊、靠左、**无权限整钮不渲染**；筛选控件 8px 圆角；筛选 ≥ 3 个时换行或收起 |
| C table | 必选 | `el-table border`；表头浅灰 `#fafafa` 12–13px/500；操作列 `fixed="right"` 宽 140–200；长文本省略 + tooltip |
| D pagination | 数据 > 1 页 | `layout="total, prev, pager, next, sizes"`；`current` 从 1 起 |
| E D-Form | 新建有权限时内建 | 圆角 12；label 右对齐宽 90–100；底栏「取消 + 保存（primary 胶囊，提交中 loading）」；**不暴露 id 输入** |

## 3. 配置模型（PageSchema）

通用组件消费一份 TS 配置（约定类型名，落地以真实代码为准）：

```ts
// src/views/{module}/{page}.config.ts
export interface CrudPageSchema {
  /** 权限前缀，如 'blog:category' → 按钮权限自动拼 :create/:update/:delete */
  permissionPrefix: string;
  /** 接口封装（src/api 模块方法；业务组件不直接 axios） */
  api: {
    page:    (q: PageQuery & Record<string, unknown>) => Promise<PageResult<Row>>;
    create?: (data: Record<string, unknown>) => Promise<void>;
    update?: (id: string, data: Record<string, unknown>) => Promise<void>;
    remove?: (id: string) => Promise<void>;
  };
  /** 列定义（顺序即渲染顺序） */
  columns: ColumnDef[];
  /** 筛选定义（渲染进 toolbar 右侧） */
  filters?: FilterDef[];
  /** 主操作（默认仅「新建」；可扩展导入等） */
  primaryActions?: ActionDef[];
  /** 行内操作（默认内建 编辑/删除；可增删） */
  rowActions?: RowActionDef[];
  /** 新建/编辑表单字段（D-Form） */
  form: FormFieldDef[];
  /** 新建默认值 */
  formDefaults?: Record<string, unknown>;
}
```

### 3.1 ColumnDef（列）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prop` | `string` | 取值字段（与后端 VO 对齐；主键 `string`） |
| `label` | `string` | 表头文字 |
| `width` / `minWidth` | `number?` | 操作列/短列定宽；文本列 `minWidth` |
| `type` | `text \| enum \| datetime \| amount \| pill` | `enum`/`pill` 需配 `options`；`pill` 渲染软胶囊状态（§5 组件约定） |
| `options` | `{ value; label; pillTone? }[]` | 枚举映射；`pillTone ∈ success/warning/danger`（禁自造配色） |
| `copyable` | `boolean?` | 长标识（id、objectKey）显示复制图标 |
| `tooltip` | `boolean?` | 长文本省略 + hover 全文（默认长文本开） |

### 3.2 FilterDef（筛选）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prop` | `string` | 请求参数名（丢弃 `undefined/null/''`，由 request 封装保证） |
| `label` | `string` | 占位/label |
| `type` | `input \| select \| dateRange` | 回车或变更即查；`dateRange` 拆 `beginTime/endTime` |
| `options` | `{ value; label }[]` | select 专用；来源后端枚举，**禁止前端自造枚举值** |

### 3.3 ActionDef / RowActionDef（操作）

| 字段 | 说明 |
|------|------|
| `key` | 动作标识（`create` / `update` / `delete` / 自定义） |
| `label` | 按钮文字 |
| `permission` | 权限串；内建动作自动拼 `permissionPrefix:key`，自定义须显式全串（`system:resource:action`） |
| `confirm?` | 危险操作文案；非空则走 `ElMessageBox.confirm`，确认后执行 |
| `visible?` | `(row) => boolean` 行级显隐（如「启用」仅停用行显示） |

默认内建：主操作 `create`；行内 `update`（打开 D-Form 回显）、`delete`（confirm + `ElMessage` 成功 toast，成功后**刷新当前页**）。

### 3.4 FormFieldDef（D-Form 字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prop` / `label` | `string` | 对齐后端 DTO 字段 |
| `type` | `input \| textarea \| number \| select \| date \| switch` | 校验失败字段下方红字（Element 默认） |
| `rules` | `Rule[]` | Bean Validation 的前端镜像；必填、长度、格式 |
| `options` | `{ value; label }[]` | select 专用 |
| `hiddenWhen` | `(mode, row?) => boolean` | 新建/编辑差异字段（如编辑时锁定编码） |
| `span` | `1 \| 2` | 单列/整行 |

编辑模式：打开时**按行数据回显**；主键等不可变字段以 `disabled` 呈现。

## 4. 数据与契约（对齐 `CLAUDE.md`）

| 项 | 约定 |
|----|------|
| 请求 | 只调 `src/api` 封装（`http.get/post/put/delete`），解包后拿到 `data` |
| 分页入参 | `PageQuery { current, size }` + 筛选参数；`current` 从 1 起 |
| 分页出参 | `PageResult { records, total, size, current }` |
| 主键 | 出参/入参一律 `string`（Long 序列化契约） |
| 排序 | 如需 `orderBy`/`order`，须后端白名单；前端不自由拼接 |
| 401/403/429 | request 封装统一分流；本组件不处理 |

## 5. 状态与交互

| 场景 | 行为 |
|------|------|
| 初次加载 | 表格区 `v-loading`；**禁止**页面级白屏 loading |
| 空数据 | `el-empty`「暂无数据」；有 `create` 权限时给主操作入口 |
| 筛选变更 | 回车/变更即查，页码重置为 1 |
| 删除 | 二次确认（danger 文案）→ 成功 toast → 刷新当前页；当前页删空则回退一页 |
| 表单提交 | 按钮 `loading` 防重；成功关弹窗 + toast ≤ 2s + 刷新 |
| 错误 | toast 后端 `msg`；表单校验错误就地红字，不弹窗 |

## 6. 工程落点

```text
frontend/{system}-admin/src/
├── components/common/CrudPage.vue      # 通用骨架（唯一实现，各系统同构复制）
├── components/common/CrudForm.vue      # D-Form 内建实现
└── views/{module}/
    ├── XxxList.vue                     # ≈ 10 行：引入 CrudPage + 传 schema
    └── xxx.config.ts                   # 本页全部差异（§3 schema）
```

- `CrudPage.vue` 内部不改全局样式；全部走「极光 Aurora」token 与 §5 组件约定
- 页面仅允许通过 schema 定制；骨架级改动（列宽策略、弹窗宽度等）在组件内统一裁决
- **P-Tree / D-Panel 不套用本组件**（树表交互差异大，维持 `admin-ui-design.md` 原模式）

## 7. 落地自检

- [ ] 页面文件 ≤ 30 行（不含 config）；差异全在 `xxx.config.ts`
- [ ] 色值/圆角/间距未新增，全部「极光 Aurora」token
- [ ] 主按钮与行内操作权限串符合 `system:resource:action`；无权限的钮**不渲染**（不是 disabled）
- [ ] 主键、分页、枚举值均与后端契约一致，无前端自造
- [ ] 删除有二次确认；成功后刷新当前页；提交按钮防重
- [ ] `npm run lint && npm run type-check` 通过

## 8. 样张

交互样张（纯 HTML 演示配置驱动效果，mock 数据）：[`generic-admin-page-sample.html`](./generic-admin-page-sample.html)。样张仅演示视觉与交互，不作为工程契约；工程契约以本文与 `admin-ui-design.md` 为准。
