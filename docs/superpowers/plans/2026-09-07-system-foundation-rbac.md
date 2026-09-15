# Sprint 4：System Foundation / RBAC 实施计划

> **最新接续状态（2026-09-09）：** 密码修改与管理员重置已完成实现及专项验证。独立复审未发现密码阶段阻断问题；JDK 21 下专项 25 项通过（0 failure/error/skipped）。新增强制改密恢复路径、本人版本返回、锁内 authVersion 重验和真实 HTTP/JDBC 回归。全量 `clean verify` 已运行但 354 项中 6 项既有用户/角色事务测试失败，未声明全量通过；详见 [密码管理验证](../verification/2026-09-09-password-management-verification.md)。本阶段提交不包含前端，下一步继续处理日志/字典前的既有回归与边界收敛。

> **最新接续状态（2026-09-09，优先于下方历史状态）：** 登录失败计数与锁定阶段已完成实现、独立静态审查及当前分支验证。从 Claude worktree 同步 8 个最终文件，未引入 V24、未修改已应用迁移。提交前独立静态审查 PASS；JDK 21 顺序 clean focused 25 项与全量 clean verify 353 项均 0 failure/error/skipped、退出码 0，JAR 打包成功，验证前后 414 个源码/资源文件哈希一致。审查与同步的 8 文件哈希逐项匹配。详见 [登录锁定接续验证](../verification/2026-09-09-login-lockout-verification.md)。会话清理异常静默为非阻断可观测性后续项，authVersion 兜底已验证。下一阶段为修改/重置密码；不提前实现日志、字典或 React，不 merge、不 push，保留原 worktree 及无关文档。

> **接续状态（2026-09-09，优先于下方历史状态）：** 角色菜单分配已完成本阶段实现与验证。Codex 保存的实现、测试、运行日志及源码哈希属于提交前实现/验证证据：JDK 21 下顺序运行 clean focused 46 项及全量 clean verify 348 项，均 0 failure/error/skipped、退出码 0，JAR 打包成功；验证前后 412 个源码/资源文件 SHA-256 完全一致。提交 `58b0ac1` 后的独立只读审计初始结论为 Spec PASS、Quality CHANGES_REQUIRED，唯一待办是修正原验证记录无法自行证明的“提交前独立审批”措辞；修正后独立只读复核 PASS，Quality 门禁关闭，且后置审计不追溯为提交前证据。证据与限制见 [角色菜单授权接续验证](../verification/2026-09-09-role-menu-grants-verification.md)。本阶段提交只包含角色菜单授权相关后端文件及计划/验证记录，排除参考资料和商城草案，不 merge、不 push。下一阶段为登录失败计数与锁定；尚未完成整个 Sprint 4，也未提前开始 React。

