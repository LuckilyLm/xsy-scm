# W2 Customer + Supplier · Target Design（目标设计）

> 上游输入：`docs/architecture/2026-09-15-w2-customer-supplier-legacy-audit.md`、`2026-09-15-smartadmin-old-vs-v2-audit-workbuddy.md`
> 冻结基线：HEAD `95a54233586aa3c652e55cd833bb346d51f5a952`，branch `feature/sprint5`
> 写入边界：**仅根目录 V2 工作区**。`xsy-scm-server/**`、`xsy-scm-web/**` 为正式代码，
> `xsy-scm-miniapp/**` 与 `project-reference-examples/**` 只读。
> 设计日期：2026-09-15　状态：**已实施并验收；止于 W2**
>
> **修订记录**
> - `2026-09-15` 初版。
> - `2026-09-15` **前端迁移策略修订**（用户批准）：W2 起统一原则 **Frontend = Copy First + Adapt**；
>   重写 **§5**（新增 §5.0 迁移原则、§5.1 文件来源标记、§5.2 强制适配清单含 TS 严格化门禁）、
>   重写 **§6**（改为 C 前端资产迁移清单：可直接复制 / 复制后需改 / 不应复制 / 仍需新写）、
>   重订 **T13**（页面迁入 + 剪枝 + 适配，取代"重新实现 Vue 页面"）。
>   **§1–§4、§7–§12、§14 未改动**（后端仍为 Rebuild/Adapt，C 后端不作主干）。
>
> 已应用的 V6 / V7 不得回改；W2 的 schema 演进只能新增 `V8` / `V9`。
> W1 Product 的 Java 代码（`module/scm/product/**`）**不做任何修改**；W2 只新增 `module/scm/{customer,supplier}` 与
> `module/scm/common` 的共享件。

---

## 1. 范围（Scope）

### 1.1 做

**Customer**：客户档案 · 客户类型（可维护字典）· 联系人 · 地址 · 状态 · 账期基础字段 · 归属关系基础字段（上级集团 / 业务员 / 绑定供应商）· 列表 / 分页 / 搜索 / 详情 / 新增 / 编辑 / 状态 / 删除 · 下拉选择器。

**Supplier**：供应商档案 · 联系人 · 地址 · 状态 · 列表 / 分页 / 搜索 / 详情 / 新增 / 编辑 / 状态 / 删除 · 下拉选择器 · **Product-Supplier Relation（`supplier_sku`，SKU 级）** 的查询与整表替换维护。

**通用**：Flyway V8 / V9 · Entity / DAO / Manager / Service / Controller · SmartAdmin 菜单与权限（`scm:customer:*` / `scm:supplier:*`）· 单测 / PG 集成测试 / Web 层测试 · Vue3 页面 · TS 基线棘轮 · Playwright E2E。

### 1.2 不做

Agreement Price · Customer Type Price · Customer SKU Visibility · Customer Discount · Pricing Resolver ·
Customer 分级（`customer_level_id`）· 余额账户（`balance`）· 经纬度 · Sales Order · Purchase · Receiving ·
Inventory · Mall · MiniApp · `warehouse` 主数据 · `@DataScope` 业务员数据隔离 · 多联系人子表 · 商城收货地址。

### 1.3 交付物

| 类型 | 路径 |
|---|---|
| Migration | `V8__scm_customer_supplier.sql`、`V9__scm_customer_supplier_permissions.sql` |
| 后端 | `xsy-scm-server/sa-admin/**/module/scm/{common,customer,supplier}/**` |
| 前端 | `xsy-scm-web/src/{api,types,constants,components,views}/**/scm/**` |
| 质量基线 | `docs/quality/ts-baseline.json`、`tools/ts_baseline_ratchet.py`（Step 0 已交付） |
| 报告 | `docs/architecture/2026-09-15-w2-customer-supplier-验收报告.md` |

---

## 2. 目标架构

### 2.1 模块落点

沿用 W1 结论：SCM 业务模块位于 `sa-admin`，与 SmartAdmin `module/business/*` 平级，独立成 `module/scm`。

```
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/
├─ common/
│  ├─ constant/ScmCustomerStatusEnum.java        POTENTIAL / COOPERATING / SUSPENDED / BLACKLIST
│  ├─ constant/ScmSettleModeEnum.java            INDEPENDENT / GROUP
│  ├─ constant/ScmCreditPeriodTypeEnum.java      BY_AMOUNT / BY_TIME
│  ├─ constant/ScmCreditPeriodUnitEnum.java      DAY / MONTH
│  ├─ error/ScmCommonErrorCode.java              VERSION_CONFLICT(40921) 等跨域共享码
│  ├─ util/ScmDecimalStrings.java                4 位定点字符串解析与归一化（统一 legacy D13/D14）
│  ├─ constant/ScmEnableStatusEnum.java          已存在（W1 交付），W2 复用
│  ├─ constant/ScmOperator.java                  已存在（W1 交付），W2 复用
│  ├─ error/ScmErrorCode.java                    已存在（W1 交付）
│  ├─ exception/ScmBusinessException.java        已存在（W1 交付）
│  ├─ handler/ScmExceptionHandler.java           已存在（W1 交付）
│  └─ json/JsonbStringMapTypeHandler.java        已存在（W1 交付），W2 复用
├─ customer/
│  ├─ constant/CustomerErrorCode.java
│  ├─ controller/CustomerController.java
│  ├─ controller/CustomerTypeController.java
│  ├─ dao/CustomerDao.java                       @Mapper
│  ├─ dao/CustomerTypeDao.java                   @Mapper
│  ├─ domain/entity/CustomerEntity.java
│  ├─ domain/entity/CustomerTypeEntity.java
│  ├─ domain/form/CustomerQueryForm.java          继承 PageParam
│  ├─ domain/form/CustomerAddForm.java
│  ├─ domain/form/CustomerUpdateForm.java         继承 AddForm + {customerId, version}
│  ├─ domain/form/CustomerStatusForm.java         {customerId, version, status}
│  ├─ domain/form/CustomerDeleteForm.java         {customerId, version}
│  ├─ domain/form/CustomerTypeQueryForm.java      继承 PageParam
│  ├─ domain/form/CustomerTypeAddForm.java
│  ├─ domain/form/CustomerTypeUpdateForm.java     继承 AddForm + {typeId, version}
│  ├─ domain/form/CustomerTypeDeleteForm.java     {typeId, version}
│  ├─ domain/vo/CustomerVO.java                   列表行
│  ├─ domain/vo/CustomerDetailVO.java             详情
│  ├─ domain/vo/CustomerOptionVO.java             下拉
│  ├─ domain/vo/CustomerTypeVO.java               列表行 + 下拉
│  ├─ manager/CustomerValidator.java              账期组合 / 上级关系 / 归一化
│  └─ service/CustomerService.java                写
│  └─ service/CustomerQueryService.java           读
│  └─ service/CustomerTypeService.java            客户类型读写
└─ supplier/
   ├─ constant/SupplierErrorCode.java
   ├─ controller/SupplierController.java
   ├─ controller/SupplierSkuController.java
   ├─ dao/SupplierDao.java                         @Mapper
   ├─ dao/SupplierSkuDao.java                      @Mapper
   ├─ domain/entity/SupplierEntity.java
   ├─ domain/entity/SupplierSkuEntity.java
   ├─ domain/form/SupplierQueryForm.java           继承 PageParam
   ├─ domain/form/SupplierAddForm.java
   ├─ domain/form/SupplierUpdateForm.java          继承 AddForm + {supplierId, version}
   ├─ domain/form/SupplierStatusForm.java          {supplierId, version, status}
   ├─ domain/form/SupplierDeleteForm.java          {supplierId, version}
   ├─ domain/form/SupplierSkuQueryForm.java        继承 PageParam（分页只读视图）
   ├─ domain/form/SupplierSkuReplaceForm.java      {supplierId, items: List<SupplierSkuItemForm>}
   ├─ domain/form/SupplierSkuItemForm.java         {id?, version?, skuId, purchaseUnit, referencePrice, purchaserId?, defaultFlag, status?}
   ├─ domain/vo/SupplierVO.java                    列表行
   ├─ domain/vo/SupplierDetailVO.java              详情
   ├─ domain/vo/SupplierOptionVO.java              下拉
   ├─ domain/vo/SupplierSkuVO.java                 关联行
   ├─ manager/SupplierSkuChangeSet.java            差量计算结果（retain / insert / remove）
   ├─ manager/SupplierSkuSyncManager.java          差量落库
   ├─ manager/SupplierValidator.java               归一化 + 编码查重
   └─ service/SupplierService.java                 写
   └─ service/SupplierQueryService.java            读
   └─ service/SupplierSkuService.java              关联读写
```

Mapper XML：`sa-admin/src/main/resources/mapper/business/scm/customer/*.xml`、`.../scm/supplier/*.xml`。

### 2.2 分层职责（对齐 SmartAdmin ERP 范式与 W1）

| 层 | 职责 | 不做什么 |
|---|---|---|
| Controller | 权限注解、参数校验、调用 Service、返回 `ResponseDTO` | 不写业务规则 |
| Service | 事务边界、聚合编排、调用 Manager/DAO | 不拼 SQL |
| Manager | 单条业务规则（校验器、差量计算、差量落库） | 不控制事务 |
| DAO | 数据访问（`BaseMapper` + XML） | 不含业务判断 |

### 2.3 数据模型

```
customer_type (可维护字典)
        ▲ customer_type_id
customer ──self──▶ customer（parent_customer_id：上级集团，NULL = 独立客户）

supplier ──1:N──▶ supplier_sku ──▶ product_sku（W1 已存在）
                                  └─ purchaser_id ──▶ t_employee（SmartAdmin）
customer.seller_id / customer.supplier_id ──▶ t_employee / supplier
```

**边界说明**：
- `customer` 是 W2 的聚合根（无子表，联系人/地址/账期为内嵌字段，见 §3.2）。
- `supplier` 是 W2 的聚合根；`supplier_sku` 是**供应商侧的关联实体**，但它的对端 `product_sku` 属于 W1 Product 域。
  W2 **只读** `product_sku` / `product_spu`（通过 `ProductSkuDao.selectOrderableByIds`，W1 已交付），不写。
- `t_employee` 由 SmartAdmin 提供，W2 **只读**（`EmployeeDao.getEmployeeByIds` / `listAll`）。
- **不引入数据库外键**（遵循 `AGENTS.md` §8 与 W1 约定）。

---

## 3. Migration 设计

### 3.1 命名与数量约束

- 只允许**两个**迁移文件：`V8__scm_customer_supplier.sql`（全部 DDL + 客户类型种子）、
  `V9__scm_customer_supplier_permissions.sql`（菜单与权限种子）。不允许每表一个迁移。
- 表名沿用 legacy 词汇：`customer_type` / `customer` / `supplier` / `supplier_sku`。
  schema 为 `xsy_v2`，与 legacy `public` schema 隔离，无命名冲突；与 W1 的 `product_*` 无重叠。
- Flyway 是唯一 schema 演进机制；已应用的迁移不可修改。

### 3.2 `V8__scm_customer_supplier.sql`

**customer_type**（沿用 legacy DDL，补 `name` 索引）

```sql
CREATE TABLE customer_type (
    id         BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    type_code  VARCHAR(64)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    version    INTEGER      NOT NULL DEFAULT 0,
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT ck_customer_type_status  CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_customer_type_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_customer_type_code_active ON customer_type (type_code) WHERE deleted = FALSE;
CREATE INDEX idx_customer_type_name ON customer_type (name);
```

**customer**（legacy 7 列 + W2 新增 11 列）

```sql
CREATE TABLE customer (
    id                      BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_code           VARCHAR(64)   NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    customer_type_id        BIGINT        NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'POTENTIAL',
    parent_customer_id      BIGINT,
    seller_id               BIGINT,
    supplier_id             BIGINT,
    contact_name            VARCHAR(100),
    contact_phone           VARCHAR(32),
    address                 VARCHAR(255),
    settle_mode             VARCHAR(20)   NOT NULL DEFAULT 'INDEPENDENT',
    credit_limit            NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit_period_type      VARCHAR(20),
    credit_amount_threshold NUMERIC(18,4),
    credit_period_value     INTEGER,
    credit_period_unit      VARCHAR(10),
    settle_day              SMALLINT,
    remark                  VARCHAR(500),
    version                 INTEGER       NOT NULL DEFAULT 0,
    deleted                 BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64),
    updated_by              VARCHAR(64),
    CONSTRAINT ck_customer_status CHECK (
        status IN ('POTENTIAL','COOPERATING','SUSPENDED','BLACKLIST')),
    CONSTRAINT ck_customer_settle_mode CHECK (settle_mode IN ('INDEPENDENT','GROUP')),
    CONSTRAINT ck_customer_credit_limit CHECK (credit_limit >= 0),
    CONSTRAINT ck_customer_self_parent CHECK (
        parent_customer_id IS NULL OR parent_customer_id <> id),
    CONSTRAINT ck_customer_credit_period CHECK (
           (credit_period_type IS NULL
              AND credit_amount_threshold IS NULL
              AND credit_period_value IS NULL
              AND credit_period_unit IS NULL
              AND settle_day IS NULL)
        OR (credit_period_type = 'BY_AMOUNT'
              AND credit_amount_threshold IS NOT NULL
              AND credit_amount_threshold >= 0
              AND credit_period_value IS NULL
              AND credit_period_unit IS NULL
              AND settle_day IS NULL)
        OR (credit_period_type = 'BY_TIME'
              AND credit_amount_threshold IS NULL
              AND credit_period_value IS NOT NULL
              AND credit_period_value > 0
              AND credit_period_unit IN ('DAY','MONTH')
              AND (   (credit_period_unit = 'DAY'   AND settle_day IS NULL)
                   OR (credit_period_unit = 'MONTH'
                       AND (settle_day IS NULL OR settle_day BETWEEN 1 AND 28))))
    ),
    CONSTRAINT ck_customer_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_customer_code_active ON customer (customer_code) WHERE deleted = FALSE;
CREATE INDEX idx_customer_type_id         ON customer (customer_type_id);
CREATE INDEX idx_customer_name            ON customer (name);
CREATE INDEX idx_customer_status          ON customer (status);
CREATE INDEX idx_customer_parent_active   ON customer (parent_customer_id)
    WHERE deleted = FALSE AND parent_customer_id IS NOT NULL;
CREATE INDEX idx_customer_seller_active   ON customer (seller_id)
    WHERE deleted = FALSE AND seller_id IS NOT NULL;
CREATE INDEX idx_customer_supplier_active ON customer (supplier_id)
    WHERE deleted = FALSE AND supplier_id IS NOT NULL;
```

