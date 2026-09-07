# SmartAdmin 参考实现使用规则

> **核心原则：SmartAdmin 是老师，xsy-scm 是产品。**
>
> 开发任何“系统通用能力”之前，先调查当前项目，再搜索
> `project-reference-examples/smart-admin-java17` 的对应实现；开发任何“供应链业务能力”之前，
> 先搜索 `xsy-scm-server` 和 `xsy-scm-web` 的当前实现。SmartAdmin 仅作只读参考，
> 不直接修改、不机械复制，也不替换当前 Java 21 + React 技术栈。

## 1. 参考仓库定位

项目中的外部参考工程位于：

```text
project-reference-examples/smart-admin-java17/
├── smart-admin-api-java17-springboot3/
├── smart-admin-web-typescript/
├── smart-app/
└── 数据库 SQL 脚本
```

该目录是 `REFERENCE IMPLEMENTATION`，不是 `PROJECT BASE`，不属于当前产品业务代码。

AI 可以：

- 阅读和搜索参考项目；
- 分析其设计思想、调用链和代码组织；
- 参考数据库、认证权限、日志、字典、文件等通用能力设计；
- 将适用的设计思想重新实现到当前技术栈中。

AI 禁止：

- 修改 `project-reference-examples/**`；
- 将 SmartAdmin 源码整体或机械复制到当前项目；
- 复制 SmartAdmin 的包名、表名、DTO 或 Controller 作为当前项目实现；
- 将当前 React 前端替换为 Vue，或并行维护 Vue 管理后台；
- 为模仿 SmartAdmin 而破坏、替换或大规模重构已有业务代码；
- 将 SmartApp 提前与当前后端强耦合。

## 2. 当前项目是事实源

正式项目是：

```text
xsy-scm-server/
xsy-scm-web/
```

执行优先级为：

```text
当前用户请求
→ AGENTS.md / CLAUDE.md
→ 当前 Sprint 规格及相关 docs
→ 当前项目实现
→ 当前测试
→ 本参考规则
→ SmartAdmin 具体实现
→ 框架惯例
```

当 SmartAdmin 与当前项目不同：

```text
当前项目已有成熟规范
→ 保留当前规范

当前项目缺少通用能力或规范
→ 调查 SmartAdmin 并选择性借鉴

当前实现明显不完整
→ 对比后提出适配方案，不直接照搬

技术栈或领域模型不同
→ 迁移设计思想，使用当前技术栈重新实现
```

Sprint 专项规格高于旧版设计草案；SmartAdmin 不得覆盖已经确认的供应链业务规则。

## 3. 后端参考原则

当前后端：

```text
xsy-scm-server/
Java 21 + Spring Boot
com.xianshuyuan.scm
```

后端参考：

```text
project-reference-examples/smart-admin-java17/
└── smart-admin-api-java17-springboot3/
```

开发下列系统通用能力前，应优先搜索 SmartAdmin 的对应实现：

```text
登录 / 退出登录
认证 / Token 生命周期
用户 / 员工
角色 / 权限 / 菜单
部门 / 组织架构 / 数据权限
按钮权限 / 接口权限
字典
操作日志 / 登录日志 / 异常日志
文件上传下载与元数据
统一异常 / 参数校验 / 统一响应
分页 / 审计字段 / 状态枚举
验证码 / 配置管理 / 消息通知
```

执行顺序：

1. 搜索 `xsy-scm-server` 是否已有实现、依赖、数据表和测试；
2. 搜索 SmartAdmin 对应模块并梳理完整调用链；
3. 对比数据模型、API、事务、异常、权限和依赖差异；
4. 给出适配当前项目的推荐方案和影响范围；
5. 获得确认后，按当前领域结构实现；
6. 补充与风险相称的测试和验证。

任何借鉴都必须转换为 `com.xianshuyuan.scm` 下符合当前模块化单体和领域分包规则的实现。不得为了模仿参考项目引入全局 Controller、Service、Mapper、Entity 包树。

## 4. 前端参考原则

当前前端：

```text
xsy-scm-web/
React + TypeScript + Ant Design / ProComponents
React Router + TanStack Query + Axios
```

前端参考：

```text
project-reference-examples/smart-admin-java17/
└── smart-admin-web-typescript/
    Vue 3 + TypeScript + Ant Design Vue
```

由于框架不同，禁止复制 Vue 源码。只能参考：

- 页面布局和系统管理信息架构；
- 菜单、权限和按钮权限交互；
- 表格字段、筛选方式及新增编辑流程；
- 详情页、字典展示、操作日志页面；
- 登录、加载、空状态、错误和重试体验。

然后使用当前 React 技术栈重新实现，例如：

```text
SmartAdmin Vue 的 v-if 权限
→ 当前项目的 Permission / AuthGuard / hook
```

优先复用当前项目已有布局、组件、Hooks、API 边界、查询缓存、表单和样式令牌。不得引入 Vue，也不得让 SmartAdmin 的视觉或路由约定破坏当前 Admin Theme 和双侧栏布局。

## 5. 优先研究的通用模块

### 登录认证

重点理解：

- 登录接口和身份校验；
- Token 创建、续期、失效和退出；
- 当前用户、权限和菜单加载；
- 登录失败和安全审计。

只借鉴流程和边界，不复制耦合代码。

### 用户、员工和组织

重点参考：

