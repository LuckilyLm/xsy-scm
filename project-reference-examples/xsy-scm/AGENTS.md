# AGENTS.md（AI 协作约定）

> 本仓库 XSY-SCM（基于 SmartAdmin v3.30.0 二次开发的供应链管理系统）的 AI 协作约定。
> 完整初始化规程见 `XSY_SCM_JAVA17_AI_EXECUTION_GUIDE.md`。
> 下方「XSY-SCM 专属规则」为供应链业务约束；其余为通用 Java17 / Vue3 编程规程（**整理自官方 SmartAdmin 规范：官方链接、官方介绍与来源说明均保持原样未改写**，仅把指代"本项目"的名称改为 XSY-SCM，并适配本仓库实际路径）。

## XSY-SCM 专属规则
## 铁律

- 禁止改 Java17 / Spring Boot 大版本 / MyBatis-Plus / Vue3 / Ant Design Vue / Sa-Token
- 禁止拆微服务、引入 Spring Cloud、拆多仓库
- 禁止删除 XSY-SCM 核心模块与官方示例（二开参考模板）
- 禁止一次性重构整个项目、无理由升级依赖

## 编码前必做

1. 阅读 XSY-SCM 现有同类型模块（Controller / Service / Manager / Dao / Entity / DTO / VO）
2. 总结当前项目编码规范，查找可复用组件、工具类、已有依赖
3. 先设计数据库与 API，再编码

## 分层与复用

- 后端复用：Lombok、MyBatis-Plus、Hutool、Apache Commons、Sa-Token、Validation、fastexcel
- 前端新增页面必须找到 XSY-SCM 原生相似页面作为模板，复用现有 request / Table / Form / 权限机制
- 不重复造分页、权限、异常、Result、Bean Copy、日期、ID 生成器

## 数据规则

- 金额、重量：Java 用 `BigDecimal`，数据库用 `DECIMAL`，禁止 double/float
- 重量内部统一单位 kg，展示层再转换
- 状态字段使用 Enum，前后端语义统一，禁止业务代码散落 0/1/2
- 每张业务表明确：主键、业务编号、状态、创建/更新时间、创建/更新人、逻辑删除
- 默认不依赖 MySQL 物理外键，关系由业务层维护

## 业务规则

- 库存必须「余额 + 流水」，任何模块不得自行 UPDATE 库存，统一走库存业务层
- 订单价格使用快照，禁止关联实时商品价格
- 订单 / 采购 / 库存流水 / 退款 / 配送 / 财务 原则上不允许物理删除
- 改价、取消、退款、收货、盘点、报损报溢等操作必须留审计日志

## 提交前

- 后端：`mvn -P dev clean compile`
- 前端：`npm run build:prod`（或项目实际 build 脚本）+ lint / type-check
- xsy-app：H5 编译通过

## 开发节奏

需求确认 → 现有代码调查 → 数据模型 → SQL → 后端 API → 测试 → PC 后台 → xsy-app → 联调 → 提交
禁止一次让 AI 同时生成所有业务模块。

---

## 通用编程规程（Java17 / Vue3）

## 0. 最重要的执行原则

### 0.1 规则优先级

AI 修改代码时按以下顺序判断：

1. 当前任务的明确业务需求
2. 当前仓库已经存在的实现、目录、命名和技术选型
3. 当前仓库已有 `AGENTS.md` / README / 代码检查规则
4. SmartAdmin 官方当前版本的代码规范和架构方式
5. 本文件
6. 一般性的行业最佳实践

若通用“最佳实践”和 XSY-SCM 当前项目已有实现冲突，**优先保持项目一致性**，不要擅自重构整套架构。

安全、合规、数据正确性高于风格规则。

### 0.2 先读现有代码，再写代码

开始实现任何功能前，必须先找到至少一个项目内相似实现，优先复用其：

- package / 目录结构
- Controller / Service / Manager / Dao 分层
- Form / VO / DTO / BO / Entity 用法
- ResponseDTO / 错误码
- 权限注解
- 数据范围
- API 封装
- Vue 页面布局
- 表格、弹窗、抽屉、表单组件
- 常量 / 枚举
- Loading、上传、帮助文档等已有能力

禁止脱离项目现状，凭空设计另一套风格。

### 0.3 最小改动原则

默认采取“最小必要改动”：

- 不因为开发一个功能顺便重构无关模块。
- 不因为个人偏好调整大面积格式。
- 不随意改公共返回结构。
- 不随意改权限体系。
- 不随意改数据库基础字段约定。
- 不随意调整全局 axios、router、layout、theme。
- 不随意升级依赖版本。
- 不引入与已有框架功能重复的库。

### 0.4 优先复用，禁止重复造轮子

在新增工具、组件、依赖之前依次检查：

1. JDK / Spring Boot 是否已有能力；
2. SmartAdmin `sa-base`（本项目已更名为 `xsy-scm-base`）/ common / util 是否已有；
3. 项目内是否已有封装；
4. Hutool 是否已有；
5. Vue 项目 components / lib / utils / constants 是否已有；
6. 最后才考虑新依赖。

---

# 1. 高质量代码总原则

SmartAdmin 的代码质量判断顺序：

1. **满足业务需要**
2. **代码清晰明了**
3. **代码尽可能少**
4. **在前 3 点成立的基础上再追求复用和模块化**

AI 必须避免“为了抽象而抽象”“为了设计模式而设计模式”。

具体执行：

- 能用简单方案解决，不引入复杂架构。
- 小范围重复代码如果抽象后反而难读，可以保留。
- 业务逻辑复杂时，优先拆清楚逻辑，再考虑复用。
- 不制造只有一处使用、没有明显价值的抽象层。
- 不创建大量 `Util`、`Helper`、`Factory`、`Strategy` 只为形式完整。
- 不把简单 CRUD 做成复杂领域框架。
- 删除无用代码，不要长期注释掉。
- TODO / FIXME 必须说明原因和待处理事项；能立即完成就不要留下 TODO。
- 修改代码时同步维护注释，禁止“代码已经变了，注释还是旧逻辑”。

