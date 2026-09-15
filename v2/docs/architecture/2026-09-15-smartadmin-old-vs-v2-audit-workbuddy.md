# W2-A Repository A/B Audit · SmartAdmin-old vs 当前 V2

> 审计日期：2026-09-15
> 审计对象：A = Legacy（`xsy-scm-server/**`、`xsy-scm-web/**`、`xsy-scm-miniapp/**`）；B = 当前正式 V2（`v2/xsy-scm-v2-server/**`、`v2/xsy-scm-v2-web/**`）；C = 旧 SmartAdmin 二开项目（`project-reference-examples/xsy-scm/**`）
> 边界：**只调查**。未编码、未复制、未合并、未修改任何业务代码，未修改 reference 项目。C 未运行 Maven/npm 构建或服务启动（会产生 `target/`、构建产物与运行状态，越界）。
> 前置动作：W2 Customer/Supplier 开发已按指令暂停。
> 结论：**唯一推荐方案 A**（见 §11）。

---

## 0. 证据口径与一句话结论

| 项 | 说明 |
|---|---|
| 数字来源 | 全部为实测（`find` / `wc -l` / `grep -c` / 读文件），非估算 |
| 完成度定义 | "有源码/API/页面"只算**已实现资产**；涉及关键业务但缺自动化或真实链路证据的，一律标记 `FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED`，**不等同于完成** |
| "能否编译/启动" | 分列「静态可构建性」与「本次未运行实证」，不把存在源码误报为启动验收 |
| 一句话结论 | C 是**资产库**，不是**候选主干**。C 的价值在 Customer / Supplier / Purchase / Receiving / Stock 五个领域的页面交互、DTO 形状与业务对象组织；它的聚合规则、并发控制、数据库约束与测试证据全面弱于当前 V2，**不具备反向承载 V2 的资格** |

---

## 1. 第一步：C 自身文档阅读结论

已完整阅读：`AGENTS.md`（44 KB）、`README.md`、`XSY_SCM_INIT.md`、`XSY_SCM_JAVA17_AI_EXECUTION_GUIDE.md`（24 KB）、`docs/requirement/**`（15 篇）、`docs/database/**`（含 6 个 DDL 脚本）、`功能清单模块.docx`（已成功解出全文 62 段）、`数据库SQL脚本/**`、`deploy/**`。

### 1.1 C 原本的业务目标（来自 `功能清单模块.docx`）

文档列出 13 个功能模块：商品管理、客户管理、营销管理、订单管理、采购管理、库存管理、分拣管理、物流配送、财务报表管理、后台管理、下单商城、订单助手、溯源功能。

关键业务口径（文档原话摘要）：
- 商品：三级分类、批量改图/改价、不同类型客户不同定价、时价、协议价、标品/非标品/多规格/多供应商、自采 vs 供应商送货默认采购员/供应商。
- 客户：分类型、指定客户显示/屏蔽商品、下属单位独立采购 + 集团统一结算、绑定供应商/业务员 + 专属推广二维码、账期按金额/时间（月或天）灵活设置。
- 订单：商城下单 + 后台快速录单、补单、订单核算、**实重同步到客户手机端**、退款退货、异常订单报表、操作日志。
- 采购：订单实时汇总、按供应商/采购员/品类自动生成采购单、采购单二维码分享、**多次收货、实重收货**。
- 库存：实重入出库、盘点、报损报溢、**多规格转换**、与商城/订单联动、阈值预警、**进价加权平均核算**。
- 分拣：按单品或客户订单两种分拣、实重分拣打印、误差阈值、标品一键分拣、按分拣员/送货时间/线路/供应商筛选、供应商代分拣。
- 配送：按区域/时间/数量/金额线路规划、线路订单分布可视、轨迹记录、发货单一键打印。

### 1.2 C 的技术约束（自述）

- 基线：**SmartAdmin v3.30.0**（Tag `baseline-smartadmin-v3.30.0`，commit `6867198b`），Java 17 + Spring Boot 3 + Vue3 + TS + **MySQL 8** + Redis。
- 单仓库三目录：`xsy-scm-server`（后端，含 `xsy-scm-base` 平台 fork）、`xsy-scm-web`（PC 后台）、`xsy-app`（UniApp Vue3 移动端）。
- 铁律：不改 Java17/Boot 大版本/MyBatis-Plus/Vue3/AntD Vue/Sa-Token；不拆微服务；不引入多套 Result/权限/异常/文件/任务框架；金额重量一律 BigDecimal/DECIMAL，重量内部统一 kg；库存必须"余额 + 流水"，任何模块不得自行 UPDATE 库存；订单价格用快照，禁止关联实时商品价格；订单/采购/库存流水/退款/配送/财务不允许物理删除。
- 数据规则：**默认不依赖 MySQL 物理外键**；每张业务表明确主键/业务编号/状态/创建更新时间/创建更新人/逻辑删除；状态用 Enum 不用散落 0/1/2。
- 节奏：需求确认 → 代码调查 → 数据模型 → SQL → 后端 API → 测试 → PC → xsy-app → 联调 → 提交；**禁止一次让 AI 同时生成所有业务模块**。

### 1.3 模块规划与数据库设计（C 的设计文档）

`docs/database/00-数据库设计总览.md` 明确：**当前范围只有 P0 主链路（商品/客户/订单/采购/库存）**，分拣/配送/财务/营销/商城/订单助手/溯源**明确列为"不在本次范围（后续 P1/P2）"**。P1 补充模块脚本（`07-补充模块P1.sql`）自述状态为**草案，需与 P0 一并评审**。

精度口径（C 已定）：金额 `DECIMAL(18,2)`、单价/均价 `DECIMAL(18,4)`、重量 `DECIMAL(18,3)`（单位 kg）、数量 `DECIMAL(18,3)`；Java 侧一律 BigDecimal。编号口径：`{module}_no` = 前缀 + `yyyyMMdd` + 4 位流水（按日重置），商品/客户编码 = 前缀 + 6 位流水（不重置），编号字段唯一索引。

仓库与批次：**单仓库、不启用批次与保质期**；`warehouse_id`（默认 1）与 `batch_id` 保留但暂不使用。

### 1.4 文档与实现的落差（关键判断依据）

> **不要只根据源码文件数量判断完成度。**

文档承诺 13 个模块，实现只覆盖到 P0 主链路 + 部分 P1：

| 文档承诺模块 | 代码实现实况 |
|---|---|
| 商品 / 客户 / 订单 / 采购 / 库存 | 有实现（详见 §3） |
| 营销管理 | **后端无 marketing 包**；`ProductPriceService` 里留了 `PROMOTION` 促销价空位，注释"待营销模块接入" |
| 分拣管理 | **无独立模块**；仅订单状态枚举有 `SORTING(5)/SORTED(6)` 状态位 |
| 物流配送 | **无独立模块**；仅订单状态枚举有 `DELIVERING(7)` 状态位 |
| 下单商城 | **后端无 mall 包**；仅 xsy-app 有商品浏览页，无购物车/下单 |
| 订单助手 | 无独立实现（部分能力落在 xsy-app 订单页） |
| 溯源功能 | 有 `trace` 包（batch/code/inspect/warn），但**前端溯源页面全部缺失** |
| 数据大屏 | 有 `screen` 包（config/data），但**前端大屏页面缺失** |

---

## 2. 第二步：C 技术基线完整审计

### 2.1 技术栈对照

| 项 | C 实际值 | 当前 V2 实际值 | 差异与影响 |
|---|---|---|---|
| SmartAdmin | **v3.30.0**（文档/SQL 更新日志一致） | **v3.31** | C 系统层不可复制；需逐 API 适配 |
| 后端包名 | `com.xsy.scm.base` / `com.xsy.scm.admin`（**已重命名 fork**，非 `net.lab1024.sa.*`） | `net.lab1024.sa.base` / `net.lab1024.sa.admin` | C 的平台是"源码内嵌 + 包名重写"，**升级不是改依赖版本，而是 diff/merge 源码** |
| Java | **17**（compiler source/target 17，`-parameters`） | **21** | 业务代码需在 Java 21 约束下重新吸收 |
| Spring Boot | **3.5.4**（BOM import，无 parent） | 3.5.4 | 一致，非主要成本 |
| MyBatis-Plus | **3.5.12**（`mybatis-plus-spring-boot3-starter` + `jsqlparser`） | 3.5.12 | 一致，Dao/Manager/XML 结构可作参考 |
| Sa-Token | **1.44.0**（`sa-token-spring-boot3-starter` + `sa-token-redis-jackson`） | 1.44.0 | 一致；权限命名与登录上下文需对齐 V2 |
| 数据库 | **MySQL 8**（`jdbc:p6spy:mysql://127.0.0.1:3306/supply_chain`，P6Spy + Druid，max-active 10） | **PostgreSQL 18.3**（port 15432，schema `xsy_v2`） | **最大成本项**，见 §7 |
| PostgreSQL 驱动 | **不存在**（仅 `mysql-connector-j:9.3.0`） | 已有 | C 无 PG 能力 |
| Redis | `spring-boot-starter-data-redis` + `redisson-spring-boot-starter:3.50.0`，`spring.cache.type=redis`，db 1 | 已有（容器 `xsy-v2-redis`） | 可复用缓存/重复提交思路 |
| 文件上传 | `file.storage.mode: local`，`upload-path: C:/Users/chenk/xsy-scm/upload/`；另含阿里云 OSS（未启用）；AWS SDK S3 | SmartAdmin File 模块（`COMMON` 目录） | 业务只应迁文件字段与展示，不迁存储实现 |
| 日志 | log4j2（`log4j2-spring.xml`），`${localPath}/logs/...`，Tomcat accesslog 开启 | 已有 | — |
| 权限 | 后端 `@SaCheckPermission` **272 次**（14 模块；purchase 37 / customer 31 / product 29 / stock 28）；前端 `v-privilege` **272 次 / 58 文件** | V2 权限体系 + `scm:*` 命名 | 权限码需整体重命名并映射 |
| Vue | **3.4.27** | Vue 3 | 同大版本，页面语义可较高复用 |
| UI 库 | **Ant Design Vue 4.2.5** | Ant Design Vue 4 | 一致 |
| 构建工具 | 后端 Maven 3.9.9；Web Vite **5.2.12**；xsy-app Vite **4.0.3** | 后端 Maven；Web Vite | Web 相近；移动端待 W6 决策 |
| UniApp | `@dcloudio/uni-app 3.0.0-3090920231225001`，**Vue 3.2.47**，Pinia 2.0.36，`uni-ui 1.5.0` | V2 未建移动端；Legacy miniapp 是 **Taro 4.2.1 + React 18.3.1** | 技术路线与 Legacy 完全不同，需 W6 单独决策 |
| migration / SQL 管理 | **无 Flyway / 无 Liquibase**；SmartAdmin 官方 SQL 按版本手工执行（`数据库SQL脚本/mysql/sql-update-log/v3.15.0~v3.30.0`）；业务设计 SQL 独立放 `docs/database/`；运行时业务表在 `deploy/sql/xsy_scm_v3.30.0_supply_chain.sql`（第 1317–2076 行） | **Flyway 唯一 schema 演进机制**（V1–V7 已应用，V6/V7 哈希锁定不可回改） | C 的 SQL 纪律与 V2 完全相反；**必须重建为 V2 Flyway migration** |
| 是否能编译 | **静态可构建**（pom 依赖齐全、多模块完整、无孤儿引用；实测无 `target/`、无任何编译产物 → 本机从未编译过） | 已构建（`sa-admin/target` 存在） | **本次未运行实证** |
| 是否能启动 | 有 Docker Compose / Dockerfile / dev-test-prod 配置；依赖 MySQL + Redis + 环境变量 + 文件服务 | 已启动验收（18080/18081） | **本次未运行实证**；不能宣称端到端可启动 |

