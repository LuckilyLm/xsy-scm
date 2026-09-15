# W1 Product Pilot · Product Legacy Audit（只读审计）

> 审计对象：`xsy-scm-server` / `xsy-scm-web` 的 Product 域（legacy，冻结只读）
> 冻结基线：HEAD `95a54233586aa3c652e55cd833bb346d51f5a952`，branch `feature/sprint5`
> 冻结快照：`D:\DevCaches\Codex\reviews\xsy-scm-legacy-freeze-2026-09-14\`
> 审计性质：**只读**。本文件不修改、不格式化、不移动任何 legacy 文件。
> 审计日期：2026-09-14

---

## 1. 审计范围与方法

对以下路径做了穷尽式读取（而非抽样）：

| 层 | 路径 |
|---|---|
| 后端主源码 | `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/**`（25 个 Java 文件） |
| 后端 Mapper XML | `xsy-scm-server/src/main/resources/mapper/product/{ProductSpuMapper,ProductSkuMapper,ProductCategoryMapper}.xml` |
| 后端 DDL / Seed | `xsy-scm-server/src/main/resources/db/migration/V1__create_sprint1_schema.sql`、`V2__seed_sprint1_demo_data.sql` |
| 后端测试 | `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/**`（6 个测试类，共 20 个测试方法） |
| 前端页面 | `xsy-scm-web/src/pages/product/{ProductPage,ProductDrawer,SkuEditableTable,productFormModel}.tsx/ts` |
| 前端契约 | `xsy-scm-web/src/api/products.ts`、`src/api/product-categories.ts`、`src/types/product.ts` |
| 参考范式 | `project-reference-examples/xsy-scm/xsy-scm-server/xsy-scm-server` 与 `project-reference-examples/xsy-scm/xsy-scm-web` 的商品实现 |

结论提取原则：**只提取业务规则与不变量**，不复制旧包结构，不翻译旧 React。

---

## 2. 后端资产清单

### 2.1 分层文件（legacy 包结构，V2 不沿用）

```
com.xianshuyuan.scm.product
├─ controller/  ProductController、ProductCategoryController
├─ dto/         ProductSaveRequest、ProductSkuSaveRequest、ProductPageQuery、
│               ProductStatusRequest、ProductCategorySaveRequest
├─ entity/      ProductSpuEntity、ProductSkuEntity、ProductCategoryEntity、
│               ProductType、ShelfStatus
├─ mapper/      ProductSpuMapper、ProductSkuMapper、ProductCategoryMapper
├─ service/     ProductApplicationService、ProductQueryService、ProductCategoryService、
│               ProductAggregateValidator、ProductSkuChangeSet、ProductErrorCodes
├─ converter/   ProductConverter
└─ vo/          ProductDetailResponse、ProductSummaryResponse、ProductSkuResponse、
                ProductCategoryTreeNode
```

### 2.2 枚举

| 枚举 | 取值 | 用途 |
|---|---|---|
| `ShelfStatus` | `ON_SHELF` / `OFF_SHELF` | SPU 与 SKU 共用上下架状态 |
| `ProductType` | `STANDARD`（标品） / `NON_STANDARD`（非标品） | SKU 级属性 |
| 分类状态（字符串，无枚举类） | `ENABLED` / `DISABLED` | `product_category.status`，DTO 用 `@Pattern` 校验 |

### 2.3 实体字段（V1 DDL 为准）

**product_category**

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| parent_id | BIGINT NULL | `ck: level=1 → parent_id IS NULL；level>1 → parent_id IS NOT NULL` |
| category_code | VARCHAR(64) NOT NULL | partial unique（`WHERE deleted = FALSE`） |
| name | VARCHAR(100) NOT NULL | |
| level | SMALLINT NOT NULL | `CHECK (level BETWEEN 1 AND 3)` |
| sort_order | INTEGER NOT NULL DEFAULT 0 | |
| status | VARCHAR(16) NOT NULL DEFAULT 'ENABLED' | `CHECK IN ('ENABLED','DISABLED')` |
| version | INTEGER NOT NULL DEFAULT 0 | `CHECK (version >= 0)` |
| deleted | BOOLEAN NOT NULL DEFAULT FALSE | 逻辑删除 |
| created_at / updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| created_by / updated_by | VARCHAR(64) NULL | legacy 写死 `"SYSTEM"` |

**product_spu**

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| spu_code | VARCHAR(64) NOT NULL | partial unique（`WHERE deleted = FALSE`） |
| name | VARCHAR(150) NOT NULL | 索引 `idx_product_spu_name` |
| alias | VARCHAR(150) NULL | |
| category_id | BIGINT NOT NULL | 索引；无 FK |
| description | VARCHAR(1000) NULL | |
| status | VARCHAR(20) NOT NULL DEFAULT 'OFF_SHELF' | `CHECK IN ('ON_SHELF','OFF_SHELF')` |
| version | INTEGER NOT NULL DEFAULT 0 | 乐观锁 |
| deleted | BOOLEAN NOT NULL DEFAULT FALSE | |
| created_at / updated_at / created_by / updated_by | | |

**product_sku**

| 列 | 类型 | 约束 |
|---|---|---|
| id | BIGINT identity PK | |
| spu_id | BIGINT NOT NULL | 索引；无 FK |
| sku_code | VARCHAR(64) NOT NULL | partial unique（`WHERE deleted = FALSE`） |
| barcode | VARCHAR(64) NULL | partial unique（`WHERE deleted = FALSE AND barcode IS NOT NULL`） |
| spec_name | VARCHAR(150) NOT NULL | |
| spec_values | JSONB NOT NULL DEFAULT '{}' | `CHECK (jsonb_typeof(spec_values) = 'object')` |
| sale_unit | VARCHAR(32) NOT NULL | |
| product_type | VARCHAR(20) NOT NULL | `CHECK IN ('STANDARD','NON_STANDARD')` |
| market_price | NUMERIC(18,4) NOT NULL | `CHECK (market_price >= 0)` |
| status | VARCHAR(20) NOT NULL DEFAULT 'OFF_SHELF' | `CHECK IN ('ON_SHELF','OFF_SHELF')` |
| is_default | BOOLEAN NOT NULL DEFAULT FALSE | **partial unique `(spu_id) WHERE deleted = FALSE AND is_default = TRUE`** |
| sort_order | INTEGER NOT NULL DEFAULT 0 | |
| version | INTEGER NOT NULL DEFAULT 0 | 乐观锁 |
| deleted | BOOLEAN NOT NULL DEFAULT FALSE | |
| created_at / updated_at / created_by / updated_by | | |

### 2.4 API 契约（legacy，`ApiResponse` + `PageData`）

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/products` | `page,pageSize,keyword,categoryId,spuStatus,skuStatus,productType` | `PageData<ProductSummaryResponse>` |
| GET | `/api/products/{id}` | — | `ProductDetailResponse` |
| POST | `/api/products` | `ProductSaveRequest` | `Long`（新 SPU id） |
| PUT | `/api/products/{id}` | `ProductSaveRequest` | `void` |
| PUT | `/api/products/{id}/status` | `{version,status}` | `void` |
| DELETE | `/api/products/{id}?version=` | — | `void` |
| GET | `/api/product-categories/tree` | — | `List<ProductCategoryTreeNode>` |
| POST | `/api/product-categories` | `ProductCategorySaveRequest` | `Long` |
| PUT | `/api/product-categories/{id}` | `ProductCategorySaveRequest` | `void` |
| DELETE | `/api/product-categories/{id}` | — | `void` |

`ProductSummaryResponse` 同时返回 `defaultSku`（单对象）、`skuCount`、`minMarketPrice`/`maxMarketPrice`（字符串）以及**完整 `skus` 列表**（列表页展开行使用，避免二次请求）。

`ProductSkuResponse.marketPrice` 是 **String**，由 `ProductQueryService.price()` 做 `setScale(4).toPlainString()`。

### 2.5 错误码（`ProductErrorCodes`）

| 码 | HTTP | 语义 |
|---|---|---|
| 40410 | 404 | 商品分类不存在 |
| 40010 | 400 | 商品分类最多支持三级 |
| 40011 | 400 | 上级分类不正确 |
| 40910 | 409 | 分类下存在子分类，不能删除 |
| 40911 | 409 | 分类下存在商品，不能删除 |
| 40020 | 400 | 至少一个 SKU 必须保留 |
| 40021 | 400 | 商品必须且只能有一个默认 SKU |
| 40022 | 400 | SKU 编码重复 |
| 40023 | 400 | SKU 条码重复 |
| 40024 | 400 | SKU 规格组合重复 |
| 40025 | 400 | SKU 市场价不能小于零 |
| 40420 | 404 | 商品不存在 |
| 40920 | 409 | SKU 不属于当前商品 |
| **40921** | 409 | **数据已被其他操作修改，请刷新后重试（乐观锁冲突）** |

---

## 3. 必须保留的业务不变量（逐条定位到实现）

| # | 不变量 | legacy 实现位置 | 证据 |
|---|---|---|---|
| P1 | SPU 至少 1 个 SKU | `ProductAggregateValidator.validate` 首段 | `ProductAggregateValidatorTest.rejectsEmptySkuList`；DTO `@NotEmpty` |
| P2 | 恰好 1 个默认 SKU | `ProductAggregateValidator.validate` 计 `defaultSku` 数量 | `ProductAggregateValidatorTest.rejectsMoreThanOneDefaultSku` |
| P3 | 默认 SKU 由 DB 兜底唯一 | DDL partial unique `uk_product_sku_default_active` | V1 DDL 第 134–135 行 |
| P4 | 分类最多三级，层级由父级推导 | `ProductCategoryService.resolveLevel`（`MAX_LEVEL = 3`） | `ProductCategoryServiceTest.rejectsFourthLevelCategory` |
| P5 | 商品只能挂到**已启用的第三级**分类 | `ProductCategoryService.requireSelectableCategory` | `level != 3 \|\| status != ENABLED → 40011` |
| P6 | 分类有子分类或有商品则禁止删除 | `ProductCategoryService.delete` | 40910 / 40911 |
| P7 | 分类停用时不可在其下新增子分类 | `resolveLevel` 校验 parent status | `CATEGORY_PARENT_INVALID` |
| P8 | SKU 编辑按稳定 id 差量同步 | `ProductSkuChangeSet.between` + `ProductApplicationService.update` | `ProductSkuChangeSetTest.retainsExistingIdsAndSeparatesInsertAndRemoval` |
| P9 | 已存在 SKU 保留 id / version | 同上：`updated` 走 `updateById`，不重建 | `ProductApplicationServiceIT.updatesSkuCollectionWithoutChangingRetainedSkuId` |
| P10 | 禁止 delete-and-recreate | 差量同步只对 `removedIds` 调 `deleteByIds` | 同上断言 `retainedIdAfter == retainedId` |
| P11 | SKU id 不属于当前 SPU → 拒绝 | `ProductSkuChangeSet.between` 中 `unmatched.remove(request.id()) == null` | 40920；`ProductSkuChangeSetTest.rejectsSkuIdOwnedByAnotherSpu` |
| P12 | 默认 SKU 切换 = 先清空再置位 | `skuMapper.clearDefault(spuId)` 后逐个 `updateById` | `clearDefault` 为 XML 自定义 UPDATE |
| P13 | 删除最后一个 SKU 被拒绝（前端约束） | `productFormModel.removeSku` 抛错 | `at least one SKU` |
| P14 | 删除默认 SKU 后，第一个剩余 SKU 自动成为默认 | `productFormModel.removeSku` 的 `removed.defaultSku ? index === 0` | 前端规则 |
| P15 | 新增商品自动带 1 个默认 SKU | `createEmptyProductForm` → `skus: [createEmptySku(0, true)]` | 前端规则 |
| P16 | SKU 编码大小写不敏感去重 | `ProductAggregateValidator.normalizeCode`（`trim().toUpperCase()`） | `rejectsDuplicateNormalizedSkuCode` |
| P17 | 条码非空才去重 | `trimToNull` 后判空 | `rejectsDuplicateNonBlankBarcode` |
| P18 | 规格组合去重需先归一化（key/value 均 trim+小写、TreeMap 排序） | `normalizeSpecifications` | `rejectsDuplicateSpecificationCombination` |
| P19 | 市场价 ≥ 0 | DTO `@DecimalMin("0.0000")` + validator + DB CHECK | `rejectsNegativeMarketPrice` |
| P20 | 搜索覆盖 SPU 编码/名称/别名 + SKU 编码/条码 | `ProductSpuMapper.xml` 的 `ILIKE` + `EXISTS` | 分页 SQL |
| P21 | 列表不产生 N+1 查询 | `selectActiveBySpuIds` 批量取 SKU，再内存分组 | `ProductQueryServiceIT` 测试名即 `WithoutNPlusOneShape` |
| P22 | 详情返回 JSONB 规格快照 | `ProductSkuMapper.xml` 指定 `JsonbStringMapTypeHandler` | `ProductQueryServiceIT.readsJsonbSpecificationSnapshotInProductDetail` |
| P23 | 删除后编码可复用 | partial unique 均带 `WHERE deleted = FALSE` | V1 DDL |
| P24 | SPU 与 SKU 各自独立乐观锁 | `@Version` 分别标注 | 40921 |

---

## 4. 前端 legacy 行为审计（React，仅作行为参考）

| 区域 | legacy 行为 | 是否保留到 V2 |
|---|---|---|
| 筛选栏 | 分类 Cascader、关键字、SPU 状态；「高级筛选」再展开 SKU 状态 + 商品类型 | 保留语义，重做为 Vue 版式 |
| 列表列 | 名称、SPU 编码、分类路径、默认单位、市场价区间、SKU 数、状态、别名、更新时间、操作 | 保留 |
| 展开行 | 展开显示该 SPU 的 SKU 明细表 | 保留 |
| 价格区间 | min == max 时只显示一个值 | 保留 |
| 分类级联 | 三级路径回填；只能选到第三级 | 保留 |
| 抽屉 | 基础信息 + SKU 可编辑表格，宽 1080 | 保留（Vue 版式） |
| SKU 表格 | 默认单选、编码、规格名、规格属性键值对增删、单位、市场价、类型、条码、状态、删除 | 保留 |
| 删除按钮 | 仅剩 1 个 SKU 时禁用 + Popconfirm | 保留 |
| 市场价校验 | 前端正则 `/^\d+(\.\d{1,4})?$/` | 保留 |
| 冲突提示 | HTTP 409 → 「数据已被其他人修改，请刷新详情后重试」 | 保留语义（V2 用 code 40921） |
| 权限 | `Permission` 组件 + `AUTHORITIES.productManage`（单一权限） | **改为** 细分 `scm:product:*` + `v-privilege` |
| 加载失败 | Alert + 重新加载 | 保留 |
| Tab | 「基础商品 / 加工品（禁用）」占位 | 不迁移（无业务依据，YAGNI） |

### 4.1 legacy 前端缺陷（V2 不复制）

1. **抽屉会话状态机过度复杂**：`seededSession` + `sessionStartRef` + `dataUpdatedAt` 三重判定，用于规避 React Query 缓存竞态。Vue3 + 按需请求详情可自然规避，不需要移植这套机制。
2. **`Permission` 依赖 `AuthProvider`**，而 V2 的 `AuthProvider` 未挂载；V2 必须改用 `v-privilege`（后端鉴权为准）。
3. 抽屉内市场价用字符串手工校验，未复用统一定点数格式化组件。
4. `ProductPage` 的筛选表单与 ProTable 的 `request` 通过 `form.getFieldsValue()` 隐式耦合，V2 应改为显式 `queryForm` 响应式对象。

---

## 5. 后端 legacy 缺陷与风险（V2 必须修正）

| # | 问题 | 位置 | 影响 | V2 处置 |
|---|---|---|---|---|
| D1 | `created_by` / `updated_by` 写死 `"SYSTEM"` | `ProductApplicationService`（create/update/updateStatus/delete）、`ProductConverter.toSpu/toSku`、`ProductSkuMapper.clearDefault` XML | 审计字段失真，无法追溯操作人 | 改用 SmartAdmin `SmartRequestUtil.getRequestUser()` 写入真实操作人 |
| D2 | `price()` 用 `setScale(4)` **未指定 RoundingMode** | `ProductQueryService.price` | 值超过 4 位小数时抛 `ArithmeticException`；依赖 DDL 恰好是 NUMERIC(18,4) 才不炸 | 改为 `setScale(4, RoundingMode.HALF_UP)`，与 `ScmFixedScale4Serializer` 一致 |
| D3 | 列表页返回完整 `skus` 数组 | `ProductSummaryResponse` | 大数据量时响应体偏大 | 保留（前端展开行需要），但分页上限收紧并在设计文档标注 |
| D4 | `clearDefault` 后依赖内存中旧 `version` 做 `updateById` | `ProductApplicationService.update` | `clearDefault` 不改 `version` 才侥幸正确，语义脆弱 | V2 显式规定：清默认 → 逐行带 version 更新 → 再插入新行 |
| D5 | 分类更新**无乐观锁** | `ProductCategorySaveRequest` 无 version 字段 | 并发编辑静默覆盖 | V2 分类更新携带 version（若前端不便，至少在设计中明确接受该风险并记录） |
| D6 | 无 `@OperateLog`、无权限校验 | 两个 Controller | 无操作审计、无鉴权 | V2 加 `@SaCheckPermission` + `@OperateLog` |
| D7 | 分页响应是 `PageData{records,page,pageSize,total}` | `common/api/PageData` | 与 SmartAdmin `PageResult{pageNum,pageSize,total,pages,list,emptyFlag}` 不兼容 | V2 统一用 `PageResult` |
| D8 | 分页参数用 `@RequestParam`（GET + query string） | `ProductController.page` | 与 SmartAdmin 统一 POST + `PageParam` 体不一致 | V2 改 POST + Form 继承 `PageParam` |
| D9 | **完全没有商品图片能力** | 全域无图片字段/表/接口 | 商品无主图/图集 | V2 **新增** `product_image` 表 + 上传接线（W1 明确的新能力） |
| D10 | 分类删除硬编码依赖 `countActiveProducts` 查 `product_spu` | `ProductCategoryMapper.xml` | 与分类表同 schema 才有效 | V2 保持同 schema 内自洽查询 |
| D11 | `delete` 与 `updateStatus` 的 version 传递方式不一致（query param vs body） | `ProductController` | 前端易错 | V2 统一：删除与改状态都以 body 传 `{id, version}` |

---

## 6. 测试覆盖审计（legacy 20 个测试方法）

| 测试类 | 类型 | 覆盖 | 缺口 |
|---|---|---|---|
| `ProductAggregateValidatorTest` | 纯单测（7） | 空 SKU、多默认、编码/条码/规格重复、负价 | 未覆盖「零个默认 SKU」；未覆盖规格 key 为空串 |
| `ProductSkuChangeSetTest` | 纯单测（2） | 保留 id、区分新增/删除、跨 SPU 拒绝 | 未覆盖 `requested` 为空（被 DTO 拦） |
| `ProductCategoryServiceTest` | 纯单测（4） | 层级推导、四级拒绝、排序、有商品禁删 | 未覆盖有子分类禁删（40910）、父级停用 |
| `ProductControllerTest` | `@WebMvcTest`（5） | 分页信封、pageSize 越界、空 SKU 400、40420、40921 | 未覆盖权限拒绝、未覆盖 `@Valid` 其他字段 |
| `ProductApplicationServiceIT` | `@SpringBootTest`（1） | 差量同步端到端（保留 id / 软删 / 新 id） | 未覆盖默认 SKU 切换、并发 version 冲突 |
| `ProductQueryServiceIT` | `@SpringBootTest`（2） | 按 SKU 编码搜到 SPU、JSONB 规格读回 | 未覆盖分类路径、价格区间、条码搜索 |

**必须补齐到 V2 的测试（W1 测试矩阵来源）**：默认 SKU 切换、零默认 SKU 拒绝、并发乐观锁冲突、分类 40910、分页边界、权限拒绝、搜索全字段覆盖、软删后编码复用。

---

## 7. 跨域依赖与 W1 排除项

### 7.1 Product 被谁依赖（V2 后续波次，W1 不实现）

| 依赖方 | 依赖点 | W1 处置 |
|---|---|---|
| order / mall | `selectOrderableByIds`、`selectAllOrderable`（要求 SPU 与 SKU 均 `ON_SHELF` 且未删除） | **只保留表结构与状态语义**，不迁移这两个查询方法 |
| purchase | `product_sku` 作为采购/收货行引用 | 不实现 |
| inventory | SKU 作为库存维度 | 不实现 |
| sorting / device | SKU 作为称重与分拣维度 | 不实现 |
| customer pricing | SKU 作为客户价维度 | 不实现 |

> 设计约束：`product_sku.status` 与 `product_spu.status` 的「可下单」语义（两者同时 `ON_SHELF`）必须在 W1 保留，因为后续波次会依赖；但**可下单查询本身**不在 W1 范围。

### 7.2 W1 明确不做（用户锁定）

customer、supplier、pricing、order、purchase、inventory、mall、marketing、miniapp。

### 7.3 Product 对 SmartAdmin 的依赖（W1 直接复用）

`ResponseDTO`、`PageResult`/`PageParam`/`SmartPageUtil`、Sa-Token `@SaCheckPermission`、`@OperateLog`、文件上传 `/support/file/upload`、全局异常、Vue Layout、动态菜单、`v-privilege`。

---

## 8. 审计结论

1. Product 域在 legacy 中是**完整且自洽**的一个限界上下文：分类 → SPU → SKU 三级聚合，无跨域写依赖，只有 2 个「可下单」读查询供后续波次使用。
2. 24 条业务不变量全部有实现与测试证据，**可以逐条映射到 V2**，无需猜测业务规则。
3. 有 **11 项实现缺陷**必须修正，其中 D1（审计人写死）、D2（定点数舍入）、D7/D8（响应与分页契约）、D9（无图片能力）是硬性缺口。
4. legacy 的**分类与商品是两张独立表**，与 SmartAdmin 参考实现（`t_category` 树 + `t_goods` 单表）模型不同：SmartAdmin 的 `goods` 是「单品」，无 SPU/SKU 聚合。因此 **SmartAdmin category/goods 只能作为工程范式（分层、命名、权限、前端版式）参考，不能作为数据模型参考**。
5. 前端 legacy 的交互规则可完整保留，但实现必须按 SmartAdmin Vue3 版式重写；`Permission`/`AuthProvider` 机制不可用，须改用 `v-privilege`。
