# SmartAdmin V2 迁移 · Repository Audit 报告

> 日期：2026-09-14
> 范围：`xsy-scm` 全仓库 + `project-reference-examples/xsy-scm`
> 性质：**只读审计**。本报告不包含任何迁移代码，未修改任何 legacy 文件。
> 结论状态：**待确认**（第 11 节列出需人工拍板的 9 项决策）

---

## 0. 执行摘要

### 0.1 一句话结论

迁移在技术上可行，且比预期**更干净**：旧 `auth` + `system` 共 **99 个 Java 类 / 15 张表** 可以被整体丢弃，因为它们与供应链业务几乎没有耦合（实测只有 **2 个业务 Java 文件** import 了 `auth` 包）。真正的成本不在「拆旧系统」，而在 **SmartAdmin 基线只提供 MySQL 脚本，而项目要求保持 PostgreSQL** —— 需要自建一套 PG 的系统表 DDL 与 Flyway 基线。

### 0.2 三个必须先解决的前置问题

| # | 问题 | 严重度 | 说明 |
|---|---|---|---|
| P1 | **SmartAdmin 官方仓库不含 PostgreSQL 脚本** | 🔴 高 | `数据库SQL脚本/` 只有 `mysql/`。README 声称支持 PG，但 PG/达梦/金仓等方言脚本标注为**收费购买**。必须自行把 44 张系统表从 MySQL 转写为 PG，并改造 `PaginationInnerInterceptor(DbType.MYSQL)` 硬编码。 |
| P2 | **文档规则与新决策直接冲突** | 🔴 高 | `SMARTADMIN_REFERENCE_RULES.md` 第 8 行、`AGENTS.md` 第 6 行明文**禁止**「将 React 前端替换为 Vue」。`docs/architecture/xsy-vs-smartadmin-conventions.md` §4 明确**不采用 Sa-Token**、保留 Spring Security。这 3 份文档不改，后续任何 agent 都会自相矛盾。 |
| P3 | **金额序列化语义冲突** | 🔴 高 | SmartAdmin 全局注册 `BigDecimalNullZeroSerializer`（null → 0）。这与 Sprint 5 价格中心规格「**缺价必须返回 `UNPRICED` 而非 0 元**」直接冲突，会把「无价」静默变成「0 元」。必须为 SCM 模块排除该序列化器。 |

### 0.3 基线数字

| 项 | 旧 xsy-scm | SmartAdmin (v3.31) |
|---|---|---|
| 语言/框架 | Java 21 + Spring Boot + Spring Security | Java 17 + Spring Boot 3.5.4 + Sa-Token 1.44.0 |
| 持久化 | MyBatis-Plus + Flyway + PostgreSQL | MyBatis-Plus 3.5.12 + 手工 SQL 脚本 + MySQL |
| 管理后台 | React 19 + ProComponents | Vue 3.4.27 + ant-design-vue 4.2.5 |
| 小程序 | Taro 4.2.1 + React 18 | uni-app (Vue3) + uni-ui |
| 数据库表 | 61 张（46 业务 + 13 sys_* + 2 session） | 44 张（t_* 系统表） |
| 迁移脚本 | Flyway V1–V36（缺 V24） | 无迁移工具 |
| 多租户 | 无 | 无（全仓 `tenant` 0 命中）✅ 与「不引入 SaaS」一致 |
| 外键 | 无 | 无 ✅ 与项目规则一致 |

---

## 1. 审计范围与方法

### 1.1 已阅读的输入

- 本迁移说明（用户 query）
- `AGENTS.md`（52KB，全文）、`CLAUDE.md`、`CONTEXT.md`、`README.md`
- `SMARTADMIN_REFERENCE_RULES.md`（已确认其核心禁令作废）
- `docs/` 全部 40 个 md + 5 张 UI 图（分层审计）
- `xsy-scm-server/src/main/**`（Java + Flyway + mapper XML 全量）
- `xsy-scm-web/src/**`（页面/字段/交互提取）
- `xsy-scm-miniapp/**`（技术栈确认）
- `project-reference-examples/xsy-scm/**`（后端 sa-base/sa-admin + `xsy-scm-web` + `xsy-app` + SQL 脚本）

### 1.2 方法

- 六个独立子审计并行执行：旧业务后端 / 旧系统与迁移 / SmartAdmin 后端 / SmartAdmin 前端 / 旧 React 页面 / docs 规格。
- 关键结论均以**真实文件路径、真实类名、真实表名**为证据，报告正文中标注。
- 对存疑项（如统一响应成功码）做了二次代码复核。

### 1.3 已复核的关键事实（避免误判）

| 事实 | 复核结果 |
|---|---|
| SmartAdmin 成功码 | `ResponseDTO.OK_CODE = 0`；前端 `axios.ts:78` 判定 `if (res.code && res.code !== 1)` → **`code=0` 被当作成功**。旧 `ApiResponse(code=0)` 与 SmartAdmin Vue 前端**兼容** ✅ |
| 响应体字段差异 | 旧 `{code,message,data}` vs 新 `{code,level,msg,ok,data,dataType}` → 字段名 `message`/`msg` 不一致，前端已读 `res.msg` |
| SmartAdmin 多租户 | 全仓 `tenant` 关键字 0 命中 → 默认**不带** SaaS ✅ |
| SmartAdmin 数据权限 | `@DataScope` 注解在全仓**0 处使用**，`DataScopeTypeEnum` 仅 1 项（NOTICE） |
| SmartAdmin 操作日志 | `@OperateLog` 仅 3 处使用 |
| 旧业务 → 旧 auth 耦合 | 仅 `customer/service/CustomerTypePriceService.java`、`marketing/controller/MarketingController.java` 两个文件 |
| 旧 `sys_dictionary` | 有表、无任何 Java 类引用（死表） |
| 旧 `sys_config` | **被采购模块读取**（`mapper/purchase/PurchaseReceivingConfigMapper.xml`）→ 不可直接丢弃 |
| 旧 `xsy-device-agent` | **不存在**（AGENTS.md 中为规划项） |

---

## 2. A. 旧 xsy-scm 现状

### 2.1 后端模块清单（`com.xianshuyuan.scm`）

| 模块 | Controller | Service | Entity | Mapper | Mapper XML | DTO | VO | 其他 |
|---|---|---|---|---|---|---|---|---|
| `product` | 2 | 6 | 5 | 3 | 3 | 5 | 4 | converter 1 |
| `customer` | 5 | 18 | 10 | 9 | 5 | 12 | 8 | converter 1, row 1 |
| `supplier` | 2 | 4 | 3 | 3 | 2 | 4 | 3 | converter 1 |
| `order` | 3 | 17 | 13 | 7 | 7 | 12 | 5 | converter 1 |
| `purchase` | 3 | 8 | 17 | 12 | 12 | 11 | 5 | — |
| `inventory` | 1 | 2 | 3 | 2 | 2 | — | — | — |
| `mall` | 6 | 9 | 5 | 7 | 6 | 8 | 15 | **security 6**, row 2 |
| `marketing` | 1 | 2 | 13 | 6 | 6 | 7 | 7 | row 3 |
| `common` | — | — | — | — | — | — | — | 10 个类 |
| `auth`（废弃） | 1 | 4 | — | — | — | 3 | — | security 11, validation 2 |
| `system`（废弃） | 9 | 9 | 5 | 9 | 9 | 39 | — | converter 5 |

### 2.2 数据库表现状（61 张）

**SCM 业务表（46 张）**

| 来源迁移 | 表 |
|---|---|
| V1 | `supplier`、`warehouse`、`product_category`、`product_spu`、`product_sku` |
| V3 | `customer_type`、`customer`、`customer_sku_visibility`、`customer_agreement_price` |
| V4 | `sales_order`、`sales_order_item`、`order_operation_log`、`idempotency_record`（+3 个 sequence） |
| V5 | `order_return`、`order_return_item`、`order_refund` |
| V7 | `customer_agreement_price_operation_log` |
| V8 | `supplier_sku`、`purchase_demand`、`purchase_order`、`purchase_order_item`、`purchase_demand_allocation`、`purchase_operation_log`、`purchase_receipt`、`purchase_receipt_item`、`receipt_weighing_record`、`inventory`、`inventory_movement` |
| V10 | `purchase_receipt_confirmation`、`purchase_receipt_confirmation_item` |
| V27 | `purchase_demand_generation_batch` |
| V29 | `mall_customer_account`、`mall_customer_session`、`mall_customer_address`、`mall_cart_item` |
| V30 | `mall_order_address` |
| V31 | `marketing_promotion`、`marketing_home_section`、`marketing_coupon`、`marketing_customer_coupon`、`marketing_frequent_sku` |
| V32 | `marketing_theme_config` |
| V34 | `customer_type_price`、`customer_price_batch_audit` |
| V35 | `customer_type_price_operation_log` |

**旧 System / Auth 表（13 张，全部 DROP）**

| 表 | 来源 | 处理 |
|---|---|---|
| `sys_user` | V1 + V12 扩展 | DROP（SmartAdmin `t_employee` 替代） |
| `sys_department`、`sys_role`、`sys_permission`、`sys_menu`、`sys_user_role`、`sys_role_permission`、`sys_role_menu` | V12 | DROP |
| `sys_login_log`、`sys_operation_log` | V14 | DROP（用 `t_login_log`/`t_operate_log`） |
| `sys_dictionary`、`sys_dictionary_item` | V14 | DROP（死表，无代码引用） |
| **`sys_config`** | **V27** | ⚠️ **不可直接 DROP**：采购超收容差 `purchase.over_receipt_tolerance_percent`（默认 10）在此表中，被 `PurchaseReceivingConfigMapper.xml` 读取 → 数据需迁入 `t_config` |

**Spring Session 表（2 张，DROP）**：`SPRING_SESSION`、`SPRING_SESSION_ATTRIBUTES`（V13）

### 2.3 Flyway 逐文件分类

| 版本 | 内容 | 分类 | V2 处理 |
|---|---|---|---|
| V1 | `sys_user` + supplier/warehouse/product_category/product_spu/product_sku | **MIXED** | 拆：业务部分保留，`sys_user` 丢弃 |
| V2 | 商品演示数据 | DEMO_SEED | 丢弃（或改造为 V2 种子） |
| V3 | 客户/客户类型/可见性/协议价 | SCM_BUSINESS | 保留 |
| V4 | 销售订单/订单行/操作日志/幂等 | SCM_BUSINESS | 保留 |
| V5 | 退货/退货行/退款 | SCM_BUSINESS | 保留 |
| V6 | 客户演示数据 | DEMO_SEED | 丢弃 |
| V7 | 协议价操作日志 | SCM_BUSINESS | 保留 |
| V8 | 供应商 SKU + 采购全链 + 库存 | SCM_BUSINESS | 保留 |
| V9 | 采购/收货/库存修订 | SCM_BUSINESS | 保留 |
| V10 | 收货确认批次 | SCM_BUSINESS | 保留 |
| V11 | 收货/采购行修订 + 库存追加式触发器 | SCM_BUSINESS | 保留（**触发器必须保留**） |
| V12 | `sys_department/role/permission/menu/*_role/*_grant` + `sys_user` 扩展 | **OLD_SYSTEM_AUTH** | DROP |
| V13 | `SPRING_SESSION*` | Spring Session | DROP |
| V14 | `sys_login_log`、`sys_operation_log`、`sys_dictionary*` | **OLD_SYSTEM_AUTH** | DROP |
| V15–V23 | `sys_user` 唯一索引 + 各类 `system:*` 权限种子 | **OLD_SYSTEM_AUTH** | DROP |
| V25、V26 | `system:*` 权限种子 | **OLD_SYSTEM_AUTH** | DROP |
| V27 | `sys_config` + 采购需求生成批次 + 采购修订 + `purchase:receipt:putaway` 权限 | **MIXED** | 业务保留；`sys_config` 数据迁 `t_config`；权限码重播种 |
| V28 | 收货行修订 | SCM_BUSINESS | 保留 |
| V29 | 商城客户账号/会话/地址/购物车 | SCM_BUSINESS | 保留（**认证部分需重设计**） |
| V30 | 商城订单地址 + `sales_order` 扩展 | SCM_BUSINESS | 保留 |
| V31 | 营销促销/首页/优惠券/常用菜 | SCM_BUSINESS | 保留 |
| V32 | 营销主题配置 + `marketing.read/manage` 权限种子 | **MIXED** | 业务保留；权限码重播种 |
| V33 | 采购行超收约束放开 | SCM_BUSINESS | 保留 |
| V34 | 客户类型价 + 批量调价审计 + `sales_order_item` 扩展 | SCM_BUSINESS | 保留 |
| V35 | 客户类型价操作日志 | SCM_BUSINESS | 保留 |
| V36 | `sales_order` 扩展（MALL 免补单字段） | SCM_BUSINESS | 保留 |