### 2.2 C 代码规模实测

| 指标 | 数值 |
|---|---|
| Java 文件 | **1056**（`xsy-scm-base` 359 + `xsy-scm-server` 697） |
| Java 总行数 | **63,675**（base 24,057 + server 39,660） |
| Controller / Service / Manager / Dao / Entity | **88 / 109 / 55 / 83 / 69** |
| Form / VO | 199 / 105 |
| Mapper XML | **87 个 / 4,743 行**（100% 含手写 SQL） |
| `@Transactional` | **294 处 / 99 文件** |
| `@Version`（乐观锁） | **0 处** |
| `@SaCheckPermission` | 272 次 |
| 业务表 | **47 张**（运行时脚本 25 + P0 设计 22） |
| SQL 文件 | 19 个 / 5,363 行 |

### 2.3 C 的包结构（分层规范，但业务聚合弱）

业务代码集中在 `com.xsy.scm.admin.module.business.*`（575 文件 / 31,419 行）；系统管理在 `...module.system.*`（113 文件 / 7,777 行）。分层为 Controller → Service（具体类，无 interface/impl 拆分）→ Manager（`@Service` + 事务）→ Dao（`@Mapper`），**全项目仅 4 个 `*ServiceImpl`**（均在 base 的 cache/file 策略实现）。

各领域规模：

| 领域包 | 文件数 | 行数 | 对应需求领域 |
|---|---:|---:|---|
| product | 58 | 2,879 | Product（含 Pricing 的 ProductPrice） |
| customer | 63 | 3,006 | Customer |
| purchase | 79 | 4,361 | Purchase + **Receiving**（Receive*） |
| stock | 67 | 3,843 | Inventory / Stock |
| finance | 56 | 2,989 | Finance |
| oa | 56 | 3,892 | OA（bank/enterprise/invoice/notice，非供应链） |
| order | 43 | 2,386 | Sales Order |
| supplier | 41 | 2,019 | Supplier（账户/厂商/报品/对账） |
| trace | 33 | 1,682 | Traceability |
| external | 23 | 1,176 | 外部系统对接 |
| screen | 18 | 810 | Screen |
| category | 14 | 996 | 分类 |
| print | 13 | 668 | 打印模板 |
| goods | 11 | 712 | 商品（简版） |

---

## 3. 第三步：A / B / C 按领域对比

> 完成度取值：**完整** / **部分** / **骨架** / **不存在**。凡关键业务缺自动化或真实链路证据的，统一加注 `UNVERIFIED`。

### 3.1 三源现状

| 领域 | A · Legacy 现状 | B · 当前 V2 现状 | C · SmartAdmin-old 现状 |
|---|---|---|---|
| **Product** | `product_spu` / `product_sku` / `product_category` 3 表；SPU-SKU 聚合校验、变更集、分类树、乐观锁；2 Controller / 6 Service / 1,533 行；**无商品图片能力**（W1 D9 已记为缺陷） | **W1 已验收**：4 表（含 `product_image`）、`ProductAggregateValidator` + 差量同步 Manager、双 `@Version`、JSONB 规格、BigDecimal 4 位契约、`scm:product:*` 权限、E2E 2 用例；43 项测试全绿 | 6 Controller / 6 Service / 6 Manager / 6 Entity / 58 文件 2,879 行；SPU/SKU/条码/价格/供应商/分类全 CRUD + Vue 页面 |
| **Customer** | `customer` / `customer_type` / `customer_agreement_price` / `customer_type_price` / `customer_sku_visibility` / `customer_price_batch_audit` + 2 日志表 = 8 表；5 Controller / 18 Service / 2,290 行；**价格解析优先级** `CustomerPriceResolver`、价格历史快照、批量调价、乐观锁、可见性变更集 | **未迁入**（W2 已暂停） | `t_customer` / `t_customer_period` / `t_customer_goods_visible` / `t_customer_qrcode` + P1 的 `t_customer_discount` / `t_customer_product_alias`；6 Controller / 6 Service；63 文件 3,006 行；Vue 4 列表页 1,355 行 |
| **Supplier** | `supplier` / `supplier_sku` / `warehouse` 3 表；2 Controller / 4 Service / 1,198 行；Vue 端 `SupplierPage`(15) / `WarehousePage`(15) 仅**占位式只读** | **未迁入** | 采购侧 `Supplier` + 独立 `supplier` 包（Account/Manufacturer/ProductApply/Statement）；41 文件 2,019 行；Vue 4 页 1,572 行（含对账单 441 行） |
| **Pricing** | **非独立领域**，寄居 customer：`CustomerPriceResolver` 四级解析 + `customer_agreement_price` / `customer_type_price` + 价格历史 + 批量调价审计 | W1 只有**商品基础价格字段**；客户定价未迁入 | **非独立领域**，寄居 product：`t_product_price`（基础/分级/时价/协议价四级）+ `PriceTypeEnum` + `t_customer_discount`；`ProductPriceService.resolvePrice` 已实现优先级（促销价留空位） |
| **Sales Order** | `sales_order` / `_item` / `order_return` / `_item` / `order_refund` / `order_operation_log` / `idempotency_record` = 7 表；3 Controller / 17 Service / 2,838 行；**状态机** `OrderStateTransitionPolicy`、**幂等** `IdempotencyGuard`、价格/商品快照、`SELECT ... FOR UPDATE`、乐观锁；Vue 9 页 | **未迁入** | `t_order` / `_item` / `_log` / `t_refund` 4 表；`SaleOrderService`(221) 含 12 态状态机（confirm/deliver/sign，deliver 逐条扣库存、sign 生成应收）；43 文件 2,386 行；Vue 4 页 1,398 行 |
| **Purchase** | `purchase_order(_item)` / `purchase_demand(_allocation/_generation_batch)` / `purchase_receipt(_item/_confirmation/_confirmation_item)` / `receipt_weighing_record` / `purchase_operation_log` = 11 表；3 Controller / 8 Service / 3,913 行；需求生成/分配、短关闭、收货过磅、**悲观锁 + 乐观锁**；Vue 7 页 | **未迁入** | `t_purchase_order` / `_item` / `t_receive` + P1 的 `t_inquiry` / `_item` / `_inquiry_quote`；6 Controller / 6 Service；79 文件 4,361 行（`ReceiveService` 344 行、`InquiryService` 339 行）；Vue 6 页 2,210 行 |
| **Receiving** | **非独立领域**，寄居 purchase：`purchase_receipt` 系列 + `receipt_weighing_record`（含 `deviceSessionId`）；收货确认 + 过磅 | **未迁入** | **非独立领域**，寄居 purchase：`ReceiveController/Service/Manager`；344 行含收货确认入库、按累计量推进采购单状态 `advancePurchaseOrder`；Vue 收货页 + app 收货页 |
| **Inventory / Stock** | `inventory` / `inventory_movement` 2 表；1 Controller / 2 Service / 544 行；`postPurchaseIn`（MANDATORY 事务）、`selectByWarehouseAndSkuForUpdate` **行锁**、不可变流水；Vue 2 页**只读** | **未迁入** | `t_stock_balance` / `t_stock_flow` / `t_stock_check` / `_item` / `t_stock_adjust` + P1 `t_product_convert` / `_item`；5 Controller / 7 Service；67 文件 3,843 行；`StockOperateManager`(191) 统一入口 + 事务内维护余额并写前后快照流水 + **实时重算加权平均成本**；**无任何并发控制**；Vue 7 页 1,694 行 + 转换页 587 行 |
| **Finance** | **不存在**（0 命中；仅 `settlement` 金额字段散落 order/marketing） | **未迁入** | `t_receivable` / `t_payment` / `t_finance_voucher` / `t_voucher_entry` / `t_external_config` / `t_invoice`；5 Controller / 5 Service；56 文件 2,989 行（`FinanceVoucherService` 239 行）；Vue **仅 2 页** 500 行（缺发票/凭证/外部配置页，菜单引用组件 MISS） |
| **Traceability** | **不存在**（仅 1 个错误码 `TRACE_NOT_FOUND`） | **未迁入** | `t_trace_batch` / `t_trace_code` / `t_trace_inspect`（+ Warn）；4 Controller / 4 Service；33 文件 1,682 行；**Vue 3 个组件全部 MISS**（菜单 1090–1093 引用了不存在的文件） |
| **Mall / Customer Ordering** | `mall_customer_account` / `_session` / `_address` / `mall_cart_item` / `mall_order_address` 5 表；6 Controller / 9 Service / 2,027 行；微信登录、独立会话、结算指纹、下单幂等、地址快照；miniapp 11 页纯客户商城 | **未建设** | **后端无 mall 包**；xsy-app 仅商品浏览 `goods-index.vue`(222)，无购物车/下单/支付 |
| **UniApp / 移动端** | **Taro 4.2.1 + React 18.3.1**，58 文件，11 页（login/home/category/cart/profile + address×2 + product/checkout/order-list/order-detail），zustand 4.5.5，无第三方 UI 库；**0 测试** | **未迁移**（W6 待决策） | **UniApp Vue3 + Pinia**，76 个 .vue / 13,216 行，`pages.json` 25 页；偏**员工端**（订单/采购收货/财务/客户线索）+ 少量客户浏览；tabBar 与"我的"仍是官方 demo |
| **Sorting（分拣）** | **不存在** | 不存在 | **不存在**（仅状态位） |
| **Delivery（配送）** | **不存在** | 不存在 | **不存在**（仅状态位） |
| **Marketing（营销）** | `marketing_coupon` / `_customer_coupon` / `_promotion` / `_home_section` / `_frequent_sku` / `_theme_config` 6 表；1 Controller / 2 Service / 1,517 行；Vue 3 页（仅主题/首页装修，**无券/促销页**） | 未迁入 | **不存在**（仅 `PROMOTION` 空位） |
| **Screen（大屏）** | **不存在** | 不存在 | `screen` 包 18 文件 810 行；**Vue 组件 MISS** |
| **Device（电子秤）** | **无独立领域**（仅 `receipt_weighing_record.deviceSessionId` 字段） | 不存在 | **不存在** |

