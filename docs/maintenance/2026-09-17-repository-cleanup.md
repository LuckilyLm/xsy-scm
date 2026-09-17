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
- 发现 6 个 2026-09-14/15 遗留的 `tmp_obj_*`，共 1,816,115 字节。确认无 Git 锁，逐个检查时间与哈希后隔离到 `.runtime/git-maintenance-2026-09-17/`；`manifest.json` 保留原路径、备份路径、长度和 SHA-256，6 项备份摘要全部一致。`git count-objects -vH` 的 garbage 从 6 降为 0。

## 上轮结论勘误

1. **引用根因未证实**：本次 fetch 与远端实时查询一致，未复现 stale ref。`refs/codex/**` 存在不能证明其工具导致引用回退；正常 `pack-refs` 会保留引用值。上轮“直接改 packed-refs”不能作为日常维护方法，见 [维护说明](git-and-document-maintenance.md)。
2. **SQL 换行分布应按实际字节与冻结清单判定**：所有 18 个迁移的 Git blob 都为 LF。清单要求 V3/V6/V7/V10/V11/V13/V14/V18 为 CRLF，其余 10 个为 LF。上轮“只有 V15/V17 为 LF、其余 16 个为 CRLF”的概括不适用于当前证据。
3. **存在不改 SQL 或清单的修法**：默认 `text eol=lf`，为上述 8 个历史文件单独指定 `text eol=crlf`。这样 Git blob 保持原样，检出字节符合冻结摘要。无需统一改写 SQL、重算清单或运行 Flyway repair。
4. 数据库的 fresh-DB 结果属于上轮证据，本次不重复迁移，也不把新克隆检查称为 fresh-DB 实测。

## 验证与交付

文档整理提交：`2d9e411 docs(repo): reconcile V2 rules and document repository maintenance`。
第二笔为 `.gitattributes`、`.gitignore`、根空 lockfile 清理及本记录，不混入业务代码。

### 迁移检出验证

先对 `b476ce9` 独立 clone，复现默认 Windows 检出失败；再创建三份独立 clone，放入待提交的
`.gitattributes` 并重新从索引生成迁移文件，用 Python `hashlib.sha256` 逐项校验冻结清单。

| 场景 | 清单通过 | 摘要通过 |
| --- | --- | --- |
| 修复前 clone，`core.autocrlf=true` | 3/8 | 16/30 |
| 修复属性，`core.autocrlf=true` | 8/8 | 30/30 |
| 修复属性，`core.autocrlf=false` | 8/8 | 30/30 |
| 修复属性，`core.autocrlf=input` | 8/8 | 30/30 |

失败复现涉及 f0、pg-closure、v3、w2、w5 共 5 份清单，不仅是上轮报告提到的 w5/f0。
三种配置下 SQL 与清单相对于 Git 索引的 diff 均为零；当前主工作区也为 8/8、30/30。
18 个迁移 SQL 的 Git blob 与 8 份冻结清单均未改动。新迁移默认 LF，仅 8 个历史文件保留 CRLF 例外。

注意：修改属性不会立即重写已存在且索引认为未变化的工作文件。本次在隔离 clone 中移除其自动生成的
SQL 副本后重新检出验证，没有删除主工作区 SQL。已有工作区若校验失败，应先保护本地修改再按文件重新检出，
不要对整个仓库运行 reset/clean。提交后还需用实际提交再做一次无补丁的新 clone 确认。

### 其他检查

- 本地 Markdown 链接检查：59 份文档，147 个文件链接，无失效目标（不检查外部 URL 与锚点）。
- `git diff --check` 通过。
- `git fsck --full --no-reflogs` 退出 0，444 个 dangling 对象保留，不执行强制清理。
- 主工作区保持 `main`；旧审查 worktree 未改动。
- 未运行后端、前端、数据库测试：业务源码、SQL blob 和数据库内容均不在本次改动范围。
