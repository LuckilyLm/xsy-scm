# 项目状态

最后更新：2026-10-01

## 当前完成

- SCM V2 正式底座、商品、客户、供应商、定价、订单、采购、库存、分拣、配送和 Finance R1 F1-8 已完成，详情见 [有效决策](decisions.md) 与 [Finance R1 规划](plan/active/finance-r1-design.md)。
- Java 质量整改已实施并留下[先前验收记录](quality/java-quality-final-verification-2026-09-28.md)；其中的零计数与 PASS 只描述当时门禁覆盖的范围。
- 后续已修正 common 对 report 的权限常量依赖、共享权限目录、格式配置和已发现的阶段注释，见[后续修复记录](quality/java-quality-follow-up-2026-09-28.md)。
- 2026-10-01 主线代码收口已完成：Finance CUSTOMER Payment 重放重新校验当前客户范围；QF-TEST-01～06 已补测并通过；V74 采购商品每日清单已在 fresh PostgreSQL 数据库应用并完成后端、前端与浏览器验证。细节见[后续修复记录](quality/java-quality-follow-up-2026-09-28.md)和 [ADR-005](adr/005-purchase-daily-report.md)。

## 当前进行

- 本轮主线代码收口已完成，当前没有待验收的主线项；没有开始新的业务阶段。

## 暂停或未开始

- W6-2 小程序尚未开始，当前目录保持冻结。
- 地图 M2 的完整商用底图方案仍待路线选择；现有 M0/M1 与可配置接入不受影响。

## 风险与验证状态

- Finance R1 F1-3C / F1-4 的生产代码、V68 / V69 迁移已通过定向 PostgreSQL 集成验证：反向收付款 8/8，核销 / 红字 13/13，并发锁序 4/4；结合 schema、只读契约和日期区间检查，目标类共 45/45。
- Finance R1 F1-5 / F1-6 已交付：V70 查询 / 导出权限、V71 五页面菜单、V72 折叠导航图标；定向 PostgreSQL 组 44/44。财务菜单实际为 `1500–1505, 1511–1515, 1521–1527, 1531`，最大 `menu_id=1531`。
- Finance R1 F1-7 / F1-8 在 2026-09-30 通过 `python tools/verify.py all`：后端 1,243 tests，0 failures / errors / skipped；Web 单测 258/258；浏览器 E2E 154 passed，0 skipped / unexpected / flaky。质量门禁、迁移校验（73 条无漂移）、生产构建和 TS 基线棘轮均通过。
- 前端 lint 退出 0，保留 3 条既有 warning；生产构建有依赖 `icon.svg`、动态导入和大 chunk 的既有提示。TS 基线棘轮为 baseline 1974、current 1940、delta -34，SCM 错误 0、新增错误 0；直接全仓 `vue-tsc --noEmit` 仍有历史类型错误，不记为全仓 typecheck 通过。
- F1-8 新增 Finance R0 往来概览的六项指标、期末应收/应付明细和三种 XLSX 导出；Finance R0 定向浏览器用例 3/3 通过，包含移动端完整字段卡片、服务端权限拒绝和 XLSX 文件签名检查。桌面/窄屏视觉复核通过，详情见 `.runtime/finance-r0-review/`。
- V68–V74 只应用到本地验收 / scratch 数据库，未应用到生产库。2026-10-01 fresh PostgreSQL 数据库从 V1 应用至 V74；V74 Job 单行启用且无重复类名。后端全量 1,261 tests，0 failures / errors / skipped；5 个 `F0FileStorageCloudIT` 用例均实际执行并通过。
- 2026-10-01 前端正式入口通过：TS 棘轮无新增错误、lint 0 errors（3 条既有 warning）、Web 单测 258/258、生产构建成功。全量浏览器 E2E 156 passed，0 skipped / unexpected / flaky。
- Migration checksum snapshot 已冻结到 V74：74 migrations，drift 0、missing 0、renamed 0、unbaked 0。质量门禁六项 baseline 均为 0。
- 归档进度中的测试结果仅描述当时的运行记录，不代表本次改动已验证。

## 下一步

- 若地图 M2 或多仓默认选择进入实施，先更新对应 ADR 和活动计划。
