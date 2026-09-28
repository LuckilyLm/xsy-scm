# Java 代码质量整改最终验收

日期：2026-09-28。历史 Q0 快照保留在 [Java 质量审计](java-quality-audit-2026-09-26.md)，本文件记录最终现状。

## 验收结论

**QUALITY REMEDIATION Q0–Q4 — IMPLEMENTATION COMPLETE**  
**FINAL VERIFICATION COMPLETE**（接受 5 个既有云门控跳过）  
**FINANCE DEVELOPMENT — STILL PAUSED**

行为与质量门禁验证针对源码/测试提交 `a805f2497a29205268aa55c3834b536caa625b38`。分支为 `main`；验证时 `origin/main` 为 `3ac89db9659f9d2f8e1c7607afbad604e03990af`。未推送。

从审计锚点 `da7103eee4d14eb9c79fb4d8a2bde6d63220f1e4` 到该源码/测试提交：163 commits、800 files changed、`+25,023 / -18,753`。baseline 收缩与本报告作为最终文档收口单独本地提交。

## 当前源码指标

| 指标 | 当前值 | 结果 |
| --- | ---: | --- |
| `com.xsy.scm` 生产 Java 文件 | 731 | 旧 SCM 包 0 |
| SCM 测试 Java 文件 | 156 | |
| Checkstyle | 0 | AvoidStarImport、UnusedImports、LineLength、RedundantImport、命名和其它违规均为 0 |
| 泛化依赖字段 A / 缩写字段 B / 未裁决项 | 0 / 0 / 0 | 15 个域逐域审计 |
| Magic String 真实债务 | 0 | 精确例外见下文 |
| Raw Permission 字面量 | 0 | |
| 阶段注释 | 0 | |
| Bean Validation 约束 | 819 有 message / 0 缺失 | 覆盖嵌套泛型约束 |

表单校验分项：`NotNull 230`、`NotBlank 61`、`NotEmpty 28`、`Size 178`、`Min 106`、`Max 21`、`Positive 81`、`PositiveOrZero 0`、`Pattern 11`、`DecimalMin 18`、`DecimalMax 4`、`Digits 14`、`AssertTrue 3`、`ScmEnumValue 64`。所有 819 条均带 message。

唯一精确分类的合法 error token 是 `ProductImportService.addError(..., "CATEGORY_DISABLED", ...)`：它是导入行的 `errorCode`，不是状态、类型或来源。Quality Guard 只排除此文件中这个 token 的 error-code 参数位置；该项没有留在 baseline。裁决见 [Enum 词汇复核](q3-enum-vocabulary-review.md)。

## Baseline 收缩

“Q0 初始”来自历史审计；“捕获前 baseline”是本次检查读取的旧账本；“捕获前 current”是本次全树扫描。全部确认 `0 NEW / 0 GROWN` 后执行 identity-safe capture，无 `--allow-growth`。

| Family | Q0 初始 | 捕获前 baseline | 捕获前 current | 最终 baseline |
| --- | ---: | ---: | ---: | ---: |
| Checkstyle | 871 | 712 | 0 | 0 |
| 泛化依赖字段 | 71 | 28 | 0 | 0 |
| Magic String | 191 | 152 | 0 | 0 |
| Raw Permission | 285 | 246 | 0 | 0 |
| 阶段注释 | 695 | 695 | 0 | 0 |
| 旧 SCM 包 | 847 | 0 | 0 | 0 |

最终六个 baseline 均为 0；复核结果 `+0`。

## 权限与架构

现有 Permission Catalog 共 15 个：Customer、Dashboard、Delivery、Finance、Inventory、Order、Pricing、Product、Purchase、Report、Screen、Sorting、Supplier、Warehouse 和 ScmCrossDomain。全局 `ScmPermissionContractPgIT` 扫描 SCM Controller 的权限注解与 `StpUtil.checkPermission` 调用，包含数组权限；每个值都属于相应 Java Catalog，并存在于 `t_menu.api_perms`。测试没有自动补种权限。

`ScmArchitectureTest` 9/9 PASS；`FinanceReadOnlyContractTest` 4/4 PASS；Finance 权限 IT 3/3 PASS；新增全局权限契约 1/1 PASS。Finance → Order 的 `OrderIdempotencyService` 导入和 ArchUnit 例外已删除，Finance 不再直接引用 Order Java 类型；退款完成状态由只读来源 DAO 投影为本域布尔事实。跨域 DAO guard PASS，24 条精确只读/锁读白名单均有效，没有 stale 项。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| `mvn -B -N checkstyle:check` | PASS，0 violations |
| `mvn -B '-Dquality.ratchet.from=NONE' spotless:check` | PASS，完整检查 731 个生产源码文件，0 项需改写 |
| `python tools/verify.py quality` | PASS |
| `python -m unittest discover -s quality -p 'test_*.py'` | 85/85 PASS |
| `python tools/migration_checksum_guard.py check` | PASS；67 migrations，drift / missing / renamed / unbaked 均为 0 |
| 后端全量回归 | 1203 tests，0 failures，0 errors，5 skipped |

后端在新库 `xsy_quality_accept_20260928_105914` 上运行。测试前只执行 `CREATE DATABASE`，`xsy_v2` schema 数为 0；随后由 Flyway 创建 schema。5 个 skip 全部来自 `F0FileStorageCloudIT`，属于设计内云存储门控；`tools/verify.py backend` 因此返回 `INCOMPLETE (exit 2)`，测试计数仍为 1203 / 0 / 0 / 5。

行为敏感用例均通过：商品导入 `ProductImportServiceTest` 26、`ProductImportUpdatePgIT` 5、`ProductMasterDataPgIT` 11；配送打印 `DeliveryPrintTrackingIT` 7、`DeliveryPrintConcurrencyIT` 2、`DeliveryRouteServiceIT` 2。幂等回归包括 `PurchaseOrderIdempotencyIT` 4、`PurchaseIdempotencyRequestHasherTest` 5、`PurchaseIdempotencyResultJsonTest` 4、`DeliveryDispatchPgIT` 9、`ScmFinancePaymentRacePgIT` 1，以及全量套件中的订单、库存和 Finance 重放/回滚用例。

Flyway 对 PostgreSQL 18.6 输出“版本高于已测试支持范围（17）”提示；67 条仓库 migration 校验无漂移，迁移 IT 均在全量套件中执行。无 migration 被修改或新增。

本轮没有新增业务 API、页面、业务状态或 migration；Finance F1-3C 未启动。最终本地提交和 `git status` 以仓库当前 `main` 为准。