---

# 2. 架构选型规则

## 2.1 默认保持 SmartAdmin 单体 / 模块化架构

除非用户明确要求并已有真实业务拆分依据，否则：

- 不要把 SmartAdmin 主动改造成微服务。
- 不要为了“以后可能扩展”提前拆服务。
- 不要新增 Nacos、Gateway、Feign、MQ、分布式事务等基础设施，除非业务确实需要。
- 不要为了一个普通中后台功能单独创建新服务。

只有在以下条件较明确时才建议拆微服务：

- 已能稳定划清业务领域边界；
- 有长期维护需求；
- 有足够人员、基础设施和运维能力；
- 拆分收益明显高于复杂度成本。

## 2.2 业务优先于技术炫技

架构决策同时考虑：

- 业务复杂度
- 项目生命周期
- 团队人数与熟练度
- 基础设施
- 维护成本
- 发布时间
- 性能与稳定性
- 后续扩展

禁止仅因“某技术流行”而引入。

---

# 3. Java17 / Spring Boot 3 基线

## 3.1 Java 版本

本项目按 Java 17+ 编写。

- 不回退 Java 8 写法来规避问题。
- 新代码使用项目当前已经采用的 Java 17 特性即可，不为了“新语法”强行改旧代码。
- 编译目标保持仓库 `pom.xml` 实际配置。

## 3.2 Spring Boot 3 / Jakarta

Spring Boot 3 项目优先使用 Jakarta 命名空间，例如：

- `jakarta.persistence.*`
- `jakarta.validation.*`
- `jakarta.servlet.*`
- `jakarta.annotation.*`
- `jakarta.transaction.*`

不要在 Java17 / Boot3 模块中新引入老的 `javax.*` API，除非第三方库明确要求且仓库已有兼容方案。

## 3.3 依赖版本

- 版本统一由项目 parent / BOM / dependencyManagement 管理。
- 子模块已有统一版本管理时，不单独写依赖版本。
- 不为了一个小功能升级 Spring Boot / MyBatis-Plus / Sa-Token / Druid 等核心依赖。
- 新依赖必须先确认 Boot3 / Java17 兼容性。
- 同类依赖只能保留一套主方案。

---

# 4. Java 命名与方法规则

## 4.1 项目名

项目 / Maven module 名：

- 全小写
- 使用 `kebab-case`

示例：

```text
mall-management-system
order-service-client
user-api
```

## 4.2 英文命名

- 使用正确、常见、符合业务语义的英文。
- 禁止拼音式命名，除非业务专有名词确实如此。
- 同一业务词在前端、后端、数据库、Redis、Docker、接口中保持一致。

例如统一使用 `notice`，不要后端叫 `notice`、前端叫 `news`、移动端叫 `message`。

## 4.3 名词和动词

- 类、模块、表、实体等使用名词。
- 方法使用动词或动宾结构。

示例：

```java
OrderService
createOrder()
queryOrder()
updateOrder()
deleteOrder()
```

## 4.4 方法参数

单个方法参数原则上不超过 **5 个**。

超过时：

- 封装成 Form / DTO / BO 等对象；
- 不依靠多个同类型参数的位置区别业务语义。

---

# 5. Java package 与模块目录

## 5.1 common

通用能力放 common，例如：

```text
common/
  anno/
  constant/
  domain/
  exception/
  json/
  swagger/
  validator/
```

只有真正跨业务通用的代码才能进入 common。

禁止把某个业务模块专用代码塞进 common。

## 5.2 module

业务代码按业务模块组织，而不是把所有 Controller、Service、Dao 平铺到全局目录。

推荐结构：

```text
module/
  role/
    RoleController.java
    RoleService.java
    RoleManager.java
    RoleDao.java
    RoleConst.java
    domain/
      RoleEntity.java
      RoleForm.java
      RoleVO.java
```

新增模块时优先参考当前仓库同类型业务目录，不自行发明另一套结构。

---

# 6. 后端分层：Controller → Service → Manager → Dao

## 6.1 Controller

Controller 负责：

- 路由
- 参数接收
- `@Valid` 等简单参数校验
- 调用 Service
- 返回统一响应

Controller 禁止：

- 写核心业务逻辑；
- 直接操作 Dao；
- 拼装复杂业务数据；
- 开事务；
- 做复杂业务校验；
- 写大量转换代码。

Controller 应尽量短。

## 6.2 Service

Service 负责：

- 业务流程编排；
- 业务校验；
- 调用 Manager；
- 必要的数据准备；
- 组织返回结果。

当一个 Service 明显过大时，按业务动作拆分，例如：

```text
OrderQueryService
OrderCreateService
OrderDeliverService
OrderValidatorService
```

不要为了减少类数量制造几千行的 Service。

## 6.3 Manager

Manager 是 SmartAdmin 架构中的重要层，不要擅自删除。

主要职责：

- 对第三方平台做封装；
- 下沉 Service 的通用能力；
- 缓存 / 中间件通用处理；
- 多 Dao 组合；
- 多表事务；
- 拆分复杂数据库操作；
- 封装无强业务属性的数据库操作。

规则：

- Service 可以调用 Manager。
- Manager 可以调用 Dao。
- **不同业务 Manager 之间禁止随意互调**，避免事务嵌套和耦合。
- 事务优先下沉到 Manager 的小范围数据库操作中。
- 不要把 Manager 做成另一个 Service。

## 6.4 Dao

持久层优先遵循项目现有 MyBatis-Plus + XML 方式。