**supplier**（legacy 4 列 + V8 追加的 `remark` + W2 新增 3 列）

```sql
CREATE TABLE supplier (
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    supplier_code VARCHAR(64)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    contact_name  VARCHAR(100),
    contact_phone VARCHAR(32),
    address       VARCHAR(255),
    remark        VARCHAR(500),
    version       INTEGER      NOT NULL DEFAULT 0,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    CONSTRAINT ck_supplier_status  CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_supplier_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_supplier_code_active ON supplier (supplier_code) WHERE deleted = FALSE;
CREATE INDEX idx_supplier_name   ON supplier (name);
CREATE INDEX idx_supplier_status ON supplier (status);
```

**supplier_sku**（沿用 legacy V8 DDL，逐列一致）

```sql
CREATE TABLE supplier_sku (
    id                     BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    supplier_id            BIGINT        NOT NULL,
    sku_id                 BIGINT        NOT NULL,
    supplier_code_snapshot VARCHAR(64)   NOT NULL,
    supplier_name_snapshot VARCHAR(150)  NOT NULL,
    sku_code_snapshot      VARCHAR(64)   NOT NULL,
    sku_name_snapshot      VARCHAR(150)  NOT NULL,
    spec_values_snapshot   JSONB         NOT NULL DEFAULT '{}'::JSONB,
    purchase_unit          VARCHAR(32)   NOT NULL,
    reference_price        NUMERIC(18,4),
    purchaser_id           BIGINT,
    is_default             BOOLEAN       NOT NULL DEFAULT FALSE,
    status                 VARCHAR(16)   NOT NULL DEFAULT 'ENABLED',
    version                INTEGER       NOT NULL DEFAULT 0,
    deleted                BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(64),
    updated_by             VARCHAR(64),
    CONSTRAINT ck_supplier_sku_spec_values     CHECK (jsonb_typeof(spec_values_snapshot) = 'object'),
    CONSTRAINT ck_supplier_sku_reference_price CHECK (reference_price IS NULL OR reference_price >= 0),
    CONSTRAINT ck_supplier_sku_status          CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_supplier_sku_version         CHECK (version >= 0)
);
CREATE UNIQUE INDEX uk_supplier_sku_active ON supplier_sku (supplier_id, sku_id) WHERE deleted = FALSE;
CREATE INDEX idx_supplier_sku_sku_id    ON supplier_sku (sku_id) WHERE deleted = FALSE;
CREATE INDEX idx_supplier_sku_purchaser ON supplier_sku (purchaser_id)
    WHERE deleted = FALSE AND purchaser_id IS NOT NULL;
-- 刻意不建 (supplier_id) WHERE is_default = TRUE 的唯一索引。
-- legacy 明确允许同一供应商存在多条默认（R12），V2 不自造基数策略。
```

**客户类型种子**：吸收 C 的 `CustomerTypeEnum` 三种主体形态作为**字典数据**（辨析见 Legacy Audit §8.2 冲突 A）：

```sql
INSERT INTO customer_type(type_code,name,status) VALUES
  ('ENTERPRISE','企业','ENABLED'),
  ('PERSONAL','个人','ENABLED'),
  ('GROUP','集团','ENABLED');
```

**无数据库外键**。**无 demo 客户数据**（W1 亦未携带 legacy 演示商品）。

### 3.3 `V9__scm_customer_supplier_permissions.sql`

现有 `t_menu` 最大 `menu_id`：基础种子到 300，W1 占用 401–424。W2 使用 **431–482**（425–500 全段空闲，已核验）。

| menu_id | 名称 | type | parent | path | component | 权限码 |
|---|---|---|---|---|---|---|
| 431 | 客户管理 | 1 目录 | 0 | `/customer` | — | — |
| 432 | 客户档案 | 2 菜单 | 431 | `/customer/customer-list` | `/business/scm/customer/customer-list.vue` | — |
| 433 | 客户类型 | 2 菜单 | 431 | `/customer/customer-type-list` | `/business/scm/customer/customer-type-list.vue` | — |
| 434 | 客户详情 | 2 菜单（隐藏） | 432 | `/customer/customer-detail` | `/business/scm/customer/customer-detail.vue` | — |
| 441 | 查询 | 3 功能点 | 432 | — | — | `scm:customer:query` |
| 442 | 新建 | 3 | 432 | — | — | `scm:customer:add` |
| 443 | 编辑 | 3 | 432 | — | — | `scm:customer:update` |
| 444 | 状态 | 3 | 432 | — | — | `scm:customer:status` |
| 445 | 删除 | 3 | 432 | — | — | `scm:customer:delete` |
| 451 | 查询 | 3 | 433 | — | — | `scm:customer:type:query` |
| 452 | 新建 | 3 | 433 | — | — | `scm:customer:type:add` |
| 453 | 编辑 | 3 | 433 | — | — | `scm:customer:type:update` |
| 454 | 删除 | 3 | 433 | — | — | `scm:customer:type:delete` |
| 461 | 供应商管理 | 1 目录 | 0 | `/supplier` | — | — |
| 462 | 供应商档案 | 2 菜单 | 461 | `/supplier/supplier-list` | `/business/scm/supplier/supplier-list.vue` | — |
| 463 | 供应商详情 | 2 菜单（隐藏） | 462 | `/supplier/supplier-detail` | `/business/scm/supplier/supplier-detail.vue` | — |
| 464 | 商品-供应商关系 | 2 菜单 | 461 | `/supplier/supplier-sku-list` | `/business/scm/supplier/supplier-sku-list.vue` | — |
| 471 | 查询 | 3 | 462 | — | — | `scm:supplier:query` |
| 472 | 新建 | 3 | 462 | — | — | `scm:supplier:add` |
| 473 | 编辑 | 3 | 462 | — | — | `scm:supplier:update` |
| 474 | 状态 | 3 | 462 | — | — | `scm:supplier:status` |
| 475 | 删除 | 3 | 462 | — | — | `scm:supplier:delete` |
| 481 | 查询 | 3 | 464 | — | — | `scm:supplier:sku:query` |
| 482 | 维护 | 3 | 464 | — | — | `scm:supplier:sku:update` |

- 功能点行：`api_perms = web_perms = <权限码>`，`perms_type = 1`，`context_menu_id` 指向所属菜单。
- 434 / 463 详情菜单：`visible_flag = FALSE`（可深链、不在侧栏显示），与 W1 的 404 同法。
- 授权：`t_role_menu` 为 `role_id = 1` 插入全部 25 个 `menu_id`。
- 全部插入使用显式 `menu_id` + `INSERT ... ON CONFLICT (menu_id) DO NOTHING`，文件末尾
  `SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), false);`。

---

## 4. 后端设计

### 4.1 API 契约（统一 `/scm/**`，SmartAdmin 风格）

**客户档案**

| 方法 | 路径 | 请求体 | 响应 | 权限 |
|---|---|---|---|---|
| POST | `/scm/customer/query` | `CustomerQueryForm`（继承 `PageParam`） | `ResponseDTO<PageResult<CustomerVO>>` | `scm:customer:query` |
| GET | `/scm/customer/detail/{customerId}` | — | `ResponseDTO<CustomerDetailVO>` | `scm:customer:query` |
| POST | `/scm/customer/add` | `CustomerAddForm` | `ResponseDTO<Long>` | `scm:customer:add` |
| POST | `/scm/customer/update` | `CustomerUpdateForm` | `ResponseDTO<String>` | `scm:customer:update` |
| POST | `/scm/customer/updateStatus` | `CustomerStatusForm` | `ResponseDTO<String>` | `scm:customer:status` |
| POST | `/scm/customer/delete` | `CustomerDeleteForm` | `ResponseDTO<String>` | `scm:customer:delete` |
| POST | `/scm/customer/option/list` | `{}` | `ResponseDTO<List<CustomerOptionVO>>` | `scm:customer:query` |

**客户类型**

| 方法 | 路径 | 请求体 | 响应 | 权限 |
|---|---|---|---|---|
| POST | `/scm/customer/type/query` | `CustomerTypeQueryForm` | `ResponseDTO<PageResult<CustomerTypeVO>>` | `scm:customer:type:query` |
| POST | `/scm/customer/type/add` | `CustomerTypeAddForm` | `ResponseDTO<Long>` | `scm:customer:type:add` |
| POST | `/scm/customer/type/update` | `CustomerTypeUpdateForm` | `ResponseDTO<String>` | `scm:customer:type:update` |
| POST | `/scm/customer/type/delete` | `CustomerTypeDeleteForm` | `ResponseDTO<String>` | `scm:customer:type:delete` |
| POST | `/scm/customer/type/option/list` | `{}` | `ResponseDTO<List<CustomerTypeVO>>` | `scm:customer:type:query` |

**供应商档案**

| 方法 | 路径 | 请求体 | 响应 | 权限 |
|---|---|---|---|---|
| POST | `/scm/supplier/query` | `SupplierQueryForm`（继承 `PageParam`） | `ResponseDTO<PageResult<SupplierVO>>` | `scm:supplier:query` |
| GET | `/scm/supplier/detail/{supplierId}` | — | `ResponseDTO<SupplierDetailVO>` | `scm:supplier:query` |
| POST | `/scm/supplier/add` | `SupplierAddForm` | `ResponseDTO<Long>` | `scm:supplier:add` |
| POST | `/scm/supplier/update` | `SupplierUpdateForm` | `ResponseDTO<String>` | `scm:supplier:update` |
| POST | `/scm/supplier/updateStatus` | `SupplierStatusForm` | `ResponseDTO<String>` | `scm:supplier:status` |
| POST | `/scm/supplier/delete` | `SupplierDeleteForm` | `ResponseDTO<String>` | `scm:supplier:delete` |
| POST | `/scm/supplier/option/list` | `{}` | `ResponseDTO<List<SupplierOptionVO>>` | `scm:supplier:query` |

**商品-供应商关系（`supplier_sku`）**

| 方法 | 路径 | 请求体 | 响应 | 权限 |
|---|---|---|---|---|
| GET | `/scm/supplier/sku/list/{supplierId}` | — | `ResponseDTO<List<SupplierSkuVO>>` | `scm:supplier:sku:query` |
| POST | `/scm/supplier/sku/query` | `SupplierSkuQueryForm` | `ResponseDTO<PageResult<SupplierSkuVO>>` | `scm:supplier:sku:query` |
| POST | `/scm/supplier/sku/replace` | `SupplierSkuReplaceForm` | `ResponseDTO<String>` | `scm:supplier:sku:update` |

设计要点：

1. **写路径唯一**：`supplier_sku` 只有 `replace` 一个写入口（保留 legacy 的整表替换语义 R5），
   不做行级 add/update/delete 端点，避免两套规则漂移。
   `list/{supplierId}` 供替换编辑页回填；`query` 分页供"按 SKU 反查供应商"的只读视图（W2 只读）。
2. **删除与改状态都通过 body 传 `{id, version}`**，统一乐观锁入口（修正 legacy 的 4 种不一致形态）。
3. 所有写接口加 `@OperateLog`（修正 legacy D3）。
4. 所有接口加 `@SaCheckPermission`（修正 legacy D2）。
5. 列表用 POST + 表单体，与 SmartAdmin ERP 及 W1 一致，便于 `PageParam.sortItemList` 传递。

### 4.2 关键 VO 字段

`CustomerVO`（列表行）：
`customerId, version, customerCode, name, customerTypeId, customerTypeName, status, settleMode,
creditLimit(String), contactName, contactPhone, sellerId, sellerName, parentCustomerId,
parentCustomerName, updatedAt`

`CustomerDetailVO`：
`customerId, version, customerCode, name, customerTypeId, customerTypeName, status, settleMode,
creditLimit(String), creditPeriodType, creditAmountThreshold(String), creditPeriodValue,
creditPeriodUnit, settleDay, contactName, contactPhone, address, parentCustomerId,
parentCustomerName, sellerId, sellerName, supplierId, supplierName, remark, createdAt, updatedAt`

`CustomerOptionVO`：`customerId, customerCode, name, status`

`CustomerTypeVO`：`typeId, version, typeCode, name, status, createdAt`

`SupplierVO`（列表行）：
`supplierId, version, supplierCode, name, status, contactName, contactPhone, skuCount, updatedAt`

`SupplierDetailVO`：`supplierId, version, supplierCode, name, status, contactName, contactPhone, address, remark, skuCount, createdAt, updatedAt`

`SupplierOptionVO`：`supplierId, supplierCode, name`

`SupplierSkuVO`：
`id, version, supplierId, skuId, supplierCodeSnapshot, supplierNameSnapshot, skuCodeSnapshot,
skuNameSnapshot, specValuesSnapshot(Map<String,String>), purchaseUnit, referencePrice(String),
purchaserId, purchaserName, defaultFlag, status, updatedAt`

**定点数契约**：`creditLimit` / `creditAmountThreshold` / `referencePrice` 一律为 **4 位小数字符串**，
由 `ScmFixedScale4Serializer`（`setScale(4, HALF_UP)`）序列化，且**必须同时声明 `using` 与 `nullsUsing`**，
`null` 严格保持 `null` 与 `"0.0000"` 区分（修正 legacy D14）。请求侧用 `ScmDecimalStrings.parseScale4`
统一解析（正则 `^\d{1,14}(\.\d{1,4})?$`，数字字面量拒绝），修正 legacy D13 的双重规则。

