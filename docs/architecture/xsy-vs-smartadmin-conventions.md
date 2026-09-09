# xsy-scm 与 SmartAdmin 系统基础规范对照

- 日期：2026-09-07
- 范围：认证、用户、部门、角色、菜单、权限、日志、字典及前端权限基础
- 决策原则：SmartAdmin 是参考实现，xsy-scm 的现有合同和技术栈优先

## 1. 调查摘要

### 1.1 xsy-scm 当前状态

后端：

- `xsy-scm-server/pom.xml` 尚无 Spring Security、Sa-Token、JWT 或 Redis。
- `V1__create_sprint1_schema.sql` 已建立最小 `sys_user`，字段只有用户名、显示名、状态、版本、软删除和通用审计字段；没有密码、部门、角色、权限或会话。
- 没有正式 `auth`、系统用户、部门、角色、权限、菜单或字典模块。
- 已有订单、协议价、采购等领域操作日志；操作者仍主要使用临时值 `SYSTEM`。
- API 使用 `ApiResponse<T>` 和 `PageData<T>`；业务错误使用 `BusinessException(ErrorCode)`。
- 请求/响应命名以 `XxxSaveRequest`、`XxxPageQuery`、`XxxResponse` 为主，结构映射放在领域 `converter`。
- 自定义 SQL 按规则放在 mapper XML；数据库是 PostgreSQL 且禁止外键。

前端：

- `src/router/index.tsx` 使用静态 `createBrowserRouter` 和 lazy route。
- `src/layouts/AdminLayout/navigation.tsx` 静态维护一级、二级菜单。
- `src/layouts/AdminLayout/index.tsx` 固定显示“管理员”，没有登录状态。
- `src/api/http.ts` 统一解包 `{code,message,data}`，但没有 token 注入和 401/403 会话处理。
- TanStack Query 管理服务端状态；没有认证 store/provider、路由守卫、动态菜单、按钮权限或 API 权限辅助组件。
- `package.json` 当前没有 Zustand；现阶段可以先用 React Context + TanStack Query，避免只为少量认证状态增加依赖。

### 1.2 SmartAdmin 当前状态

- 使用 Sa-Token 1.44.0 和 Redis adapter。
- 自定义 MVC interceptor 统一读取 token、加载用户上下文、检查匿名注解和 Sa-Token 权限注解。
- Employee 同时承担员工档案与登录账号。
- RoleEmployee、RoleMenu 和 Menu/功能点构成 RBAC；Menu 同时保存前端及 API 权限字符串。
- 登录返回 token、用户、角色菜单和安全提示，并记录登录日志。
- 存在登录日志、AOP 操作日志和 DataTracer 三种日志机制。
- 字典使用 Dict + DictData，并提供缓存。

## 2. 规范对照与采用决策

