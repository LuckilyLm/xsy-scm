# W3 Pricing 价格中心 · Legacy Audit（只读审计）

状态：审计已完成人工评审；legacy 事实源随后由用户删除，当前仅保留审计文档与冻结快照
日期：2026-09-15
波次：W3 Pricing（Customer Agreement Price / Customer Type Price / Customer SKU Visibility / Price Resolver / Price Priority / Price Effective Time / Price History & Audit / Bulk Price Update / UNPRICED）
上游基线：W1 Product ✅ · W2 Customer + Supplier ✅ · SmartAdmin v3.31 · Java 21 · PostgreSQL · Flyway · Vue3 · Sa-Token

审计对象（均为**删除前的 legacy 快照，全部冻结只读**）：

| 代号 | 目录 | 定位 |
|---|---|---|
| **A** | `xsy-scm-server/`（删除前） | 业务规则唯一事实源（Legacy Spring Boot + PostgreSQL） |
| **A-web** | `xsy-scm-web/`（删除前） | Legacy React 后台，仅作字段与交互行为参考 |
| **C** | `project-reference-examples/xsy-scm/` | 旧二开 SmartAdmin v3.30 + MySQL8，**资产库非主干** |
| **A-doc** | `docs/superpowers/specs/2026-09-11-sprint5-price-center.md` | 价格中心原始规格（legacy 契约冻结件） |

本文件**只陈述事实与缺陷**，不含任何设计决策；设计见 `2026-09-15-w3-pricing-target-design.md`。
全程只读，未修改任何文件。

> Verification Closure 说明：本审计文档记录的是删除前的 legacy 事实。当前 `xsy-scm-server` / `xsy-scm-web` 文件已由用户删除，`verify_w3_legacy.py` 会报告缺失并保持 No-Go，不恢复文件。

---

## 1. 审计范围与方法

### 1.1 范围

W3 的 9 个能力点在 legacy 中的对应物：

| W3 能力 | Legacy 事实源 | 是否存在 |
|---|---|---|
| Customer Agreement Price | `customer_agreement_price` + `AgreementPriceService` | ✅ |
| Customer Type Price | `customer_type_price` + `CustomerTypePriceService` | ✅ |
| Customer SKU Visibility | `customer.visibility_policy` + `customer_sku_visibility` | ✅ |
| Price Resolver | `CustomerPriceResolver` | ✅（**无对外端点**） |
| Price Priority | `CustomerPriceResolver` 顺序 + `PriceSource` | ✅ |
| Price Effective Time | `effective_from` / `effective_to` + 半开区间 SQL | ✅ |
| Price History / Audit | 两张 operation_log + `PriceHistoryQueryService` | ✅ |
| Bulk Price Update | `CustomerTypePriceBatchService/Writer` | ✅ |
| UNPRICED 语义 | `PriceStatus.UNPRICED` | ⚠️ **契约存在、真实路径不可达**（见 §8.1） |

### 1.2 方法

1. 逐文件通读 legacy `com.xianshuyuan.scm.customer` 包全部 pricing 资产（controller 3 / dto 8 / entity 8 / mapper 8 / service 17 / vo 7 / row 1）。
2. 以 **DDL 为准**（`db/migration/V3/V7/V34/V35`）核对实体字段，避免被 Java 命名误导。
3. 逐个通读 legacy pricing 测试类，把「测试断言了什么」作为不变量的第二证据源。
4. 反向 grep 全部价格消费点（order / mall / customer controller），界定 W3 边界。
5. 通读 C 项目的 `t_product_price` / `t_customer_discount` / `t_customer_goods_visible` 三条资产线与前端页面。

### 1.3 结论摘要（先看这 8 条）

1. **价格优先级是 `AGREEMENT → CUSTOMER_TYPE → MARKET`，命中即返回、逐 SKU 独立、不叠加。** 权威实现在 `CustomerPriceResolver.resolve`（§3.1）。
2. **人工锁价不属于 Pricing，属于 Order。** Legacy Pricing 域**没有任何锁价概念**；`OVERRIDE` 只存在于 `order.entity.PriceSource` 与 `sales_order_item`（§3.7）。
3. **UNPRICED 是「契约完整、真实路径不可达」的死分支**（D1）。`PriceStatus.UNPRICED` 的文档语义齐全、mall 消费方齐全，但 `product_sku.market_price` 是 `NOT NULL` 且 `requireOrderable` 已过滤，导致 Resolver 永不返回 null。
4. **生效区间是半开 `[effective_from, effective_to)`，`effective_to IS NULL` = 长期有效。** 生效判定与重叠检测口径一致，且有 DB CHECK + `FOR UPDATE` 双保险，但**没有 DB 级排除约束**（D7）。
5. **批量调价是「整批事务 + 行号定位」，不是逐行容错**；**无幂等**（D2）、**逐条 INSERT 有 N+1**（D4）、响应结构无法承载逐行失败（D14）。
6. **审计基本完备但有操作人缺失**：协议价与批次审计硬编码 `"SYSTEM"`（D5），只有客户类型价用了真实用户。
7. **Legacy 前端不存在 UNPRICED 展示、不存在可见性独立页、不存在价格专属权限码、不存在 SKU option 端点**；SKU 选项一律从 `/products?pageSize=100` 嵌套拍平，超过 100 个 SPU 即不完整（§4、§8.2）。
8. **C 的三条价格资产线都是半成品**：`t_product_price` 无唯一约束/无时间窗/无审计；`t_customer_discount` 有表有 CRUD 但**无任何消费点**；`t_customer_goods_visible` 前端是裸 ID 输入的单行 CRUD；菜单 component 路径全部错位（§6）。

---

## 2. 后端资产清单

### 2.1 分层文件（legacy 包结构，V2 不沿用）

全部落在 `xsy-scm-server/src/main/java/com/xianshuyuan/scm/customer/`：

```text
controller/
  AgreementPriceController.java
  CustomerTypePriceController.java
  PriceHistoryController.java
dto/
  AgreementPricePageQuery.java        AgreementPriceSaveRequest.java
  CustomerTypePricePageQuery.java     CustomerTypePriceSaveRequest.java
  CustomerTypePriceBatchRequest.java  CustomerTypePriceBatchRowRequest.java
  PriceHistoryPageQuery.java          CustomerSkuVisibilityRequest.java
entity/
  CustomerAgreementPriceEntity.java           AgreementPriceOperationLogEntity.java
  CustomerTypePriceEntity.java                CustomerTypePriceOperationLogEntity.java
  CustomerPriceBatchAuditEntity.java          CustomerSkuVisibilityEntity.java
  VisibilityPolicy.java                       EnabledStatus.java
mapper/
  CustomerAgreementPriceMapper.java           AgreementPriceOperationLogMapper.java
  CustomerTypePriceMapper.java                CustomerTypePriceOperationLogMapper.java
  CustomerPriceBatchAuditMapper.java          CustomerSkuVisibilityMapper.java
  PriceHistoryMapper.java
row/
  PriceHistoryRow.java
service/
  CustomerPriceResolver.java                  ResolvedCustomerPrice.java
  PriceSource.java                            PriceStatus.java
  AgreementPriceService.java                  AgreementPriceValidator.java
  CustomerTypePriceService.java               CustomerTypePriceValidator.java
  CustomerTypePriceBatchService.java          CustomerTypePriceBatchWriter.java
  CustomerTypePriceBatchRowException.java     CustomerPriceBatchAuditService.java
  PriceHistoryQueryService.java               CustomerSkuVisibilityChangeSet.java
  OrderableSkuQueryService.java               CustomerErrorCodes.java
vo/
  AgreementPriceResponse.java                 CustomerTypePriceResponse.java
  CustomerTypePriceBatchResponse.java         PriceHistoryResponse.java
  CustomerSkuVisibilityResponse.java          OrderableSkuResponse.java
```

**架构事实：legacy 把「价格」全部放在 `customer` 包内，没有独立 pricing 包。** `PriceHistory` 与 `CustomerSkuVisibility` 也挂在 customer 域。V2 若拆 `module/scm/pricing` 属于**结构升级**，不是 legacy 形状。

### 2.2 枚举（逐值）

| 枚举 | 位置 | 取值 | 语义 |
|---|---|---|---|
| `PriceSource` | `customer/service/PriceSource.java:3` | `AGREEMENT`, `CUSTOMER_TYPE`, `MARKET` | **价格来源（Pricing 域）**。与 `PriceStatus` 一起构成解析结果 |
| `PriceStatus` | `customer/service/PriceStatus.java:10` | `PRICED`, `UNPRICED` | 是否有可成交价。Javadoc 原文：「UNPRICED 表示请求时点没有任何有效价格来源…客户端必须按"不可购买/询价"处理，不能用 0 元替代…零价是合法价格，属于 PRICED」 |
| `VisibilityPolicy` | `customer/entity/VisibilityPolicy.java:3` | `ALL_ENABLED`, `ALLOWLIST` | 可见性策略。**只有两种，没有黑名单** |
| `EnabledStatus` | `customer/entity/EnabledStatus.java:3` | `ENABLED`, `DISABLED` | 客户/客户类型状态（DDL 里是 `VARCHAR(16)`） |
| `order.entity.PriceSource` | `order/entity/PriceSource.java` | `AGREEMENT`, `CUSTOMER_TYPE`, `MARKET`, `OVERRIDE` | **Order 域的价格来源**，比 Pricing 域多一个 `OVERRIDE`。`OrderEnumsTest.exposesSupportedPriceSources` 断言了这个四值集合 |
| `ProductType` | `product/entity/` | `STANDARD`, `NON_STANDARD` | 被 `OrderableSkuResponse` 引用 |
| `ShelfStatus` | `product/entity/` | `ON_SHELF`, `OFF_SHELF` | 被 `OrderableSkuResponse` 引用 |

> **关键事实：`OVERRIDE` 不在 Pricing 域枚举里。** 两个同名枚举分属两个域，这是 legacy 刻意的边界。

### 2.3 实体字段（以 DDL 为准）

#### 2.3.1 `customer_agreement_price`（`V3__create_customer_pricing_schema.sql:60-82`）

| 列 | 类型 | Null | Default | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | N | — | PK |
| `customer_id` | `BIGINT` | N | — | 客户（无外键） |
| `sku_id` | `BIGINT` | N | — | SKU（无外键） |
| `unit_price` | `NUMERIC(18,4)` | N | — | 协议单价 |
| `effective_from` | `TIMESTAMPTZ` | N | — | 生效起点（含） |
| `effective_to` | `TIMESTAMPTZ` | **Y** | — | 失效终点（不含）；NULL = 长期有效 |
| `version` | `INTEGER` | N | 0 | 乐观锁 |
| `deleted` | `BOOLEAN` | N | FALSE | 逻辑删除 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | N | `CURRENT_TIMESTAMP` | |
| `created_by` / `updated_by` | `VARCHAR(64)` | Y | — | |

约束与索引：
```sql
CONSTRAINT ck_customer_agreement_price_amount CHECK (unit_price >= 0)
CONSTRAINT ck_customer_agreement_price_period CHECK (
    effective_to IS NULL OR effective_to > effective_from)
CONSTRAINT ck_customer_agreement_price_version CHECK (version >= 0)

CREATE INDEX idx_customer_agreement_price_lookup
    ON customer_agreement_price (customer_id, sku_id, effective_from, effective_to)
    WHERE deleted = FALSE;
```
**没有唯一约束、没有 EXCLUDE 约束、没有触发器。**

#### 2.3.2 `customer_type_price`（`V34__create_customer_type_price_schema.sql:1-21`）

列名与类型与 2.3.1 **完全同构**，唯一差别是 `customer_id` → `customer_type_id`：

| 列 | 类型 | Null | Default |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | N | — |
| `customer_type_id` | `BIGINT` | N | — |
| `sku_id` | `BIGINT` | N | — |
| `unit_price` | `NUMERIC(18,4)` | N | — |
| `effective_from` | `TIMESTAMPTZ` | N | — |
| `effective_to` | `TIMESTAMPTZ` | Y | — |
| `version` | `INTEGER` | N | 0 |
| `deleted` | `BOOLEAN` | N | FALSE |
| `created_at`/`updated_at` | `TIMESTAMPTZ` | N | `CURRENT_TIMESTAMP` |
| `created_by`/`updated_by` | `VARCHAR(64)` | Y | — |

```sql
CONSTRAINT ck_customer_type_price_amount CHECK (unit_price >= 0)
CONSTRAINT ck_customer_type_price_period CHECK (effective_to IS NULL OR effective_to > effective_from)
CONSTRAINT ck_customer_type_price_version CHECK (version >= 0)
CREATE INDEX idx_customer_type_price_lookup
    ON customer_type_price (customer_type_id, sku_id, effective_from, effective_to)
    WHERE deleted = FALSE;
```