### 4.3 Legacy 不变量 → V2 实现映射

**客户（C1–C18）**

| # | 不变量 | V2 实现 |
|---|---|---|
| C1 | 客户编码唯一（活动记录内） | DB `uk_customer_code_active` + Service 显式查重 → `CUSTOMER_CODE_DUPLICATE(40936)`（修正 D4/D5） |
| C2 | 客户类型必须存在、未删除且 ENABLED | `CustomerTypeService.requireSelectableType(typeId)` → 40431 |
| C3 | 客户必须存在且未删除 | `CustomerService.require(customerId)` → 40430 |
| C4 | 状态不可交易则不能进入交易链 | `ScmCustomerStatusEnum.tradable()` 唯一判定点（仅 `COOPERATING`）；`requireTradable` → 40930 |
| C5 | 更新必须携带 version | `CustomerUpdateForm.version` `@NotNull @Min(0)` |
| C6 | 影响行数 ≠ 1 即冲突 | `dao.updateById(entity) != 1` → `ScmCommonErrorCode.VERSION_CONFLICT(40921)` |
| C7 | 状态变更走独立端点 | `POST /scm/customer/updateStatus`；`CustomerUpdateForm` **不含 status**（修正 D7） |
| C8 | 删除是原子 id + version 谓词的软删 | `CustomerDao.xml` 的 `softDelete(customerId, version)` 返回 0 → 40921 |
| C9 | 删除前的引用检查 | W2 无下游引用；`CustomerService.delete` 预留 `CUSTOMER_REFERENCED(40939)` 检查位（当前恒通过，W3 接定价后启用） |
| C10 | 删除后编码可复用 | partial unique `WHERE deleted = FALSE` |
| C11 | 状态取值域 | DB CHECK + `ScmCustomerStatusEnum` + DTO `@Pattern` |
| C12 | 分页边界 | `CustomerQueryForm` 覆写 `getPageNum() @Min(1)`、`getPageSize() @Min(1) @Max(100)` |
| C13 | 关键字覆盖编码 + 名称 | `CustomerDao.xml`：`customer_code ILIKE` / `name ILIKE`（**增强**：追加 `contact_name` / `contact_phone`，见 Q8） |
| C14 | 客户类型精确匹配 | `customer_type_id = #{...}` |
| C15 | 默认排序 | `updated_at DESC, id DESC`；`sortItemList` 走**白名单**（`customer_code` / `name` / `status` / `updated_at`），修正 D18 |
| C16 | 列表不返回明细、详情返回全部 | `CustomerVO` vs `CustomerDetailVO` 两个 VO |
| C17 | 不 N+1 | 批量：`customerTypeDao.selectBatchIds`、`customerDao.selectBatchIds`（上级客户名）、`employeeDao.getEmployeeByIds`（业务员名） |
| C18 | 版本冲突统一码 | `ScmCommonErrorCode.VERSION_CONFLICT(40921)`（与 W1 同码，前端已可映射） |

**客户类型（T1–T8）**

| # | 不变量 | V2 实现 |
|---|---|---|
| T1 | `type_code` 唯一（活动记录内） | DB partial unique + 显式查重 → `CUSTOMER_TYPE_CODE_DUPLICATE(40937)` |
| T2 | `name` 不唯一 | 不建唯一索引；`idx_customer_type_name` 仅加速排序/搜索 |
| T3 | 列表按 name 排序 | 分页默认 `ORDER BY name ASC, id ASC`；`sortItemList` 白名单 `type_code`/`name`/`status`/`updated_at` |
| T4 | 更新必须携带 version | `CustomerTypeUpdateForm.version` `@NotNull @Min(0)` |
| T5 | 影响行数 ≠ 1 即 40921 | `dao.updateById != 1` |
| T6 | **无删除端点** → W2 **新增** | `POST /scm/customer/type/delete`；删除前检查活动客户引用 → `CUSTOMER_TYPE_IN_USE(40938)`（Q3） |
| T7 | 创建初值 | `version=0, deleted=false, createdAt=updatedAt=now(), createdBy=ScmOperator.current()` |
| T8 | 状态随更新体带入（无独立状态端点） | `CustomerTypeAddForm.status` `@NotNull @Pattern`，update 沿用同一字段 |

**供应商（S1–S12）**

| # | 不变量 | V2 实现 |
|---|---|---|
| S1 | 供应商编码唯一 | DB partial unique + 显式查重 → `SUPPLIER_CODE_DUPLICATE(40944)` |
| S2 | 存在且未删除 → 40440 | `SupplierService.require(supplierId)` |
| S3 | 停用不能用于关联 → 40940 | `SupplierService.requireEnabled(supplierId)` |
| S4 | 关联写入前**先锁 supplier 行** | `SupplierDao.xml` `selectActiveByIdForUpdate` + `SupplierSkuDao.xml` `selectActiveBySupplierIdForUpdate`，**锁序不变** |
| S5 | 更新带 version；冲突 40921 | `SupplierUpdateForm.version`；`dao.updateById != 1` |
| S6 | **更新不改状态** | `SupplierUpdateForm` **不含 status**；`apply()` 不触碰 `status` |
| S7 | 创建强制 `ENABLED` | `SupplierAddForm` **不含 status**；`SupplierService.add` 显式 `setStatus("ENABLED")` |
| S8 | 状态走独立端点 | `POST /scm/supplier/updateStatus` |
| S9 | **无删除端点** → W2 **新增** | `POST /scm/supplier/delete`；删除前检查活动 `supplier_sku` 引用 → `SUPPLIER_IN_USE(40947)`（Q4） |
| S10 | 编码冲突专属码 | Service 显式查重 → 40944（不再依赖约束名匹配） |
| S11 | 管理列表返回全部状态；下拉只返回 ENABLED | `POST /scm/supplier/query`（全部）+ `POST /scm/supplier/option/list`（`status = 'ENABLED'`） |
| S12 | 全量按 name 排序 | 分页默认 `ORDER BY name ASC, id ASC` |

**商品-供应商关系（R1–R20）**

| # | 不变量 | V2 实现 |
|---|---|---|
| R1 | `(supplier_id, sku_id)` 活动唯一 | DB `uk_supplier_sku_active` + `SupplierSkuChangeSet` 内重复检测（修正 D16） |
| R2 | 先锁 supplier，再锁 supplier_sku | `SupplierSkuSyncManager.replace`：`requireEnabledSupplierForUpdate` → `selectActiveBySupplierIdForUpdate`（顺序固定，与 legacy 一致） |
| R3 | SKU 必须可下单（SPU 与 SKU 均 ON_SHELF） | `ProductSkuDao.selectOrderableByIds(List.of(skuId))`（W1 已交付），空 → `SKU_DISABLED(40942)` |
| R4 | 快照字段冻结 | `buildSnapshot(supplier, sku)`：编码/名称/`productName`/`specValues`（null → `{}`）；**取 `productName` 而非 `specName`** |
| R5 | 整表替换 = 差量同步 | `SupplierSkuChangeSet.between(existing, requested)` → `retained` / `inserted` / `removedIds` |
| R6 | 先全部校验，再统一写 | `SupplierSkuSyncManager.replace` 两段式：第一段只构造 `Planned` 列表并做全部校验；第二段才落库 |
| R7 | 请求内 supplierId 一致 + skuId 不重复 → 40943 | `SupplierSkuChangeSet` 前置校验（`SupplierSkuReplaceForm.supplierId` 与路径/表单一致） |
| R8 | 带 id 的行必须归属该供应商且 skuId 不变 → 40943 | `between` 内 `retained` 匹配失败 → 40943 |
| R9 | 带 id 的行 version 必须一致 → 40921 | `between` 内 `Objects.equals(existing.version, requested.version)` 失败 → 40921 |
| R10 | 无 id 但 `(supplierId, skuId)` 已存在 → 复用该行 | `existingBySkuId` 兜底匹配 |
| R11 | 空数组 = 清空全部 | `items.isEmpty()` → 全部进 `removedIds`，按行 version 软删 |
| **R12** | **允许同一供应商多条 `is_default = TRUE`** | **不建**唯一索引、**不写**校验（`SupplierSkuValidatorTest` 显式断言该行为，防止被"顺手修复"） |
| R13 | 参考价 4 位小数**字符串** | `SupplierSkuItemForm.referencePrice` 为 `String` + `@Pattern("^\\d{1,14}(\\.\\d{1,4})?$")` + `ScmDecimalStrings.parseScale4` |
| R14 | 非 MVC 入口解析失败 → 40000 | `ScmDecimalStrings.parseScale4` 抛 `ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR)` |
| R15 | 单行删除用原子 supplier_id + id + version 谓词 | `SupplierSkuDao.xml` `softDeleteOwnedWithVersion`，返回 0 → 40921 |
| R16 | "可采购"四表联动 + 排序 | W2 **不实现查询端点**，但保留 `status` 列、`is_default` 列与语义；`SupplierSkuDao` 提供 `selectEnabledBySkuId`（四表 JOIN，`ORDER BY is_default DESC, id`）供 W3 直接复用 |
| R17 | `requireEnabledForPurchasing` 双查（不旁路主数据） | W2 提供 `SupplierSkuService.requireEnabledForPurchasing(supplierId, skuId)`，W3 调用 |
| R18 | `purchaserId` 必须为正 | `@Positive Long purchaserId` + 存在性校验（`EmployeeDao.selectById`）→ `SUPPLIER_SKU_PURCHASER_INVALID(40040)` |
| R19 | `purchaseUnit` 必填 ≤ 32 | `@NotBlank @Size(max=32)` |
| R20 | `status` 缺省 `ENABLED` | `SupplierSkuItemForm.status` 可空，null → `ENABLED` |

### 4.4 错误码

**`ScmCommonErrorCode`（W2 新增；W1 的 `ProductErrorCode` 保持不变，见 §4.5）**

| 码 | 语义 |
|---|---|
| 40921 | 数据已被其他操作修改，请刷新后重试 |
| 40000 | 请求参数不正确（非 MVC 入口的解析失败复用） |

**`CustomerErrorCode`**

| 码 | 语义 | 来源 |
|---|---|---|
| 40430 | 客户不存在 | legacy |
| 40431 | 客户类型不存在 | legacy |
| 40930 | 客户状态不可交易 | legacy 40930（语义由"未启用"扩展为"状态不可交易"） |
| 40936 | 客户编码已存在 | **新增**（修正 D5） |
| 40937 | 客户类型编码已存在 | **新增**（修正 D5） |
| 40938 | 客户类型已被客户引用，不能删除 | **新增**（W2 新能力） |
| 40939 | 客户已被业务数据引用，不能删除 | **新增**（W2 预留，W3 启用） |
| 40032 | 上级客户不正确（自身 / 环形 / 类型不是集团） | **新增**（避开 W3 将使用的 40030/40031） |

**`SupplierErrorCode`**

| 码 | 语义 | 来源 |
|---|---|---|
| 40440 | 供应商不存在 | legacy |
| 40442 | 供应商 SKU 配置不存在 | legacy |
| 40940 | 供应商未启用 | legacy |
| 40941 | **弃用**（版本冲突统一为 40921） | legacy 码值保留说明 |
| 40942 | SKU 未启用或不存在 | legacy |
| 40943 | 供应商 SKU 配置重复 | legacy |
| 40944 | 供应商编码已存在 | legacy 码值，改为 Service 显式抛出 |
| 40946 | 供应商 SKU 配置已存在 | legacy 码值，保留为 DB 唯一索引的并发兜底映射 |
| 40947 | 供应商已被商品关联引用，不能删除 | **新增**（W2 新能力） |
| 40040 | 默认采购员不正确 | **新增** |