### 3.2 完成度 / 缺陷 / 改造难度 / 推荐来源

| 领域 | 数据表 | 后端完成度 | Vue 页面完成度 | 小程序完成度 | 自动化测试 | 已知缺陷 | 与当前业务规格冲突 | PG 改造难度 | 推荐来源 |
|---|---|---|---|---|---|---|---|---|---|
| Product | A:3 / B:4 / C:6 | A 完整+已验收 / B **完整+已验收** / C 完整但 `UNVERIFIED` | A 完整 / B 完整 / C 完整 | A 无 / B 无 / C 仅浏览 | A 有 / **B 43 项全绿 + E2E** / **C 0** | C 无 `@Version`、无默认 SKU、无图片表、`skuNo` = "SKU"+自增 id、条码独立表未去重 | **C 全面弱于 W1** | 高（C 需整体重写） | **B/W1 主体**；C 只吸收供应商关联/选择器/页面交互 |
| Customer | A:8 / B:0 / C:4+P1:2 | A 完整 / B 无 / **C 完整但 `UNVERIFIED`** | A 完整 / B 无 / C 4 页 | A 无 / B 无 / C 线索页 | A 有 / B 无 / **C 0** | C 菜单 component 路径全 MISS | C 价格/权限命名与 A/B 不一致 | 高 | **A 规则 + C 业务骨架 + B 基线** |
| Supplier | A:3 / B:0 / C:4 | A 完整 / B 无 / **C 完整但 `UNVERIFIED`**（协同部分文档标 P1） | A **仅占位只读** / B 无 / **C 4 页完整** | A 无 / B 无 / C 无 | A 有 / B 无 / **C 0** | C 协同扩展≠完成 | 供应商绑定/采购拆分/数据范围需按 A/B 重建 | 高 | **C 页面资产 + A 规则 + B 基线** |
| Pricing | A:2+2 / B:0 / C:2+P1:1 | A 完整（四级解析+审计） / B 仅商品价格字段 / C 完整但 `UNVERIFIED` | A 4 页（含批量） / B 无 / C 价格页 | — | A 有 / B 无 / **C 0** | C 价格模型非 W1/V2 合同 | 快照、BigDecimal 4 位、客户层级覆盖需对齐 | 高 | **A 规则 + B W1 精度/审计约束**；C 只吸收页面与字段 |
| Sales Order | A:7 / B:0 / C:4 | A 完整（状态机+幂等+快照+行锁） / B 无 / **C 完整但 `UNVERIFIED`**（无幂等、无行锁、无乐观锁） | A 9 页 / B 无 / C 4 页 | A 11 页商城 / B 无 / C 列表+详情 | A 有 / B 无 / **C 0** | C 无幂等记录、无实重回写证据 | 状态机、实重结算、退款审计必须按 A/B 重建 | 高 | **A 事实 + C 骨架 + B API/迁移规范** |
| Purchase | A:11 / B:0 / C:3+P1:3 | A 完整（需求分配+过磅+悲观锁） / B 无 / **C 完整但 `UNVERIFIED`** | A 7 页 / B 无 / **C 6 页较完整** | A 无 / B 无 / C 收货页 | A 有 / B 无 / **C 0** | C 无跨模块事务与幂等证据 | 需求来源、供应商拆分、幂等需对齐 | 高 | **C 首选业务骨架来源**，A 作规则裁决 |
| Receiving | A:11（含过磅） / B:0 / C:2 | A 完整 / B 无 / **C 完整但 `UNVERIFIED`** | A 3 页 / B 无 / C 收货页 | A 无 / B 无 / C 表单 | A 有 / B 无 / **C 0** | C 实重/部分收货一致性未证明 | 实重、部分收货、入库流水须用 B 事务边界 | 高 | **C 收货流程 + A 实重规则 + B Inventory 约束** |
| Inventory / Stock | A:2 / B:0 / C:5+P1:2 | A 完整（行锁+不可变流水） / B 无 / **C 完整但 `UNVERIFIED`**（**无并发控制**） | A 2 页只读 / B 无 / **C 7 页最完整** | A 无 / B 无 / C 无 | A 有 / B 无 / **C 0** | C 无乐观锁、无并发/幂等验证 | B 禁止静默 UPDATE，需重建事务/事件 key/加权平均 | 高 | **C 作页面/流程资产**；核心规则以 A/B 为准 |
| Finance | A:0 / B:0 / C:6 | A 无 / B 无 / **C 部分 `UNVERIFIED`**（凭证/外部同步是扩展规划） | A 无 / B 无 / **C 仅 2 页** | A 无 / B 无 / C 应收/收款页 | 全无 | C 页面缺口大（3 组件 MISS） | 金额精度、收款幂等、订单/库存结算须按 A/B | 高 | **A 规则 + C 字段/骨架**；先做内部闭环 |
| Traceability | A:0 / B:0 / C:3+ | A 无 / B 无 / **C 后端 `UNVERIFIED`** | A 无 / B 无 / **C Vue 全 MISS** | 全无 | 全无 | C 前端完全缺失 | 批次/收货/库存/扫码关联需按 PG/B 设计 | 高 | **A 规则 + C 后端模型**；暂不迁页面 |
| Mall / 客户下单 | A:5 / B:0 / C:0 | A 完整（登录/会话/指纹/幂等） / B 无 / **C 无** | A 3 页装修 / B 无 / C 无 | **A 11 页完整** / B 无 / C 仅浏览 | A 有 E2E / B 无 / C 无 | C 无商城闭环 | 必须保留 A 的客户身份与订单规则 | — | **A Legacy mall 规则**；C 仅页面/字段参考 |
| UniApp | A 58 文件 / B 0 / C 76 文件 | A Taro+React / B 无 / C UniApp Vue3 | 不适用 | A 11 页 / B 无 / **C 25 页** | A 0 / B 无 / **C 0** | C 员工端为主、客户商城不足；tabBar 仍 demo | 与 A 的 Taro+React 路线不同 | 中-高 | **先保留 A 业务事实**；C 作 W6 候选资产 |
| Sorting / Delivery / Marketing / Screen / Device | — | **三源均无可用实现**（Marketing 仅 A 有） | — | — | — | — | — | — | **按 A 规则 + B 规范新建**（Marketing 优先吸收 A） |

### 3.3 关键判断：Customer / Supplier / Order / Purchase / Stock 是否"成熟到不值得从 legacy 重迁一次"

**结论：不值得。C 不构成"已成熟到可跳过 legacy 迁移"的资产。**

理由（逐条可验证）：

1. **零测试兜底**：C 全部 14 个领域 **0 单测 / 0 DB 集成 / 0 并发 / 0 幂等 / 0 E2E**，唯一测试类是空壳 `AdminApplicationTest`（`@Test` 方法数 = 0）。上述五个领域恰恰是**并发与幂等风险最高**的区域（库存扣减、收货确认、订单状态竞争、收款核销），却完全无验证。
2. **并发控制缺失**：C 全项目 `@Version` = **0 处**；`StockOperateManager` 在事务内读余额→改余额→写流水，**无行锁、无版本号、无分布式锁**。Legacy 对应实现是 `selectByWarehouseAndSkuForUpdate` + 乐观锁 + 独立 `IdempotencyGuard`。这是**能力差距，不是风格差距**。
3. **聚合规则缺失**：C 的 Product/Customer 均为 `SmartBeanUtil.copy(form, Entity.class)` + `updateById` 的平铺 CRUD（实测 `ProductService.update`、`ProductSkuService.update`）。没有聚合校验、没有差量同步、没有跨聚合引用拒绝。而 Legacy 与 W1 在这些点上有明确不变量。
4. **菜单与权限不可直接用**：C 的菜单 seed 存在**两类硬缺陷**——① `xsy_scm_v3.30.0_supply_chain.sql` 引用 **15 个不存在的前端组件**（`/business/customer/discount-list.vue`、`/business/product/barcode-list.vue`、`/business/finance/{voucher,invoice,external-config}-list.vue`、`/business/trace/*-list.vue`、`/business/screen/screen-config-list.vue`、`/business/print/print-template-list.vue`、`/business/external/*-list.vue`）；② `business_module_menu_seed.sql` 与 `stock_module_menu_seed.sql` 的 component 路径 **100% MISS**（写成 `customer/customer/customer-list.vue` 形式，实际布局是扁平的 `customer/customer-list.vue`）。**菜单挂载后必然白屏/404**，不能视为"菜单权限已完成"。
5. **数据库纪律相反**：C 无 Flyway/Liquibase，SQL 靠手工执行 + `sql-update-log` 增量脚本。V2 的 Flyway 是唯一 schema 演进机制且 V6/V7 已哈希锁定。C 的 SQL 不能作为 V2 migration 来源。
6. **平台层不可复制**：C 的平台是 `com.xsy.scm.base` 包名重写的 fork。v3.30→v3.31 对 C 而言不是改版本号，而是源码 diff/merge。

因此正确路径确实是用户设想的：

```text
C 实现（页面/DTO/业务对象/流程骨架）
  ↓ 提取设计意图，不搬代码
迁入当前 V2（net.lab1024.sa.admin.module.scm.<domain>）
  ↓
PostgreSQL / Flyway 化（新 Vn__scm_<domain>.sql，只追加）
  ↓
对齐 Legacy 正确业务规则（状态机/幂等/行锁/快照/精度）
  ↓
补测试（单测 + PG 集成 + 并发 + 幂等 + E2E）
```

**唯一需要修正的措辞**：不是"C 实现 → 迁入"，而是"**C 的页面交互与业务对象 → 参考实现**"。C 的后端聚合逻辑、并发控制与数据库约束**不应迁入**，应直接按 Legacy 规则 + V2 规范重建。

---

## 4. Product 特别审计：C Product 逐项对照当前 W1

**前置声明：不允许因为 C 有 Product 就覆盖 W1。C Product 只作为字段、交互、供应商关联与选择器的参考来源。**

### 4.1 逐项对照（W1 的 20 项要求）

