# ADR-005：采购商品每日清单

状态：已确认并验证，2026-10-01。

## 统计口径

- 每天在可配置时间自动生成前一天的采购商品统计清单。默认 03:00 仅为初始配置。
- 用户确认按 **Asia/Shanghai 的采购单提交日期**统计，取 `[当日 00:00, 次日 00:00)`。
- 生成时只纳入 `SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED`；排除草稿、取消单和逻辑删除的单据或明细。
- 按 SKU、采购单位和采购单生成时保存的 SPU/SKU 编码及名称快照分组，汇总采购单数、计划采购数量、采购行金额，不跨单位相加；采购金额不代表财务应付。
- 若同一天同一 SKU 的采购单位相同、但两张采购单中的名称或编码快照不同，清单按这组快照分成两行；快照相同的采购行仍合并。这样每行显示对应采购单生成时的商品信息，不用当前主档名覆盖历史，也不从多张订单中任意挑选名称。
- 后续取消、收货、主数据改名不改变已生成清单。同日重复执行保留已有结果，无采购也记录空清单。

## 实现与权限

- 复用 SmartAdmin 定时任务，不新增调度系统。任务类为 `com.xsy.scm.report.job.PurchaseDailyReportJob`。
- 后台任务通过专用只读 DAO 全量读取采购事实，只写报表快照表；前台无全量生成写接口。
- `report_purchase_daily` 的日期主键阻止重复生成。表头与所有明细同事务提交，失败整体回滚，可安全重试。
- 明细保留仓库和采购员维度；交互查询与导出均先取两种授权范围的交集，再聚合商品。筛选只能缩小范围，空范围不返回数据。
- 查看沿用 `scm:report:purchase:query`；导出同时需要 `scm:report:export`。任务配置和补生成由现有任务管理权限控制。

## 使用与运维

1. 部署时应用 `V74__scm_purchase_daily_report.sql`，创建快照表并注册启用的任务。历史已应用 migration 不变。
2. 在 **系统设置 → 定时任务 → 采购商品每日清单** 编辑触发时间：如 `0 30 2 * * ?` 表示每天 02:30。配置保存后由底座热刷新，无需改代码。定时运行的任务参数保持为空。
3. SmartAdmin Cron 使用应用 JVM 时区；本项目 Docker Compose 已设置 `TZ=Asia/Shanghai`。独立 JVM 部署需使用 `-Duser.timezone=Asia/Shanghai`，确保执行时刻与业务日界一致。
4. 在 **报表中心 → 采购分析 → 每日清单** 选择日期查看或导出 XLSX。清单未生成、无访问范围和已生成但无匹配商品有明确提示。
5. 停机错过执行或执行失败，在任务管理查看日志后手动执行。空参数生成昨日；手动执行参数可填写 `yyyy-MM-dd` 补生成历史日，禁止今天或未来日期。不要把补生成日期保存为长期任务参数，否则以后每天只会重试该日期。
6. 补生成按执行时仍有效的采购单状态取数，不复原当日历史状态；已存在的清单不会覆盖。没有自动追补全部停机日期。

## 验证边界

2026-10-01 验收结果：

- fresh PostgreSQL scratch 库未手动建 schema，由 Flyway 应用 V1–V74；检查确认两张每日清单表、日期索引和唯一启用的 `PurchaseDailyReportJob`，Cron 为 `0 0 3 * * ?`，空参数。
- `PurchaseDailyReportJobTest` 6/6；`PurchaseDailyReportPgIT` 8/8，覆盖上海日界、状态排除、快照 tuple 分组、范围交集、导出 ZIP 签名、事务失败回滚后重试和并发生成。
- 后端全量 1,261 tests，0 failures / errors / skipped；本地 MinIO 的 5 个云存储 IT 实际执行并通过。
- 前端 TS 棘轮无新增错误、lint 0 errors（3 条既有 warning）、单测 258/258、生产构建成功；正式浏览器 E2E 156 passed，0 skipped / unexpected / flaky。每日清单用例覆盖默认上海昨日、今天/未来禁选、未生成与空清单、仓库/关键字筛选、分页、Content-Disposition、XLSX `PK` 签名及窄屏布局。
- Migration checksum guard 已冻结 V74：74 migrations，drift 0、missing 0、renamed 0、unbaked 0。质量门禁全部 baseline 为 0。

截至上述验收记录，V74 只在本地验收 / scratch 数据库应用，未应用生产库；本轮文档整理未查询生产环境。相关代码已于 2026-10-02 合并并推送到 `main`（交付 SHA：`2edb8ccf4a7129bc02be1cdf3f67bcdea75edc37`），见[项目状态](../status.md)。代码交付不等于生产库已迁移；后续版本不由本记录推断。
