# 角色菜单授权接续验证（2026-09-09）

本次接续范围为 Sprint 4 当前未完成的角色菜单授权阶段。Codex 保留并接续了 Claude 已实现的生产代码和测试；Codex 保存的运行日志与源码哈希属于提交前实现/验证证据，没有修改生产代码或测试逻辑。其余 Sprint 4 阶段不在本次完成声明内。

## 当前实现与审查

- GET `/api/system/roles/{id}/menus` 使用 `system:role:list`；POST 使用 `system:role:assign-menus`，新增 V23 权限迁移。
- 最多 100 个菜单 ID，按角色 version 原子全量替换；保留关系 ID、软移除、重加生成新历史关系。
- 共享安全事务锁内重验当前操作者，检查委派菜单覆盖、祖先结构和可见性；菜单不授予 API 权限。
- 角色 version、用户 authVersion、操作审计同事务提交；提交后逐用户容错撤销会话，旧会话仍由版本校验拒绝。
- 提交前接续记录曾写为“独立源码审查 Spec PASS / Quality APPROVE”，但当时保存的 `.git/sdd/task-8-codex-*` 只证明运行验证和源码哈希，不能自行证明独立审查；该表述已在提交后复核中纠正。
- 提交 `58b0ac1` 后的独立只读审计实际核查了实现、相关测试、SmartAdmin 只读参考及保存的验证证据，初始正式结论为 Spec PASS、Quality CHANGES_REQUIRED，唯一待办是修正证据归属。修正文档后经独立只读复核 PASS，Quality 门禁关闭；本文不将后置审计追溯为提交前证据。

## 本次新验证证据

在同一份源码上顺序执行，没有并行 Maven 构建：

1. `mvn.cmd -Dtest=RoleMenuGrantsIT,RoleMenuGrantTransactionsIT,RoleCrudIT clean test`：46 项通过，0 failure/error/skipped，退出码 0。
2. `mvn.cmd clean verify`：348 项通过，0 failure/error/skipped，JAR 打包成功，退出码 0。
3. 验证前后 412 个 `xsy-scm-server/src` 文件 SHA-256 清单完全一致。
4. `git diff --check` 通过；提交前另检查暂存差异。

本地证据位于 `.git/sdd/`：`task-8-codex-focused-jdk21.log`、`task-8-codex-full-verify.log`、`task-8-codex-before.sha256`、`task-8-codex-after.sha256`、`task-8-codex-evidence.log`。本报告替代历史并行运行及中途添加用例时的最终验证声明，不改写原日志。

首次运行因终端默认 JDK 17 不支持 release 21 而编译失败，未执行测试；失败日志为 `task-8-codex-focused.log`。确认原因后仅在验证进程内设置 `JAVA_HOME=D:\Java\JDK21`，未修改全局环境或项目配置。

## 证据边界与下一步

数据库集成测试覆盖真实 PostgreSQL 锁等待、同版本竞争、回滚以及会话清理失败后的旧 cookie 401。会话测试使用 repository 建立认证会话，不是浏览器登录验收；本次未修改前端，因此未运行前端或 Playwright 验收。

原计划记录的历史管理员 marker 信任、既有用户会话清理异常、测试 schema best-effort 清理等后续项仍保留。下一阶段按计划进入登录失败计数与锁定，随后密码、日志、字典及业务权限；后端完成后再开始 React。
