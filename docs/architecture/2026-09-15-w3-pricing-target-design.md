# W3 Pricing 价格中心 · Target Design（目标设计）

状态：**人工评审通过；实现已冻结，W3 Verification Closure 已完成；验收后停止，不进入 W4**
日期：2026-09-15
波次：W3 Pricing
上游基线：W1 Product ✅ · W2 Customer + Supplier ✅ · SmartAdmin v3.31 · Java 21 · PostgreSQL 18 · Flyway · Vue3 · Sa-Token

工作区边界：V2 正式代码、工具、文档和运行证据均位于仓库根目录；本文件随 Root Promotion
一并归档到根目录 `docs/architecture/`。

架构原则（永久生效，本文件全程遵守）：

```text
Frontend              = SmartAdmin Base + C SCM Vue（Copy First + Adapt）
Backend Infrastructure = SmartAdmin Native First
Backend SCM Business   = SmartAdmin Structure + confirmed SCM business rules
```

事实依据：`2026-09-15-w3-pricing-legacy-audit.md`（不变量 P1–P27、缺陷 D1–D20、缺口 G1–G12、冲突辨析 §8.2）。
本文件为已批准的编码依据；每个决策都标注它的 legacy 依据或「新增能力」标记。

> **Verification Closure 状态（2026-09-15）**：V2 实现、真实 PostgreSQL IT、Web Test、前端门禁和 W1/W2/W3 Playwright 已完成并通过。V10/V11 首次应用后的 hash 已冻结。`xsy-scm-server` 与 `xsy-scm-web` 已由用户删除，legacy SHA-256 无法复验；详见 [`2026-09-15-w3-pricing-验收报告.md`](./2026-09-15-w3-pricing-验收报告.md)。本文件冻结，不进入 W4 设计或编码。

---

## 1. 范围（Scope）

### 1.1 做

| # | 能力 | Legacy 依据 |
|---|---|---|
| 1 | **Customer Agreement Price**（客户协议价 CRUD + 审计） | P1/P2/P5–P9/P24/P25 |
| 2 | **Customer Type Price**（客户类型价 CRUD + 审计） | 同上（`customer_type_id` 维度） |
| 3 | **Customer SKU Visibility**（客户 SKU 可见性：策略 + 白名单差量） | P14–P18 |
| 4 | **Price Resolver**（价格解析器：软模式 + 严格模式） | P1–P4 |
| 5 | **Price Priority**（AGREEMENT → CUSTOMER_TYPE → MARKET） | P1 |
| 6 | **Price Effective Time**（半开区间 + 长期有效 + 重叠禁止） | P5–P9 |
| 7 | **Price History / Audit**（双来源合并历史 + 变更留痕） | P24–P26 |
| 8 | **Bulk Price Update**（客户类型价批量调价：整批事务 + 逐行失败报告） | P19–P22 + G4/G5 |
| 9 | **UNPRICED 语义**（保持原始缺价定义 + 与 0 元严格区分） | P11–P13 + G1 |
| 10 | **W3 P0 前置：SKU option API**（`/scm/product/sku/option-list`） | G2 |
| 11 | **W2 钩子启用**：客户/客户类型删除的 pricing 引用检查 | P27 + D17 |

### 1.2 不做（用户锁定 + legacy 事实源）

用户锁定：

```text
Sales Order · Purchase · Receiving · Inventory · Warehouse · Finance · Mall · MiniApp
```

按 legacy 事实源同样不做：

| 项 | 理由 |
|---|---|
| 人工锁价 / 订单锁价 | **P23：属 Order 域。** Pricing 域枚举只有 3 值，不得含 `OVERRIDE` |
| 活动价 / 促销价 | 规格 §2「活动价本 Sprint 不参与成交计算」 |
| 客户折扣率 | legacy 无此表；C 有表无消费点（C11） |
| 价格组 / 阶梯价 | legacy 无 `price_group` 表；规格 §6/§10 未冻结 |
| 客户分级价（`customer_level_id`） | **只有 C 有**，legacy 用客户类型价代替（审计 §8.2 冲突 1） |
| 起订量 / 增量步长 / 数量精度 | legacy 无对应表/字段 |
| 黑名单可见性 | legacy 只有 `ALL_ENABLED` / `ALLOWLIST` |
| 协议价批量调价 | legacy 批量只针对客户类型价 |
| 价格「启用/停用」状态字段 | legacy 价格表无 `status` 列；上下架由生效区间表达 |
| 改 `V6/V7/V8/V9` | 哈希已冻结（`w1-applied-migrations.sha256` / `w2-applied-migrations.sha256`） |
| 改 W1 Product 聚合写模型 | 只允许**新增只读查询能力**（见 §4.11） |

### 1.3 交付物

```text
1. docs/architecture/2026-09-15-w3-pricing-legacy-audit.md      ✅ 已完成
2. docs/architecture/2026-09-15-w3-pricing-target-design.md     ← 本文件
3. V10__scm_pricing.sql
4. V11__scm_pricing_permissions.sql
5. module/scm/pricing/**（后端）
6. module/scm/customer/**（可见性增补）
7. module/scm/product/**（只读 SKU option 增补，零修改既有文件）
8. views/business/scm/pricing/** + customer/customer-sku-visibility-list.vue（前端）
9. components/business/scm/sku-select（改造为远程搜索）
10. tools/verify_w3_legacy.py + tools/w3_e2e_accounts.py
11. W3 验收报告（含 Frontend Migration Provenance 表）
```

---

## 2. 目标架构

### 2.1 模块落点

```text
net.lab1024.sa.admin.module.scm.pricing                  ← 新建（结构升级，legacy 无此包）
├─ constant/
│   ├─ PricingErrorCode.java                W3 错误码枚举
│   ├─ ScmPriceSourceEnum.java              AGREEMENT | CUSTOMER_TYPE | MARKET（3 值，无 OVERRIDE）
│   ├─ ScmPriceStatusEnum.java              PRICED | UNPRICED
│   ├─ ScmUnpricedReasonEnum.java           仅 NO_PRICE_SOURCE
│   ├─ ScmUnavailableReasonEnum.java        五种不可售原因（与价格状态独立）
│   └─ ScmPriceBatchResultEnum.java         SUCCESS | FAILED
├─ controller/
│   ├─ AgreementPriceController.java        /scm/pricing/agreement-price
│   ├─ CustomerTypePriceController.java     /scm/pricing/type-price
│   ├─ PriceHistoryController.java          /scm/pricing/history
│   └─ PriceResolveController.java          /scm/pricing/resolve
├─ dao/
│   ├─ AgreementPriceDao.java               + mapper/business/scm/pricing/AgreementPriceDao.xml
│   ├─ AgreementPriceOperationLogDao.java
│   ├─ CustomerTypePriceDao.java
│   ├─ CustomerTypePriceOperationLogDao.java
│   ├─ PriceBatchAuditDao.java
│   └─ PriceHistoryDao.java                 + mapper/business/scm/pricing/PriceHistoryDao.xml
├─ domain/
│   ├─ entity/
│   │   ├─ AgreementPriceEntity.java                 @TableName("customer_agreement_price")
│   │   ├─ AgreementPriceOperationLogEntity.java     @TableName("customer_agreement_price_operation_log")
│   │   ├─ CustomerTypePriceEntity.java              @TableName("customer_type_price")
│   │   ├─ CustomerTypePriceOperationLogEntity.java  @TableName("customer_type_price_operation_log")
│   │   └─ PriceBatchAuditEntity.java                @TableName("customer_price_batch_audit")
│   ├─ form/
│   │   ├─ AgreementPriceAddForm / UpdateForm / DeleteForm / QueryForm
│   │   ├─ CustomerTypePriceAddForm / UpdateForm / DeleteForm / QueryForm
│   │   ├─ PriceBatchForm / PriceBatchRowForm
│   │   ├─ PriceHistoryQueryForm
│   │   └─ PriceResolveForm
│   └─ vo/
│       ├─ AgreementPriceVO / CustomerTypePriceVO
│       ├─ PriceBatchResultVO / PriceBatchRowFailureVO
│       ├─ PriceHistoryVO / PriceHistoryRow
│       └─ ResolvedPriceVO / PriceResolveResultVO
├─ manager/
│   ├─ AgreementPriceValidator.java         价格 + 区间 + 引用校验
│   ├─ CustomerTypePriceValidator.java
│   ├─ PricePeriodOverlapGuard.java         重叠检测编排（锁 → 计数 → 判定）
│   └─ PriceBatchValidator.java             整批预校验（纯函数，不触库）
└─ service/
    ├─ AgreementPriceService.java           写路径（create/update/delete + 留痕）
    ├─ AgreementPriceQueryService.java      读路径（query/detail）
    ├─ CustomerTypePriceService.java
    ├─ CustomerTypePriceQueryService.java
    ├─ PriceBatchService.java               入口 + 失败审计（对应 legacy BatchService）
    ├─ PriceBatchWriter.java                单事务写入（对应 legacy BatchWriter）
    ├─ PriceHistoryQueryService.java        双来源合并查询
    └─ PriceResolver.java                   价格解析（软 + 严格双模式）
```

```text
module/scm/customer/  ← W3 增补（可见性）
├─ constant/CustomerErrorCode.java           +3 个码（40033/40034/40932）
├─ dao/CustomerSkuVisibilityDao.java         新
├─ domain/entity/CustomerSkuVisibilityEntity.java   新
├─ domain/form/CustomerSkuVisibilityItemForm.java   新
├─ domain/vo/CustomerSkuVisibilityVO.java           新（明细）
├─ domain/vo/CustomerSkuVisibilityReverseVO.java    新（反查行，带快照）
├─ manager/CustomerSkuVisibilityChangeSet.java      新（差量 record）
├─ service/CustomerSkuVisibilityService.java        新（replace / listByCustomer / reverseQuery）
├─ service/CustomerService.java                    改：+visibilityPolicy +visibilities 差量 +引用检查启用
├─ service/CustomerQueryService.java               改：detail 带 visibilities；+reverse 分页
├─ domain/form/CustomerAddForm/UpdateForm.java     改：+visibilityPolicy +visibilities
├─ domain/vo/CustomerVO/DetailVO.java              改：+visibilityPolicy（列表只带策略，不带明细）
└─ resources/mapper/business/scm/customer/CustomerSkuVisibilityDao.xml   新
```

```text
module/scm/product/  ← W3 只增不改（SKU option，只读）
├─ controller/ProductSkuController.java             新  POST /scm/product/sku/option-list
├─ dao/ProductSkuOptionDao.java                     新（不碰 ProductSkuDao）
├─ domain/form/ProductSkuOptionQueryForm.java       新
├─ domain/vo/ProductSkuOptionVO.java                新
├─ domain/vo/ProductSkuOptionListVO.java            新
├─ service/ProductSkuOptionQueryService.java        新
└─ resources/mapper/business/scm/product/ProductSkuOptionDao.xml   新
```

**零修改保证**：W1 既有文件（`ProductSpuDao` / `ProductSkuDao` / `ProductSpuService` / `ProductQueryService` / `ProductController` / 全部 W1 实体与 VO）**一行不改**。SKU option 全部落在新文件里。

### 2.2 分层职责（对齐 SmartAdmin ERP 范式与 W1/W2）

| 层 | 职责 | 禁止 |
|---|---|---|
| `controller` | 参数校验（`@Valid`）、权限（`@SaCheckPermission`）、操作日志（`@OperateLog`）、组装 `ResponseDTO` | 写业务规则、直接调 Dao |
| `service`（写） | 事务边界、校验编排、留痕、乐观锁、引用检查 | 拼 SQL |
| `service`（读） | 查询编排、VO 组装、批量补全（避免 N+1） | 写库 |
| `manager` | 纯校验 / 差量计算 / 锁编排（无状态、可单测） | 事务 |
| `dao` | 只放方法签名；SQL 一律在 `resources/mapper/business/scm/pricing/*.xml` | 注解 SQL |

### 2.3 数据模型

```text
customer ──(customer_type_id)──> customer_type
   │                                  │
   │ 1:N                              │ 1:N
   ▼                                  ▼
customer_sku_visibility        customer_type_price
   (白名单明细)                        │
   │                                  │
   │ 1:N                              │ 1:N
   ▼                                  ▼
customer_agreement_price      customer_type_price_operation_log
   │
   │ 1:N
   ▼
customer_agreement_price_operation_log

customer_price_batch_audit  （批次审计，独立）
product_spu ──1:N──> product_sku   （W1，只读引用；价格表 sku_id 指向它）
```

**无外键**（AGENTS.md §8）：全部引用靠应用层 + 索引保证。

**三个建模冲突的裁决（照抄 legacy 侧）**：

| 冲突 | 裁决 | 理由 |
|---|---|---|
| 客户类型价 vs 客户分级价 | **客户类型价**（`customer_type_id`） | legacy 唯一事实源；W2 已把 `customer_type` 做成可维护字典表 |
| SKU 级 vs SPU 级可见性 | **SKU 级 + 策略字段在 `customer` 上** | legacy；与 W2 `supplier_sku` 的 SKU 级口径一致 |
| 两表 vs 单表多态 | **两张表**（`customer_agreement_price` + `customer_type_price`） | legacy；C 的单表多态正是 C1/C6/C14 的根源 |

---

## 3. Migration 设计

### 3.1 命名与数量约束

| 约束 | 事实 |
|---|---|
| 编号起点 | 现有序号 V1–V9（V1–V5 SmartAdmin 系统，V6/V7 W1，V8/V9 W2）→ **W3 从 V10 起** |
| 数量 | **2 个**：`V10__scm_pricing.sql`（DDL）+ `V11__scm_pricing_permissions.sql`（菜单权限）。与 W1/W2 的「1 DDL + 1 权限」模式一致 |
| 不可回改 | V6–V9 哈希已冻结（`verify_w2_legacy.py` 校验）；**V10/V11 首次成功应用后同样冻结**，并在 W3 交付时生成 `v3-applied-migrations.sha256` |
| 禁改 V6/V7 | SKU option 是**纯读能力**，**不需要任何 DDL**，因此不触碰 V6/V7 |
| 唯一演进 | 只追加新 migration；不改已应用的 |
| 无外键 | 全部引用建索引，不建 FK |
| 命名 | 表名不带 `t_` 前缀（V2 风格）；列名 `created_at/updated_at/created_by/updated_by/deleted/version`（与 V6/V8 一致） |
| 精度 | 金额一律 `NUMERIC(18,4)` |

### 3.2 `V10__scm_pricing.sql`

> 头部注释（与 V6/V8 同风格）：
> `-- W3 approved Pricing aggregate. Immutable after first successful Flyway application.`
> `-- Scope: customer_agreement_price / customer_type_price / operation logs / batch audit / customer_sku_visibility + customer.visibility_policy. No foreign keys (AGENTS.md §8).`

#### 3.2.1 `customer_agreement_price`

```sql
CREATE TABLE customer_agreement_price (
    id             BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_id    BIGINT        NOT NULL,
    sku_id         BIGINT        NOT NULL,
    unit_price     NUMERIC(18,4) NOT NULL,
    effective_from TIMESTAMPTZ   NOT NULL,
    effective_to   TIMESTAMPTZ,
    version        INTEGER       NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64),
    updated_by     VARCHAR(64),
    CONSTRAINT ck_customer_agreement_price_amount  CHECK (unit_price >= 0),
    CONSTRAINT ck_customer_agreement_price_period  CHECK (
        effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_customer_agreement_price_version CHECK (version >= 0)
);
```

#### 3.2.2 `customer_type_price`

```sql
CREATE TABLE customer_type_price (
    id               BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_type_id BIGINT        NOT NULL,
    sku_id           BIGINT        NOT NULL,
    unit_price       NUMERIC(18,4) NOT NULL,
    effective_from   TIMESTAMPTZ   NOT NULL,
    effective_to     TIMESTAMPTZ,
    version          INTEGER       NOT NULL DEFAULT 0,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64),
    updated_by       VARCHAR(64),
    CONSTRAINT ck_customer_type_price_amount  CHECK (unit_price >= 0),
    CONSTRAINT ck_customer_type_price_period  CHECK (
        effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_customer_type_price_version CHECK (version >= 0)
);
```

#### 3.2.3 `customer_agreement_price_operation_log`