> **状态（2026-09-08）：** 用户 CRUD 已实现，规格审查 PASS、质量审查 PASS；审查修复 RED 为 22 项中 6 项失败，GREEN 为 22 项全通过，全量 `mvn test` 为 216 项通过（0 failure/error/skipped）。证据保存在本地 `.git/sdd/task-1-review-{red,green,full-test}.log`，本次提交整理不重复运行未变更测试。提交边界为用户 CRUD 与其依赖的既有未提交认证/Session/RBAC schema 基础的单一后端提交，不伪造未跟踪基础文件的历史版本；包含相关依赖、测试及计划/约定文档，不包含小程序商城草案。用户阶段已提交为 `30595cd`。部门 CRUD 已完成，规格审查 PASS、质量审查 APPROVE；初始 CRUD RED 9 项失败，focused GREEN 32 项通过，全量 `mvn test` 233 项通过（0 failure/error/skipped），证据为本地 `.git/sdd/task-2-{red,green,full}.log`。并发 RED 是移除锁的实验验证，不是原始 TDD RED。部门阶段独立提交包括 17 个后端文件及本计划状态，不包含商城草案；提交整理不重复运行未变更测试。P3 后续补充直接 A→B/B→A 对向移动并发测试（审查确认现有共享锁正确串行化）。部门阶段已提交为 `52ac0dc`。角色 CRUD 已完成（含 P1 角色启用提权修复），独立规格审查 PASS、质量审查 APPROVE；原始 CRUD RED 8 项失败后 GREEN 8 项通过，原始事务测试为补充测试，无原始 RED。P1 RED 3 项失败、保留角色 RED 1 项失败，修复后 focused GREEN 38 项、全量 252 项通过（0 failure/error/skipped），证据为本地 `.git/sdd/task-3-p1-{red,reserved-red,green,full}.log`。角色阶段独立提交 17 个后端文件及计划状态，不修改已应用 V18，不重复运行未变更测试。角色阶段已提交为 `c2e45bb`。权限 CRUD/查询已完成，独立规格审查 PASS、质量审查 APPROVE；最新有效证据为 `.git/sdd/task-4-activation-red-2.log`（14 项中 1 项失败、0 error，grant-free 委派启用预期 200、实际 403）、`task-4-activation-green.log`（14 项通过）及 `task-4-activation-full.log`（266 项通过，0 failure/error/skipped，BUILD SUCCESS；外层 wrapper 在 Maven 完成后超时）。早期 legacy-red/full264 证据已被替代，并发/cache 测试为补充覆盖，不声称原始 RED。权限阶段独立提交 17 个后端文件及计划状态，不修改已应用 V19，不重复运行未变更测试。权限阶段已提交为 `262b014`。菜单 CRUD 已完成，独立最终规格审查 PASS、质量审查 APPROVE（协调者确认三个阻塞项全部关闭）；冻结证据 `.git/sdd/task-5-safety-green-frozen.log` focused 15 项、`task-5-full-safety-frozen.log` 全量 281 项通过，均 0 failure/error/skipped、BUILD SUCCESS。最终 fixture/assertion 调整后的冻结全量结果替代早期 281 项中 1 项 fixture collision error 的失败运行；`task-5-diff-safety-frozen.log` exit_code=0，协调者最终 diff 检查亦通过。菜单阶段独立提交范围为 18 个后端文件（2 修改、16 新增）及本计划；提交整理不修改生产代码/测试、不重复运行未变更测试，排除只读参考资料和商城草案，不恢复外部已消失的 Word 临时文件。菜单阶段已提交为 `c7e2628`。用户角色分配已完成，协调者确认审查者独立检查最终 service、两个 IT 和实际日志后正式最终 Spec PASS / Quality APPROVE；先前过早报告已撤回并被本次最终审查替代。最终 `.git/sdd/task-6-patch-focused-final.log` 15 项、`task-6-patch-full-final.log` 296 项通过，均 0 failure/error/skipped、BUILD SUCCESS，替代 focused12/full293；`task-6-patch-diff-check.log` 仅换行警告，协调者确认 exit0。原始读取/写入/委派/保留角色移除 RED 分别见 `task-6-red-2.log`、`task-6-write-red.log`、`task-6-delegation-red.log`、`task-6-reserved-red.log`（各 1 failure/0 error）；禁用目标预期成功断言在移除限制前产生 `task-6-disabled-assertion-red.log` 1 failure/0 error，早期直接异常不是断言 RED。事务/竞争/session 覆盖为补充 first-green，不声称原始 RED。阶段范围仅 10 个后端文件（1 修改、9 新增）及本计划，不修改生产/测试代码、不重复运行未变更测试。用户角色阶段已提交为 `a0e2fc4`。角色权限分配已完成，协调者报告独立实际源码审查 Spec PASS / Quality APPROVE；整理时亲自核对 `.git/sdd/task-7-verified-frozen-{focused,full,evidence}.log`，clean focused22、full310 均 0 failure/error/skipped、BUILD SUCCESS，before/after 哈希清单 404 文件一致且当前文件逐一匹配。`task-7-verified-frozen-diff.log` 只有换行警告，补充 `task-7-coordinator-ready-diff-check.log` 明确记录 exit0。审查者把 8 个 inline 测试误计为 9 的说法已完全撤回；原始22/310日志有效，额外 clean/hash 验证源于消息时序而非修复陈旧字节码。原始 `task-7-red.log` GET 预期200实际403、`task-7-red-admin-marker.log` 预期403实际200，各1 failure/0 error；其余事务/竞争/session覆盖为补充first-green。范围11个后端文件（2修改9新增）及本计划；提交整理不改生产/测试、不重跑未变更测试。下一阶段仅角色菜单分配，不提前开始前端。
>
> **角色权限分配合同与未解决安全事项：** GET `system:role:list`、POST `system:role:assign-permissions`；最多100项、versioned原子全量替换，允许未删除的禁用角色，最终权限必须启用且未删除；保留关联ID、软移除、重加新历史行。共享安全锁内重验数据库actor，影响用户authVersion与角色version/审计原子提交，提交后逐用户容错清理；真实PermissionService双顺序pg_locks waiter及JDBC旧cookie401兜底，不是登录验收。新writer拒绝最终集合包含 `system.administrator`，即使操作者是管理员；但AuthorityRules和身份读取器对历史/手工marker的信任是未解决的既有安全后续项，本阶段未修复reader，既有角色启用/管理员用户角色分配仍可能暴露异常手工保留marker数据。此前用户清理异常、部门对向并发P3、schema best-effort清理及菜单证据限制均保留。
>
> **用户角色分配合同与证据边界：** GET 使用 `system:user:list`，POST 使用 `system:user:assign-roles`；最多 100 项的 versioned 原子全量替换，保留活跃关联 ID、软删除移除项、重新分配新增历史行。允许未删除的禁用目标，不能借授权修改 status/lock/administrator；请求角色须启用且未删除。共享安全锁先于角色及升序用户行锁，锁内重验当前数据库操作者与委派权限/导航覆盖，保护保留角色和自身权限；变化时 version/authVersion/安全审计同事务提交，提交后 RuntimeException 容错撤销 session，无变化不产生版本/审计/清理副作用。真实 RoleService 与 grant 两种先后顺序以 PID 特定 pg_locks waiter 验证；真实 repository 创建的已认证 JDBC session 先 HTTP200，清理失败仍保留行且提交成功，旧 cookie 再 HTTP401，此为会话版本兜底集成测试，不是登录流程验收。既有用户清理异常、JVM 共享自有随机 schema best-effort 清理、部门对向移动 P3 及菜单原始 gate RED/专用 stale HTTP/深度副作用断言限制继续保留。
>
> **菜单安全合同与证据边界：** 菜单只负责导航，权限仍是 API 授权事实；DIRECTORY/MENU 使用增量 V20 转换 PAGE 并保留 ID/关联，受控 routeKey/path 注册表不接受可执行元数据。树迭代校验后过滤，父节点被过滤时不提升子节点。64 层是 JSON 嵌套技术安全边界，不是业务分类规则；校验先于实体变更、失效及审计，旧深树可通过不受深度门禁的 list/detail/status/delete 及修复重挂处理，不相关根不阻止修复。保留导航关联包括未删除的禁用角色和后代关联；委派新增原先不存在的权限门禁属于收紧，允许，但移除/替换已有门禁、改变保留导航身份或重新激活/显示被保护。共享安全锁内重验数据库操作者，受影响 authVersion 与安全审计原子提交，提交后逐用户 RuntimeException 容错清理。`task-5-gate-red.log` 恢复旧谓词实验为实现后 1 项/0 failure/1 error，不能称为原始或断言 RED，原始 gate TDD 缺口保留；尚无菜单专用 stale HTTP fallback 断言，深度拒绝直接断言 version，未逐一直接断言全部 audit/session 无副作用。继承测试基类使用 JVM 共享随机生成且测试自有的隔离 schema，shutdown hook 清理 best-effort，异常退出可能遗留；既有用户清理异常及部门对向移动 P3 后续项保留。
>
> **权限安全合同：** 新建编码采用不可变冒号格式，初始 DISABLED；保留权限以系统标记、`system:` 命名空间及明确旧权限注册表识别，不把所有旧点号编码误判为核心。允许无保留授权权限的委派启用；只要未删除角色保留未删除授权关联，无论角色是否启用或是否分配用户，启用均须防止提权。安全写先共享事务锁，再资源/升序用户行锁，锁内重验当前数据库操作者可用性、authVersion、管理员标记和权限；授权变更在同一事务内递增受影响用户 authVersion 并写入含不可变 actor ID 的安全审计，提交后清理会话，版本过滤兜底且清理异常不阻止其他用户。既有用户会话清理异常、schema best-effort 清理及部门对向移动并发测试 P3 后续项保留。
>
> **角色启用安全合同：** 先获取共享安全事务锁，再按角色 → 用户 ID 升序加锁；启用保留授权属于权限扩张，必须在锁内重读当前数据库操作者状态、authVersion、锁定状态、管理员标识与实际权限，不信任旧会话权限。非管理员须当前持有 `system:role:status` 且覆盖该角色将激活的全部权限，不能激活保留/系统角色；身份变更递增受影响用户 authVersion，提交后撤销会话。既有用户 afterCommit 异常、schema best-effort 清理与部门对向移动并发测试 P3 后续项继续保留。
>
> **用户阶段已知后续项：** afterCommit 会话清理遇到非 DataAccessException 的意外异常仍可能把已提交操作误报为失败；测试隔离 schema 清理是 best-effort。后续部门/角色/授权/密码等安全写事务必须先获取 `pg_advisory_xact_lock(20260908, 1)`，再按部门 → 用户顺序加行锁；部门禁用/删除在同一事务内检查子部门和用户。影响身份/授权的变更递增受影响用户 `authVersion`，提交后撤销旧会话，保留版本检查兜底。
>
> **续作优先级（覆盖下文旧实施顺序及冲突草案）：** 用户 CRUD → 部门 CRUD → 角色 CRUD → 权限 CRUD/查询 → 菜单 CRUD → 用户角色分配 → 角色权限分配 → 角色菜单分配 → 登录失败计数与锁定 → 修改/重置密码 → 登录日志 → 系统操作日志 → 字典 CRUD → 细粒度业务权限 → React 认证 → React 动态菜单 → React 路由守卫 → React 按钮权限 → React 系统管理页面 → Playwright Sprint 4 验收。
>
> **当前请求优先的合同：** 权限采用 `system:user:list`、`purchase:order:list`、`purchase:receipt:confirm`、`inventory:stock:list` 等冒号编码；既有合同如需调整，使用增量迁移，不修改已应用 migration。菜单采用 DIRECTORY/MENU，按钮消费独立 ACTION 权限；如已有 PAGE 数据，通过增量迁移兼容调整。部门删除或禁用均检查子部门和用户。密码变更撤销所有旧会话。锁定返回明确错误。系统日志不落完整请求体，保留领域日志。后端管理与授权事务测试通过后才开始 React。
>
> **提交门禁：** 每阶段相关测试、`git diff --check`、计划状态更新和审查后小步 commit；不自动 merge main，不 push。保留并辨识开始时已有的未提交认证基础改动。

**Goal：** 在保留当前 Java 21 + React 模块化单体和既有业务行为的前提下，为 xsy-scm 建立登录、用户、部门、角色、菜单、权限、按钮/API 授权、系统操作日志、登录日志、字典及 React 动态菜单与权限控制。