| # | W1 要求 | 当前 V2 W1 落点与证据 | C Product 实测证据 | 结论 |
|---|---|---|---|---|
| 1 | SPU ≥ 1 SKU | `ProductAggregateValidator` + `ProductAggregateValidatorTest`(5) + `ProductPgIT` | `ProductSkuService` 是**独立 CRUD Controller**，SPU 无聚合校验器；`ProductService` 与 `ProductSkuService` 完全解耦 | **W1 强** |
| 2 | 恰好 1 个默认 SKU | 校验器 + `ProductPgIT` | `ProductSkuEntity` **无 `defaultFlag`/`isDefault` 字段**（实测字段仅 skuId/productId/skuNo/specName/unit/unitWeight/status/审计字段）；全项目 `grep -i defaultSku\|isDefault` = **0 命中** | **W1 强（C 无此概念）** |
| 3 | 默认 SKU partial unique | `uk_product_sku_default_active` + `ScmProductMigrationIT` | MySQL 无 partial index；C 全库 `grep -i "WHERE deleted"` = **0 命中** | **W1 强** |
| 4 | SKU 差量同步 | `ProductSkuChangeSet.between` + `ProductSkuChangeSetTest` | 无差量概念；SKU 走独立 add/update/delete 端点 | **W1 强** |
| 5 | 已存在 SKU 保留 id/version | `updated` 组走 `updateById` + `ProductPgIT` + E2E 断言 id 不变 | 无 version 字段；`update` 直接 `updateById` | **W1 强** |
| 6 | 禁止 delete-and-recreate | 仅 `removedIds` 软删 | 前端 SKU 管理走独立增删，存在 delete-and-recreate 风险 | **W1 强** |
| 7 | 跨 SPU SKU id 拒绝 | `between` 未命中 → 40920 + `ProductSkuChangeSetTest` | 无对应校验 | **W1 强** |
| 8 | SKU 编码大小写不敏感 | `normalizeCode` + 测试 | `skuNo` 由 `"SKU" + 自增主键` 事后回填（`skuDao.updateSkuNo`），非业务契约；大小写行为依赖 MySQL collation | **W1 强** |
| 9 | 条码去重 | `trimToNull` + 校验器 + 测试 | 条码在**独立表** `t_product_barcode`，Product 侧无去重契约 | **W1 强** |
| 10 | 规格组合归一化 | `normalizeSpecifications` + 测试 | `specName` 是**单一自由文本字段**（`String specName`，注释"规格/单位 组合"），无结构化组合 | **W1 强** |
| 11 | `@Version` | 4 个 SCM entity 均带 `@Version`；`ScmOptimisticLockTest` 证明生效；`ProductPgIT` 并发冲突用例 | **全项目 `@Version` = 0 处** | **W1 强** |
| 12 | 软删除后编码复用 | 唯一索引均带 `WHERE deleted = FALSE` + `ScmProductMigrationIT` | MySQL 无 partial unique，软删后编码复用行为未定义 | **W1 强** |
| 13 | JSONB 规格 | `JsonbStringMapTypeHandler` + `ProductPgIT` | 全库 `grep -i jsonb` = **0 命中**；规格为普通文本字段 | **W1 强** |
| 14 | BigDecimal 4 位契约 | `ScmFixedScale4Serializer`（HALF_UP，null 保持 null）+ `ScmBigDecimalNullSemanticsTest`(7) | 文档规定 `DECIMAL(18,4)`/`DECIMAL(18,2)`，Java 用 BigDecimal；**无端到端 4 位/舍入模式测试** | **W1 强** |
| 15 | 商品图片 | `product_image` 表（仅 SPU 图集）+ `ProductImageChangeSet` + `ProductImageChangeSetTest` + `uk_product_image_primary_active` | **无图片表**；`ProductEntity.mainImage` 仅单个 String（文件服务地址）；主图/图集/主图唯一性均无 | **W1 强** |
| 16 | Vue 页面 | `product-list.vue`、`product-detail.vue`（隐藏路由可深链）、`product-form-drawer.vue`、`product-sku-editable-table.vue`、`product-image-upload.vue`、分类 3 件套 | 4 列表页 1,424 行 + `goods`/`catalog` 6 文件 989 行；**交互组织更丰富**（含供应商关联页、条码页、价格页） | **C 可选择性吸收交互** |
| 17 | 权限 | `scm:product:*` + `@SaCheckPermission` + `@OperateLog` + 只读角色 E2E 断言 | 有 `@SaCheckPermission`（product 29 次）+ `v-privilege`，命名 `product:*` | **映射后吸收权限清单** |
| 18 | E2E | `e2e/scm-product.spec.ts` 2 passed（主流程 + 只读角色） | **0 E2E**（无 playwright/cypress 配置） | **W1 明显更强** |
| 19 | 分类三级 + 启用校验 | `ProductCategoryService.resolveLevel` + `ProductCategoryLevelTest` + `ProductPgIT` | 有 `ProductCategoryService`(81 行)，未见等价层级校验测试 | **W1 强** |
| 20 | 列表不 N+1 / 搜索覆盖 | 批量取 SKU/图片/分类内存分组 + `ILIKE`/`EXISTS` + `ProductPgIT` | 未见等价查询优化证据 | **W1 强** |

### 4.2 明确结论

**值得补回当前 W1 的 C Product 能力（仅设计意图，不搬代码）：**

1. **供应商关联的组织方式**：C 的 `ProductSupplierController/Service/Manager` + `ProductSupplierVO` + 独立列表页，展示了"商品-供应商关系（含默认供应商、供货价、采购方式）"的字段组织与页面布局。W1 当前只在 SPU 上有 `defaultSupplierId`/`defaultPurchaseMode`，**没有商品-供应商多对多关系表**。这是 W1 的真实缺口，建议在 W2/W3 采购域立项时按 Legacy `supplier_sku` 规则补，而非照搬 C。
2. **条码独立表与扫码作业设计**：C 的 `t_product_barcode` + `ProductBarcodeService` 面向"扫码作业"（分拣/订单助手），与 Legacy 的 SKU 条码字段定位不同。建议记录为设计输入，待分拣域立项时评估。
3. **商品选择器 / 分类选择器组件**：C 前端有可复用的商品/分类选择交互，可参考其 props 形状。
4. **价格多级页面的信息架构**：C 的 `ProductPrice` 四级（基础/分级/时价/协议价）页面结构，与 Legacy `CustomerPriceResolver` 的四级解析语义**方向一致**，可作为客户定价域的信息架构输入（规则仍以 Legacy 为准）。
5. **与采购页面对接时的展示字段**：C 的采购/收货页面直接消费商品与 SKU 字段，可反推"商品列表需要向采购域暴露哪些字段"。

**明显弱于当前 W1 的 C Product 能力（不得回迁）：**

- MySQL 数据约束体系（无 partial unique、无 CHECK）
- 默认 SKU 概念（**完全不存在**）
- SKU 差量同步 / 保留 id-version / 跨 SPU 拒绝
- 乐观锁（**全项目 0 处**）
- 规格组合归一化与结构化规格（仅自由文本）
- SKU 编码契约（"SKU"+自增 id，非业务编号）
- 条码去重契约
- 商品图片能力（无表、无主图唯一性）
- BigDecimal 4 位精度与舍入契约的自动化证明
- E2E 证据

**唯一推荐：保留 W1 Product 实现，逐项吸收上述 5 条设计意图。不得用 C Product 替换 W1。** 逐项对照已证明 C 在 20 项要求中 **0 项优于 W1**（16 项明确弱，4 项为"交互/权限清单可参考"但非能力更强）。

---

## 5. C 的 SmartAdmin API 依赖与 v3.30 → v3.31 适配面

**前提：不允许简单复制 C 的系统层。** 只分析业务代码对平台 API 的依赖。

### 5.1 业务代码对 SmartAdmin API 的依赖实测

业务代码 = `com.xsy.scm.admin.module.business.**`（575 Java / 51 Controller）。

| API / 类 | C 业务使用量 | 典型调用方式 | v3.31 适配判断 |
|---|---|---|---|
| `ResponseDTO` | 105 文件；`ok` 291、`userErrorParam` 115、`error` 9、`okMsg` 5 | `ResponseDTO.ok(...)`、`ResponseDTO.userErrorParam("...")` | **低**。方法集稳定；需核对 `userErrorParam`/`okMsg` 是否保留 |
| `PageResult` | 245 引用 / 95 文件；**无 `new`**，全部经 `SmartPageUtil.convert2PageResult` | `ResponseDTO<PageResult<XxxVO>>` | **低-中**。字段 `emptyFlag` 若变更需同步前端 |
| `PageParam`（分页基类） | 50 文件 `extends PageParam` | `class XxxQueryForm extends PageParam` | **低**。注意：C 中**不存在** `SmartPageQuery`/`SmartPageParam`（v3.30 无此类） |
| `SmartPageUtil` | 48 文件 / `convert` 100 次 | `SmartPageUtil.convert2PageResult(page, list, XxxVO.class)` | **中**。含 SQL 注入校验逻辑，签名变更将波及 48 文件 |
| `SmartRequestUtil` | 5 文件 / 7 次（`getRequestUserId` 4、`getRequestUser` 3） | `SmartRequestUtil.getRequestUserId()` | **中**。若 v3.31 改为 Sa-Token 原生取用户，属高危签名点 |
| Sa-Token `@SaCheckPermission` | **272 次**（purchase 37 / customer 31 / product 29 / stock 28） | `@SaCheckPermission("product:sku:add")` | **中**。注解稳定；权限码字符串必须与菜单 seed 一致 |
| `StpUtil` | **0** | — | 无依赖 |
| `@OperateLog` | **3 次**（仅 oa 的 Enterprise/Invoice/NoticeController） | `@OperateLog` | **低**。覆盖率极低 |
| `@DataScope` | **0** | 平台仅有 `DataScopePlugin.java`，无注解 | 无依赖；**C 业务完全没有数据范围控制** |
| File（`FileService`/`FileController`/`SmartFileUtil`） | **0 直接调用**；仅 `@JsonSerialize(using = FileKeyVoSerializer.class)` 4 文件 | 序列化注解方式 | **中**。文件模块若重构，`FileKeyVoSerializer/Deserializer` 路径会变 |
| Dict | `DictService` 1 文件（2 处）、`DictDataDeserializer` 2 文件、`@Dict` **0** | `dictService.getDictDataLabel(...)` | **中**。直接注入 Service 而非走缓存 |
| `SmartBeanUtil` | 49 文件（`copy` 94、`copyList` 8、`copyProperties` 1） | `SmartBeanUtil.copy(form, Entity.class)` | **低** |
| `SmartJob` | 业务 **0**（仅 system 模块） | — | **低** |
| `SerialNumberService` | 16 文件 / `generate` 17 次（purchase 6、finance 3） | `serialNumberService.generate(SerialNumberIdEnum.XXX)` | **中**。依赖 `SerialNumberIdEnum` 常量集，枚举增减即编译失败 |
| `DataTracer` | `@DataTracer` 29、`@DataTracerFieldLabel` 28（**全部集中在 oa 模块**） | `@DataTracer(type=..., businessId=...)` | **低-中**。仅 1 个模块使用 |
| `BusinessException` | 35 引用 / 10 文件 | `new BusinessException("收款单不存在")` | **低**。注意：C 直接用字符串，**未走模块错误码常量** |
| `UserErrorCode` | 7 次（`DATA_NOT_EXIST`） | `ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST)` | **低** |
| `@RepeatSubmit` | **2 次**（NoticeController:86,95） | `@RepeatSubmit` | **低** |
| `@Transactional` | 277 次（业务自有，非平台 API） | — | — |

