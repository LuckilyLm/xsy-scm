# W2 Customer + Supplier 验收报告

- 日期：2026-09-15
- 波次：W2（Customer + Supplier）
- 范围指令：2026-09-15 用户正式批准 W2 Target Design，Q1–Q14 全部按推荐方案批准，仅 Q5 修订
- 结论：**Go（附 2 项已修复缺陷与 1 项遗留限制，见 §7 / §8）**
- 边界：本报告止于 W2，**不自动进入 W3**

---

## 1 交付范围与硬约束遵守情况

| 约束 | 要求 | 实际 | 结论 |
|---|---|---|---|
| 新增 migration | 只允许 `V8__scm_customer_supplier.sql`、`V9__scm_customer_supplier_permissions.sql` | 恰好新增这两个，无其它 | ✅ |
| 不修改 V6/V7 | 字节不变 | SHA-256 与 W1 应用时一致（见 §6） | ✅ |
| 不修改 `module/scm/product/**` | 只读 | 35 个 java 文件无改动 | ✅ |
| legacy / reference 只读 | 冻结 | 900 文件逐项 SHA-256 一致 | ✅ |
| 正式代码只写 `v2/**` | — | 全部新增落在 `v2/xsy-scm-v2-server` 与 `v2/xsy-scm-v2-web` | ✅ |
| Q5 修订 | `warehouse` 不进 W2，且**不标记 "W3"** | V8 无 warehouse 表；文档与 seed 中无任何 "W3" 标记 | ✅ |

**SCM 只保留领域能力的落地结果**：W2 未新建任何认证、授权、员工上下文、权限、数据权限、日志、
字典、文件、Redis、分页、`ResponseDTO`、全局异常、校验、MyBatis-Plus、Web/MVC、Jackson 基础设施，
全部复用 SmartAdmin v3.31 原生能力。SCM 侧新增的只有领域语义（客户状态机、账期三形态、
供应商关联整表替换、快照冻结、业务锁与版本冲突）。

---

## 2 后端交付物

| 区域 | 文件数 | 说明 |
|---|---|---|
| `module/scm/customer/**` | 25 java | 2 Controller / 2 Dao / 2 Entity / 9 Form / 4 VO / 2 Validator / 3 Service / 1 ErrorCode |
| `module/scm/supplier/**` | 27 java | 2 Controller / 2 Dao / 2 Entity / 7 Form / 6 VO / 1 Validator / 3 Service / 2 Manager / 1 ErrorCode |
| `module/scm/common/**` | 16 java（W1 已有，W2 复用） | 枚举、错误码、异常、JSON 定点数序列化、`ScmDecimalStrings` |
| `mapper/business/scm/**` | 7 xml | 新增 `CustomerDao.xml`、`CustomerTypeDao.xml`、`SupplierDao.xml`、`SupplierSkuDao.xml` |
| 测试 | 28 java 类 | 17 单测/切片 + 10 PG IT + 1 IT 基类 |

### 2.1 迁移脚本

- `V8__scm_customer_supplier.sql`：`customer_type`、`customer`、`supplier`、`supplier_sku` 四张表。
  - 软删统一 `deleted BOOLEAN NOT NULL DEFAULT FALSE`；唯一约束一律 partial（`WHERE deleted = FALSE`）。
  - **刻意不建** `(supplier_id) WHERE is_default = TRUE` 唯一索引 —— legacy R12 允许多条默认来源。
  - 金额列 `NUMERIC(18,4)`；`supplier_sku.spec_values_snapshot` 为 `JSONB`。
- `V9__scm_customer_supplier_permissions.sql`：菜单 id 431–482，`component` 路径与 `src/views/**` 逐段一致。

### 2.2 已实现的 legacy 不变量（关键项）