**Architecture：** 后端使用 Spring Security 原生 `Authentication` / `SecurityContext` 和 Spring Session JDBC 持久化 `HttpSession`，浏览器通过安全 Cookie 持有 session id；认证放在 `auth` 域，系统资料与 RBAC 放在 `system` 域。权限作为独立安全资源，菜单只负责导航。React 保留现有双侧栏布局，以受控 route registry 将服务端 menu metadata 映射为 lazy routes，并按 Spring Security SPA 指南处理 CSRF。现有领域日志继续作为业务事实审计，新增登录安全日志和系统管理操作日志。

**Tech Stack：** Java 21、Spring Boot 3.5.13、Spring Security、MyBatis-Plus、Flyway、PostgreSQL、React 19、React Router 7、TanStack Query、Axios、Ant Design/ProComponents、Vitest、Playwright。

**Reference：**

- `AGENTS.md`
- `SMARTADMIN_REFERENCE_RULES.md`
- `CLAUDE.md`
- `CONTEXT.md`
- `docs/architecture/smartadmin-code-conventions.md`
- `docs/architecture/xsy-vs-smartadmin-conventions.md`
- `project-reference-examples/xsy-scm/**`（只读上游参考）

## 1. 调查结论

### 1.1 当前项目

- `xsy-scm-server/pom.xml` 尚无 Spring Security、Sa-Token、JWT 或 Redis。
- `V1__create_sprint1_schema.sql` 已有最小 `sys_user`，只有用户名、显示名、状态、版本、软删除及审计字段；没有密码或 RBAC。必须增量扩展，不能重建导致 ID 变化。
- 未发现正式 auth、部门、角色、权限、菜单、字典、登录日志或系统操作日志模块。
- 已有订单、协议价、采购等领域日志，仍以临时 actor `SYSTEM` 为主。
- API 固定使用 `{code,message,data}`，分页使用 `PageData`，业务错误使用 `BusinessException(ErrorCode)`。
- React 当前路由和双侧栏菜单完全静态；Axios 已统一解包响应，但无 Cookie credentials、CSRF、401/403、认证状态和权限基础。

### 1.2 SmartAdmin 参考

- 登录真实链路：Controller → LoginService → Employee/Security services → Sa-Token → LoginManager → Role/Menu → LoginLog。
- 使用 Sa-Token 1.44.0 + Redis、自定义 MVC interceptor、`StpInterface` 和 `@SaCheckPermission`。
- Employee—RoleEmployee—Role—RoleMenu—Menu/Function Point 构成 RBAC；Menu 同时承载前端和 API 权限，导航与授权耦合较强。
- Manager 只在事务、批处理和缓存场景出现，不是必经层。
- 日志分登录日志、AOP 操作日志、DataTracer；字典分 Dict/DictData。
- 适合借鉴完整能力边界，不适合照搬万能密码、GET 修改、反射复制、SQL 字符串重写数据权限、全请求日志和依赖全集。

## 2. 范围

### 2.1 本 Sprint 目标

- 管理端账号密码登录、当前用户、退出、修改密码；
- 用户、部门、角色、菜单、权限和字典管理；
- 用户—角色、角色—菜单、角色—权限授权；
- React 动态菜单和路由访问控制；
- React 按钮/操作权限控制；
- 后端 API 权限；
- 登录日志、系统操作日志和权限变更审计；
- 将现有领域日志的 actor 从固定 `SYSTEM` 渐进接入当前用户；
- 风险相称的后端、前端和浏览器测试。

### 2.2 非目标

- OAuth2/OIDC、企业 SSO、LDAP、短信或邮件二次认证；
- 客户商城账号、供应商账号、司机或设备账号；
- Redis、分布式 session、微服务网关；
- JWT access/refresh token 体系；
- 仓库、客户、供应商、采购员、业务员等数据权限的完整实现；
- SmartAdmin 的 SQL 重写数据权限插件；
- 字段级权限；
- 把核心业务状态改成数据库字典；
- 替换已有业务领域日志；
- 修改 `project-reference-examples/**`。

## 3. 架构决策

### 3.1 模块边界

```text
com.xianshuyuan.scm
├─ auth
│  ├─ controller
│  ├─ service
│  ├─ security
│  ├─ dto
│  └─ vo
├─ system
│  ├─ user
│  ├─ department
│  ├─ role
│  ├─ permission
│  ├─ menu
│  ├─ dictionary
│  └─ audit
└─ common
   └─ security（仅真正跨域的当前用户契约）
```

保持领域分包，不建立全项目级 controller/service/mapper/entity 目录。事务应用服务直接编排 Mapper 或明确协作者；不新增强制 Manager 层。

### 3.2 权限是独立安全资源

```text
SystemUser ──< UserRole >── Role ──< RolePermission >── Permission
                                    └──< RoleMenu >──── Menu
SystemUser ── Department
```

- `RolePermission` 是后端执行权限的来源。
- `RoleMenu` 是导航可见性的来源。
- 菜单可声明页面所需 permission code，便于校验和前端裁剪，但菜单不能用 URL 字符串定义后端授权。
- 多角色取 permission/menu 并集；本 Sprint 不实现 deny 或角色继承。
- 超级管理员可跳过普通授权检查，但其创建、禁用和删除受特殊保护并留审计。

### 3.3 权限编码

采用稳定、可读、与 HTTP path 解耦的 `<domain>.<resource>.<action>`：

```text
system.user.read
system.user.create
system.user.update
system.user.disable
system.user.reset-password
system.department.manage
system.role.read
system.role.manage
system.role.grant
system.menu.manage
system.permission.read
system.dictionary.manage
system.login-log.read
system.operation-log.read
product.read
product.manage
order.read
order.create
order.confirm
order.cancel
purchase.read
purchase.manage
purchase.receive
inventory.read
```

在实施第一步冻结完整 endpoint—permission 矩阵。权限编码视为 API 合同；重命名需要迁移，不从 URL 临时推导。

## 4. RBAC 数据模型

所有表使用 bigint ID、`version`、`deleted`、`created_at`、`updated_at` 及必要审计字段；关系列建立索引但不建立外键。

### 4.1 `sys_department`

建议字段：

```text
id
parent_id nullable
code
name
leader_user_id nullable
sort_order
status ENABLED|DISABLED
version
deleted
created_at / updated_at / created_by / updated_by
```

约束：

- 活动部门编码唯一；
- `parent_id <> id`；
- 根节点约定 `parent_id IS NULL`；
- 不允许移动到自身后代；
- 有活动子部门或活动用户时不能删除；
- 停用部门后的用户登录策略在实现前冻结，推荐不自动停用用户，但禁止新增/迁入并向管理员展示风险。

### 4.2 扩展现有 `sys_user`

保留现有 ID、username、display_name、status、version、deleted 和审计字段，新增：

```text
password_hash
password_changed_at
department_id nullable
email nullable
phone nullable
administrator boolean
must_change_password boolean
auth_version bigint
failed_login_count integer
locked_until timestamptz nullable
last_login_at timestamptz nullable
```

规则：

- 活动 username 继续唯一并规范化；
- email/phone 是否唯一在 API 合同冻结时确认；默认非空活动值唯一；
- password hash 永远不出现在 Response、日志或操作摘要；
- 新用户通过一次性初始密码或受控重置流程激活，并强制改密；
- 禁用、删除、密码修改、密码重置和敏感授权变更增加 `auth_version` 并撤销会话；
- 不允许删除或禁用最后一个可登录超级管理员；
- 当前用户不能通过普通接口取消自身最后的管理员能力。