```sql
CREATE TABLE customer_agreement_price_operation_log (
    id                  BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    agreement_price_id  BIGINT      NOT NULL,
    operation_type      VARCHAR(20) NOT NULL,
    operator            VARCHAR(64) NOT NULL,
    before_data         JSONB,
    after_data          JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    CONSTRAINT ck_agreement_price_log_operation CHECK (
        operation_type IN ('CREATE','UPDATE','DELETE')),
    CONSTRAINT ck_agreement_price_log_before_data CHECK (
        before_data IS NULL OR jsonb_typeof(before_data) = 'object'),
    CONSTRAINT ck_agreement_price_log_after_data CHECK (
        after_data  IS NULL OR jsonb_typeof(after_data)  = 'object')
);
```

#### 3.2.4 `customer_type_price_operation_log`

同 3.2.3，主键列名 `customer_type_price_id`，约束名 `ck_customer_type_price_log_*`。

#### 3.2.5 `customer_price_batch_audit`

```sql
CREATE TABLE customer_price_batch_audit (
    id             BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    batch_key      VARCHAR(100) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    result         VARCHAR(16) NOT NULL,
    row_count      INTEGER     NOT NULL,
    error_data     JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64),
    CONSTRAINT ck_customer_price_batch_result CHECK (result IN ('SUCCESS','FAILED'))
);
```

#### 3.2.6 `customer_sku_visibility`

```sql
CREATE TABLE customer_sku_visibility (
    id         BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_id BIGINT     NOT NULL,
    sku_id      BIGINT     NOT NULL,
    version     INTEGER    NOT NULL DEFAULT 0,
    deleted     BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    CONSTRAINT ck_customer_sku_visibility_version CHECK (version >= 0)
);
```

#### 3.2.7 `customer` 增列（可见性策略）

```sql
-- 可见性策略落在客户主数据上（legacy 形状，见审计 §2.3.3）。
-- 默认 ALL_ENABLED 保证既有客户行为不变。
ALTER TABLE customer ADD COLUMN visibility_policy VARCHAR(20) NOT NULL DEFAULT 'ALL_ENABLED';
ALTER TABLE customer ADD CONSTRAINT ck_customer_visibility_policy
    CHECK (visibility_policy IN ('ALL_ENABLED','ALLOWLIST'));
```

> **这是 W3 唯一一处触碰 W2 表**。只 `ADD COLUMN`，不改列、不改约束、不改数据；对既有行全部落到默认值 `ALL_ENABLED`，与 W2 语义（无可见性 = 全可见）等价。
> **不加索引**：`visibility_policy` 只有两个值，且没有任何查询以它为主过滤条件。

### 3.3 `V11__scm_pricing_permissions.sql`

菜单 id 分配（基础 ≤300、W1 = 401–424、W2 = 431–434/441–445/451–454/461–464/471–475/481–482）：

| menu_id | 名称 | type | parent | path | component | perms |
|---|---|---|---|---|---|---|
| **425** | 规格查询 | 3 | 402（商品档案） | – | – | `scm:product:sku:query` |
| **435** | 客户 SKU 可见性 | 2 | 431（客户管理） | `/customer/sku-visibility-list` | `/business/scm/customer/customer-sku-visibility-list.vue` | – |
| **486** | 查询 | 3 | 435 | – | – | `scm:customer:visibility:query` |
| **487** | 维护 | 3 | 435 | – | – | `scm:customer:visibility:update` |
| **501** | 价格中心 | 1 | 0 | `/pricing` | – | – |
| **502** | 客户协议价 | 2 | 501 | `/pricing/agreement-price-list` | `/business/scm/pricing/agreement-price-list.vue` | – |
| **503** | 客户类型价 | 2 | 501 | `/pricing/customer-type-price-list` | `/business/scm/pricing/customer-type-price-list.vue` | – |
| **504** | 批量调价 | 2 | 501 | `/pricing/customer-type-price-batch` | `/business/scm/pricing/customer-type-price-batch.vue` | – |
| **505** | 价格历史 | 2 | 501 | `/pricing/price-history-list` | `/business/scm/pricing/price-history-list.vue` | – |
| **506** | 取价试算 | 2 | 501 | `/pricing/price-preview` | `/business/scm/pricing/price-preview.vue` | – |
| **511** | 查询 | 3 | 502 | – | – | `scm:pricing:agreement:query` |
| **512** | 新建 | 3 | 502 | – | – | `scm:pricing:agreement:add` |
| **513** | 编辑 | 3 | 502 | – | – | `scm:pricing:agreement:update` |
| **514** | 删除 | 3 | 502 | – | – | `scm:pricing:agreement:delete` |
| **521** | 查询 | 3 | 503 | – | – | `scm:pricing:type-price:query` |
| **522** | 新建 | 3 | 503 | – | – | `scm:pricing:type-price:add` |
| **523** | 编辑 | 3 | 503 | – | – | `scm:pricing:type-price:update` |
| **524** | 删除 | 3 | 503 | – | – | `scm:pricing:type-price:delete` |
| **525** | 批量调价 | 3 | 503 | – | – | `scm:pricing:type-price:batch` |
| **531** | 查询 | 3 | 505 | – | – | `scm:pricing:history:query` |
| **541** | 试算 | 3 | 506 | – | – | `scm:pricing:resolve:query` |

共 **21 个 menu_id**。种子写法照抄 V9（`ON CONFLICT (menu_id) DO NOTHING` + `t_role_menu` 授权 role 1 + `setval` 校正）：

```sql
-- W3 menu and permissions. menu_id 425 / 435 / 486-487 / 501-506 / 511-514 / 521-525 / 531 / 541.
-- 425 lands under the W1 product menu because the SKU option endpoint is a product read capability
-- shared by Pricing / Supplier / Purchase. 435 lands under the W2 customer menu (customer domain page).
-- component paths must match src/views/** exactly (router resolves ../views${component}).
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,context_menu_id,visible_flag,create_user_id)
VALUES (425,'规格查询',3,402,425,NULL,NULL,1,'scm:product:sku:query','scm:product:sku:query',402,true,1) ON CONFLICT (menu_id) DO NOTHING;
-- ...（其余 20 行同构）
INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1,m.menu_id FROM t_menu m
WHERE m.menu_id IN (425,435,486,487,501,502,503,504,505,506,511,512,513,514,521,522,523,524,525,531,541)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id=1 AND r.menu_id=m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), false);
```

**权限码规范**：`scm:<domain>:<action>`，子资源加一级 → `scm:pricing:agreement:*` / `scm:pricing:type-price:*` / `scm:pricing:history:query` / `scm:pricing:resolve:query` / `scm:customer:visibility:*` / `scm:product:sku:query`。

**不做价格「导出」「审批」权限**（legacy 无导出、无审批流）。

### 3.4 PostgreSQL 索引设计（逐条给理由）

#### `customer_agreement_price`

| 索引 | 定义 | 用途 | 来源 |
|---|---|---|---|
| PK | `id` | – | legacy |
| `idx_customer_agreement_price_lookup` | `(customer_id, sku_id, effective_from, effective_to) WHERE deleted = FALSE` | **取价主路径**（`selectEffective`）+ 重叠检测（`countOverlapping` 前缀命中 `customer_id, sku_id`）+ 引用检查（`customer_id` 前缀） | legacy（`V3:80-82`） |
| `idx_customer_agreement_price_effective` | `(effective_from DESC, id DESC) WHERE deleted = FALSE` | 列表页默认排序 `effective_from DESC, id DESC`（无客户/SKU 过滤时） | **W3 新增** |
| `idx_customer_agreement_price_sku_id` | `(sku_id) WHERE deleted = FALSE` | SKU 侧反查（「该 SKU 被哪些客户定价」）与 SKU 删除前的引用检查 | **W3 新增** |

#### `customer_type_price`

| 索引 | 定义 | 用途 | 来源 |
|---|---|---|---|
| PK | `id` | – | legacy |
| `idx_customer_type_price_lookup` | `(customer_type_id, sku_id, effective_from, effective_to) WHERE deleted = FALSE` | 取价主路径 + 重叠检测 + 类型引用检查 | legacy（`V34:19-21`） |
| `idx_customer_type_price_effective` | `(effective_from DESC, id DESC) WHERE deleted = FALSE` | 列表页默认排序 | **W3 新增** |
| `idx_customer_type_price_sku_id` | `(sku_id) WHERE deleted = FALSE` | SKU 侧反查 | **W3 新增** |

#### 两张 operation_log

| 索引 | 定义 | 来源 |
|---|---|---|
| `idx_agreement_price_log_price_created` | `(agreement_price_id, created_at DESC)` | legacy（`V7:19-20`） |
| `idx_agreement_price_log_created` | `(created_at DESC)` | legacy（`V35:26-27`） |
| `idx_customer_type_price_log_price_created` | `(customer_type_price_id, created_at DESC)` | legacy（`V35:20-21`） |
| `idx_customer_type_price_log_created` | `(created_at DESC)` | legacy（`V35:23-24`） |

> 历史查询是 `UNION ALL` 两张 log 表后按 `operated_at DESC, id DESC` 排序并分页。`(created_at DESC)` 索引让两个分支各自走索引有序扫描，避免全表排序。

#### `customer_price_batch_audit`

| 索引 | 定义 | 用途 | 来源 |
|---|---|---|---|
| `idx_customer_price_batch_audit_key` | `(batch_key, created_at DESC)` | 按批次号查审计 | legacy（`V34:35-36`） |
| `uk_customer_price_batch_audit_success` | `UNIQUE (batch_key) WHERE result = 'SUCCESS'` | **幂等保护**：同一 `batchKey` 不允许成功两次（见 §4.8 / G5） | **W3 新增** |

> 用**部分唯一索引**而非全表唯一：`FAILED` 行可重复（同一批次号可以失败多次再成功），只有 `SUCCESS` 唯一。

#### `customer_sku_visibility`

| 索引 | 定义 | 用途 | 来源 |
|---|---|---|---|
| `uk_customer_sku_visibility_active` | `UNIQUE (customer_id, sku_id) WHERE deleted = FALSE` | 白名单去重 + 差量加载主路径（`customer_id` 前缀） | legacy（`V3:55-56`） |
| `idx_customer_sku_visibility_sku_id` | `(sku_id)` | 反向视图（某 SKU 被哪些客户授权） | legacy（`V3:57-58`） |

#### 不建的索引（明确记录）

| 不建 | 理由 |
|---|---|
| `customer.visibility_policy` 索引 | 只有两个值，选择性极低；无主过滤查询 |
| `unit_price` 索引 | 没有任何按价格数值过滤的查询 |
| `deleted` 单列索引 | 一律用 partial index（`WHERE deleted = FALSE`）替代 |

### 3.5 不引入的 DB 级约束（**明确记录为已知风险**）

| 不引入 | 理由 | 风险 | 缓解 |
|---|---|---|---|
| `EXCLUDE USING gist (customer_id WITH =, sku_id WITH =, tstzrange(effective_from, COALESCE(effective_to,'infinity'), '[)') WITH &&)` | 需要 `btree_gist` 扩展（通常要求 superuser），legacy 未使用；引入后新增 `23P01` 错误路径与部署依赖 | 重叠只靠应用层 `FOR UPDATE`，旁路写入可破坏不变量（D7） | 应用层 `lockCustomer` / `lockCustomerType` + `countOverlapping`；Q4=A：W3 不引入；保留真实 PG 并发 IT |
| 唯一索引 `(customer_id, sku_id, effective_from) WHERE deleted = FALSE` | 不能真正防止区间重叠，Q4 明确不采用 | 同 D7（更窄） | Q4=A：W3 不引入；保留真实 PG 并发 IT |
| DB 外键 | AGENTS.md §8 明令禁止 | 引用完整性靠应用层 | 删除前的引用检查（§4.10） |

---

## 4. 后端设计

### 4.1 API 契约（统一 `/scm/**`，SmartAdmin 风格）

#### 4.1.1 客户协议价 `/scm/pricing/agreement-price`

| 方法 | URL | 权限 | 操作日志 |
|---|---|---|---|
| POST | `/query` | `scm:pricing:agreement:query` | – |
| GET | `/detail/{id}` | `scm:pricing:agreement:query` | – |
| POST | `/add` | `scm:pricing:agreement:add` | ✅ |
| POST | `/update` | `scm:pricing:agreement:update` | ✅ |
| POST | `/delete` | `scm:pricing:agreement:delete` | ✅ |

请求 / 响应：

```jsonc
// POST /query
{ "pageNum":1, "pageSize":20, "keyword":"春风",
  "customerId":123, "skuId":456, "effectiveFrom":"2026-09-01T00:00:00+08:00",
  "effectiveTo":"2026-10-01T00:00:00+08:00",
  "sortItemList":[{"column":"effective_from","isAsc":false}] }
→ { "code":0, "ok":true, "data": { "list":[AgreementPriceVO], "total":12, "pageNum":1, "pageSize":20 } }

// AgreementPriceVO
{ "agreementPriceId":88, "version":2,
  "customerId":123, "customerCode":"C001", "customerName":"春风超市",
  "skuId":456, "skuCode":"SKU-0001", "productName":"本地菠菜",
  "specName":"500g/袋", "specValues":{"规格":"500g"},
  "unitPrice":"6.5000",
  "effectiveFrom":"2026-09-01T00:00:00+08:00",
  "effectiveTo":null,                    // null = 长期有效
  "updatedAt":"2026-09-15T10:00:00+08:00" }

// POST /add  → ResponseDTO<Long>  (agreementPriceId)
// POST /update → ResponseDTO<Void>
// POST /delete → ResponseDTO<Void>
{ "agreementPriceId":88, "version":2 }
```

`AgreementPriceAddForm`：

```java
@Data public class AgreementPriceAddForm {
    @NotNull private Long customerId;
    @NotNull private Long skuId;
    @Pattern(regexp = ScmDecimalStrings.PATTERN)                    // "^\\d{1,14}(\\.\\d{1,4})?$"
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    @NotNull private String unitPrice;                              // 4 位定点字符串
    @NotNull private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;                             // 可空 = 长期有效
}
// UpdateForm extends AddForm { @NotNull Long agreementPriceId; @NotNull Integer version; }
// DeleteForm { @NotNull Long agreementPriceId; @NotNull Integer version; }
```

> **金额入参只接受 JSON 字符串**（`ScmStrictDecimalStringDeserializer`），出参统一 4 位小数字符串（`ScmFixedScale4Serializer` + `nullsUsing`）——与 W1/W2 完全一致。

#### 4.1.2 客户类型价 `/scm/pricing/type-price`

端点与权限同构：`query` / `detail/{id}` / `add` / `update` / `delete`（`scm:pricing:type-price:*`）+ `batch`（`scm:pricing:type-price:batch`）。

`CustomerTypePriceVO` 把 `customerId/Code/Name` 换成 `customerTypeId/TypeCode/TypeName`。

#### 4.1.3 批量调价 `POST /scm/pricing/type-price/batch`

```jsonc
// 请求
{ "batchKey":"PRICE-20260915-01",
  "rows":[
    { "rowNumber":1, "customerTypeId":3, "skuId":456, "unitPrice":"6.5000",
      "effectiveFrom":"2026-09-16T00:00:00+08:00", "effectiveTo":null },
    { "rowNumber":2, "customerTypeId":7, "skuId":789, "unitPrice":"-1.0000",
      "effectiveFrom":"2026-09-16T00:00:00+08:00", "effectiveTo":"2026-09-01T00:00:00+08:00" }
  ] }

// 响应（HTTP 200, code=0）—— 全部成功
{ "code":0, "ok":true, "data": {
    "batchKey":"PRICE-20260915-01", "committed":true, "rowCount":2,
    "ids":[901,902], "failures":[] } }

// 响应（HTTP 200, code=0）—— 任一行失败 ⇒ 整批回滚
{ "code":0, "ok":true, "data": {
    "batchKey":"PRICE-20260915-01", "committed":false, "rowCount":0, "ids":[],
    "failures":[
      { "rowNumber":2, "customerTypeId":7, "skuId":789,
        "code":40031, "message":"结束时间必须晚于开始时间" }
    ] } }
```

**设计理由（对 legacy D14 的修正）**：legacy 的错误通道是 `ScmBusinessException → ResponseDTO.error(code)`，**结构上没有 `data` 字段**，无法承载逐行报告；而规格 §7 明确要求「响应必须包含每个失败行的行号、SKU/对象标识、稳定错误码和人类可读消息」。因此 W3 把批量结果定义为**校验报告**（`code=0` + `committed=false`），而不是错误。
**这是本设计唯一一处偏离「业务错误 HTTP 200 + 非 0 code」惯例的地方，已列为待确认项（§12 Q1）。**

#### 4.1.4 价格历史 `POST /scm/pricing/history/query`