- Dao 按项目约定继承 `BaseMapper`。
- 优先使用项目现有 Mapper XML。
- 官方规范不推荐 MyBatis-Plus Wrapper 条件构建器；当前项目如果遵循此规则，则继续使用明确 SQL / XML。
- 不在 XML 中直接写死本应由业务层传入的状态常量。
- SQL 应可搜索、可定位、可维护。
- 多表 JOIN 较复杂时优先考虑拆解或 Manager 组合，不为了“一条 SQL 完成所有事情”制造不可维护 SQL。
- JOIN 使用别名还是全表名，优先跟随仓库现有风格；官方倾向可读性强的完整表名。

---

# 7. 事务规则

`@Transactional` 必须谨慎使用。

## 7.1 原则

- 事务范围尽可能小。
- 业务校验尽量在开启事务前完成。
- 尽量先准备数据，再进入事务。
- 不在长时间网络调用、文件操作、复杂循环外层包大事务。
- 默认使用：

```java
@Transactional(rollbackFor = Exception.class)
```

具体以仓库现状为准。

## 7.2 优先下沉到 Manager

推荐：

```text
Service:
  业务校验
  查询与准备数据
  ↓
Manager:
  @Transactional
  只执行真正需要原子性的数据库操作
```

## 7.3 注意 Spring 自调用事务失效

不要在同一个 Bean 中依赖“方法 A 直接调用带 `@Transactional` 的方法 B”来开启事务。

优先：

- 提取到 Manager / 独立 Bean；
- 不为了绕代理问题滥用 `AopContext`。

---

# 8. JavaBean / Domain 对象规则

## 8.1 通用规则

JavaBean：

- 不写业务逻辑；
- 不写计算逻辑；
- 数据库 / 请求对象字段尽量使用包装类型；
- 不随意给字段默认值；
- 字段有清晰注释；
- 按项目现状使用 Lombok 减少样板代码。