前端（`xsy-scm-web`）：

| 项 | 使用量 | 适配判断 |
|---|---|---|
| `TableOperator` | 111 引用 / 56 文件 | **中**。依赖 `TABLE_ID_CONST` 常量体系 |
| `v-privilege` | 272 引用 / 58 文件 | **中**。与后端 `@SaCheckPermission` 强耦合 |
| `SmartLoading` | 455 引用 / 94 文件 | **低** |
| 全局插件 | `dict-plugin` / `privilege-plugin` / `smart-enums-plugin` | **中** |

### 5.2 是否需要适配 v3.31（结论）

| 类别 | 结论 |
|---|---|
| **需要适配的（高优先）** | `SmartPageUtil`（48 文件，签名+注入校验）、`SmartRequestUtil`（5 文件，取用户方式）、`SerialNumberIdEnum`（16 文件，枚举集合）、`FileKeyVoSerializer/Deserializer` + `DictDataDeserializer`（跨包隐式契约，6 文件） |
| **需要重命名的（业务侧）** | 272 处 `@SaCheckPermission` 权限码（`customer:*` → `scm:customer:*`）与 272 处 `v-privilege`；`TABLE_ID_CONST` 常量 |
| **基本无需改动的** | `ResponseDTO`、`PageResult`、`PageParam`、`SmartBeanUtil`、`BusinessException`、`@OperateLog`、`@RepeatSubmit` |
| **C 完全没有的能力（V2 需补齐）** | 数据范围 `@DataScope`（C 业务 0 依赖，Legacy 有 RBAC + 数据范围）、模块级错误码常量（C 用裸字符串）、`@OperateLog` 覆盖（C 仅 3 处）、`DataTracer` 覆盖（C 仅 oa）、`SmartJob`（C 业务 0） |
| **未发现"绕过平台自造轮子"** | C 业务代码中**无**自建 `Result`/`Response`/`Exception`/`Page` 类；无自写权限判断；无 `StpUtil` 手工取用户；分页统一走 `SmartPageUtil`；前端无自建请求封装（统一 `src/lib/axios.ts`）。这一点 C 做得**符合规范**，值得肯定 |

### 5.3 v3.30 → v3.31 的真正成本

由于 C 的平台是**包名重写的内嵌 fork**（`com.xsy.scm.base`），迁移不是"升级依赖版本"，而是"把官方 v3.31 源码 diff/merge 进已重命名的 fork"或"重新执行包名重写"。**这使方案 B 的 SmartAdmin 升级成本远高于普通版本升级。**

---

## 6. 测试质量评价（按业务风险，不按文件数量）

### 6.1 三源测试实况

| 维度 | A · Legacy | B · 当前 V2 | C · SmartAdmin-old |
|---|---|---|---|
| 后端测试文件 | **75 个**（含 **37 个 `*IT`**） | **10 个**（SCM 域） | **1 个**（空壳 `AdminApplicationTest`） |
| 后端测试用例 | **371** 个（`@Test`/`@ParameterizedTest`），9,219 行 | **43** 个（全绿） | **0** 个（`@Test` 方法数 = 0） |
| 前端测试 | **17 个** Vitest（44 用例） | E2E 2 用例 | **0** |
| E2E | **4 个 Playwright spec** | `e2e/scm-product.spec.ts`（2 passed） | **0**（无 playwright/cypress 配置） |
| 小程序测试 | **0** | — | **0** |
| CI | 有测试但未见 CI 描述 | — | **0**（无 `.github/workflows`、`.gitlab-ci.yml`、`Jenkinsfile`） |
| 测试脚手架 | 有（`DatabaseSecurityActor`、`IsolatedUserDatabase` 等辅助类） | 有（测试基类 + PG IT + fixture） | **无**（无基类、无工具类、无数据构造器、无 H2/Testcontainers；`spring-boot-starter-test` 已引入但未使用） |

### 6.2 按业务风险逐领域评价

| 业务领域 | 单测 | DB 集成 | 并发 | 幂等 | E2E | 风险定级 | 状态标记 |
|---|---|---|---|---|---|---|---|
| Product | A ✅ / B ✅ / **C ❌** | A ✅ / B ✅ / **C ❌** | A ✅ / B ✅ / **C ❌** | A ✅ / B 部分 / **C ❌** | A ✅ / B ✅ / **C ❌** | 高 | C：`FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED` |
| Customer / Pricing | A ✅ / B ❌ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | **极高**（价格错误直接资损） | C：**UNVERIFIED** |
| Order | A ✅（状态机/幂等/金额） / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅（`IdempotencyGuard`） / **C ❌** | A ✅ / **C ❌** | **极高** | C：**UNVERIFIED** |
| Purchase / Receiving | A ✅（过磅/乐观锁 `ProcurementOptimisticLockIT`） / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | **极高** | C：**UNVERIFIED** |
| Inventory / Stock | A ✅（行锁过账） / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | **极高**（C 无并发控制） | C：**UNVERIFIED** |
| Finance | A ❌ / **C ❌** | A ❌ / **C ❌** | ❌ | ❌ | ❌ | 高 | C：**UNVERIFIED** |
| Supplier | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | A ✅ / **C ❌** | ❌ | 中 | C：**UNVERIFIED** |
| Marketing | A 部分 / C ❌ | A 部分 / C ❌ | ❌ | ❌ | ❌ | 中 | C：**不存在** |
| Mall | A ✅（E2E `sprint5-mall-pricing`） / C ❌ | A ✅ / C ❌ | A ✅ / C ❌ | A ✅（结算指纹） / C ❌ | A ✅ / C ❌ | 高 | C：**不存在** |
| Trace / Screen / External / Print / Goods / Category / OA | C 全 ❌ | ❌ | ❌ | ❌ | ❌ | 低-中 | C：**UNVERIFIED** |
| UniApp（A miniapp / C xsy-app） | ❌ | ❌ | ❌ | ❌ | ❌ | 中 | 两端均 **UNVERIFIED** |

### 6.3 结论

- **C 的 14 个领域全部标记 `FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED`**，其中 `stock` / `purchase` / `order` / `customer-pricing` 为**高风险未验证区**：合计 277 处 `@Transactional`，但**没有任何并发或幂等验证**。
- **Legacy 是唯一具备真实回归网的信息源**：371 个后端用例 + 37 个 `*IT` + 4 个 Playwright spec，覆盖订单状态机、幂等、金额、收货过账与乐观锁、价格解析优先级、RBAC 事务、Flyway 迁移。
- **B 的 W1 是质量标杆**：43 项测试（含 `ScmOptimisticLockTest` 证明 `@Version` 真实生效、`ProductPgIT` 19 项、`ScmProductMigrationIT`、BigDecimal null 语义 7 项）+ E2E 2 用例 + 冻结复验。**W2 后续领域应复刻这套测试范式，而不是降低门槛。**
- 后续领域最低质量门槛（沿用 W1 标准）：单测（状态转换/精度/聚合校验）+ PG 集成（Flyway/唯一约束/partial unique/流水）+ 并发（库存扣减/收货确认/状态竞争/乐观锁）+ 幂等（外部下单/收货确认/库存入账/收款退款）+ E2E（只覆盖关键主链路）。

---

## 7. MySQL → PostgreSQL 专项

### 7.1 扫描范围

| 项 | 数量 |
|---|---|
| `.sql` 文件 | 19 个 / 5,363 行 |
| MyBatis Mapper XML | **87 个 / 4,743 行**（100% 含手写 SQL；285 条语句标签：`<select>`205 / `<update>`62 / `<delete>`14 / `<insert>`4；73 个含动态 SQL） |
| Java 内嵌 SQL（`@Select`/`@Update`/`@Insert`/`@Delete`） | **0 处**（仅 `DataSourceConfig.java:116` 有 `SELECT 1` 探活） |
| Java 文件 | 1,056 个 / 63,717 行 |

### 7.2 逐项搜索结果（用户指定清单 + 额外发现）