```jsonc
{ "pageNum":1, "pageSize":20,
  "source":"AGREEMENT",                    // AGREEMENT | CUSTOMER_TYPE | null
  "customerId":123, "customerTypeId":null, "skuId":456,
  "operationType":"UPDATE",                // CREATE | UPDATE | DELETE | null
  "effectiveFrom":"2026-09-01T00:00:00+08:00", "effectiveTo":null,
  "operatedFrom":"2026-09-01T00:00:00+08:00", "operatedTo":null }
→ PageResult<PriceHistoryVO>

// PriceHistoryVO
{ "historyId":5012, "source":"AGREEMENT", "priceId":88,
  "customerId":123, "customerName":"春风超市",
  "customerTypeId":null, "customerTypeName":null,
  "skuId":456, "skuCode":"SKU-0001", "productName":"本地菠菜", "specName":"500g/袋",
  "operationType":"UPDATE", "operator":"ADMIN:1",
  "operatedAt":"2026-09-15T10:00:00+08:00",
  "beforeData":{...}, "afterData":{...},
  "currentUnitPrice":"6.5000", "currentEffectiveFrom":"...", "currentEffectiveTo":null,
  "currentDeleted":false }
```

筛选语义照抄 legacy（P26）：`customerId` 只作用 AGREEMENT，`customerTypeId` 只作用 CUSTOMER_TYPE，`source` 非法值归一为 `null`（不筛选），`effectiveFrom/To` 按当前记录区间做重叠匹配，`operatedFrom/To` 按操作时间过滤，排序 `operated_at DESC, id DESC`。
**W3 修正 D13**：`current_*` 只在 `LEFT JOIN` 命中时返回；硬删（`p` 为 null）时 `currentUnitPrice=null` 且 `currentDeleted=true`，VO 层显式区分。

#### 4.1.5 取价试算 `POST /scm/pricing/resolve`

```jsonc
{ "customerId":123,
  "at":"2026-09-15T10:00:00+08:00",     // 可选；缺省 = 服务端当前时刻。客户端不得做本地时区换算
  "skuIds":[456,457] }
→ { "code":0, "ok":true, "data": {
    "customerId":123, "customerTypeId":3, "customerTypeName":"企业客户",
    "at":"2026-09-15T02:00:00Z",
    "items":[
      { "skuId":456, "skuCode":"SKU-0001", "productName":"本地菠菜", "specName":"500g/袋",
        "unitPrice":"6.5000", "priceStatus":"PRICED", "priceSource":"AGREEMENT",
        "sourceRecordId":88, "unpricedReason":null, "sellable":true, "unavailableReason":null },
      { "skuId":457, "skuCode":"SKU-0002", "productName":"本地生菜", "specName":"500g/袋",
        "unitPrice":"8.0000", "priceStatus":"PRICED", "priceSource":"MARKET",
        "sourceRecordId":null, "unpricedReason":null, "sellable":true, "unavailableReason":null },
      { "skuId":999, "skuCode":null, "productName":null, "specName":null,
        "unitPrice":null, "priceStatus":"UNPRICED", "priceSource":null,
        "sourceRecordId":null, "unpricedReason":"NO_PRICE_SOURCE", "sellable":false, "unavailableReason":"SKU_NOT_FOUND" }
    ] } }
```

**这是新增能力（G3）**：legacy 的 Resolver 没有对外端点。W3 需要它来让「优先级 / 生效时间 / UNPRICED」可验证、可演示。
**边界声明**：该端点**只读**，不写库、不锁库、不产生订单，因此不属于「Sales Order / Mall」范围。

#### 4.1.6 客户 SKU 可见性反查 `POST /scm/customer/visibility/reverse/query`

```jsonc
{ "pageNum":1, "pageSize":20, "customerId":null, "skuId":456, "visibilityPolicy":"ALLOWLIST" }
→ PageResult<CustomerSkuVisibilityReverseVO>
// { customerId, customerCode, customerName, customerTypeName, visibilityPolicy,
//   skuId, skuCode, productName, specName, skuStatus, spuStatus,
//   createdAt, createdBy }
```

只读。**写入口唯一**：客户新增/更新（§4.6）。

#### 4.1.7 SKU option `POST /scm/product/sku/option-list`（W3 P0）

```jsonc
{ "keyword":"菠菜",           // 可选，匹配 sku_code / barcode / spec_name / spu.name / spu.alias
  "status":"ON_SHELF",       // 可选，ON_SHELF | OFF_SHELF | null(=全部)
  "spuId":null,              // 可选，限定某个 SPU
  "limit":50 }               // 可选，默认 50，@Max(200)
→ { "code":0, "ok":true, "data": {
    "options":[
      { "skuId":456, "skuCode":"SKU-0001", "spuId":12, "productName":"本地菠菜",
        "specName":"500g/袋", "specValues":{"规格":"500g"}, "saleUnit":"袋",
        "productType":"STANDARD", "status":"ON_SHELF",
        "spuStatus":"ON_SHELF", "categoryId":33, "categoryStatus":"ENABLED",
        "marketPrice":"8.0000" }
    ],
    "truncated":false } }
```

**设计要点**：

| 项 | 决策 | 理由 |
|---|---|---|
| 端点是 SKU 级还是 SPU 级 | **SKU 级** | 修 G2/A14：`sku-select` 目前按 SPU 分页拍平，超过 500 个上架 SPU 时选项不完整 |
| 分页 or 上限 | **上限 `limit`（默认 50，max 200）+ `truncated` 标记**，不返回 total | option 列表是「远程搜索」场景，不是数据浏览；避免分页 UI 复杂度 |
| 是否要求 keyword | 不要求；keyword 为空时按 `sku_code` 升序返回前 `limit` 条并置 `truncated` | 兼容「打开即用」的下拉体验 |
| 是否只返回上架 | 由 `status` 参数控制，默认**返回全部**（含下架），并在 DTO 里带 `status` / `spuStatus` / `categoryStatus` | legacy 各页面需求不同（协议价允许给下架 SKU 定价是缺陷 A9，但**可见性白名单**需要看到下架 SKU）；把判断交给调用方，DTO 给足信息 |
| 权限 | `scm:product:sku:query`（新） | 供 Pricing / Supplier / 后续 Purchase 共用；不复用 `scm:product:query`（避免跨域权限耦合） |
| 是否改 W1 | **零修改**：新 controller + 新 dao + 新 XML + 新 service + 新 form/VO | 遵守「只允许新增只读查询能力」 |

SQL（新文件 `mapper/business/scm/pricing/../product/ProductSkuOptionDao.xml`）：

```sql
SELECT sku.id AS sku_id, sku.sku_code, sku.spu_id, sku.spec_name, sku.spec_values,
       sku.sale_unit, sku.product_type, sku.status, sku.market_price,
       spu.name AS product_name, spu.alias AS product_alias,
       spu.status AS spu_status, spu.category_id,
       cat.status AS category_status
FROM product_sku sku
JOIN product_spu spu ON spu.id = sku.spu_id AND spu.deleted = FALSE
LEFT JOIN product_category cat ON cat.id = spu.category_id AND cat.deleted = FALSE
WHERE sku.deleted = FALSE
  <if test="status != null">AND sku.status = #{status}</if>
  <if test="spuId != null">AND sku.spu_id = #{spuId}</if>
  <if test="keyword != null and keyword != ''">
    AND (sku.sku_code ILIKE CONCAT('%', #{keyword}, '%')
      OR sku.barcode ILIKE CONCAT('%', #{keyword}, '%')
      OR sku.spec_name ILIKE CONCAT('%', #{keyword}, '%')
      OR spu.name ILIKE CONCAT('%', #{keyword}, '%')
      OR spu.alias ILIKE CONCAT('%', #{keyword}, '%'))
  </if>
ORDER BY sku.sku_code ASC, sku.id ASC
LIMIT #{limitPlusOne}    -- limit + 1，用于判断 truncated，再在 Service 里截断
```

> **PG 适配注意**：`ILIKE` 是 PostgreSQL 原生，无需 legacy 的 `INSTR → STRPOS` 转换。
> **索引现状**：`idx_product_sku_spu_id`、`idx_product_sku_status`、`uk_product_sku_code_active` 已存在（V6）。keyword 前缀模糊搜索走顺序扫描是可接受的（SKU 量级为千级），**W3 不新增索引**，避免触碰 V6。

#### 4.1.8 API 总览

| # | 方法 | URL | 权限 | 写 |
|---|---|---|---|---|
| 1 | POST | `/scm/pricing/agreement-price/query` | `scm:pricing:agreement:query` | – |
| 2 | GET | `/scm/pricing/agreement-price/detail/{id}` | `scm:pricing:agreement:query` | – |
| 3 | POST | `/scm/pricing/agreement-price/add` | `scm:pricing:agreement:add` | ✅ |
| 4 | POST | `/scm/pricing/agreement-price/update` | `scm:pricing:agreement:update` | ✅ |
| 5 | POST | `/scm/pricing/agreement-price/delete` | `scm:pricing:agreement:delete` | ✅ |
| 6 | POST | `/scm/pricing/type-price/query` | `scm:pricing:type-price:query` | – |
| 7 | GET | `/scm/pricing/type-price/detail/{id}` | `scm:pricing:type-price:query` | – |
| 8 | POST | `/scm/pricing/type-price/add` | `scm:pricing:type-price:add` | ✅ |
| 9 | POST | `/scm/pricing/type-price/update` | `scm:pricing:type-price:update` | ✅ |
| 10 | POST | `/scm/pricing/type-price/delete` | `scm:pricing:type-price:delete` | ✅ |
| 11 | POST | `/scm/pricing/type-price/batch` | `scm:pricing:type-price:batch` | ✅ |
| 12 | POST | `/scm/pricing/history/query` | `scm:pricing:history:query` | – |
| 13 | POST | `/scm/pricing/resolve` | `scm:pricing:resolve:query` | – |
| 14 | POST | `/scm/customer/visibility/reverse/query` | `scm:customer:visibility:query` | – |
| 15 | POST | `/scm/product/sku/option-list` | `scm:product:sku:query` | – |

### 4.2 关键 VO 字段

| VO | 字段 |
|---|---|
| `AgreementPriceVO` | `agreementPriceId`, `version`, `customerId`, `customerCode`, `customerName`, `skuId`, `skuCode`, `productName`, `specName`, `specValues`, `unitPrice`(4位串), `effectiveFrom`, `effectiveTo`(可 null), `updatedAt` |
| `CustomerTypePriceVO` | `customerTypePriceId`, `version`, `customerTypeId`, `customerTypeCode`, `customerTypeName`, `skuId`, `skuCode`, `productName`, `specName`, `specValues`, `unitPrice`, `effectiveFrom`, `effectiveTo`, `updatedAt` |
| `PriceBatchResultVO` | `batchKey`, `committed`, `rowCount`, `ids`, `failures` |
| `PriceBatchRowFailureVO` | `rowNumber`, `customerTypeId`, `skuId`, `code`, `message` |
| `PriceHistoryVO` | 见 §4.1.4 |
| `ResolvedPriceVO` | `skuId`, `skuCode`, `productName`, `specName`, `unitPrice`(可 null), `priceStatus`, `priceSource`(可 null), `sourceRecordId`(可 null), `unpricedReason`(可 null，仅 NO_PRICE_SOURCE), `sellable`, `unavailableReason`(可 null) |
| `PriceResolveResultVO` | `customerId`, `customerTypeId`, `customerTypeName`, `at`, `items` |
| `CustomerSkuVisibilityVO` | `id`, `version`, `skuId` |
| `CustomerSkuVisibilityReverseVO` | 见 §4.1.6 |
| `ProductSkuOptionVO` | `skuId`, `skuCode`, `spuId`, `productName`, `specName`, `specValues`, `saleUnit`, `productType`, `status`, `spuStatus`, `categoryId`, `categoryStatus`, `marketPrice` |
| `ProductSkuOptionListVO` | `options`, `truncated` |

**命名规范**：与 W1/W2 一致 —— 主键属性名用 `<domain>Id`（不用裸 `id`），`BeanUtils.copyProperties` 后显式 `setXxxId(entity.getId())`。VO 上的 `version` 一律返回（前端编辑/删除必须回传）。

### 4.3 Legacy 不变量 → V2 实现映射

| 编号 | 不变量 | V2 实现 | 与 legacy 的差异 |
|---|---|---|---|
| P1 | AGREEMENT → CUSTOMER_TYPE → MARKET，命中即返回 | `PriceResolver.resolve`：两批 `selectEffective` + `toMap(..., (a,b)->a)` + 逐 SKU 短路 | **无差异**（照抄结构） |
| P2 | `sourceRecordId` 随结果返回 | `ResolvedPriceVO.sourceRecordId`；MARKET 时为 `null` | 无差异 |
| P3 | 类型价按客户当前启用类型 | `customerService.requireTradable(customerId)` 取 `customerTypeId` | 无差异 |
| P4 | 客户启用 + SKU 可下单前置守卫 | **拆成两个方法**：软模式逐 SKU 独立返回价格状态与可售状态；`requireResolvable` 严格模式抛 `SKU_NOT_SELLABLE` | **新增拆分**（见 §4.4 / Q2） |
| P5 | 半开区间 `[from, to)` | SQL 照抄：`effective_from <= #{at} AND (effective_to IS NULL OR effective_to > #{at})` | 无差异 |
| P6 | `to IS NULL` = 长期有效 | 同上 + `ck_*_period` CHECK | 无差异 |
| P7 | 同客户/类型 + SKU 不得重叠 | `PricePeriodOverlapGuard.guard(...)`：锁行 → `countOverlapping` → 抛 40933/40935 | 无差异（仍不加 DB 排除约束） |
| P8 | 重叠判定与生效判定口径一致 | `countOverlapping` SQL 照抄：`effective_from < COALESCE(#{to},'infinity') AND (effective_to IS NULL OR effective_to > #{from})` | **补 `jdbcType=TIMESTAMP_WITH_TIMEZONE`** 修 D18 |
| P9 | `to > from` 双保险 | `PricePeriodValidator`（40031）+ DB CHECK | 无差异 |
| P10 | 决胜 `effective_from DESC, id DESC` | SQL `ORDER BY effective_from DESC, id DESC` + `toMap` 保留首元素 | **新增显式注释 + 集成测试**（修 D8 的「无告警」） |
| P11 | null = UNPRICED；非 null = PRICED | `ResolvedPriceVO`：`priceStatus = unitPrice == null ? UNPRICED : PRICED` | 无差异 |
| P12 | 0.0000 是有效价 | 校验器只拒负（`signum() < 0`）；测试固化 0 元 | 无差异 |
| P13 | UNPRICED 消费方语义 | **W3 只保证契约**（`priceStatus` + `unpricedReason`）；消费方在 W4/W6 | 无差异（W3 不实现消费方） |
| P14 | 只有 ALL_ENABLED / ALLOWLIST | `ScmVisibilityPolicyEnum`（枚举） + DB CHECK | 无差异 |
| P15 | ALLOWLIST 空 = 全部不可见 | `CustomerSkuVisibilityService.visibleSkuIds` 返回空集，**不回退** | 无差异 |
| P16 | 可见性硬门禁 | `PriceResolver` 软模式记 `sellable=false, unavailableReason=NOT_VISIBLE`；`requireResolvable` 抛 `SKU_NOT_SELLABLE` | 语义拆分（同 P4） |
| P17 | 可见性整表替换差量 + 乐观锁 | `CustomerSkuVisibilityChangeSet.between` + `CustomerSkuVisibilityDao` 差量落库 | 无差异；**`updated_by` 用真实操作人**（修 D5） |
| P18 | 可见性影响范围 | W3 只落「读能力」（`visibleSkuIds` / `requireVisible`）；消费点在 W4/W6 | 无差异 |
| P19 | 批量整批回滚 | `PriceBatchWriter.write` 单事务；`PriceBatchService` 负责失败审计 | 无差异 |
| P20 | 行号定位 + FAILED 审计 | **预校验阶段收集全部失败行**（不抛异常），失败则整批不写 + 写 FAILED 审计 | **升级**（修 D3/D14，见 §4.8） |
| P21 | 行上限 500 / 批次号 100 | `@Size(max=500)` / `@NotBlank @Size(max=100)` | 无差异 |
| P22 | 批次审计字段 | `customer_price_batch_audit` 同构 + `error_data` 存 `failures[]` 全量 | **增强**（legacy 只存单条） |
| P23 | 锁价属 Order，不属 Pricing | `ScmPriceSourceEnum` 只有 3 值；**不建任何锁价字段** | 无差异（严格排除） |
| P24 | 增删改必留痕 | 每个写方法在同一 `@Transactional` 内 `log(...)` | 无差异 |
| P25 | 快照语义（含 DELETE 的 version+1） | `PriceSnapshot.of(entity)` 纯函数 + DELETE 分支补 `deleted=true, version+1` | 无差异 |
| P26 | 历史筛选正交 | `PriceHistoryQueryService` + `PriceHistoryDao.selectHistoryPage` 照抄 union | 无差异 |
| P27 | 引用检查 | `CustomerService.assertNotReferenced` 启用 40939；`CustomerTypeService` 扩展 40938 | **启用 W2 预留钩子**（修 D17） |

