# 2026-09-17 文档与 Git 整理记录

起点：`main` / `origin/main` / 远端 main 均为 `b476ce9`，主工作区干净。
范围：文档入口、现行规范冲突、Git 仓库卫生与迁移检出字节；不新增业务实现、不新增迁移版本唯一性门禁。

## 已确认的问题与文档整理

| 问题 | 处理 |
| --- | --- |
| README、CLAUDE、底座规则仍用“止于 W5”表述，遗漏 W5.5/F0 | 对齐阶段入口；CLAUDE 不再重复维护进度 |
| AGENTS 同时要求 Vue3 / React、原生 Layout / 双级侧栏、禁用 / 推荐 MapStruct | 删除失效实施要求，按既有 V2 决策整理；保留业务、权限、审计与阶段边界 |
| AGENTS 包路径仍为 legacy，UI Skill 指向不存在文件 | 改为实际 SmartAdmin 包与分层；以有效文档和已安装 Skill 为入口 |
| 2026-09-07 编码调查仍列为现行规范 | 添加历史标记，移入索引历史区，原始调查正文保留 |
| 索引重复、查找入口不明确 | 增加按任务导航与目录职责，保留设计/批准/验收及冻结证据原路径 |
| 选迁移号前祖先检查方向错误 | 最新远端 main 必须为本地 HEAD 的祖先或相等；落后/分叉先集成，无新增自动门禁 |

AGENTS 从 2,829 行整理为 1,994 行，主要移除失效的 React 生产力与旧映射规则。
这只是现有决策的文档收口，不构成新阶段或依赖引入批准。

## Git 实查

- 本地及远端只有 `main`；无 stash，无被忽略规则命中但仍跟踪的文件。
- `git fsck --full --no-reflogs` 退出 0；有 dangling 对象，不代表对象损坏，不执行强制 prune。
- 独立审查 worktree `D:/DevCaches/Codex/reviews/xsy-deepseek-ea5838a` 保留：HEAD `ea5838a`，相对 main 存在 47 个独有提交，且有未跟踪的 `PurchaseDemandSuggestionReviewTest.java`。不合并、不删除。
- 根目录没有 npm 项目，空 `package-lock.json` 属无效元数据；删除空 lockfile，实际 web/miniapp lockfile 保留。
- `.gitignore` 重复的 `.playwright-cli` 条目移除，原目录忽略规则保留。

## 上轮结论勘误

1. **引用根因未证实**：本次 fetch 与远端实时查询一致，未复现 stale ref。`refs/codex/**` 存在不能证明其工具导致引用回退；正常 `pack-refs` 会保留引用值。上轮“直接改 packed-refs”不能作为日常维护方法，见 [维护说明](git-and-document-maintenance.md)。
2. **SQL 换行分布应按实际字节与冻结清单判定**：所有 18 个迁移的 Git blob 都为 LF。清单要求 V3/V6/V7/V10/V11/V13/V14/V18 为 CRLF，其余 10 个为 LF。上轮“只有 V15/V17 为 LF、其余 16 个为 CRLF”的概括不适用于当前证据。
3. **存在不改 SQL 或清单的修法**：默认 `text eol=lf`，为上述 8 个历史文件单独指定 `text eol=crlf`。这样 Git blob 保持原样，检出字节符合冻结摘要。无需统一改写 SQL、重算清单或运行 Flyway repair。
4. 数据库的 fresh-DB 结果属于上轮证据，本次不重复迁移，也不把新克隆检查称为 fresh-DB 实测。

## 验证与交付

文档提交与 Git 检出修复分开交付。最终独立 clone 检查结果在仓库修复提交中补记。