优先复用已有 Lombok 约定，如：

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
```

不要手写大量 getter / setter。

## 8.2 对象职责

```text
XxxEntity  数据库持久对象
XxxForm    前端 / RPC 请求对象
XxxVO      返回前端 / RPC 的对象
XxxDTO     数据传输对象
XxxBO      Service / Manager / Dao 内部业务对象
```

### Entity

- 与表语义对应；
- 字段与数据库字段匹配；
- 不加入无关组合对象；
- 时间统一使用项目已有的 `LocalDateTime` / `LocalDate`；
- 不直接拿 Entity 当接口请求或响应模型。

### Form

- 不继承 Entity；
- 只用于请求参数；
- 根据当前项目使用 validation 注解。

### VO

- 不继承 Entity；
- 用于对外响应；
- 不把内部敏感字段直接暴露。

### BO

- 仅用于后端内部处理；
- 不作为 Controller 对外接口模型。

---

# 9. Boolean / Flag 规则

如果当前项目沿用 SmartAdmin 官方约定：

- Java 布尔属性使用 `xxxFlag`
- 数据库使用 `xxx_flag`

例如：

```text
deletedFlag / deleted_flag
onlineFlag  / online_flag
```

不要新增 `isDeleted`、`is_deleted` 风格混用。

---

# 10. 数据库规范

## 10.1 数据库名

- 小写
- 下划线分隔
- 环境清晰

示例：

```text
smart_admin_dev
smart_admin_test
smart_admin_prod
```

## 10.2 表名

官方风格：

```text
t_employee
t_department
t_config
```

新增表是否继续 `t_` 前缀，以当前仓库最新迁移脚本为最终依据。

## 10.3 基础字段

普通业务表通常至少考虑：

```text
[module]_id
create_time
update_time
```

但日志、流水、纯关联表等按业务需要，不机械添加无意义字段。

## 10.4 枚举字段

数据库字段注释必须说明枚举含义，例如：

```sql
sync_status COMMENT '同步状态：0 未开始，1 同步中，2 成功，3 失败'
```

新增 / 修改状态值时，同步更新：

- 数据库注释
- 后端枚举
- 前端常量 / 枚举
- API 文档

## 10.5 变更数据库时

- 优先使用项目已有 Flyway / migration 机制；
- 不直接要求用户手工改生产库；
- migration 必须可重复部署且版本有序；
- 先确认数据库兼容范围，尤其项目支持 MySQL、PostgreSQL、Oracle、国产数据库时，避免无必要使用某一数据库独有语法。

---

# 11. API 与返回值

## 11.1 URL

SmartAdmin 官方习惯偏动作式 URL：

```text
GET  /department/get/{id}
POST /department/query
POST /department/add
POST /department/update
POST /department/delete/{id}
```

实际开发时：

- **优先复制当前仓库已有 Controller 的 URL 风格**；
- 不为追求 RESTful 统一而大面积改历史接口；
- 不为了迎合旧文档，机械新增不安全的 GET 删除接口；
- 有副作用的操作优先使用 POST / PUT / DELETE 等项目当前约定方式。

## 11.2 ResponseDTO

统一使用项目现有响应封装，例如 `ResponseDTO<T>`。

不要新增另一套：

```text
Result
ApiResult
CommonResponse
R
```

除非仓库已经迁移到了新统一结构。

## 11.3 错误码

错误应优先使用项目已有错误码体系，不在代码中散落魔法字符串。

可按项目设计区分：

- system：系统异常；
- unexpected：不应出现的异常业务状态；
- user：用户操作 / 校验错误。

新增业务模块时，优先创建或扩展该模块自己的错误码常量 / 枚举，而不是随意返回字符串。

## 11.4 API 文档

- 每个对外接口需要有清楚的接口描述。
- 注释格式、OpenAPI / Swagger 注解类型必须以当前 Java17 仓库实际依赖为准。
- **不要因为老文档示例使用 `@ApiOperation` 就强行引入 Swagger2 注解。**
- 如果项目已有 springdoc / OpenAPI3，则继续使用对应注解。
- 作者字段如果当前仓库仍保留，则跟随现有项目约定；不要自行在全项目批量补作者。

---

# 12. 校验、异常与日志

## 12.1 参数校验

- Controller 使用 `@Valid` / Bean Validation 做结构性校验。
- 复杂业务条件在 Service。
- 不把所有判断堆进 Controller。

## 12.2 异常

- 优先使用项目已有 `BusinessException`、全局异常处理器、错误码体系。
- 不在每个 Controller 重复 try/catch。
- 不吞异常。
- 日志中保留必要上下文，但不能输出密码、token、密钥、完整身份证、银行卡等敏感数据。

## 12.3 环境差异

系统需要能区分：

- local
- dev
- test
- pre
- prod

开发 / 测试可以返回更多诊断信息，生产环境禁止向用户返回堆栈和内部异常详情。

---

# 13. 数据范围 / 权限数据隔离

若需求涉及“不同用户看到不同数据”，先检查 SmartAdmin `@DataScope` 体系。

优先考虑：

- 部门数据范围
- 员工数据范围
- 自定义策略
- 已有数据范围枚举与 SQL 拼接机制

不要在每个 Service / Mapper 手写一套：

```java
if (role == xxx) ...
```

注意：

- 权限校验必须在后端生效；
- 前端隐藏按钮不等于权限控制；
- 自定义数据范围必须验证 SQL 安全性；
- 不直接拼接用户可控 SQL。

---

# 14. 防重复提交 / 限流

若需求是：

- 短信发送频率限制
- 登录频率限制
- 表单重复点击
- 短时间重复请求

先检查项目现有 `@RepeatSubmit` 能否满足。

官方思路：

- 单实例：可使用 Caffeine；
- 多实例：使用 Redis；
- ticket 可按 URL + 用户等维度构造。

AI 不应重新写一套重复提交拦截器，除非已有能力明确不满足。

对于支付、库存、订单等关键写操作，除了重复提交控制，还应根据业务需要设计真正的幂等键和数据库唯一约束，不能只靠时间窗口限流。

---

# 15. 定时任务 SmartJob

新增定时任务前优先检查项目是否已启用 SmartJob。

适用时优先使用现有 SmartJob，而不是直接新增：

- Quartz
- XXL-JOB
- Elastic-Job
- 其他调度平台

SmartJob 适合项目当前“简单、好用、够用”的任务场景，支持：

- cron
- 固定间隔
- 参数
- 动态添加 / 修改
- 启停
- 手动执行
- 执行记录
- 多实例避免重复执行
- 数据库配置动态刷新

只有 SmartJob 明确不满足需求时，再评估新的调度系统。

---

# 16. 文件上传

文件相关功能优先复用 SmartAdmin 已有文件服务抽象。

设计原则：

- S3 协议优先；
- 兼容主流云 OSS / OBS / COS 等；
- 可兼容 MinIO；
- 私有化部署可支持本地存储；
- 上传记录入库；
- 记录文件名、大小、时间等元信息；
- 区分公共文件与私有文件。

禁止：

- 每个业务模块自己实现一套云 SDK；
- 把永久公网 URL 直接写死在业务表；
- 绕开现有文件权限体系。

---

# 17. 数据变更记录 DataTracer

对于重要业务数据的新增、修改、删除，优先检查 DataTracer 能否复用。

重点数据变更记录至少考虑：

- 操作时间
- 操作用户
- IP / 设备
- 修改前
- 修改后
- 业务类型
- 操作类型

复杂对象可以比较前后 JavaBean 并保存差异。

不要在每个业务表额外发明自己的 `xxx_change_log`，除非业务审计要求确实不同。

---

# 18. 接口加解密

仅当明确需求要求应用层报文加解密时，才使用 SmartAdmin 已有接口加解密体系。

现有设计思想：

- 请求解密通过 Controller advice / RequestBody advice；
- 响应加密通过 ResponseBody advice；
- 可扩展算法；
- 前后端密钥 / 算法必须一致。

安全要求：

- 应用层 AES / SM4 **不能替代 HTTPS/TLS**；
- 禁止把真实生产密钥硬编码提交 Git；
- 密钥应通过安全配置 / 密钥管理注入；
- 只有确实需要的接口才加密，不要全局无脑开启。

---

# 19. Smart Reload

若需求是“不重启进程刷新某项内存配置 / 缓存”，优先检查 Smart Reload。

官方实现倾向：

```text
数据库 reload item
  ↓
后台轮询
  ↓
变化检测
  ↓
事件 / 监听者
  ↓
