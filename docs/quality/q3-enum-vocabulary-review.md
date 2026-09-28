# Q3 重复 Enum 词汇裁决

日期：2026-09-28。复核 SCM 生产代码中名称相同的 Enum 常量。相同字符串不自动表示同一状态机；裁决按字段含义、持久化 CHECK 和所属业务事实确定。

## 已统一

订单快照的商品类型与商品主档是同一词汇及同一 CHECK 集合。删除了 `ScmOrderProductTypeEnum`，订单代码改用公共 `ScmProductTypeEnum`；写入值仍为 `STANDARD` / `NON_STANDARD`。

## 保留为独立词汇

### 聚合状态

下列名称出现在不同聚合的状态机中，允许转换、终态和业务含义不同，继续由各自 Enum 与 DB CHECK 管理：

| 常量 | Enum |
| --- | --- |
| `ACTIVE` | `ScmDeliveryAssignmentStatusEnum`, `ScmInventoryReservationStatusEnum`, `ScmSortingOccupationStatusEnum` |
| `CANCELLED` | `ScmDeliveryRouteStatusEnum`, `ScmInventoryStocktakeStatusEnum`, `ScmOrderReturnStatusEnum`, `ScmSortingTaskStatusEnum`, `ScmOrderStatusEnum`, `ScmInventoryTransferStatusEnum`, `ScmPurchaseStatusEnum`, `ScmInventoryOutboundStatusEnum` |
| `COMPLETED` | `ScmInventoryConversionStatusEnum`, `ScmDeliveryRouteStatusEnum`, `ScmPutawayStatusEnum`, `ScmSortingTaskStatusEnum`, `ScmInventoryLossGainStatusEnum`, `ScmOrderRefundStatusEnum` |
| `CONFIRMED` | `ScmOrderStatusEnum`, `ScmInventoryStocktakeStatusEnum`, `ScmReceiptStatusEnum`, `ScmInventoryOutboundStatusEnum` |
| `DRAFT` | `ScmDeliveryRouteStatusEnum`, `ScmInventoryStocktakeStatusEnum`, `ScmOrderStatusEnum`, `ScmReceiptStatusEnum`, `ScmInventoryTransferStatusEnum`, `ScmPurchaseStatusEnum`, `ScmInventoryOutboundStatusEnum` |
| `PENDING` | `ScmInventoryConversionStatusEnum`, `ScmPutawayStatusEnum`, `ScmOrderReturnStatusEnum`, `ScmSortingTaskStatusEnum`, `ScmOrderStatusEnum`, `ScmInventoryLossGainStatusEnum`, `ScmPurchaseDemandStatusEnum`, `ScmOrderRefundStatusEnum`, `ScmDeliveryFulfillmentStatusEnum` |
| `RECEIVED` | `ScmPurchaseStatusEnum`, `ScmInventoryTransferStatusEnum` |
| `REJECTED` | `ScmInventoryConversionStatusEnum`, `ScmInventoryLossGainStatusEnum`, `ScmOrderReturnStatusEnum` |
| `RELEASED` | `ScmDeliveryAssignmentStatusEnum`, `ScmInventoryReservationStatusEnum`, `ScmSortingOccupationStatusEnum` |
| `SIGNED` | `ScmDeliverySignResultEnum`, `ScmDeliveryFulfillmentStatusEnum` |

`ENABLED` / `DISABLED` also remain domain-owned where the persisted facts differ: `ScmWarehouseStatusEnum` is the warehouse lifecycle vocabulary consumed by inventory and delivery, while `ScmProductMasterStatusEnum` adds `ARCHIVED` and explicitly differs from sale availability. General shared master-data enablement continues to use `ScmEnableStatusEnum`.

### Operation, source, and classification values

These same spellings describe different dimensions or different stored facts; sharing one broad Enum would accept values that a particular CHECK or command must reject.

| Constant(s) | Enum(s) | Ruling |
| --- | --- | --- |
| `AGREEMENT`, `CUSTOMER_TYPE`, `MARKET` | `ScmPriceSourceEnum`, `ScmOrderPriceSourceEnum` | Keep pricing resolution and order snapshot vocabularies separate; order additionally records `OVERRIDE`. |
| `CANCEL`, `CREATE`, `DELETE`, `SUBMIT`, `UPDATE` | `ScmOrderOperationTypeEnum`, `ScmPurchaseOperationTypeEnum`, `ScmPriceOperationTypeEnum`, `ProductImportService.ImportMode` | Keep per-aggregate audit events and the import command mode separate. |
| `DELIVERY_ROUTE` | `ScmInventorySourceDocumentTypeEnum`, `ScmDeliveryIdempotencyResourceTypeEnum` | One identifies an inventory source; the other identifies an idempotent command result. |
| `EXCEPTION` | `ScmDeliverySignResultEnum`, `ScmDeliveryFulfillmentStatusEnum` | Sign result and route fulfillment status have distinct owners. |
| `MANUAL`, `SYSTEM` | `ScmWeighingSourceEnum`, `ScmOrderQuantitySourceEnum`, `ScmPurchaseQuantitySourceEnum`, finance payable source Enums | The token describes different facts: measurement source, order quantity source, purchase quantity source, and finance provenance. |
| `NORMAL` | `ScmFinanceReverseEntryTypeEnum`, `ScmFinanceEntryTypeEnum`, `ScmInventoryWarningStatusEnum`, `ScmSortingResultEnum` | Finance direction, warning level, and sorting result are independent axes. |
| `OTHER` | `ScmUomCategoryEnum`, `ScmFinancePaymentMethodEnum` | Unit category and payment method are unrelated vocabularies. |
| `PAYABLE`, `RECEIVABLE` | `ScmFinanceWriteOffTargetTypeEnum`, `ScmFinanceBusinessTypeEnum` | A write-off target and a finance business fact are different roles. |
| `PAYMENT`, `RECEIPT` | `ScmFinanceBusinessTypeEnum`, `ScmFinanceWriteOffSourceTypeEnum` | A business fact and a write-off source are distinct roles. |
| `PURCHASE_RECEIPT_ITEM` | `ScmFinancePayableItemSourceTypeEnum`, `ScmInventorySourceDocumentTypeEnum` | Finance payable attribution and inventory source attribution are separate contracts. |
| `RECEIPT_PUTAWAY` | `ScmTodoCardEnum`, `ScmPurchaseOperationTypeEnum` | A dashboard card key is not a purchase audit event. |
| `WRITE_OFF` | `ScmFinanceOperationTypeEnum`, `ScmFinanceBusinessTypeEnum` | An operation-log action differs from a financial business type. |

## 精确保留的错误诊断 token

`ProductImportService` emits `CATEGORY_DISABLED` as the import row's `errorCode` argument to `addError`. It is an error token, not a persisted status, type, or source. Although the pricing domain has an enum member with the same spelling, importing that enum into product would create a product-to-pricing dependency. The quality guard therefore excludes only this exact literal in this exact import error-code position; the magic-string baseline does not retain it as debt.

## Maintenance rule

Enum duplication is reviewed when vocabularies change. New shared values must preserve each database CHECK and command boundary; do not merge Enums solely because their constants have the same spelling.