> **汇总**：业务迁移 26 个（含 3 个 MIXED 需拆分）；纯废弃 15 个（V12–V26 区间）；纯种子 2 个（V2、V6）。

### 2.4 真实业务规则（KEEP 依据）

**商品（product）**
- SPU 为聚合根，SKU 为交易单位；每个 SPU ≥1 SKU 且**恰好 1 个默认 SKU**（DB 层部分唯一索引 `uk_product_sku_default_active ON(spu_id) WHERE is_default=TRUE`）。
- 分类三级，`level BETWEEN 1 AND 3`，`parent_id` 与 level 一致性由 CHECK 约束保证。
- SKU 编码/条码/规格组合去重；`normalizeCode` 大写、`normalizeSpecifications` 小写 TreeMap 归一化。
- 编辑采用**差量同步**（`ProductSkuChangeSet`）：保留已存在行的 ID/version，新增插入，移除软删，**禁止 delete-and-recreate**。
- 删除/上下架必须携带 `version`，冲突返回 40921。

**客户与定价（customer）**
- 可见性策略 `ALL_ENABLED` / `ALLOWLIST`（`customer_sku_visibility` 差量同步）。
- 协议价半开区间 `[effectiveFrom, effectiveTo)`，同客户同 SKU **不可重叠**；用 `lockCustomerType` 悲观锁 + `countOverlapping` 校验（40933/40935）。
- **价格解析优先级：`AGREEMENT` > `CUSTOMER_TYPE` > `MARKET`**，由 `CustomerPriceResolver` 统一实现，被 `order` 与 `mall` 共同依赖 —— 这是全系统最关键的单点。
- 批量调价：整批校验、单事务、失败全回滚，写 `customer_price_batch_audit`。
- 价格历史：`PriceHistoryMapper.xml` 合并协议价 + 类型价两类留痕。

**销售订单与售后（order）**
- 状态机：`DRAFT → PENDING → CONFIRMED`；`DRAFT|PENDING → CANCELLED`（必填原因）；`CONFIRMED|CANCELLED` 为编辑终态。
- 提交（submit）时**服务端重新解析价格并锁定**；人工改价置 `priceSource=OVERRIDE` 且原因必填。
- 标品提交时 `actualQuantity = orderedQuantity`；非标品置空，须在 PENDING 阶段录入（>0），否则 confirm 返回 40926。
- 结算额 = 实际数量 × 锁定单价，`HALF_UP` 保留 4 位（`OrderAmountCalculator`）。
- 幂等：`idempotency_record`，scope 形如 `ORDER_CREATE` / `ORDER_SUBMIT:{id}` / `ORDER_ACTUAL:{id}:{itemId}`，同键同内容重放、异内容 409。
- 售后：可退数量 = 原实际数量 − 待审批占用 − 已批准；`order_refund` 一退一退款（唯一索引）；退款仅记录状态，不执行支付与库存变动。
- 单号使用 PG sequence，不用 `MAX+1`。

**采购与收货（purchase）**
- 采购需求由已确认订单行生成，支持半开时间区间汇总、库存抵扣（`calculateInventory`）、稳定排序。
- 采购单：`DRAFT → SUBMITTED → PARTIALLY_RECEIVED → RECEIVED`，另有 `SHORT_CLOSED`；`DRAFT|SUBMITTED → CANCELLED`。
- 一张采购单最多一张活动收货单，收货单可多次增量确认，批次**不可覆盖历史**。
- 超收容差：`maximum = planned × (1 + tol%)`，`tol` 读 `sys_config.purchase.over_receipt_tolerance_percent`（默认 10，范围 0–100），超出抛 40963。
- 标品按数量入库；非标品必须提供 `actualWeight` + `weightSource=MANUAL`，`effectiveQuantity = actualWeight`，且不覆盖计划量。
- 入库模式 `DIRECT`（同事务入库）/ `DEFERRED`（`PENDING_PUTAWAY`，需 `putaway` 权限二次入库）。
- 收货确认、采购进度、库存余额、`PURCHASE_IN` 流水、操作日志、幂等完成 **同事务提交**；锁序固定（采购单 → 行 → 库存）。

**库存（inventory）**
- 粒度 `warehouse_id + sku_id`（部分唯一索引）。
- `inventory_movement` **追加式**：DB 触发器 `trg_inventory_movement_append_only` 禁止 UPDATE/DELETE；CHECK `quantity_after = quantity_before + change`；唯一索引防重复过账。
- 写路径当前仅 `PURCHASE_IN`；`SALES_OUT`/`ADJUSTMENT` 等枚举已定义但未启用。
- 库存对外只读，无人工调整入口。

**商城（mall）**
- **独立认证栈**：`MallSecurityConfig @Order(1)` 拦截 `/api/mall/**`，STATELESS + 自定义 token 头，关 CSRF，authority `MALL_CUSTOMER`；token 仅存 SHA-256 摘要，TTL 默认 168h（`xsy.mall.session.ttl-hours`）。
- 结算价服务端解析；`MallCheckoutFingerprint`（SHA-256）覆盖客户+地址+SKU+数量+服务端价格，防变价（40970）；缺价整单拒绝（40971）。
- 下单**复用** `SalesOrderApplicationService.create` + `submit`，幂等 scope 复用 `ORDER_CREATE`；订单 `order_source=MALL`。
- 微信登录返回 501（未实现）。

**营销（marketing）**
- 促销生效条件：`status=ENABLED` 且 `startAt ≤ now ≤ endAt`。
- 优惠券发放服务端校验 `total - issued` 与 `perLimit`；券码 `CP + UUID`；核销对 `USED`/`EXPIRED` 拒绝。
- 主题白名单：`FRESH_GREEN` / `OCEAN_BLUE` / `WARM_ORANGE`（+ 前端 `MINIMAL_DARK`）。
- **零测试**；促销规则**未参与下单金额计算**，优惠券与订单**无自动核销联动**。

### 2.5 跨模块技术契约（横切）

| 项 | 旧实现 |
|---|---|
| 统一响应 | `common/api/ApiResponse.java` → `{code, message, data}`，成功 `code=0` |
| 分页 | `common/api/PageData.java` → `{records, page, pageSize, total}` |
| 金额/数量 | `BigDecimal`；`common/json/FixedScale4Serializer`（4 位定点字符串，仅 purchase/inventory 实体使用）；其余由 converter 手工 `setScale(4).toPlainString()` |
| 异常 | `common/exception/ErrorCode`（40000/40400/40900/50000 基）+ `BusinessException` + `GlobalExceptionHandler`；错误码按模块分段（product 40010–40921、customer 40430–40935、supplier 40440–40946、order 40420–40926、售后 40450–40954、purchase 40452–50060、mall 40170–50170、marketing 400900–409904） |
| 鉴权 | `auth/security/SecurityConfig.java` **URL 级** `requestMatchers(...).access(AuthorityRules.hasAuthority(...))`；`@EnableMethodSecurity` 开启但**业务模块零 `@PreAuthorize`** |
| 操作人 | 普遍写死 `"SYSTEM"`；仅 `CustomerTypePriceService` 用 `CurrentOperator.username()`、`MarketingController` 用 `AuthenticatedUser` |
| 测试 | 单测 `*Test` + 集成 `*IT`（`@SpringBootTest`，依赖 `XSY_TEST_DB_URL`/`XSY_DB_USERNAME`/`XSY_DB_PASSWORD`）。**order、marketing 无 IT** |

### 2.6 模块成熟度评级

| 模块 | 成熟度 | 说明 |
|---|---|---|
| product | ✅ 完整 | 含分类树、SKU 差量同步、完整单测 + 2 个 IT + Playwright |
| customer | ✅ 完整 | 5 个 Controller、18 个 Service，测试最充分 |
| order | ✅ 完整 | 业务最重，单测充分，但**无 IT** |
| purchase | ✅ 完整 | 含超收/入库/幂等，2 个 IT |
| supplier | ✅ 完整（后端） | 后端完整；**前端仅只读列表** |
| inventory | ✅ 完整（后端） | 写逻辑内聚在采购，接口极简 |
| mall | ⚠️ 半成品 | 工程基础扎实（独立安全链 + IT），微信登录 501、无支付/售后 |
| marketing | ⚠️ 最低 | 后台配置型 CRUD 齐全但**零测试**、未参与金额计算、与订单无联动 |

### 2.7 React 页面提取（仅字段与交互，不迁实现）

**导航结构**（`src/layouts/AdminLayout/navigation.tsx` + `src/router/routeRegistry.tsx`）

双级侧栏，7 个一级组：

| 一级组 | 二级菜单 → 路由 |
|---|---|
| 商品 | 商品档案 `/products`；商品分类 `/products/categories` |
| 订单 | 销售订单 `/orders`；退货申请 `/order-returns`；退款记录 `/order-refunds` |
| 采购 | 采购需求 `/purchases/demands`；采购订单 `/purchases/orders`；收货管理 `/purchases/receipts` |
| 库房 | 供应商 `/warehouses/suppliers`；仓库设置 `/warehouses/settings`；库存余额 `/warehouses/inventories`；库存流水 `/warehouses/inventory-movements` |
| 客户 | 客户档案 `/customers`；客户类型 `/customers/types`；协议价 `/customer-agreement-prices`；客户类型价 `/customer-type-prices`；批量调价 `/customer-type-prices/batch`；价格历史 `/price-history` |
| 商城 | 商城主题 `/shop/theme`；首页装修 `/shop/home-sections` |
| 系统 | 部门/用户/角色/菜单/权限/登录日志/操作日志 |

**关键字段与交互摘要（按模块）**