### 4.3 `sys_role` 与 `sys_user_role`

`sys_role`：

```text
id, code, name, description, status, system_role,
version, deleted, audit fields
```

`sys_user_role`：

```text
id, user_id, role_id, created_at, created_by, deleted
```

- 活动 role code 唯一；
- 活动 `(user_id, role_id)` 唯一；
- 系统角色不可删除或任意改码；
- 授权者不得授予自己无权管理的角色/权限；
- 更新用户角色时整体校验、差异更新并同事务审计。

### 4.4 `sys_permission` 与 `sys_role_permission`

`sys_permission`：

```text
id
code
name
module
resource_type PAGE|ACTION|API
status
system_permission
sort_order
description
version / deleted / audit fields
```

`sys_role_permission`：

```text
id, role_id, permission_id, created_at, created_by, deleted
```

- 活动 permission code 全局唯一；
- 活动 `(role_id, permission_id)` 唯一；
- API 与按钮可以共享相同业务 action permission；`resource_type` 用于管理展示和一致性检查，不改变 authority 字符串；
- 核心权限由 Flyway seed 管理，后台不允许删除 system permission；
- role permission 更新后使所有受影响用户会话/`auth_version` 失效。

### 4.5 `sys_menu` 与 `sys_role_menu`

`sys_menu`：

```text
id
parent_id nullable
type DIRECTORY|PAGE
name
path
route_key nullable
icon_key nullable
required_permission_code nullable
sort_order
visible
status
version / deleted / audit fields
```

`sys_role_menu`：

```text
id, role_id, menu_id, created_at, created_by, deleted
```

规则：

- 不保存任意前端文件路径；只保存受控 `route_key`；
- PAGE 必须有 path 和 route_key，DIRECTORY 不需要组件；
- 活动 sibling path/name 唯一约束按最终 UI 需求确定；
- 阻止树环和移动到后代；
- 角色拿到子菜单时，查询响应补全可见祖先目录；
- 菜单可见不表示 API 可执行，后端以 permission 为准；
- ACTION 不作为 menu row；按钮直接消费 `Permission(resource_type=ACTION)`，避免 SmartAdmin 将三种概念塞入同一表。

### 4.6 Spring Session JDBC 表

认证会话不自建 `auth_session` 和 token filter，而由 Spring Session JDBC 提供的标准表承担：

```text
SPRING_SESSION
SPRING_SESSION_ATTRIBUTES
```

实施要求：

- 使用 Spring Boot 管理的 `spring-session-jdbc`，复用 `HttpSessionSecurityContextRepository` 持久化 Spring Security `SecurityContext`；
- 使用 Flyway 创建适配 PostgreSQL 的 Spring Session 官方 schema，不启用运行时自动建表；
- Cookie 仅保存随机 session id，设置 `HttpOnly`、`SameSite=Lax`、path `/`，生产环境强制 `Secure`；
- session 固定攻击由 Spring Security 登录时 session id 轮换机制处理，不自行生成认证 token；
- 使用 `FindByIndexNameSessionRepository` 按 principal 查询并删除会话，实现强制下线、用户禁用和密码/权限变更失效；
- 会话保存当前 `userId`、`authVersion` 和 authorities 快照；每个受保护请求通过轻量用户状态检查确认账号仍启用且 auth version 未变化，变化时删除 session 并返回 401；
- 绝对登录时长由 session attribute + 检查 filter 补充；空闲超时使用 Spring Session `maxInactiveInterval`；
- 首版不限制复杂的多端设备策略，只提供按用户查看/注销全部会话的基础能力；后续 App/小程序可使用同一 session cookie（WebView/同域）或在独立客户端认证设计中增加 header session id resolver，不改变 RBAC。

### 4.7 `sys_login_log`

```text
id
user_id nullable
username_snapshot
result SUCCESS|FAILURE|LOCKED|LOGOUT
failure_reason_code nullable
ip nullable
user_agent nullable
occurred_at
```

追加写。不得保存密码、session id、Cookie、CSRF header 或敏感请求体。失败响应使用统一文案，日志内部使用受控 reason code。

### 4.8 `sys_operation_log`

```text
id
actor_user_id nullable
actor_name_snapshot
module
operation_code
target_type
target_id nullable
http_method
request_path
request_id nullable
ip nullable
user_agent nullable
success
error_code nullable
before_data jsonb nullable
after_data jsonb nullable
occurred_at
```

- 字段白名单和脱敏；
- 不保存完整异常堆栈；
- 授权、用户状态、密码重置、菜单和字典变更使用稳定 operation code；
- 关键系统管理审计与业务写入同事务；
- 查询分页并限制导出权限；
- 现有领域日志继续保留。

### 4.9 `sys_dictionary` 与 `sys_dictionary_item`

`sys_dictionary`：code、name、description、status、version、deleted、审计字段。

`sys_dictionary_item`：dictionary_id、value、label、color/style、sort_order、status、version、deleted、审计字段。

- 活动 dictionary code 唯一；
- 同一字典活动 value 唯一；
- 核心状态机、库存类型、价格来源和财务口径不得由字典动态修改；
- 前端按 code 按需查询并用 TanStack Query 缓存，不强制登录时加载全部字典。

### 4.10 数据权限扩展点

Sprint 4 只定义 `CurrentUser` 和未来 `DataAccessScope` 的边界，不建 SmartAdmin 的通用 SQL 字符串改写插件。后续专项设计应分别处理 department、warehouse、customer、supplier、purchaser、salesperson，并把 scope 显式传入 Query Service/Mapper 和对象级命令授权。

## 5. Flyway 迁移计划

不得修改 V1—V11。推荐在当前最新迁移后拆分：

### V12：组织、用户和 RBAC schema

- 新建 department、role、user_role、permission、role_permission、menu、role_menu；
- `ALTER TABLE sys_user` 增加认证和组织字段；
- 创建 CHECK、普通索引和活动部分唯一索引；
- 不创建数据库外键。

### V13：Spring Session、登录日志、系统操作日志和字典

- 使用 Spring Session JDBC 官方 PostgreSQL schema 创建 `SPRING_SESSION`、`SPRING_SESSION_ATTRIBUTES` 及 principal/expiry 索引；
- 新建 `sys_login_log`、`sys_operation_log`、`sys_dictionary`、`sys_dictionary_item`；
- 为用户会话索引、日志时间/结果/操作者和字典查询建立索引；
- 对日志 JSONB 做类型 CHECK，不建立无明确查询价值的过多 GIN 索引。

### V14：系统权限、菜单和初始角色 seed

- seed 稳定 permission code、系统管理菜单和 system administrator role；
- 使用可重复确定的业务 code 关联，不依赖固定 identity ID；
- 不提交生产默认密码；
- 首个管理员采用受控 bootstrap：环境变量提供一次性初始化凭证或启动后命令，成功后拒绝重复执行并要求立即改密；
- demo/test 用户只进入测试 profile 或明确的开发 seed，不进入生产 migration。

### 迁移验证

- 空数据库可从 V1 完整迁移到最新；
- `sys_user` 既有 ID 与 username 保留；
- 表、列、CHECK、部分唯一索引符合计划；
- 全库仍无数据库外键；
- 核心 permission/menu seed 无缺漏或重复；
- Spring Session 表、principal 索引和活动关系符合官方 schema；
- 已有业务模块的 migration integration tests 继续通过。

## 6. 认证方案最终收敛

### 6.1 三方案比较