| 编号 | 不变量 | 落点 |
|---|---|---|
| R2/R6 | `supplier_sku` 写入严格四段式：校验段 A → 锁段 → 校验段 B → 写段 | `SupplierSkuSyncManager.replace` |
| R4 | `sku_name_snapshot` 取 **SPU 名称**，不是 SKU 规格名 | `SupplierSkuSyncManager.fill` |
| R11 | 空数组 = 清空全部关联，不是「无操作」 | `SupplierSkuSyncManager.replace` |
| R12 | 允许同一供应商多条 `is_default = TRUE`，不加约束、不做单选限制 | V8 无该索引；`SupplierSkuItemForm` 无基数校验；前端用 checkbox |
| R16 | 可采购来源四表联动（supplier_sku → supplier → product_sku → product_spu） | `SupplierSkuDao.selectEnabledBySkuId`（W3 采购域复用） |
| 锁序 | `supplier` → `supplier_sku`，顺序固定 `FOR UPDATE` | `SupplierDao.selectActiveByIdForUpdate` → `SupplierSkuDao.selectActiveBySupplierIdForUpdate` |
| 客户状态机 | 只有 `COOPERATING` 可交易；状态变更只走 `updateStatus` | `ScmCustomerStatusEnum.tradable()`、`CustomerService.updateStatus` |
| 账期三形态 | `BY_AMOUNT` 用金额阈值；`BY_TIME` 用账期值 + 单位（月可选固定结算日 1–28） | `CustomerValidator`、`ScmCreditPeriodTypeEnum`、`ScmCreditPeriodUnitEnum` |
| 删除引用检查 | 客户类型被引用 → 40938；供应商被商品关联 → 40947 | `CustomerTypeValidator`、`SupplierValidator` |

---

## 3 Frontend Migration Provenance（逐文件）

原则：**C 已有同功能 SCM Vue 页面一律 Copy First + Adapt，禁止重新生成**。
C 根目录 = `project-reference-examples/xsy-scm/xsy-scm-web/src`；V2 根目录 = `v2/xsy-scm-v2-web/src`。

### 3.1 Copy（C 源码复制后适配）

| C source | V2 target | 标记 | 适配内容 |
|---|---|---|---|
| `views/business/customer/customer-list.vue`（376 行） | `views/business/scm/customer/customer-list.vue` | Copy + Adapt | 删 `balance` 列、批量删除、`row-selection`、`resizable`/`@resizeColumn`；状态变更由二态开关改 `a-dropdown` 四态；API → `/scm/customer/**`；权限 → `scm:customer:*`；`columns` 补 `TableColumnsType<CustomerRow>`；补请求序号 `requestId` |
| `views/business/customer/customer-list.vue` 内联抽屉（L93–147） | `views/business/scm/customer/components/customer-form-drawer.vue` | Copy + Adapt | 位移为独立组件；删 `customerLevelId`/`longitude`/`latitude`/区域级联；`parentCustomerId` → `CustomerSelect type-code="GROUP"`；`sellerId` → V2 原生 `EmployeeSelect`；新增账期 6 字段；状态改只读 |
| `views/business/purchase/supplier-list.vue`（328 行） | `views/business/scm/supplier/supplier-list.vue` | Copy + Adapt | 删「供应商ID」列、批量删除、`resizable`；新增「关联商品数」列与「关联商品」操作；API → `/scm/supplier/**`；权限 → `scm:supplier:*` |
| `views/business/purchase/supplier-list.vue` 内联抽屉（L83–117） | `views/business/scm/supplier/components/supplier-form-drawer.vue` | Copy + Adapt | 位移为独立组件；删「供应商ID」字段与状态字段（新建强制 `ENABLED`）；编辑先拉详情回填 |
| `views/business/product/product-supplier-list.vue`（334 行） | `views/business/scm/supplier/supplier-sku-list.vue` | Copy + Adapt（骨架） | **SPU 级 → SKU 级**：C 的供货关系挂在 SPU 上，V2 是 `supplier_sku`，行标识/字段/唯一性都不同 → 只借页面骨架，剪掉全部编辑能力改为**只读反查**分页表 |
| `components/business/customer-select/index.vue`（69 行） | `components/business/scm/customer-select/index.vue` | Copy + Adapt | API → `/scm/customer/option/list`；新增 `excludeId` prop（供上级集团客户排除自身）；新增 `typeCode` 过滤 |
| `components/business/supplier-select/index.vue`（75 行） | `components/business/scm/supplier-select/index.vue` | Copy + Adapt | API → `/scm/supplier/option/list` |
| `constants/business/customer/customer-const.ts`（76 行） | `constants/business/scm/customer-const.ts` | Copy + Adapt | 改为 V2 `SmartEnum<T>` 形状；新增 `CUSTOMER_TYPE_STATUS_ENUM`；按 V2 规则追加注册到 `src/constants/index.ts` |
| `constants/business/supplier/supplier-const.ts`（49 行） | `constants/business/scm/supplier-const.ts` | Copy + Adapt | 同上；新增 `SUPPLIER_SKU_STATUS_ENUM` |
| `api/business/customer/customer-api.ts` | `api/business/scm/customer-api.ts` | Copy + Adapt | 路径 → `/scm/customer/**`；DTO → V2 contract |
| `api/business/customer/customer-period-api.ts` | （并入 `customer-api.ts`） | Copy + Adapt | C 把账期做成分离的「账期管理」页；V2 按 Target Design 把账期 6 字段并入客户主档 → **不单独迁页面**，接口能力合并进 `customer-api.ts` |
| `api/business/purchase/supplier-api.ts` | `api/business/scm/supplier-api.ts` | Copy + Adapt | 路径 → `/scm/supplier/**` |
| `api/business/product/product-supplier-api.ts` | `api/business/scm/supplier-sku-api.ts` | Copy + Adapt | **SPU 级 → SKU 级**；写语义改为整表替换 `replace` |