- **商品**：搜索 = 三级分类 Cascader + 关键词 + 商品状态 + SKU 状态 + 商品类型。表格列 = 商品名称/SPU 编码/分类/默认单位/市场价区间/SKU 数/状态/别名/更新时间，展开行显示 SKU 明细。表单 = 分类*(仅三级)、名称*(≤150)、SPU 编码*(≤64, 转大写)、别名、状态(Radio)、简介(≤1000)；SKU 子表含 默认(Radio 单选)/编码*/规格名称*/规格属性键值对/单位*/市场价*(正则 `^\d+(\.\d{1,4})?$`)/类型/条码/状态/删除（至少留 1 行）。删除默认 SKU 后首行自动成默认。409 → 「数据已被其他人修改，请刷新详情后重试」。
- **客户**：编码*/名称*/类型*(仅启用)/状态/可见范围(Radio)；选 `ALLOWLIST` 时出现「可见 SKU」多选（至少 1 个）。
- **协议价/类型价**：单价*(≤4 位小数)、生效时间*、结束时间（空=长期）；文案「协议价优先于市场价」「零价也是有效价格」。40933 → 「该客户与 SKU 的协议价有效期发生重叠，请调整时间范围」。
- **批量调价**：批次号* + 可编辑行表（≤500 行），整批事务，返回 `rowCount`。
- **订单编辑**：客户*（仅 `editable && items.length===0` 可改，改客户清空明细）、订单类型、补单原因*；明细可改单价 → 自动置 `OVERRIDE` 且改价原因必填。「保存并提交」弹二次确认「提交订单并锁定价格？…提交后不可编辑」，**失败后轮换幂等键**。
- **订单详情**：`ready = 所有明细 actualQuantity>0`；**确认订单按钮 `disabled={!ready}`**；仅 PENDING 且非标品显示「录入实重/修正实重」→ Modal 录入实际数量* + 人工录入原因*。
- **退货**：批准数量**不得超过申请数量**；驳回/取消均需 Modal 必填原因；批准后提示「退款记录已生成」。
- **退款**：完成 Modal 外部凭证（选填、不可重复、≤128）。
- **采购需求**：「按时间段生成」Modal 含仓库*、统计时间段*、计算库存（默认勾选）、**预览**（源订单数/原始需求/库存抵扣/建议采购）；待分配=0 的行复选框禁用。
- **收货确认**：明细列 SKU/商品/历史已收/本次数量/实际重量/**人工修正原因**——后两列**仅非标品显示**；非标品必须有实际重量且 `weightSource:'MANUAL'`。下方「确认历史」表 = 确认批次号/确认数量/操作人/确认时间。
  - ⚠️ `PurchaseReceiptStatusSummary.tsx` 定义了超收/入库模式等展示逻辑，**但未被任何页面引用（死代码）** —— 超收拒绝提示在 UI 层**未实现**。
- **商城主题**：预设主题/编码/主色/强调色/背景/卡片圆角(0–32)/卡片样式/商品卡片密度/导航样式；脏值检测「有未保存的修改」。
- **首页装修**：板块类型（BANNER/FLASH_SALE/NEW_ARRIVAL/CATEGORY/RECOMMEND/CUSTOM）；排序内联即时保存 + 上移/下移。
- **营销促销**：类型与 API 已定义（FLASH_SALE/FULL_REDUCE/FULL_GIFT/TIME_LIMIT），**无管理页面**。

**前端接口约定**（`src/api/http.ts`）
- axios `baseURL=/api`，`withCredentials`，HttpOnly Cookie `XSY_SESSION`，CSRF 头 `X-XSRF-TOKEN`。
- 响应拦截：校验 `code===0` 后**解包** `response.data = data`；非 0 抛 `ApiError(code,message,status)`；401 触发全局登出。
- **金额/数量一律以字符串传输**；数量用 `utils/decimal.ts` 的 BigInt 定点（4 位小数）运算。
- 幂等键统一 `Idempotency-Key` 请求头，仅用于创建/提交/状态流转类写操作。

### 2.8 小程序现状

`xsy-scm-miniapp`：**Taro 4.2.1 + React 18 + Zustand**，微信小程序首发、预留 H5。
页面：`pages/{home,category,cart,login,profile}` + `subpackages/{account,trade}`，另有 `services`/`stores`/`types`/`utils`。
→ 迁移目标应为上游 `xsy-app`（**uni-app Vue3 + uni-ui**，支持微信小程序/H5/App）。

---

## 3. B. SmartAdmin 现状

### 3.1 版本与依赖基线

- 版本 **v3.31**（git `2dbf3c2`），Maven 坐标 `net.lab1024:sa-parent:3.0.0`，子模块 `sa-base` + `sa-admin`。
- Java **17**；Spring Boot **3.5.4**；MyBatis-Plus **3.5.12**；Druid 1.2.25 + p6spy 3.9.1。
- **数据库仅 MySQL**（`mysql-connector-j:9.3.0`，JDBC `jdbc:p6spy:mysql://`）。
- **无 Flyway/Liquibase**（grep 0 命中），纯手工 SQL。
- Redis + Redisson 3.50.0 + Caffeine；fastjson 2.0.57（`RedisService` 序列化）；Jackson（Web 层）。
- Lombok **有**，MapStruct **无**（对象拷贝用自研 `SmartBeanUtil`）。
- springdoc-openapi 2.8.9 + knife4j 4.6.0；Log4j2。
- 认证 **Sa-Token 1.44.0** + `sa-token-redis-jackson`（`spring-security-crypto` 仅用于 Argon2）。
- 配置：`server.port 1024`；`sa-token` token-name `Authorization`、prefix `Bearer`、timeout 30 天、`is-concurrent:false`、`token-style:simple-uuid`、`auto-renew:true`；`file.storage.mode: local`。
- **多租户：完全没有**（`tenant` 0 命中）✅

### 3.2 包结构

```
net.lab1024.sa.base                     # sa-base：框架与通用支撑
├─ common/{annoation,code,constant,controller,domain,enumeration,exception,json,swagger,util,validator}
├─ config/                              # 17 个配置类
├─ constant/                            # CacheKeyConst / RedisKeyConst / LoginDeviceEnum
├─ handler/                             # GlobalExceptionHandler / MybatisPlusFillHandler
└─ module/support/*                     # 24 个子模块（dict/file/job/log/message/codegenerator/...）

net.lab1024.sa.admin                    # sa-admin：业务 + 系统管理
├─ config/ (MvcConfig, OperateLogAspectConfig)
├─ interceptor/AdminInterceptor
├─ module/system/{login,employee,department,position,role,menu,datascope,message,support}
└─ module/business/{category,goods,oa/*}
```

**统一分层**：`controller / service / manager / dao / constant / domain{entity,form,vo,dto}`。

### 3.3 系统能力逐项

| 能力 | 实现要点 |
|---|---|
| **登录认证** | `LoginController`：`POST /login`、`GET /login/getLoginInfo`、`GET /login/logout`、`GET /login/getCaptcha`、`GET /login/sendEmailCode/{loginName}`、`GET /login/getTwoFactorLoginFlag`。`LoginService implements StpInterface`。登录态存 **Redis**，前端 `Authorization: Bearer <uuid>`，loginId 形如 `"2:44"`（`userType:employeeId`）。**非 JWT**。图形验证码 + 可选邮箱双因子。密码 **Argon2id**，盐 = `employee_uid`。失败锁定表 `t_login_fail`，密码历史 `t_password_log`。 |
| **用户上下文** | 接口 `RequestUser`，实现 `RequestEmployee`；ThreadLocal 由 `SmartRequestUtil` 承载；`AdminInterceptor.preHandle` 解析 token → `RequestEmployee` → `SmartRequestUtil.setRequestUser()`。 |
| **权限校验** | 方法级 `@SaCheckPermission("system:role:add")`，超管 `administratorFlag` 跳过。 |
| **员工** | `EmployeeController` / `EmployeeService` / `t_employee` / `EmployeeEntity` |
| **部门** | `DepartmentController`（`/department/treeList` 等）/ `t_department` |
| **职务** | `PositionController` / `t_position` |
| **角色** | `RoleController` / `t_role` + `t_role_employee` + `t_role_menu` |
| **菜单/功能点** | `MenuController`（`/menu/query`、`/menu/tree`、`/menu/auth/url`）/ `t_menu`；`menu_type` 区分菜单与功能点；权限串存 `api_perms`（后端）/ `web_perms`（前端） |
| **数据权限** | `@DataScope` 注解 + `MyBatisPlugin extends DataScopePlugin implements Interceptor` 改写 `BoundSql`；范围 `DataScopeViewTypeEnum`：本人(0)/本部门(1)/本部门及子部门(2)/全部(10)；`t_role_data_scope` 存配置。**⚠️ 全仓 0 处实际使用** |
| **操作日志** | `@OperateLog` 注解 + `OperateLogAspect`，表 `t_operate_log`。**⚠️ 全仓仅 3 处使用** |
| **登录日志** | `t_login_log`（0 成功/1 失败/2 退出） |
| **字典** | `t_dict`（`dict_code` 唯一）+ `t_dict_data`；缓存 `dict_data_cache` |
| **文件** | `FileService` + `IFileStorageService`（local / S3 兼容）；表 `t_file`（`file_key` 唯一、`folder_type`、creator 信息） |
| **统一响应** | `ResponseDTO<T>`：`code`（成功 **0**）/`level`/`msg`/`ok`/`data`/`dataType` |
| **统一异常** | `GlobalExceptionHandler`（`@ControllerAdvice`）；`ErrorCode` 接口 + `UserErrorCode`/`SystemErrorCode`/`UnexpectedErrorCode`；`BusinessException` |
| **分页** | 入参 `PageParam{pageNum, pageSize, searchCount, sortItemList[]}`（`@Max(500)`、`@Size(max=10)`，含 SQL 注入检测）；出参 `PageResult{pageNum, pageSize, total, pages, list, emptyFlag}`；`SmartPageUtil.convert2PageQuery/convert2PageResult` |
| **缓存** | `RedisService` + `RedisKeyConst`；Redisson 分布式锁；Spring Cache |
| **其它** | 定时任务（`t_smart_job*`）、站内消息、公告、序列号（`t_serial_number*`）、心跳、热加载、代码生成器、数据变更追踪、接口加解密、防重复提交（`repeatsubmit`）、数据脱敏、配置中心（`t_config`）、帮助文档、邮件模板、意见反馈、变更日志 |

### 3.4 CRUD 代码范式（以字典为典型）

| 层 | 路径 | 约定 |
|---|---|---|
| Controller | `sa-admin/.../module/system/support/AdminDictController.java` | `@RestController` + `extends SupportBaseController`；`@PostMapping("/dict/queryPage")` + `@SaCheckPermission("support:dict:query")`；返回 `ResponseDTO<PageResult<DictVO>>` |
| Service | `sa-base/.../module/support/dict/service/DictService.java` | `@Service`；返回 `ResponseDTO<...>`；`SmartBeanUtil.copy(form, Entity.class)`；写操作加 `@CacheEvict` |
| Dao | `.../dict/dao/DictDao.java` | `@Mapper @Component interface DictDao extends BaseMapper<DictEntity>` + 自定义分页方法 |
| Entity | `.../domain/entity/DictEntity.java` | `@Data @TableName("t_dict")`，`@TableId(type = IdType.AUTO)` |
| Form | `.../domain/form/{Add,Update,Query}Form.java` | Add/Update 用 `jakarta.validation`；Query `extends PageParam` |
| VO | `.../domain/vo/DictVO.java` | 出参 |
| Mapper XML | `sa-base/src/main/resources/mapper/support/DictMapper.xml` | `namespace` = Dao 全限定名 |

**分页写法**：
```java
Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
List<DictVO> list = dictDao.queryPage(page, queryForm);
return SmartPageUtil.convert2PageResult(page, list);
```

**逻辑删除**：**不使用** MyBatis-Plus `@TableLogic`；表用 `deleted_flag tinyint` 手工维护，SQL 自行 `and deleted_flag = 0`。`RoleService.deleteRole` 为**物理删除**。

**审计字段**：`MybatisPlusFillHandler implements MetaObjectHandler` **仅填** `createTime`/`updateTime`；**无** `createUserId`/`updateUserId` 自动填充；**无 BaseEntity 基类**。

### 3.5 数据库脚本

- `数据库SQL脚本/README.md`：MySQL 免费；**PostgreSQL / SqlServer / Oracle / 达梦 / 金仓 / GaussDB / OceanBase / PolarDB 等均标注收费购买，仓库内不含**。
- `数据库SQL脚本/mysql/smart_admin_v3.sql`（约 181KB，全量 DDL + 初始化数据）
- `数据库SQL脚本/mysql/sql-update-log/`：v3.15.0 / v3.18.0 / v3.21.0 / v3.23.0 / v3.25.0 / v3.26.0 / v3.30.0
- 系统表 **44 张**（`t_*`），含 4 张 OA 示例表（`t_oa_*`）与 2 张商品示例表（`t_category`、`t_goods`）。
- **无任何外键约束**（`FOREIGN KEY`/`REFERENCES` 0 命中）✅

### 3.6 Vue3 前端现状

- `vue 3.4.27` / `vue-router 4.3.2` / `pinia 2.1.7` / `ant-design-vue 4.2.5` / `vite 5.2.12` / `typescript 5.6.3`
- axios 1.6.8、lodash、dayjs、**decimal.js 10.3.1**、echarts、vue-i18n、wangeditor、sortablejs
- **无 vxe-table**，表格统一 `a-table`
- 别名 `/@/*` → `src/*`；开发端口 8081

**目录**：`api/`（base-model + system + support + business）、`components/{business,framework,support,system}`、`config/`、`constants/`、`directives/`、`i18n/`、`layout/`、`lib/`（axios 等）、`plugins/`、`router/`、`store/modules/{model,system}`、`theme/`、`types/`、`utils/`、`views/{business,support,system}`（**125 个 .vue**）

**Layout**：`layout/index.vue` 按 `LAYOUT_ENUM` 切换 `side-layout` / `side-expand-layout` / `top-layout` / `top-expand-layout`；含**多页签** `page-tag/`（default-tab / antd-tab / chrome-tab）；主题用 `<a-config-provider :theme>` + `theme.darkAlgorithm`；Less 变量在 `vite.config.ts` 的 `modifyVars`。

**路由与菜单**：静态路由 `router/routers.ts`；动态路由 `buildRoutes(menuRouterList)` 遍历后端 `menuList`，`name = menuId`、`path = e.path`，组件用 `import.meta.glob('../views/**/**.vue')` 按 `e.component` 映射；守卫 `router.beforeEach` 校验 token。

**权限**：指令 `directives/privilege.ts`（`v-privilege="'support:dict:add'"`），超管 `administratorFlag` 放行；插件 `plugins/privilege-plugin.ts` 提供 `$privilege('...')`；数据源 `store/modules/system/user.ts` 的 `pointsList`（`menuType === POINTS`）。

**CRUD 页面范式**：
- 标准页 `views/support/dict/index.vue`：`a-form.smart-query-form` → `a-card` + `a-row.smart-table-btn-block`（新建 + `TableOperator` 列设置）→ `a-table`（`#bodyCell` 渲染状态与操作列）→ `a-pagination` → 表单弹窗。
  **无 `useTable` 封装**，每页手写 `queryForm(reactive)` / `tableLoading` / `tableData` / `total` / `ajaxQuery()` / `onSearch()` / `resetQuery()`，`onMounted(ajaxQuery)`。
