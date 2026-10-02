# Java 工程规范与质量基线

更新：2026-10-02。Q0–Q4 和后续覆盖补测已完成；本文只保留持续适用的规则及最近收口证据，删除旧阶段计划与重复审计过程。规则优先级见根目录 AGENTS.md。

## 包结构与职责

- SCM 正式包为 `com.xsy.scm`；SmartAdmin 保持原有包。生产与测试均不得残留旧 SCM 包，Spring 与 MyBatis 扫描随正式包配置。
- 依赖方向为 Controller → Service → Manager / Policy → DAO。Controller 不直接访问 DAO，Service 不依赖 Controller，common 不反向依赖业务域，生产代码不依赖测试包。
- Service 承担事务编排、状态转换及领域协作；超过 400 行须审查职责，按业务边界拆分，不按行数或一个方法一个类机械拆分。
- 库存命令集中维护余额锁、单位、成本和追加流水；收货确认与库存协作、配送发车与出库等原子事务不能拆成独立提交。
- 跨域读使用正式领域契约或已登记的只读 DAO；[白名单](../../tools/quality/cross-domain-dao-allowlist.tsv)限定文件、DAO 与方法。写语句、缺失 Mapper、未登记调用应被守卫拒绝；Finance 不写其他业务域表。
- 幂等基建位于 common，参与调用方事务；跨域命令不得直接使用订单兼容外观或幂等记录 DAO。

## 命名与安全重构

- 类型使用 UpperCamelCase，方法/参数/变量使用 lowerCamelCase，常量使用 UPPER_SNAKE_CASE；名称须表达业务对象与动作，避免裸 `dao`、`service` 或无语义缩写。
- 注入字段默认使用类型名去掉项目级 `Scm` 前缀后的 lowerCamelCase；Product、Purchase、Delivery 等领域词不得省略。角色明确的别名如 `readOnlyDataSource` 可以保留。
- DAO、Service、Validator、Calculator 等名称与职责一致；只读查询职责不混入写命令。参数中的 ID 应标明对象，例如 `customerId`。
- `i/j` 只用于明显且短小的索引循环，短 catch 可用 `e`；业务异常和 lambda 参数仍用语义名。
- 符号改名优先 IDE / AST / 编译器感知方式。环境不支持时，只能精确修改声明和对应引用；禁止整文件单词正则替换，不得污染字符串、注解、权限码、URL 或同名方法。
- 自动审计不能明确判断时标为人工复核，不得自动记为通过。

## 枚举、权限与校验

- 业务状态、类型、来源优先复用现有 Enum；禁止重复 magic string 或无边界的巨型 Constant 类。新枚举与数据库 CHECK、表单校验同步。
- 相同拼写不代表同一业务事实：各聚合状态机、定价来源与订单价格快照、财务方向与分拣结果不能机械合并。共同商品类型使用 `ScmProductTypeEnum`。
- `ProductImportService.addError` 的 `CATEGORY_DISABLED` 是导入错误码，保留精确位置的例外；不能借此扩大业务状态字符串的豁免。
- 权限使用领域 Permission Catalog；跨域共享权限放 common 目录。注解、手工检查、范围包装方法及菜单发布必须一致，`sa-security.yml` 不是 SCM 权限事实源。
- 输入表单的校验必须提供可读 message；状态参数使用 Enum 或统一 Enum Validator，避免每个表单维护独立的状态正则。
- 工具优先使用 JDK → Spring → Apache Commons；只为明确的业务语义创建领域工具，不重复包装字符串、集合和对象通用工具。

## 注释与格式

- 注释记录原因、不变量和易错点；禁止在生产代码注释维护阶段编号、提交过程、测试数量或过期计划。
- 公共契约、并发/事务和非直观边界需要说明；意图明确的私有方法不强制补 Javadoc。
- 显式 import，禁止 wildcard 和无用 import。按仓库格式配置使用 UTF-8、4 空格、Java 120 列、末尾换行和无行尾空白；既有 migration 的字节与换行遵循 `.gitattributes`，不得为统一格式改写已应用迁移。

## 门禁与维护

