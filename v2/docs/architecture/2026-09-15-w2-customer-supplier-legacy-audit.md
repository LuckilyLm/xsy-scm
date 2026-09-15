# W2 Customer + Supplier · Legacy Audit（只读审计）

> 审计对象：`xsy-scm-server` / `xsy-scm-web` 的 Customer 与 Supplier 域（legacy，冻结只读）
> 冻结基线：HEAD `95a54233586aa3c652e55cd833bb346d51f5a952`，branch `feature/sprint5`
> 审计性质：**只读**。本文件不修改、不格式化、不移动任何 legacy 文件。
> 审计日期：2026-09-15　上游输入：`AGENTS.md`、`2026-09-14-w1-product-legacy-audit.md`、`2026-09-15-smartadmin-old-vs-v2-audit-workbuddy.md`
> 当前决策（用户锁定）：`v2/**` 唯一正式主干；Legacy 只读且为业务规则事实源；`project-reference-examples/xsy-scm/**` 只读且为 SmartAdmin SCM 业务资产来源；不采用 C 作为主干；**不覆盖 W1 Product**。

---

## 1. 审计范围与方法

对以下路径做了穷尽式读取（而非抽样）：

| 层 | 路径 |
|---|---|
| 后端主源码（customer） | `xsy-scm-server/src/main/java/com/xianshuyuan/scm/customer/**`（63 个 Java 文件） |
| 后端主源码（supplier） | `xsy-scm-server/src/main/java/com/xianshuyuan/scm/supplier/**`（23 个 Java 文件） |
| 后端 Mapper XML | `resources/mapper/customer/{CustomerMapper,CustomerSkuVisibilityMapper}.xml`、`resources/mapper/supplier/{SupplierMapper,SupplierSkuMapper}.xml` |
| 后端 DDL / Seed | `db/migration/V1__create_sprint1_schema.sql`（supplier / warehouse）、`V3__create_customer_pricing_schema.sql`（customer_type / customer）、`V8__create_purchase_receiving_inventory_schema.sql`（supplier.remark、warehouse.address/remark、supplier_sku） |
| 后端错误码与异常 | `customer/service/CustomerErrorCodes.java`、`supplier/service/SupplierErrorCodes.java`、`common/exception/{ErrorCode,GlobalExceptionHandler}.java` |
| 后端测试 | `src/test/java/com/xianshuyuan/scm/customer/**`、`src/test/java/com/xianshuyuan/scm/supplier/**`（11 个测试类） |
| 前端页面（React） | `xsy-scm-web/src/pages/customer/{CustomerPage,CustomerTypePage,CustomerDrawer,CustomerTypeDrawer}.tsx`、`pages/supplier/{SupplierPage,WarehousePage}.tsx`、`pages/customer/customerFormModel.ts` |
| 前端契约 | `xsy-scm-web/src/api/customers.ts`、`src/api/suppliers.ts`、`src/types/sales.ts` |
| 需求文档 | `docs/requirements/产品功能需求基线.md`（客户管理段） |
| 参考资产（只读，非主干） | `project-reference-examples/xsy-scm/**` 的 customer / supplier / product-supplier 实现与 `docs/database/{01,02,04}.sql` |

结论提取原则：**只提取业务规则、不变量与真实字段语义**，不复制旧包结构，不翻译旧 React。

---

## 2. 后端资产清单

### 2.1 分层文件（legacy 包结构，V2 不沿用）

```
com.xianshuyuan.scm.customer
├─ controller/  CustomerController、CustomerTypeController、AgreementPriceController、
│               CustomerTypePriceController、PriceHistoryController
├─ dto/         CustomerSaveRequest、CustomerPageQuery、CustomerStatusRequest、
│               CustomerTypeSaveRequest、CustomerSkuVisibilityRequest、
│               AgreementPrice*、CustomerTypePrice*、PriceHistoryPageQuery
├─ entity/      CustomerEntity、CustomerTypeEntity、CustomerSkuVisibilityEntity、
│               CustomerAgreementPriceEntity、CustomerTypePriceEntity、
│               CustomerTypePriceOperationLogEntity、CustomerPriceBatchAuditEntity、
│               AgreementPriceOperationLogEntity、EnabledStatus、VisibilityPolicy
├─ mapper/      CustomerMapper、CustomerTypeMapper、CustomerSkuVisibilityMapper、…
├─ service/     CustomerService、CustomerQueryService、CustomerErrorCodes、
│               CustomerSkuVisibilityChangeSet、OrderableSkuQueryService、
│               AgreementPriceService/Validator、CustomerTypePriceService/Validator/
│               BatchService/BatchWriter、CustomerPriceResolver、PriceHistoryQueryService、
│               PriceSource、PriceStatus、ResolvedCustomerPrice
├─ converter/   CustomerConverter
└─ vo/          CustomerResponse、CustomerTypeResponse、CustomerSkuVisibilityResponse、
                OrderableSkuResponse、AgreementPriceResponse、CustomerTypePriceResponse、…

com.xianshuyuan.scm.supplier
├─ controller/  SupplierController、WarehouseController
├─ dto/         SupplierSaveRequest、SupplierSkuSaveRequest、WarehouseSaveRequest、
│               MasterDataStatusRequest
├─ entity/      SupplierEntity、SupplierSkuEntity、WarehouseEntity
├─ mapper/      SupplierMapper、SupplierSkuMapper、WarehouseMapper
├─ service/     SupplierService、SupplierSkuService、WarehouseService、SupplierErrorCodes
├─ converter/   SupplierConverter
└─ vo/          SupplierVO、SupplierSkuVO、WarehouseVO
```

> **W2 只涉及上表加粗相关的子集**：`CustomerService / CustomerQueryService / CustomerErrorCodes /
> CustomerSkuVisibilityChangeSet / CustomerConverter / CustomerEntity / CustomerTypeEntity /
> EnabledStatus` 与 `SupplierService / SupplierSkuService / WarehouseService /
> SupplierErrorCodes / SupplierConverter / SupplierEntity / SupplierSkuEntity / WarehouseEntity`。
> 定价子域（AgreementPrice / CustomerTypePrice / CustomerPriceResolver / PriceHistory）**整体属于 W3+**，
> 本审计只记录其对外依赖，不作为 W2 规则来源。

### 2.2 枚举

| 枚举 | 位置 | 取值 | 用途 |
|---|---|---|---|
| `EnabledStatus` | `customer/entity` | `ENABLED` / `DISABLED` | **customer、customer_type、supplier、warehouse、supplier_sku 五张表共用一个枚举**（跨包引用：supplier 域 import customer 域） |
| `VisibilityPolicy` | `customer/entity` | `ALL_ENABLED` / `ALLOWLIST` | 客户 SKU 可见性策略（**W3 范围**） |
| `PriceSource` / `PriceStatus` | `customer/service` | `MARKET`/`AGREEMENT`/`CUSTOMER_TYPE`/`OVERRIDE` 等 | 定价域（**W3+**） |

