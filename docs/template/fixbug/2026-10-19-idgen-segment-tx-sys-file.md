# 2026-10-19 发号段与业务事务同回滚 → sys_file 主键冲突

> 范围：blog `IdGenerator`（`blog-framework`）/ `sys_file` 上传。  
> 现象：重复上传同一图片报 `Duplicate entry '2026101900000002' for key 'sys_file.PRIMARY'`。

---

## 1. 上传同文件第二次主键冲突

| 项 | 内容 |
|----|------|
| 现象 | `INSERT INTO sys_file` 失败，`Duplicate entry '2026101900000002'`（16 位发号 = 业务日 + 日序列 2） |
| 根因 | `IdGenerator.fetchSegment` 用默认 `PROPAGATION_REQUIRED` 的 `TransactionTemplate`，**取号段与业务 `@Transactional` 同事务**。业务回滚时 `sys_sequence.current_val` 一并回滚，但内存 `nextSeq` 已递增；后续请求继续发号或**重启/另一实例**再取段时，库内序列未前进，**重发相同 id** |
| 判别 | id 形态为 `yyyyMMdd` + 8 位日序列；冲突号偏小（如 …0000002）且偶发/重启后易复现 |
| 改动 | ① 取号段改为 **`PROPAGATION_REQUIRES_NEW`** 独立提交（业务回滚不回滚已取段，只产生空洞、不损唯一）；② 取段上限用 `LAST_INSERT_ID(current_val + step)` 原子更新；③ `ensureRow` 改 `INSERT … ON DUPLICATE KEY UPDATE id = id` |
| 验证 | 重启 `blog-service` 后：同一图片连传两次均成功且 id 不同；故意让一次保存失败后继续传，不再撞主键 |

**注意：** 冲突的 `2026101900000002` 行若已存在则属正常占用；新请求会从当前 `sys_sequence.current_val` 之后取号。若 `sys_sequence` 落后于已用 id，可人工 `UPDATE sys_sequence SET current_val = GREATEST(current_val, <max_used_seq>) WHERE id = 20261019`。

---

## 2. 发号唯一性约定（摘）

- 段式发号允许**空洞**（业务失败/取段未用完）；**禁止**与业务事务绑成同一回滚单位。
- 多实例部署时必须依赖 DB 段隔离（`FOR UPDATE` / 原子 UPDATE），不能各记各的内存序列。