- 正式入口为 `tools/verify.ps1`、`tools/verify.sh`、`tools/verify.py`；执行条件见[贡献指南](../../CONTRIBUTING.md)。默认不运行测试或构建，只有用户要求验证时执行。
- Spotless / Checkstyle 管格式与基础规范，ArchUnit 管依赖边界；项目守卫覆盖旧包、业务字符串、原始权限、命名与阶段注释。源码依赖检查补充常量内联后字节码不可见的边界。
- baseline 只允许收缩；新问题不得增加。禁止为通过门禁扩大 exclude/suppression、降低检查级别或削弱测试。新增规则需要调整基线时必须记录具体理由。
- 迁移冻结清单不可随意重写；[迁包说明](package-migration-readiness.md)及其机器清单保留供工具读取。
- 需求、状态、规则与开发规划各自只维护一份；重复过程文档直接删除，历史实现从 Git 追溯。正式 ADR 和仍被使用的设计不当作普通过程稿删除。
- 报告区分已通过、跳过、未执行与未覆盖；本地验收、Git 推送和生产部署不得互相替代。

## 2026-10-01 Mainline hardening 收口

该次只处理 Finance CUSTOMER Payment 重放范围、QF-TEST-01～06 与 V74 采购商品每日清单验收，没有开始新的业务阶段。

| 编号 | 状态 | 修复与覆盖 | 验证 |
| --- | --- | --- | --- |
| QF-TEST-01 | COMPLETE / PASS | 源码扫描覆盖 `@SaCheckPermission`、`StpUtil.checkPermission` / `hasPermission`、`ScmDataScopeService` 与 `ScmReportAccess` 包装；分割 Java 注释与字符串，实际权限必须来自目录并发布到菜单表。 | `ScmPermissionContractPgIT` 2/2；`python tools/verify.py quality` PASS。 |
| QF-TEST-02 | COMPLETE / PASS | 新增 `common` 对各业务域的源码依赖守卫，覆盖普通 import、static import 和全限定引用；注释与字符串不计。 | 源码守卫六个临时 fixture 用例通过；Python quality suite 101/101。 |
| QF-TEST-03 | COMPLETE / PASS | 阶段注释扫描识别独立的 `P<n>` token，正例、反例均覆盖。 | `python tools/quality/quality_guard.py check --checkstyle` PASS；stage-comment baseline 0。 |
| QF-TEST-04 | COMPLETE / PASS | 幂等哈希测试使用 `amount`、`quantity`、`price`、`weight`、`cost`、`rate` 等实际字段；编码、外部引用、备注前导零与数组顺序保持原样。 | `PurchaseIdempotencyRequestHasherTest` 6/6。 |
| QF-TEST-05 | COMPLETE / PASS | CUSTOMER Payment 重放会重新验证当前客户范围，失去范围返回既有 41139；Receipt 重放改派后拒绝并保持既有 30005。Supplier Payment 未新增范围限制。 | `ScmFinancePaymentPgIT` 22/22；`ScmFinanceReceiptPgIT` 17/17。 |
| QF-TEST-06 | COMPLETE / PASS | DAO guard 有临时 fixture 覆盖只读白名单、写语句拒绝、缺失 statement、MyBatis-Plus 基础只读方法、过期 allowlist 和未登记 DAO。 | DAO guard 七个 fixture 用例通过；真实 guard PASS；Python quality suite 101/101。 |

### 当次门禁结果（本次文档精简未重跑）

- Checkstyle 0；质量守卫六项 baseline 全部为 0；`python tools/verify.py quality` PASS。
- `python tools/verify.py backend` PASS：1,261 tests，0 failures / errors / skipped；`F0FileStorageCloudIT` 5/5 实际执行。
- `python tools/verify.py frontend` PASS：TS 棘轮新增错误 0，lint 0 errors，单测 258/258，生产构建 PASS。
- `python tools/verify.py e2e` PASS：156 passed，0 skipped、0 unexpected、0 flaky。
- Migration checksum guard：74 migrations，drift 0、missing 0、renamed 0、unbaked 0。

本轮代码变更已于 2026-10-02 合并并推送到远端 `main`（交付 SHA：`2edb8ccf4a7129bc02be1cdf3f67bcdea75edc37`）。V68–V74 的 migration 验证仍限于本地验收 / scratch 数据库；生产库迁移状态需在部署时单独核验。