#### 2.3.3 `customer_sku_visibility`（`V3:42-58`）

| 列 | 类型 | Null | Default |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | N | — |
| `customer_id` | `BIGINT` | N | — |
| `sku_id` | `BIGINT` | N | — |
| `version` | `INTEGER` | N | 0 |
| `deleted` | `BOOLEAN` | N | FALSE |
| `created_at`/`updated_at` | `TIMESTAMPTZ` | N | `CURRENT_TIMESTAMP` |
| `created_by`/`updated_by` | `VARCHAR(64)` | Y | — |

```sql
CREATE UNIQUE INDEX uk_customer_sku_visibility_active
    ON customer_sku_visibility (customer_id, sku_id) WHERE deleted = FALSE;
CREATE INDEX idx_customer_sku_visibility_sku_id ON customer_sku_visibility (sku_id);
```

> **建模要点：可见性策略不在明细表上，而在 `customer.visibility_policy`**（`V3:25`，`VARCHAR(20) NOT NULL DEFAULT 'ALL_ENABLED'`，CHECK `IN ('ALL_ENABLED','ALLOWLIST')`）。明细表**只存白名单 SKU**，不存「显示/屏蔽」标记。C 项目相反（`t_customer_goods_visible.visible_type` 1=显示 / 2=屏蔽），**C 的建模不迁移**。

#### 2.3.4 `customer_agreement_price_operation_log`（`V7__add_agreement_price_audit_log.sql`）

| 列 | 类型 | Null | Default |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | N | — |
| `agreement_price_id` | `BIGINT` | N | — |
| `operation_type` | `VARCHAR(20)` | N | — |
| `operator` | `VARCHAR(64)` | N | — |
| `before_data` | `JSONB` | Y | — |
| `after_data` | `JSONB` | Y | — |
| `created_at` | `TIMESTAMPTZ` | N | `CURRENT_TIMESTAMP` |
| `created_by` | `VARCHAR(64)` | Y | — |

```sql
CONSTRAINT ck_agreement_price_log_operation CHECK (operation_type IN ('CREATE','UPDATE','DELETE'))
CONSTRAINT ck_agreement_price_log_before_data CHECK (before_data IS NULL OR jsonb_typeof(before_data) = 'object')
CONSTRAINT ck_agreement_price_log_after_data  CHECK (after_data  IS NULL OR jsonb_typeof(after_data)  = 'object')
CREATE INDEX idx_agreement_price_log_price_created
    ON customer_agreement_price_operation_log (agreement_price_id, created_at DESC);
```

#### 2.3.5 `customer_type_price_operation_log`（`V35`）

与 2.3.4 同构，主键列名 `customer_type_price_id`，外加 `V35:23-27` 追加的两个时间倒序索引：
```sql
CREATE INDEX idx_customer_type_price_log_created ON customer_type_price_operation_log (created_at DESC);
CREATE INDEX idx_agreement_price_log_created    ON customer_agreement_price_operation_log (created_at DESC);
```

#### 2.3.6 `customer_price_batch_audit`（`V34:23-36`）

| 列 | 类型 | Null | Default |
|---|---|---|---|
| `id` | `BIGINT IDENTITY` | N | — |
| `batch_key` | `VARCHAR(100)` | N | — |
| `operation_type` | `VARCHAR(32)` | N | — |
| `result` | `VARCHAR(16)` | N | — |
| `row_count` | `INTEGER` | N | — |
| `error_data` | `JSONB` | Y | — |
| `created_at` | `TIMESTAMPTZ` | N | `CURRENT_TIMESTAMP` |
| `created_by` | `VARCHAR(64)` | Y | — |

```sql
CONSTRAINT ck_customer_price_batch_result CHECK (result IN ('SUCCESS','FAILED'))
CREATE INDEX idx_customer_price_batch_audit_key
    ON customer_price_batch_audit (batch_key, created_at DESC);
```
**`batch_key` 没有唯一约束**（D2）。

#### 2.3.7 `V34` 对 Order 表的连带修改（W3 必须知道的事实）

```sql
ALTER TABLE sales_order_item DROP CONSTRAINT ck_sales_order_item_draft_source;
ALTER TABLE sales_order_item ADD CONSTRAINT ck_sales_order_item_draft_source
    CHECK (draft_price_source IN ('MARKET','AGREEMENT','CUSTOMER_TYPE','OVERRIDE'));
ALTER TABLE sales_order_item DROP CONSTRAINT ck_sales_order_item_locked_source;
ALTER TABLE sales_order_item ADD CONSTRAINT ck_sales_order_item_locked_source
    CHECK (locked_price_source IS NULL OR locked_price_source IN ('MARKET','AGREEMENT','CUSTOMER_TYPE','OVERRIDE'));
```
→ **`sales_order_item` 已经有 `draft_price_source` / `locked_price_source` / `locked_price_source_id` / `manual_price_override` / `manual_price_reason` / `locked_unit_price` 列，且白名单已含 `CUSTOMER_TYPE` 与 `OVERRIDE`。** 这是 W3 交付 Resolver 后 W4 能直接落地的既有接口。

#### 2.3.8 Java 实体注解事实

| 实体 | `@TableName` | `@TableId` | `@Version` | `@TableLogic` |
|---|---|---|---|---|
| `CustomerAgreementPriceEntity` | `customer_agreement_price` | `IdType.AUTO` | ✅ | ✅（无 value/delval 显式参数） |
| `CustomerTypePriceEntity` | `customer_type_price` | `IdType.AUTO` | ✅ | ✅ |
| `CustomerSkuVisibilityEntity` | `customer_sku_visibility` | `IdType.AUTO` | ✅ | ✅ |
| `CustomerPriceBatchAuditEntity` | `customer_price_batch_audit`（`autoResultMap = true`） | `IdType.AUTO` | ❌ | ❌（审计表无 version/deleted） |

> V2 门禁要求 SCM 实体显式写 `@TableLogic(value="false", delval="true")`，legacy 未写——这是 V2 要补的适配项，不是 legacy 不变量。

### 2.4 Mapper 自定义 SQL（穷尽 7 处）

| # | Mapper | statement | 关键语义 |
|---|---|---|---|
| 1 | `CustomerAgreementPriceMapper` | `selectEffective` | 半开区间生效查询，`ORDER BY effective_from DESC, id DESC`，**无 LIMIT** |
| 2 | `CustomerAgreementPriceMapper` | `countOverlapping` | 重叠计数，`COALESCE(#{to},'infinity'::timestamptz)` |
| 3 | `CustomerAgreementPriceMapper` | `selectAgreementPage` | 分页 + keyword（客户编码/名称、SPU 名称、SKU 编码、SKU id 文本） |
| 4 | `CustomerAgreementPriceMapper` | `lockCustomer` | `SELECT id FROM customer WHERE id=? AND deleted=FALSE FOR UPDATE` |
| 5 | `CustomerAgreementPriceMapper` | `softDelete` | 原子谓词 `id + version + deleted=FALSE`，`updated_by='SYSTEM'` 硬编码 |
| 6 | `CustomerTypePriceMapper` | `selectEffective` / `countOverlapping` / `selectPricePage` / `lockCustomerType` / `softDelete` | 与 1–5 同构，锁的是 `customer_type` 行 |
| 7 | `CustomerSkuVisibilityMapper` | `selectActiveByCustomerId` / `selectVisibleSkuIds` / `softDeleteOwned` | 按客户取白名单；批量软删用 `customer_id + deleted=FALSE + id IN(...)` |
| 8 | `PriceHistoryMapper` | `selectHistoryPage` | 两张 log 表 `UNION ALL` + `LEFT JOIN` 主表带出当前值 |

原文（关键两处）：

**生效查询** `CustomerAgreementPriceMapper.xml:4-11`
```sql
SELECT * FROM customer_agreement_price
WHERE customer_id = #{customerId} AND deleted = FALSE
AND effective_from <= #{at} AND (effective_to IS NULL OR effective_to > #{at})
AND sku_id IN (...)
ORDER BY effective_from DESC, id DESC
```

**重叠检测** `CustomerAgreementPriceMapper.xml:13-19`
```sql
SELECT COUNT(*) FROM customer_agreement_price
WHERE customer_id = #{customerId} AND sku_id = #{skuId} AND deleted = FALSE
<if test="id != null">AND id != #{id}</if>
AND effective_from < COALESCE(#{to}, 'infinity'::timestamptz)
AND (effective_to IS NULL OR effective_to > #{from})
```

**历史合并** `PriceHistoryMapper.xml:30-98`：`customer_agreement_price_operation_log LEFT JOIN customer_agreement_price`（`source='AGREEMENT'`）`UNION ALL` `customer_type_price_operation_log LEFT JOIN customer_type_price`（`source='CUSTOMER_TYPE'`），外层 `ORDER BY h.operated_at DESC, h.id DESC`；`current_deleted` 用 `COALESCE(p.deleted, TRUE)`。注释明确「价格表使用软删除，这里不做 deleted 过滤，便于追溯已删除记录」。

### 2.5 API 契约（legacy：`ApiResponse` + `PageData`，`/api/**`）

| Controller | 方法 | URL | 请求 | 响应 | 权限 |
|---|---|---|---|---|---|
| `AgreementPriceController` | GET | `/api/customer-agreement-prices`（别名 `/api/agreement-prices`） | `page@Min(1)`、`pageSize@Min(1)@Max(100)`、`customerId?`、`skuId?`、`keyword?@Size(max=100)` | `ApiResponse<PageData<AgreementPriceResponse>>` | **无** |
| | POST | 同上 | `@Valid AgreementPriceSaveRequest` | `ApiResponse<Long>` | **无** |
| | PUT | `/{id}` | `@Valid AgreementPriceSaveRequest` | `ApiResponse<Void>` | **无** |
| | DELETE | `/{id}?version=` | `version@Min(0)` | `ApiResponse<Void>` | **无** |
| `CustomerTypePriceController` | GET | `/api/customer-type-prices` | `page/pageSize/customerTypeId?/skuId?/keyword?` | `PageData<CustomerTypePriceResponse>` | **无** |
| | POST | 同上 | `CustomerTypePriceSaveRequest` | `ApiResponse<Long>` | **无** |
| | POST | `/batch` | `CustomerTypePriceBatchRequest` | `CustomerTypePriceBatchResponse` | **无** |
| | PUT | `/{id}` | `CustomerTypePriceSaveRequest` | `ApiResponse<Void>` | **无** |
| | DELETE | `/{id}?version=` | `version@Min(0)` | `ApiResponse<Void>` | **无** |
| `PriceHistoryController` | GET | `/api/price-history` | `page/pageSize/source?/customerId?/customerTypeId?/skuId?/operationType?/effectiveFrom?/effectiveTo?/operatedFrom?/operatedTo?`（后四个 `OffsetDateTime`，ISO DATE_TIME） | `PageData<PriceHistoryResponse>` | **无** |
| `CustomerController` | GET | `/api/customers/{id}/skus` | — | `List<OrderableSkuResponse>` | **无** |

> **Legacy pricing 全部端点零权限注解**（全项目靠 `AuthorityRules` + `@PreAuthorize` 判定，价格端点没有任何一处声明）。V2 必须补 `@SaCheckPermission`（对应 legacy 缺陷 D-P1）。

DTO / VO 字段（完整）：

```java
AgreementPriceSaveRequest(Integer version, @NotNull Long customerId, @NotNull Long skuId,
                          @NotNull @Digits(integer=14,fraction=4) BigDecimal unitPrice,
                          @NotNull OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo)

CustomerTypePriceSaveRequest(Integer version, @NotNull Long customerTypeId, @NotNull Long skuId,
                             @NotNull @Digits(integer=14,fraction=4) BigDecimal unitPrice,
                             @NotNull OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo)

CustomerTypePriceBatchRequest(@NotBlank @Size(max=100) String batchKey,
                              @NotEmpty @Size(max=500) List<@Valid CustomerTypePriceBatchRowRequest> rows)

CustomerTypePriceBatchRowRequest(@Positive int rowNumber, @NotNull Long customerTypeId, @NotNull Long skuId,
                                 @NotNull @Digits(integer=14,fraction=4) BigDecimal unitPrice,
                                 @NotNull OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo)

AgreementPriceResponse(Long id, Integer version, Long customerId, Long skuId, String unitPrice,
                       OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo)
CustomerTypePriceResponse(Long id, Integer version, Long customerTypeId, Long skuId, String unitPrice,
                          OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo)
CustomerTypePriceBatchResponse(String batchKey, int rowCount, List<Long> ids)
PriceHistoryResponse(Long id, String source, Long priceId, Long customerId, Long customerTypeId, Long skuId,
                     String operationType, String operator, OffsetDateTime operatedAt,
                     JsonNode beforeData, JsonNode afterData,
                     String currentUnitPrice, OffsetDateTime currentEffectiveFrom,
                     OffsetDateTime currentEffectiveTo, boolean currentDeleted)
CustomerSkuVisibilityRequest(Long id, Integer version, @NotNull Long skuId)
CustomerSkuVisibilityResponse(Long id, Integer version, Long skuId)
OrderableSkuResponse(Long skuId, Long spuId, String productName, String skuCode, String specName,
                     Map<String,String> specValues, String saleUnit, ProductType productType,
                     String marketPrice, ShelfStatus status)
```