### 4.4 Price Resolver 设计（双模式，Q2 人工修订）

价格与可售性独立解析，禁止因不可售而丢弃已有价格。

```text
priceStatus: PRICED | UNPRICED
sellable: true | false
unpricedReason: null | NO_PRICE_SOURCE
unavailableReason: null | SKU_NOT_FOUND | SKU_OFF_SHELF | SPU_OFF_SHELF | CATEGORY_DISABLED | NOT_VISIBLE
```

1. 校验客户可交易、当前客户类型有效；无效客户仍返回客户级业务错误。
2. 批量读取 SKU（不以可售状态过滤）、可见性、当前协议价、当前客户类型价。
3. 独立按 SKU 不存在 → SKU 下架 → SPU 下架 → 分类不可用 → 客户不可见，确定 `sellable` 和 `unavailableReason`。
4. 独立按 AGREEMENT → CUSTOMER_TYPE → MARKET 选择有效价格；非 null（包含 0.0000）即 PRICED。
5. 只有当前时点完全没有任何有效价格来源，才返回 UNPRICED、unitPrice=null、unpricedReason=NO_PRICE_SOURCE。
6. SKU 不存在本身不是缺价原因；若同时查不到任何价格来源，可同时返回 UNPRICED/NO_PRICE_SOURCE 与 sellable=false/SKU_NOT_FOUND。
7. `/scm/pricing/resolve` 返回逐 SKU 软结果；`requireResolvable` 遇到 `sellable=false OR priceStatus=UNPRICED` 抛 SKU_NOT_SELLABLE（40949）。

示例：市场价 5.0000 + SKU 下架 → PRICED、unitPrice="5.0000"、sellable=false、unavailableReason=SKU_OFF_SHELF、unpricedReason=null。

W1 `market_price NOT NULL` 保持不变。正常存在 SKU 的 NO_PRICE_SOURCE 暂不可达是允许的；单测可模拟未来缺价源，但 PG IT 不得改列约束造状态。

### 4.5 错误码

#### 4.5.1 `PricingErrorCode`（新建，`module/scm/pricing/constant`）

| 常量 | 码值 | 消息 | 依据 |
|---|---|---|---|
| `PRICE_INVALID` | **40030** | 价格不能小于零 | legacy `PRICE_INVALID`；**W2 已为本码预留** |
| `PERIOD_INVALID` | **40031** | 结束时间必须晚于开始时间 | legacy `PERIOD_INVALID`；**W2 已为本码预留** |
| `AGREEMENT_PRICE_NOT_FOUND` | **40432** | 协议价不存在 | legacy 原码 |
| `CUSTOMER_TYPE_PRICE_NOT_FOUND` | **40433** | 客户类型价不存在 | legacy 原码 |
| `AGREEMENT_PRICE_OVERLAP` | **40933** | 协议价有效期重叠 | legacy 原码 |
| `CUSTOMER_TYPE_PRICE_OVERLAP` | **40935** | 客户类型价有效期重叠 | legacy 原码 |
| `PRICE_BATCH_ROW_INVALID` | **40035** | 批量调价存在非法行 | **W3 新增**（legacy 用 30001 兜底） |
| `PRICE_BATCH_KEY_DUPLICATE` | **40948** | 批次号已成功提交，请勿重复提交 | **W3 新增**（幂等保护，修 G5） |
| `SKU_NOT_SELLABLE` | **40949** | SKU 不可售或对该客户不可见 | **W3 新增**（严格模式；替代 legacy 40931 的跨域复用） |
| `PRICE_RESOLVE_CUSTOMER_TYPE_MISSING` | **40036** | 客户未设置有效的客户类型 | **W3 新增**（`customer_type_id` 指向停用/删除类型时） |

#### 4.5.2 `CustomerErrorCode` 增补（W3）

| 常量 | 码值 | 消息 | 依据 |
|---|---|---|---|
| `VISIBILITY_NOT_OWNED` | **40932** | 可见性记录不属于当前客户 | legacy 原码 |
| `VISIBILITY_POLICY_CONFLICT` | **40033** | 全部在售策略不能提交可见性明细 | legacy 行为（`CustomerServiceTest.rejectsVisibilityRowsForAllEnabledPolicy`），legacy 未定义专用码 |
| `VISIBILITY_ITEM_INVALID` | **40034** | 可见性明细行不合法（更换已存在行的 SKU / 请求内 SKU 重复） | legacy 行为，legacy 复用 40931；V2 不复用 |

复用（不新增）：
- `CUSTOMER_NOT_TRADABLE(40930)` —— 价格解析与可见性的客户状态门禁（legacy `CUSTOMER_DISABLED`）
- `CUSTOMER_TYPE_NOT_FOUND(40431)`
- `CUSTOMER_TYPE_IN_USE(40938)` —— **W3 扩展语义**：被客户**或客户类型价**引用时不可删
- `CUSTOMER_REFERENCED(40939)` —— **W3 启用**：被协议价 / 类型价 / 可见性引用时不可删客户

#### 4.5.3 码值占用总表（避免撞码）

| 区间 | 归属 | W3 是否占用 |
|---|---|---|
| 40010 / 40011 / 40020–40026 | W1 Product | – |
| 40030 / 40031 | **W3 Pricing**（W2 预留） | ✅ |
| 40032 | W2 Customer（`CUSTOMER_PARENT_INVALID`） | – |
| 40033 / 40034 | **W3 Customer 可见性** | ✅ |
| 40035 / 40036 | **W3 Pricing** | ✅ |
| 40040 | W2 Supplier | – |
| 40410 / 40420 | W1 | – |
| 40430 / 40431 | W2 Customer | – |
| 40432 / 40433 | **W3 Pricing** | ✅ |
| 40440 / 40442 | W2 Supplier | – |
| 40910 / 40911 / 40920–40923 | W1 | – |
| **40921** | `ScmCommonErrorCode.VERSION_CONFLICT`（+ W1 `ProductErrorCode` 重复定义） | 复用 |
| 40930 / 40936–40939 | W2 Customer | 复用/扩展 |
| 40931 | legacy `SKU_NOT_VISIBLE` | **不沿用**（改用 `SKU_NOT_SELLABLE=40949`） |
| 40932 | **W3 Customer 可见性** | ✅ |
| 40933 / 40935 | **W3 Pricing** | ✅ |
| 40934 | legacy `VERSION_CONFLICT` | **不沿用**（V2 统一 40921） |
| 40940–40947 | W2 Supplier | – |
| 40948 / 40949 | **W3 Pricing** | ✅ |
| 10001 | SmartAdmin `SYSTEM_ERROR` | – |
| 30001 | SmartAdmin 参数校验失败 | 复用（Bean Validation） |

#### 4.5.4 Step 0 纯 Java 重构（W3 开始前）

保留 W1 ProductErrorCode 及所有既有 Product 文件；新增 W3 代码引用 ScmCommonErrorCode.VERSION_CONFLICT。双定义清理另案处理。

### 4.6 可见性设计（写入口唯一 + 只读反查）

**唯一写入口：客户新增 / 更新**（legacy 语义，P17）。

```java
// CustomerAddForm / CustomerUpdateForm 增补
@NotNull private String visibilityPolicy;                    // ALL_ENABLED | ALLOWLIST
@NotNull @Valid private List<CustomerSkuVisibilityItemForm> visibilities;  // ALL_ENABLED 时必须为空

// CustomerSkuVisibilityItemForm
public class CustomerSkuVisibilityItemForm {
    private Long id;            // 已存在行回填（乐观锁）
    private Integer version;    // 同上
    @NotNull private Long skuId;
}
```

差量语义照抄 legacy `CustomerSkuVisibilityChangeSet`，**五项校验各自绑定唯一错误码**：

| 校验 | 错误码 | 消息 |
| --- | --- | --- |
| `ALL_ENABLED` 却提交非空 `visibilities` | `VISIBILITY_POLICY_CONFLICT` (**40033**) | 全部在售策略不能提交可见性明细 |
| 已存在行更换 `skuId` | `VISIBILITY_ITEM_INVALID` (**40034**) | 可见性明细行不合法 |
| 请求内 `skuId` 重复 | `VISIBILITY_ITEM_INVALID` (**40034**) | 可见性明细行不合法 |
| 已存在行未回填 `id` / `version` | `VISIBILITY_ITEM_INVALID` (**40034**) | 可见性明细行不合法 |
| `id` 不属于该客户 | `VISIBILITY_NOT_OWNED` (**40932**) | 可见性记录不属于当前客户 |
| `ALLOWLIST` 明细含不可售 SKU（SKU 未上架 / SPU 未上架 / 分类停用） | `VISIBILITY_SKU_NOT_SELLABLE` (**40037**) | 可见性明细包含不可售 SKU |

- `ALLOWLIST` 时明细为空 → **允许**（= 一个 SKU 都不可见，P15），不报错。
- `40033` 与 `40034` 分工明确、不合并：前者是「策略与明细矛盾」，后者是「明细行本身不合法」。
- 不复用 legacy 的 `40931`（legacy 将其重载为「请求内重复 SKU」，D6 语义重载）；`40037` 为本域新增码，不跨域复用 Pricing 的 `SKU_NOT_SELLABLE(40949)`。
- 校验顺序固定：`40033`（策略）→ `40034`（行合法性，含请求内重复）→ `40932`（归属）→ `40037`（可售性），保证同请求多错时报码稳定可测。

**落库（差量四段式，照抄 W2 `SupplierSkuSyncManager` 范式）**：
```text
1) 锁 customer 行（FOR UPDATE）——与协议价写路径共用同一把锁，锁序 customer 最前
2) 加载现有 active 明细
3) CustomerSkuVisibilityChangeSet.between(existing, requested)
4) inserted → insert；updated → updateById（version 不匹配 → 40921）；removedIds → softDeleteOwned
```

**只读反查页**：`POST /scm/customer/visibility/reverse/query`（§4.1.6），不做 CRUD、不做批量授权、不做独立写入口。

**审计**：可见性变更走客户聚合的 `@OperateLog`（`/scm/customer/add|update`）+ SmartAdmin 操作日志表；**不新增 visibility 审计表**（legacy 无事实源）。列为待确认项（§12 Q5）。

### 4.7 事务与并发顺序

#### 4.7.1 锁对象与全局锁序

```text
全局锁序（同一事务内需要多把锁时，必须按此顺序获取）：
    customer_type  →  customer  →  customer_agreement_price  →  customer_type_price  →  customer_sku_visibility
```

| 操作 | 锁 | 顺序 |
|---|---|---|
| 协议价 add/update | `customer` 行 `FOR UPDATE`（`lockCustomer`） | 校验客户 → 锁 customer → 重叠检测 → 写 |
| 类型价 add/update | `customer_type` 行 `FOR UPDATE`（`lockCustomerType`） | 校验类型 → 锁 customer_type → 重叠检测 → 写 |
| 类型价 batch | 每行锁 `customer_type`，**行必须先按 `customer_type_id` 升序排序** | 见 §4.8 |
| 可见性 replace | `customer` 行 `FOR UPDATE` | 锁 customer → 差量 → 写 |
| 协议价/类型价 delete | 无显式锁（原子谓词软删 + 乐观锁） | – |

> **W3 加固（对 legacy D19/D20）**：批量调价**先排序再逐行加锁**，避免两个并发批次以相反类型顺序加锁导致死锁。这是 legacy 未处理的缺陷。

#### 4.7.2 并发手段（三层）

| 层 | 手段 | 用途 | 失败映射 |
|---|---|---|---|
| 乐观锁 | `@Version` + `updateById != 1` | 更新 / 删除 / 可见性明细更新 | `ScmCommonErrorCode.VERSION_CONFLICT(40921)` |
| 原子谓词软删 | `UPDATE ... WHERE id=? AND version=? AND deleted=FALSE` 返回 0 | 删除 | `40921` |
| 悲观锁 | `SELECT ... FOR UPDATE` on `customer` / `customer_type` | 序列化同一客户/类型的定价写入，保护重叠检测 | – |
| 唯一约束 | `uk_customer_sku_visibility_active`、`uk_customer_price_batch_audit_success` | 可见性去重、批次幂等 | `DuplicateKeyException` → 业务码映射 |

#### 4.7.3 事务边界

| 操作 | 事务 |
|---|---|
| 协议价 add/update/delete | `@Transactional`（含留痕） |
| 类型价 add/update/delete | `@Transactional`（含留痕） |
| 类型价 batch（写入） | `@Transactional`（整批，含 SUCCESS 审计） |
| 类型价 batch（失败审计） | `@Transactional(propagation = REQUIRES_NEW)`（不被回滚） |
| 可见性 replace | `@Transactional`（随客户 update） |
| 所有读端点 | 无事务（或只读） |

#### 4.7.4 不引入的手段（明确记录）

| 不引入 | 理由 |
|---|---|
| 分布式锁 / Redisson | 单实例模块化单体，AGENTS.md §5 |
| DB 级 `EXCLUDE` 排除约束 | 见 §3.5（Q4=A，本波次不引入） |
| 显式隔离级别 | 全部默认（READ COMMITTED + `FOR UPDATE` 已足够） |
| 幂等表 | batch 用部分唯一索引；resolve 是只读 |

### 4.8 批量调价设计（对 legacy 的四处升级）

#### 4.8.1 两阶段

```text
阶段 1（预校验，不触库写，全部内存）：
  PriceBatchValidator.validate(rows)
    → List<PriceBatchRowFailureVO>
  校验项（逐行，全部收集，不短路）：
    - customerTypeId 存在且 ENABLED
    - skuId 存在且可售（上架 + SPU 上架 + 分类启用）
    - unitPrice 格式（4 位定点、非负）
    - effectiveFrom 非空
    - effectiveTo 为空 或 effectiveTo > effectiveFrom
    - 请求内 (customerTypeId, skuId) 不重复
  若 failures 非空 → 不进入阶段 2，写 FAILED 审计，返回 committed=false

阶段 2（写入，单事务）：
  按 customerTypeId 升序排序 rows（防死锁）
  for row in sortedRows:
      lockCustomerType(row.customerTypeId)
      if countOverlapping(row) > 0 → failures += {row, 40935}
  if failures 非空 → 写 FAILED 审计（REQUIRES_NEW）+ 抛内部回滚 → 返回 committed=false
  for row in sortedRows: insert + log(CREATE)
  写 SUCCESS 审计
  返回 committed=true
```

#### 4.8.2 响应结构

见 §4.1.3。`committed` 是唯一权威标志：`false` ⇒ DB 无任何变化。

#### 4.8.3 与 legacy 的四处差异（全部为修正）

| # | legacy | W3 | 修正的缺陷 |
|---|---|---|---|
| 1 | 逐行 `try/catch`，遇第一个业务异常即中断，只报第一行 | **预校验收集全部失败行**；重叠类失败也在写入前批量检测 | D3、D14、A4 |
| 2 | 响应只有 `{batchKey, rowCount, ids}`，无逐行清单 | 响应带 `committed` + `failures[]`（含行号/SKU/错误码/消息） | D14、规格 §7 |
| 3 | 无幂等，重复提交同 `batchKey` 重复建价 | `uk_customer_price_batch_audit_success (batch_key) WHERE result='SUCCESS'`；重复成功提交 → `40948` | D2、G5 |
| 4 | 逐行 6 次 DB 往返、逐条 INSERT | **预校验阶段批量查询**（一次查全部类型、一次查全部 SKU、一次查全部重叠）；写入仍逐条 INSERT（保持简单、行数 ≤500） | D4（部分修正） |

> 重叠检测的批量化：`countOverlapping` 改为一次 `selectOverlappingKeys(customerTypeIds, skuIds, ranges)` 返回冲突键集合，避免 500 次往返。
> **逐条 INSERT 保留**：500 行的批量 INSERT 收益有限，而逐条能复用 `@Version` / 留痕 / 唯一约束映射逻辑。若性能不达标，列为后续优化项（§12 Q6）。