> 观察：legacy **没有** `CustomerTypeEnum`（企业/个人/集团）这种语义枚举——客户类型是**可维护字典表**
> `customer_type`。这与 C 的硬编码枚举是两种不同建模，详见 §8.2。

### 2.3 实体字段（以 DDL 为准）

**supplier**（V1 建表 + V8 追加 `remark`）

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| supplier_code | VARCHAR(64) NOT NULL | partial unique `uk_supplier_code_active WHERE deleted = FALSE` |
| name | VARCHAR(150) NOT NULL | 无索引（列表按 name 排序，无索引支撑） |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| remark | VARCHAR(500) NULL | **V8 追加**（V1 无此列） |
| version | INTEGER NOT NULL DEFAULT 0 | `CHECK (version >= 0)` |
| deleted | BOOLEAN NOT NULL DEFAULT FALSE | 逻辑删除 |
| created_at / updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| created_by / updated_by | VARCHAR(64) NULL | legacy 写死 `"SYSTEM"` |

**warehouse**（V1 建表 + V8 追加 `address` / `remark`）

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| warehouse_code | VARCHAR(64) NOT NULL | partial unique `uk_warehouse_code_active WHERE deleted = FALSE` |
| name | VARCHAR(150) NOT NULL | |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| address | VARCHAR(500) NULL | **V8 追加** |
| remark | VARCHAR(500) NULL | **V8 追加** |
| version / deleted / created_at / updated_at / created_by / updated_by | | 同上 |

**customer_type**（V3 建表）

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| type_code | VARCHAR(64) NOT NULL | partial unique `uk_customer_type_code_active WHERE deleted = FALSE` |
| name | VARCHAR(100) NOT NULL | **无唯一约束**；列表 `ORDER BY name ASC` |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| version / deleted / created_at / updated_at / created_by / updated_by | | 同上 |

**customer**（V3 建表）

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| customer_code | VARCHAR(64) NOT NULL | partial unique `uk_customer_code_active WHERE deleted = FALSE` |
| name | VARCHAR(150) NOT NULL | 索引 `idx_customer_name` |
| customer_type_id | BIGINT NOT NULL | 索引 `idx_customer_type_id`；**无 FK** |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| visibility_policy | VARCHAR(20) NOT NULL DEFAULT 'ALL_ENABLED' | `CHECK IN ('ALL_ENABLED','ALLOWLIST')`；**W3 范围** |
| version / deleted / created_at / updated_at / created_by / updated_by | | 同上 |

> **关键事实：legacy 的 `customer` 表只有 7 个业务列，没有联系人、没有地址、没有账期、没有归属关系。**
> 用户 W2 范围中的「联系人 / 地址 / 账期基础字段」在 legacy 中**没有事实源**（详见 §8.1）。

**supplier_sku**（V8 建表；即 W2 所称 "Product-Supplier Relation"）

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| supplier_id | BIGINT NOT NULL | |
| sku_id | BIGINT NOT NULL | |
| supplier_code_snapshot | VARCHAR(64) NOT NULL | 快照 |
| supplier_name_snapshot | VARCHAR(150) NOT NULL | 快照 |
| sku_code_snapshot | VARCHAR(64) NOT NULL | 快照 |
| sku_name_snapshot | VARCHAR(150) NOT NULL | 快照，取 **SPU/SKU 的 productName**，不是 specName |
| spec_values_snapshot | JSONB NOT NULL DEFAULT '{}' | `CHECK (jsonb_typeof(...) = 'object')` |
| purchase_unit | VARCHAR(32) NOT NULL | 采购单位 |
| reference_price | NUMERIC(18,4) NULL | `CHECK (reference_price IS NULL OR reference_price >= 0)` |
| purchaser_id | BIGINT NULL | 默认采购员；**无引用目标**（legacy 无 FK） |
| is_default | BOOLEAN NOT NULL DEFAULT FALSE | **无唯一约束——允许同一供应商多条默认** |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| version / deleted / created_at / updated_at / created_by / updated_by | | 同上 |
| 索引 | | `uk_supplier_sku_active UNIQUE (supplier_id, sku_id) WHERE deleted = FALSE`；`idx_supplier_sku_sku_id (sku_id) WHERE deleted = FALSE`；`idx_supplier_sku_purchaser_id (purchaser_id) WHERE deleted = FALSE AND purchaser_id IS NOT NULL` |

### 2.4 Mapper XML 自定义 SQL（全部 4 处，穷尽）

| 文件 | 语句 | 语义 |
|---|---|---|
| `customer/CustomerMapper.xml` | `selectCustomerPage` | `SELECT * FROM customer WHERE deleted = FALSE` + keyword `customer_code / name ILIKE` + `customer_type_id =` + `ORDER BY updated_at DESC, id DESC` |
| `customer/CustomerMapper.xml` | `softDelete` | `UPDATE customer SET deleted = TRUE, version = version + 1, updated_at = now(), updated_by = 'SYSTEM' WHERE id = ? AND version = ? AND deleted = FALSE` |
| `supplier/SupplierMapper.xml` | `selectActiveByIdForUpdate` | `SELECT * FROM supplier WHERE id = ? AND deleted = FALSE FOR UPDATE` |
| `supplier/SupplierSkuMapper.xml` | `selectActiveBySupplierId` | `WHERE supplier_id = ? AND deleted = FALSE ORDER BY id` |
| `supplier/SupplierSkuMapper.xml` | `selectActiveBySupplierIdForUpdate` | 同上 + `FOR UPDATE` |
| `supplier/SupplierSkuMapper.xml` | `selectEnabledBySkuId` | 四表 JOIN（supplier_sku + supplier + product_sku + product_spu），要求 `ss.status='ENABLED' AND s.status='ENABLED' AND sku.status='ON_SHELF' AND spu.status='ON_SHELF'` 且四表均未删；`ORDER BY ss.is_default DESC, ss.id` |
| `supplier/SupplierSkuMapper.xml` | `softDeleteOwnedWithVersion` | `UPDATE supplier_sku SET deleted = TRUE, version = version + 1 … WHERE supplier_id = ? AND id = ? AND version = ? AND deleted = FALSE` |
| `customer/CustomerSkuVisibilityMapper.xml` | 3 条 | W3 范围，此处不展开 |

> 结论：legacy 的 Customer/Supplier 主数据读写**绝大部分走 MyBatis-Plus `BaseMapper`**，
> 自定义 XML 只有「分页查询 / 带 version 的原子软删 / 悲观行锁 / 跨表可采购查询」四类。

### 2.5 API 契约（legacy：`ApiResponse` + `PageData`，`/api/**`）