- 弹窗 `views/support/dict/components/dict-form-modal.vue`：`a-modal + a-form(ref+model+rules)`，`defineExpose({ showModal })`，`emit('reloadList')`。
- 树形页 `views/system/department/department-list.vue`：`a-table` + `:defaultExpandedRowKeys` + `:pagination="false"`，本地递归组树；`views/system/menu/menu-list.vue` 用 `SmartEnumSelect` + `$smartEnumPlugin.getDescByValue`。
- API 层：`src/api/support/dict-api.ts` 导出**对象** `dictApi = { queryDict, addDict, ... }`，方法调 `postRequest('/support/dict/queryPage', p)`。
- 请求封装 `src/lib/axios.ts`：`Authorization: 'Bearer ' + token`；`code !== 1` 走 `message.error(res.msg)`（因 `code=0` 为 falsy，实际 0 视为成功）；`30007/30008` 自动登出。
- 上传 `components/support/file-upload/index.vue` → `fileApi.uploadFile(formData, folder)` → `/support/file/upload?folder=`。
- 通用组件：`dict-select`、`dict-label`、`smart-enum-select/radio/checkbox`、`table-operator`、`boolean-select`、`department-tree-select`、`employee-select`、`smart-loading`、`file-preview`。**无独立分页封装**。

**ERP 示例模块（可直接作为 SCM 参考）**：
- 前端 `views/business/erp/{catalog,goods}`：`goods-list.vue` + `components/goods-form-modal.vue` + `catalog/category-tree-table.vue` + `catalog/components/category-form-modal.vue`
- 后端 `module/business/{category,goods}`：完整 `controller/service/manager/dao/domain/{entity,form,vo}/constant` 分层

### 3.7 小程序端（上游 `xsy-app`）

uni-app（Vue3）+ uni-ui，`pages/{home,goods,list,list2,form,message,notice,mine,order-detail,select-people,enterprise,support,login}`，含 `api/`、`store/`、`components/`、`uni_modules/`。

---

## 4. C1 · KEEP（直接保留的 SCM 业务能力）

> 判定原则：**业务规则与不变量保留，承载它的代码按 SmartAdmin 范式重写**（见 C3）。下表为「能力层面」的 KEEP 清单。

| # | 能力 | 旧证据位置 | 保留理由 |
|---|---|---|---|
| K1 | SPU/SKU 聚合 + 不变量（≥1 SKU、恰 1 默认 SKU、规格快照 JSONB） | `product/service/ProductAggregateValidator.java` | 核心领域模型，订单/采购/库存/称重全部以 SKU 为单位 |
| K2 | SKU 差量同步（保留 ID/version，禁止重建） | `product/service/ProductSkuChangeSet.java` | 保证历史单据引用不失效 |
| K3 | 三级商品分类 + parent/level 一致性约束 | `product_category` 表 CHECK | 分类是商品与营销的共同维度 |
| K4 | 客户 / 客户类型 | `customer/service/*` | 业务主数据 |
| K5 | SKU 可见策略 `ALL_ENABLED` / `ALLOWLIST` | `CustomerSkuVisibilityChangeSet`、`OrderableSkuQueryService` | 影响可下单范围 |
| K6 | 客户协议价（半开区间、不可重叠） | `customer/service/AgreementPriceService.java` | 定价核心 |
| K7 | 客户类型价 + 批量调价 + 价格历史 | `CustomerTypePriceService`、`CustomerTypePriceBatchService`、`PriceHistoryMapper.xml` | Sprint 5 价格中心交付物 |
| K8 | **价格解析优先级 `AGREEMENT > CUSTOMER_TYPE > MARKET` + 缺价 `UNPRICED`** | `customer/service/CustomerPriceResolver.java` | 全系统单点，order/mall 共同依赖 |
| K9 | 销售订单状态机 + 显式命令端点 | `order/service/OrderStateTransitionPolicy.java` | 审计与并发要求 |
| K10 | 提交锁价 + 人工改价 `OVERRIDE` + 原因必填 | `SalesOrderApplicationService` | 结算金额可信性 |
| K11 | 标品/非标品 `actualQuantity` 规则 + `HALF_UP` 4 位结算 | `OrderAmountCalculator.java` | 非标品称重结算的领域基石 |
| K12 | 幂等 `idempotency_record` + `Idempotency-Key`（同键同内容重放 / 异内容 409） | `order/service/IdempotencyService.java` | 财务/库存副作用防护 |
| K13 | 退货状态机 + 可退额度（含 pending 占用）+ 一退一退款 | `order/service/AfterSalesRules.java` | 售后合规 |
| K14 | 退款 `PENDING`/`COMPLETED`（仅记录状态） | `order_refund` + `OrderRefundController` | 明确不执行支付，范围受控 |
| K15 | 订单/采购/协议价/类型价操作日志（before/after JSONB） | `order_operation_log`、`purchase_operation_log`、`customer_agreement_price_operation_log`、`customer_type_price_operation_log` | 审计要求 |
| K16 | 采购需求生成（半开时间区间 + 库存抵扣 + 稳定排序） | `purchase/service/PurchaseDemandService.java`、`purchase_demand_generation_batch` | P1 升级交付物 |
| K17 | 采购单状态机（含 `SHORT_CLOSED`） | `purchase/service/PurchaseOrderService.java` | 采购履约主流程 |
| K18 | 收货单增量确认 + 批次不可覆盖 | `purchase_receipt_confirmation(_item)` | 多次收货留痕（负责人已确认口径） |
| K19 | 超收容差（`sys_config` 默认 10%，0–100） | `PurchaseReceivingConfigMapper.xml` | 业务容错口径 |
| K20 | `DIRECT` / `DEFERRED` 入库 + putaway | `purchase/service/PurchaseReceiptApplicationService.java` | 负责人已确认口径 |
| K21 | 库存余额（warehouse+sku）+ `PURCHASE_IN` 追加式流水 + before/change/after | `inventory/service/InventoryApplicationService.java`、`InventoryMovement` | 库存可信性 |
| K22 | 库存追加式 DB 触发器 + 防重复过账唯一索引 | V11 `trg_inventory_movement_append_only` | **DB 层强约束，必须保留** |
| K23 | 收货确认全链同事务（进度+库存+流水+日志+幂等）+ 固定锁序 | `PurchaseReceiptApplicationService` | 一致性核心 |
| K24 | 供应商 / 仓库 / 供应商-SKU（含参考价） | `supplier/service/*` | 采购前置主数据 |
| K25 | 商城：客户账号 / 会话 / 地址 / 购物车 / 目录 / 首页预览 | `mall/service/*` | 一个逻辑商城多端（已确认口径） |
| K26 | 商城结算指纹防变价 + 缺价整单拒绝 | `MallCheckoutFingerprint.java` | 价格一致性 |
| K27 | 商城下单复用销售订单（`order_source=MALL`） | `MallCheckoutService` → `SalesOrderApplicationService` | 单一订单模型 |
| K28 | 营销：促销 / 首页板块 / 优惠券 / 主题配置 / 常用菜 | `marketing/*` | 商城配置能力 |
| K29 | 单号生成使用 PG sequence（非 `MAX+1`） | V4 三个 sequence | 并发安全 |
| K30 | 无数据库外键、软删除 + 部分唯一索引的建模风格 | 全部 SCM 迁移 | 与 AGENTS.md 规则一致 |

---

## 5. C2 · DROP（完全废弃的旧系统能力）

| # | 对象 | 规模 | 废弃依据 |
|---|---|---|---|
| D1 | `com.xianshuyuan.scm.auth.**` | 22 个类 | 新决策：不迁旧 auth |
| D2 | `com.xianshuyuan.scm.system.**` | 77 个类（controller 9 / service 9 / dto 39 / entity 5 / mapper 9 / converter 5） | 新决策：不迁旧 system |
| D3 | `sys_user`、`sys_department`、`sys_role`、`sys_permission`、`sys_menu`、`sys_user_role`、`sys_role_permission`、`sys_role_menu` | 8 张表（V12） | 由 `t_employee`/`t_department`/`t_role`/`t_menu`/`t_role_employee`/`t_role_menu` 替代 |
| D4 | `sys_login_log`、`sys_operation_log` | 2 张表（V14） | 由 `t_login_log`/`t_operate_log` 替代 |
| D5 | `sys_dictionary`、`sys_dictionary_item` | 2 张表（V14） | **死表**：无任何 Java 类引用 |
| D6 | `SPRING_SESSION`、`SPRING_SESSION_ATTRIBUTES` | 2 张表（V13） | 明确要求不套用旧 Spring Session JDBC |
| D7 | Flyway `V12`–`V23`、`V25`、`V26` | 15 个迁移 | 纯 system/RBAC/session |
| D8 | `xsy-scm-web`（React 管理后台） | 整个目录 | 停止继续开发，**仅作业务/UI 参考** |
| D9 | `xsy-scm-miniapp` 的 Taro + React 实现 | 整个目录 | 统一迁 Vue 技术栈 |
| D10 | `common/api/ApiResponse`、`PageData`、`common/exception/ErrorCode`、`BusinessException`、`GlobalExceptionHandler` | 5 个类 | 由 `ResponseDTO` / `PageResult` / SmartAdmin `ErrorCode` + `GlobalExceptionHandler` 替代 |
| D11 | `auth/security/SecurityConfig.java` 的 URL 权限路由表 | 1 个类（含全部 SCM 路由映射） | 由 `@SaCheckPermission` 替代（**但路由→权限的语义需先提取，见 R9**） |
| D12 | Spring Security + Cookie `XSY_SESSION` + CSRF `XSRF-TOKEN` + `SpaCsrfTokenRequestHandler` | auth/security 内 | 由 Sa-Token + Bearer + Redis 替代 |
| D13 | 前端 React 技术栈：ProComponents / TanStack Query / Zustand / react-hook-form / zod / react-router | 全前端 | 统一 Vue3 + Pinia + ant-design-vue + a-form rules |
| D14 | `SMARTADMIN_REFERENCE_RULES.md` 第 8 行「禁止将 React 替换为 Vue」条款 | 1 条 | **与新决策直接冲突，必须废止** |
| D15 | `AGENTS.md` 第 6 行同义禁令 | 1 条 | 同上 |
| D16 | `docs/architecture/xsy-vs-smartadmin-conventions.md` §4（保留 Spring Security、不采用 Sa-Token） | 1 节 | 与新决策冲突 |
| D17 | `docs/design/xsy-scm-UIUX设计与前端开发指导-v1.1.md` §11/§22/§25–§30（AntD/ProComponents/React Skill 假设） | 若干节 | Vue 栈下失效 |
| D18 | `docs/superpowers/plans/*`（10 份实施计划） | 10 文件 | 已完成/被取代，仅历史参考 |
| D19 | SmartAdmin 示例表 `t_category`、`t_goods`、`t_oa_*` | 6 张表 | 演示数据，V2 基线不保留 |

### ⚠️ 5.1 DROP 前必须抢救的 3 处隐性业务资产

| 资产 | 位置 | 抢救方式 |
|---|---|---|
| **采购超收容差配置值** | `sys_config` 表 `purchase.over_receipt_tolerance_percent`（V27 播种，默认 10） | 迁入 SmartAdmin `t_config`（配置中心）或 SCM 专用配置表；**不可随 V27 一起丢** |
| **业务权限码** | `sys_permission` 中 `marketing.read` / `marketing.manage` / `purchase:receipt:putaway`（V27、V32 播种），并已授权给角色 | 按 C6 映射为 `scm:*` 权限码，重新播种到 `t_menu` |
| **SCM 路由 → 权限映射表** | `auth/security/SecurityConfig.java` | 提取为 C6 的 PERMISSION MAPPING，再删除该类 |

---

## 6. C3 · REWRITE（按 SmartAdmin 方式重新落地的 SCM 代码）

### 6.1 统一改写规则（适用于全部 8 个业务模块）