| # | 特性 | 命中数 | 主要位置 | 代表性片段 |
|---|---|---:|---|---|
| 1 | `AUTO_INCREMENT` | **225** | xsy_scm 94 / smart_admin 69 / 07-P1 23 | `) ENGINE = InnoDB AUTO_INCREMENT = 381` |
| 2 | `IFNULL` | **6** | `ScreenDataMapper.xml`(5)、`ExternalConfigMapper.xml`(1) | `IFNULL(SUM(actual_amount), 0) AS salesAmount` |
| 3 | `GROUP_CONCAT` | **0** | — | — |
| 4 | `FIND_IN_SET` | **0** | — | — |
| 5 | `DATE_FORMAT` | **49**（XML） | `NoticeMapper.xml`(16)、`Enterprise/Message/HelpDoc`(各 4) | `AND DATE_FORMAT(create_time,'%Y-%m-%d') >= #{query.startDate}` |
| 6 | `LIMIT offset,count` | **0** | 全部为 `LIMIT 1` / `LIMIT #{limit}`（23 处） | — |
| 7 | `JSON_EXTRACT` | **0** | — | — |
| 8 | `JSON_CONTAINS` | **0** | — | — |
| 9 | `ON DUPLICATE KEY` | **0** | — | — |
| 10 | `unsigned` | **20** | smart_admin 9 / xsy_scm 9 / v3.25.0 2 | `price decimal(10,2) UNSIGNED NOT NULL` |
| 11 | `enum(` 列类型 | **0** | DDL 全用 `tinyint` 表示状态 | — |
| 12 | `tinyint` | **267** | xsy_scm 102 / 07-P1 60 / smart_admin 37 | `disabled_flag tinyint unsigned NOT NULL COMMENT '0否1是'` |
| 13 | `datetime` / `DATETIME` | 大量（`DEFAULT CURRENT_TIMESTAMP` 324 处） | 全部 DDL | `create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| 14 | `CREATE TRIGGER` | **0** | — | — |
| 15 | `CREATE PROCEDURE/FUNCTION` / `DELIMITER` | **0** | — | — |
| 16 | `ENGINE=InnoDB` | 15（`ROW_FORMAT` 116） | smart_admin 5 / xsy_scm 5 / update-log | `) ENGINE = InnoDB ... ROW_FORMAT = Dynamic` |
| 17 | `utf8mb4` | **1,233** | xsy_scm 626 / smart_admin 418 | `CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci` |
| 18 | `ON UPDATE CURRENT_TIMESTAMP` | **154** | xsy_scm 63 / smart_admin 38 / 07-P1 23 | `update_time DATETIME ... ON UPDATE CURRENT_TIMESTAMP` |
| 19 | 反引号标识符 | **9,338**（SQL 9,326 / XML 12） | xsy_scm 4,068 / smart_admin 2,416 / 07-P1 998 | `` CREATE TABLE `t_category` `` |
| 20 | `INSERT IGNORE` | **30** | `demo_data.sql` 22 / `business_module_menu_seed.sql` 5 / `stock_module_menu_seed.sql` 3 | `INSERT IGNORE INTO t_menu ...` |
| 21 | `REPLACE INTO` | **0** | — | — |
| 22 | `CONVERT(... USING ...)` | **0** | — | — |
| 23 | `SUBSTRING_INDEX` | **0** | — | — |
| 24 | `STRAIGHT_JOIN` | **0** | — | — |
| 25 | `SQL_CALC_FOUND_ROWS` | **0** | MyBatis-Plus 分页 | — |
| 26 | `LAST_INSERT_ID` | **0** | `IdType.AUTO` 81 处 + 回填 | — |
| 27 | `SHOW TABLES/COLUMNS` / `DESCRIBE` | **0** | 改用 `information_schema` | — |
| 28 | `BIT` 类型 | **0** | — | — |
| 29 | `YEAR` 类型 | **0** | — | — |
| 30 | `ZEROFILL` | **0** | — | — |
| 31 | `SET NAMES` | **2** | `smart_admin_v3.sql:13`、`xsy_scm.sql:13` | `SET NAMES utf8mb4;` |
| 32 | `LOCK TABLES` | **0** | — | — |
| + | `COLLATE` | 617 | 全部 DDL 尾部 | `COLLATE = utf8mb4_general_ci` |
| + | `COMMENT '...'` | **1,792** | 全部 DDL | 列/表注释 |
| + | `USING BTREE` | 227 | xsy_scm / smart_admin | `KEY \`idx_x\`(...) USING BTREE` |
| + | **`INSTR(...)`**（用户清单外，重点） | **117** | 46 个 XML：`NoticeMapper`(17)、`HelpDocDao`(7)、`EnterpriseMapper`(4) | `AND ( INSTR(t_change_log.content, #{keyword}) ...` |
| + | `information_schema` + `database()` | 3 | `CodeGeneratorMapper.xml` | `where table_schema = (select database())` |
| + | `DATEDIFF` / `CURDATE` | 1 / 1 | `TraceWarnMapper.xml:13` | `DATEDIFF(qualification_expire_date, CURDATE())` |
| + | `CONCAT` / `COALESCE` / `NOW()` | 2 / 1 / 3(XML) | Payment/Receivable、SaleOrderItemMapper | `LIKE CONCAT('%', #{no}, '%')` |

> **重大利好**：`GROUP_CONCAT` / `FIND_IN_SET` / `JSON_EXTRACT` / `JSON_CONTAINS` / `ON DUPLICATE KEY` / `REPLACE INTO` / 触发器 / 存储过程 / `LIMIT offset,count` / `LAST_INSERT_ID` **全部为 0**。C 的 SQL 属于"宽但浅"的 MySQL 方言使用。

### 7.3 四类分类与改造工作量

| 类别 | 规模 | 代表性内容 |
|---|---|---|
| **A. 可直接兼容** | SQL 约 4,200 行主体；285 条语句中的 ~250 条 | `ORDER BY`(109)、`GROUP BY`、`HAVING`、`LIKE`、`CASE WHEN`、`COALESCE`、`NOW()`、`LIMIT n`、MyBatis 动态标签、MyBatis-Plus Wrapper |
| **B. 简单语法转换（机械替换）** | 反引号 9,338 · `utf8mb4`/`COLLATE` 1,850 · `ENGINE`/`ROW_FORMAT`/`USING BTREE` 358 · `COMMENT` 1,792 · `AUTO_INCREMENT` 225 · `tinyint` 267 · `unsigned` 20 · `INSERT IGNORE` 30 · `SET NAMES` 2 · `IFNULL` 6 | `AUTO_INCREMENT`→`GENERATED BY DEFAULT AS IDENTITY`；`tinyint`→`smallint`/`boolean`；`datetime`→`timestamp`；`longtext`→`text`；`INSERT IGNORE`→`ON CONFLICT DO NOTHING`；`COMMENT 'x'`→`COMMENT ON COLUMN` |
| **C. 强 MySQL 依赖（需重写逻辑）** | **`INSTR` 117（46 文件）** · `DATE_FORMAT` 49（14 文件） · `information_schema` + `database()` 3 · `DATEDIFF`/`CURDATE` 2 · `ON UPDATE CURRENT_TIMESTAMP` 154 · `IdType.AUTO` 81 + `AUTO_INCREMENT` 225 · 分页方言 `DbType.MYSQL` 2 处 | `MybatisPlusConfig.java:29` `new PaginationInnerInterceptor(DbType.MYSQL)`；`DataSourceConfig.java:105` `setDbType(DbType.MYSQL)`；`CodeGeneratorMapper.xml` 整个代码生成器元数据查询 |
| **D. PostgreSQL 可优化** | 索引 227（`USING BTREE`→默认 B-tree，可升级 GIN/部分索引/表达式索引）· 状态位 `tinyint`→`boolean`（Java 侧 `deletedFlag` 已是 `Boolean`）· `DATE_FORMAT` 在 WHERE 中包裹列 → 改范围查询可走索引 · `INSTR` → `strpos` / `pg_trgm` · `unsigned` → `CHECK` / `numeric` · 注释纳入 Flyway 版本管理 | 客户/商品可见性、订单状态、采购需求、库存流水、溯源批次适合 partial unique + `jsonb` + 复合索引 + `CHECK` + 事件 key |

### 7.4 建表规模

| 归属 | 数量 | 文件 |
|---|---:|---|
| `CREATE TABLE` 总数 | **167** | 全部 `.sql` |
| SmartAdmin 官方系统表 | **44** | `数据库SQL脚本/mysql/smart_admin_v3.sql` |
| 官方增量脚本 | 9 | `sql-update-log/v3.15.0/3.21.0/3.23.0/3.25.0`（v3.18/26/30 为纯 INSERT/ALTER） |
| 运行时落地脚本业务表 | **25** | `deploy/sql/xsy_scm_v3.30.0_supply_chain.sql`（第 1317–2076 行） |
| 设计文档业务表（P0） | **22** | `docs/database/01~05*.sql` |
| 设计文档业务表（P1 草案） | **23** | `docs/database/07-补充模块P1.sql` |
| **业务表去重合计** | **47** | 25（运行时）+ 22（P0 设计，Mapper 已引用） |
| 业务表 DDL 总行数 | **1,953** | `deploy/sql` 业务段 760 + `docs/database/*.sql` 1,193 |

**47 张业务表清单**

- 运行时脚本 25 张：`t_receivable` `t_payment` `t_supplier_account` `t_supplier_product_apply` `t_supplier_manufacturer` `t_supplier_statement` `t_inquiry` `t_inquiry_item` `t_inquiry_quote` `t_product_convert` `t_product_convert_item` `t_customer_discount` `t_customer_product_alias` `t_product_barcode` `t_print_template` `t_screen_config` `t_finance_voucher` `t_voucher_entry` `t_external_config` `t_invoice` `t_trace_batch` `t_trace_inspect` `t_trace_code` `t_external_mapping` `t_external_sync_log`
- P0 设计 22 张（Mapper 已引用，DDL 仅在 `docs/database/`）：`t_product_category` `t_product` `t_product_sku` `t_product_price` `t_product_supplier` `t_customer` `t_customer_period` `t_customer_goods_visible` `t_customer_qrcode` `t_order` `t_order_item` `t_order_log` `t_refund` `t_supplier` `t_purchase_order` `t_purchase_item` `t_receive` `t_stock_balance` `t_stock_flow` `t_stock_check` `t_stock_check_item` `t_stock_adjust`

### 7.5 改造工作量估算

| 工作面 | 规模 | 估算 |
|---|---|---|
| DDL 改造 | 167 张表 / 5,363 行；处理反引号 9,326、`COLLATE` 617、`COMMENT` 1,792、`USING BTREE` 227、`AUTO_INCREMENT` 225、`tinyint` 267、`ON UPDATE CURRENT_TIMESTAMP` 154、`INSERT IGNORE` 30 | **12–18 人日**（脚本化改写 + 人工抽检） |
| Mapper XML 改造 | 87 文件 / 4,743 行，其中 **52 文件需改**；`INSTR` 117、`DATE_FORMAT` 49、`IFNULL` 6、`information_schema` 3 | **10–15 人日**（`INSTR` 与 `DATE_FORMAT` 占 80%） |
| Java 代码改造 | 1,056 文件 / 63,717 行，**内嵌 SQL = 0**；实际只需改 3 处配置：`MybatisPlusConfig.java:29`、`DataSourceConfig.java:105`、`pom.xml` 驱动 | **1–2 人日** |
| 基础设施 | `sa-base.yaml` JDBC URL、`docker-compose.db.yml`、Flyway 基线 | **1–2 人日** |
| **机械合计** | — | **24–37 人日** |

**但必须叠加以下非机械成本（这才是真实成本）**：
- 47 张表逐表 schema 评审（partial unique / CHECK / JSONB / 索引重设计）：**每领域至少 1 次评审**
- 实体/枚举/唯一性/并发/幂等重建（C 无 `@Version`、无幂等记录、无行锁）
- 测试补齐（14 个领域从 0 到有）
- 与 Legacy 规则对齐（状态机、价格快照、实重、加权平均）
- 数据迁移（若需从 C 的 MySQL 存量迁移）

**结论：机械改造 24–37 人日；连同实体、XML 查询、分页、枚举、唯一性、并发、幂等、测试与规则对齐，整体为高工作量。**