**CustomerController** `@RequestMapping("/api/customers")`

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/customers` | query: `page,pageSize,keyword,customerTypeId` | `PageData<CustomerResponse>` |
| GET | `/api/customers/{id}` | — | `CustomerResponse`（含可见性明细） |
| GET | `/api/customers/{id}/skus` | — | `List<OrderableSkuResponse>`（W3 范围） |
| POST | `/api/customers` | `CustomerSaveRequest` | `Long` |
| PUT | `/api/customers/{id}` | `CustomerSaveRequest` | `void` |
| PUT | `/api/customers/{id}/status` | `{version,status}` | `void` |
| DELETE | `/api/customers/{id}?version=` | — | `void` |

**CustomerTypeController** `@RequestMapping("/api/customer-types")`

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/customer-types` | — | `List<CustomerTypeResponse>`（全量，无分页） |
| POST | `/api/customer-types` | `CustomerTypeSaveRequest` | `Long` |
| PUT | `/api/customer-types/{id}` | `CustomerTypeSaveRequest` | `void` |

**SupplierController** `@RequestMapping("/api/suppliers")`

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/suppliers` | — | `List<SupplierVO>`（全量，无分页） |
| GET | `/api/suppliers/{id}` | — | `SupplierVO` |
| GET | `/api/suppliers/{id}/skus` | — | `List<SupplierSkuVO>`（全量） |
| POST | `/api/suppliers` | `SupplierSaveRequest` | `Long` |
| PUT | `/api/suppliers/{id}` | `SupplierSaveRequest` | `void` |
| **POST** | `/api/suppliers/{id}/status` | `{version,status}` | `void` |
| PUT | `/api/suppliers/{id}/skus` | `List<SupplierSkuSaveRequest>` | `void`（**整表替换**） |

**WarehouseController** `@RequestMapping("/api/warehouses")`

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/warehouses` | — | `List<WarehouseVO>` |
| GET | `/api/warehouses/{id}` | — | `WarehouseVO` |
| POST | `/api/warehouses` | `WarehouseSaveRequest` | `Long` |
| PUT | `/api/warehouses/{id}` | `WarehouseSaveRequest` | `void` |
| POST | `/api/warehouses/{id}/status` | `{version,status}` | `void` |

**契约不一致点（V2 必须统一）**：

| 项 | customer | supplier | warehouse | V2 统一为 |
|---|---|---|---|---|
| 列表 | GET + 分页 | GET + **无分页** | GET + **无分页** | POST + `PageParam` |
| 改状态 | `PUT /{id}/status` | `POST /{id}/status` | `POST /{id}/status` | `POST /updateStatus`（body 带 id） |
| 删除 | `DELETE /{id}?version=` | **无** | **无** | `POST /delete`（body 带 id+version） |
| 详情 | `GET /{id}` | `GET /{id}` | `GET /{id}` | `GET /detail/{id}` |

### 2.6 错误码

**CustomerErrorCodes**

| 码 | HTTP | 语义 | W2 相关 |
|---|---|---|---|
| 40430 | 404 | 客户不存在 | ✅ |
| 40431 | 404 | 客户类型不存在 | ✅ |
| 40432 | 404 | 协议价不存在 | ✖（W3） |
| 40433 | 404 | 客户类型价不存在 | ✖（W3） |
| 40930 | 409 | 客户未启用 | ✅ |
| 40931 | 409 | SKU 对该客户不可见或未上架 | ✖（W3） |
| 40932 | 409 | 可见性记录不属于当前客户 | ✖（W3） |
| 40933 | 409 | 协议价有效期重叠 | ✖（W3） |
| **40934** | 409 | **数据已被其他操作修改，请刷新后重试** | ✅ |
| 40935 | 409 | 客户类型价有效期重叠 | ✖（W3） |
| 40030 | 400 | 协议价不能小于零 | ✖（W3） |
| 40031 | 400 | 结束时间必须晚于开始时间 | ✖（W3） |

**SupplierErrorCodes**

| 码 | HTTP | 语义 | 触发点 |
|---|---|---|---|
| 40440 | 404 | 供应商不存在 | `requireSupplier` |
| 40441 | 404 | 仓库不存在 | `requireWarehouse` |
| 40442 | 404 | 供应商 SKU 配置不存在 | `requireEnabledForPurchasing` / `require` |
| 40940 | 409 | 资料未启用 | supplier / warehouse 停用 |
| **40941** | 409 | **数据已被其他操作修改，请刷新后重试** | supplier / warehouse / supplier_sku |
| 40942 | 409 | SKU 未启用或不存在 | `SupplierSkuService.build` |
| 40943 | 409 | 供应商 SKU 配置重复 | `replaceForSupplier` / `updateExisting` |
| 40944 | 409 | 供应商编码已存在 | **仅由 `GlobalExceptionHandler` 按约束名 `uk_supplier_code_active` 映射** |
| 40945 | 409 | 仓库编码已存在 | 同上（`uk_warehouse_code_active`） |
| 40946 | 409 | 供应商 SKU 配置已存在 | 同上（`uk_supplier_sku_active`） |

**通用错误码（`ErrorCode`）**：`40000` 请求参数不正确 / `40400` 请求的资源不存在 / `40900` 数据状态冲突 / `50000` 系统内部错误。

> ⚠ **缺口**：`uk_customer_code_active` 与 `uk_customer_type_code_active` **没有**约束名映射，
> 客户/客户类型编码重复会退化为通用 `40900 数据状态冲突`。V2 必须补专属错误码。

### 2.7 编码冲突的处理方式（重要架构事实）

legacy **不在 Service 层查重编码**，而是依赖 DB 唯一索引抛 `DataIntegrityViolationException`，
再由 `GlobalExceptionHandler.handleDataConflict` 用 `constraintDetail(exception).contains("<约束名>")`
做字符串匹配映射到业务错误码：

```java
ErrorCode code = detail.contains("uk_supplier_code_active")
        ? SupplierErrorCodes.SUPPLIER_CODE_CONFLICT
        : detail.contains("uk_warehouse_code_active") ? …
        : detail.contains("uk_supplier_sku_active") ? … : ErrorCode.DATA_CONFLICT;
```

**风险**：PostgreSQL 驱动/服务端消息本地化、或未来换索引名，都会让匹配失效并静默退化为 40900。
**V2 处置**：改由 Service 层**显式查重**（`SELECT 1 … WHERE code = ? AND deleted = FALSE`）返回专属错误码，
DB partial unique 仅作为**并发兜底**保留。

---

## 3. 必须保留的业务不变量（逐条定位到实现与测试）

### 3.1 客户（Customer）