### 3.2 W1-derived（C 无对应页面，按已验收 W1 同构形态派生）

| V2 target | W1 模板 | 标记 | 说明 |
|---|---|---|---|
| `views/business/scm/customer/customer-type-list.vue` | `product/category-list.vue` | W1-derived | C **无**客户类型管理页；W1 是树表，客户类型是平铺字典 → 改分页表 + 查询表单 + 排序白名单 |
| `views/business/scm/customer/components/customer-type-form-modal.vue` | `product/components/category-form-modal.vue` | W1-derived | 剪掉层级/排序；`typeCode` 提交前 `trim().toUpperCase()` |
| `views/business/scm/customer/customer-detail.vue` | `product/product-detail.vue` | W1-derived | C **无**客户详情页；账期按三形态条件渲染（`v-if`），不用「—」假装 |
| `views/business/scm/supplier/supplier-detail.vue` | `product/product-detail.vue` | W1-derived | C **无**供应商详情页；主体 `a-descriptions` + 只读快照关联表 |
| `views/business/scm/supplier/components/supplier-sku-editable-table.vue` | `product/components/product-sku-editable-table.vue` | W1-derived | **「默认来源」用 `a-checkbox` 而非 radio** —— R12 允许多条默认，做成单选是凭空发明约束；参考价全程字符串，不做 `Number()` |
| `views/business/scm/customer/customer-form-model.ts` | `product/product-form-model.ts` | W1-derived | 纯函数表单模型（校验 / 默认值 / 提交归一化），可被 `node --test` 直接单测 |
| `views/business/scm/supplier/supplier-form-model.ts` | `product/product-form-model.ts` | W1-derived | 同上；`toReplaceItems` **刻意不校验 `defaultFlag` 基数** |
| `views/business/scm/customer/customer-errors.ts` | `product/product-errors.ts` | W1-derived | 错误码 → 文案映射 |
| `views/business/scm/supplier/supplier-errors.ts` | `product/product-errors.ts` | W1-derived | 同上 |
| `e2e/scm-customer.spec.ts` | `e2e/scm-product.spec.ts` | W1-derived | 登录（SM4 传输加密）、夹具账号生命周期、CJK 按钮空格处理、请求断言风格照抄 |
| `e2e/scm-supplier.spec.ts` | `e2e/scm-product.spec.ts` | W1-derived | 同上 |
| `v2/tools/w2_e2e_accounts.py` | `v2/tools/w1_e2e_accounts.py` | W1-derived | 前缀 `w2_e2e_`；权限集 = W1 商品域 401–424（造 SKU 夹具）+ W2 客户/供应商域 431–482 |
| `v2/tools/verify_w2_legacy.py` | `v2/tools/verify_w1_legacy.py` | W1-derived | 同一冻结清单；「已应用 migration」清单扩到 V9 |

### 3.3 New（确属新写，已说明为何不能复用）

