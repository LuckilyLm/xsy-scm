# SmartAdmin Java17 编码规范调查与提取

- 调查日期：2026-09-07
- 参考范围：`project-reference-examples/xsy-scm/xsy-scm-server/xsy-scm-server`
- 文档性质：只读参考调查，不是 xsy-scm 的强制实现模板

## 1. 使用边界

SmartAdmin 是系统通用能力的参考实现，xsy-scm 才是产品事实源。本文只提取登录、组织、RBAC、菜单、字典和日志的设计意图及编码习惯，不复制其包名、表名、Controller、DTO、Vue 页面或 MySQL SQL，也不以其 Java 17 技术栈替代 xsy-scm 的 Java 21 + Vue3 技术栈。

参考时采用以下判断：

1. 当前项目已有成熟约定时，保留当前约定。
2. 当前项目缺少系统能力时，借鉴 SmartAdmin 的职责划分和安全边界。
3. 技术栈、数据库或领域模型不同时，只迁移思想，重新实现。
4. SmartAdmin 的缺陷、历史兼容设计和不必要依赖不进入 xsy-scm。

## 2. 模块组织与真实分层

SmartAdmin 按业务模块组织代码。常见结构是：

```text
module/system/<module>/
├─ controller
├─ service
├─ manager
├─ dao
├─ domain/
│  ├─ entity
│  ├─ form
│  └─ vo
└─ constant
```

实际调用链并非固定四层：

```text
Controller → Service → DAO
Controller → Service → Manager → DAO
Controller → Service → other Service → DAO
```

### 2.1 Controller

典型职责：

- 声明 HTTP endpoint；
- 使用 `@Valid` 做请求结构校验；
- 使用 OpenAPI `@Tag`、`@Operation`；
- 使用 `@SaCheckPermission` 声明功能权限；
- 读取当前请求用户；
- 将业务委托给 Service。

参考文件：

- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/controller/LoginController.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/employee/controller/EmployeeController.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/department/controller/DepartmentController.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/menu/controller/MenuController.java`

可借鉴薄 Controller、入口校验和显式授权。不能照搬 GET 执行退出、删除或状态修改的接口风格。

### 2.2 Service

Service 实际承担：

- 资源存在性、唯一性、状态和关系校验；
- 请求模型到 Entity 的转换；
- DAO 查询与跨 Service 编排；
- 登录、权限等用例流程；
- 部分事务边界。

SmartAdmin 的 Service 经常直接返回 `ResponseDTO`，这会让业务层与 Web 响应耦合。xsy-scm 不应复制这一点。

### 2.3 Manager

Manager 不是所有请求的必经层，也不是领域模型中的统一“领域管理器”。其真实用途主要是：

- 多表原子写入；
- 批量插入或删除；
- MyBatis-Plus `ServiceImpl` 能力；
- 缓存加载和失效；
- 把一组持久化动作包裹在事务中。

参考文件：

- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/manager/LoginManager.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/employee/manager/EmployeeManager.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/role/manager/RoleMenuManager.java`
- `sa-base/src/main/java/net/lab1024/sa/base/module/support/dict/manager/DictManager.java`

结论：只在事务、批处理或缓存职责明确时引入等价协作者，不将 Manager 固化为 xsy-scm 的强制层。

### 2.4 DAO 与 XML

DAO 通常继承 MyBatis-Plus `BaseMapper`，简单 CRUD 使用框架能力，自定义 SQL 放在 mapper XML。该原则与 xsy-scm 一致。

参考文件：

- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/employee/dao/EmployeeDao.java`
- `sa-admin/src/main/resources/mapper/system/employee/EmployeeMapper.xml`
- `sa-admin/src/main/resources/mapper/system/role/RoleMenuMapper.xml`

参考工程 SQL 面向 MySQL，例如存在 MySQL 专用函数；xsy-scm 使用 PostgreSQL，SQL 不可复制。

## 3. 登录与认证链路

### 3.1 真实调用链

```text
POST /login
→ LoginController.login
→ LoginService.login
→ CaptchaService.checkCaptcha
→ EmployeeService.getByLoginName
→ EmployeeDao / EmployeeMapper.xml
→ SecurityLoginService.checkLogin
→ SecurityPasswordService.matchesPwd
→ StpUtil.login
→ LoginManager.loadLoginInfo
→ RoleEmployeeService / RoleMenuService
→ LoginLogService.log
→ LoginManager.loadUserPermission
```

主要文件：

- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/controller/LoginController.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/service/LoginService.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/manager/LoginManager.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/domain/LoginForm.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/login/domain/LoginResultVO.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/interceptor/AdminInterceptor.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/config/MvcConfig.java`