**HTTP 状态约定**：沿用 W1 Q1 的已批准决议——**HTTP 200 + `ResponseDTO.code`**。
`ScmExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）返回 `ResponseDTO<Void>`，不带 `@ResponseStatus`，
因此业务错误一律 HTTP 200。前端 `lib/axios.ts` 只在 2xx 响应体上展示 `code`/`msg`，沿用 HTTP 409 会退化为通用网络错误提示。

### 4.5 关于 40921 与 W1 `ProductErrorCode` 的关系

W1 已在 `ProductErrorCode` 中定义 `VERSION_CONFLICT(40921)`。W2 **不修改 W1 代码**（用户要求"不覆盖 W1 Product"），
因此新增 `ScmCommonErrorCode.VERSION_CONFLICT(40921)`。两个枚举携带**相同的码值**，
对前端与 `t_operate_log` 无差异。

> **已知轻微技术债**：同一码值在两个枚举中各定义一次。建议在 W3 开始前做一次纯 Java 重构
> （`ProductErrorCode.VERSION_CONFLICT` 指向 `ScmCommonErrorCode.VERSION_CONFLICT`），
> 该重构不涉及 migration、不改变任何对外行为。本轮不做。

### 4.6 事务与并发顺序

**`CustomerService.update`**（修正 legacy 无锁序问题）

1. `validator.validate(form)`（账期组合、上级关系、字段长度、归一化）
2. `customerTypeService.requireSelectableType(form.getCustomerTypeId())` → 40431
3. `require(form.getCustomerId(), form.getVersion())`：`selectById` → null 时 40430；`version` 不一致 → 40921
4. 上级关系校验：`parentCustomerId` 非空时 → 不得等于自身（40032）；上级必须存在（40430）且其 `customer_type` 必须是 `GROUP`（40032）；沿 `parent_customer_id` 上溯**检测环**（40032）
5. 编码变更时显式查重：`existsCode(code, excludeId)` → 40936
6. `apply(entity, form)` + `stamp(entity)`
7. `dao.updateById(entity) != 1` → 40921

**`CustomerService.delete`**（保留 legacy 的原子谓词语义）

1. `require(customerId, version)`
2. 引用检查（W2 恒通过，W3 启用）→ 40939
3. `dao.softDelete(customerId, version) != 1` → 40921

**`SupplierSkuSyncManager.replace`**（**逐条保留 legacy 的四段式**）

1. **校验段 A（请求内一致性）**：`items` 内 `skuId` 不得重复 → 40943；
   `SupplierSkuReplaceForm.supplierId` 必须等于路径 `supplierId` → 40943
2. **锁段**：`supplierDao.selectActiveByIdForUpdate(supplierId)` → null → 40440；`status != ENABLED` → 40940
3. **锁段**：`supplierSkuDao.selectActiveBySupplierIdForUpdate(supplierId)`（`ORDER BY id FOR UPDATE`）
4. **校验段 B（逐条）**：构造 `Planned` 列表，对每条：
   - 命中 `existingById`（带 id）或 `existingBySkuId`（无 id）
   - 带 id 且未命中 / `skuId` 与库中不一致 → 40943
   - 命中但 `version` 不一致 → 40921
   - `purchaserId` 非空时 `EmployeeDao.selectById` 不存在 → 40040
   - `ProductSkuDao.selectOrderableByIds(List.of(skuId))` 为空 → 40942
   - 构造快照（`supplier_code/name`、`sku_code`、`sku_name = productName`、`spec_values`，null → `{}`）
5. **写段**（校验段 A/B 全部通过后才开始）：
   - `retained` → 逐条 `updateById`（带 `@Version`），≠ 1 → 40921
   - `inserted` → 逐条 `insert`
   - `removedIds` → 逐条 `softDeleteOwnedWithVersion(supplierId, id, version)`，≠ 1 → 40921
6. 任一步失败整体回滚；`@OperateLog` 由 AOP 记录

**`SupplierService.update`**（保留 S6）

1. `validator.validate(form)`
2. `require(supplierId, version)`
3. 编码变更时显式查重 → 40944
4. `apply(entity, form)` —— **不触碰 `status`**
5. `dao.updateById(entity) != 1` → 40921

### 4.7 查询实现要点

**`CustomerDao.xml` `queryPage`**
- 基础条件 `c.deleted = FALSE`
- `keyword`：`c.customer_code ILIKE '%'||?||'%' OR c.name ILIKE ... OR c.contact_name ILIKE ... OR c.contact_phone ILIKE ...`
- `customerTypeId`：精确 `=`
- `status`：`= `（多选时 `IN`）
- `settleMode`：`= `
- `parentCustomerId`：`= `（查下属单位）
- 排序默认 `c.updated_at DESC, c.id DESC`；`sortItemList` 白名单 `customer_code` / `name` / `status` / `updated_at`
- 补全（避免 N+1）：`customerTypeDao.selectBatchIds(distinct typeIds)`（**显式判空跳过**，修正 legacy D15）、
  `customerDao.selectBatchIds(distinct parentIds)`、`employeeDao.getEmployeeByIds(distinct sellerIds)`

**`CustomerTypeDao.xml` `queryPage`**
- `deleted = FALSE`；`keyword` → `type_code ILIKE / name ILIKE`；`status` → `= `
- 默认排序 `name ASC, id ASC`；白名单 `type_code` / `name` / `status` / `updated_at`
- `option/list`：`deleted = FALSE AND status = 'ENABLED' ORDER BY name ASC, id ASC`

**`SupplierDao.xml` `queryPage`**
- `deleted = FALSE`；`keyword` → `supplier_code ILIKE / name ILIKE / contact_name ILIKE / contact_phone ILIKE`；`status` → `= `
- 默认排序 `name ASC, id ASC`；白名单 `supplier_code` / `name` / `status` / `updated_at`
- 补 `skuCount`：`SELECT supplier_id, COUNT(*) FROM supplier_sku WHERE deleted = FALSE AND supplier_id IN (...) GROUP BY supplier_id`（一次查询，不 N+1）
- `option/list`：`deleted = FALSE AND status = 'ENABLED' ORDER BY name ASC, id ASC`

**`SupplierSkuDao.xml`**
- `selectActiveBySupplierId`：`WHERE supplier_id = ? AND deleted = FALSE ORDER BY id`
- `selectActiveBySupplierIdForUpdate`：同上 + `FOR UPDATE`
- `selectEnabledBySkuId`：四表 JOIN（保留 legacy 形状，W3 复用），`ORDER BY ss.is_default DESC, ss.id`
- `softDeleteOwnedWithVersion`：原子 `supplier_id + id + version` 谓词
- `queryPage`（只读反查）：`deleted = FALSE` + `supplierId` / `skuId` / `status` 过滤，
  默认 `ORDER BY is_default DESC, ss.id ASC`
- `countActiveBySupplierId`：删除引用检查用

---

## 5. 前端设计（Copy First + Adapt）

### 5.0 迁移原则（W2 起生效，取代"参考版式重新实现"）

**Frontend = Copy First + Adapt。Backend = Rebuild/Adapt according to Legacy + V2 rules。**

| 项 | 规则 |
|---|---|
| 允许复制 | `project-reference-examples/xsy-scm/xsy-scm-web/src/**` 中**真实存在**且落在 W2 业务范围内的 SCM Vue 页面 / 组件 / 常量 / API 形状，允许复制源码到 `xsy-scm-web` 作为实现起点 |
| 禁止行为 | **不再重新生成同功能 Vue 页面**。统一动作：复制 → 剪枝 → 适配 → 补测试 |
| 重点复用 | Vue 页面结构 · Ant Design Vue 布局 · Smart query form · Table · Drawer / Modal · `TableOperator` · Form 字段排列 · 查询条件 · 操作按钮 · 状态展示 · Select 组件结构 |
| 视觉目标 | 页面最终视觉保持 C 已运行系统的 SmartAdmin 风格（`smart-query-form` / `a-card` / `smart-table-btn-block` / `a-drawer` 底栏 / `smart-query-table-page`） |
| 后端边界 | C 的 `Service` / `Manager` / `Dao` / `Entity` / DDL **不因前端可复制而复制**，仍按 §4 与 §8 重建；C 后端只作 Controller / Form / VO / 流程组织参考 |
| W1 边界 | `module/scm/product/**` 已验收，**不覆盖**；C 的 Product 页面只允许**选择性吸收更好的 UI / 交互** |

**绝不复制**（继续 100% 使用 V2 SmartAdmin v3.31）：

```text
layout/**                                  login/**
system/**（user / role / dept / menu / dict / log / online / position）
router core（router/index.ts、router/routers.ts、router/{support,system}/**）
permission framework（directives/privilege.ts、plugins/privilege-plugin.ts、store/modules/system/user/**）
request framework（lib/axios/**、lib/smart-sentry.ts、api/system/**、拦截器）
SmartAdmin common/base（lib/**、utils/**、components/framework/**、components/support/**、theme/**、plugins/**）
constants/index.ts 的既有内容、constants/system/**、constants/support/**
system menu seed、system user / role / dept 种子
```

### 5.1 文件清单（含来源标记）

图例：`C复制` = 从 C 复制源码后适配；`W1派生` = 从 V2 W1 同型文件派生；`新写` = 无同型资产；`复用` = 直接用 V2 原生，不复制。

**API 层**

| 文件 | 来源 | 说明 |
|---|---|---|
| `src/api/business/scm/customer-api.ts` | **C复制** | 起点 = C `api/business/customer/customer-api.ts`（端点命名与调用形状）；URL 改 `/scm/customer/*`，补 `version` 形参 |
| `src/api/business/scm/customer-type-api.ts` | 新写 | C 无客户类型页；形状对齐 V2 W1 `api/business/scm/product-category-api.ts` |
| `src/api/business/scm/supplier-api.ts` | **C复制** | 起点 = C `api/business/purchase/supplier-api.ts` |
| `src/api/business/scm/supplier-sku-api.ts` | 新写 | C `product-supplier-api.ts` 是 **SPU 级** `/product/supplier/*`，与 V2 **SKU 级** `/scm/supplier/sku/*` 语义不符，只借命名风格 |

**类型与常量**

| 文件 | 来源 | 说明 |
|---|---|---|
| `src/types/business/scm/customer.d.ts` | 新写 | V2 contract；对齐 W1 `product.d.ts` |
| `src/types/business/scm/supplier.d.ts` | 新写 | 同上 |
| `src/constants/business/scm/customer-const.ts` | **C复制** | C 的 `{ value, desc }` 结构与 V2 `SmartEnum<T>` **同构**；取值由数字改 V2 字符串码 |
| `src/constants/business/scm/supplier-const.ts` | 新写 | C `supplier-const.ts` 全是**供应商协同**枚举（账号 / 报品 / 厂商 / 对账），与主数据无关 |
| `src/constants/index.ts`（**追加 2 行**） | 适配 | 把 SCM 枚举注册进 SmartEnum 插件（`main.ts` 的 `constantsInfo`）；不改既有内容 |
| `src/constants/support/table-id-const.ts`（**追加**） | 适配 | `SCM_CUSTOMER` / `SCM_CUSTOMER_TYPE` / `SCM_SUPPLIER` / `SCM_SUPPLIER_SKU`，沿用 W1 的扁平编号风格（`SCM_PRODUCT: 50001` 起） |

**选择器组件**

| 文件 | 来源 | 说明 |
|---|---|---|
| `src/components/business/scm/customer-select/index.vue` | **C复制** | 起点 = C `components/business/customer-select/index.vue`（69 行，结构近零改动） |
| `src/components/business/scm/supplier-select/index.vue` | **C复制** | 起点 = C `components/business/supplier-select/index.vue`（75 行） |
| `src/components/business/scm/sku-select/index.vue` | **C复制** | 起点 = C `components/business/sku-select/index.vue`（69 行）；`queryAll` 改 W1 `product-api` 且只列 `ON_SHELF` |
| `src/components/business/scm/customer-type-select/index.vue` | 新写 | C 无（C 用硬编码枚举）；结构照抄 `supplier-select` |
| 业务员 / 采购员选择器 | **复用** | 直接用 V2 原生 `src/components/system/employee-select/index.vue`，**不复制、不新写** |

**客户视图**

| 文件 | 来源 | 起点 |
|---|---|---|
| `src/views/business/scm/customer/customer-list.vue` | **C复制** | C `views/business/customer/customer-list.vue`（376 行） |
| `src/views/business/scm/customer/customer-type-list.vue` | W1派生 | V2 W1 `product/category-list.vue`（同为简单字典表 CRUD） |
| `src/views/business/scm/customer/customer-detail.vue` | W1派生 | V2 W1 `product/product-detail.vue` |
| `src/views/business/scm/customer/components/customer-form-drawer.vue` | **C复制** | C 抽屉**就地内联**在 list 内，此处为**纯位移**抽取 |
| `src/views/business/scm/customer/components/customer-type-form-modal.vue` | W1派生 | V2 W1 `product/components/category-form-modal.vue` |
| `src/views/business/scm/customer/customer-form-model.ts` | 新写 | 对齐 V2 W1 `product-form-model.ts`（可 `node:test` 单测） |

**供应商视图**

| 文件 | 来源 | 起点 |
|---|---|---|
| `src/views/business/scm/supplier/supplier-list.vue` | **C复制** | C `views/business/purchase/supplier-list.vue`（328 行） |
| `src/views/business/scm/supplier/supplier-detail.vue` | W1派生 | V2 W1 `product/product-detail.vue` |
| `src/views/business/scm/supplier/supplier-sku-list.vue` | **C复制骨架** | C `views/business/product/product-supplier-list.vue`（334 行）+ 大幅剪枝为只读 |
| `src/views/business/scm/supplier/components/supplier-form-drawer.vue` | **C复制** | 内联抽屉位移抽取 |
| `src/views/business/scm/supplier/components/supplier-sku-editable-table.vue` | W1派生 | V2 W1 `product/components/product-sku-editable-table.vue`（同为差量提交可编辑表格） |
| `src/views/business/scm/supplier/supplier-form-model.ts` | 新写 | 对齐 V2 W1 `product-form-model.ts` |

**测试**

| 文件 | 来源 |
|---|---|
| `test/customer-form-model.test.mjs` | W1派生（`test/product-form-model.test.mjs`） |
| `test/supplier-form-model.test.mjs` | W1派生 |
| `e2e/scm-customer.spec.ts` | W1派生（`e2e/scm-product.spec.ts`） |
| `e2e/scm-supplier.spec.ts` | W1派生 |

### 5.2 复制后强制适配清单（逐项门禁）

| # | 类别 | C 原样 | V2 改为 |
|---|---|---|---|
| 1 | API 路径 | `/customer/query`、`/supplier/query` | `/scm/customer/query`、`/scm/supplier/query` |
| 2 | HTTP 形状 | `GET /customer/delete/{customerId}` | `POST /scm/customer/delete` + body `{ id, version }` |
| 3 | DTO 字段名 | `customerId` / `customerNo` / `customerName` / `customerType` / `creditAmount` / `createTime` | `id` / `customerCode` / `name` / `customerTypeId` / `creditLimit` / `createdAt` |
| 4 | DTO 字段名 | `supplierId` / `supplierNo` / `supplierName` | `id` / `supplierCode` / `name` |
| 5 | DTO 字段名 | `defaultFlag` / `supplyPrice` / `specName` / `skuNo` | `isDefault` / `referencePrice` / V2 `product.d.ts` 的 SKU 字段名 |
| 6 | 权限码 | `customer:query`、`supplier:batchDelete` | `scm:customer:query`、`scm:supplier:query` …（批量删除权限**不存在**） |
| 7 | 枚举取值 | `CUSTOMER_STATUS_ENUM` = `1/2/3/4` | 同组件保留，取值改 `'POTENTIAL'/'COOPERATING'/'SUSPENDED'/'BLACKLIST'` |
| 8 | 枚举归属 | `CUSTOMER_TYPE_ENUM` 前端硬编码 | **删除前端枚举**，改 `customer-type-select`（数据源 `customer_type` 表） |
| 9 | 枚举归属 | `SUPPLIER_STATUS_ENUM` 放在 `constants/business/purchase/purchase-const.ts` | 移到 `constants/business/scm/supplier-const.ts` |
| 10 | 枚举注册 | C 未注册到全局 | 注册进 `constants/index.ts` → SmartEnum 插件（`SmartEnumSelect enum-name=` 才能取到值） |
| 11 | 选择器 | `a-input-number` 手填业务员 / 上级客户 / 供应商 ID | `employee-select` / `customer-select` / `supplier-select` |
| 12 | 乐观锁 | 编辑 / 状态 / 删除**无** `version` | 一律带 `version`；`40921` → 提示 + 自动重载 |
| 13 | 路由菜单 | `component: 'customer/customer/customer-list.vue'`（与真实文件不符） | `V9` 写 `/business/scm/customer/customer-list.vue` 等 6 条，与 `src/views/**` 实际路径一一对应 |
| 14 | 加载态 | `a-table` **缺 `:loading="tableLoading"`**（`tableLoading` 成死状态） | 补 `:loading` |
| 15 | 删除交互 | `Modal.confirm` | 与 W1 统一为 `a-popconfirm` |

**16. TypeScript 严格化（硬门禁，`scm` 必须 0 error）**

V2 与 C 的 `tsconfig.json` **逐字节相同**（`strict: true`、`noUnusedLocals: true`、`noUnusedParameters: true`），
但 **C 从未跑过 `vue-tsc --noEmit`**，其页面在同配置下必然报错。已逐处定位（以 `customer-list.vue` 为例，其余页面同型）：

| C 位置 | 报错 | 适配 |
|---|---|---|
| `function handleResizeColumn(w, col)` | TS7006 ×2 | 删除整个函数（见 #17） |
| `function onSelectChange(selectedRowKeys)` | TS7006 | 删除（Q14 不做批量删除） |
| `function addOrUpdate(rowData)` / `deleteOne(record)` / `doDeleteOne(record)` | TS7006 ×3 | 显式标注对应行 DTO 类型 |
| `item.dragAndDropFlag = true` | TS2339 | 删除（`dragAndDropFlag` 仅 C 存在） |
| `const doc = document.querySelector('.ant-form')` … `doc.offsetHeight` / `box.offsetHeight` | TS18047 ×5 | 加非空守卫，或改用 `:scroll="{ y: ... }"` |
| `const tableData = ref([])` → `tableData.value = res.data.list` | TS2322 | 改 `ref<CustomerRow[]>([])`，类型从 `types/business/scm/customer.d.ts` 引入 |
| `const columns = ref([...])` 无类型（`fixed: 'right'` 被推成 `string`） | TS2322 | 标注 `TableColumnsType<CustomerRow>`（对齐 W1 `product-list.vue`） |
| 选择器内 `onChange(value)`、`customerList = ref([])` | TS7006 / TS2322 | 标注 DTO 类型 |

**17. 删除 V2 不存在的表格能力**

V2 SmartAdmin v3.31 **没有** `TableHeaderCell`、**没有** `@resizeColumn`、**没有**列宽拖拽（全库 grep 0 命中）。
C 页面里的 `resizable: true` 列属性、`@resizeColumn="handleResizeColumn"`、`handleResizeColumn()` 函数必须**全部删除** ——
既引入死代码，又直接触发 TS2339。

**18. 剪枝 W2 范围外字段与功能**（逐项见 §6.2）

`customerLevelId` · `longitude` · `latitude` · `balance`（余额列） · `CUSTOMER_TYPE_ENUM` 硬编码枚举 ·
`supplierId` 手填框 · 批量删除按钮与 `:row-selection` · `PERIOD_STATUS_ENUM` · `VISIBLE_TYPE_ENUM` ·
C 抽屉里的 `供货价 / 是否默认`（SPU 级语义） · 供应商列表的「供应商ID」列。

**19. 保留 C 的 SmartAdmin 视觉（已核验，复制成本在字段/类型/权限，不在版式）**

| C 依赖 | V2 现状 |
|---|---|
| `.smart-query-form-row` / `.smart-query-form-item` / `.smart-query-table-page` / `.smart-table-btn-block` / `.smart-table-operate` | ✅ 存在于 `src/theme/smart-admin.less` |
| `.admin-content`（`yHeight` 自适应高度依赖） | ✅ 存在于 `layout/{side,top}-layout.vue` 等 4 个布局 |
| `TableOperator` / `SmartEnumSelect` / `SmartLoading` / `smartSentry` / `PAGE_SIZE_OPTIONS` / `TABLE_ID_CONST` | ✅ 全部存在于 V2 |
| `v-privilege` 指令 | ✅ 存在于 `src/directives/privilege.ts` |

### 5.3 版式约定（复制 C 后保持）

- 筛选区：`<a-form class="smart-query-form">` + `<a-row class="smart-query-form-row">` + `.smart-query-form-item`；`查询` / `重置` 用 `a-button-group`。
- 表格区：`<a-card size="small" :bordered="false" :hoverable="true">`，顶部 `<a-row class="smart-table-btn-block">`（左 `.smart-table-operate-block`，右 `.smart-table-setting-block` + `TableOperator`）。
- 表格：`a-table`（**无 ProTable**）、`size="small"`、`bordered`、`:pagination="false"`、`rowKey="id"`、`:loading`、`#bodyCell` 渲染状态 / 金额 / 操作列（`.smart-table-operate` + `a-button type="link"`）。
- 分页：`<div class="smart-query-table-page">` + `a-pagination`（`showSizeChanger` / `showQuickJumper` / `:show-total`）。
- 抽屉：`a-drawer`（`:width="560"`）+ `a-form :label-col="{ span: 5 }"` + 底部固定操作条（`取消` / `提交`）。
- 权限：`v-privilege="'scm:customer:add'"`（不用 `Permission` / `AuthProvider`）。
- 枚举标签：`SmartEnumSelect` / `$smartEnumPlugin.getDescByValue`。
- 金额展示：4 位定点**字符串**，禁止 `Number()` + `toFixed`。
- 加载 / 异常：`SmartLoading.show()/hide()`、`smartSentry.captureError(e)`；`40921` 走 §5.4 专用文案。
- 表格高度：保留 C 的 `yHeight` 自适应（`.admin-content` 在 V2 存在），但**必须补非空守卫**。

### 5.4 页面行为

**`customer-list.vue`（C 复制 + 剪枝 + 适配）**
- 筛选：关键字（客户名称 / 编码 / 联系人 / 电话）、客户类型（`customer-type-select`）、状态、结算方式。
- 列：客户名称（链接进详情）、客户编码、客户类型、联系人、联系电话、结算方式、授信额度、状态、更新时间、操作（编辑 / 状态 / 删除）。
  **删除 C 的「余额 `balance`」列**（W2 范围外）。
- 新增 / 编辑：打开 `customer-form-drawer`；保存后刷新列表。
- 状态切换 / 删除：`a-popconfirm` + 携带当前 `version`。
- 冲突处理：`40921` → 「数据已被其他人修改，请刷新后重试」+ 自动重载；`40936` → 「客户编码已存在」。
- **删除 C 的批量删除按钮与 `:row-selection`**（Q14）。

**`customer-form-drawer.vue`（C 抽屉位移 + 字段重组）**
- 基础信息：客户编码、客户名称、客户类型（`customer-type-select`）、状态（**只读展示**，变更走列表操作列独立按钮 —— 按 C7 / 修正 D7）。
- 联系与地址：联系人、联系电话、地址。
- 归属关系：上级集团客户（`customer-select`，仅可选 `GROUP` 类型）、业务员（`employee-select`）、绑定供应商（`supplier-select`）。
- 账期：结算方式、授信额度、账期类型（按金额 / 按时间 / 不设置）。
  选「按金额」→ 显示金额阈值；选「按时间」→ 显示账期值 + 单位（天 / 月），单位为「月」时显示固定结算日（1–28）。
- 备注。
- 校验：客户编码 / 名称 / 客户类型必填；联系电话 `^1[3-9]\d{9}$` 或 `^0\d{2,3}-?\d{7,8}$`（宽松）；
  授信额度 `^\d+(\.\d{1,4})?$`；结算日为整数 1–28。

**`customer-detail.vue`（W1 派生）**
- `a-descriptions` 展示全部字段 + 上级客户名 / 业务员名 / 绑定供应商名 + 创建 / 更新时间；返回列表；「编辑」按钮回列表打开抽屉。

**`customer-type-list.vue`（W1 派生自 `category-list.vue`）**
- 列：类型编码、类型名称、状态、创建时间、操作（编辑 / 删除）。
- 弹窗：类型编码、类型名称、状态。删除用 `a-popconfirm` + `version`；`40938` → 「该客户类型已被客户引用，不能删除」。

**`supplier-list.vue`（C 复制 + 剪枝 + 适配）**
- 筛选：关键字（编码 / 名称 / 联系人 / 电话）、状态下拉。
- 列：供应商编码、名称、联系人、联系电话、关联商品数（`skuCount`）、状态、更新时间、操作（编辑 / 状态 / 删除 / **关联商品**）。
  **删除 C 的「供应商ID」列**。
- 「关联商品」→ 打开 `supplier-sku-editable-table` 抽屉（整表替换提交）。

**`supplier-sku-list.vue`（C 骨架 + 剪枝为只读）**
- 保留 C 的筛选区 + 表格 + 分页骨架；**删除**新建 / 编辑 / 删除 / 批量删除按钮、抽屉、`Modal.confirm` 逻辑。
- 筛选：SKU（`sku-select`）、供应商（`supplier-select`）、状态。
- 列：供应商编码 / 名称、SKU 编码 / 名称、规格、采购单位、参考价、默认采购员、是否默认、状态、更新时间。
- **只读**，提示「请在供应商详情的『关联商品』中维护」。

**`supplier-sku-editable-table.vue`（W1 派生）**
- 表格列：SKU（选择器，已存在的行只读）、采购单位、参考价、默认采购员、是否默认（**可多选，不限制单选** —— R12）、状态、删除。
- 「添加一行」→ 打开 SKU 选择器；SKU 已存在时禁止重复添加（前端提示 + 后端 40943 兜底）。
- 提交：构造 `items`（已存在的行带 `id` + `version`，新增行不带 `id`），整体 `POST /scm/supplier/sku/replace`；空列表提交 = 清空（前端二次确认）。
- 冲突处理：40921 / 40942 / 40943 分别给出明确文案。

### 5.5 路由与菜单

菜单由后端 `menuList` 经 `buildRoutes` 动态注册，`t_menu.component` 必须与 `src/views/**` 下的真实文件路径一致
（`import.meta.glob('../views/**/**.vue')`）：