| # | 旧形态 | V2 形态 | 影响面 |
|---|---|---|---|
| W1 | `ApiResponse<T>{code,message,data}` | `ResponseDTO<T>{code,level,msg,ok,data,dataType}` | 全部 Controller |
| W2 | `PageData<T>{records,page,pageSize,total}` | `PageResult<T>{pageNum,pageSize,total,pages,list,emptyFlag}` + 入参 `PageParam` | 全部分页查询 |
| W3 | `@RestController` 返回裸对象 + 手写包装 | 返回 `ResponseDTO.ok(...)` / `ResponseDTO.error(ErrorCode)` | 全部 Controller |
| W4 | `common/exception/ErrorCode` 常量类 | 实现 SmartAdmin `ErrorCode` 接口的枚举/类，保留旧错误码数值语义 | 8 个 `*ErrorCodes` |
| W5 | `GlobalExceptionHandler` 中硬编码供应商唯一索引名映射 | 迁移到 SCM 模块级异常处理器或扩展 SmartAdmin 处理器 | `common/exception/GlobalExceptionHandler.java` |
| W6 | 操作人写死 `"SYSTEM"` | `SmartRequestUtil.getRequestUser().getUserId()/getUserName()` | 全部写操作 + 操作日志 |
| W7 | `auth.security.CurrentOperator.username()` | `SmartRequestUtil` / `RequestEmployee` | `CustomerTypePriceService` |
| W8 | URL 级权限（`SecurityConfig` 路由表） | 方法级 `@SaCheckPermission("scm:xxx:yyy")` | 全部 Controller |
| W9 | 领域包结构 `controller/service/entity/mapper/dto/vo/converter` | SmartAdmin 结构 `controller/service/manager/dao/constant/domain/{entity,form,vo,dto}` | 全部模块 |
| W10 | Mapper 接口 `XxxMapper` + `resources/mapper/<domain>/*.xml` | `XxxDao extends BaseMapper` + `resources/mapper/scm/<domain>/*.xml` | 全部 62 个 XML |
| W11 | 分页 `MybatisPlusConfig` 无方言/或用 PG 方言 | `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` | 全局（**SmartAdmin 现为 MYSQL，必须改**） |
| W12 | 逻辑删除 `@TableLogic deleted BOOLEAN` + 部分唯一索引 | **保留**（SmartAdmin 无全局 logic-delete 配置，不冲突；但需显式声明，不依赖全局） | 全部业务表 |
| W13 | 乐观锁 `@Version version` | **保留** | 全部可变业务表 |
| W14 | 审计字段 `created_by`/`updated_by` 字符串 | 保留列，但改由业务层显式写入当前用户名；`create_time`/`update_time` 交给 `MybatisPlusFillHandler` | 全部业务表 |
| W15 | 金额/数量 4 位小数字符串（`FixedScale4Serializer` + converter 手工 toPlainString） | 保留 4 位字符串契约；**排除 SmartAdmin 的 `BigDecimalNullZeroSerializer`**；统一一个 SCM 的 `FixedScale4Serializer` 并全局注册 | 全部金额字段 |
| W16 | 幂等 `idempotency_record` + `Idempotency-Key` | **保留**，不替换为 SmartAdmin `repeatsubmit` | order/purchase/mall |
| W17 | 单号 PG sequence | 保留；或评估改用 `t_serial_number`（见 R10） | order/purchase |
| W18 | `converter/*` 手工映射 | SmartAdmin 用 `SmartBeanUtil`；SCM 复杂映射保留显式 converter（**不引入 MapStruct**，因 SmartAdmin 基线无 MapStruct，避免双重范式） | 全部 |
| W19 | mall 独立 `SecurityFilterChain` + 自有 token 表 | **需重设计**（见 R8）：或接入 Sa-Token 的 `userType`，或保留独立过滤器但去掉 Spring Security 依赖 | mall 6 个 security 类 |
| W20 | 前端 React 页面 | Vue3 页面（见 C7） | 全部页面 |

### 6.2 模块级 REWRITE 工作量评估

| 模块 | 后端重写量 | 前端重写量 | 备注 |
|---|---|---|---|
| product | 中（22 类） | 中（2 页 + 2 弹窗） | **试点模块** |
| customer | 大（54 类） | 大（6 页） | `CustomerPriceResolver` 是全局单点，必须最先稳定 |
| supplier | 小（12 类） | 小（2 页，且需补齐 CRUD） | 前端原本只有只读列表 |
| order | 大（45 类） | 大（8 页，含编辑器/详情/售后） | 业务最重，交互最复杂 |
| purchase | 大（43 类） | 大（5 页 + 收货确认页） | 需补超收 UI（原为死代码） |
| inventory | 小（6 类） | 小（2 页只读） | 写逻辑在 purchase 内 |
| mall | 中（30 类 + 6 security） | 中（3 页配置 + 预览页） | 认证栈需重设计 |
| marketing | 中（24 类） | 小（2 页 + 需新增促销页） | 零测试，需补测试 |

---

## 7. C4 · DATABASE MAPPING

### 7.1 总体策略

1. **SmartAdmin 系统表与 SCM 业务表并存**，前缀区分：`t_*` = SmartAdmin 系统，无前缀 snake_case = SCM 业务。**实测无表名冲突**（SmartAdmin 的 `t_category`/`t_goods` 是演示表，V2 不保留）。
2. **V2 使用全新的数据库 + 全新的 Flyway 版本序列（从 V1 重新开始）**。旧 V1–V36 不复制，按语义重新组织为 V2 基线。
3. **方言转换是本次最大工作量**（见 7.4）。

### 7.2 V2 Flyway 基线设计（建议）

| 版本 | 内容 |
|---|---|
| `V1__sa_system_baseline.sql` | SmartAdmin 系统表 PG 版（去掉 `t_category`/`t_goods`/`t_oa_*` 演示表），约 38 张 |
| `V2__sa_system_seed.sql` | 部门/职务/超管员工/角色/菜单/功能点/字典/配置 初始数据（含 SCM 菜单与 `scm:*` 权限码） |
| `V3__scm_product.sql` | `product_category`、`product_spu`、`product_sku`（源：旧 V1） |
| `V4__scm_product_seed.sql` | 演示/基础数据（可选） |
| `V5__scm_customer_pricing.sql` | `customer_type`、`customer`、`customer_sku_visibility`、`customer_agreement_price`、`customer_agreement_price_operation_log`、`customer_type_price`、`customer_price_batch_audit`、`customer_type_price_operation_log`（源：V3+V7+V34+V35） |
| `V6__scm_supplier_warehouse.sql` | `supplier`、`warehouse`、`supplier_sku`（源：V1+V8） |
| `V7__scm_sales_order.sql` | `sales_order`、`sales_order_item`、`order_operation_log`、`idempotency_record`、`order_return`、`order_return_item`、`order_refund` + sequences（源：V4+V5+V30+V34+V36） |
| `V8__scm_purchase_receiving.sql` | 采购与收货 12 张表（源：V8+V9+V10+V27+V28+V33，**含 V11 的库存触发器**） |
| `V9__scm_inventory.sql` | `inventory`、`inventory_movement` + append-only 触发器 + 防重唯一索引（源：V8+V11） |
| `V10__scm_mall.sql` | `mall_customer_account`、`mall_customer_session`、`mall_customer_address`、`mall_cart_item`、`mall_order_address`（源：V29+V30） |
| `V11__scm_marketing.sql` | 6 张 `marketing_*`（源：V31+V32） |

> 拆分原则：**按领域边界组织，不按旧版本号机械搬运**。跨模块扩展列（如 `sales_order_item.price_source` 支持 `CUSTOMER_TYPE`）落在所属领域的最新版本中。

### 7.3 列/类型映射

| 概念 | 旧 SCM | SmartAdmin | **V2 决定** |
|---|---|---|---|
| 主键 | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | `AUTO_INCREMENT` | **保留 PG identity**；实体 `@TableId(type = IdType.AUTO)` 兼容 |
| 逻辑删除 | `deleted BOOLEAN` + 部分唯一索引 `WHERE deleted = FALSE` | `deleted_flag tinyint` 手工 | **SCM 表保留旧式**（部分唯一索引是实现「编码删后可复用」的关键，SmartAdmin 无等价物） |
| 乐观锁 | `version INTEGER` + `@Version` | 无 | **SCM 保留** |
| 审计时间 | `created_at`/`updated_at TIMESTAMPTZ` | `create_time`/`update_time datetime` | **SCM 保留旧列名**；由 `MybatisPlusFillHandler` 填充 |
| 审计人 | `created_by`/`updated_by VARCHAR(64)` | 无自动填充 | **SCM 保留列**，业务层显式写入 |
| 金额/数量 | `NUMERIC(18,4)` | `decimal(10,2)` | **`NUMERIC(18,4)`** |
| 快照 | `JSONB`（`spec_values`） | `text` | **`JSONB`**（需 `JsonbStringMapTypeHandler`） |
| 布尔 | `BOOLEAN` | `tinyint(1)` | **`BOOLEAN`** |
| 枚举 | `VARCHAR(16)` + CHECK | `tinyint` + Java enum | **`VARCHAR(16)` + CHECK**（可读性与迁移友好） |
| 外键 | 无 | 无 | **无** ✅ |

### 7.4 MySQL → PostgreSQL 转换清单（**关键路径**）

| # | MySQL 特性 | 出现位置 | PG 处理 |
|---|---|---|---|
| C1 | `` `反引号` ``、`select database()`、`information_schema.tables`、`INSTR()` | `sa-base/src/main/resources/mapper/support/CodeGeneratorMapper.xml`（第 22–29 行）**唯一强耦合 XML** | 重写为 `information_schema` + `current_schema()`；或 V2 不启用代码生成器 |
| C2 | `PaginationInnerInterceptor(DbType.MYSQL)` 硬编码 | `MybatisPlusConfig` | **必须改 `DbType.POSTGRE_SQL`**，否则分页 SQL 全错 |
| C3 | `LIMIT n` | `MenuMapper.xml`(22,68)、`RoleEmployeeMapper.xml`(146)、`CategoryMapper.xml`(54)、`LoginLogMapper.xml`(41)、`PasswordLogMapper.xml`(14) | PG 兼容 `LIMIT`，但需回归验证 |
| C4 | `ON DUPLICATE KEY UPDATE` | **0 命中** | 无需处理 ✅ |
| C5 | `tinyint` / `tinyint(1)` / `unsigned` | 全表（`deleted_flag`、`disabled_flag`、`gender`、`administrator_flag`、`success_flag`…） | → `SMALLINT` 或 `BOOLEAN`；实体用 `Boolean`，验证 TypeHandler |
| C6 | `AUTO_INCREMENT` + `@TableId(IdType.AUTO)` | 全部 44 张表 | → `GENERATED BY DEFAULT AS IDENTITY`；验证 `useGeneratedKeys` 回填主键 |
| C7 | `datetime ... ON UPDATE CURRENT_TIMESTAMP` | 大量使用 | PG 无此语法 → 依赖 `MybatisPlusFillHandler`（当前仅填时间，可覆盖） |
| C8 | `utf8mb4` / `utf8mb4_general_ci` / `ENGINE=InnoDB` / `USING BTREE` / `ROW_FORMAT=Dynamic` / 显示宽度 `int(0)` | 全库 | 全部删除；PG 默认 B-tree |
| C9 | `decimal(10,2) UNSIGNED` | `t_goods.price`（该表 V2 不保留） | 去掉 UNSIGNED |
| C10 | 原生 `json` 列 / `json_extract` | **0 命中**（`t_table_column.columns` 是 `text`） | 无需处理 ✅ |
| C11 | `SET FOREIGN_KEY_CHECKS` | 脚本尾部 | 删除 |
| C12 | Druid + p6spy JDBC URL | `application.yaml` | 改为 `jdbc:p6spy:postgresql://`；验证 Druid PG 兼容 |

### 7.5 需迁移的数据（非 DDL）

| 数据 | 来源 | 去向 |
|---|---|---|
| 采购超收容差 | `sys_config.purchase.over_receipt_tolerance_percent` | `t_config`（键值）或 SCM 配置表 |
| 业务权限码 + 角色授权 | `sys_permission` / `sys_role_permission`（V27、V32） | `t_menu`（`api_perms`/`web_perms`）+ `t_role_menu` |
| 部门/员工/角色/菜单基础数据 | `sys_department`/`sys_user`/`sys_role`/`sys_menu` | 不迁移（重新初始化），仅**组织架构**如需保留则做一次性导入 |

---

## 8. C5 · API MAPPING

### 8.1 路径与风格决策