#### 4.8.4 审计 `error_data` 结构

```jsonc
{ "failures":[ {"rowNumber":2,"customerTypeId":7,"skuId":789,"code":40031,"message":"结束时间必须晚于开始时间"} ],
  "failedCount":1, "submittedCount":2 }
```
`row_count` 保持 legacy 语义 = **请求行数**（不是成功行数）。

### 4.9 价格历史与审计设计

| 项 | 决策 |
|---|---|
| 数据来源 | 两张 log 表 `UNION ALL` + `LEFT JOIN` 主表（照抄 legacy） |
| 动作类型 | `CREATE` / `UPDATE` / `DELETE`（DB CHECK 约束） |
| 快照存储 | `JSONB`，字段 `{id, customerId|customerTypeId, skuId, unitPrice(4位串), effectiveFrom(ISO), effectiveTo(ISO|null), version, deleted}` |
| 快照构造 | `PriceSnapshot.of(entity)` 纯函数（可单测），DELETE 分支补 `deleted=true` + `version+1` |
| **操作人（修 D5）** | 全部用 `ScmOperator.current()`（= `userType:userId`），**禁止 `"SYSTEM"` 硬编码**；批次审计也用真实操作人 |
| 当前值 | `currentUnitPrice` / `currentEffectiveFrom` / `currentEffectiveTo` / `currentDeleted`；硬删时 `currentUnitPrice=null` 且 `currentDeleted=true`（修 D13） |
| 来源 | 只有 `AGREEMENT` / `CUSTOMER_TYPE`。**不含订单锁价 / OVERRIDE**（P23） |
| 保留期 | 不做归档 / 不做清理（legacy 无） |

### 4.10 引用检查（启用 W2 预留钩子）

| 场景 | 检查 | 错误码 |
|---|---|---|
| 删除客户 | 是否存在 `customer_agreement_price` / `customer_type_price`（通过该客户类型）/ `customer_sku_visibility` 活动行 | `CUSTOMER_REFERENCED(40939)`（W2 已落地检查位，W3 启用） |
| 删除客户类型 | 是否被 `customer` 引用（W2 已实现）**或** `customer_type_price` 活动行引用 | `CUSTOMER_TYPE_IN_USE(40938)`（W3 扩展语义） |
| 删除 SKU（W1 域，**W3 不改**） | 是否被 `customer_agreement_price` / `customer_type_price` / `customer_sku_visibility` 引用 | **记录为 W3 发现、W4+ 处理**（W1 删除逻辑零修改） |

### 4.11 SKU option API 的 W1 边界声明

| 约束 | 落实 |
|---|---|
| 不改 W1 聚合写模型 | `ProductSpuService` / `ProductSkuSyncManager` / `ProductSkuChangeSet` **零修改** |
| 不改 `module/scm/product/**` 既有文件 | 新文件共 6 个（controller / dao / form / vo×2 / service）+ 1 个 XML；**既有文件零 diff** |
| 不改 V6/V7 | 纯读能力，无 DDL |
| 只允许新增只读查询能力 | 新 controller 只有 1 个 `POST /option-list`，无写端点 |
| 必须补回归测试 | `ProductSkuOptionIT`（PG，覆盖 keyword / status / spuId / limit / truncated）+ `ProductSkuControllerTest`（Web 层，权限 30005 + 分页信封）+ **W1 既有测试全量重跑（回归）** |

---

## 5. 前端设计（Copy First + Adapt）

### 5.0 迁移原则（W2 起生效，W3 沿用）

```text
1. C 有同功能 Vue 页面 ⇒ 复制 → 剪枝 → 适配 → 补测试。禁止重写。
2. C 没有、legacy 有 ⇒ 参照 legacy 行为 + V2 列表页范式新写。
3. legacy 和 C 都没有 ⇒ 新写（属新增能力）。
4. 绝不复制 layout / login / system / router core / permission framework / request framework /
   SmartAdmin common-base / system menu seed。
5. 删 `resizable` / `@resizeColumn` / `handleResizeColumn`（V2 无 `TableHeaderCell` 使用）。
6. C 未跑 `vue-tsc` ⇒ 迁入必补类型/判空（TS7006 / 2339 / 18047 / 2322）。
7. 每个复制文件头部必须写「来源 + 剪枝 + 适配 + 验收」注释块。
```

### 5.1 文件清单（含来源标记）

| # | V2 文件 | 来源 | 动作 |
|---|---|---|---|
| 1 | `src/views/business/scm/pricing/agreement-price-list.vue` | C `views/business/product/product-price-list.vue`（版式骨架）+ legacy `AgreementPricePage.tsx`（字段与交互） | **复制 + 剪枝 + 适配** |
| 2 | `src/views/business/scm/pricing/components/agreement-price-form-drawer.vue` | C 抽屉骨架 + legacy `AgreementPriceDrawer.tsx` | **复制 + 剪枝 + 适配** |
| 3 | `src/views/business/scm/pricing/customer-type-price-list.vue` | C 同上骨架 | **复制 + 剪枝 + 适配** |
| 4 | `src/views/business/scm/pricing/components/customer-type-price-form-drawer.vue` | C 抽屉骨架 + legacy `CustomerTypePriceDrawer.tsx` | **复制 + 剪枝 + 适配** |
| 5 | `src/views/business/scm/pricing/customer-type-price-batch.vue` | legacy `CustomerTypePriceBatchPage.tsx`（C 无批量页） | **新写**（参照 legacy 行编辑表 + V2 范式） |
| 6 | `src/views/business/scm/pricing/price-history-list.vue` | legacy `PriceHistoryPage.tsx`（C 无历史页） | **新写** |
| 7 | `src/views/business/scm/pricing/price-preview.vue` | 无（legacy/C 均无取价试算页） | **新写**（新增能力 G3） |
| 8 | `src/views/business/scm/customer/customer-sku-visibility-list.vue` | C `views/business/customer/customer-goods-visible-list.vue` | **复制 + 剪枝为只读反查** |
| 9 | `src/views/business/scm/customer/components/customer-form-drawer.vue` | W2 已有 | **扩展**：补「商品可见范围」区块（legacy 语义） |
| 10 | `src/components/business/scm/sku-select/index.vue` | 已有 | **改造**：远程搜索（去 `pageSize=500` 拍平） |
| 11 | `src/components/business/scm/customer-type-select/index.vue` | 已有 | 复用（无需改） |
| 12 | `src/components/business/scm/customer-select/index.vue` | 已有 | 复用（无需改） |
| 13 | `src/api/business/scm/pricing-api.ts` | C `product-price-api.ts` 形状 | **复制 + 适配**（`/scm` 前缀 + TS 类型 + `version`） |
| 14 | `src/api/business/scm/product-sku-api.ts` | 无 | **新写**（`optionList`） |
| 15 | `src/api/business/scm/customer-visibility-api.ts` | C `customer-goods-visible-api.ts` 形状 | **复制 + 适配** |
| 16 | `src/constants/business/scm/pricing-const.ts` | C `product-const.ts`（`PRICE_TYPE_ENUM` / `PRICE_STATUS_ENUM` 语义） | **复制 + 适配**（改 `SmartEnum<string>`，去 `PROMOTION`） |
| 17 | `src/types/business/scm/pricing.d.ts` | legacy `types/sales.ts`（字段形状） | **新写**（金额一律 `string`） |
| 18 | `src/views/business/scm/pricing/pricing-form-model.ts` | W2 范式（`customer-form-model.ts`） | **新写**（纯函数：默认值 / 校验 / 归一化） |
| 19 | `src/views/business/scm/pricing/pricing-errors.ts` | W2 范式（`customer-errors.ts`） | **新写**（错误码 → 文案） |
| 20 | `src/utils/scm-amount.ts` | 无（legacy `AmountText.tsx` 语义） | **新写**（共享金额格式化，含 null 保护 + 未定价文案） |
| 21 | `test/pricing-form-model.test.mjs` | W2 范式 | **新写** |
| 22 | `e2e/scm-pricing.spec.ts` | W2 范式 | **新写**（2 用例：live pilot + 只读角色） |
| 23 | `src/constants/index.ts` | 已有 | **改**：注册 `scmPricing` |
| 24 | `src/constants/support/table-id-const.ts` | 已有 | **改**：+ `SCM_PRICING_AGREEMENT: 50007` … `50011` |
| 25 | `tools/ts_baseline_ratchet.py` | 已有 | **需改**：`SCM_PREFIXES` 已含 `src/views/business/scm/` 与 `src/api/business/scm/` 等；本 W3 新增的 `src/utils/scm-amount.ts` 不在白名单内，**需同步加入 `SCM_PREFIXES`**（该文件由 SCM 交付，必须受「SCM 0 错误」门禁保护，见 §5.2 第 62 项 / §9.6） |

### 5.2 复制后强制适配清单（逐项门禁）

**A. 通用（所有复制文件）**

| # | 适配项 | 说明 |
|---|---|---|
| 1 | API 路径加 `/scm` 前缀 | C：`/product/price/query` → V2：`/scm/pricing/agreement-price/query` |
| 2 | 补 TS 类型（C 全隐式 any） | `import type { ... } from '/@/types/business/scm/pricing'` |
| 3 | 枚举从 `SmartEnum<number>` 改 `SmartEnum<string>` | C `PRICE_TYPE_ENUM.BASE.value === 1` → V2 `'MARKET'` |
| 4 | 枚举注册进 `src/constants/index.ts` | 否则 `SmartEnumSelect` 会 `console.error('无法找到变量名称：…')` 且列表为空 |
| 5 | 删 `resizable` / `@resizeColumn` / `handleResizeColumn` | V2 无 `TableHeaderCell` 使用 |
| 6 | 列定义用 `ref<TableColumnsType<Row>>([...])` | 否则 TS2322 |
| 7 | 表格 `:pagination="false"` + 独立 `<a-pagination>` 包 `<div class="smart-query-table-page">` | W2 范式 |
| 8 | 排序走白名单映射（驼峰 → snake_case） | 后端 `sortItemList` 只接受白名单列 |
| 9 | 请求竞态守卫 `let requestId = 0` | W2 范式 |
| 10 | 编辑前先 `api.detail(id)` | 列表 VO 不含完整字段 |
| 11 | 错误走 `pricing-errors.ts` 映射 + `a-alert` | 不用 `window.alert`（A10） |
| 12 | 金额输入用 `a-input`（字符串直传）或 `a-input-number string-mode` | 禁止 `Number()` 运算 |
| 13 | 时间不做客户端时区换算（A31） | 直接传 `dayjs(...).format('YYYY-MM-DDTHH:mm:ssZ')` 或 ISO 原值；后端 `OffsetDateTime` 解析 |
| 14 | 时间选择器统一用 `RangePicker`（生效/结束） | 修 A25；用 `showTime` |
| 15 | 权限指令 `v-privilege="'scm:pricing:...'"` | 替换 C 的 `product:price:*` |
| 16 | `TableOperator` 用新的 `TABLE_ID_CONST.BUSINESS.SCM_PRICING_*` | 50007+ |

**B. 协议价列表 / 抽屉（#1、#2）**

| # | 适配项 |
|---|---|
| 17 | 表格列改为：客户名称 / 客户编码 / **SKU 编码 + 商品名 + 规格**（去掉裸 SKU ID，修 A3）/ 协议单价 / 生效时间 / 结束时间（null → 「长期有效」）/ 更新时间 / 操作 |
| 18 | 查询条件补齐：关键字 + **客户选择器** + **SKU 选择器** + 生效区间（修 A2） |
| 19 | SKU 选择器用改造后的远程搜索 `SkuSelect`；**上下架 SKU 给 `disabled`**（修 A9） |
| 20 | 表单补条件校验：客户必选、SKU 必选、单价 4 位非负、`effectiveTo > effectiveFrom` |
| 21 | 补「零价也是有效价格」提示（修 A24） |
| 22 | 错误映射：`40933` → 「该客户与 SKU 的协议价有效期发生重叠」；`40921` → 「已被其他人修改，请刷新后重试」；`40030` → 「价格不能小于零」；`40031` → 「结束时间必须晚于开始时间」 |

**C. 客户类型价列表 / 抽屉（#3、#4）**

| # | 适配项 |
|---|---|
| 23 | **修正 legacy 的错误码反转（A1）**：`40935` → 重叠文案；`40921` → 版本冲突文案 |
| 24 | 表格列：客户类型名称 / 类型编码 / SKU 编码+商品名+规格 / 客户类型价 / 生效时间 / 结束时间 / 更新时间 / 操作 |
| 25 | 工具栏按钮：新增客户类型价（`scm:pricing:type-price:add`）+ 批量调价（`scm:pricing:type-price:batch`，**用 `router.push` 而非 `window.location.assign`**，修 A8） |
| 26 | 类型选择器用 `customer-type-select`，**停用类型给 `disabled`** |

**D. 批量调价页（#5，新写）**

| # | 适配项 |
|---|---|
| 27 | 保留 legacy 的行编辑表结构（行号 / 客户类型 / SKU / 单价 / 生效时间 / 结束时间 / 删除） |
| 28 | 批次号：**默认服务端风格自动生成**（`PRICE-YYYYMMDD-HHmmss`，可编辑），前端做格式提示 |
| 29 | **逐行失败展示**：提交后 `committed=false` 时，把 `failures[]` 按 `rowNumber` 映射回表格行 → 该行标红 + 行内展示 `message`；同时顶部 `a-alert type="error"` 汇总 |
| 30 | 行上限 500（与后端一致），新增行按钮 `disabled` |
| 31 | 提交成功后展示 `ids.length` 条并跳回列表页 |
| 32 | 不引入 Excel 导入（legacy 无；列为待确认 §12 Q7） |

**E. 价格历史页（#6，新写）**

| # | 适配项 |
|---|---|
| 33 | 筛选条件照抄 legacy 8 项（来源 / 客户 / 客户类型 / SKU / 操作 / 生效区间 / 操作区间） |
| 34 | 展示列照抄 + **补 `currentEffectiveFrom` / `currentEffectiveTo`**（修 A11） |
| 35 | 变更详情：**用 `a-modal` + 字段级 diff 表**（字段 / 变更前 / 变更后），不用 `window.alert`（修 A10） |
| 36 | `currentDeleted=true` → Tag 红「已删除」；`currentUnitPrice === null` → 「未定价」文案（与 §5.4 统一） |
| 37 | 页面级权限 `scm:pricing:history:query` |

**F. 取价试算页（#7，新写）**

| # | 适配项 |
|---|---|
| 38 | 输入：客户（`CustomerSelect`）+ SKU 多选（远程搜索 `SkuSelect mode="multiple"`）+ 生效时点（可选 `DatePicker showTime`，留空 = 当前） |
| 39 | 输出：`a-table`（SKU / 商品 / 规格 / 单价 / 价格状态 / 来源 / 来源记录 ID / 缺价原因 / 可售状态 / 不可售原因） |
| 40 | `priceStatus=UNPRICED` → Tag 灰「未定价」+ 缺价原因列；`PRICED` → 4 位金额 + 来源 Tag（AGREEMENT 绿 / CUSTOMER_TYPE 蓝 / MARKET 灰） |
| 41 | 明示「价格由服务端解析，客户端不做任何计算」 |

**G. 客户 SKU 可见性反查页（#8，复制剪枝）**

| # | 适配项 |
|---|---|
| 42 | **剪掉 CRUD**（新增/编辑/删除抽屉、`add`/`update`/`delete` API），改为**只读反查** |
| 43 | 裸 `a-input-number` 客户/商品 ID → `CustomerSelect` + `SkuSelect`（修 C18） |
| 44 | 筛选条件：客户 / SKU / 可见性策略（`ALLOWLIST` / `ALL_ENABLED`） |
| 45 | 列：客户编码 / 客户名称 / 客户类型 / 可见性策略 / SKU 编码 / 商品名 / 规格 / SKU 状态 / SPU 状态 / 授权时间 / 授权人 |
| 46 | 顶部 `a-alert type="info"`：「维护客户可见范围请到 客户档案 → 编辑 → 商品可见范围」 |
| 47 | 页面级权限 `scm:customer:visibility:query` |

**H. 客户抽屉扩展（#9）**

