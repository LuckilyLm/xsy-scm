# Java 质量复核后续修复

原始记录日期：2026-09-28。以下前两节保留当时的工作范围和待办状态；2026-10-01 的收口验证追加在文末。

## 已修改

- `ScmDataScopeService` 不再导入或通过 Javadoc 引用 `report.support.ScmReportAccess`，成本查看权直接引用 common 权限目录。
- `CUSTOMER_SCOPE_ALL_QUERY`、`ORDER_SCOPE_ALL_QUERY`、`CUSTOMER_ASSIGN`、`REPORT_COST_QUERY` 统一定义在 `ScmCrossDomainPermission`。数据范围服务及 Customer / Report 目录保留原有公开常量名作为引用；四个权限字符串的值不变。
- 清除数据范围值对象与仓库写侧守卫中的阶段编号，保留范围相交、业务归属、失败关闭等业务说明。
- Eclipse formatter 的泛型、类型注解和枚举参数从强制逐项换行改为紧凑换行；120 列及 4 空格缩进不变。只整理本次触及方法的短泛型写法，没有批量格式化仓库。既有文件是否符合新规则尚未检查。
- 幂等哈希只归一化字段名以 `amount`、`quantity`、`price`、`weight`、`cost` 或 `rate` 结尾的数字文本；凭据号、编码和备注中的数字文本保持原样，避免把 `00123` 与 `123` 当成同一请求。
- 收款登记在 claim 前校验请求客户范围，重放时再校验已记录结果的客户范围；客户被改派或调用者失去范围后，不再返回原收款的客户、金额或凭据数据。
- 跨域 DAO 守卫会读取目标 Mapper 的 statement 类型。白名单调用必须对应 `<select>`，或为 MyBatis-Plus 的 `select*` / `exists*` 基础只读方法；无法解析或映射到写语句会失败。

## 测试与门禁待办（2026-09-28 时记录）

| 编号 | 现有缺口 | 后续需要覆盖 |
| --- | --- | --- |
| QF-TEST-01 | `ScmPermissionContractPgIT` 只覆盖 Controller 注解及直接 `StpUtil.checkPermission`，不覆盖 `hasPermission` 与数据范围包装方法。 | 检查真实使用的功能、数据范围和字段级权限是否来自目录且已发布；包含 `ScmDataScopeService.hasPermission` 与 `ScmReportAccess.canViewCost` 的调用链。 |
| QF-TEST-02 | javac 内联字符串常量后，ArchUnit 字节码分析无法发现 common 对业务域常量的源码依赖。 | 增加源码层依赖检查，覆盖普通 import、static import 及全限定引用；保留现有 ArchUnit 规则。 |
| QF-TEST-03 | 阶段注释扫描曾漏掉“P0 基线收口裁决”等表述。 | 补齐规则与负向样例，识别阶段编号，同时保留业务不变量和正常业务术语。 |
| QF-TEST-04 | `ScmIdempotencyRequestHasher` 的旧测试使用泛化键名验证数字字符串归一。 | 将断言迁到真实金额/数量字段；补充 `externalReference`、业务编码和零前缀文本不能归一的覆盖。 |
| QF-TEST-05 | 收款重放此前直接返回已记录结果，没有覆盖客户改派或撤销范围后的行为。 | 覆盖同键重放时当前客户范围仍有效，以及失去范围时以数据范围错误拒绝。 |
| QF-TEST-06 | 跨域 DAO 守卫新增 Mapper SQL 类型检查，尚无守卫自身的正反例。 | 覆盖 `<select>` 通过、`<update>` / `<delete>` 拒绝、缺失 Mapper statement 拒绝、基础 `select*` 方法通过。 |

该段只描述 2026-09-28 当时状态：测试覆盖待办尚未完成，未运行 Checkstyle、Spotless、质量入口或回归。

## 验证记录适用范围

[先前验收报告](java-quality-final-verification-2026-09-28.md) 中的 1203 项后端结果、85 项工具单测和各门禁 PASS 仅适用于当时的源码和规则；不能证明之后的改动已验证。

## 2026-10-01 Mainline hardening 收口

本轮只处理 Finance CUSTOMER Payment 重放范围、QF-TEST-01～06 与 V74 采购商品每日清单验收，没有开始新的业务阶段。

| 编号 | 状态 | 修复与覆盖 | 验证 |
| --- | --- | --- | --- |
| QF-TEST-01 | COMPLETE / PASS | 源码扫描覆盖 `@SaCheckPermission`、`StpUtil.checkPermission` / `hasPermission`、`ScmDataScopeService` 与 `ScmReportAccess` 包装；分割 Java 注释与字符串，实际权限必须来自目录并发布到菜单表。 | `ScmPermissionContractPgIT` 2/2；`python tools/verify.py quality` PASS。 |
| QF-TEST-02 | COMPLETE / PASS | 新增 `common` 对各业务域的源码依赖守卫，覆盖普通 import、static import 和全限定引用；注释与字符串不计。 | 源码守卫六个临时 fixture 用例通过；Python quality suite 101/101。 |
| QF-TEST-03 | COMPLETE / PASS | 阶段注释扫描识别独立的 `P<n>` token，正例、反例均覆盖。 | `python tools/quality/quality_guard.py check --checkstyle` PASS；stage-comment baseline 0。 |
| QF-TEST-04 | COMPLETE / PASS | 幂等哈希测试使用 `amount`、`quantity`、`price`、`weight`、`cost`、`rate` 等实际字段；编码、外部引用、备注前导零与数组顺序保持原样。 | `PurchaseIdempotencyRequestHasherTest` 6/6。 |
| QF-TEST-05 | COMPLETE / PASS | CUSTOMER Payment 重放会重新验证当前客户范围，失去范围返回既有 41139；Receipt 重放改派后拒绝并保持既有 30005。Supplier Payment 未新增范围限制。 | `ScmFinancePaymentPgIT` 22/22；`ScmFinanceReceiptPgIT` 17/17。 |
| QF-TEST-06 | COMPLETE / PASS | DAO guard 有临时 fixture 覆盖只读白名单、写语句拒绝、缺失 statement、MyBatis-Plus 基础只读方法、过期 allowlist 和未登记 DAO。 | DAO guard 七个 fixture 用例通过；真实 guard PASS；Python quality suite 101/101。 |

### 最终门禁

- Checkstyle 0；质量守卫六项 baseline 全部为 0；`python tools/verify.py quality` PASS。
- `python tools/verify.py backend` PASS：1,261 tests，0 failures / errors / skipped；`F0FileStorageCloudIT` 5/5 实际执行。
- `python tools/verify.py frontend` PASS：TS 棘轮新增错误 0，lint 0 errors，单测 258/258，生产构建 PASS。
- `python tools/verify.py e2e` PASS：156 passed，0 skipped、0 unexpected、0 flaky。
- Migration checksum guard：74 migrations，drift 0、missing 0、renamed 0、unbaked 0。

V68–V74 只应用到本地验收 / scratch 数据库，没有应用到生产库。未推送本地提交。