- `/business/scm/customer/customer-list.vue`
- `/business/scm/customer/customer-type-list.vue`
- `/business/scm/customer/customer-detail.vue`（`visible_flag = FALSE`）
- `/business/scm/supplier/supplier-list.vue`
- `/business/scm/supplier/supplier-detail.vue`（`visible_flag = FALSE`）
- `/business/scm/supplier/supplier-sku-list.vue`

> C 的 `deploy/sql/business_module_menu_seed.sql` **不可复用**：`component` 路径与真实文件不一致
> （seed 写 `customer/customer/customer-list.vue`，实际文件是 `customer/customer-list.vue`，C 自身 15 处 MISS）、
> `menu_id` 600–644 / 900–914 与 V2 冲突、权限码 `customer:query` 无模块前缀。V2 用 `V9` 重新分配 431–482。

---

## 6. C 前端资产迁移清单

### 6.1 可以直接复制的 C Vue 文件

| # | C 源文件 | 行数 | V2 目标 | 复制性质 |
|---|---|---|---|---|
| 1 | `src/views/business/customer/customer-list.vue` | 376 | `views/business/scm/customer/customer-list.vue` | 整页复制（剪枝 + 适配） |
| 2 | `src/views/business/purchase/supplier-list.vue` | 328 | `views/business/scm/supplier/supplier-list.vue` | 整页复制（剪枝 + 适配） |
| 3 | `src/views/business/product/product-supplier-list.vue` | 334 | `views/business/scm/supplier/supplier-sku-list.vue` | **只复制骨架**（筛选 + 表格 + 分页），CRUD 部分全删 |
| 4 | `src/components/business/customer-select/index.vue` | 69 | `components/business/scm/customer-select/index.vue` | 近零改动 |
| 5 | `src/components/business/supplier-select/index.vue` | 75 | `components/business/scm/supplier-select/index.vue` | 近零改动 |
| 6 | `src/components/business/sku-select/index.vue` | 69 | `components/business/scm/sku-select/index.vue` | 近零改动 |
| 7 | `src/constants/business/customer/customer-const.ts` | 76 | `constants/business/scm/customer-const.ts` | 结构复制，取值改写 |
| 8 | `src/api/business/customer/customer-api.ts` + `src/api/business/purchase/supplier-api.ts` | 13 + 13 | `api/business/scm/customer-api.ts` / `supplier-api.ts` | 形状复制，URL / version 改写 |

另有 2 处**位移式复制**（不是新增文件，而是把 C 内联在列表页里的抽屉整块抽出）：

| C 位置 | V2 目标 | 性质 |
|---|---|---|
| `customer-list.vue` L93–147 的 `<a-drawer>` 块 + `<script>` 内对应表单段 | `views/business/scm/customer/components/customer-form-drawer.vue` | 纯位移 + 字段重组 |
| `supplier-list.vue` L83–117 的 `<a-drawer>` 块 + `<script>` 内对应表单段 | `views/business/scm/supplier/components/supplier-form-drawer.vue` | 纯位移 + 字段重组 |

**合计：C 直接贡献 10 个文件的实现起点，覆盖 W2 全部列表页与全部选择器。**

### 6.2 复制后需要修改的点（按文件）

#### A. `customer-list.vue` → `views/business/scm/customer/customer-list.vue`