| # | 适配项 |
|---|---|
| 48 | 补「商品可见范围」区块：`Radio.Group`（全部在售 SKU / 仅白名单 SKU）+ `SkuSelect mode="multiple"` |
| 49 | `ALL_ENABLED` 时清空并隐藏多选（**修 A26：切换策略时清空已选，不靠提交时丢弃**） |
| 50 | `ALLOWLIST` 时允许空选择（全部不可见） |
| 51 | 编辑回填时把 `visibilities` 的 `skuId` 灌入多选，并保留 `{id, version}` 供差量回填 |
| 52 | 多选的 SKU 选项**带 `disabled`**（不可售 SKU 不可选） |

**I. `sku-select` 改造（#10）**

| # | 适配项 |
|---|---|
| 53 | 删除 `productApi.query({pageNum:1, pageSize:500, ...})` 拍平逻辑（修 A14） |
| 54 | 改远程搜索：`show-search` + `:filter-option="false"` + `@search` 300ms debounce → `productSkuApi.optionList({keyword, status, limit:50})` |
| 55 | 新增 props：`status?: 'ON_SHELF' \| 'OFF_SHELF' \| null`（默认 `null` = 全部）、`spuId?: ScmId`、`limit?: number` |
| 56 | 新增 props：`disabledStatuses?: string[]`（默认 `['OFF_SHELF']` 时下架项置灰）——由调用方决定 |
| 57 | 首次打开（未输入关键字）预加载前 50 条；`truncated=true` 时下拉底部提示「结果过多，请继续输入以缩小范围」 |
| 58 | 保留 `optionFilterProp` 移除（服务端过滤） |
| 59 | 保持既有 props/emits 兼容（`value` / `placeholder` / `width` / `size` / `update:value` / `change`），**不破坏 W2 的两个使用点** |

**J. 共享金额工具（#20）**

| # | 适配项 |
|---|---|
| 60 | 新建 `src/utils/scm-amount.ts`：`formatAmount(value: string \| null \| undefined): string`（null → `'未定价'`，非 null → `¥ 1,234.5000`） |
| 61 | 新建 `formatAmountOrDash`（null → `'—'`）用于列表空值 |
| 62 | **必须同步**：`src/utils/` 不在 `ts_baseline_ratchet.py` 的 `SCM_PREFIXES` 内。决策：文件放 `src/utils/scm-amount.ts`，**同时把该路径加入 `SCM_PREFIXES`**（SCM 交付文件必须受「SCM 0 错误」门禁保护，否则棘轮门禁会漏放）。此项为 T0 前置改动 |

### 5.3 版式约定（复制 C 后保持）

- 查询栏：`<a-form class="smart-query-form" layout="inline" @finish="search">`，每项 `class="smart-query-form-item"`
- 高级筛选：局部 `advanced = ref(false)` + `type="link"` 切换
- 表格：`a-table` + `:pagination="false"` + `size="small"` `bordered` `:scroll="{x:N}"`；独立 `<a-pagination>` 包在 `<div class="smart-query-table-page">`
- 操作列：`{ title:'操作', dataIndex:'action', width:N, align:'right', fixed:'right' }` + `<a-space :size="0" class="smart-table-operate">`
- 行操作按钮：`type="link" size="small"`；删除加 `danger`；全部带 `v-privilege`
- 列设置：`<TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_*" :refresh="load" />`
- 抽屉 vs 弹窗：字段多/需分节 → `a-drawer`（协议价 620、类型价 620、批量页全屏）；≤4 字段的字典类 → `a-modal`
- 抽屉 footer：`<template #footer>` 固定「取消 / 保存」，保存按钮 `:loading="saving"`
- 空值展示：`{{ record.contactName || '—' }}`（全角破折号）
- 金额列：`align: 'right'` + `.amount { font-variant-numeric: tabular-nums; white-space: nowrap; }`
- 表单校验：`a-form-item` 内联 `:rules`；**跨字段约束走纯函数**（`pricing-form-model.ts`）

### 5.4 UNPRICED / 0 元 / 缺价在前端的统一呈现（**W3 必须统一，修 A19**）

| 语义 | 呈现 | 颜色 |
|---|---|---|
| `PRICED` 且 `unitPrice = "0.0000"` | `¥ 0.0000` | 正常 |
| `PRICED` 且 `unitPrice > 0` | `¥ 1,234.5000` | 正常 |
| `UNPRICED`（`unitPrice = null`） | Tag「未定价」+ 缺价原因列 | 灰（default） |
| 协议价 `effectiveTo = null` | 「长期有效」 | 正常 |
| 价格历史 `currentDeleted = true` | Tag「已删除」 | 红 |

**唯一文案来源**：`src/utils/scm-amount.ts` + `pricing-const.ts`。禁止再出现「询价」「无价格」「¥ --」「--」四套措辞。

**缺价原因文案映射**（`pricing-const.ts`）：

```ts
export const UNPRICED_REASON_ENUM: SmartEnum<string> = {
  NO_PRICE_SOURCE: { value: 'NO_PRICE_SOURCE', desc: '无可用价格来源' },
};
export const UNAVAILABLE_REASON_ENUM: SmartEnum<string> = {
  SKU_NOT_FOUND:      { value: 'SKU_NOT_FOUND',      desc: 'SKU 不存在' },
  SKU_OFF_SHELF:      { value: 'SKU_OFF_SHELF',      desc: 'SKU 已下架' },
  SPU_OFF_SHELF:      { value: 'SPU_OFF_SHELF',      desc: '商品已下架' },
  CATEGORY_DISABLED:  { value: 'CATEGORY_DISABLED',  desc: '所属分类不可用' },
  NOT_VISIBLE:        { value: 'NOT_VISIBLE',        desc: '客户不可见' },
};
```

### 5.5 路由与菜单

**路由完全由数据库菜单驱动**（`src/router/index.ts:98-165` `buildRoutes`）：`t_menu.component` 会被拼成 `../views${component}` 去 `import.meta.glob('../views/**/**.vue')` 里找，**路径必须与 `src/views/**` 完全一致**。

因此 W3 新增页面的落地步骤固定为：

```text
1. 创建 src/views/business/scm/pricing/*.vue（路径与 V11 的 component 字段逐字符一致）
2. V11 插入 t_menu 行（path = 前端 URL，component = 相对 src/views 的路径）
3. V11 插入功能点行（menu_type=3，api_perms = web_perms = 权限码，context_menu_id = 所属菜单）
4. V11 授权 role 1
5. V11 setval 校正 t_menu 序列
```

前端 `src/router/` **零修改**。

### 5.6 前端表格 ID

```ts
// src/constants/support/table-id-const.ts（追加）
SCM_PRICING_AGREEMENT:       50007,
SCM_PRICING_TYPE_PRICE:      50008,
SCM_PRICING_HISTORY:         50009,
SCM_PRICING_BATCH:           50010,
SCM_CUSTOMER_SKU_VISIBILITY: 50011,
```

---

## 6. C 前端资产迁移清单

### 6.1 可直接复制（骨架级）

| C 文件 | V2 目标 | 保留 | 剪掉 |
|---|---|---|---|
| `views/business/product/product-price-list.vue` | `pricing/agreement-price-list.vue` + `pricing/customer-type-price-list.vue` | `smart-query-form` 查询栏 / `a-table` + `#bodyCell` / `a-drawer` + `a-form` + `:rules` / footer 取消保存 / `v-privilege` / `SmartEnumSelect` / `TableOperator` / `SmartLoading` / `a-pagination` | 裸 ID 列（`productId`/`skuId`/`customerId`/`customerLevelId`）/ `priceType` 4 值 / `customerLevelId` 输入 / `onMounted` 只在有 `productId` 才查询的逻辑 / `resizable` + `@resizeColumn` + `handleResizeColumn` |
| `views/business/customer/customer-goods-visible-list.vue` | `customer/customer-sku-visibility-list.vue` | 查询栏 + 表格 + `SmartEnumSelect` 用法 | 全部 CRUD（新增/编辑/删除抽屉 + `add`/`update`/`delete`/`batchDelete` API）/ 裸 `a-input-number` 客户与商品 ID |
| `api/business/product/product-price-api.ts` | `api/business/scm/pricing-api.ts` | 5 方法命名形状（`query`/`add`/`update`/`delete`/`batchDelete` → 改为 `query`/`detail`/`add`/`update`/`delete` + `batch`） | 隐式 `any`；`/product/price/*` 前缀 |
| `api/business/customer/customer-goods-visible-api.ts` | `api/business/scm/customer-visibility-api.ts` | 5 方法形状 | 同上 |
| `constants/business/product/product-const.ts` 的 `PRICE_TYPE_ENUM` / `PRICE_STATUS_ENUM` | `constants/business/scm/pricing-const.ts` | 枚举键名与语义 | `SmartEnum<number>` → `SmartEnum<string>`；`PROMOTION` / `LEVEL` 不入 W3 |
| `constants/business/customer/customer-const.ts` 的 `VISIBLE_TYPE_ENUM` | 不入 W3 | – | C 的双向标记建模与 V2 的「策略 + 白名单」不同，**不复制** |

### 6.2 不可复制（明确）

| 资产 | 原因 |
|---|---|
| C 的 `SmartEnumSelect` / `smart-enums-plugin` / `router.buildRoutes` | 框架级，V2 已有自己的实现，覆盖会破坏 V2 |
| C 的任何 DDL / `@TableName("t_*")` / mapper 表列名 | C 是 MySQL8 + `t_` 前缀；V2 是 PostgreSQL + 无前缀 |
| C 的菜单 / 权限 seed | component 路径本身错位（C9）；且 V2 菜单体系已重构 |
| C 的 `customer-discount` 全部资产 | 无消费点（C11），且 W3 不做折扣率 |
| C 的 `t_product_price` 单表多态建模 | 见审计 §8.2 冲突 3 |
| C 的 `customer_level_id` / `price_type` 4 值 | 见审计 §8.2 冲突 1 |

### 6.3 后端资产的复用边界（重申）

```text
可参考：分层（controller/service/manager/dao）、API 路径形状、枚举语义、
        ResolvedPriceBO 字段思想、ProductPriceMapper 的「SKU 级优先于商品级」排序思想
不可继承：MySQL DDL、@TableName 表名、Dao/Mapper 的 SQL、Service 的业务实现、
        无审计/无版本/无时间窗的写法
```

---

## 7. Legacy 不变量覆盖清单（验收对账用）

逐条对应 `2026-09-15-w3-pricing-legacy-audit.md` 的 P1–P27（映射见 §4.3）。验收时必须逐条给出「实现位置 + 测试方法名」。

| 编号 | 不变量 | 实现 | 测试 |
|---|---|---|---|
| P1 | 优先级 3 级 | `PriceResolver.resolve` | `PriceResolverTest`（含「协议价 + 类型价同时存在」用例，补 B2） |
| P2 | `sourceRecordId` | `ResolvedPriceVO` | `PriceResolverTest` |
| P3 | 类型价按当前启用类型 | `requireTradable` + `customerTypeId` | `PriceResolverTest` |
| P4 | 客户启用 + SKU 可售守卫 | `resolve`（软）/ `requireResolvable`（严） | `PriceResolverTest` + `PriceResolveIT` |
| P5 | 半开区间 | `AgreementPriceDao.selectEffective` | `PriceEffectivePeriodIT`（补 B4） |
| P6 | `to IS NULL` 长期有效 | 同上 + CHECK | 同上 |
| P7 | 不得重叠 | `PricePeriodOverlapGuard` | `PriceOverlapIT`（补 B5/B6）+ `PriceOverlapConcurrencyIT` |
| P8 | 口径一致 | `countOverlapping` SQL | `PriceOverlapIT` |
| P9 | `to > from` 双保险 | Validator + CHECK | `AgreementPriceValidatorTest` + `PriceSchemaIT` |
| P10 | 决胜规则 | SQL + `toMap` | `PriceEffectivePeriodIT` |
| P11 | null = UNPRICED | `ResolvedPriceVO` | `PriceResolverTest` + `PriceResolveIT` |
| P12 | 0 元是有效价 | 校验器 + 解析 | `PriceZeroAmountIT`（补 B14） |
| P13 | UNPRICED 消费方契约 | VO 契约 | `PriceResolveIT` |
| P14 | 两个策略 | `ScmVisibilityPolicyEnum` + CHECK | `PriceSchemaIT` |
| P15 | ALLOWLIST 空 = 全不可见 | `visibleSkuIds` | `CustomerSkuVisibilityIT`（补 B9） |
| P16 | 可见性硬门禁 | `requireResolvable` | `CustomerSkuVisibilityIT` + `PriceResolveIT` |
| P17 | 整表替换差量 + 乐观锁 | `CustomerSkuVisibilityChangeSet` | `CustomerSkuVisibilityChangeSetTest`（迁 legacy 4 例）+ `CustomerSkuVisibilityIT` |
| P18 | 可见性影响范围 | `visibleSkuIds` 读能力 | `CustomerSkuVisibilityIT` |
| P19 | 批量整批回滚 | `PriceBatchWriter` | `PriceBatchWriterIT`（补 B7） |
| P20 | 行号定位 + FAILED 审计 | `PriceBatchValidator` + `PriceBatchService` | `PriceBatchServiceTest` + `PriceBatchAuditIT`（补 B8） |
| P21 | 行上限 500 / 批次号 100 | Bean Validation | `CustomerTypePriceControllerTest` |
| P22 | 批次审计字段 | `customer_price_batch_audit` | `PriceBatchAuditIT` |
| P23 | 锁价属 Order | `ScmPriceSourceEnum` 3 值 | `ScmPriceSourceEnumTest`（断言不含 `OVERRIDE`） |
| P24 | 增删改必留痕 | 两个写 Service | `AgreementPriceServiceTest` + `CustomerTypePriceServiceTest` |
| P25 | 快照语义 | `PriceSnapshot` | `PriceSnapshotTest`（含 DELETE 的 version+1） |
| P26 | 历史筛选正交 | `PriceHistoryQueryService` | `PriceHistoryQueryIT`（迁 legacy 1 例 + 补 B12） |
| P27 | 引用检查 | `assertNotReferenced` / `CUSTOMER_TYPE_IN_USE` | `CustomerReferenceIT` |

---

## 8. C 已知缺陷与处置

| C 缺陷 | 处置 |
|---|---|
| C1 无唯一约束 / 取价 `LIMIT 1` 随机 | **不复制**：V2 用「锁 + 重叠检测 + 决胜排序」 |
| C2 无审计 | **不复制**：V2 双 log 表 + 真实操作人 |
| C3 无并发控制 | **不复制**：V2 `@Version` + `FOR UPDATE` |
| C4 无 UNPRICED（硬抛异常） | **不复制**：V2 显式 UNPRICED + reason（§4.4） |
| C5 无 0 元区分 | **不复制**：V2 显式区分（P11/P12） |
| C6 忽略生效时间 | **不复制**：V2 半开区间 + 重叠禁止 |
| C7 可见性逻辑删除撞唯一键 | **不复制**：V2 partial unique index `WHERE deleted = FALSE` |
| C8 可见性双向标记语义不完整 | **不复制**：V2 用 legacy 的「策略 + 白名单」 |
| C9 菜单 component 路径错位 | **不复制**：V11 的 component 逐字符对齐 `src/views/**`，并有 IT 断言文件存在 |
| C10 权限码未 seed → 403 | **不复制**：V11 全部 seed + `t_role_menu` 授权 |
| C11 折扣率无消费点 | **不做**（W3 不含折扣率） |
| C12 折扣无重叠校验 | **不做** |
| C13 查询强制 `productId` | **不复制**：V2 查询全部条件可选 |
| C14 表格显示裸 ID | **不复制**：V2 全部反查名称/编码 |
| C15 表单无条件校验 | **不复制**：V2 `pricing-form-model.ts` 纯函数校验 |
| C16 API 无 TS 类型 | **不复制**：V2 全量类型化 |
| C17 客户分级手输 ID | **不复制**：V2 用 `customer-type-select` |
| C18 可见性手输 ID | **不复制**：V2 用选择器 |
| C19 路径与权限码风格漂移 | **不复制**：V2 统一 `scm:<domain>:<action>` |
| C20 `ProductPriceManager` 职责名不副实 | **不复制**：V2 `manager` 只放纯校验/差量/锁编排 |

---

## 9. 测试矩阵

### 9.1 后端单测（无 DB，Mockito / 纯函数）