| # | 不变量 | legacy 实现位置 | 证据 |
|---|---|---|---|
| C1 | `customer_code` 在未删除记录内唯一 | DDL `uk_customer_code_active … WHERE deleted = FALSE` | V3 DDL |
| C2 | 客户必须挂在一个**存在、未删除且 ENABLED** 的客户类型上 | `CustomerService.requireType`（三条件合一 → 40431） | `CustomerServiceTest.rejectsDisabledCustomerType` |
| C3 | 客户必须存在且未删除 | `CustomerService.require` → 40430 | `CustomerQueryService.get` |
| C4 | 停用客户不能作为"可交易客户" | `CustomerService.requireEnabled` → 40930 | 供 W3 定价/下单使用 |
| C5 | 更新必须携带 `version`，缺省即冲突 | `CustomerService.update`：`r.version() == null → 40934` | 实现 |
| C6 | 更新影响行数 ≠ 1 即视为并发冲突 | `customers.updateById(e) != 1 → 40934` | 实现 |
| C7 | 状态变更走**独立端点**，body `{version,status}` | `CustomerController.status`（PUT） | `CustomerControllerTest.mapsConflictToStableCode`（40934） |
| C8 | 删除是**原子 id + version 谓词**的软删，不是 `updateById` | `CustomerMapper.softDelete` XML | `CustomerServiceTest.customerDeleteUsesAtomicIdVersionPredicate`（`softDelete(1,3)` 返回 0 → 40934） |
| C9 | 删除客户时级联软删其 SKU 可见性行 | `CustomerService.delete` 先 `selectActiveByCustomerId` → `softDeleteOwned` | 实现（W3 语义，但 W2 需保留删除语义的扩展位） |
| C10 | 删除后编码可复用 | partial unique `WHERE deleted = FALSE` | V3 DDL |
| C11 | 状态取值域 `ENABLED` / `DISABLED` | DB CHECK + `EnabledStatus` | V3 DDL |
| C12 | 分页 `page ≥ 1`，`pageSize ∈ [1,100]` | `CustomerController.page` 的 `@Min/@Max` | 实现 |
| C13 | 关键字搜索覆盖**客户编码 + 客户名称**（`ILIKE %kw%`） | `CustomerMapper.xml` | XML |
| C14 | 客户类型筛选为**精确匹配** `customer_type_id =` | `CustomerMapper.xml` | XML |
| C15 | 默认排序 `updated_at DESC, id DESC` | `CustomerMapper.xml` | XML |
| C16 | 列表**不返回**可见性明细（传 `List.of()`），详情才返回 | `CustomerQueryService.page` vs `.get` | 实现 |
| C17 | 列表补客户类型名时**批量取**，不 N+1 | `types.selectBatchIds(distinct ids)` | 实现 |
| C18 | 版本冲突统一 40934 | `CustomerErrorCodes.VERSION_CONFLICT` | 实现 |

### 3.2 客户类型（CustomerType）

| # | 不变量 | 位置 | 证据 |
|---|---|---|---|
| T1 | `type_code` 在未删除记录内唯一 | DDL partial unique | V3 DDL |
| T2 | `name` **不唯一**（允许重名） | DDL 无唯一约束 | V3 DDL |
| T3 | 全量列表按 `name ASC` 排序 | `CustomerService.listTypes` | 实现 |
| T4 | 更新必须携带 `version`，缺省即 40934 | `CustomerService.updateType` | 实现 |
| T5 | 更新影响行数 ≠ 1 即 40934 | `types.updateById(e) != 1` | 实现 |
| T6 | **无删除端点** | `CustomerTypeController` 只有 list/create/update | 控制器全文 |
| T7 | 创建时 `version=0, deleted=false, created_by='SYSTEM'` | `CustomerService.createType` | 实现 |
| T8 | 更新走 `CustomerConverter.toType`，**status 由请求体带入**（无独立状态端点） | `CustomerTypeSaveRequest.status` `@NotNull` | DTO |

### 3.3 供应商（Supplier）

| # | 不变量 | 位置 | 证据 |
|---|---|---|---|
| S1 | `supplier_code` 在未删除记录内唯一 | DDL partial unique | V1 DDL |
| S2 | 供应商必须存在且未删除 → 40440 | `SupplierService.requireSupplier` | 实现 |
| S3 | 停用供应商不能用于采购/关联 → 40940 | `requireEnabledSupplier` | `SupplierServiceTest.rejectsDisabledSupplierForEnabledRequirement` |
| S4 | 采购路径必须**先锁 supplier 行**（`SELECT … FOR UPDATE`），再读 `supplier_sku` | `requireEnabledSupplierForUpdate` + `selectActiveByIdForUpdate` | 实现（悲观锁） |
| S5 | 更新必须携带 `version`；缺省或影响行数 ≠ 1 → 40941 | `updateSupplier` | `SupplierServiceTest.mapsStaleSupplierVersionToConflict` |
| S6 | **更新不改状态**：`status` 字段保持 `null`，不进 UPDATE 语句 | `updateSupplier` 构造新实体时**不 set status** | `SupplierServiceTest.updatesSupplierWithoutChangingStatus`（断言 `getStatus() == null`） |
| S7 | 创建时**强制** `status = ENABLED`，忽略请求体 | `createSupplier`：`e.setStatus(EnabledStatus.ENABLED)` | 实现 |
| S8 | 状态变更走 `POST /{id}/status`，body `{version,status}` | `SupplierController.status` | `SupplierControllerCrudTest.changesSupplierStatusExplicitly` |
| S9 | **无删除端点** | `SupplierController` 无 `@DeleteMapping` | 控制器全文 |
| S10 | 编码冲突靠 DB 唯一索引 + 约束名映射 → 40944 | `GlobalExceptionHandler` | 实现（脆弱，见 §2.7） |
| S11 | 全量列表**只过滤 `deleted=false`，不过滤 status**（停用供应商也会返回） | `SupplierService.listSuppliers` | 实现 |
| S12 | 全量列表按 `name ASC` 排序 | 同上 | 实现 |

### 3.4 仓库（Warehouse）

| # | 不变量 | 位置 |
|---|---|---|
| W1 | `warehouse_code` 在未删除记录内唯一 | V1 DDL partial unique |
| W2 | 必须存在且未删除 → 40441 | `WarehouseService.requireWarehouse` |
| W3 | 停用仓库不能用于收货 → 40940 | `requireEnabledWarehouse` |
| W4 | 更新必须携带 `version`；冲突 → 40941 | `updateWarehouse` |
| W5 | 状态变更走 `POST /{id}/status` | `WarehouseController.status` |
| W6 | **无删除端点** | `WarehouseController` 无 `@DeleteMapping` |
| W7 | 全量列表只过滤 `deleted=false`，按 `name ASC` | `listWarehouses` |

### 3.5 商品-供应商关系（SupplierSku / Product-Supplier Relation）