| 类别 | C 原样 | 改为 |
|---|---|---|
| API | `customerApi.query/add/update/delete/batchDelete` 打 `/customer/*` | `/scm/customer/*`；删除走 `POST /scm/customer/delete` + `{ id, version }`；**删除 `batchDelete`** |
| 主键 | `rowKey="customerId"`、`record.customerId` | `rowKey="id"`、`record.id` |
| 字段 | `customerNo` / `customerName` / `customerType` / `creditAmount` / `createTime` | `customerCode` / `name` / `customerTypeId` / `creditLimit` / `createdAt` |
| 列 | 含「余额 `balance`」列 | **删除**（W2 范围外） |
| 列 | 无类型数组 `ref([...])` | `ref<TableColumnsType<CustomerRow>>` |
| 列 | `resizable: true` ×11 | **删除**（V2 无列宽拖拽） |
| 表格 | `@resizeColumn="handleResizeColumn"` | **删除** |
| 表格 | 缺 `:loading` | 补 `:loading="tableLoading"` |
| 表格 | `:row-selection` + 批量删除按钮 | **删除**（Q14） |
| 筛选 | `customerName` / `customerType`（枚举） | `keyword` / `customerTypeId`（`customer-type-select`） |
| 枚举 | `SmartEnumSelect enum-name="CUSTOMER_TYPE_ENUM"` | 换 `customer-type-select`（客户类型已是字典表） |
| 枚举 | `CUSTOMER_STATUS_ENUM` / `SETTLE_MODE_ENUM` 数值 | 同组件保留，取值改字符串码 |
| 权限 | `customer:query/add/update/delete/batchDelete` | `scm:customer:query/add/update/status/delete` |
| 表单 | `customerLevelId` / `longitude` / `latitude` | **删除**（W2 范围外） |
| 表单 | `parentCustomerId` / `sellerId` / `supplierId` 用 `a-input-number` 手填 ID | `customer-select` / `employee-select` / `supplier-select` |
| 表单 | 无账期字段 | 补结算方式 / 授信额度 / 账期类型 / 金额阈值 / 账期值 / 单位 / 结算日 |
| 表单 | 无备注 | 补 `remark` |
| 表单 | 抽屉内联在页面 | 位移到 `customer-form-drawer.vue` |
| 校验 | `customerName` / `customerType` / `contactName` / `contactPhone` 必填 | 改为 编码 / 名称 / 类型 必填；联系人电话按 §5.4 |
| 提交 | 无 `version` | 编辑 / 状态 / 删除全部带 `version` |
| 错误 | `smartSentry.captureError` 一把抓 | 补 `40921` / `40936` 专项文案 |
| 高度 | `doc.offsetHeight` 等无守卫 | 补非空守卫 |
| 类型 | `handleResizeColumn` / `onSelectChange` / `addOrUpdate` / `deleteOne` / `doDeleteOne` 隐式 any | 显式标注 DTO 类型或删除 |
| 类型 | `tableData = ref([])` | `ref<CustomerRow[]>([])` |
| 交互 | `Modal.confirm` 删除确认 | 改 `a-popconfirm`（与 W1 统一） |

#### B. `supplier-list.vue` → `views/business/scm/supplier/supplier-list.vue`

| 类别 | C 原样 | 改为 |
|---|---|---|
| API | `/supplier/*` | `/scm/supplier/*`；删除走 `POST` + `version`；**删除 `batchDelete`** |
| 主键 | `supplierId` | `id` |
| 字段 | `supplierNo` / `supplierName` / `createTime` | `supplierCode` / `name` / `createdAt` |
| 列 | 含「供应商ID」列 | **删除** |
| 列 | 无「关联商品数」 | 补 `skuCount` |
| 列 | 无类型 + `resizable` + `@resizeColumn` | 同 A |
| 列 | 无「关联商品」操作按钮 | 补（打开 `supplier-sku-editable-table` 抽屉） |
| 表格 | 缺 `:loading` | 补 |
| 表格 | `:row-selection` + 批量删除 | **删除** |
| 筛选 | `supplierName` | `keyword` |
| 枚举 | `SUPPLIER_STATUS_ENUM` 来自 `constants/business/purchase/purchase-const.ts`（数值） | 移到 `constants/business/scm/supplier-const.ts`，取值改 `'ENABLED' / 'DISABLED'` |
| 权限 | `supplier:query/add/update/delete/batchDelete` | `scm:supplier:query/add/update/status/delete` |
| 表单 | 无 `remark` | 补 |
| 表单 | 抽屉内联 | 位移到 `supplier-form-drawer.vue` |
| 校验 | 仅 `supplierName` 必填 | 补 `supplierCode` 必填 |
| 其余 | version / 错误码 / 高度守卫 / 类型标注 | 同 A |

#### C. `product-supplier-list.vue` → `supplier-sku-list.vue`

| 类别 | C 原样 | 改为 |
|---|---|---|
| 页面性质 | SPU 级**可增删改**列表 | SKU 级**只读**反查视图 |
| 新建 / 编辑 / 删除 / 批量删除按钮 | 有 | **全删** |
| 抽屉 | 有（含供货价 / 是否默认） | **整块删除** |
| 列 | 产品ID / 供应商ID / 供货价 / 是否默认 | 供应商编码·名称 / SKU 编码·名称 / 规格 / 采购单位 / 参考价 / 默认采购员 / 是否默认 / 状态 / 更新时间 |
| 选择器 | `ProductSelect` | `sku-select` |
| API | `/product/supplier/*` | `/scm/supplier/sku/query`（只读） |
| 权限 | `product:supplier:*` | `scm:supplier:sku:query` |
| 路由 query 联动 | `route.query.productId` 预置筛选 | 改 `skuId`（或删除，视 §12 结论） |
| 其余 | `resizable` / `@resizeColumn` / 隐式 any / 缺 `:loading` | 同 A |

#### D. 三个选择器（`customer-select` / `supplier-select` / `sku-select`）

| 类别 | C 原样 | 改为 |
|---|---|---|
| API 导入 | `/@/api/business/customer/customer-api`、`.../purchase/supplier-api`、`.../product/product-sku-api` | `/@/api/business/scm/customer-api`、`.../scm/supplier-api`；sku 走 W1 `product-api` |
| 方法 | `queryAll()` 全量 | `optionList()`（只返回 `ENABLED` / `ON_SHELF`） |
| 字段 | `item.customerId` / `customerName` / `customerNo` | `item.id` / `name` / `customerCode` |
| 字段 | `item.supplierId` / `supplierName` / `supplierNo` | `item.id` / `name` / `supplierCode` |
| 字段 | `item.skuId` / `specName` / `skuNo` | V2 `product.d.ts` 的 SKU 字段名 |
| 类型 | `value: [Number, Array]` | `value: [Number, String, Array]` |
| 类型 | `customerList = ref([])`、`onChange(value)` 隐式 any | 标注 DTO 类型 |
| 搜索 | 只设 `showSearch`，未设过滤字段 | 补 `optionFilterProp="label"`（否则搜索无效） |

#### E. `customer-const.ts` / API 文件

| 类别 | C 原样 | 改为 |
|---|---|---|
| 枚举取值 | `CUSTOMER_TYPE_ENUM` = 1/2/3 | **删除**，转 `customer_type` 表种子（`ENTERPRISE` / `PERSONAL` / `GROUP`） |
| 枚举取值 | `SETTLE_MODE_ENUM` = 1/2 | `'INDEPENDENT'` / `'GROUP'` |
| 枚举取值 | `CUSTOMER_STATUS_ENUM` = 1/2/3/4 | `'POTENTIAL'` / `'COOPERATING'` / `'SUSPENDED'` / `'BLACKLIST'` |
| 枚举取值 | `PERIOD_TYPE_ENUM` = 1/2 | `'BY_AMOUNT'` / `'BY_TIME'`；重命名 `CREDIT_PERIOD_TYPE_ENUM` |
| 枚举取值 | `PERIOD_UNIT_ENUM` = 1/2 | `'DAY'` / `'MONTH'`；重命名 `CREDIT_PERIOD_UNIT_ENUM` |
| 枚举 | `PERIOD_STATUS_ENUM` / `VISIBLE_TYPE_ENUM` | **删除**（W2 范围外） |
| 命名 | `PERIOD_*` / `VISIBLE_TYPE_ENUM` 过于通用 | 加语义前缀，避免与 V2 全局枚举命名空间冲突 |
| 注册 | C 未注册到全局 | 在 `constants/index.ts` 注册进 SmartEnum 插件 |
| API 形状 | `delete: (customerId) => getRequest(...)` | `delete: (id, version) => postRequest('/scm/customer/delete', { id, version })` |
| API 形状 | `queryAll: () => getRequest(...)` | `optionList: () => getRequest('/scm/customer/option-list')` |

### 6.3 不应该复制的文件

| # | C 资产 | 原因 |
|---|---|---|
| 1 | `src/layout/**` | V2 继续用 SmartAdmin v3.31 Layout（4 种布局、iframe、keep-alive 均已适配） |
| 2 | `src/views/system/login/**`、`src/api/system/login-api.ts` | 认证由 V2 Sa-Token + Bearer 承担 |
| 3 | `src/views/system/**`（user / role / dept / menu / dict / log / online / position） | V2 系统域已完整；属"system user·role·dept 继续完全使用 V2" |
| 4 | `src/router/**`（`index.ts` / `routers.ts` / `support/**` / `system/**`） | V2 router core + 动态菜单 `buildRoutes` |
| 5 | `src/directives/privilege.ts`、`src/plugins/privilege-plugin.ts`、`src/store/modules/system/user/**` | permission framework 不复制 |
| 6 | `src/lib/axios/**`、`src/lib/smart-sentry.ts`、`src/api/system/**`、拦截器 | request framework 不复制 |
| 7 | `src/lib/**`、`src/utils/**`、`src/components/framework/**`、`src/components/support/**`、`src/theme/**`、`src/plugins/**` | SmartAdmin common/base 不复制 |
| 8 | `src/constants/index.ts`（既有内容）、`src/constants/system/**`、`src/constants/support/**` | 同上；`index.ts` 只允许**追加** 2 行 SCM 注册 |
| 9 | `deploy/sql/business_module_menu_seed.sql` | `menu_id` 与 V2 冲突；`component` 路径与真实文件不符（C 自身 15 处 MISS）；权限码无模块前缀。V2 用 `V9` 重写 |
| 10 | `src/views/business/supplier/{account,manufacturer,product-apply,statement}-list.vue` | 是**供应商协同**（账号 / 厂商 / 报品 / 对账），不是供应商主数据；W3+ 或不做 |
| 11 | `src/views/business/customer/{customer-period-list,customer-goods-visible-list,customer-qrcode-list}.vue` | W2 范围外（账期在 W2 为内嵌字段；可见性 / 二维码属 W3+） |
| 12 | `src/views/business/product/{product-list,product-sku-list,product-price-list}.vue` | W1 Product 已验收，**不覆盖**；只允许选择性吸收 UI / 交互 |
| 13 | `src/constants/business/supplier/supplier-const.ts` | 全部是供应商协同枚举，与供应商主数据无关 |
| 14 | `src/api/business/supplier/*-api.ts`（4 个协同 API）、`src/api/business/customer/{customer-goods-visible,customer-period,customer-qrcode}-api.ts` | 同上，W2 范围外 |
| 15 | `src/api/business/product/product-supplier-api.ts` | SPU 级 `/product/supplier/*`，与 V2 SKU 级 `/scm/supplier/sku/*` 语义不符（只借命名风格） |
| 16 | `src/constants/business/purchase/purchase-const.ts` 的 `SUPPLIER_STATUS_ENUM` | C 把供应商主数据状态放在采购常量里；V2 归入 `constants/business/scm/supplier-const.ts` |
| 17 | C 后端 `CustomerService` / `CustomerManager` / `CustomerDao` / `ProductSupplierService` / `SupplierService` 及全部 `mapper/business/**/*.xml` | **Backend = Rebuild**；C 后端只作 Controller / Form / VO / 流程组织参考 |
| 18 | C 的 `docs/database/*.sql`、`deploy/sql/xsy_scm_v3.30.0_supply_chain.sql` | MySQL 方言、无 Flyway、无 `@Version`、唯一键不带软删条件；V2 用 `V8` 重建 |

### 6.4 当前 W2 缺失、仍需新写的 Vue 文件

| # | 文件 | 为什么必须新写 | 可借的骨架 |
|---|---|---|---|
| 1 | `views/business/scm/customer/customer-type-list.vue` | C **没有客户类型管理页**（C 把客户类型做成硬编码枚举 1/2/3） | V2 W1 `product/category-list.vue` |
| 2 | `views/business/scm/customer/components/customer-type-form-modal.vue` | 同上 | V2 W1 `product/components/category-form-modal.vue` |
| 3 | `views/business/scm/customer/customer-detail.vue` | C 无详情页 | V2 W1 `product/product-detail.vue` |
| 4 | `views/business/scm/supplier/supplier-detail.vue` | C 无详情页 | V2 W1 `product/product-detail.vue` |
| 5 | `views/business/scm/supplier/components/supplier-sku-editable-table.vue` | C 的关联维护是 SPU 级独立列表，无"SKU 级差量提交可编辑表格" | V2 W1 `product/components/product-sku-editable-table.vue` |
| 6 | `components/business/scm/customer-type-select/index.vue` | C 无（硬编码枚举） | C `supplier-select/index.vue` |
| 7 | `api/business/scm/customer-type-api.ts` | C 无 | V2 W1 `product-category-api.ts` |
| 8 | `api/business/scm/supplier-sku-api.ts` | C 对应文件是 SPU 级，端点语义不符 | V2 W1 `product-api.ts` |
| 9 | `types/business/scm/customer.d.ts`、`types/business/scm/supplier.d.ts` | C 无 TS DTO 契约文件（页面内联 any） | V2 W1 `product.d.ts` |
| 10 | `constants/business/scm/supplier-const.ts` | C 的供应商常量全是协同枚举 | V2 W1 `product-const.ts` |
| 11 | `views/business/scm/customer/customer-form-model.ts`、`supplier-form-model.ts` | C 无独立表单模型（逻辑内联在页面） | V2 W1 `product-form-model.ts` |
| 12 | `test/customer-form-model.test.mjs`、`test/supplier-form-model.test.mjs` | C 前端 **0 单测** | V2 W1 `test/product-form-model.test.mjs` |
| 13 | `e2e/scm-customer.spec.ts`、`e2e/scm-supplier.spec.ts` | C 的 Playwright 4 个 spec 无客户 / 供应商主流程 | V2 W1 `e2e/scm-product.spec.ts` |
| 14 | `constants/support/table-id-const.ts` 的 SCM 追加项 | 无 C 对应（C 的 `TABLE_ID_CONST` 是分层结构，与 V2 扁平风格不同） | V2 W1 的 `SCM_PRODUCT: 50001` |
| 15 | `constants/index.ts` 的 2 行 SCM 注册 | 同上 | V2 既有 `...notice` / `...enterprise` 注册行 |