| 类 | 用例数（目标） | 覆盖 |
|---|---|---|
| `PriceSnapshotTest` | 5 | CREATE/UPDATE/DELETE 快照；DELETE 的 `deleted=true` + `version+1`；null 字段处理 |
| `PricePeriodValidatorTest` | 4 | `to > from`；`to = null` 放行；`to = from` 拒绝；`to < from` 拒绝 |
| `AgreementPriceValidatorTest` | 4 | 负数拒绝（40030）；区间反转（40031）；`@Digits` 边界；客户/SKU 必填 |
| `CustomerTypePriceValidatorTest` | 3 | 同上（补 B11） |
| `PricePeriodOverlapGuardTest` | 4 | 锁 → 计数 → 判定的调用顺序；`id != #{id}` 排除自身；`to = null` 视为 infinity |
| `PriceBatchValidatorTest` | 8 | 全部失败行收集（不短路）；类型停用；SKU 不可售；价格格式；区间非法；请求内重复 (type, sku)；空行；500 行上限 |
| `CustomerSkuVisibilityChangeSetTest` | 6 | 迁 legacy 4 例（保留身份/外来记录/换 SKU/重复）+ ALL_ENABLED 空明细 + ALLOWLIST 空明细放行 |
| `PriceResolverTest` | 8 | 协议价优先；**协议价与类型价同时存在时 AGREEMENT 胜出（补 B2）**；类型价次之；市场价兜底；0 元 PRICED；独立价格/可售状态、五种不可售原因及唯一缺价原因；空列表短路；`requireResolvable` 抛 40949 |
| `ScmPriceSourceEnumTest` | 1 | 断言 3 值且**不含 `OVERRIDE`**（P23） |
| `ScmUnpricedReasonEnumTest` | 1 | UNPRICED_REASON_ENUM 仅 NO_PRICE_SOURCE；五种不可售原因与 UNAVAILABLE_REASON_ENUM 一致 |
| `PricingErrorCodeTest` | 1 | 码值唯一、与 W1/W2 不撞码 |

### 9.2 后端 PG 集成测试（`@SpringBootTest` + test profile + Flyway，`*IT`）

基类：新增 `ScmW3PgITBase`（照抄 `ScmW2PgITBase`：`@Transactional` + `JdbcTemplate` + `ObjectMapper` + `prefix` + 操作人注入 + `newOnShelfSku` 夹具）。

| 类 | 用例数（目标） | 覆盖 |
|---|---|---|
| `PriceSchemaIT` | 6 | V10 表/列/约束/索引存在性；`ck_*_period`；`ck_*_amount`；`customer.visibility_policy` 默认值 + CHECK；`uk_customer_sku_visibility_active` 部分唯一；`uk_customer_price_batch_audit_success` 只覆盖 SUCCESS |
| `AgreementPriceIT` | 10 | CRUD；半开区间生效（含 `to=null`）；重叠拒绝（40933）；`to<=from` 拒绝（40031）；负数拒绝（40030）；乐观锁（40921）；软删；留痕 CREATE/UPDATE/DELETE；**决胜规则 `effective_from DESC, id DESC`（补 B4）**；**0 元可写入（补 B14）** |
| `CustomerTypePriceIT` | 8 | 同构 + 停用类型拒绝（补 D12） |
| `PriceEffectivePeriodIT` | 6 | 边界：`at == from`（命中）；`at == to`（不命中）；`at` 在区间内；`to=null` 命中；重叠边界（相邻区间不算重叠） |
| `PriceOverlapIT` | 6 | 重叠检测 SQL 本身（补 B5/B6）：完全相同区间；部分重叠；包含；相邻不重叠；`to=null` vs 有终点；自身排除 |
| `PriceOverlapConcurrencyIT` | 3 | 两个线程并发写同一 (customer, sku) 同区间 → 一成一败（40933）；不同区间 → 都成功；乐观锁冲突 → 40921 |
| `PriceHistoryQueryIT` | 5 | 迁 legacy 1 例 + 非法 source 归一（补 B12）+ 硬删时 `currentUnitPrice=null` + 分页排序 |
| `PriceBatchWriterIT` | 6 | 整批成功；任一行重叠 → 整批回滚 + `committed=false`；FAILED 审计；SUCCESS 审计；**幂等（重复 batchKey → 40948）**；**排序后加锁不产生死锁** |
| `PriceBatchAuditIT` | 4 | `error_data` 全量 failures；`row_count = 请求行数`；`created_by` 是真实操作人；`REQUIRES_NEW` 不被回滚 |
| `PriceResolverIT` | 8 | 三级优先级端到端；0 元；五种不可售原因（保留已有价格）与 NO_PRICE_SOURCE 契约；`requireResolvable` 抛 40949；客户不可交易 40930 |
| `CustomerSkuVisibilityIT` | 8 | ALLOWLIST 空 → 空集（补 B9）；差量 insert/update/softDelete；乐观锁；`VISIBILITY_NOT_OWNED`；`VISIBILITY_POLICY_CONFLICT`；`VISIBILITY_SKU_NOT_SELLABLE`；**下架 SKU 白名单后解析 → 保留 PRICED 价格，sellable=false** |
| `CustomerReferenceIT` | 5 | 有协议价的客户不可删（40939）；有类型价的客户类型不可删（40938）；有可见性明细的客户不可删；清空后不可删→可删；无引用可删 |
| `ProductSkuOptionIT` | 7 | 无 keyword 返回前 limit + `truncated=true`；keyword 命中 sku_code/barcode/spec_name/spu.name/spu.alias；status 过滤；spuId 过滤；limit 上限 200；已删除 SKU/SPU 不返回；分类状态带出 |
| `ScmPricingMigrationIT` | 1 | Flyway migrate + validate；断言 V6–V9 历史未被回改（沿用 W2 做法） |

### 9.3 后端 Web 层测试（`@WebMvcTest` + `addFilters=false`）

| 类 | 用例数（目标） | 覆盖 |
|---|---|---|
| `AgreementPriceControllerTest` | 6 | 分页信封；`@Valid` 失败 → 30001；40432；40933；40921；40030/40031 |
| `CustomerTypePriceControllerTest` | 6 | 同上 + 批量 500 行上限 → 30001 |
| `PriceHistoryControllerTest` | 3 | 分页信封；非法 source 不报错；日期参数解析 |
| `PriceResolveControllerTest` | 3 | 正常返回；缺价行；空 skuIds |
| `CustomerVisibilityControllerTest` | 3 | 反查分页；权限 30005；`@Valid` |
| `ProductSkuControllerTest` | 4 | 权限 30005（只读角色）；limit 默认 50；limit > 200 → 30001；空 keyword |
| `PricingPermissionIT` | 1 | 断言 V11 的 21 个 menu_id 存在、`t_role_menu` 21 条、权限码与 `@SaCheckPermission` 逐一对齐 |

### 9.4 前端

| 类型 | 文件 | 用例数（目标） | 覆盖 |
|---|---|---|---|
| 单测（`node --experimental-strip-types --test`） | `test/pricing-form-model.test.mjs` | 10 | 默认值；金额 4 位定点校验（含 0 允许、负数拒绝）；区间校验；`ALL_ENABLED` 清空明细；`ALLOWLIST` 允许空白名单；批次号格式；批量行校验收集全部失败；空串 → null；不改 `Number()` |
| 单测 | `test/scm-amount.test.mjs` | 4 | `null` → 「未定价」；`"0.0000"` → `¥ 0.0000`；千分位；`undefined` |
| 单测 | `test/pricing-errors.test.mjs` | 3 | 40933/40935 → 重叠文案；40921 → 版本冲突；40030/40031 |
| TS 门禁 | `python tools/ts_baseline_ratchet.py check` | – | SCM 区 0 错误；total ≤ 1974 |
| ESLint | `npm run lint`（`eslint src`） | – | 0 error |
| 构建 | `npm run build` | – | 成功 |

### 9.5 E2E（Playwright，`e2e/scm-pricing.spec.ts`，**2 用例**，与 W1/W2 一致）

| 用例 | 覆盖 |
|---|---|
| `live pricing pilot: agreement price, type price, batch with row errors, visibility, history, price preview and deep link` | ① 建客户 + 建 SKU（复用 W1/W2 夹具）② 协议价新增 → 列表可见 → 取价试算显示 AGREEMENT ③ 类型价新增 → 试算回落到 CUSTOMER_TYPE ④ 批量调价：**故意放一行非法区间 → 断言该行标红 + 整批未写入** ⑤ 改成合法 → 成功 ⑥ 客户抽屉设 ALLOWLIST + 选 1 个 SKU → 反查页可见 ⑦ 价格历史展示 UPDATE 前后 diff ⑧ 深链协议价列表 ⑨ 断言 `pageerror` 为空 |
| `read-only role cannot mutate prices and buttons are hidden` | 只读角色：直接 POST `/scm/pricing/agreement-price/add` → `code=30005`；打开列表页 → 「新增协议价」按钮不存在；「查询」可见 |

### 9.6 质量门禁（Step 0 交付物）

```text
1. 后端单测：mvn -pl sa-admin test（SCM 全量，W1+W2+W3 一起跑）
   - SCM 测试全在 sa-admin；用 classworlds 启动器 + -Dmaven.multiModuleProjectDirectory=<repo>/xsy-scm-server
   - 配 -Dsurefire.failIfNoSpecifiedTests=false
2. 后端 PG IT：mvn -pl sa-admin test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
   - PG 需常驻 D:/PostgreSQL/bin/postgres.exe -D D:/PostgreSQL/data -p 15432
   - XSY_V2_DB_URL / _USERNAME / _PASSWORD 环境变量
3. 前端单测：npm run test
4. TS 棘轮：python tools/ts_baseline_ratchet.py check  → 必须 PASS（new 0 / SCM 0 / total ≤ 1974）
   - ⚠️ 需同步把 src/utils/scm-amount.ts 加入 SCM_PREFIXES
5. ESLint：npm run lint（= eslint src；不要用 eslint .）
6. 构建：npm run build（vite build --mode production --outDir dist-verify --emptyOutDir）
7. E2E：npx playwright test e2e/scm-pricing.spec.ts（2/2）
   - PLAYWRIGHT_BROWSERS_PATH=D:/DevCaches/Playwright
   - 后端 18080 / 前端 18081
8. legacy 冻结复验：python tools/verify_w3_legacy.py
   - legacy 全量 SHA-256 清单（沿用 W2 的 900 文件基线 + 新增 pricing 文件）
   - V6–V9 migration 哈希未变
   - 前端「复制」文件的头部注释包含来源路径
   - 输出 .runtime/w3-legacy-recheck.{manifest,json}
9. 生成 docs/architecture/v3-applied-migrations.sha256（V6–V11）
```

---

## 10. 风险

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | **UNPRICED 定义（Q2）拍板错误** | 若选 Option B（改 `market_price` 可空），需改 W1 表单/VO/校验/测试 + V6 约束，回归面大 | 默认 Option A（不动 W1）；Option B 单独立项 |
| R2 | **批量响应偏离「非 0 code」惯例（Q1）** | 前端/调用方可能误把 `code=0` 当成功 | 前端**必须**以 `committed` 为唯一判据；e2e 覆盖 |
| R3 | **批量排序加锁引入行为变化** | 响应 `ids` 顺序与请求行顺序不一致 | 文档明示 + `failures` 按 `rowNumber` 匹配；前端按 `rowNumber` 回填而非数组下标 |
| R4 | **`customer.visibility_policy` 是 W3 唯一触碰 W2 表处** | 若 W2 的 IT 断言 `customer` 列集合，会失败 | 先跑 W2 全量 IT 确认；V10 只 `ADD COLUMN`，不改既有列 |
| R5 | **W1 Product 目录被新增只读端点** | 用户明确允许，但需保证零修改既有文件 | CI 级 diff 检查：`module/scm/product/**` 既有文件必须零 diff（`verify_w3_legacy.py` 断言） |
| R6 | **`sku-select` 改造影响 W2 两个使用点** | W2 的 `supplier-sku-editable-table` / `supplier-sku-list` 可能回归 | 保持 props/emits 兼容；W2 e2e 2/2 必须重跑 |
| R7 | **重叠仍无 DB 级保护（Q4）** | 旁路写入可破坏不变量 | 应用层 `FOR UPDATE`；列为待确认加固；IT 覆盖并发 |
| R8 | **批量逐条 INSERT 在 500 行时性能** | 写入耗时 | 预校验阶段已批量查询；INSERT 500 条在单事务内可接受；列为优化项（Q6） |
| R9 | **价格历史 `UNION ALL` 分页性能** | 数据量大时慢 | 两张 log 表各有 `(created_at DESC)` 索引；分页用 `Page` 插件 |
| R10 | **`ScmW3PgITBase` 与 W2 基类重复** | 维护成本 | 新建独立 ScmW3PgITBase；W2 基类不修改；公共抽取以后单独重构（Q8=B） |

---

## 11. 实施顺序（确认后执行）

```text
T0  Step 0 清理（无行为变更）
    - W1 Product 文件全部保持不变（本次人工修订优先）；W3 使用 ScmCommonErrorCode.VERSION_CONFLICT
    - ts_baseline_ratchet.py 的 SCM_PREFIXES 加入 src/utils/scm-amount.ts（提前，供 T12 使用）
    - 全量重跑 W1+W2 后端单测 / PG IT / 前端单测 / TS 棘轮 / ESLint，确认基线未变

T1  V10__scm_pricing.sql（6 表 + customer 增列 + 全部索引）
    + ScmPricingMigrationIT + PriceSchemaIT

T2  V11__scm_pricing_permissions.sql（21 menu_id + role 授权 + setval）
    + PricingPermissionIT

T3  pricing 域骨架：常量 / 枚举 / 错误码 / 实体 / Form / VO
    + PricingErrorCodeTest + ScmPriceSourceEnumTest + ScmUnpricedReasonEnumTest

T4  SKU option（W3 P0 前置，先做，因为它解除前端阻塞）
    - ProductSkuOptionDao(+XML) / Form / VO×2 / Service / Controller
    - ProductSkuOptionIT + ProductSkuControllerTest
    - W1 既有测试全量回归

T5  协议价：AgreementPriceDao(+XML) / PricePeriodOverlapGuard / Validator / Service / QueryService / Controller
    + PriceSnapshotTest + AgreementPriceValidatorTest + PricePeriodOverlapGuardTest
    + AgreementPriceIT + PriceEffectivePeriodIT + PriceOverlapIT + PriceOverlapConcurrencyIT
    + AgreementPriceControllerTest

T6  客户类型价：同 T5 结构（含停用类型拒绝）
    + CustomerTypePriceValidatorTest + CustomerTypePriceIT + CustomerTypePriceControllerTest

T7  价格历史：PriceHistoryDao(+XML) / PriceHistoryQueryService / Controller
    + PriceHistoryQueryIT + PriceHistoryControllerTest

T8  价格解析：PriceResolver（软 + 严）
    + PriceResolverTest + PriceResolverIT + PriceResolveControllerTest

T9  批量调价：PriceBatchValidator / PriceBatchWriter / PriceBatchService / Controller
    + PriceBatchValidatorTest + PriceBatchWriterIT + PriceBatchAuditIT

T10 可见性：CustomerSkuVisibilityEntity / ChangeSet / Dao(+XML) / Service
    + CustomerService / CustomerQueryService / CustomerAddForm / CustomerUpdateForm / CustomerVO 增补
    + CustomerSkuVisibilityChangeSetTest + CustomerSkuVisibilityIT
    + CustomerVisibilityControllerTest

T11 引用检查启用：CustomerService.assertNotReferenced + CustomerTypeService 扩展
    + CustomerReferenceIT

T12 前端基础设施
    - types/business/scm/pricing.d.ts
    - constants/business/scm/pricing-const.ts（含 UNPRICED_REASON_ENUM）+ 注册进 constants/index.ts
    - utils/scm-amount.ts + test/scm-amount.test.mjs
    - api/business/scm/{pricing-api,product-sku-api,customer-visibility-api}.ts
    - views/business/scm/pricing/{pricing-form-model,pricing-errors}.ts + test/pricing-form-model.test.mjs + test/pricing-errors.test.mjs
    - table-id-const.ts 追加 5 个 ID
    - sku-select 改造为远程搜索

T13 前端页面（Copy First + Adapt）
    - agreement-price-list.vue + agreement-price-form-drawer.vue（复制 C 骨架）
    - customer-type-price-list.vue + customer-type-price-form-drawer.vue（复制 C 骨架）
    - customer-type-price-batch.vue（新写）
    - price-history-list.vue（新写）
    - price-preview.vue（新写）
    - customer-sku-visibility-list.vue（复制 C 骨架，剪枝为只读）
    - customer-form-drawer.vue 扩展（商品可见范围）

T14 e2e/scm-pricing.spec.ts（2 用例）+ tools/w3_e2e_accounts.py

T15 门禁全跑（§9.6 九项）+ tools/verify_w3_legacy.py + v3-applied-migrations.sha256

T16 验收报告（含 Frontend Migration Provenance 表）+ approval 文件
```