| 项 | 旧 | V2 建议 |
|---|---|---|
| 前缀 | `/api` | 采用 SmartAdmin 风格**无统一前缀**（context-path `/`），由前端 `VITE_APP_API_URL` + 代理控制；SCM 统一加业务前缀 `/scm` |
| 动作命名 | RESTful（`POST /api/products`、`PUT /{id}`） | SmartAdmin 风格 `/scm/product/queryPage`、`/scm/product/add`、`/scm/product/update`、`/scm/product/delete/{id}`、`/scm/product/detail/{id}` |
| 命令端点 | `/api/orders/{id}/submit` | `/scm/order/submit`（body 带 id）+ 保留 `Idempotency-Key` 头 |
| 分页入参 | `page` / `pageSize` | `pageNum` / `pageSize`（`PageParam`） |
| 分页出参 | `{records,page,pageSize,total}` | `{pageNum,pageSize,total,pages,list,emptyFlag}` |
| 响应体 | `{code,message,data}` | `{code,level,msg,ok,data,dataType}` |
| 鉴权 | Cookie `XSY_SESSION` + `X-XSRF-TOKEN` | `Authorization: Bearer <token>`，无 CSRF |

> ⚠️ 路径风格变更会**同步改变前端全部 API 调用**，属于预期成本。

### 8.2 Product 模块 API 映射（试点，逐条）

| 旧 | 新 | 权限 |
|---|---|---|
| `GET /api/products`（分页） | `POST /scm/product/queryPage` | `scm:product:query` |
| `GET /api/products/{id}` | `GET /scm/product/detail/{id}` | `scm:product:query` |
| `POST /api/products` | `POST /scm/product/add` | `scm:product:add` |
| `PUT /api/products/{id}` | `POST /scm/product/update` | `scm:product:update` |
| `PUT /api/products/{id}/status` | `POST /scm/product/updateStatus` | `scm:product:status` |
| `DELETE /api/products/{id}?version=` | `POST /scm/product/delete`（body 带 id + version） | `scm:product:delete` |
| `GET /api/product-categories/tree` | `GET /scm/product-category/tree` | `scm:product-category:query` |
| `POST /api/product-categories` | `POST /scm/product-category/add` | `scm:product-category:add` |
| `PUT /api/product-categories/{id}` | `POST /scm/product-category/update` | `scm:product-category:update` |
| `DELETE /api/product-categories/{id}` | `POST /scm/product-category/delete` | `scm:product-category:delete` |

### 8.3 其余模块 API 映射（模式级）

| 模块 | 旧路径族 | 新路径族 |
|---|---|---|
| customer | `/api/customers`、`/api/customer-types`、`/api/customer-agreement-prices`（别名 `/api/agreement-prices`）、`/api/customer-type-prices`（含 `/batch`）、`/api/price-history` | `/scm/customer/*`、`/scm/customer-type/*`、`/scm/agreement-price/*`、`/scm/customer-type-price/*`（`/batch` 保留）、`/scm/price-history/queryPage` |
| supplier | `/api/suppliers`（含 `/{id}/skus`）、`/api/warehouses` | `/scm/supplier/*`、`/scm/warehouse/*` |
| order | `/api/orders`（含 `/submit`、`/confirm`、`/cancel`、`/items/{itemId}/actual-quantity`、`/logs`）、`/api/order-returns`（`/approve`、`/reject`、`/cancel`）、`/api/order-refunds`（`/complete`） | `/scm/order/*`、`/scm/order-return/*`、`/scm/order-refund/*`（命令端点全部保留 `Idempotency-Key`） |
| purchase | `/api/purchase-demands`（`/generate`、`/allocate`）、`/api/purchase-orders`（`/submit`、`/cancel`、`/short-close`、`/logs`）、`/api/purchase-receipts`（`/confirm`、`/putaway`） | `/scm/purchase-demand/*`、`/scm/purchase-order/*`、`/scm/purchase-receipt/*` |
| inventory | `GET /api/inventories`、`GET /api/inventory-movements` | `/scm/inventory/queryPage`、`/scm/inventory-movement/queryPage` |
| mall | `/api/mall/auth/*`、`/addresses`、`/cart`、`/catalog`、`/home`、`/theme`、`/orders` | `/mall/*`（**保持独立前缀**，因商城是面向客户端的独立认证域，不与 `/scm` 后台混合） |
| marketing | `/api/marketing/*` | `/scm/marketing/*` |

### 8.4 错误码映射

旧错误码**数值语义保留**（前端已有提示文案依赖），但改为实现 SmartAdmin `ErrorCode` 接口：

| 旧错误码段 | 模块 | 新实现 |
|---|---|---|
| 40010–40921 | product | `ScmProductErrorCode implements ErrorCode` |
| 40430–40935 | customer | `ScmCustomerErrorCode` |
| 40440–40946 | supplier | `ScmSupplierErrorCode` |
| 40420–40926 | order | `ScmOrderErrorCode` |
| 40450–40954 | 售后 | `ScmAfterSalesErrorCode` |
| 40452–50060 | purchase | `ScmPurchaseErrorCode` |
| 40970–40971 | inventory / mall 结算 | `ScmInventoryErrorCode` / `ScmMallErrorCode` |
| 40170–50170 | mall | `ScmMallErrorCode` |
| 400900–409904 | marketing | `ScmMarketingErrorCode` |

> ⚠️ 需检查与 SmartAdmin 现有码段（`SYSTEM_ERROR=10001`、登录 `11011–11051`、`30007–30012`）**是否冲突**。建议 SCM 统一使用 **40000–49999** 区间以彻底隔离。

---

## 9. C6 · PERMISSION MAPPING

### 9.1 权限模型对照

| 项 | 旧 | V2 |
|---|---|---|
| 载体 | `sys_permission`（点号分隔 `product.read`） | `t_menu`（`menu_type=POINTS`，`api_perms` / `web_perms`，冒号分隔） |
| 后端校验 | `SecurityConfig` URL 匹配 + `AuthorityRules.hasAuthority` | `@SaCheckPermission("scm:product:add")` |
| 前端校验 | `hasPermission()` + 导航裁剪 | `v-privilege="'scm:product:add'"` + `$privilege()` |
| 超管 | `sys_user.administrator=TRUE` → `system.administrator` 全局放行 | SmartAdmin `administratorFlag` 内置放行 |
| 角色授权 | `sys_role_permission` + `sys_role_menu` | `t_role_menu` |
| 数据权限 | 无 | `@DataScope`（**能力存在但零使用**，见 R13） |

### 9.2 权限码映射表

| 旧权限码 | 新权限码 | 说明 |
|---|---|---|
| `product.read` | `scm:product:query`、`scm:product-category:query` | 拆为商品 + 分类 |
| `product.manage` | `scm:product:add` / `:update` / `:delete` / `:status`、`scm:product-category:add` / `:update` / `:delete` | 粗粒度拆细 |
| `customer.read` | `scm:customer:query`、`scm:customer-type:query`、`scm:agreement-price:query`、`scm:customer-type-price:query`、`scm:price-history:query` | — |
| `customer.manage` | `scm:customer:add` / `:update` / `:delete` / `:status`、`scm:customer-type:*`、`scm:agreement-price:*`、`scm:customer-type-price:*`（含 `:batch`） | — |
| `supplier.read` | `scm:supplier:query`、`scm:warehouse:query` | — |
| `supplier.manage` | `scm:supplier:add` / `:update` / `:status` / `:assign-sku`、`scm:warehouse:add` / `:update` / `:status` | 前端原未使用，需补齐 |
| `order.read` | `scm:order:query`、`scm:order-return:query`、`scm:order-refund:query` | — |
| `order.manage` | `scm:order:add` / `:update` / `:submit` / `:confirm` / `:cancel` / `:actual-quantity`、`scm:order-return:add` / `:approve` / `:reject` / `:cancel`、`scm:order-refund:complete` | 命令级拆分（便于按岗位授权） |
| `purchase.read` | `scm:purchase-demand:query`、`scm:purchase-order:query`、`scm:purchase-receipt:query` | — |
| `purchase.manage` | `scm:purchase-demand:generate` / `:allocate`、`scm:purchase-order:add` / `:update` / `:submit` / `:cancel` / `:short-close`、`scm:purchase-receipt:add` / `:confirm` | — |
| `purchase:receipt:putaway` | `scm:purchase-receipt:putaway` | 统一冒号风格 |
| `inventory.read` | `scm:inventory:query`、`scm:inventory-movement:query` | — |
| `marketing.read` | `scm:marketing:query` | — |
| `marketing.manage` | `scm:marketing:theme` / `:home-section` / `:promotion` / `:coupon` | — |
| `system.administrator` | SmartAdmin `administratorFlag` | 内置，不建权限码 |
| `system:user:*`、`system:role:*`、`system:menu:*`、`system:permission:*`、`system:department:*`、`system:login-log:list`、`system:operation-log:list` | SmartAdmin `system:employee:*`、`system:role:*`、`system:menu:*`、`system:department:*`、`support:login-log:query`、`support:operate-log:query` | 直接用 SmartAdmin 内置权限码 |

### 9.3 关键发现

> 旧权限码在数据库中**几乎未被播种**（只有 `marketing.read`/`marketing.manage`/`purchase:receipt:putaway` 三行由 V27/V32 插入），其余 `product.read` 等只是 `SecurityConfig` 里的字符串常量。
> **结论：权限体系基本是空的，重建成本低，不需要「迁移」旧授权关系，只需重新设计并播种。**

---

## 10. C7 · FRONTEND MAPPING

### 10.1 技术栈映射

| 能力 | 旧 React | V2 Vue |
|---|---|---|
| 框架 | React 19 + Vite | Vue 3.4.27 + Vite 5 |
| UI 库 | antd 5.29 + `@ant-design/pro-components` | `ant-design-vue` 4.2.5（**无 ProComponents**） |
| 表格 | `ProTable` | `a-table` + 手写 `queryForm`/`ajaxQuery` + `TableOperator`（列设置）+ `a-pagination` |
| 表单 | `ProForm`/`ModalForm`/`DrawerForm` + react-hook-form + zod | `a-form(ref + :model + :rules)` + `formRef.validate()` |
| 服务端状态 | `@tanstack/react-query` | **无缓存库**；`onMounted` 调 api + `ref/reactive` + `SmartLoading` |
| 全局状态 | React Context | `pinia`（`useUserStore` / `useAppConfigStore` / `useDictStore`） |
| 路由 | react-router 7 + `RequireAuth`/`Permission` | `vue-router` 4 + `beforeEach` 守卫 + `buildRoutes` 动态 `addRoute`（`name = menuId`） |
| 按钮权限 | `usePermission()` / `hasPermission` | `v-privilege="'scm:xxx:yyy'"` 指令 / `$privilege('...')` |
| 布局 | 自研 `AdminLayout`（Header + 双级侧栏） | SmartAdmin `side-layout`（单侧栏递归菜单）+ **多页签** |
| 字典 | 前端硬编码常量 | `t_dict` + `dict-plugin` + `dict-select`/`dict-label` |
| 枚举 | TS 常量 | `constants/*` + `$smartEnumPlugin.getDescByValue` |
| HTTP | axios（Cookie + CSRF） | axios（`Bearer` token），`src/lib/axios.ts` |
| 金额 | 字符串 + `utils/decimal.ts`（BigInt 定点） | 字符串 + **decimal.js 10.3.1**（已在依赖中） |
| 国际化 | 无 | `vue-i18n`（可先不启用中文以外语言） |

### 10.2 页面/组件映射

| 旧文件 | 新文件（建议） |
|---|---|
| `pages/products/ProductPage.tsx` | `src/views/scm/product/product-list.vue` |
| `pages/products/ProductDrawer.tsx` | `src/views/scm/product/components/product-form-drawer.vue` |
| `pages/products/SkuEditableTable.tsx` | `src/views/scm/product/components/sku-editable-table.vue` |
| `pages/products/productFormModel.ts` | `src/views/scm/product/product-form-model.ts`（纯逻辑可**近乎原样**移植，不含 JSX） |
| `pages/products/ProductCategoryPage.tsx` | `src/views/scm/product/product-category-list.vue`（参考 `views/business/erp/catalog/category-tree-table.vue`） |
| `pages/products/ProductCategoryDrawer.tsx` | `src/views/scm/product/components/product-category-form-modal.vue` |
| `api/products.ts` | `src/api/scm/product/product-api.ts`（对象导出 + `getRequest`/`postRequest`） |
| `components/common/StatusTag.tsx` | `src/views/scm/components/scm-status-tag.vue` 或复用 SmartAdmin 标签模式 |
| `components/common/AmountText.tsx` | `src/views/scm/components/scm-amount-text.vue`（保留 `tabular-nums` 右对齐） |
| `layouts/AdminLayout/navigation.tsx` | **删除** → 菜单数据来自 `t_menu`（后端下发 `menuList`） |
| `router/routeRegistry.tsx` | **删除** → 路由由 `buildRoutes` 从 `menuList` 动态构建 |

### 10.3 设计令牌与主题

