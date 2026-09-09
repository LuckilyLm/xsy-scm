# 登录失败计数与锁定接续验证（2026-09-09）

## 范围与来源

本次承接 Claude 中断的登录锁定阶段，从 `.claude/worktrees/agent-a34b6bdad612476f9` 同步 8 个后端文件至 `feature/sprint4`。两个工作区基线均为 `e6c67dd`；同步前检查目标文件无未提交修改，同步后逐文件哈希与审查版本一致。未修改 Claude 的最终生产逻辑和测试，原 worktree 保留。

- 数据库事务内串行更新登录失败计数，达到阈值后锁定并递增 authVersion；事务返回后再抛认证异常，避免正常失败计数被回滚。
- 默认连续 5 次失败锁定 15 分钟，可通过 `XSY_LOGIN_FAILURE_THRESHOLD` 和 `XSY_LOGIN_LOCK_DURATION` 覆盖，配置须为正数/正时长。
- 未过期锁定不继续累计；过期后输错密码从 1 计数，成功登录清零并记录登录时间。
- 依照计划 §6.8，错误密码、未知、已删除、禁用和锁定账号统一返回 401 / 40101 / “用户名或密码错误”；不可认证账号执行 dummy BCrypt。
- 锁定提交后撤销会话，清理失败仍由 authVersion 校验拒绝旧会话。
- 未引入 V24，未修改已应用迁移；复用 V12 已有字段。本次没有实现密码修改、日志模块、字典或 React。

## 本次独立静态审查

提交前由独立子代理 `/root/review_login_lockout` 只读审查上述 8 文件及必要关联实现，结论 PASS，无阻断性发现。审查核对了事务、Provider 接线、共享锁顺序、过期逻辑、配置、统一错误、会话失效，并只读对照 SmartAdmin `SecurityLoginService`。

审查者未运行测试；下面的测试由主协调者独立执行。审查结论及 8 个 SHA-256 保存为 `.git/sdd/task-9-codex-independent-review.log`，同步清单为 `task-9-codex-import.sha256`，主协调者已逐项比较一致。本记录不是把后置审计追溯为提交前审批，也不复用历史测试结果作为本次通过证据。

非阻断建议：Provider 的 afterCommit 会话清理异常当前静默忽略，后续可增加不包含账号、凭据或异常详情的固定警告日志；已验证异常不会破坏锁定提交及旧会话版本兜底。

## 当前分支顺序验证

仅在验证进程设置 `JAVA_HOME=D:\Java\JDK21`。两个命令均附加 `-Dlogging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR`，抑制测试切片自动生成的开发密码日志；不改变认证逻辑或全局环境。

| 命令 | 结果 |
| --- | --- |
| `mvn.cmd -Dtest=LoginLockoutIT,SecurityConfigTest,UserCrudIT,UserSecurityTransactionsIT clean test` | 25 项通过，0 failure/error/skipped，退出码 0 |
| `mvn.cmd clean verify` | 353 项通过，0 failure/error/skipped，JAR 打包成功，退出码 0 |
| 验证前后源码 SHA-256 | 414 个源码与资源文件完全一致 |
| `git diff --check` | 退出码 0；提交前另外检查暂存差异 |

本次专项没有单独包含 FlywayMigrationIT，因此为 25 项，而非历史 worktree 日志的 52 项；Flyway 的 27 项迁移测试包含在本次全量 353 项中。

本地原始日志及清单位于 `.git/sdd/`：`task-9-codex-focused.log`、`task-9-codex-full-verify.log`、`task-9-codex-before.sha256`、`task-9-codex-after.sha256`、`task-9-codex-evidence.log`。

## 验证边界

LoginLockoutIT 使用真实 PostgreSQL，通过 MockMvc 登录接口验证失败持久化、配置覆盖、锁定时正确密码仍被拒绝、过期后错误计数及成功清零、并发累计和事务回滚。JDBC 会话用 repository 建立，验证锁定前 `/api/auth/me` 为 200，清理异常后会话行仍在但旧 cookie 为 401 / 40103。此为后端 HTTP/数据库集成验证，不是浏览器登录验收；未单独枚举运行时 Provider 列表。未修改前端，未运行前端及 Playwright。

下一阶段为修改/重置密码，之后继续登录日志、系统操作日志、字典和业务权限。整个 Sprint 4 尚未完成。既有管理员 marker 信任及测试 schema best-effort 清理等计划后续项继续保留。
