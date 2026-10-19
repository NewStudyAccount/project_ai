# 运维说明 · 对象物理清理前二次引用检查

> 对应 `docs/object-storage-design.md` §3.5 / §4.5；本说明登记 blog 侧要求，**不**实现调度。

## 物理清理（`deleted=1` → `remove`）

1. 筛选 `sys_file.deleted=1` 且 `update_time` 早于保留窗口（建议 ≥ 7 天）。
2. **二次引用检查**：对每个待删对象再次检查业务引用（封面 `cover_file_id`、正文/草稿 `content_file_id`/`draft_file_id` 等）；**仍被引用则跳过**并记入跳过清单。
3. 按 `storage_code` 解析服务商 → `exists` → `remove`。
4. 必须支持 **dry-run**；输出命中/跳过/成功/失败清单（不含 AK/SK）。

## 公共读

- 桶 `blog`（或生产对应桶）须 **公共读**：匿名 GET 200，匿名 PUT 失败。
- 公开地址：`public-base-url` + `/` + `objectKey`（见 `docs/test-env.md`）。

## 禁止

- 未比对库直接按桶列表批量删除；
- 删除 `deleted=0` 或仍被引用的对象；
- 建分布式定时清理（`CLAUDE.md` §2.2）。