刷新对应内存数据
```

不要为了简单本地缓存刷新就引入 Kafka / MQ / Zookeeper。

如果系统已经有成熟 Redis Pub/Sub、MQ 或配置中心，并且项目现状使用它们，则优先保持现有方案。

---

# 20. HeartBeat

需要了解应用实例状态时，优先检查 SmartAdmin heartbeat。

典型记录：

- 项目启动路径
- 服务器 IP
- 进程号
- 进程启动时间
- 最近心跳时间

不要为了极简单实例状态需求立即引入复杂服务发现组件。

若项目已经处于 Kubernetes / Prometheus / 注册中心体系，则应优先使用现有基础设施，而不是重复维护两套心跳系统。

---

# 21. 编号生成器

合同号、订单号、单据号等有规则编号时，先检查项目已有 Serial Number / 编号生成器。

常见规则：

```text
[yyyy]
[mm]
[dd]
[nnnn]
```

增长周期可能包括：

- 不重置
- 按年
- 按月
- 按日

不要在多个 Service 中各写一套：

```java
"HT" + LocalDate.now() + random...
```

并发编号必须保证唯一性，必要时依赖数据库锁、唯一约束或项目已有生成器机制。

---

# 22. 工具类与 Bean 转换

## 22.1 优先现有工具类

优先检查：

```text
sa-base（本项目已更名为 xsy-scm-base）
com.xsy.scm.base.common.util
```

以及项目自己的 common util。

例如已有：

```text
SmartRequestUtil
SmartBeanUtil
```

先复用，不重复封装。

## 22.2 Bean 转换

如果项目当前使用 `SmartBeanUtil`，简单对象复制优先沿用。

如果业务映射复杂、编译期映射价值明显，且项目已使用 MapStruct，则优先 MapStruct。

如果项目尚未引入 MapStruct：

- 不为了一个简单 Bean copy 新增 MapStruct；
- 多处复杂转换明显能降低维护成本时，才考虑新增，并先确认用户 / 项目允许。

---

# 23. 代码生成器

CRUD 功能可以使用 SmartAdmin 代码生成器作为起点，但生成结果不是“完成品”。

使用前：

- 表和列必须有完整注释；
- 先理解业务；
- 明确字段类型、枚举、权限、数据范围；
- 确认生成目录。

生成后必须人工 / AI 二次检查：

- Controller 是否过重
- Service 逻辑
- Manager 事务
- SQL
- Form 校验
- VO 暴露字段
- 权限
- 错误码
- 前端表单
- 枚举
- API
- 测试

禁止“生成完能编译就算完成”。

---

# 24. 万能密码：安全覆盖规则

SmartAdmin 文档存在“万能密码”能力，但 AI **不得默认启用或新增生产万能密码**。

只有用户明确要求并充分理解风险时才允许评估，而且至少必须：

- 默认关闭；
- 生产环境显式配置才可启用；
- 密钥不可进入 Git；
- 强审计；
- 极短 token 有效期；
- 限定来源 / 管理员权限；
- 可快速吊销；
- 不输出到日志；
- 有安全评审。

一般调试优先采用：

- 管理员模拟身份；
- 测试环境账号；
- 临时 impersonation；
- 受审计的运维工具。

安全规则高于官网功能示例。

---

# 25. 前端技术选型：JS / TS

不要在已有项目中擅自切换 JS ↔ TS。

- 当前仓库是 JS：继续 JS。
- 当前仓库是 TS：继续 TS。
- 不因某个新模块“更现代”单独混入另一套语言风格。

如果是 TS 项目：

- 禁止把 `any` 当成逃避类型设计的默认方案；
- API 请求 / 返回模型需要明确类型；
- 方法参数 / 返回值按项目规则声明；
- 不关闭 TypeScript 检查来解决报错。

如果是 JS 项目：

- 通过命名、模块化、枚举、注释、目录规范保证可维护性。

---

# 26. Vue3 基础规范

## 26.1 项目 / 文件命名

普通目录和业务页面文件：

- 小写
- `kebab-case`

示例：

```text
head-search/
shopping-car/
role-form.vue
smart-logo.png
```

组件实际命名以仓库当前约定为准；SmartAdmin 文档整体倾向文件名使用 kebab-case。

## 26.2 引号与分号

若当前前端项目保持 SmartAdmin 官方风格：

- template 属性使用双引号；
- JS 字符串使用单引号；
- JS 语句保留分号。

不要为了个人 Prettier 偏好改全项目。

---

# 27. Vue3 Composition API

## 27.1 使用 `<script setup>`

新 Vue3 组件优先沿用项目已有 `<script setup>`。

不要在同一业务模块中新写 Options API，除非现有代码就是该风格且统一迁移成本不合理。

## 27.2 按“业务关注点”组织代码

Composition API 不应退化成：

```text
先写所有变量
再写所有方法
```

而应按逻辑块组织：

```text
组件输入 / emit / expose
查询模块：变量 + 方法
批量操作：变量 + 方法
表单模块：变量 + 方法
上传模块：变量 + 方法
```

一个逻辑关注点的状态和方法尽量放在一起。

## 27.3 Template Ref

模板引用变量：

```js
const formRef = ref();
```

要求：

- 变量以 `Ref` 结尾；
- template 的 `ref` 名称保持一致。

---

# 28. Vue 注释

按 SmartAdmin 项目风格：

- 状态变量应有简明业务注释；
- 关键方法有业务注释；
- 复杂逻辑块可用分隔注释；
- 不为明显代码添加废话注释；
- 修改逻辑同步更新注释。

---

# 29. Vue 组件规则

## 29.1 组件职责

- 页面过大时拆组件；
- 跨页面通用组件放公共 components；
- 仅当前业务复用的组件放业务模块自己的 `components/`；
- 不把业务组件强行放全局。

## 29.2 紧耦合子组件命名

子组件应体现父组件语义，例如：

```text
todo-list.vue
todo-list-item.vue
todo-list-item-button.vue
```

避免晦涩缩写。

## 29.3 组件属性

属性很多时主动换行，保证模板可读性。

## 29.4 模板表达式

模板里只写简单表达式。

复杂计算提到：

- `computed`
- 普通函数
- composable

禁止在 template 中堆复杂链式处理和多层逻辑。

## 29.5 SFC 顺序

统一：

```vue
<template>
</template>

<script setup>
</script>

<style>
</style>
```

具体 style 是否 scoped / lang="less" 以当前项目为准。

---

# 30. Router

## 30.1 页面传参

简单页面参数优先使用项目已有 router query / params 方式。

不要用全局 store 临时保存一次性页面跳转参数。

## 30.2 path

SmartAdmin 风格：

- `kebab-case`
- 与 views 目录结构尽量一致
- 保持容易从 URL 定位源码

## 30.3 name

路由 name 与组件缓存 / keep-alive 机制需要保持一致。

新增路由时必须先观察当前仓库同层路由写法，禁止凭记忆写。

---

# 31. 前端目录

主要目录职责：

```text
src/
  api/
  assets/
  components/
  config/
  constants/
  directives/
  i18n/
  lib/
  plugins/
  router/
  store/
  theme/
  utils/
  views/