**最难的 5 个具体点：**

1. **`INSTR(...)` 117 处 / 46 个 XML** —— PostgreSQL 无 `INSTR`，语义为"位置 > 0 即匹配"，需逐条改写为 `strpos(col, #{x}) > 0` 或 `col LIKE '%' || #{x} || '%'`。代表：`mapper/business/oa/notice/NoticeMapper.xml`(17)、`mapper/support/HelpDocDao.xml`(7)、`mapper/business/oa/enterprise/EnterpriseMapper.xml`(4)。
2. **`information_schema` + `database()` 代码生成器** —— `xsy-scm-base/src/main/resources/mapper/support/CodeGeneratorMapper.xml` 3 处元数据查询（`table_schema = (select database())`、`ordinal_position`、反引号别名 `` `tables` ``）。PG 需改 `table_schema = current_schema()`，且 `database()` **无等价函数**。
3. **`DATE_FORMAT(col,'%Y-%m-%d')` 49 处 / 14 文件** —— 格式串需转 `TO_CHAR(col,'YYYY-MM-DD')`；且该写法本身阻断索引，`NoticeMapper.xml`(16)、`MessageMapper.xml`/`HelpDocDao.xml`/`EnterpriseMapper.xml`(各 4)。
4. **主键与时间戳语义链** —— `AUTO_INCREMENT` 225 + `ON UPDATE CURRENT_TIMESTAMP` 154 + Java `IdType.AUTO` 81 + `MybatisPlusFillHandler`：PG 需 `IDENTITY` 序列 + 触发器/生成列补齐 `update_time`，并与 `MybatisPlusConfig.java:29` 的 `DbType.MYSQL` 分页方言（`LIMIT ?,?`）同步切换为 `DbType.POSTGRE_SQL`（`LIMIT ? OFFSET ?`）。
5. **`INSERT IGNORE` 幂等种子脚本 30 处** —— `demo_data.sql`(22)、`business_module_menu_seed.sql`(5)、`stock_module_menu_seed.sql`(3) 需整体改为 `INSERT ... ON CONFLICT DO NOTHING`，并确认目标表存在对应唯一约束（否则 `ON CONFLICT` 无目标，需退化处理）。

**补充风险**：C 的 `AGENTS.md` §10.5 明文要求"避免无必要使用某一数据库独有语法"，但现存代码大量使用 `INSTR`/`DATE_FORMAT`，**规范与实现存在偏差**；迁移时应一并清理，避免二次返工。

---

## 8. 方案 A / 方案 B 评分

**方案 A**：以当前 `v2/**` 为主干，按领域吸收 `project-reference-examples/xsy-scm` 的业务实现。
**方案 B**：以 `project-reference-examples/xsy-scm` 为主干，反向迁入 SmartAdmin v3.31 / Java 21 / PostgreSQL / Flyway / W0 / W1 Product / 测试体系。

评分 1～10 分，**分数越高越有利**。对"迁移风险、数据库迁移成本、SmartAdmin 升级成本、测试补齐成本"采用**成本越低分越高**的方向。

| 维度 | 方案 A（V2 主干吸收 C） | 方案 B（C 主干反向迁 V2/W0/W1） | 评分依据 |
|---|---:|---:|---|
| 剩余开发量 | **8** | 4 | A 需新建 8+ 领域，但 C 提供页面/DTO 骨架，且 W1 已跑通范式；B 已有大量业务代码，但要重做平台层与测试 |
| 迁移风险 | **9** | 2 | A 不触碰已验收 W1，纯增量，风险可控；B 要把已验收资产降级为待迁移对象，且 C 零测试无回归网 |
| 业务完整度 | 5 | **8** | A 当前只有 Product；B 已有 customer/purchase/stock/order/finance 等业务代码 |
| 业务正确性 | **9** | 3 | A 以 Legacy（371 用例 + 状态机 + 幂等 + 行锁）为事实源；B 继承 C 的平铺 CRUD、0 `@Version`、无幂等、无行锁 |
| 数据库迁移成本 | **9** | 2 | A 只需为每个新领域追加 Flyway migration；B 要处理 167 张表 / 47 业务表 / 9,338 反引号 / 117 `INSTR` / 49 `DATE_FORMAT` / 分页方言 / `INSERT IGNORE` |
| SmartAdmin 升级成本 | **10** | 2 | A 已是 v3.31，成本 0；B 面对"包名重写 fork 的源码 diff/merge"，远高于普通版本升级 |
| Vue 复用率 | 7 | **8** | 两者都是 Vue3 + AntD Vue 4；B 直接拥有 55 个业务页面，但路由/权限命名需重写 |
| UniApp 复用率 | 6 | **8** | B 的 xsy-app 就是 UniApp Vue3（76 文件 / 25 页）；A 需 W6 单独决策（Legacy 是 Taro+React） |
| 测试补齐成本 | **7** | 2 | A 有 W1 测试范式与工具链可复刻；B 需从 0 建测试体系，14 个领域全 `UNVERIFIED` |
| 长期维护性 | **9** | 3 | A 单一主干、单一 migration 机制、单一包名、单一权限体系；B 双基线分叉、包名重命名、无 migration 纪律 |
| **总分** | **81** | **42** | — |

### 唯一推荐：**方案 A**

**为什么：**

1. **不可逆资产保护**：W1 Product 已正式验收（43 项测试全绿 + E2E 2 用例 + 冻结复验 + V6/V7 哈希锁定）。方案 B 会把这份资产重新变成"待迁移对象"，且 C 的 Product 在 20 项要求中 **0 项优于 W1**（§4）。
2. **正确性来源不可替换**：Legacy 是业务规则事实源，具备 371 个后端用例 + 37 个 `*IT` + 4 个 Playwright spec，覆盖状态机、幂等、金额、过磅、乐观锁、价格解析、RBAC。C 全部 14 个领域 0 测试、0 `@Version`、无幂等记录、无行锁。**B 是用无验证的实现去替换有验证的规则。**
3. **技术基线不可回退**：V2 已是 SmartAdmin v3.31 + Java 21 + PostgreSQL + Flyway。C 是 v3.30 + Java 17 + MySQL + 无 migration 工具，且平台层是包名重写 fork。B 需要承担 167 张表 / 47 业务表的 PG 改造（24–37 人日机械 + 高额非机械成本）与源码 merge 风险。
4. **风险不对称**：A 的风险是"开发量"，可用 W1 范式线性推进；B 的风险是"正确性与回归"，一旦库存/订单/价格出错直接资损，且无测试兜底。
5. **B 的高分项（业务完整度、Vue/UniApp 复用率）可以在 A 内被吸收**：C 的页面与业务对象作为**参考实现**进入 V2，同样能拿到复用收益，却不必继承它的平台债与数据库债。

---

## 9. 模块处置清单

### 9.1 应该直接复用 / 优先吸收 C 的模块

| 模块 | 吸收内容 | 吸收方式 |
|---|---|---|
| **Customer** | 客户档案 / 账期 / 商品可见性 / 二维码 / 折扣 / 别名的**页面字段组织**与 DTO 形状；Vue 4 列表页的交互结构 | 参考设计，代码重写为 `module/scm/customer`；规则以 Legacy `customer` 域 8 表 + `CustomerPriceResolver` 为准；权限改 `scm:customer:*` |
| **Supplier** | 供应商 / 厂商 / 报品 / 对账单的页面与业务对象；供应商协同的信息架构 | 参考设计；规则以 Legacy `supplier`/`supplier_sku`/`warehouse` 为准 |
| **Purchase** | 采购需求 / 询价 / 采购生成 / 采购明细 / 收货的**流程骨架与页面**（C 的 6 页较完整） | 首选骨架来源；事务边界、幂等、悲观锁按 Legacy 重建 |
| **Receiving** | 收货列表/表单的交互与字段 | 参考设计；实重、部分收货、入库流水必须走 V2 事务边界 |
| **Stock** | 库存余额/流水/盘点/调整/规格转换的**页面交互**（C 的 7 页 + 转换页最完整） | 页面资产优先吸收；核心规则按 Legacy 行锁过账 + 不可变流水重建，**禁止照搬 C 的无锁实现** |
| **Order** | 订单列表/明细/退款/日志的交互与状态对象候选 | 参考设计；状态机、实重、幂等按 Legacy `OrderStateTransitionPolicy` + `IdempotencyGuard` 重建 |
| **Finance** | 应收/收款/发票/凭证的字段与页面骨架 | 参考设计；先做内部业务闭环，不把外部凭证同步当已完成 |
| **Product（辅助）** | ① 商品-供应商关联的字段组织 ② 条码独立表设计 ③ 商品/分类选择器组件 ④ 价格多级页面信息架构 ⑤ 采购对接字段 | 只吸收设计意图，**不吸收 W1 聚合实现**（§4.2） |
| **Print / External** | 打印模板、外部系统映射/同步日志的表与页面骨架 | 参考设计；待对应领域立项 |
| **C 的工程纪律** | 无自建 Result/权限/异常/分页/文件/任务框架，前端统一 `lib/axios.ts` —— **这一点 C 做对了** | 作为 V2 后续领域的规范对照 |

### 9.2 必须保留当前 V2 的模块

| 模块 | 保留内容 |
|---|---|
| **SmartAdmin 系统底座** | 认证、用户/员工、部门、角色、菜单、权限、数据范围、统一异常、统一响应、文件、日志、字典、Layout |
| **W1 Product 全部** | `V6__scm_product.sql` / `V7__scm_product_permissions.sql`、SKU 聚合规则（P1–P24）、`@Version` 乐观锁、`product_image` + 主图唯一、JSONB 规格、BigDecimal 4 位契约（HALF_UP + null 语义）、`scm:product:*` 权限、`e2e/scm-product.spec.ts` |
| **V2 技术基线** | Java 21、PostgreSQL 18.3 + schema `xsy_v2`、Flyway 唯一 migration 机制、Sa-Token 权限命名、无外键规则、Mapper XML 规则、`PageResult`/`PageParam` 契约 |
| **V2 测试范式** | `ScmOptimisticLockTest`（`@Version` 生效证明）、`*PgIT`（真实 PG 集成）、`ScmProductMigrationIT`（约束验证）、E2E fixture、冻结复验脚本 `tools/verify_w1_legacy.py` |
| **V2 前端 SCM 骨架** | `src/api/business/scm/**`、`src/types/business/scm/**`、`src/constants/business/scm/**`、`src/components/business/scm/**`、`src/views/business/scm/**` |

### 9.3 应该废弃的 C 资产