| 维度 | A. Spring Security + HttpSession / Spring Session JDBC | B. Spring Security + 自定义 opaque Bearer | C. Sa-Token |
|---|---|---|---|
| 开发复杂度 | 最低；认证、SecurityContext、session fixation、logout、CSRF 都有框架原生能力 | 中高；虽然授权复用 Spring Security，但 token 生成/hash/filter/过期/访问时间/撤销仍需自研 | 中；登录、踢人、权限 API 简洁，但需引入并统一另一套安全抽象 |
| RBAC | `GrantedAuthority` + `@PreAuthorize`，生态和测试成熟 | 同 A，token 仅承担认证载体 | `StpInterface` + 权限注解成熟 |
| 强制下线 | `FindByIndexNameSessionRepository` 按 principal 删除 JDBC sessions | 删除/撤销自建 token row | `StpUtil.kickout/logout` 原生支持 |
| 多端登录 | HttpSession 天然支持多个 session；按 principal 管理 | 需自行设计 device/session 模型 | 原生 device、并发和多端策略较强 |
| React SPA | 同源 Cookie 最简单；必须正确实现 CSRF | Bearer 无 CSRF，但 token 暴露给 JS，需承担 XSS/storage 风险 | 可 Cookie 或 header；前端和异常集成需按 Sa-Token 方式适配 |
| App/小程序 | Cookie/session 可用但客户端 cookie 管理稍弱；未来可加 Spring Session header resolver 或独立客户端认证 | Bearer 对非浏览器客户端最自然 | 多端能力最好，API 简洁 |
| Java 21 / Boot 3.5 | Spring Boot 官方依赖管理，适配最佳 | 同 A，但自研部分需自行维护 | 1.44.0 提供 Boot 3 starter；Java 21 可用，仍需项目级验证 |
| 多实例 | JDBC session 开箱即共享；无需 Redis | PostgreSQL token 表可共享 | 默认内存不适合多实例；可自定义持久层或引入 Redis |
| 当前工程侵入 | 低；增加 Security + Session JDBC 和 CSRF 前端处理 | 中高；增加安全 filter 和自建 session 表/生命周期 | 中；引入 Sa-Token interceptor/provider/异常体系并与 Spring 习惯分叉 |
| 权限及时生效 | 删除 principal sessions；请求时校验 user status/authVersion | 撤销 token/authVersion | kickout + 清权限 session/cache |
| 自研安全代码 | 最少 | 三者最多 | 少于 B，但多一套框架抽象 |

### 6.2 最终决策：方案 A

选择 **Spring Security + Spring Session JDBC + Cookie HttpSession**。

决策理由按优先级对应本 Sprint 原则：

1. 直接复用 Spring Security 的 `Authentication`、`SecurityContext`、认证成功 session 策略、Method Security、异常入口和 logout；
2. 直接复用 Spring Session JDBC 的 PostgreSQL session 持久化、过期清理及 principal 索引，不自建 token framework；
3. 当前 React 管理后台与 API 由同一个 Vite proxy/生产同源站点提供，Cookie session 是最小实现；
4. 无需 Redis即可支持单实例和多实例共享 session；
5. `FindByIndexNameSessionRepository` 可按 principal 查找并删除所有 session，满足禁用、改密、撤权和强制注销；
6. 权限继续使用标准 `GrantedAuthority` 和 `@PreAuthorize`，与 session 载体解耦；
7. 未来 App/小程序并未被阻断：短期客户端可以管理 session cookie；若确认需要 header，可对 Spring Session 增加受支持的 header session id resolver，或为外部客户端另行设计 OAuth2/OIDC，而不是现在预付自定义 Bearer 成本。

方案 A 的代价是 SPA 必须处理 CSRF；这是成熟框架内的标准工作，不是自研认证框架，且 Cookie 不向 JavaScript 暴露 session id，整体优于将长期 Bearer token 放入 Web Storage。

### 6.3 Spring Security 原生认证链

引入：

- `spring-boot-starter-security`；
- `org.springframework.session:spring-session-jdbc`；
- 开启 Method Security；
- 复用 Spring Boot 对版本和 JDBC session repository 的管理。

登录链：

```text
POST /api/auth/login + CSRF header
→ AuthenticationManager
→ UsernamePasswordAuthenticationToken
→ UserDetailsService 读取 sys_user、roles、permissions
→ PasswordEncoder 验证 BCrypt
→ SessionAuthenticationStrategy 轮换 session id
→ SecurityContextRepository 保存 SecurityContext 到 HttpSession
→ Spring Session JDBC 持久化到 PostgreSQL
→ 返回当前用户/菜单摘要（不返回 token）
```

请求链：

```text
XSY_SESSION Cookie
→ Spring Session 恢复 HttpSession
→ SecurityContextHolderFilter 恢复 Authentication
→ AccountVersionFilter 校验用户启用状态和 authVersion
→ authenticated default for /api/**
→ @PreAuthorize 校验 authority
```

不自行实现认证 token filter、token hash、last-access 更新或权限框架。

### 6.4 Cookie 和 CSRF

Cookie：

- 名称使用项目专用 `XSY_SESSION`，避免默认名冲突；
- `HttpOnly=true`；
- `SameSite=Lax`；
- path `/`；
- 生产 `Secure=true`，开发环境通过 profile 明确允许 HTTP；
- 前后端生产同源部署；跨域部署若未来出现需重新审查 CORS、SameSite 和凭据策略。

CSRF：

- 不关闭 CSRF；
- 使用 Spring Security `CookieCsrfTokenRepository.withHttpOnlyFalse()`，CSRF cookie 仅保存非认证秘密 token；
- 按 Spring Security SPA 指南使用 SPA request handler，确保 deferred token、BREACH 保护及登录/退出后 token 轮换正常；
- 提供 `GET /api/auth/csrf` 或等价初始化 endpoint 触发 token 生成；
- Axios `withCredentials=true`，从 `XSRF-TOKEN` cookie 读取并通过 `X-XSRF-TOKEN` header 发送；
- 登录、退出以及所有 POST/PUT/PATCH/DELETE 均校验 CSRF；
- 401、403 和 CSRF 失败继续返回统一 `ApiResponse`，CSRF token 轮换后前端刷新一次再由用户重试，不自动重放业务命令。

### 6.5 登录、退出和强制失效

- `POST /api/auth/login` 通过 `AuthenticationManager` 登录并显式保存 `SecurityContext`；
- `POST /api/auth/logout` 使用 Spring Security `LogoutHandler`，使当前 session 无效、删除 `XSY_SESSION`，并刷新 CSRF token；
- 使用 `FindByIndexNameSessionRepository` 的 principal 索引查找用户 session；
- 用户禁用/删除、密码修改/重置、角色/权限变更时删除该用户全部 session；
- 同时保留 `sys_user.auth_version`：session 保存登录版本，受保护请求检查版本和账号状态，避免遗漏删除或并发授权变更造成陈旧权限；
- 强制下线后下一请求返回 401，前端清理认证状态并跳转登录；
- 空闲超时使用 Spring Session `maxInactiveInterval`；若需要绝对最长登录期，在 session attribute 中记录 authenticated-at 并由轻量 filter 检查，不另建 token 生命周期框架；
- Spring Session JDBC 天然支持多实例共享，不需要 Redis。

### 6.6 方案 B：仅作为未来受证据驱动的备选

当前不采用自定义 opaque Bearer token。只有确认以下需求之一时重新评估：

- 原生移动端或第三方客户端无法可靠管理 Cookie；
- API 与前端跨站部署且 Cookie/CSRF 成本明显高于 Bearer；
- 明确需要非浏览器客户端使用 `Authorization` header；
- 外部 API 网关要求 Bearer token。