**金额对外契约：`unitPrice` 是 4 位小数字符串**（`setScale(4).toPlainString()`，见 `AgreementPriceService.java:106`、`CustomerTypePriceService.java:117`、`OrderableSkuQueryService.java:57`）。

**分页上限：价格/客户/商品全部 `@Max(100)`。** 这是 legacy 前端 SKU 选择器不完整的根因（§4.6）。

### 2.6 错误码（`customer/service/CustomerErrorCodes.java`，逐条）

```java
CUSTOMER_NOT_FOUND              = 40430 / NOT_FOUND   "客户不存在"
CUSTOMER_TYPE_NOT_FOUND         = 40431 / NOT_FOUND   "客户类型不存在"
AGREEMENT_PRICE_NOT_FOUND       = 40432 / NOT_FOUND   "协议价不存在"
CUSTOMER_TYPE_PRICE_NOT_FOUND   = 40433 / NOT_FOUND   "客户类型价不存在"
CUSTOMER_DISABLED               = 40930 / CONFLICT    "客户未启用"
SKU_NOT_VISIBLE                 = 40931 / CONFLICT    "SKU 对该客户不可见或未上架"
VISIBILITY_NOT_OWNED            = 40932 / CONFLICT    "可见性记录不属于当前客户"
AGREEMENT_PRICE_OVERLAP         = 40933 / CONFLICT    "协议价有效期重叠"
VERSION_CONFLICT                = 40934 / CONFLICT    "数据已被其他操作修改，请刷新后重试"
CUSTOMER_TYPE_PRICE_OVERLAP     = 40935 / CONFLICT    "客户类型价有效期重叠"
PRICE_INVALID                   = 40030 / BAD_REQUEST "协议价不能小于零"
PERIOD_INVALID                  = 40031 / BAD_REQUEST "结束时间必须晚于开始时间"
```

价格域直接相关的 7 个：**40030 / 40031 / 40432 / 40433 / 40931 / 40932 / 40933 / 40935**。
`40930` 被价格解析前置守卫复用；`40934` 是 legacy 的版本冲突码（V2 用 SmartAdmin 的 `40921`）。

> **`40931` 被两个语义复用**：既表示「不可见/未上架」，也被 `CustomerSkuVisibilityChangeSet:27` 用来表示「SKU 可见性重复」（带自定义消息）。语义重载（D6）。

### 2.7 时间与精度契约

| 项 | Legacy 事实 | 证据 |
|---|---|---|
| 时间类型 | 全链路 `TIMESTAMPTZ` + Java `OffsetDateTime` | `V3:65-66`、`V34:6-7` |
| 区间 | 半开 `[effective_from, effective_to)` | `selectEffective` SQL |
| 长期有效 | `effective_to IS NULL` | 同上 + `ck_..._period` |
| 时间基准 | 由调用方传入 `OffsetDateTime at`，服务端统一比较 | `CustomerPriceResolver.resolve(..., at)` |
| 客户端时区 | 规格明确「客户端不得按本地时区重算」 | `sprint5-price-center.md:23` |
| 金额精度 | `NUMERIC(18,4)`；API 出参 4 位小数字符串 | `V3:64`、`V34:5`、`setScale(4)` |
| 入参精度 | `@Digits(integer=14, fraction=4)` | `AgreementPriceSaveRequest:9` |
| 0 元 | 允许（`CHECK (unit_price >= 0)`，校验器只拒负） | `V3:73`、`V34:14`、`AgreementPriceValidator:11` |

---

## 3. 必须保留的业务不变量（逐条定位到实现与测试）

编号 `P1…P22`，W3 目标设计的覆盖清单以此为对账依据。

### 3.1 价格优先级（P1–P4）

**P1 · 优先级顺序 = `AGREEMENT → CUSTOMER_TYPE → MARKET`，命中即返回、不叠加、逐 SKU 独立。**

`CustomerPriceResolver.java:34-52`（完整）：
```java
public List<ResolvedCustomerPrice> resolve(long customerId, List<Long> skuIds, OffsetDateTime at) {
    if (skuIds.isEmpty()) return List.of();
    CustomerEntity customer = customers.requireEnabled(customerId);
    var rows = orderable.requireOrderable(customerId, skuIds);
    Map<Long, CustomerAgreementPriceEntity> agreement = prices.selectEffective(customerId, skuIds, at).stream()
            .collect(Collectors.toMap(CustomerAgreementPriceEntity::getSkuId, Function.identity(), (a, b) -> a));
    Map<Long, CustomerTypePriceEntity> type = typePrices.selectEffective(customer.getCustomerTypeId(), skuIds, at).stream()
            .collect(Collectors.toMap(CustomerTypePriceEntity::getSkuId, Function.identity(), (a, b) -> a));
    Map<Long, ProductSkuEntity> byId = rows.stream()
            .collect(Collectors.toMap(ProductSkuEntity::getId, Function.identity()));
    return skuIds.stream().map(id -> {
        var a = agreement.get(id);
        if (a != null) return new ResolvedCustomerPrice(id, a.getUnitPrice(), PriceSource.AGREEMENT, a.getId());
        var t = type.get(id);
        if (t != null) return new ResolvedCustomerPrice(id, t.getUnitPrice(), PriceSource.CUSTOMER_TYPE, t.getId());
        var sku = byId.get(id);
        return new ResolvedCustomerPrice(id, sku == null ? null : sku.getMarketPrice(), PriceSource.MARKET, null);
    }).toList();
}
```
测试：`CustomerPriceResolverTest.choosesAgreementPriceAndFallsBackToMarketPriceInBatch`（协议价 6.5000 → `AGREEMENT`；无协议价 → 市场价 8.0000 → `MARKET`）、`choosesCustomerTypePriceBeforeFallingBackToMarketPrice`（类型价 7.2000 → `CUSTOMER_TYPE`，`sourceRecordId=11`）、`returnsEmptyWithoutDatabaseCalls`（空列表不触库）。

**P2 · 来源记录 ID 必须随价格一起返回**（`sourceRecordId`）：协议价/类型价返回命中行 `id`，市场价返回 `null`。用于订单锁价溯源。

**P3 · 客户类型价按「客户当前启用类型」解析**：`customer.getCustomerTypeId()`，不是订单历史类型。规格 §6 原文：「客户类型变更不会改写历史订单；新目录/预览按请求时点读取当前类型」。

**P4 · 解析前置守卫：客户必须启用 + 所有 SKU 必须可下单。**
`CustomerPriceResolver:36-37` → `requireEnabled`（否则 `CUSTOMER_DISABLED=40930`）+ `requireOrderable`（任一 SKU 不可见/未上架 → 整批 `SKU_NOT_VISIBLE=40931`）。

> **规格 §2 还提到 `BASE`**（「SKU 基础售价/市场价（`BASE`/现有兼容来源）」）。Legacy 实现里第 3 级来源是 `MARKET`（`product_sku.market_price`），**没有独立的 BASE 表/列**。C 项目才有 4 值 `PriceTypeEnum`（1 基础价 / 2 客户分级价 / 3 时价 / 4 协议价）。**W3 只能承认 `MARKET`，不能引入 BASE 或 C 的 4 值模型。**

### 3.2 生效时间与重叠（P5–P9）

**P5 · 半开区间 `[effective_from, effective_to)`。** 起点含、终点不含。
**P6 · `effective_to IS NULL` = 长期有效**，且 DDL 允许。
**P7 · 不允许重叠：同一 `(customer_id, sku_id)` 或 `(customer_type_id, sku_id)` 在同一时点最多一条生效记录。**
应用层保证，流程固定为：`lockCustomer(customer_id)` → `countOverlapping(...)` → 写入。
`AgreementPriceService.java:83-94` 注释原文：
> "Locking the stable customer row serializes all agreement writes for this customer and protects the subsequent overlap check without requiring PostgreSQL extensions."

**P8 · 重叠判定必须与生效判定口径一致**：`existing.from < new.to && (existing.to IS NULL || existing.to > new.from)`，`new.to = NULL` 视为 `infinity`。
**P9 · `effective_to > effective_from` 必须有 DB CHECK + 应用校验双保险**（`V3:74-76`、`AgreementPriceValidator:12-13` → `PERIOD_INVALID=40031`）。

测试：`AgreementPriceServiceTest.locksCustomerBeforeCheckingOverlap`（顺序断言：先 `lockCustomer(3)` 后 `countOverlapping(null,3,8,from,to)`）、`CustomerTypePriceServiceTest.createRejectsOverlappingHalfOpenPeriodBeforeInsert`（`countOverlapping=1` → `CUSTOMER_TYPE_PRICE_OVERLAP`，且 `never().insert`）、`AgreementPriceValidatorTest.rejectsEmptyOrReversedPeriod`。

### 3.3 价格决胜（P10）

**P10 · 同一 (客户/类型, SKU) 出现多条生效记录时的决胜规则 = `effective_from DESC` → `id DESC`。**
SQL 排序（`selectEffective`）+ Java `Collectors.toMap(..., (a,b) -> a)` 保留首元素共同实现。**这是防御性兜底**：正常路径下 P7 已排除重叠。**未被任何测试覆盖**（§6.2 B4）。

### 3.4 UNPRICED 与 0 元（P11–P13）

**P11 · `unitPrice == null` 即 `UNPRICED`；`unitPrice != null` 即 `PRICED`，与数值无关。**
`ResolvedCustomerPrice`（完整）：
```java
public record ResolvedCustomerPrice(Long skuId, BigDecimal unitPrice, PriceSource source, Long sourceRecordId) {
    /** 是否存在可成交价格。零价（0.0000）是合法价格；只有解析不到任何价格来源时才是缺价。 */
    public boolean priced() { return unitPrice != null; }
    public PriceStatus status() { return priced() ? PriceStatus.PRICED : PriceStatus.UNPRICED; }
}
```

**P12 · 0.0000 是有效价，不是缺价。** 测试固化：
- `MallCartServiceTest.zeroPriceIsTreatedAsAUsablePrice`（0.0000 可加购）
- `MallCheckoutServiceTest.previewAcceptsZeroPriceBecauseZeroIsStillAPrice`（`unitPrice="0.0000"`, `totalAmount="0.0000"`）
- `PriceHistoryQueryIT`（类型价 0.0000 正常落库、查询、`currentUnitPrice="0.0000"`）
- 前端文案 `CustomerTypePricePage.tsx:49`：「客户类型价按有效期维护，零价也是有效价格」

**P13 · UNPRICED 的消费方语义（W3 不实现，但必须保留契约）**：

| 消费点 | 行为 | 证据 |
|---|---|---|
| 购物车读取 | **保留行**、`available=false`、`priceStatus=UNPRICED`、`unitPrice=null`、`reason="商品暂无有效价格"`，不静默删除 | `MallCartServiceTest.listKeepsUnpricedRowButMarksItNotSettleable` |
| 加购 / 改量 | 拒绝，抛 `PRICE_NOT_RESOLVED`，不落库 | `MallCartServiceTest.addRejectsSkuWithoutAnyResolvedPrice` |
| 结算预览 | **拒绝整次**，消息列出缺价 SKU；先拒后不解析地址 | `MallCheckoutServiceTest.previewRejectsWholeRequestWhenAnyLineIsUnpriced` |
| 提交 | 即使绕过预览也拒绝，不得创建草稿订单 | 规格 §5 |
| 目录 | 成功返回商品 + `priceStatus=UNPRICED` 显式字段 | 规格 §3 |

### 3.5 SKU 可见性（P14–P17）

**P14 · 只有两种策略：`ALL_ENABLED`（全部在售可见）/ `ALLOWLIST`（仅白名单）。没有黑名单。**