| 议题 | xsy-scm 现状 | SmartAdmin 实现 | xsy-scm 决策 |
|---|---|---|---|
| 技术栈 | Java 21、Spring Boot 3.5、React 19 | Java 17、Spring Boot 3.5、Vue 3 | 保留 Java 21 + React，迁移思想，不复制源码 |
| 包结构 | `com.xianshuyuan.scm` 下领域分包 | system/business 模块分包 | 保留领域分包；新增 `auth` 和 `system`，不建全局分层树 |
| API 响应 | `{code,message,data}` | `ResponseDTO` 含额外 `ok/level/dataType` | 保留当前 `ApiResponse`，不扩张响应字段 |
| 异常方式 | Service 抛 `BusinessException(ErrorCode)` | Service 经常返回 `ResponseDTO.error` | 保留异常式业务流程，扩展认证/授权错误码 |
| 请求模型 | `SaveRequest`、`PageQuery` | `AddForm`、`UpdateForm`、`QueryForm` | 新系统模块沿用当前命名，不批量改旧代码 |
| 响应模型 | `XxxResponse` 放 `vo` | `XxxVO` | 沿用 `Response`；不为表面一致重命名 |
| 映射 | 领域内显式 Converter | `SmartBeanUtil.copy` 较多 | 显式 Converter；重复映射再评估 MapStruct，不默认反射复制 |
| DAO | MyBatis-Plus Mapper + XML | BaseMapper DAO + XML | 原则一致；使用 PostgreSQL SQL，不复制 MySQL SQL |
| Manager | 当前无统一 Manager 层 | 事务、批处理、缓存时使用 | 不设强制 Manager；用应用服务或专门协作者表达真实职责 |
| 认证依赖 | 无 | Sa-Token + Redis | 推荐 Spring Security + 可撤销 opaque token；Redis 后置 |
| 密码 | 尚未实现 | 自定义加盐/安全服务、前端加密 | Spring Security `PasswordEncoder`（BCrypt 起步）+ TLS |
| 用户模型 | 最小 `sys_user` | Employee 合并账号和员工 | 增量扩展 `sys_user`；预留员工档案分离，不重建已有 ID |
| 部门 | 无 | 邻接树 + 全量缓存 | 邻接树起步，补环检测、移动校验和清晰启停语义 |
| 权限 | 无 | Menu/功能点持有 `webPerms/apiPerms` | 权限独立成安全资源，菜单只消费权限，不成为 API 授权真相 |
| 后端授权 | 无 | `@SaCheckPermission` | Spring Security `@PreAuthorize(hasAuthority(...))` |
| 按钮权限 | 无 | Menu 功能点 + webPerms | 用 ACTION 类型权限编码和 React `Permission` 控制 |
| API 权限 | 无 | `apiPerms` + Sa-Token 注解 | 稳定权限注册表 + `@PreAuthorize`；数据库 URL 不直接授权 |
| 数据权限 | 未实现 | MyBatis 插件字符串重写 SQL | Sprint 4 只预留；后续显式 scope 参数和对象级授权 |
| 动态菜单 | 静态一级/二级数组 | 登录返回菜单/功能点 | 服务端返回 menu metadata；React 用 routeKey 白名单映射组件 |
| 登录日志 | 无 | 独立登录日志 | 新增追加式登录安全日志，严禁密码/token 入库 |
| 操作日志 | 领域日志已存在 | 通用 AOP + DataTracer | 新增系统操作日志，保留领域日志；关键审计事务内写 |
| 字典 | 无 | Dict + DictData + 缓存 | 建两层字典；核心业务状态仍使用 Enum + CHECK |
| 缓存 | 无 Redis | 用户、权限、部门、字典缓存 | 第一版优先正确性；无必要不缓存，变更时必须失效会话/版本 |
| 软删除 | 普遍使用，活动部分唯一索引 | deletedFlag | 继续软删除及 PostgreSQL 部分唯一索引 |
| 外键 | 项目明令禁止 | 参考库模型不同 | 不加外键，以服务校验、事务、索引、CHECK 和测试保证关系 |

## 3. 当前 API、异常和 DTO/VO 风格的约束

### 3.1 API 合同

当前 `ApiResponse`：

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) { ... }
    public static ApiResponse<Void> error(int code, String message) { ... }
}
```

Sprint 4 应新增稳定错误码并映射到语义正确的 HTTP 状态：

- 400：请求结构或凭据格式错误；
- 401：未登录、token 无效或已过期；
- 403：已登录但无权限；
- 404：系统资源不存在；
- 409：用户名、角色编码、菜单路径、字典值、版本等冲突；
- 423 或 403：账号锁定，需在 API 合同冻结时二选一并保持稳定；
- 500：未处理异常，响应不泄露堆栈。

当前 `GlobalExceptionHandler` 对 Validation 和数据库冲突已有处理，但调查发现 `handleUnexpectedException(Exception)` 没有 `@ExceptionHandler(Exception.class)`。这是现有可靠性缺口，应在 Sprint 4 实现阶段先写测试再修复；本轮不修改生产代码。

### 3.2 DTO/VO/Converter

新模块应继续采用：

```text
system/user/dto/UserSaveRequest.java
system/user/dto/UserPageQuery.java
system/user/vo/UserResponse.java
system/user/converter/UserConverter.java
```

认证模型可使用：

```text
auth/dto/LoginRequest.java
auth/vo/LoginResponse.java
auth/vo/CurrentUserResponse.java
```

规则：

- Request 携带客户端可写字段；
- Response 不泄露 `passwordHash`、token hash、失败计数等安全字段；
- Entity 只映射持久化；
- Converter 只做确定性的结构转换；
- 权限判断、密码处理、事务和数据库查询不放进 Converter。

## 4. 认证技术选择

### 4.1 推荐：Spring Security + opaque session token

理由：

1. `AGENTS.md` 的默认后端栈明确列出 Spring Security。
2. xsy-scm 是模块化单体，当前没有网关或跨服务无状态 token 需求。
3. 用户禁用、密码修改、角色变更和权限变更需要即时撤销或失效。
4. 服务端会话比 JWT 黑名单/refresh token 更容易保证上述语义。
5. 可以先用 PostgreSQL session repository，未来多实例时迁移 Redis 而不改变前端 Bearer 合同。

建议 token 为高熵随机值，客户端只持有原文，数据库只保存 token hash，并记录用户、创建、最后活动、绝对过期、撤销和会话版本。

### 4.2 Sa-Token 是否适合

Sa-Token 在功能上适合后台 RBAC：API 简洁、权限注解和会话管理成熟，SmartAdmin 有真实参考链路。但本项目不优先采用：

- 会形成与仓库默认 Spring Security 不同的安全范式；
- SmartAdmin 方案连带 Redis adapter 和自定义 interceptor；
- 现有团队约定、测试生态和未来 Spring 组件集成更偏向 Spring Security；
- 不能因为参考工程使用就证明新运行时依赖必要。

结论：**能用，但当前 xsy-scm 不推荐。** 如果未来用户明确要求 Sa-Token，需单独评审依赖、Redis、异常映射、Method Security 等迁移成本。

### 4.3 JWT 是否适合

本轮不推荐 JWT。JWT 的主要优势是跨服务无状态验证，而当前项目没有该需求；权限变更即时生效、禁用强退和主动注销会引入短期 access token、refresh token、轮换、黑名单或 token version 等额外机制。若未来存在多服务或第三方 API，再重新评估。

## 5. RBAC 与菜单边界

推荐关系：

```text
SystemUser ──< UserRole >── Role ──< RolePermission >── Permission
                                    └──< RoleMenu >──── Menu