| # | 不变量 | 位置 | 证据 |
|---|---|---|---|
| R1 | `(supplier_id, sku_id)` 在未删除记录内唯一 | DDL `uk_supplier_sku_active` | V8 DDL |
| R2 | 写入前**必须**：供应商存在且 ENABLED，且**对 supplier 行加 `FOR UPDATE` 锁** | `replaceForSupplier` → `requireEnabledSupplierForUpdate` | `SupplierSkuServiceTest.locksSupplierBeforeReadingConfigurationRows`（`InOrder` 断言锁序） |
| R3 | 关联的 SKU 必须"可下单"：SPU 与 SKU **均 `ON_SHELF` 且未删除** → 否则 40942 | `SupplierSkuService.build` → `skus.selectOrderableByIds` | `SupplierSkuServiceTest.rejectsConfigurationForOffShelfSku` |
| R4 | 快照字段在写入时冻结：供应商编码/名称、SKU 编码、SKU **商品名（productName，非 specName）**、规格 JSONB（null → `{}`） | `SupplierSkuService.build` | `SupplierSkuServiceTest.snapshotsProductNameInsteadOfSpecificationName` |
| R5 | 整表替换语义：以 `supplier_id` 为单位差量同步——**保留 id**、新增、未出现的按 version 软删 | `replaceForSupplier` | `replacementUpdatesRetainedCreatesNewAndSoftDeletesRemoved` |
| R6 | **所有请求先全部校验，再开始写**（不得边校验边写） | `replaceForSupplier` 两段式（先构 `replacements`，后统一写） | `validatesEveryRequestBeforeChangingExistingRows` |
| R7 | 请求内 `supplierId` 必须等于路径 `supplierId`；`skuId` 不得重复 → 40943 | `replaceForSupplier` 前置循环 | `rejectsDuplicateSkuBeforeChangingExistingRows` |
| R8 | 带 `id` 的行必须属于该供应商，且 `skuId` 不得变更 → 40943 | `updateExisting` 两处判断 | `rejectsRetainedRowOwnedByAnotherSupplier` |
| R9 | 带 `id` 的行 `version` 必须与库中一致 → 40941 | `matched.getVersion()` 比对 | `rejectsStaleRetainedVersionBeforeUpdating` |
| R10 | 无 `id` 但 `(supplierId, skuId)` 已存在 → **复用该行**（不新增） | `existingBySkuId` 兜底匹配 | `reconcilesNoIdRowAgainstExistingActivePair` |
| R11 | 空数组 = 清空该供应商全部关联（按行 version 软删） | `replaceForSupplier(id, List.of())` | `clearingConfigurationsDeletesEveryRowWithExpectedVersion`；`SupplierControllerTest.acceptsEmptyReplacementAsClearAll` |
| **R12** | **允许同一供应商存在多条 `is_default = TRUE`——legacy 明确不发明"每供应商唯一默认"的基数策略** | `is_default` **无** partial unique | `SupplierSkuServiceTest.allowsMultipleDefaultsWithoutInventingCardinalityPolicy` |
| R13 | 参考价必须是 **4 位以内小数的字符串**：正则 `^\d{1,14}(\.\d{1,4})?$` | `SupplierSkuSaveRequest.referencePrice` | `SupplierControllerTest.rejectsNumericReferencePrice` / `rejectsReferencePriceWithExcessiveScale` |
| R14 | 非 MVC 入口的参考价解析失败 → `40000` | `SupplierSkuService.parseReferencePrice` | `mapsInvalidReferencePriceToValidationErrorOutsideMvc` |
| R15 | 删除单行走**原子 supplier_id + id + version 谓词**，返回 0 → 40941 | `softDeleteOwnedWithVersion` | `rejectsConcurrentChangeWhileDeletingOmittedRow` |
| R16 | "可采购"判定必须四表联动：`ss.ENABLED ∧ s.ENABLED ∧ sku.ON_SHELF ∧ spu.ON_SHELF`（四表均未删），排序 `is_default DESC, id` | `selectEnabledBySkuId` XML | 实现 |
| R17 | `requireEnabledForPurchasing` 先 `requireEnabledSupplier` 再按 `(supplierId, skuId)` 过滤结果（**不允许旁路主数据状态**） | `SupplierSkuService.requireEnabledForPurchasing` | `requiresEnabledConfigurationForSupplierAndSku` / `rejectsMissingConfigurationForSupplierAndSku` |
| R18 | `purchaserId` 必须为正数（可空） | `@Positive Long purchaserId` | DTO |
| R19 | `purchaseUnit` 必填、≤ 32 字符 | `@NotBlank @Size(max=32)` | DTO |
| R20 | `status` 缺省为 `ENABLED` | `build`：`request.status() == null ? ENABLED : request.status()` | 实现 |

> **R12 是本轮审计中最容易被"顺手改坏"的一条**。C 的 `t_product_supplier.default_flag` 同样无唯一约束，
> 但 C 的页面语义是"设置默认供应商"（隐含唯一）。V2 **必须按 legacy 保留"多默认允许"**，
> 否则会引入一条无依据的业务规则（参照 W1 的 P2「恰好 1 个默认 SKU」是**有 legacy 依据**的，
> 而这里 legacy 明确没有该依据）。

---

## 4. 前端 legacy 行为审计（React，仅作行为参考）

### 4.1 客户档案页（`CustomerPage.tsx` + `CustomerDrawer.tsx`）

| 区域 | legacy 行为 | 是否保留到 V2 |
|---|---|---|
| 筛选栏 | 关键字（客户名称/编码）+ 客户类型下拉 | 保留语义 |
| 列表列 | 客户名称（链接开抽屉）、客户编码、客户类型名、**可见范围**（全部在售 SKU / 白名单 SKU）、状态、更新时间、操作 | 名称/编码/类型/状态/时间保留；**可见范围列 W2 不做**（W3） |
| 操作列 | 编辑、停用/启用（Popconfirm）、删除（Popconfirm） | 保留（V2 用 `a-popconfirm` + version） |
| 抽屉 | 客户编码、名称、类型、**可见范围策略**、状态、SKU 白名单多选（按策略联动） | 基础字段保留；可见性部分 **W3** |
| 冲突提示 | HTTP 409 → 「客户数据已更新，请重新加载后再操作」 | 保留语义（V2 用 `code 40921`） |
| 权限 | `Permission` 组件 + `AUTHORITIES.customerManage`（**单一权限**） | **改为**细分 `scm:customer:*` + `v-privilege` |
| 加载失败 | Alert + 重新加载 | 保留 |
| 分页 | 默认 20，可改页大小，`showTotal` | 保留 |
| 客户类型页 | 独立页 `CustomerTypePage`（类型编码/名称/状态 + 抽屉），**无删除按钮** | 保留（V2 增加删除，见 §8.1 缺口 3） |

### 4.2 供应商页（`SupplierPage.tsx`）与仓库页（`WarehousePage.tsx`）