**P15 · `ALLOWLIST` 时白名单为空 = 一个 SKU 都不可见（返回空集，不是"回退到全部"）。**
`OrderableSkuQueryService.listOrderable`：
```java
if (customer.getVisibilityPolicy() == VisibilityPolicy.ALL_ENABLED) {
    rows = skus.selectAllOrderable();
} else {
    List<CustomerSkuVisibilityEntity> visible = visibility.selectActiveByCustomerId(customerId);
    if (visible.isEmpty()) return List.of();
    rows = skus.selectOrderableByIds(visible.stream().map(CustomerSkuVisibilityEntity::getSkuId).toList());
}
```

**P16 · 可见性是硬门禁：任一 SKU 不可见 → 整批抛 `SKU_NOT_VISIBLE`（40931），无逐 SKU 降级。**
`requireOrderable`：`valid = 可下单 SKU ∩ 白名单`；`valid.size() != skuIds.size()` → 抛错。

**P17 · 可见性写入 = 整表替换（差量），带乐观锁；已存在记录不得更换 SKU；请求内 SKU 不得重复。**
`CustomerSkuVisibilityChangeSet.between`（完整见 §2.4 引用）：`inserted` / `updated` / `removedIds` 三段差量；`VISIBILITY_NOT_OWNED=40932` 用于「不属于当前客户」与「不能更换 SKU」；重复 SKU 抛 `SKU_NOT_VISIBLE` + 「SKU 可见性重复」。
`CustomerService.validateVisibilityRequest:128-141`：`ALL_ENABLED` 时 `visibilities` 必须为空（否则抛「不能提交可见性明细」）；`ALLOWLIST` 时校验 SKU 可下单。

测试：`CustomerSkuVisibilityChangeSetTest`（4 个方法：保留身份/外来记录/换 SKU/重复）、`CustomerServiceTest.rejectsVisibilityRowsForAllEnabledPolicy`。

**P18 · 可见性影响范围（消费点穷尽）**：
- 价格解析前置守卫（`CustomerPriceResolver:37`）
- 后台客户可下单 SKU 列表 `GET /api/customers/{id}/skus`
- 商城目录 SQL 层过滤（`MallCatalogMapper.xml:42-46,61-65,78-82`：`ALL_ENABLED OR EXISTS(可见性)`）
- 商城购物车 / 结算
- 销售订单创建与提交

### 3.6 批量调价（P19–P22）

**P19 · 整批事务：任一行失败则全部回滚，不是逐行部分成功。**
`CustomerTypePriceBatchWriter.write` 有 `@Transactional`；`CustomerTypePriceBatchService.create` **无** `@Transactional`，只负责「失败也审计」。

**P20 · 失败必须带行号定位，且必须留 FAILED 审计（独立事务，不被回滚）。**
```java
// CustomerTypePriceBatchWriter.java:19-33
@Transactional
public CustomerTypePriceBatchResponse write(CustomerTypePriceBatchRequest request) {
    List<Long> ids = new ArrayList<>(request.rows().size());
    for (int index = 0; index < request.rows().size(); index++) {
        var row = request.rows().get(index);
        try {
            ids.add(prices.create(row.toSaveRequest()));
        } catch (BusinessException failure) {
            throw new CustomerTypePriceBatchRowException(row.rowNumber(), row.skuId(), failure);
        }
    }
    audits.recordSuccess(request);
    return new CustomerTypePriceBatchResponse(request.batchKey().trim(), ids.size(), List.copyOf(ids));
}
// CustomerTypePriceBatchService.java:14-21
public CustomerTypePriceBatchResponse create(CustomerTypePriceBatchRequest request) {
    try { return writer.write(request); }
    catch (RuntimeException failure) { audits.recordFailed(request, failure); throw failure; }
}
```
`CustomerTypePriceBatchRowException` 消息格式固定为 `"第 {rowNumber} 行：{cause.message}"`，并携带 `rowNumber` / `skuId`。
`CustomerPriceBatchAuditService.recordFailed` 用 `@Transactional(propagation = REQUIRES_NEW)`，写 `error_data = {code, message, rowNumber?, skuId?}`，非业务异常 code 兜底 `50000`。
测试：`CustomerTypePriceBatchServiceTest.failedBatchIsAuditedAfterTransactionalWriterRollsBack`。

**P21 · 行数上限 500，批次号上限 100 字符。**（`@Size(max=500)` / `@Size(max=100)`）

**P22 · 审计记录内容**：`batch_key` / `operation_type`（固定 `CUSTOMER_TYPE_PRICE_CREATE`）/ `result`（`SUCCESS`|`FAILED`）/ `row_count`（= 请求行数，**不是成功行数**）/ `error_data` / `created_at` / `created_by`。
SUCCESS 只记 `rowCount`，**不记成功行 ID 明细**（ID 只在 HTTP 响应里）。

### 3.7 人工锁价归属（P23）——**审计重点**

**P23 · Legacy 的「人工锁价」属于 Order 域，Pricing 域完全没有锁价概念。**

| 事实 | 证据 |
|---|---|
| Pricing 域枚举只有 3 值，无 `OVERRIDE` | `customer/service/PriceSource.java:3` = `{AGREEMENT, CUSTOMER_TYPE, MARKET}` |
| `OVERRIDE` 只定义在 Order 域 | `order/entity/PriceSource.java`；`OrderEnumsTest.exposesSupportedPriceSources` 断言 `[AGREEMENT, CUSTOMER_TYPE, MARKET, OVERRIDE]` |
| 锁价字段全在订单行上 | `SalesOrderItemEntity:34-38` → `manualPriceOverride` / `manualPriceReason` / `lockedUnitPrice` / `lockedPriceSource` / `lockedPriceSourceId` |
| 锁价发生在订单提交时 | `SalesOrderApplicationService:128-141`：`manualPriceOverride=true` → `lockedUnitPrice=draftUnitPrice` + `lockedPriceSource=OVERRIDE`；否则用 Resolver 结果覆盖 `draft*` 与 `locked*` |
| 改价必填原因、非改价不得带单价 | `SalesOrderValidator:23-27` → `OVERRIDE_REASON_REQUIRED` / `INVALID_PRICE_OVERRIDE` |
| DB 白名单已含 OVERRIDE | `V4:68-70,91-98` + `V34:38-44` |

> 结论：**W3 不得在 Pricing 域引入任何「客户锁价 / 协议锁价 / 人工锁价」概念。** 规格 §8 原文也把两者分开：「订单锁定价格和人工改价历史**独立于当前价格表**，基础资料修改不得静默改变已锁定订单。」
> 因此 `PriceHistory` 只有 `AGREEMENT` / `CUSTOMER_TYPE` 两个来源，**不包含订单锁价或 OVERRIDE 历史**（前端 `PriceHistorySource = 'AGREEMENT' | 'CUSTOMER_TYPE'` 印证）。

### 3.8 价格历史与审计（P24–P26）

**P24 · 每次增删改都写 operation_log，且与主表在同一事务内**（不会出现"改了价没留痕"）。
`AgreementPriceService:56,69,80`（CREATE/UPDATE/DELETE）、`CustomerTypePriceService:53,66,77`。

**P25 · 快照语义固定**：
- `CREATE`：`before=null`，`after=快照`
- `UPDATE`：`before=旧快照`，`after=新快照`
- `DELETE`：`before=旧快照`，`after=旧快照 + deleted=true + version=version+1`
快照字段（`AgreementPriceService.snapshot:110-125`）：`id, customerId, skuId, unitPrice(4位字符串), effectiveFrom(ISO), effectiveTo(ISO|null), version, deleted`。**用 JSONB 存储，不展开列。**

**P26 · 价格历史查询的筛选语义正交**：
- `source` ∈ {AGREEMENT, CUSTOMER_TYPE}，非法值归一为 `null`（不筛选，避免拼写错误静默返回空）
- `customerId` 只作用于协议价；`customerTypeId` 只作用于客户类型价
- `effectiveFrom/To` 按**当前价格记录的生效区间**做重叠匹配（`COALESCE(current_effective_to,'infinity') > effectiveFrom` 且 `current_effective_from < effectiveTo`）
- `operatedFrom/To` 按操作时间过滤
- 排序 `operated_at DESC, id DESC`
测试：`PriceHistoryQueryIT.mergesBothSourcesAndAppliesSourceCustomerTypeSkuPeriodAndOperationFilters`。

### 3.9 客户/类型删除的引用检查（P27）

**P27 · 删除客户类型前必须检查是否被客户引用；删除客户前的引用检查位已预留。**
Legacy 侧没有 pricing 引用检查（`customer_type_price` 引用 `customer_type_id` 但不阻止删除）——这是 legacy 缺陷（D-P2）。

### 3.10 不变量总表（W3 对账用）

| 编号 | 不变量 | 测试证据 |
|---|---|---|
| P1 | 优先级 AGREEMENT → CUSTOMER_TYPE → MARKET，命中即返回 | `CustomerPriceResolverTest` ×3 |
| P2 | `sourceRecordId` 随结果返回 | 同上（`sourceRecordId=11`） |
| P3 | 类型价按客户当前启用类型 | `CustomerPriceResolverTest`（mock `getCustomerTypeId`） |
| P4 | 客户启用 + SKU 可下单前置守卫 | 无直接测试（B3） |
| P5 | 半开区间 `[from, to)` | `CustomerTypePriceServiceTest` + SQL |
| P6 | `to IS NULL` = 长期有效 | `PriceHistoryQueryIT`（0 元行） |
| P7 | 同客户/类型 + SKU 不得重叠 | `AgreementPriceServiceTest.locksCustomerBeforeCheckingOverlap`、`CustomerTypePriceServiceTest.createRejectsOverlappingHalfOpenPeriodBeforeInsert` |
| P8 | 重叠判定与生效判定口径一致 | SQL 层，无集成测试（B5） |
| P9 | `to > from` 双保险 | `AgreementPriceValidatorTest.rejectsEmptyOrReversedPeriod` + DB CHECK |
| P10 | 决胜 `effective_from DESC, id DESC` | **无测试（B4）** |
| P11 | null = UNPRICED；非 null = PRICED | `MallCartServiceTest`（mock 构造 null） |
| P12 | 0.0000 是有效价 | `MallCartServiceTest.zeroPriceIsTreatedAsAUsablePrice`、`MallCheckoutServiceTest.previewAcceptsZeroPriceBecauseZeroIsStillAPrice`、`PriceHistoryQueryIT` |
| P13 | UNPRICED 消费方语义 | `MallCartServiceTest` ×3、`MallCheckoutServiceTest` ×1 |
| P14 | 只有 ALL_ENABLED / ALLOWLIST | `CustomerServiceTest`（DTO 枚举） |
| P15 | ALLOWLIST 空 = 全部不可见 | **无测试（B9）** |
| P16 | 可见性硬门禁 | **无测试（B9）** |
| P17 | 可见性整表替换差量 + 乐观锁 | `CustomerSkuVisibilityChangeSetTest` ×4、`CustomerServiceTest` |
| P18 | 可见性影响范围 | 商城测试部分覆盖 |
| P19 | 批量整批回滚 | `CustomerTypePriceBatchServiceTest` |
| P20 | 行号定位 + FAILED 审计 | `CustomerTypePriceBatchServiceTest` |
| P21 | 行上限 500 / 批次号 100 | Bean Validation |
| P22 | 批次审计字段 | **无测试（B8）** |
| P23 | 锁价属 Order，不属 Pricing | `OrderEnumsTest.exposesSupportedPriceSources` |
| P24 | 增删改必留痕 | `AgreementPriceServiceTest`、`CustomerTypePriceServiceTest` ×2 |
| P25 | 快照语义（含 DELETE 的 version+1） | `CustomerTypePriceServiceTest.deleteAppendsAnOperationLogEntryMarkingTheRowDeleted` |
| P26 | 历史筛选正交 | `PriceHistoryQueryIT` |
| P27 | 引用检查 | `CustomerServiceTest.rejectsDisabledCustomerType`（类型状态，非引用） |

---

## 4. 前端 legacy 行为审计（React，仅作行为参考）

### 4.1 页面清单

| # | 页面 | 文件 | 路由 path | 菜单项 | 一级分组 | 路由权限 |
|---|---|---|---|---|---|---|
| 1 | 协议价 | `src/pages/customer/AgreementPricePage.tsx:21` | `customer-agreement-prices` | 协议价 | `customers`（客户） | `customer.read` |
| 2 | 客户类型价 | `CustomerTypePricePage.tsx:17` | `customer-type-prices` | 客户类型价 | 客户 | `customer.read` |
| 3 | 批量调价 | `CustomerTypePriceBatchPage.tsx:16` | `customer-type-prices/batch` | 批量调价 | 客户 | `customer.read` |
| 4 | 价格历史 | `PriceHistoryPage.tsx:26` | `price-history` | 价格历史 | 客户 | `customer.read` |
| 5 | 客户档案（含 SKU 可见性） | `CustomerPage.tsx:22` | `customers` | 客户档案 | 客户 | `customer.read` |