| 废弃项 | 原因 |
|---|---|
| **C 的 SmartAdmin 系统层**（登录/认证/菜单/RBAC/Layout/`xsy-scm-base` 全部平台代码） | 包名重写 fork，v3.30，不可复制、不覆盖 V2 |
| **C 的 MySQL 初始化 SQL 与 `sql-update-log`** | 作为 V2 schema 来源废弃；仅保留业务字段设计参考 |
| **C 的 Product 主聚合实现** | 20 项要求中 0 项优于 W1（§4） |
| **C 的库存无锁实现**（`StockOperateManager` 事务内无行锁/无版本） | 与 Legacy 行锁过账 + V2 不可变流水规则冲突 |
| **C 的菜单 seed SQL**（3 个文件） | 15 个组件 MISS + 2 个 seed 路径 100% MISS，挂载即白屏 |
| **C 的商城规划直接实现** | 后端无 mall 包；商城须以 Legacy `mall` 5 表 + miniapp 11 页规则重新设计 |
| **C 的溯源 Vue 页面 / 大屏 Vue 页面 / 财务 3 个缺失页面** | 前端组件 MISS，不能以"有后端类"视为完成 |
| **C 的 `specName` 自由文本规格模型** | 与 W1 结构化 JSONB 规格冲突 |
| **C 的 `skuNo` = "SKU"+自增 id** | 非业务编号，与 Legacy/V2 编号生成器口径冲突 |
| **C 的移动端业务闭环**（xsy-app 员工端为主、客户商城不足、tabBar 仍 demo） | 不足以替代 Legacy miniapp；作为 W6 候选资产另行评估 |

### 9.4 三源均缺失、需按 A 规则 + B 规范新建的领域

| 领域 | 现状 | 说明 |
|---|---|---|
| **Sorting（分拣）** | 三源均无实现（C 仅状态位） | 按 `功能清单模块.docx` 与 Legacy 订单状态位设计；设备接入先做接口抽象，不写死品牌 |
| **Delivery（配送）** | 三源均无实现（C 仅状态位） | 第一版允许人工配置路线，智能规划留后续 |
| **Marketing（营销）** | **A 有 6 表 + 1,517 行**；C 无 | 优先吸收 A 的券/促销/常购/主题实现 |
| **Traceability（溯源）** | A 无、C 后端有前端无 | 按 Legacy 规则 + C 后端模型设计 |
| **Screen（大屏）** | 三源均无可用 | 按 C 的 `screen` 包字段 + V2 前端新建 |
| **Device（电子秤）** | 三源均无独立实现 | 按 `docs/08-电子秤与设备接入设计.md`（如存在）+ `xsy-device-agent` 设计 |

---

## 10. 下一波只做什么

**下一波（W2-B）：只做「Purchase → Receiving → Inventory」主链的迁移设计，不写业务实现。**

理由：
1. 这三域是**业务价值最集中、并发/幂等风险最高、C 页面资产最完整**的交集（C 的 purchase 6 页 2,210 行 + stock 7 页 1,694 行，均为 C 前端最完整的模块之一）。
2. 它们是 Customer/Supplier 的下游消费者，先把这条链的**规则映射表**建起来，Customer/Supplier 的字段设计才有依据，避免返工。
3. 它们能最大程度验证"方案 A 吸收 C"的可行性，且不与 W1 Product 冲突。

具体动作（**只产出设计文档与测试矩阵，不编码**）：

1. **建立逐接口规则映射表**：对 Purchase / Receiving / Inventory 三域，逐接口列出 `Legacy 行为 → C 资产 → V2 目标设计` 三列，标注每个字段/校验/异常码的来源与裁决依据。
2. **逐表转 PostgreSQL / Flyway 草案**：先处理 `t_purchase_order`、`t_purchase_item`、`t_receive`、`t_stock_balance`、`t_stock_flow` 五张表。设计要点：
   - 主键 `GENERATED BY DEFAULT AS IDENTITY`
   - 状态用 `smallint` + 注释 + Java Enum，不用 MySQL `tinyint unsigned`
   - 唯一索引带 `WHERE deleted = FALSE`（partial unique）
   - 库存余额对 SKU 建 partial unique；库存流水不可变（无 `deleted_flag`）
   - 加权平均成本字段 `NUMERIC(18,4)`；数量/重量 `NUMERIC(18,3)`；金额 `NUMERIC(18,2)`
   - 无物理外键（沿用 V2 规则）
   - 预留 `warehouse_id` / `batch_id`（默认 1 / NULL，暂不使用）
3. **对 C 的关键 Service/Manager/Dao 做业务审查清单**：逐条核对部分收货、实重、供应商拆分、重复确认、库存 movement、审计日志、权限、数据范围。特别标记 C 的**无锁/无幂等**点，并给出 V2 的替代设计（行锁 `FOR UPDATE` + `@Version` + 幂等键唯一约束）。
4. **输出测试矩阵**（不实现）：单测清单（状态转换、精度、聚合校验）+ PG 集成清单（Flyway、唯一约束、partial unique、流水）+ 并发清单（库存扣减、收货确认、状态竞争）+ 幂等清单（收货确认、库存入账）+ E2E 清单（采购→收货→入库主链路）。
5. **评审门禁**：设计评审通过后，才按领域切片迁移，**每切片必须保持 W1 Product 不变、legacy 冻结不变**。

**明确不在下一波范围内**：Customer / Supplier 业务实现、任何代码迁移、任何 reference 项目修改、Sorting / Delivery / Marketing / Mall / Trace 的开发。

---

## 11. 最终结论

| 项 | 结论 |
|---|---|
| **推荐方案** | **方案 A**（当前 `v2/**` 为主干，按领域吸收 C 的业务实现）。总分 **81 : 42** |
| **为什么** | ① 保护已验收的 W1 Product 与 V6/V7 哈希锁定资产；② 正确性以 Legacy（371 用例 + 状态机 + 幂等 + 行锁）为事实源，而非 C 的零测试实现；③ 不回退 SmartAdmin v3.31 / Java 21 / PostgreSQL / Flyway 基线；④ 风险对称性远优于 B（A 的风险是开发量，B 的风险是资损级正确性）；⑤ B 的高分项（业务完整度、Vue/UniApp 复用率）可在 A 内以"参考实现"方式吸收 |
| **直接复用 C 的模块** | Customer（页面/DTO/字段组织）、Supplier（页面/业务对象）、Purchase（流程骨架，首选）、Receiving（交互/字段）、Stock（页面交互，7 页最完整）、Order（交互/状态对象候选）、Finance（字段/页面骨架）、Product 辅助（供应商关联、条码表、选择器、价格页面架构、采购对接字段）、Print/External（骨架）、C 的工程纪律（不造轮子） |
| **保留当前 V2 的模块** | SmartAdmin 系统底座全部、W1 Product 全部（含 20 项不变量 + 图片约束 + E2E）、V2 技术基线（Java 21/PG/Flyway/Sa-Token 命名/无外键）、V2 测试范式、V2 前端 SCM 骨架 |
| **废弃的模块** | C 的 SmartAdmin 系统层、C 的 MySQL SQL 与 `sql-update-log`、C 的 Product 主聚合实现、C 的库存无锁实现、C 的 3 个菜单 seed SQL、C 的商城直接实现、C 的溯源/大屏/财务缺失前端、C 的 `specName` 自由文本规格、C 的 `skuNo` 生成方式、C 的移动端业务闭环 |
| **下一波只做什么** | W2-B：**只做 Purchase → Receiving → Inventory 主链的迁移设计**（规则映射表 + PG/Flyway 表草案 + C 实现审查清单 + 测试矩阵），不写业务实现、不动 W1、不改 reference 项目 |
| **不在范围** | Customer/Supplier 业务实现、任何代码迁移、任何 reference 项目修改、Sorting/Delivery/Marketing/Mall/Trace 开发 |

---

## 附录 A：本次审计的实测命令与关键证据索引

| 证据 | 位置 / 命令 |
|---|---|
| C Java 文件数与行数 | `find . -name "*.java" \| wc -l` → 1056；`wc -l` 汇总 → 63,675 |
| C 无乐观锁 | `grep -rn "@Version" --include=*.java .` → 0 |
| C 无默认 SKU | `grep -rni "defaultSku\|isDefault" --include=*.java .` → 0；`ProductSkuEntity` 字段实测 |
| C 无 JSONB | `grep -rni "jsonb" .` → 0 |
| C 无 partial unique | `grep -rni "WHERE deleted"` → 0 |
| C Product 平铺 CRUD | `ProductService.update` / `ProductSkuService.update` 均为 `SmartBeanUtil.copy` + `updateById` |
| C 唯一测试类 | `xsy-scm-server/src/test/java/com/xsy/scm/admin/AdminApplicationTest.java`，`@Test` = 0 |
| C 菜单组件缺失 | `deploy/sql/xsy_scm_v3.30.0_supply_chain.sql` 15 个组件 MISS；2 个 seed 路径 100% MISS |
| MySQL 方言计数 | `INSTR` 117 / `DATE_FORMAT` 49 / `AUTO_INCREMENT` 225 / `tinyint` 267 / 反引号 9,338 / `INSERT IGNORE` 30 |
| 分页方言 | `MybatisPlusConfig.java:29` `DbType.MYSQL`；`DataSourceConfig.java:105` `DbType.MYSQL` |
| Legacy 测试规模 | 75 个测试文件（37 个 `*IT`）、371 用例、9,219 行 |
| V2 W1 门禁 | `2026-09-14-w1-product-验收报告.md`：43/43 测试全绿、lint 0 error、build 绿、E2E 2 passed、冻结复验通过 |
| V2 当前 SCM 范围 | `module/scm/{common,product}`，53 Java + 10 测试；迁移 V1–V7 |

## 附录 B：本次未做的验证（明确声明）

1. **未在 C 中运行 `mvn` / `npm` 构建**：会产生 `target/`、`dist/` 等构建产物，违反"不修改 reference 项目"边界。因此 §2.1 的"能否编译/启动"为**静态判断**，非运行实证。
2. **未在 C 中启动服务**：需 MySQL + Redis + 环境变量 + 文件服务，且会产生运行状态与日志。
3. **未做 C 与 Legacy 的逐行代码 diff**：本次按领域与接口粒度对比，未做文件级 diff 统计。
4. **未验证 C 的 xsy-app 在 H5/小程序下的真实运行**：仅静态审计 `pages.json` 与 `src/**`。

**审计到此停止。没有编码、没有合并、没有修改 `project-reference-examples/xsy-scm/**`、没有修改 `xsy-scm-server/**`、`xsy-scm-web/**`、`xsy-scm-miniapp/**`。**