| 区域 | legacy 行为 | 是否保留到 V2 |
|---|---|---|
| 供应商页 | **只读列表**：编码、名称、状态。无新增/编辑/删除按钮，无筛选 | **大幅扩展**（legacy 前端是占位页） |
| 仓库页 | 只读列表：编码、名称、地址、状态 | W2 是否做仓库待定（见 §8.1 缺口 5） |
| 供应商 SKU 关联 | **legacy 前端完全没有页面** | V2 **新增**（W2 明确范围） |

### 4.3 legacy 前端缺陷（V2 不复制）

1. **供应商与仓库前端是只读占位页**：后端已具备完整 CRUD（含状态与整表替换），前端未接线。V2 需从零构建列表/详情/关联维护页。
2. `CustomerPage` 的筛选表单与 `ProTable.request` 通过 `form.getFieldsValue()` **隐式耦合**，V2 应改为显式 `queryForm` 响应式对象（与 W1 一致）。
3. `Permission` 依赖 `AuthProvider`，V2 未挂载该 Provider；必须改用 `v-privilege`（后端鉴权为准）。
4. 客户抽屉的「可见范围策略 ↔ SKU 白名单」联动逻辑与 `CustomerSkuVisibilityChangeSet` 的差量语义重复实现，易漂移；W3 需单一化。
5. `CustomerTypePage` 使用 `describeError` 兜底文案，未按错误码分支；V2 应显式处理 40921/编码冲突。

---

## 5. 后端 legacy 缺陷与风险（V2 必须修正）

| # | 问题 | 位置 | 影响 | V2 处置 |
|---|---|---|---|---|
| **D1** | `created_by` / `updated_by` 写死 `"SYSTEM"` | `CustomerConverter.toCustomer/toType`、`CustomerService.updateStatus/delete`、`CustomerMapper.softDelete` XML、`SupplierService.*`、`SupplierSkuService.*`、`SupplierConverter`、`SupplierSkuMapper.softDeleteOwnedWithVersion` XML | 审计字段失真，无法追溯操作人 | 改用 `ScmOperator.current()`（W1 已交付） |
| **D2** | **完全没有权限校验**：customer/supplier 域 4 个 Controller 中 `@PreAuthorize` / `@Secured` / `hasAuthority` 出现 **0 次** | `grep` 全域 0 命中 | 任何已登录用户可读写全部主数据 | 全部端点加 `@SaCheckPermission("scm:customer:*")` / `scm:supplier:*` |
| **D3** | **完全没有操作日志**：全域无 `@OperateLog` 或等价机制 | 同上 | 客户/供应商主数据变更不可审计（而 C 的需求文档明确要求"账期修改必须留审计日志"） | 全部写端点加 `@OperateLog` |
| **D4** | **编码冲突映射靠异常消息字符串匹配** | `GlobalExceptionHandler.handleDataConflict` | 消息本地化/索引改名即静默退化为 40900 | Service 层显式查重 + 专属错误码；DB 唯一索引仅作并发兜底 |
| **D5** | `uk_customer_code_active` / `uk_customer_type_code_active` **无约束名映射** | `GlobalExceptionHandler` | 客户与客户类型编码重复只返回通用 `40900 数据状态冲突` | 新增 `CUSTOMER_CODE_CONFLICT` / `CUSTOMER_TYPE_CODE_CONFLICT` |
| **D6** | `CustomerService.updateType` 只判 `selectById(id) == null`，**不判 `deleted`** | `CustomerService.updateType` | 已软删的客户类型仍可被"更新成功" | `requireType(id)` 统一判 `deleted` |
| **D7** | `CustomerService.update` 的 `CustomerConverter.toCustomer` **把 `status` 与 `visibilityPolicy` 一并写入** | `CustomerConverter.toCustomer` + `CustomerSaveRequest` 含 `@NotNull status` | update 端点可绕过 `/{id}/status` 端点改状态；两条写路径语义重复 | V2 拆分：`CustomerUpdateForm` **不含 status**；状态只走 `updateStatus` |
| **D8** | 客户删除**不检查任何引用**（无 FK、无计数） | `CustomerService.delete` | 可删除仍被协议价/客户类型价/可见性引用的客户（可见性行会被级联软删，其余成为孤儿） | V2 删除前检查活动引用；W2 尚无下游，规则先落位 |
| **D9** | 供应商/仓库/客户类型**完全没有删除能力** | 三个 Controller 无 `@DeleteMapping` | 主数据只能停用，无法下线错误数据 | V2 新增删除端点 + 引用检查（见 §8.1 缺口 3/4） |
| **D10** | `SupplierService.createSupplier` / `updateSupplier` **不 `trim()` 编码与名称**；`CustomerConverter` 对 customer/type 做了 `trim()` | 两域不一致 | 同一份数据两套规范化规则，`" S1"` 与 `"S1"` 可共存于不同表 | V2 统一：编码与名称写入前 `trim()`，编码额外 `toUpperCase()` 归一化去重 |
| **D11** | `SupplierService.listSuppliers` / `WarehouseService.listWarehouses` **不过滤 `status`** | 两个 service | 下拉框会含停用项，前端需自行过滤 | V2 提供 `option/list`（只返回 ENABLED）+ 管理分页查询（返回全部） |
| **D12** | 全量 list 端点**无分页** | `SupplierController.list`、`WarehouseController.list`、`CustomerTypeController.list` | 数据量增长后响应体膨胀 | 管理列表改 POST + `PageParam`；下拉走独立 `option/list` |
| **D13** | 参考价解析在 Service 层用 `new BigDecimal(value)`，与 DTO 正则**双重校验但语义不同** | `SupplierSkuService.parseReferencePrice` | 非 MVC 入口才走到这里，两处规则可能漂移 | V2 统一为单一 `ScmDecimalStrings.parseScale4` 工具 |
| **D14** | `SupplierConverter.decimal` 用 `setScale(4)` **未指定 `RoundingMode`** | `SupplierConverter.decimal` | 超过 4 位小数时抛 `ArithmeticException`；依赖 DDL 恰为 NUMERIC(18,4) 才不炸（与 W1 D2 同源） | 改用 `setScale(4, RoundingMode.HALF_UP)`（`ScmFixedScale4Serializer`） |
| **D15** | `CustomerQueryService.page` 对空列表调用 `types.selectBatchIds(List.of())` | `CustomerQueryService.page` | 依赖 MyBatis-Plus 对空集合短路，语义脆弱 | V2 显式判空跳过 |
| **D16** | `SupplierSkuService.replaceForSupplier` 的 `existingBySkuId` 用 `Collectors.toMap`，重复 key 会抛 `IllegalStateException` | 同上 | 依赖 DB 唯一索引保证无重复；索引失效即 500 | V2 显式检测重复并返回业务错误码 |
| **D17** | `SupplierSkuService.supplierIdEquals` 用 `entity.getSupplierId() == supplierId`（`Long == long` 拆箱比较） | 同上 | 当前正确，但语义脆弱（若两侧都是 `Long` 则变引用比较） | V2 统一 `Objects.equals` |
| **D18** | 分页排序 `sortItemList` 只做 `SqlInjectionUtils.check`，**无字段白名单** | `SmartPageUtil.convert2PageQuery` | 可对任意列排序（含未索引列），性能与信息泄露面 | V2 按 W1 做法使用**白名单**（`updated_at`/`name`/`customer_code` 等） |
| **D19** | 无 `@Version` 之外的并发保护；`customer_type` 的 create 不校验编码重复 | `createType` | 并发创建同编码类型 → DB 异常 → 40900 | Service 显式查重 + 40934/编码冲突码 |
| **D20** | 客户/客户类型/供应商/仓库**均无 `SELECT … FOR UPDATE` 用于自身更新**（只有 supplier 供 `supplier_sku` 关联时加锁） | 各 service | 并发编辑靠乐观锁兜底（可接受）；但 `supplier_sku` 的锁序必须保持 | V2 保持乐观锁为主，`supplier_sku` 替换保留悲观锁序 |

