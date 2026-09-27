# Q3 Service 职责审查

日期：2026-09-28。依据整改计划 §11、§12 对审计中超过 400 行的服务职责进行复核。此文记录结构审查，不代表执行了测试或质量门禁。

## 处理结果

- `InventoryCommandService`：统一应用库存事实并维护余额行锁、单位、成本和流水约束。各写入入口共享同一账本不变量，保留集中写入边界。
- `ProductImportService`：拆出 `ProductImportWorkbookSupport` 负责模板和工作簿读写，`ProductImportWriteService` 继续负责整批持久化。导入服务保留行校验、业务数据解析和表单组装；这些步骤共同完成导入行到产品命令的转换。
- `PurchaseReceiptService`：管理收货单生命周期。确认收货、采购累计量和库存契约处于同一事务，不能拆成独立提交。
- `DeliveryRouteService`：负责线路生命周期、发车和签收；订单与线路状态共同构成该事务边界。线路打印登记已拆到 `DeliveryRoutePrintService`，仍使用线路锁、活动分配和请求幂等。
- `SalesOrderService`：负责订单草稿、提交、确认、取消、实发量与显式预留等订单命令，状态转换依赖同一订单锁和版本规则。
- `PurchaseOrderService`：负责采购单创建、分配、提交、转派、取消、少收关单与删除，是一个采购单生命周期。
- `PurchaseQueryService`：提供采购需求、采购单和收货单的只读查询与导出数据，跨视图共用采购归属范围和同一领域快照规则。
- `InventoryConversionService`：管理规格转换单状态、成本基准和两 SKU 余额命令，审批必须在同一事务中完成。
- `ScmReportController` 是控制器，不属于 Service 行数规则；整改计划中的 Service 阈值不作为拆分控制器的理由。

## 其余复杂度触发项

审计 §5.5 的 17 个触发项也逐项复核。行数、依赖数和方法数是审查信号，不自动要求拆类。

| 类 | 审查结论 |
| --- | --- |
| `InventoryCommandService` | 保持库存事实唯一写入口；入库、出库、盘点、报损、调拨、转换共享余额锁、单位、成本与流水约束。 |
| `PurchaseReceiptService` | 收货草稿、确认、入库确认和删除属于同一收货聚合；确认期间的采购累计、库存入账、应付生成必须同事务。 |
| `DeliveryRouteService` | 核心线路生命周期保留在一处；打印登记已独立抽取。 |
| `SalesOrderService` | 订单状态、版本锁、实数量和预留是一条订单命令边界；Excel 导入已由独立 `SalesOrderImportService` 负责。 |
| `ProductImportService` | 工作簿读写已抽到 `ProductImportWorkbookSupport`，整批数据库写入由 `ProductImportWriteService` 负责；剩余校验与表单组装共同完成导入行转换。 |
| `PurchaseQueryService` | 需求、订单、收货只读投影共用采购范围和快照规则，保持为采购读模型入口。 |
| `PurchaseOrderService` | 创建、分配、提交、转派、取消、少收关单与删除构成采购单状态机。 |
| `InventoryConversionService` | 转换单审批、成本基准和两 SKU 原子记账属于同一事务用例。 |
| `PurchaseDemandService` | 需求生成与分配编排共享同一销售订单来源快照，保留跨域调用顺序。 |
| `OrderReturnService` | 退货创建、审核及应收红字生成沿退货状态机执行。 |
| `SortingTaskService` | 分拣录入、完成、重新打开和打印登记受任务/明细占用规则约束。 |
| `SalesOrderImportService` | 独立处理导入模板、行校验、订单分组与幂等批量创建。 |
| `ProductTagService` | 标签主数据 CRUD 和商品关系维护由 `ProductTagRelationDao` / `ProductTagRelationService` 分担。 |
| `ProductCategoryService` | 单一分类主数据生命周期，校验已由 `ProductCategoryValidator` 承担。 |
| `ScmReportController` | 多个只读报表端点的 HTTP 声明；服务侧报表编排位于独立 Service。 |
| `DeliveryRouteController` | 路线 API 门面；业务校验、事务和范围规则留在 Service。 |
| `SalesOrderController` | 订单 API 门面；状态规则留在订单 Service。 |

## 结论

除商品导入工作簿处理与配送打印登记外，其余长类对应一个业务聚合或共享账本写入边界。按方法或行数继续拆分会重复锁、事务和范围规则，因此本轮保留这些边界。未修改测试类；既有 ArchUnit 契约继续负责分层检查，跨域 DAO 白名单由 `scm_cross_domain_dao_guard.py` 负责。