若未来采用，仍必须复用 Spring Security `Authentication` / `SecurityContext` / `@PreAuthorize`；token 使用 CSPRNG 高熵值、数据库只存 hash，并支持过期、撤销、最后访问、auth version 和强制失效。它当前并不优于方案 A，因为这些生命周期能力都将成为项目自研代码。

### 6.7 方案 C：Sa-Token 结论

Sa-Token 1.44.0 提供 Spring Boot 3 starter，RBAC、踢人、多端登录和 session API 成熟，对未来多端体验有优势；不使用 Redis时可单实例运行。

当前仍不采用，原因不是偏见，而是：

- 当前主需求是同源 React 单体后台，Spring Security + Spring Session 已完整覆盖；
- 多实例下 Sa-Token 默认内存存储不共享，仍需 Redis或自定义持久层；
- 引入 Sa-Token 会增加 interceptor、`StpInterface`、异常码和上下文适配，而标准 Spring Security 已是项目指导栈；
- Method Security、MockMvc 测试、CSRF 和 Servlet SecurityContext 使用 Spring Security 原生能力的集成成本更低。

如果未来多端登录成为核心且团队明确希望统一采用 Sa-Token，可单独做 ADR 和迁移验证；SmartAdmin 实现继续作为参考，而不是本 Sprint 的默认。

### 6.8 密码和登录保护

- 使用 `PasswordEncoder`，首选 BCrypt，并把 cost 配置化；
- 密码只在 TLS 上提交，不实现前端 RSA/AES 包装作为安全替代；
- 登录错误统一为“用户名或密码错误”，防账号枚举；
- 连续失败计数和短时锁定基于事务更新；成功后清零；
- 登录失败日志不能保存密码；
- 密码修改需校验旧密码并删除其他 session；
- 管理员重置密码产生一次性凭证并强制改密；
- 禁止万能密码和硬编码生产默认密码。

### 6.9 JWT 结论

本 Sprint 不采用。当前单体不需要无状态跨服务验证，而注销、禁用强退和权限变更即时生效对 JWT 需要 refresh token、短 access token、黑名单或 auth version，复杂度高于收益。

## 7. 后端 API 与授权

### 7.1 Auth API

```text
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
POST /api/auth/change-password
```

`LoginResponse` 不返回认证 token；登录成功由 `Set-Cookie: XSY_SESSION=...; HttpOnly` 建立会话。`/me` 返回：

```text
user
roles[]
permissions[]
menus[]
mustChangePassword
session expiry metadata
```

不得返回 password hash、session 内部属性、失败计数或内部锁细节。

### 7.2 System API

```text
/api/system/departments
/api/system/users
/api/system/roles
/api/system/permissions
/api/system/menus
/api/system/dictionaries
/api/system/login-logs
/api/system/operation-logs
```

CRUD 遵循当前 REST、分页、Validation、乐观锁、软删除和 409 冲突约定。角色授权和用户角色变更使用显式 command endpoint，并整体提交权限版本。

### 7.3 API 权限

- Controller 敏感方法使用 `@PreAuthorize("hasAuthority('...')")`；
- 查询与写入使用不同权限；
- 后端权限判断不读取前端菜单；
- 对对象归属或未来数据范围另做服务端检查；
- 建立 endpoint inventory 测试：每个 Controller endpoint 必须属于匿名白名单、仅登录可用 endpoint 或显式 authority；
- 为所有既有业务 endpoint 建权限矩阵后再启用全局认证，避免漏授权。

### 7.4 权限变更即时生效

任何以下变更都必须通过 `FindByIndexNameSessionRepository` 删除受影响用户 session，并提升 `auth_version`：

- 用户禁用/删除；
- 密码修改/重置；
- 用户角色变更；
- 角色权限变更；
- 系统管理员标识变化。

如果第一版每请求从数据库查询权限，可不另加缓存；若增加缓存，必须以用户 auth version 为 key 并在关系变更后清除，不能只等 TTL。

## 8. React 登录、动态菜单与权限

### 8.1 文件方向

计划新增或修改：

```text
src/api/auth.ts
src/api/system/*.ts
src/types/auth.ts
src/auth/AuthProvider.tsx
src/auth/RequireAuth.tsx
src/auth/RequirePermission.tsx
src/auth/Permission.tsx
src/auth/usePermission.ts
src/router/routeRegistry.tsx
src/router/index.tsx
src/layouts/AdminLayout/navigation.tsx
src/layouts/AdminLayout/index.tsx
src/pages/auth/LoginPage.tsx
src/pages/system/**
```

具体文件按实施时现有模式调整，不复制 SmartAdmin Vue 代码。

### 8.2 启动与登录流程

```text
打开应用
→ 请求 /api/auth/csrf 初始化 CSRF cookie
→ 请求 /api/auth/me（浏览器自动发送 XSY_SESSION Cookie）
→ 若返回 401，进入公开 /login
→ 登录请求携带 X-XSRF-TOKEN
→ 浏览器接收并持有 XSY_SESSION Cookie
→ 再次请求 /api/auth/me
→ 得到用户、权限和菜单
→ 校验 routeKey registry
→ 构造可访问路由与当前双侧栏菜单
→ 跳转原目标或第一个可访问页面
```

如果 `/me` 失败：

- 401：清理内存认证状态，返回登录；
- 403：保留会话，展示无权限；
- 网络错误：展示错误和重试，不误判为退出；
- 菜单为空：展示“暂无可访问功能”，不回退显示所有静态菜单。

### 8.3 动态路由安全边界

服务端返回 `routeKey`，React 维护静态白名单：

```tsx
const routeRegistry = {
  products: () => import('../pages/product/ProductPage'),
  orders: () => import('../pages/order/OrderListPage'),
  // ...
};
```

- 数据库不能指定任意 import 路径；
- routeKey 不存在时记录诊断并展示配置错误；
- `/login`、403、404 等基础路由静态注册；
- detail/edit 子路由可由同一页面菜单授权映射，不能因侧栏不展示而遗漏访问控制；
- 保留现有 Header + 一级/二级侧栏与 Admin Theme。

### 8.4 按钮权限

提供：

```tsx
<Permission authority="order.confirm">
  <Button>确认订单</Button>
</Permission>
```

以及：

```ts
const canConfirm = usePermission('order.confirm');
```

规则：

- 默认无权限时不渲染危险操作；需要解释时可 disabled + tooltip，但仍不调用 API；
- 批量操作、行操作、快捷入口和路由入口使用同一权限编码；
- 不在页面散落字符串比较角色名；
- 不把前端权限判断当作安全控制。

### 8.5 Axios 处理

- `withCredentials=true`，由浏览器自动携带 `XSY_SESSION`；
- 使用 Axios XSRF 配置从 `XSRF-TOKEN` cookie 注入 `X-XSRF-TOKEN` header；
- 应用启动和登录/退出后刷新 CSRF token；
- 401 触发一次内存认证状态清理和跳转；
- 403 转换为保留 status/code 的 `ApiError`，CSRF 失败提示刷新后重试；
- 不自动重放 POST/PUT/PATCH/DELETE 业务命令，避免重复提交；
- 避免多个并发请求触发重复 modal/toast/redirect；
- 日志和错误信息不得输出 session id、Cookie 或 CSRF header。

### 8.6 状态管理

当前无需为认证单独加入 Zustand：

- TanStack Query 管理 `/auth/me` 服务端状态；
- React Context 管理当前认证快照与 login/logout action；
- 认证凭据仅由 HttpOnly Cookie 持有，不保存到 localStorage/sessionStorage；
- 页面局部状态继续留在组件；
- 不把完整菜单/权限复制到多个 store。