---

## 6. 测试覆盖审计

| 测试类 | 类型 | 用例数 | 覆盖 | 缺口 |
|---|---|---|---|---|
| `CustomerServiceTest` | 纯单测 | 3 | 停用客户类型拒绝、ALL_ENABLED 不得带明细、删除原子 id+version 谓词 | 未覆盖编码重复、状态端点、`deleted` 类型更新、`trim` 行为 |
| `CustomerSkuVisibilityChangeSetTest` | 纯单测 | 4 | 保留 id 分组、跨客户拒绝、SKU 不可替换、重复 SKU | W3 范围 |
| `CustomerControllerTest` | `@WebMvcTest` | 3 | 分页信封、40934 冲突、`/skus` 契约 | 未覆盖编码冲突码、参数边界、权限拒绝 |
| `SupplierServiceTest` | 纯单测 | 4 | 停用拒绝、**更新不改 status**、版本冲突、仓库状态 | 未覆盖编码冲突、创建强制 ENABLED、列表含停用项 |
| `SupplierSkuServiceTest` | 纯单测 | **16** | 锁序、快照、可下单、重复、**多默认允许**、差量、先校验后写、跨供应商、清空、并发、版本 | 未覆盖 `status=DISABLED` 行、`purchaserId` 校验 |
| `SupplierControllerCrudTest` | `@WebMvcTest` | 2 | 列表/详情 VO、状态变更 | 未覆盖创建/更新/编码冲突 |
| `SupplierControllerTest` | `@WebMvcTest` | 5 | SKU 列表 4 位小数、整表替换、数字参考价拒绝、超 4 位小数拒绝、空数组清空 | 未覆盖 40942/40943 |
| `WarehouseControllerTest` | `@WebMvcTest` | 2 | 列表/详情、状态变更 | 未覆盖创建/更新/编码冲突 |
| `ProcurementOptimisticLockIT` | `@SpringBootTest` + `@Transactional` | 3 | supplier / warehouse / supplier_sku 三表**真实 PG 乐观锁失效** | 未覆盖 customer / customer_type；未覆盖 `FOR UPDATE` 锁序 |
| `AgreementPrice*Test` / `CustomerTypePrice*Test` / `CustomerPriceResolverTest` / `PriceHistoryQueryIT` | — | — | W3 范围 | — |

**测试总量**：Customer+Supplier 域共 **11 个测试类、约 42 个用例**。
**必须补齐到 V2 的测试**：编码冲突（4 张表）、`deleted` 类型更新拒绝、`updateStatus` 端到端、供应商删除引用检查、`supplier_sku` 的 `status=DISABLED`、`purchaserId` 校验、权限拒绝、分页排序白名单、`FOR UPDATE` 锁序在真实 PG 上的验证。

> ⚠ **`ProcurementOptimisticLockIT` 是 W2 最有价值的一条既有资产**：它证明 legacy 在真实 PostgreSQL 上
> `@Version` 生效。V2 必须在 W2 复用同一形状（插入 → `sqlSession.clearCache()` → 取两份 → 第二份 stale 返回 0），
> 并扩展到 customer / customer_type。

---

## 7. 跨域依赖与 W2 排除项

### 7.1 谁依赖 Customer / Supplier（W2 不实现）

| 依赖方 | 依赖点 | W2 处置 |
|---|---|---|
| pricing（协议价 / 客户类型价 / 客户分级） | `customer.customer_type_id`、`customer.status`、`requireEnabled` | **只保留字段与状态语义**；`requireEnabled` 的"可交易"判定必须在 W2 落位，供 W3 复用 |
| order / mall | `customer.id` 作为订单归属；`mall_customer_address`（legacy V29，独立表） | 不实现；**W2 的 `customer.address` 是档案地址，不是商城收货地址** |
| purchase / receiving | `supplier.status`（`requireEnabledSupplierForUpdate`）、`supplier_sku`（`requireEnabledForPurchasing`）、`warehouse.status` | **只保留表结构、状态语义与 `FOR UPDATE` 锁序**；不迁移 `requireEnabledForPurchasing` 调用点 |
| inventory / sorting | `warehouse.id` 作为库位维度 | 不实现 |
| 数据权限 | 业务员只能看自己的客户（C 需求 §6 提到 `@DataScope`） | **W2 不做**（跨切面能力，单独立项） |

> **必须在 W2 保留的语义**：`supplier.status = ENABLED` 与 `supplier_sku.status = ENABLED` 且
> `sku/spu = ON_SHELF` 四表联动的"可采购"谓词形状。W2 不实现"可采购查询端点"，
> 但**列、约束、状态域与排序（`is_default DESC, id`）必须与 legacy 一致**，否则 W3 无法平移。

### 7.2 W2 明确不做（用户锁定）

Agreement Price · Customer Type Price · Customer SKU Visibility · Customer Discount · Pricing Resolver ·
Sales Order · Purchase · Receiving · Inventory · Mall · MiniApp。

### 7.3 Customer / Supplier 对 SmartAdmin 的依赖（W2 直接复用）

`ResponseDTO`、`PageResult` / `PageParam` / `SmartPageUtil`、Sa-Token `@SaCheckPermission`、
`@OperateLog`（`t_operate_log`）、`t_employee`（业务员 / 采购员引用目标）、动态菜单、`v-privilege`、
`lib/axios.ts` 响应拦截器、`SmartEnumSelect`。

---

## 8. W2 范围内 Legacy 无事实源的部分（**本轮审计最关键的结论**）

用户给定的 W2 范围中有 6 项在 legacy 中**完全没有对应实现**。这些不是"迁移"，而是**新增能力**，
必须由人工拍板后写入 Target Design，不能从 legacy 推导。