**不存在独立的「价格中心」一级菜单**（`routeRegistry.tsx:27-35` 只有 7 个分组）。价格页全部挂在「客户」下。

### 4.2 协议价页（AgreementPrice）

**表格列**（`AgreementPricePage.tsx:48-94`）：客户（Map 反查名称，190）· 客户编码（150）· **SKU ID（110，直接渲染数字，无 SKU 名称/编码）** · 协议单价（`AmountText`，140，右对齐）· 生效时间（190，`toLocaleString('zh-CN',{hour12:false})`）· 结束时间（190，null → 字面量「长期有效」）· 操作（编辑/删除，`Permission customerManage`）。

**查询条件**：只有 `keyword` 一个（`Input` + `allowClear`，placeholder「客户名称 / 编码 / SKU」）。
**缺陷**：后端 `AgreementPriceController` 明确支持 `customerId` / `skuId`，前端从不传（`:126-130`）。

**表单**（`AgreementPriceDrawer.tsx:97-121`，`DrawerForm` width 620）：

| 字段 | 控件 | 校验 |
|---|---|---|
| 客户 | `Select showSearch optionFilterProp="label"` | `customerId === null` → 「请选择客户和 SKU」；**停用客户 disabled 不可选** |
| SKU | `Select showSearch` | 同上；**无上下架 disabled**（缺陷） |
| 协议单价 | `Input prefix="¥" inputMode="decimal"` | `/^\d+(\.\d{1,4})?$/` → 「协议价应为非负数字，最多四位小数」 |
| 生效时间 | `DatePicker showTime`（**不是 RangePicker**） | 空 → 「请选择生效时间」 |
| 结束时间 | `DatePicker showTime allowClear` | `to <= from` → 「结束时间必须晚于生效时间」 |

提示文案（`:128`）：「有效期按 [生效时间, 结束时间) 计算；结束时间留空表示长期有效。」
**无状态开关**（协议价没有 `status` 字段）。
时间序列化：`date?.toISOString()` → UTC `Z` 字符串。
错误映射（`:82`）：`409 + code===40933` → 「该客户与 SKU 的协议价有效期发生重叠…」；其他 409 → 「协议价已被其他人修改…」。**40933 用法正确。**

### 4.3 客户类型价页 + 批量调价

**表格列**（`CustomerTypePricePage.tsx:35-46`）：客户类型（Map）· 类型编码 · SKU（**有 Map 反查名称**，与协议价页不一致）· 客户类型价 · 生效时间 · 结束时间（null → 「长期有效」）· 操作。
副标题（`:49`）：「客户类型价按有效期维护，零价也是有效价格」——**全项目唯一一处显式的零价语义文案**。

**查询条件**：关键词 + 客户类型 + SKU（比协议价页多两个）。

**表单**：与协议价抽屉近乎逐行重复（`width=620`、`DrawerForm`、`destroyOnClose`），字段为 客户类型 / SKU / 客户类型单价 / 生效时间 / 结束时间。
**错误码判断反转（确定 BUG）**：`CustomerTypePriceDrawer.tsx:30` 判 `e.code === 40934` 为「有效期重叠」，但 `40934 = VERSION_CONFLICT`、`40935 = CUSTOMER_TYPE_PRICE_OVERLAP`。**两者文案互换**。

**批量调价（`CustomerTypePriceBatchPage.tsx`）**：

| 事实 | 内容 |
|---|---|
| 入口 | 客户类型价页工具栏按钮，`window.location.assign('/customer-type-prices/batch')` → **整页跳转、脱离 SPA 路由、硬编码绝对路径** |
| 形态 | 独立路由页（非弹窗） |
| 批次号 | **用户手输**（`Input`，placeholder「例如 PRICE-20260912-01」），无格式校验、无服务端生成 |
| 行编辑 | 原生 `Table`，`rowKey="rowNumber"`，`pagination={false}`；列：行号(70) / 客户类型(Select,190) / SKU(Select,280) / 单价(Input,150) / 生效时间(DatePicker,220) / 结束时间(DatePicker,220) / 删除(70) |
| 行上限 | `新增行 disabled={rows.length >= 500}`（与后端 `@Size(max=500)` 一致） |
| 提交前校验 | 单个 `rows.find(...)`，**只报第一个非法行**，提示统一为「请完整填写第 N 行的客户类型、SKU、单价和生效时间」，**不区分漏填/格式错/时间非法** |
| 成功反馈 | `Alert type="success"`「批量创建成功：{rowCount} 条」（但复用了 `styles.error` 类名） |
| 失败反馈 | `Alert type="error"` 只显示 `e.message` |
| 逐行结果 | **完全没有**。响应类型 `CustomerTypePriceBatchResult {batchKey, rowCount, ids}` 与后端一致，**结构上无法承载 per-row 错误** |
| Excel 导入 | **不存在**（全仓无 `xlsx`/`excel`/`Upload` 组件；`UploadOutlined` 只是按钮图标） |
| 幂等 | **不存在**（请求体只有 `{batchKey, rows}`） |

### 4.4 价格历史页（PriceHistory）

**筛选条件（8 个）**：来源（AGREEMENT/CUSTOMER_TYPE）· 客户 · 客户类型 · SKU · 操作（CREATE/UPDATE/DELETE）· **生效区间 `RangePicker`** · **操作区间 `RangePicker`**（全仓价格页唯一用 RangePicker 的地方）。
**展示列**：来源（Tag：CUSTOMER_TYPE 蓝 / AGREEMENT 绿）· 客户或类型（按 source 分支）· SKU（Map 反查）· 操作 · 操作人 · 操作时间 · 当前价格（null → 文本「无价格」）· 状态（`currentDeleted` → Tag 红「已删除」/ 绿「有效」）· 变更详情。
**变更详情 = `window.alert(JSON.stringify(beforeData) + '\n' + JSON.stringify(afterData))`**（`:45`）——阻塞式弹窗、无结构化 diff。
**`currentEffectiveFrom` / `currentEffectiveTo` 被接口返回但页面上完全不展示**（缺陷）。

### 4.5 SKU 可见性（内嵌在客户抽屉）

**不是 Transfer 穿梭框，不是 Tree，也不是独立页面。**
`CustomerDrawer.tsx:104-118`：
```tsx
<Radio.Group value={value.visibilityPolicy} onChange={...}>
  <Radio value="ALL_ENABLED">全部在售 SKU</Radio>
  <Radio value="ALLOWLIST">仅白名单 SKU</Radio>
</Radio.Group>
{value.visibilityPolicy === 'ALLOWLIST' ?
  <Form.Item label="可见 SKU" required>
    <Select aria-label="可见 SKU" mode="multiple" showSearch optionFilterProp="label"
            options={skuOptions} value={value.visibilitySkuIds} .../>
  </Form.Item> : null}
```
- 只有 2 种策略，**无黑名单**。
- SKU 选项带 `disabled: p.status !== 'ON_SHELF' || sku.status !== 'ON_SHELF'`（**全仓唯一一处给 SKU 选项加下架禁用的地方**）。
- 校验：`ALLOWLIST && visibilitySkuIds.length === 0` → 「白名单策略至少选择一个 SKU」。
- 归一化（`customerFormModel.ts:46-62`）：`ALLOWLIST` 时以 `visibilitySkuIds` 为唯一真相重算 `visibilities`，已存在行**回填 `id`+`version`（乐观锁）**；`ALL_ENABLED` 时 `visibilities: []`（清空）。
- **切换策略不清空已选**：切回 `ALL_ENABLED` 时多选组件卸载但 `visibilitySkuIds` 保留在 state，靠提交时丢弃；再切回 `ALLOWLIST` 会「意外」看到旧选择。
- **无独立页面、无反向视图（某 SKU 被哪些客户可见）、无批量授权。**

### 4.6 SKU 选项加载方式（对 W3 的 SKU option API 设计有直接约束）

**唯一加载函数**（`src/api/customers.ts:102-104`）：
```ts
export async function fetchSkuCatalog(params: ProductPageParams) {
    return (await apiClient.get<PageData<ProductSummary>>('/products', {params})).data;
}
```
→ 打的是**商品分页接口 `/products`**，SKU 嵌套在 `ProductSummary.skus` 里。

**6 个调用点全部硬编码 `{page:1, pageSize:100}`**：

| 调用点 | 位置 |
|---|---|
| 协议价抽屉 | `AgreementPriceDrawer.tsx:35` |
| 客户类型价抽屉 | `CustomerTypePriceDrawer.tsx:20` |
| 批量调价页 | `CustomerTypePriceBatchPage.tsx:18` |
| 客户类型价列表页筛选 | `CustomerTypePricePage.tsx:21` |
| 价格历史页筛选 | `PriceHistoryPage.tsx:31` |
| 客户抽屉可见性多选 | `CustomerDrawer.tsx:26` |

后端强制 `@Max(100)`（`ProductController.java:52-53`）。
→ **实际只覆盖「第 1 页的 100 个 SPU 下的全部 SKU」。** 商品数 > 100 时，超出部分的 SKU **完全无法在价格中心被选中**。
→ 选择器内**无分页 UI、无远程搜索、无无限滚动**，`showSearch optionFilterProp="label"` 只是 antd 客户端过滤已加载项。
→ 查不到的 ID 退化为 `客户 #id` / `SKU #id` / `类型 #id`。

**存在但价格中心未使用的接口**：`GET /customers/{id}/skus` → `SkuSaleOption[]`（数组非分页）：
```ts
export type SkuSaleOption = { skuId: number; skuCode: string; productName: string;
                              saleUnit: string; productType: ProductType;
                              marketPrice: string; status: ShelfStatus };
```
唯一使用点是 `OrderEditorPage.tsx:46-50`（按客户拉可下单 SKU）。**这是全仓最接近 W3 所需形状的既有 DTO，但它是「按客户维度」，不是全局 SKU 目录。**

**结论：legacy 不存在独立的 SKU option 端点。** 这正是用户给出的 W3 P0 前置要解决的问题。

### 4.7 UNPRICED / 0 元在前端的呈现

- **UNPRICED：legacy 前端不存在。** 全仓 `src/` grep `UNPRICED` / `priceStatus` 命中 0；唯一出现处是 e2e 文件内的局部类型声明（`e2e/sprint5-mall-pricing.spec.ts:9`），用于断言 `/mall/catalog/products` 返回，**不是后台 UI 组件**。
- **缺价的 4 套措辞**：`无价格`（`PriceHistoryPage.tsx:43`）· `询价`（`MallHomePage.tsx:42`）· `¥ --`（`OrderDetailPage.tsx:161`）· `--`（`OrderEditorPage.tsx:219`）。
- **0 元**：`AmountText.tsx:9-12` 对 `'0.0000'` 输出 `¥ 0.0000`，与普通价完全同构，无颜色/图标区分。
- **`AmountText` 无空值保护**：`value.split('.')` 遇 `null` 抛 TypeError；协议价/客户类型价列表直接使用 → 后端一旦返回 null 即整页崩溃。
- **无专用缺价 Tag/图标/置灰**（`StatusTag.tsx:7-23` 标签集里没有缺价项）。

### 4.8 价格来源展示

| 位置 | 形式 |
|---|---|
| 订单编辑页 价格来源列 | `StatusTag`（Tag） |
| 订单详情页 来源列 | `StatusTag` |
| 商城预览商品卡 | `Tag color="green"`：`AGREEMENT` → 「协议价」，其他 → 「专属价」（**只有两种文案**） |
| 价格历史 来源列 | Tag（CUSTOMER_TYPE 蓝 / AGREEMENT 绿） |

`StatusTag` 标签：`AGREEMENT`=协议价 · `CUSTOMER_TYPE`=客户类型价 · `MARKET`=市场价 · `OVERRIDE`=人工改价。
**只有 `OVERRIDE` 有颜色（warning 橙）**；前三者无显式颜色，**视觉上完全无法区分**。
**前端不计算优先级**，只展示后端返回值。`priceSourceId` 类型已定义但**全仓无任何渲染**。

### 4.9 金额格式化

唯一工具是 `AmountText.tsx`：
```ts
export function formatAmount(value: string) {
    const [integer = '0', decimal] = value.split('.');
    return `¥ ${groupInteger(integer)}${decimal === undefined ? '' : `.${decimal}`}`;
}
```
- **不固定小数位、不补零、不舍入**（`'6.5000'` → `¥ 6.5000`；`'6.5'` → `¥ 6.5`）
- 千分位分组，前缀固定 `¥ `（含空格）
- CSS：`font-variant-numeric: tabular-nums; white-space: nowrap;`（无颜色、无字号）
- **无空值保护**