- **保留**（与框架无关）：Admin Theme 色值（`#00B96B` / `#F5F7F9` / `#1F2329` / `#E5E6EB` / 侧栏 `#202631`）、尺寸规范（Header 56px、Button/Input 32px、Table Row 48–52px、Body 14px、Page Padding 24px）、表格对齐规则、`font-variant-numeric: tabular-nums`、状态 Tag 语义、危险操作二次确认。
- **重写**：`src/styles/tokens.ts` + `admin-theme.ts`（React/TS 常量）→ Vue 侧的 `src/theme/color.ts` + Less `modifyVars` + `<a-config-provider :theme>`。
- **⚠️ 冲突**：AGENTS.md §11.2 要求「Header + 一级侧栏 80px + 二级侧栏 140px」双级布局，而 SmartAdmin 是**单侧栏递归菜单 + 多页签**。按「前端 Layout 全部采用 SmartAdmin」的新决策，**建议以 SmartAdmin 布局为准，废止双级侧栏要求**（需确认）。

### 10.4 菜单注册

SCM 菜单写入 `t_menu`，`component` 字段填 Vue 组件路径（不含 `.vue`），与 `import.meta.glob('../views/**/**.vue')` 的 key 对应：

| 菜单 | `component` | 权限（web_perms） |
|---|---|---|
| 商品档案 | `scm/product/product-list` | `scm:product:query` |
| 商品分类 | `scm/product/product-category-list` | `scm:product-category:query` |

---

## 11. C8 · PRODUCT PILOT PLAN

### 11.1 试点目标与边界

**目标**：在不碰 legacy 代码的前提下，建立 SmartAdmin V2 clean baseline，并端到端打通「商品 SPU/SKU + 三级分类」一条业务线（含登录、权限、菜单、CRUD、乐观锁、软删除、定点金额）。

**明确不做**：customer / supplier / order / purchase / inventory / mall / marketing / 小程序 / 设备接入 / 数据大屏 / 采购超收 / 幂等命令端点。

### 11.2 阶段划分

**P0 · V2 Clean Baseline（前置，最高风险）**
1. 创建 V2 工作区（已确定为根目录 `xsy-scm-server/` 与 `xsy-scm-web/`；W0/W3 期间暂存于 `v2/`），从 SmartAdmin v3.31 复制 `sa-base` + `sa-admin` 作为基线；前端从上游 `xsy-scm-web` 复制。
2. 确定 Java 版本策略（17 保持 / 升 21，见 Q2）。
3. **MySQL → PostgreSQL 全量转换**：38 张系统表 DDL 转写；`DbType.POSTGRE_SQL`；`jdbc:p6spy:postgresql://`；逐表回归。
4. 引入 Flyway（SmartAdmin 原本没有），建立 `V1__sa_system_baseline.sql` + `V2__sa_system_seed.sql`。
5. 关闭/移除 V2 不需要的示例与模块：`t_category`/`t_goods`/`t_oa_*`、代码生成器（若 PG 重写成本高）、OA 示例业务。
6. 打通登录 + 超管 + 菜单下发 + 多页签 + 一个空页面。
7. **退出标准**：PG 上 `mvn spring-boot:run` 启动成功；浏览器登录成功；系统管理（员工/部门/角色/菜单）页面可正常 CRUD；Flyway `migrate` 幂等可重跑。

**P1 · Product 后端重写**
1. `V3__scm_product.sql`：`product_category`、`product_spu`、`product_sku`（含全部 CHECK / 部分唯一索引 / JSONB / version / deleted）。
2. `constant/`：`ProductTypeEnum`、`ShelfStatusEnum`、`ScmProductErrorCode`（实现 `ErrorCode`，保留 40010–40921 语义）。
3. `domain/entity/`：3 个 Entity（`@TableLogic`、`@Version`、`@TableName`）。
4. `domain/form/`：`ProductAddForm`/`ProductUpdateForm`/`ProductQueryForm(extends PageParam)`/`ProductStatusForm`、`ProductCategoryAddForm`/`ProductCategoryUpdateForm`。
5. `domain/vo/`：`ProductListVO`/`ProductDetailVO`/`ProductSkuVO`/`ProductCategoryTreeVO`。
6. `dao/`：3 个 `XxxDao extends BaseMapper`；`resources/mapper/scm/product/*.xml`（迁移旧 3 个 XML，改写方言与字段名）。
7. `service/`：`ProductService`（写，事务）、`ProductQueryService`（读，分页）、`ProductCategoryService`、`ProductAggregateValidator`（**领域逻辑近乎原样保留**）、`ProductSkuChangeSet`（**差量同步逻辑原样保留**）。
8. `manager/`：`ProductManager`（如需缓存/聚合装配）。
9. `controller/`：`ProductController`、`ProductCategoryController`，全部 `@SaCheckPermission` + `ResponseDTO`。
10. `V4__scm_product_seed.sql`：SCM 菜单 + `scm:product:*` / `scm:product-category:*` 权限码 + 演示数据。

**P2 · Product 前端重写**
1. `src/api/scm/product/product-api.ts`、`product-category-api.ts`（对象导出）。
2. `src/constants/scm/product-const.ts`（枚举，注册到 `constants/index.ts` 供 `$smartEnumPlugin` 使用）。
3. `src/views/scm/product/product-list.vue`（查询区 + `a-table` 展开行 SKU + 工具栏 + 分页）。
4. `src/views/scm/product/components/product-form-drawer.vue`（**保留 Drawer 形态**，因含可编辑 SKU 子表，Modal 空间不足）。
5. `src/views/scm/product/components/sku-editable-table.vue`。
6. `src/views/scm/product/product-category-list.vue` + `components/product-category-form-modal.vue`（参考 `views/business/erp/catalog/*`）。
7. `src/views/scm/components/{scm-status-tag,scm-amount-text}.vue`（共享展示组件）。
8. 全部按钮加 `v-privilege`；409 冲突统一提示；金额/数量按字符串传递。

**P3 · 联调与验收**
1. 菜单下发 → 路由构建 → 页面可达。
2. 权限验证：无 `scm:product:add` 的账号看不到新增按钮且后端 403。
3. 主链路：建三级分类 → 建商品（含 2 个 SKU、1 个默认）→ 改 SKU（验证 ID 保留）→ 上下架（version 冲突 409）→ 删除 → 编码复用。
4. 回归项：分页正确、JSONB 规格快照正确、金额 4 位字符串正确、逻辑删除后唯一索引可复用。

### 11.3 Definition of Done（Pilot）

- [ ] V2 在 PostgreSQL 上可启动、可登录、可跑通系统管理 CRUD
- [ ] Flyway 迁移可从空库一键重建
- [ ] Product 后端单测通过（`ProductAggregateValidatorTest`、`ProductSkuChangeSetTest` 逻辑等价移植）
- [ ] Product 后端集成测试在 PG 上通过
- [ ] Product 前端 `npm run build` 通过、无 TS 错误
- [ ] 浏览器主链路（P3.3）人工验证通过
- [ ] **未修改任何 legacy 目录文件**
- [ ] 报告中「需确认决策」全部有结论

---

## 12. C9 · 风险列表

| # | 风险 | 等级 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | **SmartAdmin 官方不含 PG 脚本**（PG 需付费购买） | 🔴 高 | 38 张系统表 + 全部 mapper XML 需手工转写，是 Pilot 最大工作量与最大不确定性 | 自建 PG DDL；先做 3 张表（`t_employee`/`t_menu`/`t_dict`）的转换验证再批量；考虑购买官方 PG 脚本以降低风险（需业务决策） |
| R2 | **文档规则与新决策冲突**（`SMARTADMIN_REFERENCE_RULES.md` 第 8 行、`AGENTS.md` 第 6 行、`xsy-vs-smartadmin-conventions.md` §4） | 🔴 高 | 后续 agent 会依据旧规则拒绝 Vue / 坚持 Spring Security | **迁移第一步先修订这 3 处**，否则任何编码都会被规则阻塞 |
| R3 | **`BigDecimalNullZeroSerializer`（null→0）违反「缺价 UNPRICED」** | 🔴 高 | 会把无价商品静默变成 0 元，污染订单金额 | 为 SCM 排除该全局序列化器；SCM 统一注册 `FixedScale4Serializer`；加断言测试 |
| R4 | **`PaginationInnerInterceptor(DbType.MYSQL)` 硬编码** | 🔴 高 | 不改则 PG 上分页 SQL 全错（`LIMIT ?,?` 语义差异） | P0 阶段必改 `DbType.POSTGRE_SQL` 并回归全部分页接口 |
| R5 | **认证体系切换**（Spring Session JDBC + Cookie → Sa-Token + Redis Bearer） | 🔴 高 | Redis 从「按需」变为**硬依赖**；前端全部请求头/登录态改写；CSRF 机制消失；`mall` 独立认证栈需重设计 | 接受 Sa-Token 为唯一认证体系；`mall` 采用 Sa-Token 多 `userType` 或保留独立过滤器但去掉 Spring Security 依赖（需决策） |
| R6 | **Java 版本 17 vs 21 差异** | 🟡 中 | SmartAdmin 基线 target 17，旧项目 21 | 建议统一升 21（SB 3.5.4 支持 17–24，风险低），但**需人工批准**（见 Q2）；不可自行升级 |
| R7 | **逻辑删除范式冲突**（`@TableLogic deleted BOOLEAN` + 部分唯一索引 vs `deleted_flag tinyint` 手工） | 🟡 中 | 若误用 SmartAdmin 风格会丢失「编码删后可复用」能力 | SCM 表统一 `deleted BOOLEAN` + `@TableLogic` + 部分唯一索引；SmartAdmin 无全局 logic-delete 配置，不冲突；需在 V2 显式声明不启用全局配置 |
| R8 | **审计字段范式冲突**（`created_by`/`updated_by` 字符串 vs 无自动填充） | 🟡 中 | 操作人留痕缺失或需逐处手写 | 扩展 `MybatisPlusFillHandler` 或提供 SCM 基类显式写入；统一替换 `"SYSTEM"` |
| R9 | **权限体系重建**：旧为 URL 级 + 权限码几乎未播种 | 🟡 中 | 需全新设计 `scm:*` 权限码并播种，工作量集中在 `t_menu` 数据 | 按 C6 映射表一次性设计完整权限树；用 SQL 种子管理 |
| R10 | **幂等 vs SmartAdmin `repeatsubmit`** | 🟡 中 | 用错会丢失「同键同内容重放」语义 | SCM 保留 `idempotency_record`；不替换 |
| R11 | **单号生成**：PG sequence vs `t_serial_number` | 🟢 低 | 双套单号机制 | 建议保留 sequence（简单、已验证）；后续需要可配置单号规则时再评估 |
| R12 | **布局冲突**：AGENTS.md 双级侧栏 vs SmartAdmin 单侧栏 + 多页签 | 🟡 中 | UI 规范自相矛盾 | 按新决策以 SmartAdmin 布局为准，废止双级侧栏（需确认，见 Q6） |
| R13 | **数据权限能力存在但零使用** | 🟡 中 | AGENTS.md 要求 warehouse/customer/supplier/purchaser/salesperson 数据范围，SmartAdmin `DataScopeTypeEnum` 只有 NOTICE | Pilot 不启用；后续按域扩展 `DataScopeTypeEnum` + `@DataScope` + `DataScopeSqlConfigService` 注册 |
| R14 | **SmartAdmin 无 Flyway**（手工 SQL） | 🟡 中 | 与项目「迁移只能追加、不可改已应用迁移」纪律冲突 | V2 引入 Flyway（保留旧项目的正确实践）；SmartAdmin 的 `sql-update-log` 仅作 DDL 来源参考 |
| R15 | **`mall` / `marketing` 成熟度低且耦合深** | 🟡 中 | `mall` 依赖 customer+order+marketing；`marketing` 零测试且未参与金额计算 | 排在 order/customer 之后迁移；`marketing` 需补测试与订单联动设计 |
| R16 | **小程序 Taro React → uni-app Vue3 是重写** | 🟡 中 | 非「迁移」而是重新实现（页面结构/接口契约可复用） | 以上游 `xsy-app` 为骨架；复用 `services`/`types` 的接口契约 |
| R17 | **SmartAdmin 使用 fastjson 2.0.57** | 🟡 中 | 历史安全记录不佳 | 评估是否替换为 Jackson；至少确认其仅用于 Redis 序列化 |
| R18 | **`xsy-device-agent` 不存在** | 🟢 低 | AGENTS.md 中的设备接入与称重 UI 无实现 | 不在本次范围；V2 保留 `WeightDisplay` 等前端组件设计规范 |
| R19 | **代码生成器强 MySQL** | 🟢 低 | `CodeGeneratorMapper.xml` 需重写 | V2 可不启用代码生成器；或重写为 `information_schema` + `current_schema()` |
| R20 | **前端无请求缓存库** | 🟢 低 | 旧 React 用 TanStack Query，Vue 侧需手写 loading/刷新 | 按 SmartAdmin 范式手写；如需可评估 `@tanstack/vue-query`（**需批准，属新增依赖**） |
| R21 | **`supplier` 前端原为只读** | 🟢 低 | 迁移时需补齐 CRUD，不能「照搬」 | 按 SmartAdmin CRUD 范式新写 |
| R22 | **超收 UI 缺失**（`PurchaseReceiptStatusSummary.tsx` 为死代码） | 🟢 低 | 后端有超收逻辑，前端无提示 | 迁移 purchase 时补齐 |