**新写工作量：15 项中 8 项（#1–#5、#11–#13）可从 V2 W1 同型文件派生；真正"从零设计"的只有 #6–#10、#14、#15 共 7 项，且均为 <80 行的薄文件。**

### 6.5 后端资产的复用边界（重申）

**前端可复制 ≠ 后端可复制。** C 的后端仅作 **Controller 形状 / Form / VO / 流程组织** 参考，以下一律按 §4 重建：

| C 后端资产 | V2 处置 |
|---|---|
| `CustomerController` / `SupplierController` 的 URL 与 Form 分组 | 参考 URL 命名与 Form 分组；URL 改 `/scm/**`、权限改 `scm:*`、补 `@OperateLog` |
| `CustomerAddForm` / `CustomerUpdateForm` / `SupplierAddForm` 字段清单 | 参考字段语义与必填；删 W2 范围外字段（`customerLevelId` / `longitude` / `latitude` / `balance` / `supplyPrice`） |
| `CustomerVO` / `SupplierVO` / `ProductSupplierVO` | 参考展示字段；金额改 4 位定点字符串 |
| `CustomerService` / `CustomerManager` / `CustomerDao` / `ProductSupplierService` | **重写**：Legacy 业务规则 + `@Version` + `@TableLogic` + 事务边界 + PG 方言 |
| `mapper/business/**/*.xml` | **重写**：MySQL `INSTR` 语法改 PostgreSQL `ILIKE`；排序白名单 |
| `CustomerEntity` / `SupplierEntity` / `ProductSupplierEntity` | **重写**：C 全项目 `@Version` = 0 |
| `docs/database/*.sql`、`deploy/sql/*.sql` | **不执行**：MySQL 方言、无 Flyway；V2 用 `V8` 重建 |
| `SmartBeanUtil` / `SmartPageUtil` / `SmartEnum` 等 base | V2 用 SmartAdmin 原生同名工具 |

---

## 7. Legacy 不变量覆盖清单（验收对账用）

W2 必须让下列 **65 条** legacy 不变量全部有实现与测试证据（编号沿用 Legacy Audit）：

| 组 | 条数 | V2 覆盖位置 |
|---|---|---|
| C1–C18 客户 | 18 | `CustomerService` / `CustomerQueryService` / `CustomerDao.xml` / `CustomerValidator` |
| T1–T8 客户类型 | 8 | `CustomerTypeService` / `CustomerTypeDao.xml` |
| S1–S12 供应商 | 12 | `SupplierService` / `SupplierQueryService` / `SupplierDao.xml` / `SupplierValidator` |
| W1–W7 仓库 | 7 | **W2 不做**（见 Q5）；仅在报告中标注"有意排除" |
| R1–R20 商品-供应商关系 | 20 | `SupplierSkuChangeSet` / `SupplierSkuSyncManager` / `SupplierSkuService` / `SupplierSkuDao.xml` |
| **合计** | **65（W2 覆盖 58）** | — |

---

## 8. C 已知缺陷与处置