```

不要额外创建语义重复目录，例如：

```text
services/   # 如果 api/ 已承担接口封装
helpers/    # 如果 utils/ 已存在
enums/      # 如果 constants/ 已承担枚举
```

除非项目已有此结构。

---

# 32. API 前端封装

所有 HTTP 请求优先放在 `src/api`。

## 32.1 文件名

```text
employee-api.js
login-api.js
department-api.js
```

## 32.2 导出对象

```js
export const departmentApi = {
  queryDepartment() {},
  addDepartment() {},
  updateDepartment() {},
};
```

对象以 `Api` 结尾。

## 32.3 禁止页面直接散写 axios

Vue 页面应调用业务 API：

```js
await departmentApi.queryDepartment(params);
```

不要：

```js
axios.post('/department/query', params);
```

散落在页面中。

## 32.4 async / await

新异步代码优先使用项目现有 async / await 风格。

必须处理：

- loading
- 成功反馈
- 错误处理
- finally 清理状态

---

# 33. 常量与枚举

所有业务常量 / 枚举优先进入 `src/constants`。

文件：

```text
login-const.js
employee-const.js
file-const.js
```

变量：

```text
LOGIN_RESULT_ENUM
EMPLOYEE_STATUS_ENUM
```

规则：

- 常量：大写下划线；
- 枚举：以 `_ENUM` 结尾；
- 不在 template / JS 中散落魔法数字；
- 后端状态变化时同步前端枚举。

优先使用项目现有 enum helper / plugin 来做：

- value → label
- 下拉选项
- 状态展示
- 映射

---

# 34. 表格与用户配置

中后台列表页优先检查已有 table operator / 列配置组件。

需要支持：

- 列显隐
- 列宽
- 列顺序
- 用户配置持久化

不要每个页面重新实现一套列设置。

表格列较多时优先提供用户自定义能力，而不是在代码中为所有用户固定展示。

---

# 35. Loading

存在 SmartLoading 时优先复用：

```js
SmartLoading.show();

try {
  await xxx();
} finally {
  SmartLoading.hide();
}
```

不要：

- 每个页面做一套全屏 Loading；
- 忘记 finally 关闭；
- 同一请求同时叠多个 Loading 体系。

局部按钮 Loading 是否使用组件自身 `loading` 属性，以当前页面体验为准。

---

# 36. 前端 DataTracer

展示数据变更记录时优先复用已有 DataTracer 组件。

不要在每个页面自行实现 diff UI。

业务需要新增 DataTracer 类型时：

- 后端业务类型同步；
- 前端 constants 同步；
- 页面仅传 dataId / type 等必要参数。

---

# 37. Layout

SmartAdmin 对 Layout 的思想是：**宁可少量重复，也不要为了复用把布局抽象到不可维护。**

因此：

- 不随意重构现有多个 layout 成超复杂统一组件；
- 大块通用内容才抽到 `layout/components`；
- 少量 layout 差异可以保持独立；
- 代码可读性高于“0 重复”。

---

# 38. Theme

项目级主题优先放：

```text
src/theme
```

SmartAdmin 自定义样式通常使用清晰项目级前缀，如：

```less
.smart-query-form {}
.smart-table-operate {}
```

不要：

- 到处覆盖 Ant Design 全局样式；
- 在业务页面写大量 `!important`；
- 无规划修改第三方库源码。

行业主题定制集中到 theme 层。

---

# 39. 水印

需要后台防截图 / 泄露提示时优先复用现有 watermark。

典型信息：

```text
企业 + 部门 + 姓名 + 时间
```

水印属于通用能力，应在 layout / lib 层实现，而不是复制到每个页面。

注意：水印只能提高泄露成本，不能当作真正的数据安全边界。

---

# 40. 帮助文档

复杂后台业务如果已有系统帮助文档模块：

- 优先关联到菜单 / 页面；
- 不在页面硬编码长篇使用说明；
- 文档内容与业务页面分离维护；
- 可记录阅读痕迹时沿用项目机制。

---

# 41. 登录页与项目默认配置

登录页样式、背景、布局、菜单主题等优先通过现有配置修改。

不要为了换 logo / 背景：

- 重写登录模块；
- 新建另一套路由；
- 复制整套认证逻辑。

默认项目配置优先集中在项目已有 config，例如：

```text
src/config/app-config.js
```

---

# 42. 前端多环境

保持项目现有 Vite env 体系。

典型环境：

```text
localhost
dev
test
pre
prod
```

配置放 `.env.*`，不要把 API 地址硬编码到源码。

构建命令必须使用项目 `package.json` 当前已有 script。

禁止：

```js
const API = 'https://prod.example.com';
```

直接写业务代码。

敏感 secret 不应该因为写进 Vite env 就认为安全——前端构建产物中的变量原则上都可能被用户看到。

---

# 43. 第三方依赖与工具框架加入规则

AI 可以建议加入新工具，但必须满足“确有价值”。

## 43.1 Java

### Lombok

已有 Lombok 时：

- Entity / Form / DTO / VO 等样板 getter/setter 优先 Lombok。
- 不重复手写 getter/setter。

没有 Lombok 时：

- 只有项目明确允许新增依赖才添加。

### MapStruct

适合：

- 对象映射多；
- 字段转换复杂；
- 手工 converter 已大量重复；
- 需要编译期类型安全。

不适合：

- 只有一两个简单 copy；
- 项目已有 `SmartBeanUtil` 足够。

### Hutool

项目已有 Hutool 时优先使用已有能力。

禁止为了一个 Hutool 已有函数再引入 Apache Commons / Guava 等重复库。

## 43.2 前端

新增前端库前检查：

- Vue / JS 原生能力
- Ant Design Vue
- 项目 components
- 项目 lib / utils
- lodash（若项目已有）
- 当前已有依赖

禁止为了一个简单函数再安装新 npm 包。

---

# 44. 安全规则

任何官网示例都不能覆盖以下规则。

## 44.1 密钥

禁止提交：

- AccessKey
- SecretKey
- JWT secret
- AES / SM4 生产密钥
- 数据库生产密码
- Sa-Token 敏感配置
- 云存储密钥

进入 Git。

## 44.2 SQL

- 不拼接用户输入。
- 动态排序字段必须白名单。
- 数据范围自定义 SQL 必须防注入。
- Mapper 使用参数绑定。

## 44.3 文件上传

至少校验：

- 文件大小
- 文件类型
- 文件名
- 存储路径
- 访问权限

不要信任扩展名。

## 44.4 权限

- 前端按钮权限只是 UI。
- 后端接口必须真正校验。
- 查询必须考虑数据范围。
- 新接口不能只复制页面按钮权限而漏掉服务端权限。

## 44.5 敏感数据

日志、错误响应、审计记录避免暴露：

- 密码
- token
- secret
- 私钥
- 完整身份证
- 完整银行卡
- 高敏个人数据

---

# 45. 测试规则

完成代码后至少执行与改动匹配的验证。

## 45.1 Java

优先：

```bash
mvn test
```

或项目指定模块：

```bash
mvn -pl <module> -am test
```

若时间 / 环境限制，至少：

```bash
mvn -pl <module> -am -DskipTests compile
```

不要宣称“已验证”除非实际执行成功。

## 45.2 前端

根据当前项目 package manager：

```bash
npm run lint
npm run build
```

或：

```bash
pnpm lint
pnpm build
```

不要凭空使用仓库不存在的 script。

## 45.3 必测场景

涉及以下功能时重点测试：

- 权限
- 数据范围
- 新增 / 修改 / 删除
- 重复提交
- 并发
- 事务回滚
- 分页
- 枚举
- 文件上传
- 环境配置
- 路由 keep-alive
- 表格列配置
- 生产环境错误信息

---

# 46. Git 规则

提交前：

1. 检查 `git diff`
2. 检查是否混入无关文件
3. 编译 / 测试
4. 删除未使用 import / 变量
5. 检查格式
6. 检查注释
7. 检查敏感配置
8. 更新 migration / 文档（如果需要）

不要提交：

```text
.idea/
target/
node_modules/
dist/
本地日志
临时 SQL
本地密钥
```

除非项目明确要求。

commit message 优先遵循仓库当前实际规范；不要因为官网示例使用禅道 `task#` / `bug#` 就强行改变现有 Git 规范。