- 账号状态和密码处理；
- 员工、部门和组织关系；
- 用户与角色的绑定；
- 数据权限边界。

最终模型必须由当前项目实际需求决定。

### RBAC 权限

重点分析：

```text
Role
Menu
Permission
EmployeeRole / UserRole
RoleMenu / RolePermission
```

以及菜单、按钮、接口和数据范围权限的协作方式。当前项目采用符合自身命名和领域边界的等价模型，不照搬 SmartAdmin 表结构。

### 菜单系统

参考目录、菜单、按钮、路由、权限标识、显示顺序、隐藏和启用状态。React 侧按当前 React Router 和布局结构实现权限菜单或动态路由。

### 字典

可参考 `Dict` / `DictItem` 的管理方式，但核心业务状态优先使用代码中的 Enum。不要把订单、采购、库存等所有关键状态都变成可任意修改的数据库字典。

### 日志

参考操作日志、登录日志和异常日志。当前项目还需覆盖价格修改、订单取消、采购修改、库存调整、退款、账期修改、客户价格修改和权限变更等业务审计场景。

### 文件

参考上传、下载、文件元数据和存储抽象，用于商品图片、客户或供应商附件、采购凭证及订单附件。存储方案和权限边界必须符合当前项目要求。

## 6. 新功能开发工作流

接到角色权限、登录、字典、日志、文件等系统通用能力任务时，不直接编码。先输出并确认以下调查结果：

```text
### 当前项目调查
- 已有相关代码、依赖和数据库表
- 已有后端与前端实现
- 已有测试和约束

### SmartAdmin 参考
- 对应模块位置
- 核心实现和完整调用链
- 数据模型、API 与权限模型

### 差异
- 当前项目与 SmartAdmin 的关键差异

### 推荐方案
- 复用哪些当前实现
- 借鉴哪些设计思想
- 哪些内容需要按当前技术栈重新实现

### 影响范围
- 数据库
- 后端
- 前端
- 测试与迁移
```

非平凡实现继续遵循仓库既有的计划和用户确认流程。

## 7. 供应链业务代码处理

商品、客户、销售订单、采购、库存以及后续供应商、分拣、配送和财务等属于 xsy-scm 业务域。此类能力首先以当前 Sprint 规格、现有实现和测试为准，SmartAdmin 不作为替代品。

对已有模块：

```text
已有可用代码
→ 不重写

已有测试
→ 必须保留并按需扩展

已有 API
→ 尽量兼容

已有 Flyway 迁移
→ 绝不修改已经应用的迁移

已有领域模型
→ 在原模型上渐进演进
```

如果规范不统一，采用“新代码执行当前最新规范，旧代码修改到哪里治理到哪里”的原则，不做大爆炸式重构。

## 8. 禁止为了统一规范进行大规模重构

除非用户明确批准并有独立迁移方案，否则禁止：

- 一次性修改全部 Controller、Service、DTO 或 API；
- 一次性替换全部异常和响应处理；
- 一次性重新命名所有数据库对象；
- 一次性重写所有测试；
- 用 SmartAdmin 的架构取代当前模块化单体；
- 为统一表面风格破坏既有兼容性和已验证行为。

采用渐进治理：新模块遵循最新规范，老模块仅在相关业务修改时局部治理。

## 9. SmartApp 使用方式

当前 PC 管理端和核心业务链稳定前，`smart-app` 仅作参考，不接入正式项目。

未来需要客户商城、员工移动端、微信小程序、H5 或 App 时，再根据已确认需求评估：

```text
参考并复用 SmartApp 的适用思想
或
新建 xsy-scm-app / UniApp 项目并重新实现
```

不得提前引入与当前阶段无关的移动端耦合。

## 10. 搜索参考代码的方法

应主动搜索并跟踪完整调用链，而不是只读取同名 Controller。

示例关键词：

```text
登录：login, token, sa-token, LoginController, LoginService
权限：permission, role, menu, RoleMenu, RoleEmployee
日志：log, operate, loginLog
字典：dict, dictionary
文件：file, upload, download, storage
组织：employee, department, dataScope
```

调查应覆盖入口、服务、数据访问、实体/DTO、配置、拦截器或切面、数据库脚本、前端调用和测试。

## 11. 参考实现迁移流程

任何实现都不得机械复制。必须经过：

```text
理解 SmartAdmin 的设计意图
↓
去除 SmartAdmin 专属耦合
↓
对照 xsy-scm 现有规范和业务模型
↓
适配 Java 21、当前 Spring Boot 与包结构
↓
适配当前 API、异常、事务和持久化风格
↓
使用 React 重写前端交互
↓
补充测试与验证
```

如果参考实现依赖 Redis、特定认证框架或其他当前项目未采用的基础设施，先证明其必要性，不得因为 SmartAdmin 使用了它就直接引入。

## 12. 当前使用重点

SmartAdmin 当前主要用于帮助建设以下通用能力：

```text
登录
用户 / 员工
角色 / 权限 / 菜单
组织架构和数据权限
操作日志 / 登录日志
字典
文件
统一异常及通用后台交互
```

供应链能力继续由 xsy-scm 自身规格和领域模型驱动。整体目标是：

```text
参考 SmartAdmin 的成熟通用设计
+
保留 xsy-scm 已完成业务
+
按当前技术栈重新实现
+
渐进统一工程质量
+
补齐缺失的系统能力
```