| # | C 的缺陷 | 证据 | V2 处置 |
|---|---|---|---|
| K1 | **全项目 `@Version` = 0 处**，无乐观锁 | `grep -rn "@Version"` = 0 | 四张表全部 `@Version` + `40921` |
| K2 | 无 Flyway，schema 只有 MySQL dump | `数据库SQL脚本/mysql/smart_admin_v3.sql` 只有 SmartAdmin 基表 | Flyway `V8`/`V9`，PostgreSQL 方言 |
| K3 | `uk_customer_no` / `uk_supplier_no` / `uk_product_supplier` 是**普通唯一键**，不带 `deleted_flag` 条件 | `docs/database/02-客户.sql`、`04-采购.sql`、`01-商品与价格.sql` | 全部改 partial unique `WHERE deleted = FALSE`，软删后编码可复用 |
| K4 | `t_customer.status` / `settle_mode` / `customer_type` 是 `TINYINT` **无 CHECK** | 同上 | 改 `VARCHAR` + `CHECK IN (...)` |
| K5 | `parent_customer_id BIGINT NOT NULL DEFAULT 0`（用 `0` 表示独立客户） | 同上 | 改 `NULL` 表示独立；`CHECK (parent_customer_id IS NULL OR parent_customer_id <> id)` |
| K6 | `deleted_flag TINYINT` 与 SmartAdmin 的 `deleted_flag BOOLEAN` 不一致 | `t_customer` DDL vs `t_employee` DDL | 统一 `deleted BOOLEAN` + `@TableLogic(value="false", delval="true")` |
| K7 | 只有 `create_user_id` / `create_user_name`，**无 `updated_by`** | 各表 DDL | 统一四审计列（`created_at/updated_at/created_by/updated_by`），写 `ScmOperator.current()` |
| K8 | **customer / supplier / product_supplier 无任何 `@OperateLog`**（全项目仅 3 处，在 OA 模块） | `grep -rn "@OperateLog" business/` = 3 | 全部写端点加 `@OperateLog` |
| K9 | 权限码 `customer:query` / `supplier:add` **无模块前缀** | `CustomerController` / `SupplierController` | 统一 `scm:customer:*` / `scm:supplier:*` |
| K10 | 删除用 **GET** `/{module}/delete/{id}` 且无 `version` | 两个 Controller | `POST /scm/*/delete` + body `{id, version}` |
| K11 | **无详情端点**（`/customer/{id}` 不存在），编辑直接用列表行数据 | `CustomerController` | 新增 `GET /scm/customer/detail/{customerId}` + 独立详情页 |
| K12 | 动作式 URL（`/customer/query`、`/customer/add`） | 两个 Controller | `/scm/customer/query`、`/scm/customer/add`（保留动作式，但统一 `/scm` 前缀与资源分组） |
| K13 | `SELECT *` + `resultType=VO`，依赖列名驼峰映射 | `CustomerMapper.xml` | 显式列清单，不 `SELECT *` |
| K14 | `SmartPageUtil` 排序只做 `SqlInjectionUtils.check`，**无白名单** | `SmartPageUtil.convert2PageQuery` | 白名单 |
| K15 | `SmartBeanUtil.copy` = `BeanUtils.copyProperties`，**null 也会覆盖**；依赖 MyBatis-Plus 默认 `FieldStrategy.NOT_NULL` 才能让 DB 默认值生效 | `SmartBeanUtil` | 显式逐字段赋值；可清空的列加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)` |
| K16 | 客户列表/表单把 `customerLevelId` / `sellerId` / `supplierId` 做成**手填数字 ID 输入框** | `customer-list.vue` L105–116 | 换 `employee-select` / `supplier-select` / `customer-select` |
| K17 | 菜单 seed 重复/冲突：`business_module_menu_seed.sql` 有 601 客户列表，`xsy_scm_v3.30.0_supply_chain.sql` 又有 1040–1042 客户管理 | 两处 seed | V2 重新分配 431–482，一次性 seed |
| K18 | `t_product_supplier` 是**商品（SPU）级** + `supply_price`，与 legacy 的 SKU 级不符 | `01-商品与价格.sql` L112–127 | 采用 legacy 的 SKU 级 `supplier_sku`；`supply_price` 属 W3 定价域，不迁移 |
| K19 | 14 个业务域 **0 测试**（唯一测试类是空壳 `AdminApplicationTest`） | `grep` 测试目录 | W2 交付 §9 测试矩阵 |
| K20 | `t_customer_period` 有独立列表页，但客户表单里没有账期入口，且 `credit_amount` 只在 `t_customer` 上，形成两个账期真相源 | `customer-list.vue` 无账期字段 + `customer-period-list.vue` 独立页 | W2 单一真相源：账期基础字段内嵌 `customer`；W3 若需多账期再建子表 |

---

## 9. 测试矩阵

### 9.1 后端单测（无 DB，Mockito）

| 测试类 | 用例 |
|---|---|
| `CustomerValidatorTest` | 合法表单；账期组合：不设置 / 按金额（缺阈值→拒） / 按时间·天（带结算日→拒） / 按时间·月（结算日 1 与 28 通过、0 与 29 拒绝、缺省通过）；上级客户 = 自身→40032；上级类型非 GROUP→40032；环形上级→40032；编码归一化（trim + upper）；名称 trim |
| `CustomerTypeValidatorTest` | 编码/名称必填；编码归一化；状态域 |
| `SupplierValidatorTest` | 编码归一化；名称/联系人 trim；备注可清空 |
| `SupplierSkuChangeSetTest` | 保留 id 分组；新增/删除分离；跨供应商 id→40943；`skuId` 变更→40943；重复 `skuId`→40943；无 id 命中已存在 `(supplierId, skuId)`→复用；空数组→全部 removed；**多条 `defaultFlag = true` 全部保留（R12 防回归）** |
| `ScmDecimalStringsTest` | `"12.3400"` 通过；`"12.34000"` 拒；`12.34`（数字）拒；`null`→null；`""`→null；超 14 位整数部分拒；负数拒 |
| `ScmCustomerStatusEnumTest` | `tradable()` 仅 `COOPERATING` 为真 |

### 9.2 后端 PG 集成测试（`@SpringBootTest` + test profile + Flyway）

| 测试类 | 用例 |
|---|---|
| `ScmCustomerSupplierMigrationIT` | V8 建表与 CHECK 生效；`uk_customer_code_active` 软删后编码可复用；`uk_customer_type_code_active` 同；`uk_supplier_code_active` 同；`uk_supplier_sku_active` 拒绝重复 `(supplier_id, sku_id)`；**`supplier_sku` 允许同供应商多条 `is_default = TRUE`**；`ck_customer_credit_period` 拒绝非法组合；`ck_customer_self_parent` 拒绝自引用；三条 `customer_type` 种子存在 |
| `ScmCustomerSupplierOptimisticLockIT` | 复用 `ProcurementOptimisticLockIT` 形状（insert → `sqlSession.clearCache()` → 取两份 → 第二份 `updateById` 返回 0），覆盖 **customer / customer_type / supplier / supplier_sku** 四表 |
| `CustomerServiceIT` | 新增（类型必须 ENABLED，停用类型→40431）；编码重复→40936；更新乐观锁冲突→40921；`updateStatus` 四态流转；删除软删 + 编码可复用；删除引用检查位；上级客户环形检测 |
| `CustomerTypeServiceIT` | 新增/更新/编码重复→40937；被客户引用删除→40938；未被引用可删除；`option/list` 只返回 ENABLED |
| `CustomerQueryServiceIT` | 按编码/名称/联系人/电话搜索命中；客户类型筛选；状态筛选；上级客户筛选；排序白名单生效（非法列被拒）；列表补 `customerTypeName` / `sellerName` / `parentCustomerName` 且**不 N+1**（SQL 计数断言）；分页边界 1 / 100 / 101 |
| `SupplierServiceIT` | 新增（强制 ENABLED）；**更新不改 status**（断言库中 status 不变）；编码重复→40944；乐观锁冲突→40921；`updateStatus`；被 `supplier_sku` 引用删除→40947；解除引用后可删除；`option/list` 只返回 ENABLED |
| `SupplierSkuServiceIT` | **`FOR UPDATE` 锁序**（真实 PG）；整表替换差量（保留 id / 新增 / 软删）；空数组清空；**多条默认允许**；`skuId` 变更→40943；跨供应商→40943；版本过期→40921；离线 SKU→40942；快照取 `productName`；`purchaserId` 不存在→40040；参考价超 4 位小数→40000；`selectEnabledBySkuId` 四表联动（SPU 下架即不可采购） |
| `SupplierQueryServiceIT` | 搜索、状态筛选、`skuCount` 批量补全不 N+1、排序白名单 |

### 9.3 后端 Web 层测试（`@WebMvcTest` + `addFilters=false`）

| 测试类 | 用例 |
|---|---|
| `CustomerControllerTest` | `ResponseDTO` 信封 `code=0`；`PageResult` 结构；参数校验（`version` 缺失、`pageSize=101`）；40430；40921；40936；40930；403 权限拒绝（`addFilters=true` 单独一例） |
| `CustomerTypeControllerTest` | 列表信封；40431；40937；40938；40921 |
| `SupplierControllerTest` | 列表/详情 VO；创建返回 Long；40440；40944；40921；**更新体不含 status**（JSON 带 status 被忽略） |
| `SupplierSkuControllerTest` | `list/{supplierId}` 契约；`query` 分页信封；`replace` 空数组 = 清空；参考价数字字面量→40000；参考价超 4 位小数→40000；40942/40943；40921 |

### 9.4 前端

- `vue-tsc --noEmit`：**TS 基线棘轮必须 PASS**（Step 0 交付物）——
  `new error = 0`、`scm/** = 0 error`、`total ≤ 1974`。
- `vite build --mode production --outDir dist-verify-w2 --emptyOutDir`（用全新输出目录规避沙箱批量删除保护）。
- `eslint src` 通过。
- `node --experimental-strip-types --test test/customer-form-model.test.mjs test/supplier-form-model.test.mjs`：
  客户表单账期联动、上级客户不能选自身、编码/名称归一化、授信额度 4 位小数校验；
  供应商 SKU 差量构造（已存在行带 `id` + `version`、新增行不带 `id`）、重复 SKU 前端拦截、空列表 = 清空。

### 9.5 E2E（Playwright）

`e2e/scm-customer.spec.ts`：
登录 → 新增客户类型「W2E2E 餐饮」→ 新增客户（填联系人/电话/地址/结算方式=独立结算/授信额度/账期=按时间·月·结算日 15/业务员）→
断言列表行（类型名、联系人、状态=潜在）→ 进详情页断言账期字段 → 编辑改账期为「按金额」→
状态改为「合作中」→ 搜索（按联系人电话）→ 新增第二个客户并设上级为该集团客户（先建一个集团类型客户）→
删除第二个客户 → 断言列表不再出现。

`e2e/scm-supplier.spec.ts`：
登录 → 通过 API 建一个 W1 商品（2 个 ON_SHELF SKU）→ 新增供应商（编码/名称/联系人/电话/地址）→
打开「关联商品」抽屉 → 添加 2 个 SKU（采购单位/参考价/默认采购员/其中 1 个设为默认）→ 提交 →
断言 `list/{supplierId}` 返回 2 行且快照名称正确 → 再提交（去掉 1 行、改参考价）→ 断言软删与更新 →
在「商品-供应商关系」页按 SKU 反查断言 → 尝试删除被引用的供应商（应被拒 40947）→ 清空关联 → 删除供应商 → 断言列表不再出现。

> E2E 复用 W1 的 `tools/w1_e2e_accounts.py`（临时账号 setup/cleanup）与
> `e2e/scm-product.spec.ts` 的登录/`buttonName`/`authenticate` 范式。
> **注意 W1 已发现的两个 antd 陷阱**：两字中文按钮自动插空格（用 `buttonName` 正则）、
> `a-drawer` **没有** `role="dialog"`（用 `.ant-drawer:visible` 定位）。

---

## 10. 质量门禁（Step 0 交付物）

`tools/ts_baseline_ratchet.py` + `docs/quality/ts-baseline.json`：

- 基线快照（2026-09-15，`vue-tsc --noEmit`）：**总计 1974 项**，其中 **`src/{api,components,constants,types,views}/business/scm/**` = 0 项**。
- 错误身份规则：`file + TS code + message`（不含 line/column，避免上游行位移误报为"新增"）。
- 门禁：`new error = 0` ∧ `scm error = 0` ∧ `total ≤ 1974`；基线**可减不可增**。
- 顶层错误码分布（基线）：`TS7006 839` / `TS2339 404` / `TS6133 142` / `TS2322 105` / `TS2554 95` / `TS7016 82` / `TS18046 63`。
- **不修改 SmartAdmin 上游约 200 个文件**（W1 已确认的基线债，需单独立项）。

W2 每个前端任务（T10–T12）完成后必须执行 `python tools/ts_baseline_ratchet.py check` 且结果为 `PASS`。

---

## 11. 风险

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 客户状态由 legacy 二态改为四态，W3 的"可交易"判定若散落各处会漂移 | 中 | `ScmCustomerStatusEnum.tradable()` 作为唯一判定点；W3 强制调用 |
| R2 | `supplier_sku` 的整表替换语义要求前端一次性提交全量，SKU 数量大时请求体偏大 | 中 | 实际单供应商关联数量有界；`SupplierSkuReplaceForm.items` 加 `@Size(max = 500)`；超限返回 40000 |
| R3 | 账期内嵌在 `customer` 上，若 W3 需要多账期/账期历史需改结构 | 中 | 采用 append-only migration 追加 `customer_period` 子表，不改已应用列（Q6 备选） |
| R4 | `customer.parent_customer_id` 的环形检测需要逐级上溯，客户量大时是 N 次查询 | 低 | 上溯深度限制 20 层（超出即判环形 → 40032）；后续可加物化路径 |
| R5 | 新增 `customer_type` / `supplier` 的删除能力是 W2 新能力，引用检查规则需人工确认 | 中 | Q3 / Q4 待确认；规则落位后由 IT 覆盖 |
| R6 | `ScmCommonErrorCode` 与 `ProductErrorCode` 重复定义 40921 | 低 | 码值一致，对外无差异；W3 前做纯 Java 重构（§4.5） |
| R7 | `@TableField(updateStrategy = ALWAYS)` 若不显式声明，MyBatis-Plus 默认 `NOT_NULL` 会导致**可空字段无法清空**（联系人/地址/备注置空失败） | **高** | 所有可空列显式声明 `ALWAYS`；`SupplierServiceIT` / `CustomerServiceIT` 各加一条"清空可空字段"用例 |
| R8 | `supplier` 表在 legacy V1 与 V8 之间发生过 `ALTER ADD COLUMN`；V2 是全新 schema，若漏列会导致 W3 收货不可用 | 中 | V8 的 `supplier` 列清单已与 legacy 最终形态逐列比对（`supplier_code/name/status/remark` + 审计列） |
| R9 | 客户列表搜索扩展到联系人/电话属**行为增强**（legacy 只搜编码+名称） | 低 | 在验收报告显式标注（同 W1 R2 的处理方式） |
| R10 | E2E 需要构造 W1 商品与 SKU 作为前置，跨波次耦合 | 低 | E2E 通过 API 自建前置数据并在 `afterAll` 清理（同 W1 做法） |

---

## 12. 已处理的设计确认项（历史记录）

| # | 事项 | 建议 | 理由 |
|---|---|---|---|
| **Q1** | 客户状态域：legacy 二态（`ENABLED`/`DISABLED`）还是 C 四态（`POTENTIAL`/`COOPERATING`/`SUSPENDED`/`BLACKLIST`）？ | **四态** | legacy 二态无法表达"潜在客户"；需求基线有"客户分类"；四态是"客户档案"的真实语义；二态仅服务"能否下单"一个布尔判断，W2 无订单域 |
| **Q2** | 客户编码：手工录入（legacy）还是按编号规则自动生成（C，`KH` + 6 位流水）？ | **手工录入** + DB partial unique | legacy 是事实源（`customerCode` 由用户录入）；自动生成需引入 SmartAdmin 编号规则模块，属独立能力；可作 W3 增强 |
| **Q3** | 客户类型是否新增删除能力（legacy 无）？ | **新增**，被客户引用则拒绝（40938） | 主数据字典需能下线错误数据；引用检查规则明确、无歧义 |
| **Q4** | 供应商是否新增删除能力（legacy 无）？ | **新增**，被活动 `supplier_sku` 引用则拒绝（40947） | 同上；`supplier_sku` 是唯一已知引用方，检查规则明确 |
| **Q5** | `warehouse` 主数据是否纳入 W2？ | **推迟到后续 Purchase / Receiving / Inventory 波次** | 用户 W2 范围未列；W2 无收货/库存消费方；legacy 的 warehouse 规则已在 Legacy Audit §3.4 完整记录 |
| **Q6** | 账期落地形态：`customer` 内嵌基础字段（本设计）还是独立 `customer_period` 子表（C）？ | **内嵌** | 用户措辞是"账期**基础字段**"；W2 无应收/结算消费方；审计由 `@OperateLog` 承担；若需多账期，W3 追加子表 migration |
| **Q7** | 归属关系（`parent_customer_id` / `seller_id` / `supplier_id`）是否纳入 W2？ | **纳入**为可空基础字段 | 需求基线明确要求"客户绑定供应商、业务员""下属单位独立采购，集团客户统一结算"；均为标量列，不含新聚合；**不做**结算逻辑与 `@DataScope` |
| **Q8** | 客户搜索是否扩展覆盖联系人 / 联系电话？ | **是**（行为增强） | 实际使用中按联系人找客户是高频操作；与 W1 Q3「分类含子分类」同性质的增强，需在验收报告标注 |
| **Q9** | 客户类型下拉是否包含 `DISABLED` 类型？ | **否**，下拉只含 `ENABLED`；管理列表显示全部 | 与 legacy S11 的处置一致（legacy 全量返回导致下拉含停用项，是缺陷 D11） |
| **Q10** | 是否落 legacy 的 `visibility_policy` 列？ | **否** | W2 排除 SKU Visibility；且 C 用"逐商品 `visible_type`"、legacy 用"策略枚举 + 白名单"，两种建模冲突，W3 设计时可能推翻 |
| **Q11** | `supplier_sku` 是否允许同一供应商多条 `is_default = TRUE`？ | **允许**（保持 legacy） | legacy 有专门测试 `allowsMultipleDefaultsWithoutInventingCardinalityPolicy` 明确禁止发明基数策略；加唯一约束会引入无依据的规则 |
| **Q12** | 业务员 / 采购员的引用目标？ | **SmartAdmin `t_employee.employee_id`** | legacy 的 `seller_id` / `purchaser_id` 是无引用目标的裸 `BIGINT`（其 `sys_user` 是自建 RBAC，V2 已不迁移）；V2 的员工/部门来自 SmartAdmin |
| **Q13** | 新增客户的默认状态？ | **`POTENTIAL`**（潜在） | 与 Q1 的四态一致；"登记未合作"是新增客户的真实起点；legacy 的 `ENABLED` 是二态下的等价语义 |
| **Q14** | 是否提供批量删除（C 有 `batchDelete`，legacy 无）？ | **不提供** | W1 亦未提供批量删除；批量删除与乐观锁（逐行 version）语义冲突，且无 legacy 依据 |

---

## 13. 实施顺序（确认后执行）

| # | 任务 | 内容 |
|---|---|---|
| **T1** | 前端 TS 基线棘轮 | 已完成（Step 0）：`tools/ts_baseline_ratchet.py` + `docs/quality/ts-baseline.json`；基线 1974，scm 0，`check` PASS |
| **T2** | Migration | `V8__scm_customer_supplier.sql` + `V9__scm_customer_supplier_permissions.sql`；验证可应用、可回滚至基线（不引入 `clean`） |
| **T3** | common 层扩展 | `ScmCommonErrorCode`、`ScmCustomerStatusEnum`、`ScmSettleModeEnum`、`ScmCreditPeriodTypeEnum`、`ScmCreditPeriodUnitEnum`、`ScmDecimalStrings` |
| **T4** | customer 域 entity / dao / mapper XML | `CustomerEntity`、`CustomerTypeEntity`、两个 DAO、两个 XML（含 `softDelete` 原子谓词） |
| **T5** | customer 域 manager + 单测（TDD） | `CustomerValidator`、`ScmDecimalStringsTest`、`ScmCustomerStatusEnumTest` |
| **T6** | customer 域 service + 单测 | `CustomerService`（写）、`CustomerQueryService`（读）、`CustomerTypeService` |
| **T7** | customer 域 controller + Web 层测试 | 12 个端点 + 权限注解 + `@OperateLog` |
| **T8** | supplier 域 entity / dao / mapper XML | `SupplierEntity`、`SupplierSkuEntity`、两个 DAO、两个 XML |
| **T9** | supplier 域 manager + 单测（TDD） | `SupplierValidator`、`SupplierSkuChangeSet`、`SupplierSkuSyncManager` |
| **T10** | supplier 域 service + controller + Web 层测试 | 17 个端点 + 权限 + `@OperateLog` |
| **T11** | PG 集成测试（IT） | `ScmCustomerSupplierMigrationIT`、`ScmCustomerSupplierOptimisticLockIT`、四个 Service IT、两个 Query IT |
| **T12** | 前端 api / types / constants / 选择器组件 | 4 个 api（customer、supplier 由 C 复制形状；customer-type、supplier-sku 新写）、2 个 types、2 个 const（customer 由 C 复制结构，supplier 新写）、4 个 select 组件（customer / supplier / sku **由 C 复制**，customer-type 新写；业务员复用 V2 原生 `components/system/employee-select`）、`table-id-const` 与 `constants/index.ts` 追加 |
| **T13** | 前端页面**迁入 + 剪枝 + 适配** + 表单模型单测 | **Copy First + Adapt**（§5 / §6）：① 从 C 复制 8 个文件 + 2 处抽屉位移（§6.1）；② 按 §6.2 逐文件剪枝与适配（API → `/scm/**`、DTO → V2 contract、permission → `scm:*`、enum → V2 SmartEnum、selector、`version`）；③ 从 V2 W1 派生 5 个同型文件（§6.4 #1–#5）；④ 新写 7 个薄文件（§6.4 #6–#10、#14、#15）；⑤ 删除 C 的 `resizable` / `@resizeColumn` / `handleResizeColumn` / 批量删除 / W2 范围外字段，补齐 `:loading`、DOM 非空守卫、显式 DTO 类型标注；⑥ 2 个 `.test.mjs`。**迁入完成后立即跑 `ts_baseline_ratchet check`，`scm` 必须 0** |
| **T14** | 质量门禁 + E2E + 验收报告 | `ts_baseline_ratchet check` PASS、`vite build`、`eslint`、Playwright 2 个 spec、legacy SHA-256 复验、输出《W2 Customer + Supplier 验收报告》+ Go/No-Go |

---

## 14. 完成定义（DoD）

- [ ] **65 条** legacy 不变量中 W2 覆盖的 **58 条**（C1–C18 / T1–T8 / S1–S12 / R1–R20）全部有实现且被测试覆盖；W1–W7（仓库）在报告中标注为**有意排除**
- [ ] **20 项** legacy 缺陷（D1–D20）全部修正或在报告中标注
- [ ] **20 项** C 缺陷（K1–K20）全部在报告中给出处置结论
- [ ] 仅 `V8` / `V9` 两个迁移文件，无第三张迁移；已应用的 V6 / V7 **未被修改**
- [ ] `module/scm/product/**`（W1）**未被修改**
- [ ] 后端单测 + PG 集成测试 + Web 层测试全绿
- [ ] `ts_baseline_ratchet check` = **PASS**（new 0 / scm 0 / total ≤ 1974）；`vite build` 与 `eslint` 通过
- [ ] Playwright 客户主流程 + 供应商与关联主流程通过
- [ ] legacy SHA-256 与冻结基线**全量一致**（HEAD `95a54233586aa3c652e55cd833bb346d51f5a952`）
- [ ] 输出验收报告与 Go/No-Go，**停在 W2，不进入 W3**