SystemUser ── Department
```

- `Permission` 是后端授权与按钮控制共享的稳定能力标识。
- `Menu` 是导航和页面组织模型，可声明进入页面所需权限，但不定义后端 URL 授权规则。
- `RoleMenu` 决定导航展示；`RolePermission` 决定可执行能力。
- 服务端可在分配角色菜单时自动校验菜单依赖的权限，但二者不应隐式等同。
- 前端权限取多角色权限并集；显式 deny、本期数据权限和角色继承均不在 Sprint 4 范围。

权限命名采用稳定、可读、与路由解耦的字符串，例如：

```text
system.user.read
system.user.create
system.user.update
system.user.disable
system.role.read
system.role.grant
system.menu.manage
system.dictionary.manage
system.login-log.read
system.operation-log.read
order.read
order.confirm
purchase.receive
inventory.read
```

## 6. React 动态菜单与权限控制

### 6.1 路由不完全下放数据库

数据库菜单只返回安全的 metadata：

```text
id, parentId, type, label, path, routeKey, iconKey, sortOrder, visible, requiredPermission
```

前端维护受控组件注册表：

```text
routeKey → lazy import function
iconKey → imported icon
```

服务端不得返回任意 JS 模块路径或可执行组件名供浏览器动态加载。未注册 routeKey 应进入可观测错误状态，而不是静默加载任意路径。

### 6.2 认证与服务端状态

- `AuthProvider/useAuth` 保存 token 生命周期和认证动作。
- `/auth/me` 由 TanStack Query 管理，返回当前用户、角色、权限和菜单树。
- token 首选内存并按“当前标签页”需求写 sessionStorage；不默认使用长期 localStorage。
- Axios 请求拦截器加 Bearer token。
- 401 清理会话并跳转登录；403 保持登录并展示无权限。
- 并发失败要去重，避免 toast/redirect 风暴。

### 6.3 三层前端控制

1. `RequireAuth`：只判断是否登录。
2. `RequirePermission`：控制路由是否可进入。
3. `Permission` / `usePermission`：控制按钮、链接或批量操作入口。

前端控制不是安全边界。即使按钮被隐藏，直接调用 API 仍必须由 `@PreAuthorize` 拒绝。

## 7. 日志整合决策

### 7.1 现有领域日志必须保留

订单、协议价、采购收货和库存等日志表达领域动作及结构化前后值，并与业务事务关联。它们不能被通用 HTTP AOP 日志替换。

### 7.2 新增系统操作日志

系统操作日志覆盖用户、部门、角色授权、菜单、权限和字典管理，记录：

```text
actorUserId / actorName
module / operationCode
targetType / targetId
requestId
httpMethod / path
ip / userAgent
success / errorCode
beforeSummary / afterSummary
createdAt
```

使用字段白名单和脱敏摘要，不保存密码、token、Authorization header、完整请求体或完整堆栈。关键授权与账号变更日志和业务写入同事务。

### 7.3 登录日志

登录日志独立保存成功、失败、锁定、退出、过期/撤销（按需要），失败原因使用安全类别，不保存原始密码或 token。匿名失败也要记录输入用户名的受控摘要及来源信息，但查询权限必须严格限制。

## 8. 渐进治理而非大规模重构

Sprint 4 不应为了统一风格而：

- 批量重命名现有 DTO、VO、Service 或表；
- 重写所有 Controller；
- 用通用操作日志替代现有领域日志；
- 把所有现有业务状态迁到字典；
- 引入全局 Manager 层；
- 将静态路由完全替换为数据库可执行配置；
- 一次性引入 Redis、JWT、Sa-Token 和 Spring Security 多套方案。

实施采用“新系统模块遵循新规范，旧模块接入认证权限时局部治理”的方式。认证启用前仍需完成所有 `/api/**` endpoint 的权限矩阵，避免默认放行造成漏授权。