另有 5 处「裸渲染」不走 `AmountText`：`OrderDetailPage.tsx:161` · `OrderEditorPage.tsx:198,219` · `ReturnDetailPage.tsx:138`（完全未格式化）· `MallHomePage.tsx:42`（`` `¥${value}` `` 无空格）。

精度常量与正则：`src/utils/decimal.ts` 定义 `DECIMAL_SCALE = 4` / `DECIMAL_PATTERN = /^\d+(\.\d{1,4})?$/`，但**价格页不使用它**，各自内联重复正则（共 5 处）。

### 4.10 权限

价格相关**只有 2 个业务权限码**：`customer.read`（`authorities.ts:12`）、`customer.manage`（`:13`）。
**不存在** `price.read` / `price.manage` / `price.batch` / `price.history` 之类。
4 个价格路由全部用 `customer.read`；按钮用 `customer.manage`。
**价格历史页全文无 `<Permission>` 包裹**；批量页的「新增行/删除行」也无权限包裹。

### 4.11 legacy 前端缺陷清单（W3 不复制）

| # | 缺陷 | 位置 |
|---|---|---|
| A1 | 客户类型价错误码判断反转（40934 ↔ 40935 文案互换） | `CustomerTypePriceDrawer.tsx:30` |
| A2 | 协议价列表无法按客户/SKU 过滤（后端支持，前端不传） | `AgreementPricePage.tsx:98-105,126-130` |
| A3 | 协议价表格只显示 SKU 数字 ID | `AgreementPricePage.tsx:59` |
| A4 | 批量调价只报第一处错误且不指明字段 | `CustomerTypePriceBatchPage.tsx:31-33` |
| A5 | 批量调价无逐行成功/失败反馈（响应结构不支持） | `types/sales.ts:121-125` |
| A6 | 批量调价无幂等键 | `types/sales.ts:109-119` |
| A7 | 批量调价批次号由用户手输 | `CustomerTypePriceBatchPage.tsx:54` |
| A8 | 批量调价入口整页跳转（`window.location.assign`） | `CustomerTypePricePage.tsx:56` |
| A9 | SKU 选项不区分上下架（协议价/类型价/批量三处缺 `disabled`） | `AgreementPriceDrawer.tsx:50-53` 等 |
| A10 | 价格历史 before/after 用 `window.alert + JSON.stringify` | `PriceHistoryPage.tsx:45` |
| A11 | 价格历史丢弃 `currentEffectiveFrom/To` | `PriceHistoryPage.tsx`（无引用） |
| A12 | `AmountText` 无空值保护 | `AmountText.tsx:10` |
| A13 | `OVERRIDE` 被混进 `PriceSource` 前端类型 | `types/sales.ts:6`、`StatusTag.tsx:22` |
| A14 | SKU 选项硬编码 `pageSize:100`（6 处）→ 只覆盖前 100 个 SPU | 见 §4.6 |
| A15 | 客户选项同样硬编码 100（4 处） | `AgreementPricePage.tsx:26` 等 |
| A16 | 选择器无远程搜索/无虚拟滚动 | 6 处 `Select` |
| A17 | 价格页骨架逐行重复（两页 + 两抽屉） | `AgreementPricePage` vs `CustomerTypePricePage` |
| A18 | `fetchSkuCatalog` 命名与实现不符（打 `/products`） | `api/customers.ts:102-104` |
| A19 | 缺价 4 套措辞 | 见 §4.7 |
| A20 | 金额格式化不统一（5 处裸渲染） | 见 §4.9 |
| A21 | `loadError` 与 `ProTable success:true` 混用 | `AgreementPricePage.tsx:135` 等 |
| A22 | 批量页成功 Alert 复用错误样式 class | `CustomerTypePriceBatchPage.tsx:53` |
| A23 | 批量页 `UploadOutlined` 图标误导（不支持导入） | `CustomerTypePricePage.tsx:1,56` |
| A24 | 协议价页无「零价有效」提示（类型价页有） | `AgreementPricePage.tsx:97` vs `CustomerTypePricePage.tsx:49` |
| A25 | 抽屉时间选择器未用 RangePicker（同产品内不统一） | `AgreementPriceDrawer.tsx:113-120` |
| A26 | 策略切换不清空已选 SKU | `CustomerDrawer.tsx:104-118` |
| A27 | 可见性无独立页/无审计视图/无批量授权 | 全文 |
| A28 | 价格历史无权限包裹、无导出 | `PriceHistoryPage.tsx` |
| A29 | 批量调价行内错误无高亮、不滚定位 | `CustomerTypePriceBatchPage.tsx:52` |
| A30 | 前端正则不限整数位数（后端 `@Digits(integer=14)`） | 5 处 |
| A31 | 时间用 `dayjs().toISOString()` 做本地→UTC 换算（规格明令「客户端不得按本地时区重算」） | `AgreementPriceDrawer.tsx:116,120` 等 |

### 4.12 legacy 前端**不存在**的能力（重要结论，已逐项 grep 验证）

| # | W3 能力 | 结论 |
|---|---|---|
| 1 | `UNPRICED` / `priceStatus` 字段与 UI | **不存在** |
| 2 | 缺价专用标签/图标/颜色 | **不存在** |
| 3 | 独立「SKU 可见性」页面 | **不存在**（仅客户抽屉内联） |
| 4 | 黑名单（BLOCKLIST）可见性模式 | **不存在** |
| 5 | 可见性穿梭框/树形选择 | **不存在**（仅 `Select mode="multiple"`） |
| 6 | 「价格中心」一级菜单/分组 | **不存在** |
| 7 | 价格组 / 阶梯价 / 客户折扣 | **不存在** |
| 8 | Excel/CSV 批量导入、模板下载 | **不存在** |
| 9 | 批量调价逐行成功/失败明细 | **不存在** |
| 10 | 批量删除 / 批量启停价格 | **不存在** |
| 11 | 取价试算（查某客户某 SKU 的成交价） | **不存在** |
| 12 | 活动价/促销价参与价格中心 | **不存在** |
| 13 | 订单锁价 / 人工改价历史查询 | **不存在**（`PriceHistorySource` 只有两个值） |
| 14 | 价格快照时间展示 | **不存在** |
| 15 | `priceSourceId` 展示 | **不存在**（类型已定义） |
| 16 | 起订量/增量步长/数量精度配置 UI | **不存在** |
| 17 | 价格历史结构化 diff | **不存在** |
| 18 | 价格专属权限码 | **不存在** |
| 19 | 价格数据导出 | **不存在** |
| 20 | 独立 SKU option 远程搜索端点 | **不存在** |

---

## 5. 后端 legacy 缺陷与风险（V2 必须修正）

| # | 缺陷 | 位置 | 影响 |
|---|---|---|---|
| **D1** | **UNPRICED 实际不可达（死分支）** | `CustomerPriceResolver.java:49-50` + `V1:113,125`（`market_price NOT NULL`）+ `requireOrderable` 守卫 | `byId.get(id)` 永不为 null、`getMarketPrice()` 永不为 null → `PriceStatus.UNPRICED` 在真实路径**永不产生**。规格 §3/§5 描述的缺价契约缺少可达入口 |
| **D2** | **批量调价无幂等** | `V34:23-36`（`batch_key` 无唯一约束）、`CustomerTypePriceBatchWriter` | 重复/重试提交同 `batchKey` 会重复建价并重复审计 |
| **D3** | **批量非逐行容错，且非业务异常丢行号** | `CustomerTypePriceBatchWriter.java:24-28` | 任一行 `BusinessException` → 整批回滚；NPE / `DataIntegrityViolationException` 不包装、无行号 |
| **D4** | **批量 N+1** | `CustomerTypePriceService.java:46-54,80-93` | 每行 6 次 DB 往返（查类型/查 SKU/`FOR UPDATE`/重叠计数/insert/insert log），500 行 ≈ 3000 次查询，且**逐条 INSERT** |
| **D5** | **审计操作人不完整** | `AgreementPriceService.java:131`、`CustomerPriceBatchAuditService.java:43` | 协议价与批次审计硬编码 `"SYSTEM"`；只有类型价用真实用户（`CurrentOperator.username()`）→ 不可追责 |
| **D6** | **错误码语义重载** | `CustomerErrorCodes.java:12` + `CustomerSkuVisibilityChangeSet.java:27` | `40931 SKU_NOT_VISIBLE` 同时表示「不可见/未上架」与「SKU 可见性重复」 |
| **D7** | **无 DB 级重叠排除约束** | `V3`、`V34` 无 `EXCLUDE USING gist` | 重叠只靠应用层 `FOR UPDATE`；任何旁路写入（数据修复脚本、其它模块）可破坏不变量，Resolver 只能静默取最新 |
| **D8** | **决胜规则隐式、无告警** | `CustomerPriceResolver:38-41` + `ORDER BY effective_from DESC, id DESC` | 出现重叠时静默取最新，无监控 |
| **D9** | **无黑名单能力** | `V3:33`（仅两个策略值） | 无法表达「排除某些 SKU」 |
| **D10** | **`resolve` 整批失败语义偏严** | `OrderableSkuQueryService.java:35-37` | 一个 SKU 不可下单即整批抛错，无逐 SKU 降级 |
| **D11** | **`setScale(4)` 无 `RoundingMode`** | `AgreementPriceService:106,117`、`CustomerTypePriceService:117,130`、`OrderableSkuQueryService:57` | 库内出现 >4 位小数时抛 `ArithmeticException` |
| **D12** | **类型价校验未检查类型启用状态** | `CustomerTypePriceService.java:82-84` 只判 `selectById != null`；而 `CustomerService.requireType:42-46` 要求 `ENABLED` | 两处规则不一致，可给停用类型定价 |
| **D13** | **历史行 `current_*` 重复携带、无「最新」语义** | `PriceHistoryMapper.xml:43-46,61-64` | 同一 price 的多条日志各自 `LEFT JOIN` 主表，重复返回同一「当前值」；硬删时 `current_*` 可能为 NULL |
| **D14** | **批次无部分成功报告** | `CustomerTypePriceBatchResponse.java:5` | 仅 `{batchKey,rowCount,ids}`，无逐行清单；失败时行级结果只在异常消息里 |
| **D15** | **价格端点零权限注解** | 3 个 price controller 全文 | 完全依赖框架 `AuthorityRules`，没有资源级权限声明 |
| **D16** | **客户可下单 SKU 列表展示市场价而非客户实价** | `OrderableSkuQueryService.java:57` | `listOrderable` 直接返回 `sku.getMarketPrice()`，未调 Resolver → `GET /api/customers/{id}/skus` 的价格对协议价/类型价客户**不准确** |
| **D17** | **删除客户/类型不检查 pricing 引用** | 无引用检查 | 可删掉仍有协议价的客户、仍有类型价的客户类型 |
| **D18** | **`countOverlapping` 的 `#{to}` 未显式 `jdbcType`** | `CustomerAgreementPriceMapper.xml:17` | 依赖 MyBatis 对 null `OffsetDateTime` 的类型推断，某些 PG 驱动配置下会抛 JdbcType 异常（无测试覆盖） |
| **D19** | **`lockCustomer` 锁粒度粗** | `CustomerAgreementPriceMapper.xml:47-49` | 同一客户所有 SKU 调价串行；高并发批量调价成为瓶颈 |
| **D20** | **批量逐行重复加锁** | `CustomerTypePriceBatchWriter` → 每行 `lockCustomerType` | 同事务内可重入，正确但产生 N 次锁查询 |

> 未发现时区缺陷：全链路 `TIMESTAMPTZ` + `OffsetDateTime`，跨时区比较正确。
> 未发现精度丢失：统一 `NUMERIC(18,4)`，除 D11 的 `setScale` 风险外无问题。

---

## 6. 测试覆盖审计

### 6.1 已覆盖