---

# 47. AI 实施一个新功能时的标准流程

AI 接到“新增 XX 功能”时按以下顺序执行。

## Step 1：理解业务

先确定：

- 谁使用
- 输入是什么
- 输出是什么
- 权限是什么
- 数据范围是什么
- 状态流转是什么
- 是否并发
- 是否需要审计
- 是否有文件
- 是否有定时任务

## Step 2：搜索相似模块

至少搜索：

```text
相似 Controller
相似 Service
相似 Manager
相似 Dao / Mapper
相似 Entity / Form / VO
相似 Vue list/form
相似 API
相似 constants
```

## Step 3：确定是否已有平台能力

逐项检查：

```text
错误码
权限
DataScope
RepeatSubmit
DataTracer
SmartJob
文件服务
编号生成器
SmartReload
SmartLoading
TableOperator
Watermark
帮助文档
```

能复用就不新建。

## Step 4：数据库设计

明确：

- 表
- 主键
- 状态枚举
- 索引
- 唯一约束
- 审计字段
- migration

## Step 5：后端实现

按：

```text
Form / VO / Entity
→ Dao / Mapper
→ Manager
→ Service
→ Controller
→ 错误码
→ 权限 / DataScope
→ Tests
```

## Step 6：前端实现

按：

```text
constants
→ api
→ 页面 / 组件
→ router / menu（必要时）
→ 权限点
→ loading / error handling
```

## Step 7：验证

执行：

- compile
- test
- lint
- build
- 关键业务场景检查

## Step 8：最终汇报

只说明：

- 改了什么
- 关键设计
- 数据库变化
- API 变化
- 已运行哪些测试
- 未完成 / 风险点

不要写大段没有价值的“工作总结”。

---

# 48. 明确禁止 AI 做的事情

除非用户明确要求，否则禁止：

- 把单体改成微服务。
- 新增第二套 ORM。
- 新增第二套 HTTP client。
- 新增第二套统一返回对象。
- 新增第二套权限系统。
- 新增第二套异常体系。
- 新增第二套文件服务。
- 新增第二套定时任务框架。
- 把 Manager 层删除。
- 为普通 CRUD 引入 DDD 全家桶。
- 大面积把项目从 JS 转 TS 或 TS 转 JS。
- 大面积重构 router / axios / layout。
- 用 Entity 直接作为接口请求 / 响应。
- Controller 直接调 Dao。
- Service 无脑加大事务。
- 写大量 MyBatis Wrapper 破坏现有 SQL 风格。
- 前端页面直接散写 axios。
- template 写复杂业务逻辑。
- 写魔法数字代替枚举。
- 硬编码环境地址。
- 硬编码密码 / 密钥。
- 默认启用万能密码。
- 用接口报文加密替代 HTTPS。
- 因为“更优雅”修改与需求无关代码。
- 为了 100% DRY 制造高复杂度抽象。
- 未实际运行测试却声称测试通过。

