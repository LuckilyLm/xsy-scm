# 密码修改与管理员重置验证（2026-09-09）

本阶段实现了本人改密 `POST /api/auth/change-password` 与管理员重置 `POST /api/system/users/{id}/reset-password`。

实现边界：BCrypt、12–72 UTF-8 字节密码策略、大小写/数字/符号和无空白校验、旧密码校验、当前密码复用拒绝、乐观版本、共享安全锁、数据库管理员重验、审计、authVersion 递增和提交后会话撤销。管理员重置生成指定临时密码并强制首次改密；本阶段没有引入独立凭证表或密码历史表。

强制改密状态下，AccountVersionFilter 只放行 `/api/auth/me`、`/api/auth/csrf`、`/api/auth/change-password`、`/api/auth/logout` 和登录路径，其余已认证请求返回 40305。`/api/auth/me` 返回当前用户的 `version`，供改密请求使用。安全写在数据库锁内重验 authVersion；并发版本冲突仍返回 409。

独立复审核对了当前最终源码、Spring Security 接线、事务顺序、版本校验、HTTP/数据库测试和 SmartAdmin 只读参考，未发现密码阶段阻断问题。复审指出的编码路径风险已通过恢复路径白名单消除。

## 验证

专项命令（JDK 21，顺序执行）：

`mvn.cmd -Dlogging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR -Dtest=PasswordManagementHttpIT,PasswordManagementIT,LoginLockoutIT,SecurityConfigTest clean test`

结果：25 项通过，0 failure/error/skipped，BUILD SUCCESS。

专项覆盖真实 HTTP/MockMvc、PostgreSQL、Bean Validation、临时密码强制改密闭环、旧 JDBC cookie 失效、配置策略、回滚和并发版本。

全量 `mvn clean verify` 已运行，但最终结果为 354 项中 6 项失败、0 error；失败集中在既有 `RoleTransactionsIT`、`UserCrudIT`、`UserSecurityTransactionsIT`，未将其归因于密码专用测试。完整日志保存在 `.git/sdd/task-10-main-full.log`，专项日志为 `.git/sdd/task-10-main-focused.log`。

首次 HTTP 回归因测试仓库泛型类型编译失败，修正测试类型后重新运行；随后真实 HTTP 测试 8 项全部通过。测试期间未记录密码、hash、cookie 或 session 内容。

本阶段不声明整个 Sprint 4 完成；登录日志、系统操作日志、字典、业务权限和 React 仍待后续阶段。
