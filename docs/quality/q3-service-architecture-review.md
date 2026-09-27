# Q3 Service 职责审查

日期：2026-09-28。依据整改计划 §11、§12 对审计中超过 400 行的服务职责进行复核。此文记录结构审查，不代表执行了测试或质量门禁。

## 处理结果

- `InventoryCommandService`：统一应用库存事实并维护余额行锁、单位、成本和流水约束。各写入入口共享同一账本不变量，保留集中写入边界。
- `ProductImportService`：拆出 `ProductImportWorkbookSupport` 负责模板和工作簿读写，`ProductImportWriteService` 继续负责整批持久化。导入服务保留行校验、业务数据解析和表单组装；这些步骤共同完成导入行到产品命令的转换。
- `PurchaseReceiptService`：管理收货单生命周期。确认收货、采购累计量和库存契约处于同一事务，不能拆成独立提交。
- `DeliveryRouteService`：负责线路生命周期及发车、签收和线路打印登记；这些命令都以线路及其订单分配为边界，维持同一锁与幂等约束。
- `SalesOrderService`：负责订单草稿、提交、确认、取消、实发量与显式预留等订单命令，状态转换依赖同一订单锁和版本规则。
- `PurchaseOrderService`：负责采购单创建、分配、提交、转派、取消、少收关单与删除，是一个采购单生命周期。
- `PurchaseQueryService`：提供采购需求、采购单和收货单的只读查询与导出数据，跨视图共用采购归属范围和同一领域快照规则。
- `InventoryConversionService`：管理规格转换单状态、成本基准和两 SKU 余额命令，审批必须在同一事务中完成。
- `ScmReportController` 是控制器，不属于 Service 行数规则；整改计划中的 Service 阈值不作为拆分控制器的理由。

## 结论

除商品导入的 Excel I/O 与数据库写入边界外，其余长类对应一个业务聚合或共享账本写入边界。按方法或行数继续拆分会重复锁、事务和范围规则，因此本轮保留这些边界。未修改测试类；架构规则保留在既有 ArchUnit 契约中。