---

# 49. 对官网规范的“项目现实优先”修正规则

SmartAdmin 文档横跨多个版本，部分示例可能来自 Java8 / Boot2 / Swagger2 / 旧前端目录。

所以 AI 必须遵守：

> **理解官网背后的设计意图，但代码语法、依赖和 API 以当前仓库实际版本为准。**

例如：

- 官网写 `@ApiOperation`，当前仓库若为 OpenAPI3，就使用仓库已有注解。
- 官网示例出现 Java8 依赖版本，不回退当前 Java17 依赖。
- 官网旧页面写某个目录，如果当前版本目录已调整，以仓库为准。
- 官网文档说 GET 删除，但当前项目使用 POST / DELETE，则保持当前项目。
- 官网示例写 JS，如果当前项目是 TS，则保持 TS。
- 官网示例使用 Vuex，而当前仓库若已使用 Pinia，则保持 Pinia。
- 官网工具路径变化时，以实际搜索到的类为准。

**禁止机械照抄文档中的历史代码。**

---

# 50. AI 最终判断口诀

遇到不知道怎么写时，按这个顺序：

```text
先找项目已有写法
    ↓
再找 SmartAdmin 已有能力
    ↓
再用 Spring / Vue 原生能力
    ↓
再用项目已有依赖
    ↓
最后才新增依赖和抽象
```

代码设计优先级：

```text
业务正确
> 数据安全
> 项目一致
> 清晰易维护
> 简单
> 复用
> 炫技
```

---

# 51. SmartAdmin 官方规范来源索引

本规程基于 2026-09-07 读取到的 SmartAdmin 官方“代码规范/架构”栏目整理。

主入口：

- https://smartadmin.vip/views/doc/standard/basic.html

顶级规范：

- 高质量代码思想 V3.0  
  https://smartadmin.vip/views/doc/standard/basic.html
- Vue3规范 V3.0  
  https://smartadmin.vip/views/doc/standard/front.html
- Java规范 V3.0  
  https://smartadmin.vip/views/doc/standard/back.html
- 微服务与单体那些事儿  
  https://smartadmin.vip/views/doc/standard/JavaArchitecture.html
- 前端 Js 和 Ts 的选型那些事儿  
  https://smartadmin.vip/views/doc/standard/JsOrTs.html

后端解读：

- 接口加解密  
  https://smartadmin.vip/views/doc/back/ApiEncryptDecrypt.html
- SmartJob  
  https://smartadmin.vip/views/doc/back/SmartJob.html
- 数据范围  
  https://smartadmin.vip/views/doc/back/DataScope.html
- 文件上传  
  https://smartadmin.vip/views/doc/back/FileUpload.html
- 多环境配置 / Maven BOM  
  https://smartadmin.vip/views/doc/back/MavenBOM.html
- 数据变更记录  
  https://smartadmin.vip/views/doc/back/DataTracer.html
- Manager 层  
  https://smartadmin.vip/views/doc/back/ManagerLayer.html
- 系统环境  
  https://smartadmin.vip/views/doc/back/SystemEnvironment.html
- 返回错误码  
  https://smartadmin.vip/views/doc/back/ErrorCode.html
- 心跳机制  
  https://smartadmin.vip/views/doc/back/HeartBeat.html
- 分包结构  
  https://smartadmin.vip/views/doc/back/PackageStructure.html
- 重复提交  
  https://smartadmin.vip/views/doc/back/RepeatSubmit.html
- 编号生成器  
  https://smartadmin.vip/views/doc/back/SerialNumber.html
- Smart Reload  
  https://smartadmin.vip/views/doc/back/SmartReload.html
- 工具类  
  https://smartadmin.vip/views/doc/back/UtilClass.html
- 万能密码  
  https://smartadmin.vip/views/doc/back/Password.html
- 代码生成  
  https://smartadmin.vip/views/doc/back/CodeGenerator.html
- Java17 / Spring Boot 3 升级  
  https://smartadmin.vip/views/doc/back/Java17.html

前端解读：

- 多种登录页样式  
  https://smartadmin.vip/views/doc/front/Login.html
- 不同环境配置和打包  
  https://smartadmin.vip/views/doc/front/ProfileConfiguration.html
- 数据变动记录  
  https://smartadmin.vip/views/doc/front/DataTracer.html
- 用户自定义表格列  
  https://smartadmin.vip/views/doc/front/TableOperator.html
- 常量和枚举  
  https://smartadmin.vip/views/doc/front/VueEnum.html
- API 请求  
  https://smartadmin.vip/views/doc/front/FrontApi.html
- 帮助文档  
  https://smartadmin.vip/views/doc/front/HelpDoc.html
- Layout  
  https://smartadmin.vip/views/doc/front/Layout.html
- 主题  
  https://smartadmin.vip/views/doc/front/Theme.html
- 水印  
  https://smartadmin.vip/views/doc/front/Watermark.html
- 内置组件  
  https://smartadmin.vip/views/doc/front/Components.html
- 项目默认配置  
  https://smartadmin.vip/views/doc/front/Config.html

---

## 52. 给 AI 的一句最终指令

> 这是一个 XSY-SCM（基于官方 SmartAdmin v3.30.0 二次开发的供应链管理系统）Java17 / Spring Boot 3 / Vue3 项目。不要重新设计框架。先读现有代码并寻找相似实现，优先复用 SmartAdmin 自带能力和项目已有工具，在保持现有目录、分层、返回结构、权限、API、组件和编码风格的前提下，以最小必要改动完成业务；只有现有能力明确不足时才新增依赖或抽象，并在完成后执行与改动匹配的编译、测试和前端构建验证。
