# Java 质量复核后续修复

日期：2026-09-28。用户要求修复生产代码和配置；测试问题先记录，不修改测试、不运行测试或构建。

## 已修改

- `ScmDataScopeService` 不再导入或通过 Javadoc 引用 `report.support.ScmReportAccess`，成本查看权直接引用 common 权限目录。
- `CUSTOMER_SCOPE_ALL_QUERY`、`ORDER_SCOPE_ALL_QUERY`、`CUSTOMER_ASSIGN`、`REPORT_COST_QUERY` 统一定义在 `ScmCrossDomainPermission`。数据范围服务及 Customer / Report 目录保留原有公开常量名作为引用；四个权限字符串的值不变。
- 清除数据范围值对象与仓库写侧守卫中的阶段编号，保留范围相交、业务归属、失败关闭等业务说明。
- Eclipse formatter 的泛型、类型注解和枚举参数从强制逐项换行改为紧凑换行；120 列及 4 空格缩进不变。只整理本次触及方法的短泛型写法，没有批量格式化仓库。既有文件是否符合新规则尚未检查。
- 幂等哈希只归一化字段名以 `amount`、`quantity`、`price`、`weight`、`cost` 或 `rate` 结尾的数字文本；凭据号、编码和备注中的数字文本保持原样，避免把 `00123` 与 `123` 当成同一请求。
- 收款登记在 claim 前校验请求客户范围，重放时再校验已记录结果的客户范围；客户被改派或调用者失去范围后，不再返回原收款的客户、金额或凭据数据。
- 跨域 DAO 守卫会读取目标 Mapper 的 statement 类型。白名单调用必须对应 `<select>`，或为 MyBatis-Plus 的 `select*` / `exists*` 基础只读方法；无法解析或映射到写语句会失败。

## 测试与门禁待办（暂不修复）

| 编号 | 现有缺口 | 后续需要覆盖 |
| --- | --- | --- |
| QF-TEST-01 | `ScmPermissionContractPgIT` 只覆盖 Controller 注解及直接 `StpUtil.checkPermission`，不覆盖 `hasPermission` 与数据范围包装方法。 | 检查真实使用的功能、数据范围和字段级权限是否来自目录且已发布；包含 `ScmDataScopeService.hasPermission` 与 `ScmReportAccess.canViewCost` 的调用链。 |
| QF-TEST-02 | javac 内联字符串常量后，ArchUnit 字节码分析无法发现 common 对业务域常量的源码依赖。 | 增加源码层依赖检查，覆盖普通 import、static import 及全限定引用；保留现有 ArchUnit 规则。 |
| QF-TEST-03 | 阶段注释扫描曾漏掉“P0 基线收口裁决”等表述。 | 补齐规则与负向样例，识别阶段编号，同时保留业务不变量和正常业务术语。 |
| QF-TEST-04 | `ScmIdempotencyRequestHasher` 的旧测试使用泛化键名验证数字字符串归一。 | 将断言迁到真实金额/数量字段；补充 `externalReference`、业务编码和零前缀文本不能归一的覆盖。 |
| QF-TEST-05 | 收款重放此前直接返回已记录结果，没有覆盖客户改派或撤销范围后的行为。 | 覆盖同键重放时当前客户范围仍有效，以及失去范围时以数据范围错误拒绝。 |
| QF-TEST-06 | 跨域 DAO 守卫新增 Mapper SQL 类型检查，尚无守卫自身的正反例。 | 覆盖 `<select>` 通过、`<update>` / `<delete>` 拒绝、缺失 Mapper statement 拒绝、基础 `select*` 方法通过。 |

本次没有改动 Java 测试类、质量扫描器或它们的测试；以上三项仍未完成。没有重新 capture baseline，也没有运行 Checkstyle、Spotless、质量入口或回归。

## 验证记录适用范围

[先前验收报告](java-quality-final-verification-2026-09-28.md) 中的 1203 项后端结果、85 项工具单测和各门禁 PASS 仅适用于当时的源码和规则；不能证明本次改动已验证，也不能证明上述覆盖缺口不存在。当前状态为“生产修复已写入，验证与测试覆盖待办未完成”。Finance 后续阶段继续暂停。