若后续跨窗口、多身份或复杂会话动作使 Context 明显膨胀，再评估 Zustand。

## 9. 操作日志与登录日志

### 9.1 日志分类

```text
应用诊断日志：SLF4J/Logback，不作为业务审计
登录日志：认证安全事件
系统操作日志：用户/角色/菜单/字典等系统管理动作
领域审计日志：订单、价格、采购、库存等业务事实
```

### 9.2 写入语义

- 登录失败日志不能因为写入失败而暴露凭据或造成二次异常；可使用受控 best-effort/独立事务，并在诊断日志告警。
- 权限授予、用户禁用、密码重置、菜单和字典变更的审计必须与对应事务一致。
- 现有领域日志继续在业务事务内写入。
- 通用 AOP 可捕获请求 metadata，但关键 `before/after` 由应用服务显式提供；不序列化全部参数。

### 9.3 脱敏和保留

禁止记录：

```text
password / oldPassword / newPassword
session id / session cookie
Cookie / X-XSRF-TOKEN
密码重置凭证
完整个人敏感信息
完整异常堆栈
```

需要定义日志保留、归档和删除政策；Sprint 4 至少提供按时间分页和必要索引，不提供无界列表。

## 10. 错误码计划

沿用当前 `ErrorCode` 和模块常量模式，至少覆盖：

```text
AUTH_INVALID_CREDENTIALS
AUTH_LOGIN_REQUIRED
AUTH_SESSION_INVALID
AUTH_SESSION_EXPIRED
AUTH_ACCOUNT_DISABLED
AUTH_ACCOUNT_LOCKED
AUTH_PASSWORD_CHANGE_REQUIRED
AUTH_PERMISSION_DENIED
AUTH_SESSION_REVOKED
SYSTEM_USER_NOT_FOUND
SYSTEM_USER_USERNAME_CONFLICT
SYSTEM_USER_VERSION_CONFLICT
SYSTEM_LAST_ADMIN_REQUIRED
SYSTEM_DEPARTMENT_NOT_FOUND
SYSTEM_DEPARTMENT_CYCLE
SYSTEM_DEPARTMENT_IN_USE
SYSTEM_ROLE_NOT_FOUND
SYSTEM_ROLE_CODE_CONFLICT
SYSTEM_ROLE_ASSIGNMENT_FORBIDDEN
SYSTEM_PERMISSION_NOT_FOUND
SYSTEM_MENU_NOT_FOUND
SYSTEM_MENU_CYCLE
SYSTEM_DICTIONARY_CODE_CONFLICT
SYSTEM_DICTIONARY_ITEM_VALUE_CONFLICT
```

认证 filter、method security 和 MVC 异常都必须输出同一个 `ApiResponse` 合同。同步修复既有 unexpected exception handler 缺少注解的问题，但必须先写失败测试。

## 11. 测试计划

### 11.1 后端单元测试

- 用户名规范化和密码策略；
- BCrypt 校验、修改和重置流程；
- 账号启用、禁用、锁定状态矩阵；
- 登录失败计数、锁定和成功清零；
- Spring Session 空闲/绝对期限、principal 会话删除和 auth version；
- 多角色权限并集；
- 菜单裁剪及祖先补全；
- 部门/菜单树环和移动到后代校验；
- 最后管理员保护；
- 授权者不得越权授予；
- 操作日志字段白名单和密码/session/CSRF 脱敏；
- 字典与核心枚举边界。

### 11.2 MockMvc / Security 测试

- 登录匿名可访问；
- 未认证且无有效 session 返回 401 + 标准 envelope；
- session 过期、被强制删除、账号禁用或 auth version 变化返回稳定 401；
- 已登录无权限返回 403；
- 有 authority 可访问；
- `/auth/me`、logout、change-password 合同；
- Validation、404、409 和 unexpected exception 格式；
- 所有 endpoint 的匿名/认证/authority 分类完整性；
- 敏感 API 不能依靠菜单权限绕过；
- OpenAPI/Swagger 的环境访问策略。

### 11.3 PostgreSQL Integration Tests

- 从空库 Flyway 全迁移且无外键；
- 既有 `sys_user` ID 保留；
- 活动 username/role code/permission code/dictionary value 唯一；
- 用户角色和角色权限差异更新事务；
- 角色授权失败时关系、auth version 和日志全部回滚；
- 禁用用户即时撤销会话；
- 角色权限变化删除受影响用户的现有 sessions 或使 auth version 校验失败；
- 登录并发失败计数不丢失；
- Spring Session principal 索引可查找并删除用户全部会话；
- 登录日志、操作日志追加写且不可业务更新；
- 部门和菜单关系服务校验；
- 种子 permission/menu/role 完整。

### 11.4 Frontend Vitest

- Cookie session 不写入 Web Storage，刷新后通过 `/auth/me` 恢复状态；
- Axios credentials 与 XSRF header 注入；
- 401 清会话并单次跳转；
- 403 保留会话并展示无权限；
- AuthProvider loading/error/retry/authenticated 状态；
- RequireAuth 和 RequirePermission；
- routeKey registry 和未知 routeKey；
- 服务端菜单转换为现有一级/二级侧栏；
- 菜单为空和配置错误状态；
- Permission/usePermission 控制按钮、批量与行操作；
- 直达无权 detail/edit URL 被拒绝；
- 登录表单重复提交保护和安全错误文案。

### 11.5 Playwright E2E

至少增加一条 Sprint 4 主链：

1. 管理员登录；
2. 验证当前用户和动态双侧栏；
3. 创建部门、角色和受限用户；
4. 给角色授予页面、按钮及 API 权限；
5. 受限用户登录，只看到授权菜单；
6. 授权按钮可见且 API 成功；
7. 未授权按钮隐藏；
8. 直达未授权 URL 显示 403；
9. 直接调用未授权 API 返回 403；
10. 管理员撤销权限后，已有会话立即失效或刷新为新权限；
11. 用户退出后受保护页面不可访问；
12. 登录失败、成功和系统授权日志可查询；
13. 检查 console、network 和 storage 无未处理错误或 session/CSRF 泄露。

### 11.6 全量验证命令

后端：

```powershell
cd xsy-scm-server
$env:JAVA_HOME='D:\Java\JDK21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd test
mvn.cmd clean verify
```

前端：

```powershell
cd xsy-scm-web
npm run lint
npm run typecheck
npm test
npm run build
npm run e2e -- system-foundation-rbac.spec.ts
```

PostgreSQL 集成测试和 E2E 需要真实数据库、后端及浏览器环境；未运行时不得宣称通过。

## 12. 实施顺序

### Task 1：冻结权限和认证合同

- [ ] 列出全部现有 `/api/**` endpoints。
- [ ] 为每个 endpoint 标记 anonymous / authenticated / authority。
- [ ] 冻结 permission code、401/403/423 决策、session 超时、并发会话和首管理员 bootstrap。
- [ ] 写后端和前端合同测试骨架。

### Task 2：建立 Security 基础

- [ ] 增加 Spring Security starter，开启 Method Security。
- [ ] 先写认证 envelope、401、403 和 unexpected handler 的失败测试。
- [ ] 实现统一 AuthenticationEntryPoint、AccessDeniedHandler 和当前用户契约。
- [ ] 修复现有全局未预期异常映射缺口。

### Task 3：Flyway RBAC schema

- [ ] 编写 V12/V13/V14 migration 和 migration IT。
- [ ] 增量扩展 `sys_user`，不改变既有 ID。
- [ ] 建立 RBAC、session、日志、字典表和索引，无外键。
- [ ] seed 权限、菜单和系统角色，不提交生产密码。