| 类 | 用例数 | 覆盖点 |
|---|---|---|
| `CustomerPriceResolverTest` | 3 | 协议价优先、类型价次之、市场价兜底、空列表不触库 |
| `AgreementPriceServiceTest` | 3 | 锁序、软删原子谓词、UPDATE 前后快照 |
| `AgreementPriceValidatorTest` | 2 | 负数拒绝、区间反转拒绝 |
| `CustomerTypePriceServiceTest` | 5 | 重叠先校验后写、乐观锁更新、软删、CREATE/DELETE 日志 |
| `CustomerTypePriceBatchServiceTest` | 1 | 失败后写 FAILED 审计 |
| `PriceHistoryQueryIT` | 1（`@SpringBootTest` 真库） | 双来源合并、6 个筛选维度、JSONB 映射、0 元 |
| `CustomerServiceTest` | 3（可见性相关） | 停用类型拒绝、ALL_ENABLED 提交明细拒绝、客户软删原子谓词 |
| `CustomerSkuVisibilityChangeSetTest` | 4 | 保留身份、外来记录、换 SKU、重复 SKU |
| `AgreementPriceControllerTest` | 2 | 分页信封、旧路径兼容 |
| `CustomerTypePriceControllerTest` | 1 | 分页 + customerTypeId + keyword |
| `CustomerControllerTest` | 3 | 分页信封、40934 映射、可下单 SKU |
| `MallCartServiceTest` | 5 | UNPRICED 加购/改量拒绝、0 元可加购、购物车保留缺价行、正常行 |
| `MallCheckoutServiceTest` | 3 | 缺价整次拒绝、version 使用、0 元成交 |
| `MallCheckoutFingerprintTest` | 1 | 指纹覆盖客户/地址/数量/价格来源 |
| `SalesOrderApplicationServiceTest` | 2 | 无删除行不空软删、审计快照 |
| `OrderEnumsTest` | 1 | Order `PriceSource` 四值（含 OVERRIDE） |
| `FlywayMigrationIT` | 1 | 价格表/索引/numeric_scale=4/JSONB 断言 |

### 6.2 盲区（明确列出）

| # | 未覆盖规则 | 依据 |
|---|---|---|
| B1 | Resolver 产出 UNPRICED 的分支（`sku == null`）——且因 D1 不可达，测试用 mock 伪造 | 仅 mock `selectEffective` / `requireOrderable` |
| B2 | **协议价与类型价同时命中时 AGREEMENT 胜出**：`choosesCustomerTypePriceBeforeFallingBackToMarketPrice` 里协议价返回空列表，未构造「两者都有」 | `CustomerPriceResolverTest.java:53-62` |
| B3 | `resolve` 抛 `SKU_NOT_VISIBLE` 的整批失败路径 | 无测试 |
| B4 | **`selectEffective` SQL 本身**（半开区间、NULL 结束、决胜排序） | 无集成测试 |
| B5 | **`countOverlapping` SQL 本身**（`COALESCE(to,'infinity')`、NULL 结束、`id != #{id}`、null 参数绑定） | 无集成测试 |
| B6 | 重叠拒绝的端到端（协议价侧） | `AgreementPriceServiceTest` 只验证方法被调用 |
| B7 | `CustomerTypePriceBatchWriter`（逐行包装、SUCCESS 审计、事务回滚、`rowNumber` 正确性） | 无单测 |
| B8 | `CustomerPriceBatchAuditService`（FAILED 快照、`REQUIRES_NEW`、非业务异常 → 50000、rowNumber/skuId 写入） | 无测试 |
| B9 | **`OrderableSkuQueryService`**（ALLOWLIST 过滤、上架校验、`SKU_NOT_VISIBLE`、空可见性返回空） | 无单测 |
| B10 | 人工锁价/OVERRIDE 的锁定逻辑（`SalesOrderApplicationService.submit` 的 override 分支） | 无测试 |
| B11 | `CustomerTypePriceValidator` 独立测试 | 无文件 |
| B12 | `PriceHistoryQueryService.normalizeSource` 非法来源归一为 null | 无测试 |
| B13 | **并发**（`FOR UPDATE` 串行化、`@Version` 冲突、唯一约束冲突） | 无并发/集成测试 |
| B14 | **0 元在 customer 域**（协议价/类型价允许 0 元的写入与解析） | 0 元断言只在 mall 域 |
| B15 | `SalesOrderApplicationService.submit` 用 Resolver 结果覆盖 draft/locked 价格 | 无测试 |

---

## 7. 跨域依赖与 W3 排除项

### 7.1 谁消费价格（W3 不实现，但必须知道接口形状）

| 消费方 | 调用点 | 用法 |
|---|---|---|
| 销售订单 | `SalesOrderApplicationService`（`:63,127,237`） | `priceMap` → Resolver；提交时覆盖 `draft*` 与 `locked*` |
| 商城目录 | `MallCatalogService.java:122-130` | 返回 `unitPrice` + `priceStatus` |
| 商城购物车 | `MallCartService.java:81-85,166-189` | 加购/改量/列表 |
| 商城结算 | `MallCheckoutService.java:119-144` | 预览/提交 |
| 后台客户 SKU 列表 | `CustomerController.java:43-46` | `GET /api/customers/{id}/skus` |

### 7.2 W3 明确不进入（用户锁定）

```text
Sales Order · Purchase · Receiving · Inventory · Warehouse · Finance · Mall · MiniApp
```

同时按 legacy 事实源，以下也**不在 W3**：

| 项 | 原因 |
|---|---|
| 活动价 / 促销价 | 规格 §2：「活动价本 Sprint 不参与成交计算」；`PriceTypeEnum` 里的 `PROMOTION` 在 C 里也只是空位 |
| 客户折扣率 | C 有表无消费点（§6.3）；legacy 完全无此表 |
| 价格组 | legacy 无 `price_group` 表；规格 §6 明确「价格组仅作为后台批量维护和查询维度，不改变成交优先级」，§10 列为未冻结 |
| 客户分级价（`customer_level_id`） | **只有 C 有**（`t_product_price.customer_level_id`），legacy 用「客户类型价」代替。**C 的建模不迁移** |
| 人工锁价 / 订单锁价 | P23：属 Order 域 |
| 起订量 / 增量步长 / 数量精度 | 规格 §3 提到，但 legacy 无对应表/字段 |
| 黑名单可见性 | legacy 只有两个策略值 |

### 7.3 W3 对 SmartAdmin 的依赖（直接复用，不重建）

- 认证 / 会话：Sa-Token Bearer token
- 权限：`@SaCheckPermission` + `t_menu` / `t_role_menu` 种子
- 操作日志：`@OperateLog`
- 员工上下文：`SmartRequestUtil.getRequestUser()`（SCM 封装为 `ScmOperator.current()`）
- 分页：`PageParam` / `PageResult` / `SmartPageUtil`
- 统一响应：`ResponseDTO`；统一异常：`GlobalExceptionHandler`
- 校验：Spring Validation
- 持久化：MyBatis-Plus + `@Version` + `@TableLogic` + `PaginationInnerInterceptor`
- 序列化：`JsonConfig`（BigDecimal → 字符串）+ SCM 的 `ScmFixedScale4Serializer` / `ScmStrictDecimalStringDeserializer` / `ScmDecimalStrings`

---

## 8. W3 范围内 Legacy 无事实源的部分（**本轮审计最关键的结论**）

### 8.1 缺口清单

| # | W3 能力 | Legacy 事实源状态 | 后果 |
|---|---|---|---|
| **G1** | **UNPRICED 的真实可达路径** | 契约存在（`PriceStatus.UNPRICED`）但**不可达**（D1） | W3 必须**显式定义** UNPRICED 的产生条件，否则该能力是空壳 |
| **G2** | **SKU option 查询端点** | **完全不存在**（§4.6） | W3 P0 前置：新增 `/scm/product/sku/option-list` |
| **G3** | **取价试算 / 价格预览页面** | **不存在**（Resolver 无对外端点） | W3 若要验证 Resolver 与优先级，需要新增只读端点（属新增能力） |
| **G4** | **批量调价的逐行失败报告** | 响应结构**无法承载**（D14） | W3 必须重新定义批量响应结构 |
| **G5** | **批量调价幂等** | **不存在**（D2） | W3 必须新增幂等保护或明确接受重复 |
| **G6** | **可见性独立页面 / 反向视图** | **不存在**（仅客户抽屉内联） | W3 需决定是内联还是新增页 |
| **G7** | **可见性审计** | **不存在**（无 visibility log 表） | W3 需决定是否新增审计 |
| **G8** | **价格专属权限码** | **不存在**（只有 `customer.read` / `customer.manage`） | W3 必须全新定义 `scm:pricing:*` |
| **G9** | **价格中心菜单分组** | **不存在** | W3 必须新定义菜单树 |
| **G10** | **协议价 / 类型价的状态字段** | **不存在**（无 `status` 列） | 价格「启用/停用」只能靠删除或生效区间表达 |
| **G11** | **价格精度与数量的共享格式化工具** | **不存在**（`AmountText` 无空值保护） | W3 前端需新建格式化工具 |
| **G12** | **删除客户/类型时的 pricing 引用检查** | **不存在**（D17） | W3 必须补 |

### 8.2 三个建模冲突的辨析（W3 必须选边）

#### 冲突 1：客户类型价 vs 客户分级价

| 维度 | Legacy A | C |
|---|---|---|
| 建模 | `customer_type_price(customer_type_id, sku_id)`，客户类型是**可维护字典表** | `t_product_price.price_type=2` + `customer_level_id`（客户分级 ID） |
| 取值 | 客户 → `customer.customer_type_id` → 类型价 | 客户 → `customer_level_id` → 分级价 |
| 结论 | **W3 采用 legacy：客户类型价。** C 的 `customer_level_id` / `price_type` 4 值模型**不迁移**。W2 已把客户类型做成可维护字典表（`customer_type`），链路已通 | |

#### 冲突 2：可见性的建模（策略字段位置）

| 维度 | Legacy A | C |
|---|---|---|
| 策略字段 | `customer.visibility_policy`（`ALL_ENABLED` / `ALLOWLIST`） | `t_customer_goods_visible.visible_type`（1 显示 / 2 屏蔽），**缺省可见** |
| 明细表 | `customer_sku_visibility(customer_id, sku_id)` 只存白名单 | 一行一条 (客户, 商品, 显示/屏蔽) |
| 粒度 | **SKU 级** | **商品（SPU）级** |
| 表达能力 | 「全可见 / 仅白名单」 | 「逐条显示 / 屏蔽」，缺省可见 |
| 结论 | **W3 采用 legacy：SKU 级 + 策略字段在 customer 上。** 与 W2 的 `supplier_sku` SKU 级口径一致；C 的 SPU 级 + 双向标记**不迁移**（C 的建模无法表达「仅显示这些」） | |

#### 冲突 3：价格表是一张还是两张

| 维度 | Legacy A | C |
|---|---|---|
| 建模 | **两张表**：`customer_agreement_price` + `customer_type_price`（列名几乎同构） | **一张表**：`t_product_price` + `price_type` 区分 + 可空的 `customer_id` / `customer_level_id` / `sku_id` |
| 优点 | 各自索引精准、约束简单、语义清晰；重叠检测 SQL 独立 | 表少，加新价格类型不用建表 |
| 缺点 | 加新价格类型要建表 | **可空列组合爆炸**（`sku_id` 可空表示按商品定价）、无唯一约束、取价 4 条 SQL 全靠 `LIMIT 1` |
| 结论 | **W3 采用 legacy：两张表。** C 的单表多态模型**不迁移**（C 的 D1/D6 缺陷正源于此） | |

### 8.3 明确的「不要做」

1. **不要**把 C 的 `t_product_price`（含 `price_type` 4 值、`customer_level_id`、可空 `sku_id`）搬过来。
2. **不要**引入客户折扣率（`t_customer_discount`）——C 有表无消费点，规格也未冻结。
3. **不要**引入价格组 / 阶梯价。
4. **不要**在 Pricing 域引入人工锁价 / 订单锁价。
5. **不要**把可见性做成 SPU 级或双向标记。
6. **不要**复用 legacy 的 `customer` 包结构；V2 拆 `module/scm/pricing`（结构升级，见目标设计）。
7. **不要**复用 legacy 的 `"SYSTEM"` 硬编码操作人。
8. **不要**让前端从 `/scm/product/query?pageSize=N` 拍平 SKU（这是 A14 的根因）。
9. **不要**在 `PriceHistory` 里加订单锁价 / OVERRIDE 来源。
10. **不要**改 `V6/V7/V8/V9`（哈希已冻结，见 `w1-applied-migrations.sha256` / `w2-applied-migrations.sha256`）。

---

## 9. C 项目价格资产审计（`project-reference-examples/xsy-scm/`）

### 9.1 三条资产线

| 线 | C 表 | C 后端 | C 前端 | 消费点 |
|---|---|---|---|---|
| 商品价格 | `t_product_price`（**只在 `docs/database/01-商品与价格.sql` 里，未合入 deploy SQL**） | `ProductPriceController/Service/Dao/Manager` + `PriceTypeEnum` / `PriceStatusEnum` / `ResolvedPriceBO` | `product-price-list.vue` + `product-price-api.ts` | `SaleOrderItemService.fillResolvedPrice`（唯一） |
| 客户折扣率 | `t_customer_discount`（**已合入 deploy SQL**） | `CustomerDiscountController/Service/Dao/Manager` + `DiscountScopeEnum` / `DiscountStatusEnum` | **完全缺失** | **无** |
| 客户商品可见 | `t_customer_goods_visible`（只在 docs SQL） | `CustomerGoodsVisibleController/Service/Dao/Manager` + `VisibleTypeEnum` | `customer-goods-visible-list.vue` + `customer-goods-visible-api.ts` | **无** |