| V2 target | 标记 | 为什么不能复用 C |
|---|---|---|
| `views/business/scm/supplier/components/supplier-sku-drawer.vue` | New（宿主壳） | C 的供货关系维护是 **SPU 级弹窗**，行标识与写语义（差量 vs 整表替换）都不同。外壳新写，但「加载 → 编辑 → 保存 → 冲突提示」流程与 C 的 `Modal.confirm` 交互一致 |

### 3.4 明确不迁移的 C 资产（范围外，非遗漏）

| C source | 原因 |
|---|---|
| `views/business/customer/customer-goods-visible-list.vue` | 客户可见商品：属商品/价格可见性域，W2 范围外 |
| `views/business/customer/customer-qrcode-list.vue` | 客户二维码：W2 范围外 |
| `views/business/supplier/account-list.vue` | 供应商账号：W2 范围外 |
| `views/business/supplier/manufacturer-list.vue` | 厂商：W2 范围外 |
| `views/business/supplier/product-apply-list.vue` | 供应商商品申请：W2 范围外 |
| `views/business/supplier/statement-list.vue` | 供应商对账：属财务域，W2 范围外 |

### 3.5 绝不复制清单（继续 100% 使用 V2 SmartAdmin v3.31）

`layout` / `login` / `system`（user、role、dept、menu、dict、log）/ `router core` /
permission framework（`directives/privilege.ts`、`plugins/privilege-plugin.ts`、`store/modules/system/user`）/
request framework（`lib/axios`、`api/system`、拦截器）/ SmartAdmin common-base
（`lib`、`utils`、`components/framework`、`components/support`、`theme`、`plugins`）/ system menu seed。

**核验结果**：W2 未新增或替换上述任何文件。

### 3.6 Provenance 统计

| 标记 | 文件数 |
|---|---|
| Copy + Adapt | 13 |
| W1-derived | 13 |
| New | 1 |
| **合计（W2 前端资产）** | **27** |

> 说明：`views/business/scm/**` 共 26 个文件，其中 11 个属 W1 Product（未改动）；
> 本表统计的是 W2 相关的前端资产（含 e2e 与 tools）。

---

## 4 测试与门禁结果（T14）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端单测 / 切片测试 | `mvn test` | **110 / 110 PASS**（17 类，0 失败 0 错误） |
| PG 集成测试 | `mvn test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false` | **86 / 86 PASS**（10 类） |
| ├ W2 域 IT | — | 8 类 / 66 例（含 `SupplierSkuServiceIT` 17 例） |
| └ W1 回归 IT | — | 2 类 / 20 例（`ProductPgIT` 19、`ScmProductMigrationIT` 1） |
| 前端单测 | `node --experimental-strip-types --test test/*.test.mjs` | **17 / 17 PASS** |
| TS 基线棘轮 | `python v2/tools/ts_baseline_ratchet.py check` | **PASS**（baseline 1974 / current 1973 / delta −1 / **SCM 0** / new 0） |
| ESLint | `eslint src`（项目自身 lint 脚本） | **0 error**（3 warning，全在上游基线文件） |
| 生产构建 | `vite build --mode production` | **✓ built in 50.15s** |
| Customer E2E | `playwright test e2e/scm-customer.spec.ts` | **2 / 2 PASS** |
| Supplier E2E | `playwright test e2e/scm-supplier.spec.ts` | **2 / 2 PASS** |
| legacy SHA-256 复验 | `python v2/tools/verify_w2_legacy.py` | **PASS**（900 文件 / HEAD 未变 / V6–V9 未改 / `.gitignore` 仅批准差异） |

### 4.1 门禁执行中发现的两个环境事实（非缺陷，但影响复现方式）

1. **`*IT` 不在 Surefire 默认 includes 内**（默认只跑 `*Test` / `Test*` / `*Tests` / `*TestCase`），
   因此 `mvn test` 只覆盖 110 例单测/切片，PG IT 必须显式 `-Dtest='*IT'` 才会执行。
   仅跑 `mvn test` 会给出「全绿但未验证数据库语义」的假信号。
2. **ESLint 必须按项目自身脚本 `eslint src` 运行**。直接 `eslint .` 会把 `e2e/*.spec.ts` 纳入，
   而 ESLint 配置未对 `e2e/` 启用 TS 解析器，产生 3 条 `Parsing error: Unexpected token Page`。
   这是 W1 既有的配置边界（W1 的 `scm-product.spec.ts` 同样报错），不是 W2 引入的问题。