### Task 4：部门、用户、角色、权限和菜单持久化

- [ ] 实现 Entity、Mapper/XML、Request/Query/Response、Converter。
- [ ] 实现唯一性、状态、树环、引用和乐观锁校验。
- [ ] 实现用户角色、角色权限、角色菜单差异更新事务。
- [ ] 实现最后管理员和授权提升保护。

### Task 5：Spring Session JDBC 与登录

- [ ] 实现 password encoder、登录、`/me`、logout、change-password。
- [ ] 配置 Spring Session JDBC、`XSY_SESSION` Cookie、session fixation 防护、空闲/绝对期限和 auth version 校验。
- [ ] 使用 principal 索引实现禁用、改密和权限变更后的全部会话删除。
- [ ] 实现 CSRF repository、SPA request handler、初始化 endpoint 和统一 401/403 响应。
- [ ] 实现失败计数、锁定、统一错误文案和禁用强退。
- [ ] 写登录成功/失败/锁定/退出日志。

### Task 6：后端 API 权限覆盖

- [ ] 为系统管理和所有现有业务 endpoints 应用权限矩阵。
- [ ] 使用 `@PreAuthorize`，后端不读取菜单决定授权。
- [ ] 加 endpoint inventory 测试防漏授权。
- [ ] 将现有业务 actor 从 `SYSTEM` 渐进接入当前用户，保留系统任务 fallback。

### Task 7：系统管理操作日志与字典

- [ ] 实现登录日志和操作日志分页查询。
- [ ] 实现脱敏的系统审计 writer，关键动作事务内写。
- [ ] 实现 dictionary/dictionary item CRUD 和按 code 查询。
- [ ] 确保核心业务 Enum 不依赖数据库字典。

### Task 8：React 认证基础

- [ ] 实现 auth API/types、AuthProvider 和 `/auth/me` 恢复策略。
- [ ] 实现 Axios credentials、XSRF、401/403 处理和并发去重。
- [ ] 实现登录页、RequireAuth、403/404 和会话过期体验。
- [ ] 更新 Header 显示真实当前用户及 logout。

### Task 9：React 动态菜单、路由和按钮权限

- [ ] 建立 routeKey/iconKey registry。
- [ ] 将 `/auth/me` menus 转换为现有一级/二级侧栏。
- [ ] 对 list/detail/edit routes 统一做 RequirePermission。
- [ ] 实现 Permission/usePermission，并接入敏感页面按钮。
- [ ] 实现菜单加载、空、错误、重试和未知 routeKey 状态。

### Task 10：系统管理页面

- [ ] 部门树、用户、角色、权限、菜单、字典页面。
- [ ] 角色菜单/权限和用户角色分配交互。
- [ ] 登录日志和操作日志只读分页页面。
- [ ] 保持当前 Admin Theme、双侧栏、密集表格和明确状态。

### Task 11：验证与回归

- [ ] 运行后端 focused tests、全量 test 和 clean verify。
- [ ] 运行前端 lint、typecheck、test 和 build。
- [ ] 运行 Sprint 4 Playwright 主链。
- [ ] 验证所有既有 Sprint 1—3 主链未被认证改造破坏。
- [ ] 检查日志、响应、浏览器 storage、console 和 network 无密码、session id 或 CSRF header 泄露。
- [ ] 记录未运行或失败项目，不虚报通过。

## 13. 风险与缓解

### 13.1 现有 API 从匿名切换为受保护

风险：漏配会导致越权，过严会打断已有前端和 E2E。

缓解：先生成完整 endpoint inventory 和权限矩阵；默认 `/api/**` 认证；每个敏感方法显式 authority；同步调整前端/E2E；上线前完成管理员 bootstrap 演练。

### 13.2 既有 `sys_user` 无密码

风险：重建或错误回填会破坏 ID、审计引用和登录安全。

缓解：只做 ALTER 增量迁移；密码列先允许受控未激活状态或用 bootstrap 流程初始化；不生成公共默认密码；迁移测试验证既有 ID。

### 13.3 权限与菜单漂移

风险：菜单显示与后端 authority 不一致，或数据库配置指向不存在路由。

缓解：独立 permission registry；Flyway seed；routeKey 白名单；启动/测试做权限编码、菜单依赖和 endpoint annotation 一致性检查；未知 routeKey 明确报错。

### 13.4 权限缓存陈旧

风险：撤权后现有 session 中的 authority 快照继续拥有能力。

缓解：第一版优先每请求验证 auth version，缓存从简；用户/角色/权限变化提升版本或撤销 session；加入即时失效集成测试。

### 13.5 Session Cookie、CSRF 和 XSS

风险：session cookie 被跨站请求滥用，或前端将 Cookie/CSRF 值输出到日志；XSS 仍可借用户会话发起操作。

缓解：`XSY_SESSION` 使用 HttpOnly、SameSite=Lax、生产 Secure；所有状态变更保留 Spring Security CSRF 校验；认证凭据不进入 Web Storage；限制可执行脚本来源、避免危险 HTML 注入；合理设置空闲/绝对期限；密码修改、禁用和权限变更可删除全部会话。

### 13.6 登录爆破和并发更新

风险：失败计数竞争丢失或攻击者绕过单节点限流。

缓解：数据库原子更新/行锁、统一文案、账号与 IP 两个维度、短锁定和审计；多实例后再评估 Redis 限流，不以 JVM 内存作为最终保证。

### 13.7 权限提升与最后管理员

风险：管理员授予自己超权权限、删除最后一个管理员或锁死系统。

缓解：服务端校验授权者权限子集、系统角色保护、最后管理员事务检查、关键动作二次确认及不可变审计。

### 13.8 日志敏感数据和增长

风险：AOP 捕获密码/session/CSRF/个人信息，日志表无界增长。

缓解：字段白名单、稳定 operation code、摘要而非完整 payload、时间/操作者索引、分页、保留/归档政策；完整异常只进入受控诊断日志，不进入业务数据库。

### 13.9 部门和菜单树环

风险：错误移动导致递归死循环或菜单不可用。

缓解：服务端后代检查、深度上限、访问集检测、事务写入和树结构测试；不信任前端 parentId。

### 13.10 SmartAdmin 机械迁移

风险：引入 Vue、MySQL SQL、Redis、万能密码、通用 SQL 重写或与现有 API 不兼容的 ResponseDTO。

缓解：参考目录始终只读；代码审查检查包名/表名/依赖和 SQL；以两份架构对照文档为实施约束。

## 14. 完成标准

- [ ] 认证和 RBAC 数据模型通过 PostgreSQL 集成测试且全库无外键。
- [ ] 密码、session、CSRF、禁用、锁定、强制下线和权限变更语义经过测试。
- [ ] 所有 `/api/**` endpoint 有明确认证与权限分类。
- [ ] 未授权直接 API 调用返回标准 401/403，不能靠前端隐藏。
- [ ] 用户、部门、角色、菜单、权限和字典管理可用。
- [ ] React 登录、动态双侧栏、路由守卫和按钮权限可用。
- [ ] 登录日志和系统操作日志可查询且不泄露敏感字段。
- [ ] 现有领域日志保留并可记录真实 actor。
- [ ] 后端 `clean verify` 通过。
- [ ] 前端 lint、typecheck、test、build 通过。
- [ ] Sprint 4 Playwright 主链通过。
- [ ] Sprint 1—3 关键回归未被破坏。
- [ ] `project-reference-examples/**` 未修改。