### 3.2 登录流程

`LoginService.login` 依次完成：

1. 登录设备类型校验；
2. 图形验证码；
3. 查询员工账号；
4. 删除、禁用状态校验；
5. 解密前端提交的密码；
6. 可选万能密码分支；
7. 可选邮箱验证码双因子；
8. 登录失败次数与锁定检查；
9. 密码校验；
10. Sa-Token 建立登录态；
11. 加载用户、部门、头像；
12. 清除失败计数；
13. 加载角色、菜单、功能点和上次登录信息；
14. 写登录成功日志；
15. 缓存用户资料和权限；
16. 返回 token、当前用户、菜单、改密标识和上次登录信息。

退出链路会注销 token、清除用户及权限缓存，并写退出日志。

### 3.3 Sa-Token 使用方式

SmartAdmin 父 POM 声明 Sa-Token `1.44.0`，并使用：

- `sa-token-spring-boot3-starter`；
- `sa-token-redis-jackson`；
- `StpUtil` 建立和注销会话；
- `StpInterface` 提供角色和权限列表；
- `@SaCheckPermission` 声明接口权限；
- 自定义 `AdminInterceptor` 读取 token、加载 `RequestEmployee`、设置 ThreadLocal、执行注解校验并在请求结束后清理上下文。

参考配置使用 `Authorization: Bearer`、UUID token、绝对超时、活跃超时和单账号并发策略。

### 3.4 可借鉴与不可照搬

可借鉴：

- 登录成功后统一加载身份、角色、权限和菜单；
- 区分绝对有效期与非活跃有效期；
- 成功、失败、锁定和退出均留登录日志；
- 禁用用户后撤销现有会话；
- 用户资料缓存与权限缓存分离；
- 权限关系变更后清理身份/权限状态；
- 请求用户上下文必须在请求结束时清理。

不可照搬：

- 万能密码；
- 30 天固定 token 策略；
- 前端密码加密替代 TLS；
- 自制盐值拼接密码算法；
- 因参考工程使用 Redis 就直接引入 Redis；
- GET logout；
- 向匿名调用者暴露过细的账号存在/禁用信息。

## 4. 用户、部门与组织

### 4.1 Employee 模型

SmartAdmin 的 `EmployeeEntity` 同时承担员工档案和登录账号，主要包含登录名、密码、姓名、头像、联系方式、部门、职位、管理员标识、禁用和软删除状态。角色通过 `RoleEmployee` 多对多关联。

新增链路：

```text
EmployeeController.addEmployee
→ EmployeeService.addEmployee
  → 登录名/电话/部门校验
  → 生成 employeeUid、初始密码及密码哈希
→ EmployeeManager.saveEmployee
  → EmployeeDao.insert
  → RoleEmployeeService.batchInsert
```

更新链路会在员工和角色关系写入后清理登录信息缓存。

可借鉴用户与角色关系的同事务更新、禁用强退、唯一约束和首次改密。不能假设 xsy-scm 的所有登录主体永久等同于员工，也不能用 JVM `synchronized` 替代数据库约束。

### 4.2 Department 模型

部门采用保存 `parentId` 的邻接树。`DepartmentCacheManager` 缓存全量部门、部门树、后代 ID 和完整路径。删除前检查子部门和员工引用。

xsy-scm 适配时还必须补充：

- 禁止移动到自身或后代节点；
- 检测树环；
- 明确根节点、停用和删除语义；
- 部门变化后失效关联用户的数据权限状态；
- 根据实际规模选择查询或缓存，不默认全量缓存。

## 5. 角色、菜单、按钮和 API 权限