### 8.1 缺口清单

| # | W2 范围项 | legacy 事实 | 结论 |
|---|---|---|---|
| 1 | **联系人** | `customer` / `supplier` 表**均无** `contact_*` 列；`grep` 全前端 `contact\|联系人` 在 customer/supplier 上下文 **0 命中** | 新增字段。唯一可用参考是 C 的 `contact_name` / `contact_phone`（单值内嵌） |
| 2 | **地址** | `customer` / `supplier` 表**均无** `address` 列；仅 `warehouse.address`（V8 追加）存在 | 新增字段。C 的 `address` + `longitude`/`latitude` 可参考 |
| 3 | **账期基础字段** | `customer` 表**无任何账期字段**；账期只存在于 W3 的定价域之外，legacy 全域无账期表 | **全新能力**。C 有 `t_customer_period`（按金额/按时间·天/月/固定结算日）+ `credit_amount` + `balance` |
| 4 | **客户状态语义** | legacy 只有 `ENABLED`/`DISABLED` 二态 | C 有 `POTENTIAL`/`COOPERATING`/`SUSPENDED`/`BLACKLIST` 四态。需拍板 |
| 5 | **客户类型语义** | legacy 是可维护字典表（自由 `type_code`），无预置语义 | C 是硬编码枚举（企业/个人/集团）+ 独立 `customer_level_id` 分级。需拍板 |
| 6 | **归属关系** | `customer` 表**无** `seller_id` / `supplier_id` / `parent_customer_id` | C 与需求基线（「客户绑定供应商、业务员」「下属单位独立采购，集团客户统一结算」）都有。需拍板 |

### 8.2 两个建模冲突的辨析

**冲突 A：客户类型 = 可维护字典 vs 硬编码枚举**

- legacy：`customer_type` 独立表，`type_code` 由用户自由录入（种子 `STANDARD / 标准客户`），有 status/version/软删。
- C：`CustomerTypeEnum`（1 企业 / 2 个人 / 3 集团）硬编码，另用 `customer_level_id` 表达"分级（影响取价）"。
- **辨析**：两者语义不同——legacy 的"类型"是**业务分组**（可自定义，如"餐饮/商超/团餐"），C 的"类型"是**法律主体形态**，C 的"分级"才是 legacy 意义上的分组。
- **V2 处置建议**：保留 legacy 的**可维护字典表**（W2 明确列了"客户类型"），并把 C 的三种主体形态作为**种子数据**（`ENTERPRISE`/`PERSONAL`/`GROUP`）。C 的 `customer_level_id`（客户分级 → 影响取价）**归 W3 定价域**，W2 不落。

**冲突 B：关系粒度 = SKU 级 vs 商品级**

- legacy：`supplier_sku(supplier_id, sku_id)` —— **SKU 级**，与 W1 已确立的「SKU 是跨域引用维度」一致。
- C：`t_product_supplier(product_id, supplier_id)` —— **商品（SPU）级**，且带 `supply_price`。
- **辨析**：legacy 粒度更细且与 W1 的聚合根一致；C 的 `supply_price` 属于定价域。
- **V2 处置**：**采用 legacy 的 SKU 级**，表名沿用 `supplier_sku`；C 的 `supply_price` 不迁移（W2 无定价域）。

**冲突 C：账期 = 内嵌字段 vs 独立子表**

- legacy：无。
- C：独立表 `t_customer_period`（一个客户可多行），并单独有列表页 `customer-period-list.vue`。
- **辨析**：用户 W2 措辞是「账期**基础字段**」，而非「账期管理」；且 C 的需求文档明确要求"账期修改必须留审计日志"，而 W2 尚无应收/结算消费方。
- **V2 处置建议**：W2 在 `customer` 上落**内嵌基础字段组**（`settle_mode` + `credit_limit` + 账期类型/阈值/值/单位/结算日），审计由 SmartAdmin `@OperateLog` + `t_operate_log` 承担；若后续确需多账期/账期历史，W3 追加 `customer_period` 子表 migration。

### 8.3 明确的"不要做"

| 项 | 原因 |
|---|---|
| 不落 `visibility_policy` 列 | W2 排除 SKU Visibility；且 C 用「逐商品 visible_type」而 legacy 用「策略枚举 + 白名单」，W3 设计时可能推翻，过早固化列有害 |
| 不落 `balance`（余额账户） | 属财务域；`AGENTS.md` 要求"余额变动必须走财务流水"，W2 无财务域，落列即埋隐患 |
| 不落 `longitude` / `latitude` | 消费方是物流线路规划（W7+）；无消费方的坐标字段无校验标准（GCJ-02/WGS-84 未定） |
| 不落 `customer_level_id` | 属定价域（影响取价），W3 |
| 不做 `@DataScope` 业务员数据隔离 | 跨切面能力，影响全部模块，需单独立项 |
| 不做商城收货地址 | legacy 已有独立表 `mall_customer_address`（V29），W6 迁 MiniApp 时处理 |

---

## 9. 审计结论

1. **Customer 与 Supplier 在 legacy 中是两个极简主数据域**：customer 表 7 个业务列、supplier 表 4 个业务列，
   没有联系人、没有地址、没有账期、没有归属关系。W2 范围中的这 4 项属于**新增能力**，不是迁移。
2. **真正可平移的 legacy 资产集中在"并发与删除语义"**：
   `CustomerMapper.softDelete` 的原子 id+version 软删、`SupplierMapper.selectActiveByIdForUpdate` 的悲观锁、
   `SupplierSkuService.replaceForSupplier` 的"先锁 supplier → 锁 supplier_sku → 全量校验 → 统一写"四段式。
   这三者是 W2 最有价值的事实源，必须逐条保留。
3. **共提取 18 + 8 + 12 + 7 + 20 = 65 条业务不变量**（C1–C18、T1–T8、S1–S12、W1–W7、R1–R20），
   每条都有实现位置与测试证据（R12「多默认允许」尤为重要——legacy 明确**没有**唯一默认约束）。
4. **发现 20 项实现缺陷**，其中 D2（零权限校验）、D3（零操作日志）、D4/D5（编码冲突映射脆弱）、
   D7（两条状态写路径）、D9（三张主数据表无删除能力）是硬性缺口。
5. **测试覆盖 11 类 42 用例**，`ProcurementOptimisticLockIT` 证明了 legacy 在真实 PostgreSQL 上乐观锁生效；
   W2 必须复用该形状并扩展到 customer / customer_type。
6. **两个建模冲突已辨析并给出处置**：客户类型保留 legacy 的可维护字典（C 的主体形态作种子）；
   商品-供应商关系保留 legacy 的 **SKU 级**（不采用 C 的商品级 + 供货价）。
7. **审计后新增 6 项必须人工拍板的缺口**（§8.1），Target Design 中已逐项给出推荐方案与理由。