---

## 12. 人工确认项（已批准：Q2 修订，Q8=B，其余=A）

| # | 问题 | 选项 | 推荐 |
|---|---|---|---|
| **Q1** | **批量调价的失败通道** | (A) `code=0` + `committed=false` + `failures[]`（校验报告语义）<br>(B) 两段式端点（`/batch/validate` + `/batch`）<br>(C) 保持 legacy：抛 `40035`，只带第一行错误 | **(A)**：规格 §7 要求逐行报告，而 `ScmBusinessException → ResponseDTO.error` 无 `data` 通道；(B) 有竞态；(C) 不满足规格 |
| **Q2** | 价格与可售性拆开 | **按人工修订**：UNPRICED 仅 NO_PRICE_SOURCE；不可售独立 sellable/unavailableReason；W1 不变 | 已批准 |
| **Q3** | **取价试算端点（`/scm/pricing/resolve`）是否入 W3** | (A) 入 W3（让优先级/生效时间/UNPRICED 可验证）<br>(B) 不入，Resolver 只作为内部 Service，等 W4 由 Order 消费 | **(A)**：否则「Price Resolver / Price Priority / UNPRICED 语义」三项无法验收，且 legacy 无端点可参考 |
| **Q4** | 区间保护 | **A**：父维度 FOR UPDATE → overlap query → write；不加 exclusion 或 effective_from 唯一约束；真实 PG 并发 IT | 已批准 |
| **Q5** | **可见性是否需要独立审计表** | (A) 不建，走客户聚合的 `@OperateLog`<br>(B) 建 `customer_sku_visibility_operation_log`（legacy 无事实源） | **(A)**：legacy 无此表；`@OperateLog` 已记录客户 update 的前后值 |
| **Q6** | **批量写入是否改批量 INSERT** | (A) 保留逐条 INSERT（≤500 行）<br>(B) 改 `<foreach>` 批量 INSERT | **(A)**：逐条可复用 `@Version`/留痕/唯一约束映射；(B) 列为后续优化 |
| **Q7** | **批量调价是否支持 Excel 导入** | (A) 不支持（legacy 无、C 无）<br>(B) 支持（新能力） | **(A)**：legacy/C 都无事实源；用户 W3 范围写的是「Bulk Price Update」未提导入 |
| **Q8** | PG IT 基类 | **B**：新建 ScmW3PgITBase，不修改 ScmW2PgITBase；公共抽取以后另案 | 已批准 |
| **Q9** | **协议价是否也需要批量调价** | (A) 不做（legacy 批量只针对客户类型价）<br>(B) 做 | **(A)** |
| **Q10** | **SKU option 是否默认只返回上架 SKU** | (A) 默认返回全部（含下架），由调用方传 `status`<br>(B) 默认只返回上架 | **(A)**：可见性白名单需要看到下架 SKU；协议价等业务调用方显式传 `status='ON_SHELF'` |
| **Q11** | **价格历史是否加导出** | (A) 不加（legacy 无）<br>(B) 加 CSV 导出 | **(A)** |
| **Q12** | **菜单 id 425 / 435 / 486-487 的归属** | (A) 425 挂 W1 商品菜单、435 挂 W2 客户菜单、501+ 为 W3 价格中心<br>(B) 全部塞进 501+ 新块 | **(A)**：425 是商品读能力、435 是客户域页面，挂到各自域更符合导航直觉；已在 §3.3 给出理由 |

---

## 13. Verification Closure 实际证据索引

本节记录本轮冻结实现的实际测试形态；前面的目标测试矩阵保留为设计意图，不把测试文件机械拆成多个类。

| 区域 | 实际入口 | 数量 / 结果 | 证据 |
|---|---|---:|---|
| PG IT | `PricingIT` + `ScmPricingMigrationIT` + W1/W2 IT | 94 / 94 PASS | `.runtime/w3-all-pgit-closure.log` |
| Web Test | `PricingWebTest` + W1/W2 Web Test | 120 / 120 PASS（全量 Maven Test） | `.runtime/w3-backend-full-closure.log` |
| 前端 | `npm test` | 20 / 20 PASS | `.runtime/w3-frontend-closure-final.log` |
| Playwright | Product / Customer / Supplier / Pricing | 8 / 8 PASS | `.runtime/playwright-result.json` |
| 冻结 | V6–V11 SHA-256、V10/V11 Flyway | migration PASS；legacy 因用户删除无法复验 | `.runtime/w3-legacy-recheck.json`、`v3-applied-migrations.sha256` |

## 14. 完成定义（DoD）

```text
1. V10 / V11 应用成功，Flyway validate 通过，V6–V9 哈希未变
2. 后端单测：SCM 全量（W1+W2+W3）100% 通过，无跳过
3. 后端 PG IT：SCM 全量 100% 通过（含新增 16 个 IT 类）
4. 后端 Web 层测试：SCM 全量 100% 通过
5. 前端单测：100% 通过（含新增 3 个 .test.mjs）
6. TS 棘轮 check PASS（new 0 / SCM 0 / total ≤ 1974）
7. ESLint（`eslint src`）0 error
8. vite build 成功
9. Playwright e2e 2/2（scm-pricing.spec.ts）；W1/W2 的 e2e 各 2/2 重跑通过
10. verify_w3_legacy.py PASS（legacy SHA-256 + V6–V9 哈希 + 前端 provenance 注释 + W1 既有文件零 diff）
11. v3-applied-migrations.sha256 生成（V6–V11）
12. 审计文档 P1–P27 逐条给出「实现位置 + 测试方法名」对账表
13. W3 验收报告（含 Frontend Migration Provenance 表）落地
14. 全部待确认项（Q1–Q12）已获得人工答复并回填本文件
```

---

## 附录 A：本设计与 legacy 的差异总表（审查用）

| # | 差异 | 类型 | 依据 |
|---|---|---|---|
| 1 | 拆 `module/scm/pricing` 独立包 | 结构升级 | legacy 全在 `customer` 包 |
| 2 | `resolve` 拆软 / 严双模式 | **修正 D1/D10** | 独立表达价格状态与可售状态 |
| 3 | 批量响应改为校验报告（`committed` + `failures[]`） | **修正 D14** | 规格 §7 |
| 4 | 批量预校验收集全部失败行（不短路） | **修正 D3/A4** | 规格 §7 |
| 5 | 批次幂等（部分唯一索引 + 40948） | **修正 D2/G5** | 规格 §7 审计要求 |
| 6 | 批量先按 `customer_type_id` 排序再加锁 | **修正 D20 的死锁风险** | 新增加固 |
| 7 | 预校验阶段批量查询（消除 N+1） | **部分修正 D4** | 性能 |
| 8 | 审计操作人用真实用户（去 `"SYSTEM"`） | **修正 D5** | 可追责 |
| 9 | 错误码不再复用 40931、不再用 40934 | **修正 D6** | 统一 40921 + 新增 40949 |
| 10 | `countOverlapping` 显式 `jdbcType` | **修正 D18** | 驱动兼容 |
| 11 | 类型价校验检查类型启用状态 | **修正 D12** | 与 `CustomerService.requireType` 对齐 |
| 12 | 启用客户/类型删除的 pricing 引用检查 | **修正 D17** | 启用 W2 预留钩子 |
| 13 | 历史 `current_*` 硬删时显式区分 | **修正 D13** | 语义清晰 |
| 14 | 新增 `/scm/product/sku/option-list` | 新增能力 G2 | 用户 W3 P0 前置 |
| 15 | 新增 `/scm/pricing/resolve` | 新增能力 G3 | 让优先级/UNPRICED 可验证 |
| 16 | 新增 `scm:pricing:*` / `scm:customer:visibility:*` / `scm:product:sku:query` 权限 | 新增能力 G8 | V2 权限体系 |
| 17 | 新增价格中心菜单分组（501–506） | 新增能力 G9 | V2 导航 |
| 18 | 新增可见性只读反查页 | 新增能力 G6 | C 有同功能页（剪枝） |
| 19 | 新增共享金额工具 + 统一缺价文案 | 修正 A19/G11 | 前端一致性 |
| 20 | 新增 UNPRICED 原因枚举 | 新增能力 G1 | 让缺价可诊断 |
| 21 | `customer.visibility_policy` 落到 V2（W2 未建） | 补齐 W2 缺口 | legacy 形状 |
| 22 | 价格表新增 3 个索引（effective / sku_id 反查） | 性能 | 见 §3.4 |
| 23 | 价格表新增 3 个错误码（40035/40036/40948/40949） | 新增 | 见 §4.5.1 |
| 24 | 可见性错误码 4 个（40033/40034/40037/40932） | 新增 | 见 §4.5.2 |
| 25 | `ScmPriceSourceEnum` 严格 3 值（不含 OVERRIDE） | 保持 legacy 边界 | P23 |

## 附录 B：文件变更总清单

**新增（后端）**

```text
sa-admin/src/main/resources/db/migration/V10__scm_pricing.sql
sa-admin/src/main/resources/db/migration/V11__scm_pricing_permissions.sql
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/pricing/**            （约 42 个文件）
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/constant/CustomerErrorCode.java（改）
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/dao/CustomerSkuVisibilityDao.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/domain/entity/CustomerSkuVisibilityEntity.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/domain/form/CustomerSkuVisibilityItemForm.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/domain/vo/CustomerSkuVisibilityVO.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/domain/vo/CustomerSkuVisibilityReverseVO.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/manager/CustomerSkuVisibilityChangeSet.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/service/CustomerSkuVisibilityService.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/controller/ProductSkuController.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/dao/ProductSkuOptionDao.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/domain/form/ProductSkuOptionQueryForm.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/domain/vo/ProductSkuOptionVO.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/domain/vo/ProductSkuOptionListVO.java
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/service/ProductSkuOptionQueryService.java
sa-admin/src/main/resources/mapper/business/scm/pricing/*.xml                （6 个）
sa-admin/src/main/resources/mapper/business/scm/product/ProductSkuOptionDao.xml
sa-admin/src/main/resources/mapper/business/scm/customer/CustomerSkuVisibilityDao.xml
sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/pricing/**            （约 20 个测试类）
sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/customer/CustomerSkuVisibility*Test/IT.java
sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/product/ProductSkuOptionIT.java
sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/product/ProductSkuControllerTest.java
```

**修改（后端，最小面）**

```text
module/scm/customer/constant/CustomerErrorCode.java          +4 码
module/scm/customer/domain/form/CustomerAddForm.java         +visibilityPolicy +visibilities
module/scm/customer/domain/form/CustomerUpdateForm.java      同上
module/scm/customer/domain/vo/CustomerVO.java                +visibilityPolicy
module/scm/customer/domain/vo/CustomerDetailVO.java          +visibilityPolicy +visibilities
module/scm/customer/service/CustomerService.java             +可见性差量 +引用检查启用
module/scm/customer/service/CustomerQueryService.java        +detail 带 visibilities +反查分页
module/scm/customer/controller/CustomerController.java       +反查端点
module/scm/product/constant/ProductErrorCode.java            不修改（人工修订优先）
```

**新增（前端）**

```text
src/views/business/scm/pricing/{agreement-price-list,customer-type-price-list,
  customer-type-price-batch,price-history-list,price-preview}.vue
src/views/business/scm/pricing/components/{agreement-price-form-drawer,
  customer-type-price-form-drawer}.vue
src/views/business/scm/pricing/{pricing-form-model,pricing-errors}.ts
src/views/business/scm/customer/customer-sku-visibility-list.vue
src/api/business/scm/{pricing-api,product-sku-api,customer-visibility-api}.ts
src/constants/business/scm/pricing-const.ts
src/types/business/scm/pricing.d.ts
src/utils/scm-amount.ts
test/{pricing-form-model,scm-amount,pricing-errors}.test.mjs
e2e/scm-pricing.spec.ts
```

**修改（前端，最小面）**

```text
src/components/business/scm/sku-select/index.vue            远程搜索改造
src/views/business/scm/customer/components/customer-form-drawer.vue  +商品可见范围区块
src/constants/index.ts                                      +注册 scmPricing
src/constants/support/table-id-const.ts                     +5 个表 ID
tools/ts_baseline_ratchet.py                             SCM_PREFIXES 加入 src/utils/scm-amount.ts
```

**新增（工具 / 文档）**

```text
tools/verify_w3_legacy.py
tools/w3_e2e_accounts.py
docs/architecture/v3-applied-migrations.sha256
docs/architecture/2026-09-15-w3-pricing-验收报告.md（含 Frontend Migration Provenance）
docs/architecture/2026-09-15-w3-pricing-approval.md
```

## 附录 C：Frontend Migration Provenance 计划

### C.1 目的

W2 起建立的规则：**每一个从 C 复制而来的 Vue 文件，都必须能回答「从哪里来、剪了什么、改了什么、怎么验收」**。W3 沿用并把它做成**可机器校验**的。

### C.2 落地形式（三层）

**第 1 层：文件头注释块（强制）**

```vue
<!--
  * 客户协议价列表
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/product/product-price-list.vue
  *      （Copy First + Adapt）
  * 复制日期：2026-09-15
  *
  * 剪枝：
  * - 删除裸 ID 列（productId / skuId / customerId / customerLevelId）
  * - 删除 priceType 4 值枚举（C 的 BASE/LEVEL/MARKET/AGREEMENT，V2 只保留 MARKET/AGREEMENT/CUSTOMER_TYPE）
  * - 删除 customerLevelId 的 a-input-number
  * - 删除 resizable / @resizeColumn / handleResizeColumn（V2 无 TableHeaderCell 使用）
  * - 删除 onMounted 中「仅当 route.query.productId 存在才查询」的分支（C 缺陷 C13）
  *
  * 适配：
  * - API：/product/price/* → /scm/pricing/agreement-price/*
  * - 枚举：SmartEnum<number> → SmartEnum<string>；注册进 src/constants/index.ts
  * - 权限：product:price:* → scm:pricing:agreement:*
  * - 字段：priceId → agreementPriceId；补 customerCode/customerName/skuCode/productName/specName
  * - 金额：a-input-number → a-input（4 位定点字符串）；用 src/utils/scm-amount.ts 展示
  * - 校验：新增 pricing-form-model.ts 纯函数（客户必选、单价 4 位非负、区间合法）
  * - 错误：新增 pricing-errors.ts（40933 / 40921 / 40030 / 40031）
  * - 时间：RangePicker 替代两个独立 DatePicker；不做客户端时区换算
  *
  * 验收：
  * - e2e/scm-pricing.spec.ts（live pricing pilot）
  * - TS 棘轮：src/views/business/scm/pricing/ 0 错误
  * - 截图：.runtime/w3-agreement-price-list.png
-->
```

**第 2 层：W3 验收报告的 Frontend Migration Provenance 表（强制）**

| V2 文件 | C 来源文件 | 复制日期 | 剪枝项 | 适配项 | 验收证据 |
|---|---|---|---|---|---|
| `pricing/agreement-price-list.vue` | `views/business/product/product-price-list.vue` | 2026-09-15 | 裸 ID 列 / `priceType` 4 值 / `customerLevelId` 输入 / `resizable` / `onMounted` 分支 | API / 枚举 / 权限 / 字段 / 金额 / 校验 / 错误 / 时间 | e2e 用例 1；TS 0 错误；截图 |
| … | … | … | … | … | … |

**第 3 层：机器校验（`tools/verify_w3_legacy.py`）**

```text
断言 A：legacy（xsy-scm-server / xsy-scm-web）全量文件 SHA-256 与 W2 基线一致
断言 B：V6–V9 migration SHA-256 与 w2-applied-migrations.sha256 一致
断言 C：module/scm/product/** 的 W1 既有文件零 diff（仅允许新增文件）
断言 D：所有标记为「复制」的前端文件，其头部注释块包含
        `来源：project-reference-examples/xsy-scm/` 与 `复制日期：`
断言 E：C 的源文件 SHA-256 与本次记录一致（防止 C 被静默改动）
断言 F：V11 中每个 menu_type=2 的 component 路径，在 src/views/** 下存在同名文件
断言 G：V11 中每个 menu_type=3 的权限码，在 Java 源码的 @SaCheckPermission 中出现
输出：.runtime/w3-legacy-recheck.manifest + .json
```

---

**人工评审已通过；按 T0 → T16 执行，W3 验收后停止。**