---

## 5 端到端验证覆盖的业务不变量

### 5.1 Customer（`e2e/scm-customer.spec.ts`）

1. 客户类型 CRUD（新增 → 列表可见 → 客户删除后才可删类型，验证 40938 引用检查）。
2. 新建客户：账期「按时间 / 1 / 月 / 固定结算日 15」→ **回详情接口**断言
   `creditPeriodType='BY_TIME'`、`creditPeriodValue=1`、`creditPeriodUnit='MONTH'`、`settleDay=15`。
3. 授信额度 4 位定点字符串：`creditLimit` 精确等于 `'1234.5000'`，编辑后等于 `'9999.0000'`（未被浮点化）。
4. 新建客户状态固定 `POTENTIAL`；状态变更只经 `updateStatus`（列表内下拉四态）。
5. 关键字查询（编码 / 名称 / 联系人 / 电话四字段）。
6. 深链详情：URL 带 `customerId`，`page.reload()` 后仍可渲染（独立隐藏路由）。
7. 只读角色：`/scm/customer/delete` 返回 **30005**，且「新增客户」按钮不渲染。

### 5.2 Supplier（`e2e/scm-supplier.spec.ts`）

1. 先用商品接口造「上架 SPU + 2 个上架 SKU」（`supplier_sku` 只接受 SPU 与 SKU 同时上架，否则 40942）。
2. **R12 正面验证**：关联两个 SKU 且**都勾「默认来源」**→ 接口回查 `defaultFlag=true` 的行数为 2。
3. 快照冻结：`skuNameSnapshot` 取 SPU 名称、`skuCodeSnapshot` 取 SKU 编码。
4. 列表「关联商品数」回显 2。
5. 深链详情：只读快照表渲染两个 SKU。
6. 编辑供应商 + 状态停用 / 启用（走独立端点）。
7. **R11 正面验证**：删光两行后保存 → 接口回查关联为 **0 条**（空数组 = 清空，不是无操作）。
8. 只读角色：`/scm/supplier/delete` 返回 **30005**，且「新增供应商」按钮不渲染。

### 5.3 数据洁净度

E2E 结束后核对开发库活动残留，全部为 0：

```
customer_type_active = 0    customer_active = 0     supplier_active = 0
supplier_sku_active = 0     product_spu_active = 0  employee_active = 0
role_active = 0
```

---

## 6 冻结基线与已应用 migration 复验

```
files            : 900
head             : 95a54233586aa3c652e55cd833bb346d51f5a952   （与 W1 冻结 HEAD 一致）
approved diff    : .gitignore: .workbuddy-ai + v2/.runtime
applied checked  : V6, V7, V8, V9
failures         : []
pass             : true
```

`.gitignore` 当前 SHA-256 = `7a01b8ea6126557744f5e01696417de1d51944ab89557adf489534d05d0f2f3a`，
与 W1 批准基线逐字节一致。W2 新增 `v2/docs/architecture/w2-applied-migrations.sha256`
把「已应用即不可回改」的保护范围从 V7 扩到 V9。

---

## 7 本波次发现并修复的缺陷（2 项）

### 7.1 `supplier_sku.is_default` 读路径静默丢值（**已修复**）

- **现象**：Supplier E2E 中，两条关联行都勾了「默认来源」，`replace` 写入成功，
  但 `GET /scm/supplier/sku/list/{supplierId}` 返回的 `defaultFlag` 全为 `false`。
- **根因**：`SupplierSkuDao.xml` 的 `supplierSkuMap` 依赖下划线→驼峰自动映射。
  实体属性名是 `defaultFlag`（配 `@TableField("is_default")`），而自动映射会把 `is_default`
  折成 `isDefault` —— 属性不存在，MyBatis 静默丢弃，不报错。
  `customer` / `supplier` / `customer_type` 走 `resultType` 且列名无 `is_` 前缀，因此不受影响；
  只有 `supplier_sku` 同时具备「自定义 resultMap」+「`is_` 前缀列」两个条件。