### 9.2 C 的价格解析（对比用）

`ProductPriceService.resolvePrice:103-122`：`协议价(4) → [促销价占位空] → 分级价(2) → 时价(3) → 基础价(1)`，命中即 return，不叠加。
**关键差异：只有「时价」带时间窗**，协议价/分级价/基础价取价 SQL **没有 `effective_time/expire_time` 条件** → 过期协议价仍会命中（C 缺陷 D6）。
缺价时**硬抛 `BusinessException("商品未配置有效价格（基础价缺失），无法下单")`**，没有 UNPRICED 状态（C 缺陷 D4）。

### 9.3 C 的已知缺陷（W3 不复制）

| # | 缺陷 | 证据 |
|---|---|---|
| C1 | `t_product_price` 无唯一约束，同一 (product, sku, price_type, customer) 可插多条，取价 `LIMIT 1` 随机 | `01-商品与价格.sql:103-106`；`ProductPriceMapper.xml:64,78,93,106` 无决胜排序 |
| C2 | 无审计（`createUserId/createUserName` 从未赋值） | 三个 Service 只 `SmartBeanUtil.copy` |
| C3 | 无并发控制（无 `version` 列、无 `@Version`） | 三个 Entity 无 version 字段 |
| C4 | 无 UNPRICED，缺价硬抛异常 | `ProductPriceService.java:121` |
| C5 | 无 0 元区分 | `price NOT NULL` 无 CHECK；前端 `:min="0"` |
| C6 | 协议价/分级价/基础价忽略生效时间 | `ProductPriceMapper.xml:54-65,68-79,97-107` |
| C7 | 可见性逻辑删除 + 唯一键冲突（`uk_customer_product` 不含 `deleted_flag`） | `02-客户.sql:80`；`CustomerGoodsVisibleService:74-77` |
| C8 | 可见性白/黑名单语义不完整（一行一标记 + 缺省可见，无法表达「仅显示这些」） | `VisibleTypeEnum.java:19-24` |
| C9 | 菜单 component 路径全部错位（`product/price/...` vs 实际 `views/business/product/...`；`discount-list.vue` 不存在） | `business_module_menu_seed.sql:25,96`；`xsy_scm_v3.30.0_supply_chain.sql:1734` |
| C10 | 折扣权限码 `customerDiscount:*` 无任何 seed → 必然 403 | `CustomerDiscountController.java:37,44,51,58,65` |
| C11 | 折扣率无消费点（有表有 CRUD，订单/取价链路完全不读） | `ProductPriceService.resolvePrice` 不读折扣；需求文档 `14-...md:632,656` 仍列为未决 |
| C12 | 折扣率无重叠校验、维度归一化不彻底 | `CustomerDiscountService.validateForm:108-120`、`buildEntity:125-136` |
| C13 | 价格查询强制 `productId` `@NotNull`，直进页面空白 | `ProductPriceQueryForm.java:16-18`；`product-price-list.vue:218-223` |
| C14 | 前端表格显示裸 ID（产品/规格/客户/客户分级） | `product-price-list.vue:158-162` |
| C15 | 前端表单无条件校验（协议价不校验必选客户等） | `product-price-list.vue:240-244` 仅 3 条必填 |
| C16 | 前端 API 无 TS 类型（全隐式 any） | 两个 api 文件各 12 行 |
| C17 | 客户分级用裸 `a-input-number` 手输 ID | `product-price-list.vue:100-102` |
| C18 | 可见性页用裸 `a-input-number` 手输客户/商品 ID | `customer-goods-visible-list.vue:8,11,89,92` |
| C19 | 折扣路径与权限码风格漂移（`/customer/discount/*` vs `customerDiscount:*`） | `CustomerDiscountController.java:36-67` |
| C20 | `ProductPriceManager` 职责名不副实（只有 save/update，解析在 Service） | `ProductPriceManager.java:16-31` |

### 9.4 C 的可复制资产（供目标设计引用）

| 资产 | 判定 | 原因 |
|---|---|---|
| `product-price-list.vue` 骨架（`smart-query-form` + `a-table` + `a-drawer` + `v-privilege` + `SmartEnumSelect` + `TableOperator`） | **复制后剪枝适配** | 版式可直接沿用；需改枚举名（C 是 `SmartEnum<number>`，V2 是 `SmartEnum<string>`）、去掉裸 ID 列、补条件校验、修 `onMounted` 只在有 `productId` 才查询的 bug |
| `customer-goods-visible-list.vue` 骨架 | **复制后剪枝适配** | 版式可沿用；需把裸 ID 输入换成选择器；建议剪枝为**只读反查**（写入唯一入口保留在客户抽屉） |
| `product-price-api.ts` / `customer-goods-visible-api.ts` 的 5 方法形状 | **复制后剪枝适配** | 需加 `/scm` 前缀、补 TS 类型、补 `version` |
| `PriceTypeEnum` / `PriceStatusEnum` / `VisibleTypeEnum` 语义 | **复制后剪枝适配** | 语义可参考，但值域与命名需按 V2 字符串码 + W3 范围裁剪（`PROMOTION` 不入 W3） |
| `ResolvedPriceBO` | **复制后剪枝适配** | 只有 2 字段，V2 需扩 `priceStatus` / `sourceRecordId` / `reason` |
| `*Manager` 薄壳 / `*VO` / `CustomerGoodsVisibleDao+Mapper` | **可直接复制** | 无业务耦合 |
| `SmartEnumSelect` / `smart-enums-plugin` / `router.buildRoutes` | **不可复制** | 框架级，V2 已有自己的实现 |
| C 的 DDL / `@TableName` / mapper 表列名 | **不可复制** | C 是 MySQL8 + `t_` 前缀；V2 是 PostgreSQL + 无前缀 |
| C 的菜单/权限 seed | **不可复制** | component 路径本身是错的，且 V2 菜单体系已重构 |

---

## 10. 审计结论

1. **价格优先级、生效时间、重叠规则、批量语义、审计快照语义这五组不变量在 legacy 中是完整且自洽的**，可以作为 W3 的权威事实源直接继承（P1–P27）。
2. **人工锁价明确属于 Order 域**（P23）。W3 的 `PriceSource` 只能是 3 值 `{AGREEMENT, CUSTOMER_TYPE, MARKET}`，`OVERRIDE` 不得进入 Pricing。
3. **UNPRICED 在 legacy 是死分支（D1）**，W3 必须显式定义其可达条件，否则该能力无法验收。
4. **legacy 无 SKU option 端点（G2）**，且这是 A14（超过 100 个 SPU 时 SKU 选不全）的根因。W3 P0 前置成立且必要。
5. **legacy 的价格端点零权限注解（D15）、零价格专属权限码（G8）、零价格菜单分组（G9）**，V2 必须全新定义。
6. **三个建模冲突（客户类型价 vs 客户分级价 / SKU 级 vs SPU 级可见性 / 两表 vs 单表多态）一律采用 legacy 侧**，C 的对应建模不迁移（§8.2）。
7. **批量调价需要在 V2 升级**：逐行失败报告（G4）、幂等（G5）、审计操作人（D5）、N+1（D4）四项是 legacy 无法提供的，属于 W3 的新增设计责任。
8. **C 项目三条价格资产线均为半成品**，只能提供**前端版式骨架**与**枚举语义参考**，不能提供业务实现（§9.3）。
9. **`sales_order_item` 的价格列与 CHECK 白名单已在 legacy 就绪**（§2.3.7），W3 交付 Resolver 后 W4 可直接落锁价，无需 W3 预留字段。
10. **W3 不改 `V6–V9`**；下一步 migration 编号从 **V10** 起。

---

## 附录 A：审计证据索引

| 主题 | 文件 |
|---|---|
| 价格优先级 | `xsy-scm-server/src/main/java/com/xianshuyuan/scm/customer/service/CustomerPriceResolver.java:34-52` |
| UNPRICED 契约 | `.../customer/service/ResolvedCustomerPrice.java`、`.../PriceStatus.java` |
| 半开区间 + 重叠 SQL | `.../resources/mapper/customer/CustomerAgreementPriceMapper.xml:4-19` |
| 历史 union | `.../resources/mapper/customer/PriceHistoryMapper.xml:30-98` |
| 批量事务 | `.../customer/service/CustomerTypePriceBatchWriter.java:19-33`、`CustomerTypePriceBatchService.java:14-21` |
| 批次审计 | `.../customer/service/CustomerPriceBatchAuditService.java` |
| 可见性硬门禁 | `.../customer/service/OrderableSkuQueryService.java` |
| 可见性差量 | `.../customer/service/CustomerSkuVisibilityChangeSet.java` |
| 锁价归属 | `.../order/service/SalesOrderApplicationService.java:128-141`、`.../order/entity/PriceSource.java` |
| 价格 DDL | `.../resources/db/migration/V3__create_customer_pricing_schema.sql`、`V7`、`V34`、`V35` |
| 价格规格 | `docs/superpowers/specs/2026-09-11-sprint5-price-center.md` |
| legacy 前端价格页 | `xsy-scm-web/src/pages/customer/{AgreementPricePage,CustomerTypePricePage,CustomerTypePriceBatchPage,PriceHistoryPage}.tsx` |
| C 前端价格页 | `project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/{product/product-price-list,customer/customer-goods-visible-list}.vue` |
| V2 商品 DDL | `xsy-scm-server/sa-admin/src/main/resources/db/migration/V6__scm_product.sql` |
| V2 客户 DDL | `.../V8__scm_customer_supplier.sql` |
| V2 SKU 选择器现状 | `xsy-scm-web/src/components/business/scm/sku-select/index.vue:15-16,61-85` |

## 附录 B：不变量覆盖清单（W3 验收对账用）

| 编号 | 不变量 | 来源 | 目标设计覆盖章节 |
|---|---|---|---|
| P1 | 优先级 AGREEMENT → CUSTOMER_TYPE → MARKET | `CustomerPriceResolver` | §4.3 / §4.4 |
| P2 | `sourceRecordId` 随结果返回 | 同上 | §4.2 |
| P3 | 类型价按客户当前启用类型 | 同上 | §4.3 |
| P4 | 客户启用 + SKU 可下单前置守卫 | 同上 | §4.4 |
| P5 | 半开区间 `[from, to)` | SQL | §3.4 |
| P6 | `to IS NULL` = 长期有效 | SQL | §3.4 |
| P7 | 同客户/类型 + SKU 不得重叠 | `FOR UPDATE` + `countOverlapping` | §3.4 / §4.7 |
| P8 | 重叠判定与生效判定口径一致 | SQL | §3.4 |
| P9 | `to > from` 双保险 | CHECK + Validator | §3.2 / §4.5 |
| P10 | 决胜 `effective_from DESC, id DESC` | SQL + Java | §3.4 |
| P11 | null = UNPRICED；非 null = PRICED | `ResolvedCustomerPrice` | §3.5 |
| P12 | 0.0000 是有效价 | 测试 | §3.5 |
| P13 | UNPRICED 消费方语义 | mall 测试 + 规格 | §3.5 |
| P14 | 只有 ALL_ENABLED / ALLOWLIST | `VisibilityPolicy` | §3.6 |
| P15 | ALLOWLIST 空 = 全部不可见 | `OrderableSkuQueryService` | §3.6 |
| P16 | 可见性硬门禁 | 同上 | §3.6 / §4.4 |
| P17 | 可见性整表替换差量 + 乐观锁 | `CustomerSkuVisibilityChangeSet` | §3.6 / §4.7 |
| P18 | 可见性影响范围 | 5 个消费点 | §3.6 |
| P19 | 批量整批回滚 | `BatchWriter` | §4.8 |
| P20 | 行号定位 + FAILED 审计 | `BatchRowException` + `AuditService` | §4.8 |
| P21 | 行上限 500 / 批次号 100 | Bean Validation | §4.8 |
| P22 | 批次审计字段 | `V34` | §3.3 |
| P23 | 锁价属 Order，不属 Pricing | `OrderEnumsTest` | §1.2 / §4.3 |
| P24 | 增删改必留痕 | 两个 Service | §4.9 |
| P25 | 快照语义（含 DELETE 的 version+1） | 两个 Service | §4.9 |
| P26 | 历史筛选正交 | `PriceHistoryQueryIT` | §4.9 |
| P27 | 引用检查 | 预留位 | §4.10 |

---

**审计完成。全程只读，未修改任何文件。**
