# Domain docs

工程技能在探索代码库时如何消费本仓库的领域文档。

## 探索前先读这些

- 仓库根目录的 **`GLOSSARY.md`**（由 `/domain-modeling` 按需创建；不存在时跳过）。
- **[docs/decisions.md](../decisions.md)**：本仓库唯一有效的决策事实源。业务、架构与权限不变量都在这里，按主题分节维护。
- **[AGENTS.md](../../AGENTS.md)**：协作硬约束，其中「用户界面用词」一节是已生效的术语规则（SPU → 商品、SKU → 商品规格等）。

上述文件不存在时**静默跳过**：不要提示缺失，也不要主动建议先创建。

## 不建 `docs/adr/`

`docs/README.md` 的文档维护规则明确：**新规则合并进 `decisions.md`，不再另建 ADR、设计副本或「最新版」文档**。所以：

- 不要创建 `docs/adr/` 目录，也不要写 `0001-xxx.md` 这类编号决策文件。
- `/domain-modeling` 敲定的决策，改为在 `docs/decisions.md` 对应主题下追加条目，沿用现有条目的写法（一条规则一句话，必要时补日期与原因）。
- 技能模板里提到 ADR 的地方，在本仓库一律理解为「`docs/decisions.md` 的一条」。

## 文件结构

本仓库是单上下文（single-context）：

```
/
├── GLOSSARY.md               ← 术语表，按需创建
├── AGENTS.md                 ← 协作规则（含用户界面用词）
├── docs/
│   ├── decisions.md          ← 决策事实源（替代 ADR）
│   ├── status.md             ← 交付状态与验证证据
│   └── agents/               ← 本目录：技能配置
└── xsy-scm-server/ xsy-scm-web/ xsy-scm-miniprogram/
```

出现 `GLOSSARY-MAP.md` 时才视为多上下文（multi-context），届时每个上下文各自持有 `GLOSSARY.md`。

## 使用术语表的词汇

输出中出现领域概念时（issue 标题、重构提案、假设、测试名），使用 `GLOSSARY.md` 定义的术语；`GLOSSARY.md` 与 `AGENTS.md` 的用词规则冲突时，以 `AGENTS.md` 为准。

需要的概念不在术语表里，是一个信号：要么你在发明项目不用的语言（重新考虑），要么确实存在缺口（记下来交给 `/domain-modeling`）。

## 标出决策冲突

输出与 `docs/decisions.md` 的既有条目矛盾时，明确标出来，而不是悄悄覆盖：

> _与 decisions.md 的「库存流水是追加事实」冲突，但值得重开讨论，因为……_