- **修复**：在 `supplierSkuMap` 显式加 `<result column="is_default" property="defaultFlag"/>`。
- **为什么原 IT 没抓到**：`SupplierSkuServiceIT.multipleDefaultsAreAllowed` 当时只用裸
  `jdbc.queryForObject` 查 `is_default = TRUE` 的行数，**没有走服务读路径**。
  已补齐断言：改为同时通过 `skuService.listBySupplierId` 校验 `getDefaultFlag()` 与 `getSkuId()`。
- **验证**：`SupplierSkuServiceIT` 17/17 通过；Supplier E2E 由失败转为 2/2 通过。

### 7.2 E2E 清理链路缺口（**已修复**）

- **现象**：Supplier E2E 中途失败时，`afterAll` 直接调 `/scm/supplier/delete` 会返回
  **40947（该供应商已被商品关联引用，不能删除）**，导致每次失败都留 1 条供应商 + 2 条 `supplier_sku` 脏数据。
- **修复**：`afterAll` 先调 `/scm/supplier/sku/replace` 传空数组清空关联（R11 语义），
  再重新拉详情取 version 后删除供应商。
- **验证**：连续两轮 E2E 后，活动残留全部为 0（见 §5.3）。

---

## 8 已知限制与遗留项（不阻塞 Go）

1. **`SkuSelect` 选项来源受 SPU 分页上限约束**（已知 W2 限制）：
   它通过 `productApi.query({ pageSize: 500, status:'ON_SHELF', skuStatus:'ON_SHELF' })`
   拍平各 SPU 的 `skuList`，因此**超过 500 个上架 SPU 时选项不完整**。
   正确解法是 W1 侧提供 SKU 级 option 端点，但 W1 已验收冻结，留到后续波次处理。
2. **前端 `vue-tsc` 全量基线债**：全量约 1974 项错误全部位于上游 SmartAdmin 基线，
   SCM 路径 0 错误。棘轮门禁已锁死该基线，不因 W2 变差（本次 delta −1，另有 1 项上游错误被顺带修掉）。
3. **`e2e/` 不在 ESLint 的 TS 解析范围内**（见 §4.1 第 2 点）：W1 既有配置边界，建议后续单独立项。

---

## 9 Go / No-Go

**结论：Go。**

判定依据：

| 判定项 | 要求 | 实际 | 结论 |
|---|---|---|---|
| 后端单测全绿 | 必须 | 110/110 | ✅ |
| PG IT 全绿 | 必须 | 86/86（含 W1 回归 20 例） | ✅ |
| Web 测试全绿 | 必须 | 17/17 | ✅ |
| `ts_baseline_ratchet check` | PASS | PASS | ✅ |
| SCM TS error | = 0 | 0 | ✅ |
| vite build / eslint | PASS | build ✓ 50.15s；eslint 0 error | ✅ |
| Customer Playwright | PASS | 2/2 | ✅ |
| Supplier Playwright | PASS | 2/2 | ✅ |
| legacy SHA-256 | PASS | 900 文件无变化 | ✅ |

附加条件（已满足）：本波次发现的 2 项缺陷均已修复并回归验证，修复过程未触碰
`module/scm/product/**`、V6/V7、legacy 或 reference 目录。

**不进入 W3。** 后续波次的启动需新的显式指令。

---

## 10 证据文件

| 文件 | 内容 |
|---|---|
| `v2/.runtime/t14-backend-test.log` | `mvn test` 全量输出（110 例） |
| `v2/.runtime/t14-backend-it.log` | `-Dtest='*IT'` 全量输出（86 例） |
| `v2/.runtime/w2-legacy-recheck.json` | legacy 冻结复验结果（900 文件） |
| `v2/.runtime/w2-customer-detail.png` | 客户详情页截图（E2E 产出） |
| `v2/.runtime/w2-supplier-detail.png` | 供应商详情页截图（E2E 产出） |
| `v2/docs/architecture/w2-applied-migrations.sha256` | V6–V9 应用时哈希基线 |
| `v2/docs/architecture/2026-09-15-w2-customer-supplier-approval.md` | 批准记录与 Q1–Q14 |
| `v2/docs/architecture/2026-09-15-w2-customer-supplier-target-design.md` | Target Design |
| `v2/docs/architecture/2026-09-15-w2-customer-supplier-legacy-audit.md` | Legacy 审计 |
