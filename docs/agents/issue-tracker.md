# Issue tracker：本地 Markdown

本仓库的 issue 与 spec 以 markdown 文件形式存放在 `.scratch/`。选择本地文件而不是 GitHub Issues 的原因：本机没有 `gh` CLI，且仓库既有的工作记录方式就是 `docs/status.md` 与 `docs/decisions.md`。

## 目录约定

- 一个特性一个目录：`.scratch/<feature-slug>/`
- spec 写在 `.scratch/<feature-slug>/spec.md`
- 实现工单一个文件一条：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 起编号，**不要**把所有工单合并成一个文件
- 每条 issue 顶部附近用 `Status:` 行记录状态
- 评论与讨论追加到文件末尾的 `## Comments` 标题下

## 技能说「发布到 issue tracker」时

在 `.scratch/<feature-slug>/` 下新建文件（目录不存在就创建）。

## 技能说「取相关工单」时

直接读被引用路径的文件。用户通常会直接给出路径或编号。

## 与仓库既有文档的分工

- `.scratch/` 只放**本次工作的分解与草稿**，不入版本库（见 `.gitignore`）。
- 需要长期留存的结论写进 `docs/decisions.md`；交付状态变化写进 `docs/status.md`。
- 已完成的一次性计划按仓库规则删除，从 Git 历史追溯；不要沉淀成第二份事实源。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 是一个文件，每个工单对应一个 **child** 文件。

- **Map**：`.scratch/<effort>/map.md`（Notes / Decisions-so-far / Fog 正文）。
- **Child 工单**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 编号，正文写问题。`Type:` 行记录工单类型（`research` / `prototype` / `grilling` / `task`）；`Status:` 行记录 `claimed` / `resolved`。
- **阻塞**：顶部附近用 `Blocked by: NN, NN`。列出的文件全部 `resolved` 后该工单才算解锁。
- **前沿（frontier）**：扫描 `.scratch/<effort>/issues/`，找打开、未阻塞、未认领的文件；编号最小者优先。
- **认领**：动工前先置 `Status: claimed` 并保存。
- **解决**：在 `## Answer` 标题下追加答案，置 `Status: resolved`，再把上下文指针（要点 + 链接）追加到 `map.md` 的 Decisions-so-far。