---

## 13. C10 · 文件级变更列表

> 说明：本批次**只准备 SmartAdmin clean baseline + Product Pilot**，不迁其他模块。

### 13.1 新增（V2 工作区）

**目录骨架（当前规则；W0/W3 实际证据暂存于 `v2/`）**
```
xsy-scm/
├─ xsy-scm-server/             # 从上游 xsy-scm-server 派生
│  ├─ sa-base/                 # 保持 SmartAdmin 原样（仅 PG 适配改动）
│  ├─ sa-admin/                # 保持 SmartAdmin 原样 + 新增 module/scm/**
│  └─ pom.xml
├─ xsy-scm-web/                # 从上游 xsy-scm-web 派生
│  ├─ src/
│  └─ package.json
└─ v2/                          # W0/W3 迁移过渡源与验证证据
```

**后端新增（Product Pilot）**
```
sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/
├─ constant/ProductTypeEnum.java
├─ constant/ShelfStatusEnum.java
├─ constant/ScmProductErrorCode.java
├─ controller/ProductController.java
├─ controller/ProductCategoryController.java
├─ manager/ProductManager.java
├─ service/ProductService.java
├─ service/ProductQueryService.java
├─ service/ProductCategoryService.java
├─ service/ProductAggregateValidator.java
├─ service/ProductSkuChangeSet.java
├─ dao/ProductSpuDao.java
├─ dao/ProductSkuDao.java
├─ dao/ProductCategoryDao.java
└─ domain/
   ├─ entity/ProductSpuEntity.java
   ├─ entity/ProductSkuEntity.java
   ├─ entity/ProductCategoryEntity.java
   ├─ form/ProductAddForm.java
   ├─ form/ProductUpdateForm.java
   ├─ form/ProductQueryForm.java
   ├─ form/ProductStatusForm.java
   ├─ form/ProductCategoryAddForm.java
   ├─ form/ProductCategoryUpdateForm.java
   ├─ vo/ProductListVO.java
   ├─ vo/ProductDetailVO.java
   ├─ vo/ProductSkuVO.java
   └─ vo/ProductCategoryTreeVO.java

sa-admin/src/main/resources/mapper/scm/product/
├─ ProductSpuMapper.xml
├─ ProductSkuMapper.xml
└─ ProductCategoryMapper.xml

sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/common/
├─ ScmFixedScale4Serializer.java        # 4 位定点字符串序列化（替代 BigDecimalNullZeroSerializer）
└─ ScmFixedScale4Deserializer.java
```

**前端新增（Product Pilot）**
```
src/api/scm/product/product-api.ts
src/api/scm/product/product-category-api.ts
src/constants/scm/product-const.ts
src/views/scm/components/scm-status-tag.vue
src/views/scm/components/scm-amount-text.vue
src/views/scm/product/product-list.vue
src/views/scm/product/product-category-list.vue
src/views/scm/product/components/product-form-drawer.vue
src/views/scm/product/components/sku-editable-table.vue
src/views/scm/product/components/product-category-form-modal.vue
src/views/scm/product/product-form-model.ts
```

**数据库迁移新增**
```
sa-admin/src/main/resources/db/migration/
├─ V1__sa_system_baseline.sql        # SmartAdmin 38 张系统表 PG 版
├─ V2__sa_system_seed.sql            # 部门/职务/超管/角色/菜单/功能点/字典/配置
├─ V3__scm_product.sql               # product_category / product_spu / product_sku
└─ V4__scm_product_seed.sql          # SCM 菜单 + scm:product:* 权限码 + 演示数据
```

### 13.2 修改（V2 基线适配，非 legacy）

| 文件 | 改动 |
|---|---|
| `sa-base/src/main/java/.../config/MybatisPlusConfig.java` | `PaginationInnerInterceptor(DbType.MYSQL)` → `POSTGRE_SQL` |
| `sa-base/src/main/java/.../config/DataSourceConfig.java`（及 `application.yaml`） | JDBC URL → `jdbc:p6spy:postgresql://`；Druid 驱动 → PG |
| `sa-base/src/main/java/.../config/JsonConfig.java` | 排除 `BigDecimalNullZeroSerializer` 对 SCM 模块的生效（或改为仅 SCM 使用 4 位序列化器） |
| `sa-base/src/main/resources/mapper/support/CodeGeneratorMapper.xml` | MySQL → PG（或禁用代码生成器） |
| `sa-base/src/main/resources/mapper/support/LoginLogMapper.xml` 等 5 处 `LIMIT` | 回归验证 |
| `sa-admin/pom.xml` + `sa-base/pom.xml` | 加入 `postgresql` 驱动；如升 Java 21 则改 `maven.compiler.release` |
| `sa-admin/src/main/resources/{dev,test,pre,prod}/application.yaml` | 数据源、Flyway 配置 |
| `xsy-scm-web/package.json` | 项目名、代理目标（`VITE_APP_API_URL`）、端口 |
| `xsy-scm-web/src/constants/index.ts` | 注册 SCM 枚举 |
| `xsy-scm-web/src/router/routers.ts` | 如需 SCM 首页/默认跳转 |

### 13.3 修改（仓库级规则文档，**建议在编码前完成**）

| 文件 | 改动 |
|---|---|
| `SMARTADMIN_REFERENCE_RULES.md` | 废止第 8 行「禁止替换为 Vue / 禁止并行维护 Vue 管理后台」；改为「SmartAdmin 为 V2 正式底座」 |
| `AGENTS.md` | 第 6 行 SmartAdmin 参考规则改写；§4 技术栈（前端 React → Vue3、后端认证 → Sa-Token）；§10–§15 前端章节按 Vue 重写；§11.2 双级侧栏条款废止；新增「V2 迁移章节」 |
| `CLAUDE.md` | 「Current scope」与「Architecture and data flow」按 V2 更新；命令改为 V2 目录 |
| `README.md` | 项目状态与目录结构更新 |
| `docs/architecture/xsy-vs-smartadmin-conventions.md` | §4 认证选型（Spring Security → Sa-Token）重写 |
| `docs/design/xsy-scm-UIUX设计与前端开发指导-v1.1.md` | §11/§22/§25–§30 按 Vue3 + ant-design-vue 重写；保留色值/尺寸/表格规范 |
| `docs/architecture/xsy-scm-项目设计草案-v0.2.md` | 标注为历史文档；技术栈段落更新 |
| `docs/00-文档总览与索引.md` | 更新文档优先级与废弃清单 |
| `docs/superpowers/plans/*`（10 份） | 标注 Superseded（仅追加标记，不删内容） |

### 13.4 **当时的不修改范围与当前边界**

W0 审计时根目录后端/前端仍是 legacy 参照。自 2026-09-15 起，根目录
`xsy-scm-server/` 与 `xsy-scm-web/` 改为 V2 正式工作区；仅保留
`xsy-scm-miniapp/` 为冻结 legacy。以下记录中的“legacy 后端/前端”是 W0 的历史状态，
不覆盖当前工作区规则。

```
xsy-scm-miniapp/**         # 全部 legacy 小程序
project-reference-examples/xsy-scm/**             # 只读上游参考
docs/superpowers/specs/**  # 业务规格（V2 的输入，不改）
docs/requirements/**       # 需求基线（不改）
CONTEXT.md                 # 领域词汇（不改）
```

---

## 14. 需人工确认的决策（9 项）

| # | 决策 | 选项 | 建议 |
|---|---|---|---|
| Q1 | **V2 代码放哪里** | (a) 仓库内新增 `v2/` 目录；(b) 新建独立仓库 `xsy-scm-v2` | **(a)**：保留上下文与历史，legacy 目录冻结不动，符合「不修改 legacy」 |
| Q2 | **Java 版本** | (a) 保持 SmartAdmin 基线 Java 17；(b) 统一升 Java 21 | **(b)**：SB 3.5.4 官方支持 17–24；旧代码为 21。但需你批准，我不自行升级 |
| Q3 | **PostgreSQL 脚本来源** | (a) 自写转换 38 张系统表；(b) 购买 SmartAdmin 官方 PG 脚本 | **(a) 先做 3 张表验证**，评估后再决定是否购买以降低风险 |
| Q4 | **认证切换范围** | (a) 后台 Sa-Token + 商城保留独立 token（两套）；(b) 全部统一 Sa-Token 多 `userType` | **(b)** 长期更简洁；但 (a) 迁移期风险更低。建议 Pilot 阶段先只做后台，商城留到 mall 迁移时再定 |
| Q5 | **响应体字段** | (a) 完全采用 `ResponseDTO{code,msg,...}`；(b) 保留 `{code,message,data}` 兼容旧前端 | **(a)**：前端全部重写，无兼容包袱。但需确认 `msg` vs `message` 的统一 |
| Q6 | **布局** | (a) 采用 SmartAdmin 单侧栏 + 多页签；(b) 定制成 AGENTS.md 的双级侧栏 | **(a)**：符合「Layout 全部采用 SmartAdmin」。需明确废止 AGENTS.md §11.2 |
| Q7 | **错误码区间** | (a) SCM 保留旧数值（40010–409904 分散）；(b) SCM 统一收拢到 40000–49999 | **(b)**：彻底隔离 SmartAdmin 的 10001/11011–11051/30007–30012 |
| Q8 | **`marketing` 是否保留** | (a) 保留并补齐测试与订单联动；(b) 判定为半成品，暂缓/裁剪 | 需你判断：它零测试且未参与金额计算，是否属于「废弃 POC」 |
| Q9 | **`t_config` 承载采购超收容差** | (a) 复用 SmartAdmin `t_config`；(b) 建 SCM 专用 `scm_config` | **(a)** 优先，但需先核对 `t_config` 的表结构与读写 API 是否支持业务配置 |

---

## 附录 A · 旧→新 模块归属总览

| 旧模块 | 新归属 | 处理 |
|---|---|---|
| `product` | `module/scm/product` | REWRITE（试点） |
| `customer` | `module/scm/customer` | REWRITE（第 2 批，`CustomerPriceResolver` 优先） |
| `supplier` | `module/scm/supplier` | REWRITE（含补齐前端 CRUD） |
| `order` | `module/scm/order` | REWRITE（依赖 customer 稳定） |
| `purchase` | `module/scm/purchase` | REWRITE（依赖 order/supplier/inventory） |
| `inventory` | `module/scm/inventory` | REWRITE（随 purchase） |
| `mall` | `module/scm/mall`（或独立 `module/mall`） | REWRITE + 认证重设计 |
| `marketing` | `module/scm/marketing` | REWRITE（需补测试，见 Q8） |
| `auth` | — | **DROP**（用 SmartAdmin `module/system/login` + Sa-Token） |
| `system` | — | **DROP**（用 SmartAdmin `module/system/*`） |
| `common/api`、`common/exception` | — | **DROP**（用 `ResponseDTO`/`PageResult`/`ErrorCode`） |
| `common/json` | `module/scm/common` | REWRITE（4 位定点序列化器） |
| `common/persistence` | `sa-base` config | 合并（JSONB TypeHandler 需保留） |

## 附录 B · 迁移波次建议

| 波次 | 内容 | 前置 |
|---|---|---|
| W0 | 规则文档修订 + V2 基线 + PG 转换 | 无 |
| W1 | **Product Pilot** | W0 |
| W2 | customer（含 `CustomerPriceResolver`）+ supplier | W1 |
| W3 | order + inventory | W2 |
| W4 | purchase（含超收 UI 补齐） | W3 |
| W5 | mall + marketing | W2（依赖 customer） |
| W6 | 小程序 uni-app Vue3 | W5 |

---

*报告结束。本报告未修改任何 legacy 代码；仅新增本文档。等待确认后进入 W0。*
