# 项目规范（指针）

> 本文件**不是**规范正文。项目级稳定契约与工程约定的**唯一事实来源**是根目录 [`CLAUDE.md`](CLAUDE.md)。

- 所有 AI 编码工具（Cursor、Claude Code、Windsurf、Codex、Trae、MimoCode 等）与人类协作者，在编写、修改代码前必须先读取并遵循 `CLAUDE.md` 全文。
- 本文件不再复制规范内容（防双源漂移）；规范修订**只改 `CLAUDE.md`**。
- OpenSpec（SDD）工作流入口见 `CLAUDE.md` §8：`.claude/commands/opsx/`（Trae 侧 `.trae/skills/openspec-*`）。
- 组件接入信息（IP/端口/测试账密）查 `docs/test-env.md`；错误码登记（`1xxxx`/`3xxxx` 固定码值、`2xxxxx` 系统号）见 `docs/error-code-ranges.md`；组件版本基线（各系统版本唯一取用处）见 `docs/version-baseline.md`。