### 5.1 SmartAdmin 模型

```text
Employee
→ RoleEmployee
→ Role
→ RoleMenu
→ Menu / Function Point
```

`MenuEntity` 同时描述目录、页面菜单和功能点，并保存路由、组件、图标、可见性、排序、`webPerms`、`apiPerms` 等信息。登录时按角色查询菜单与功能点，`LoginManager` 将 `apiPerms` 拆分去重后作为 Sa-Token 的权限列表。

主要文件：

- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/role/service/RoleService.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/role/service/RoleEmployeeService.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/role/service/RoleMenuService.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/menu/service/MenuService.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/menu/domain/entity/MenuEntity.java`
- `sa-admin/src/main/resources/mapper/system/role/RoleMenuMapper.xml`

### 5.2 API 清单

`sa-base/src/main/java/net/lab1024/sa/base/config/UrlConfig.java` 会扫描 Spring MVC handler，排除匿名或忽略登录的方法，供菜单管理页面查看接口清单。该扫描不等于真正授权；安全边界仍依赖 Controller 权限注解。

### 5.3 风险

- 菜单同时承担导航和安全资源，耦合较高；
- `apiPerms` 是逗号分隔字符串，缺少强关系约束；
- API 扫描无法证明敏感接口都声明了正确权限；
- 角色菜单变化后的用户权限缓存失效链路需要特别审计；
- 超级管理员完全跳过注解；
- 前端隐藏功能点不能防止直接 API 越权。

适合 xsy-scm 的结论是：菜单和权限独立建模；按钮使用稳定权限编码；后端方法授权是安全边界；数据库里的任意 URL 不能直接成为授权规则。

## 6. 数据权限

SmartAdmin 支持 `ME`、`DEPARTMENT`、`DEPARTMENT_AND_SUB`、`ALL` 等可见范围，并通过 MyBatis 插件按 Mapper 方法配置重写 SQL。当前参考实现的实际资源类型覆盖有限，且 SQL 重写依赖字符串寻找 `WHERE`、`GROUP BY`、`ORDER BY`。

可借鉴“角色对资源声明数据范围”和“无配置时最小权限”的思想，但不能复制字符串 SQL 重写。xsy-scm 后续应使用显式 `DataAccessScope`、Mapper 参数和对象级授权，分别处理仓库、客户、供应商、采购员、业务员和部门，并用集成测试证明横向越权不可发生。

## 7. 字典规范

SmartAdmin 使用两层模型：

```text
Dict(dictCode, disabledFlag, ...)
└─ DictData(dataValue, dataLabel, style, sort, disabledFlag, ...)
```

`DictService` 负责编码及字典项值的唯一性、状态和关系校验，`DictManager` 负责持久化及缓存清理，前端可加载字典项用于展示。

适合用于：

- 可运营的展示标签；
- 原因类型；
- 非核心基础选项；
- 展示颜色和样式。

不适合用于：

- 销售订单、采购订单、收货、退款状态；
- 库存流水类型；
- 价格来源；
- 参与状态机、计算或数据库 CHECK 的核心值。

核心业务状态继续使用 Java Enum、显式服务校验和数据库约束。

## 8. 日志规范

SmartAdmin 实际区分三类日志。

### 8.1 登录日志

`LoginLogEntity` 记录用户、类型、名称、IP/区域、User-Agent、设备、结果、备注和时间。成功、失败及退出都可以写入；管理员查看全部，普通用户查看自己。

参考文件：

- `sa-base/src/main/java/net/lab1024/sa/base/module/support/loginlog/LoginLogService.java`
- `sa-base/src/main/java/net/lab1024/sa/base/module/support/loginlog/domain/LoginLogEntity.java`
- `sa-admin/src/main/java/net/lab1024/sa/admin/module/system/support/AdminLoginLogController.java`

### 8.2 通用操作日志

`@OperateLog` + `OperateLogAspect` 在 Controller 成功或异常后记录用户、模块、操作说明、URL、Java 方法、参数、响应摘要、IP、User-Agent、结果和异常。写入通过自建线程池异步执行。

风险包括：

- 全参数序列化可能记录密码、token 和个人信息；
- Swagger 文案不适合作为稳定操作编码；
- 异步日志与业务事务不一致；
- 数据库保存完整堆栈会泄露实现并快速膨胀；
- 注解覆盖并不完整。

### 8.3 DataTracer

DataTracer 按 `dataId + type` 记录新增、修改、删除和字段差异，并用字段标签、枚举、字典生成可读变更内容。它更接近业务对象变更轨迹。

对 xsy-scm 的适配原则：登录安全日志、系统操作日志和领域业务审计分别建模；通用 AOP 只能做补充，不能替代订单、协议价、采购和库存事务内的结构化日志。

## 9. DTO、VO、Converter 与 Validation

### 9.1 SmartAdmin 命名

- 请求：`EmployeeAddForm`、`EmployeeUpdateForm`、`EmployeeQueryForm`；
- 响应：`EmployeeVO`、`DepartmentTreeVO`、`LoginResultVO`；
- 持久化：`Entity`；
- DTO 多用于少量内部传递。

xsy-scm 不需要为对齐参考工程而将现有 Request/Query/Response 重命名。

### 9.2 Validation

Controller 使用 `@Valid @RequestBody`；Form 使用 `@NotNull`、`@NotBlank`、`@Pattern`、长度约束、嵌套 `@Valid` 和枚举校验。Service 继续校验资源存在、唯一性、状态、关系和跨字段规则。

该边界值得采用：

```text
Bean Validation：请求结构与局部字段
Service：业务关系、状态和权限
Database：唯一、CHECK 和并发兜底
```

### 9.3 映射

SmartAdmin 大量使用 `SmartBeanUtil.copy`。xsy-scm 应继续使用领域内显式 Converter；重复且稳定的结构映射可评估 MapStruct，但不能用反射 Bean copy 作为默认策略，也不能把权限判断、计算或数据库访问隐藏在 Converter 中。

## 10. ErrorCode 与统一响应

SmartAdmin 将错误码分为 system、unexpected、user 等类别，`ResponseDTO` 包含 `code`、`level`、`ok`、`msg`、`data`、`dataType`，全局异常处理覆盖 Validation、JSON、Sa-Token、业务异常和未预期异常。

值得借鉴：

- 稳定、机器可读的错误码；
- 登录失效与权限不足分开；
- 生产环境隐藏异常细节；
- Validation 统一映射；
- 模块错误码有命名和测试。

不应照搬：

- Service 普遍返回 HTTP 响应包装；
- 多种业务错误压成通用参数错误；
- 增加重复的 `ok`、`level`、`dataType`；
- 将可预期业务异常映射为系统错误。

## 11. 提取给 xsy-scm 的编码规范

### 11.1 采用

1. 按领域组织系统模块，Controller 保持薄。
2. Bean Validation 负责 API 边界，Service 负责权威业务校验。
3. 简单 CRUD 用 MyBatis-Plus，自定义 SQL 只放 XML。
4. 多表授权更新在一个事务中完成。
5. 权限在后端显式声明，前端权限仅改善体验。
6. 登录态、用户上下文和权限状态有明确生命周期及失效机制。
7. 登录成功、失败、锁定和退出均可审计。
8. 用户、角色、权限、菜单关系变化后立即失效受影响身份状态。
9. 字典只承载可配置展示数据，不承载核心状态机。
10. 区分应用诊断日志、HTTP 操作日志、登录日志和领域审计日志。

### 11.2 不采用

1. 万能密码。
2. GET 执行状态修改。
3. 菜单记录直接决定任意 API URL 授权。
4. 前端隐藏按钮代替后端授权。
5. 字符串改写任意 SQL 实现通用数据权限。
6. `synchronized` 替代数据库唯一约束和并发策略。
7. 反射 Bean copy 作为默认映射。
8. Service 全部返回 Web `ResponseDTO`。
9. 通用日志记录全部请求参数、token、密码或完整堆栈。
10. 异步通用操作日志替代事务内领域审计。
11. 机械复制 MySQL 表结构、SQL、依赖或 Vue 页面。
12. 因参考项目使用 Sa-Token、Redis、Hutool、Guava 就整包引入。
