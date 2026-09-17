# 文档与 Git 维护

## 文档入口与职责

- [README](../../README.md)：工程结构、启动与验证命令。
- [文档总览](../00-文档总览与索引.md)：阶段进度、有效设计、批准与验收入口。
- [AGENTS](../../AGENTS.md)：当前 V2 工程约束；[SmartAdmin 底座规则](../../SMARTADMIN_REFERENCE_RULES.md)：底座与 SCM 边界。
- `target-design` 定义方案，`approval` 定义批准范围，验收报告记录当次执行证据。不能互相替代或因后续成功而改写历史失败。
- 旧调查和 legacy 规格保留来源与日期，标明失效的技术结论；不把它们重新列为现行规范。
- `*.sha256` 与前端源码哈希属于冻结证据，不随整理重算。历史文档中的旧路径和命令不作为当前操作入口。

## Git 日常检查

在仓库根目录执行：

```powershell
git status --short --branch
git branch -vv
git worktree list
git fetch origin
git rev-parse HEAD refs/heads/main refs/remotes/origin/main
git ls-remote origin refs/heads/main
git rev-list --left-right --count HEAD...origin/main
```

以 `ls-remote` 判断远端实时值，以 `rev-parse` 判断本地引用；reflog 是操作历史，不能替代远端查询。
正常提交前可领先远端；选择新迁移版本前必须包含最新远端 main，落后或分叉时先处理集成。
保留主工作区所在分支。按逻辑范围显式 `git add`，检查暂存 diff 后提交，推送后再核对引用。

如果远端跟踪引用不一致，先排除网络错误、远端新增提交和错误 refspec，再使用 Git 正规命令刷新：

```powershell
git fetch origin refs/heads/main:refs/remotes/origin/main
git rev-parse refs/remotes/origin/main
git ls-remote origin refs/heads/main
```

仍不一致时记录命令结果、时序及并发进程再排查。不要直接编辑 `packed-refs` 或手工双写 loose/packed refs；
Git 自身的引用更新带锁。loose ref 缺失、存在 `refs/codex/**` 或运行 `pack-refs` 本身都不是引用损坏的证据，
不能据此认定某工具为根因。

## 清理边界

- worktree 删除前检查未跟踪/未提交内容及未合入提交；存在独有工作时先保留。
- `git fsck --full --no-reflogs` 的 dangling 对象可以来自旧提交或工具快照，不能等同于损坏；关注 missing/corrupt/error 与退出码。
- 不为消除 dangling 输出运行强制 prune，不删除 `refs/codex/**` 或改写分支历史。
- Git 报告的 `tmp_obj_*` 需检查修改时间、锁与活动写入进程；只对确认陈旧的文件做带 SHA-256 清单的隔离备份，不删除有效对象。
- `tools/`、`.runtime/`、本机配置及参考工程凭据配置已被忽略。`tools/` 不随 clone 分发；不得在 README 中暗示克隆即可获得。
- 在 `xsy-scm-web/` 执行 npm；根目录没有 `package.json`，不维护空的根 lockfile。

## 迁移字节可复现性

冻结 SHA-256 记录的是当时工作区字节；Git blob 与检出文件的换行符可能不同。
修复必须同时检查 blob、实际字节、清单摘要和独立检出结果，不能仅依据终端显示。
本次仓库复核与跨配置 clone 证据见 [2026-09-17 整理与修复记录](2026-09-17-repository-cleanup.md)。

文档/检出属性整理只做链接、diff、哈希、Git 完整性与独立 clone 验证，不借此重复运行数据库迁移或业务回归。
