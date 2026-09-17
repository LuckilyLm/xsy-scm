# Sprint 5 目标分支复用矩阵

> 历史参考：本文保留原始业务/设计语义。技术栈、实施状态和进度均为 legacy 快照；当前实现以 V2 对应波次的 target-design、approval 与验收报告为准。已删除的旧计划及验证记录可从 Git 历史恢复。

状态：待人工规格评审
审计日期：2026-09-11
审计分支：`feature/sprint5-reuse-audit`
审计方式：只读分析（git 提交图 / 文件差异 / 现有实现代码），不修改业务代码、不合并分支、不调整迁移。

> 本文所有提交哈希、共同祖先、文件差异均为本次实测结果，不引用历史截图或旧报告。
> 外部产品介绍（蔬东坡等）只作流程与信息架构参考，**不作为本项目已确认需求**。

---

## 0. 审计元数据与提交基线

### 0.1 分支与提交哈希（实测）

| 分支 | 提交哈希 | 说明 |
| --- | --- | --- |
| `feature/sprint5`（当前集成基线） | `78666e69cdde6c02a46f142dfacdcc3c714a9054` | `merge: integrate Sprint 5 mall client` |
| `feature/sprint5-reuse-audit`（审计分支） | `852d8b440043c494901c09b0debd5d760072ea3e` | 本次审计文档提交 |
| `workbuddy/feature-sprint5-e5d79aff` | `78666e69cdde6c02a46f142dfacdcc3c714a9054` | 与基线同指向 |
| `feature/sprint5-quality-gate` | `78666e69cdde6c02a46f142dfacdcc3c714a9054` | 与基线同指向 |
| `feature/sprint5-mall-client` | `ebc65bc0fd7f03a87b72b9bd12e06f66327bc4f6` | 已并入基线 |
| `workbuddy/feature-sprint5-mall-client-203bd67e` | `ebc65bc0fd7f03a87b72b9bd12e06f66327bc4f6` | 与 mall-client 同指向 |
| `workbuddy/feature-sprint5-557a2cbb` | `4fadfdf6b723ca2f6686c0da22e645e555e58187` | 已并入基线 |
| `feature/sprint5-price-center-backend` | `66d01904a2f646b0181cb4bc9f5175f9e8434376` | 已并入基线 |
| `feature/sprint4-miniprogram` | `956286ec23c738593c2a5fa02c94fe07b331f194` | **未并入基线** |

### 0.2 共同祖先与拓扑

- Sprint 5 两条集成分支的分叉点（共同祖先）：`0f2e7fd73c44d4e6ea8b68ffc5dfc543b7ef2c02`（`style(docs): remove trailing whitespace`）。
- 基线 `78666e6` 是合并提交，父节点为 `66d0190`（价格中心后端）与 `ebc65bc`（商城小程序客户端）。实测 `git diff <parent> 78666e6` 无冲突解决残留，为干净合并。
- `feature/sprint5` 与 `feature/sprint4-miniprogram` 的共同祖先：`ab7771b7365309ce2be9d9056ed5ec8ec297f3d3`（`feat(web): add audit log pages and fix form types`，2026-09-10）。
- `git merge-base --is-ancestor 956286e feature/sprint5` → **否**：`feature/sprint4-miniprogram` 整体**未合并**。
- `git merge-base --is-ancestor ebc65bc feature/sprint5` → **是**：Sprint 5 商城客户端**已合并**。

### 0.3 只读约束与未验证项

- 本审计未运行任何构建或测试：`xsy-scm-miniapp/node_modules` 不存在，无法执行 `lint / type-check / build:weapp / build:h5`；后端未启动，未运行 JUnit / Flyway 校验。
- `ebc65bc` 提交信息自述"lint、type-check、build:weapp、build:h5 全部通过"，本次**未复核**，列为待验证事实。
- 本文只覆盖 `docs/superpowers/specs/2026-09-11-sprint5-reuse-matrix.md` 一个文件，未提交 `plans/`、`price-center` 等未跟踪文档。

---

## 1. 逐提交矩阵（从共同祖先 `0f2e7fd` 开始）

### 1.1 已并入基线的提交

| # | 提交哈希 | 标题 | 主要文件 | 领域 | 与基线关系 | 已合并 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `4fadfdf` | `docs(sprint5): add external capability gap research` | `docs/references/sdongpo/*`、`docs/requirements/2026-09-11-需求基线实现差距分析.md` | 文档 | 基线文档来源 | 是 | **reuse** |
| 2 | `ebc65bc` | `feat(mall-miniapp): 打通登录/可见性/价格/购物车/结算/订单查询适配` | `xsy-scm-miniapp/src/**`（20 文件，+877/−174） | 商城小程序 | 基线客户端主链路 | 是 | **reuse**（含 adapt 跟进项） |
| 3 | `66d0190` | `feat(customer): add customer type pricing` | `customer/**`、`mall/MallCheckoutFingerprint`、`order/entity/PriceSource`、`V34__create_customer_type_price_schema.sql`（33 文件，+857/−34） | 客户价格 / 商城结算 | 基线价格权威 | 是 | **reuse**（含 adapt 跟进项） |
| 4 | `78666e6` | `merge: integrate Sprint 5 mall client` | 合并提交 | 集成 | 当前基线 HEAD | 是 | **reuse** |

### 1.2 未并入基线的提交（`ab7771b..956286e`）

| # | 提交哈希 | 标题 | 变更规模 | 领域 | 已合并 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 5 | `8c6d256` | `feat(miniprogram): add mall and business domain foundations` | 252 文件 / +10241 | 小程序 + mall/marketing/sorting/delivery/finance/traceability + V27–V35 | 否 | **reject**（整体） |
| 6 | `302345f` | `merge: integrate miniprogram and business domain foundations` | 合并提交 | 集成 | 否 | **reject** |
| 7 | `e08c74e` | `feat: add cross-platform mall and business foundations` | 同上范围 | 集成 | 否 | **reject** |
| 8 | `956286e` | `merge: integrate mall and business foundations` | 合并提交 | 集成 | 否 | **reject** |

**判断依据**：`feature/sprint4-miniprogram` 的商城与小程序代码是**更早版本**，已被基线超越：
- 该分支小程序缺 `utils/decimal.ts`、`utils/error.ts`（实测 `git ls-tree` 无此文件），即缺少定点金额与统一错误处理。
- 该分支 `mall/service/` **没有 `MallCheckoutFingerprint.java`**，`MallCheckoutItemResponse` 缺 `priceSourceId`，即没有服务端价格指纹。
- 该分支额外引入 `MallTraceController`（溯源，Sprint 12）及 marketing/sorting/delivery/finance 全套域代码与迁移。
- 其 Flyway `V27–V34` 与基线 `V27–V34` **含义完全不同**（见 §9 风险 R1），整体合并会破坏迁移链。

---

## 2. 逐模块矩阵

| 模块 / 区域 | 分类 | 处理决定 | 主要风险 / 验证 |
| --- | --- | --- | --- |
| `xsy-scm-miniapp` 工程配置、Taro 页面、通用组件 | `reuse` | 以基线已集成版本为准；逐文件核对身份、目录、购物车、结算、订单、地址、异常状态 | 需重跑 lint / type-check / weapp / h5 构建；Taro H5 与 weapp API 差异 |
| 小程序 `services/http.ts` | `reuse` | 保留"任意状态码优先解析信封 + 认证接口自身失败不触发跳转 + 并发 401 去抖" | 会话过期跳转、并发 401、Token 本地存储（见 R6） |
| 小程序 `utils/decimal.ts`、`utils/error.ts` | `reuse` | 定点金额（整数化 + HALF_UP）与统一错误提示，作为后续金额展示唯一入口 | 内部 6 位精度上限；`Number` 仅用于数量步进器 UI |
| 小程序 `catalog/cart/checkout/order/address` 服务 | `adapt` | 对照 `/api/mall/**` 聚合 API；服务端重新校验价格、可见性、数量、地址 | DTO 漂移、`PriceSource` 缺 `CUSTOMER_TYPE`、缺价路径 |
| 后端 `mall` 客户账户/会话/地址/目录/购物车/预览/订单 | `reuse` | 基线已有领域与迁移，作为 Sprint 5 M0–M2 基础 | 客户停用、SKU 下架/删除、缺价、越权、事务、幂等 |
| 后端 `customer` 可见性与 `CustomerPriceResolver` | `reuse` | 优先级已实现 `AGREEMENT > CUSTOMER_TYPE > MARKET`；保留半开有效期 `[from, to)` | 缺价返回 `unitPrice=null`，下游未防御（见 R2） |
| 后端 `mall` 结算指纹 `MallCheckoutFingerprint` | `reuse` | 指纹覆盖客户、地址、SKU、数量、单价、价格来源、来源记录 ID | 未显式含快照时间/价格版本号（见 §8-C4） |
| 后端订单幂等 `IdempotencyService` | `reuse` | 同 scope+key 不同请求哈希会被拒绝；请求体含 `customerId`，跨客户复用被拒 | 无 TTL/清理策略说明；商城 create 与 submit 共用同一 key、分 scope |
| Web 商城首页、主题与运营配置 | `reuse` | 保留已集成页面与 API；本 Sprint 只补稳定性/验收，不扩通用装修器 | 主题不得改变成交价；未知主题回退 |
| 小程序首页 / 分类页错误态 | `adapt` | 当前 `catch {}` 静默吞错，无错误/重试 UI | 违反"所有网络页面覆盖加载/空/错/重试" |
| 起订量 / 步长 / 数量精度 | `defer` | 后端与 DTO **无 MOQ/step 字段**，客户端步进器硬编码 `min=1` | 需先冻结 SKU 交易约束合同 |
| 小程序 `types/mall.ts` `PriceSource` | `adapt` | 需补 `CUSTOMER_TYPE`；`priceSourceLabel` 与 `ProductCard` 同步 | 当前 `CUSTOMER_TYPE` 会显示空标签 |
| `marketing` 促销 / 优惠券 / 首页板块 | `defer` | 首页可展示 `promotions`，但**不进入 Sprint 5 成交价优先级** | 活动资格、叠加、有效期、订单快照未冻结 |
| `delivery` / `sorting` / `finance` / `traceability` | `reject`（本次） | 不从目标分支复用；分别属 Sprint 9–12 | 未批准状态机、迁移、权限种子 |
| 权限种子及 `AuthorityRules` / `SecurityConfig` | `reject`（直接复用） | 使用当前 Sprint 4 RBAC 基线；仅按新接口最小追加 | 目标分支含跨域权限，可能扩大访问范围 |
| Flyway `V27–V35`（目标分支） | `reject`（整体） | 不复制或重排；基线新增迁移只能从 **V35** 起 | 版本冲突、已应用迁移被覆盖、回退收货规则 |
| 目标分支删除/改名/大范围域变更 | `reject` | 不通过整体 merge/cherry-pick 带入 | 破坏当前 Sprint 4 收货、订单与迁移链 |
| 支付、供应商协同、硬件直连、完整 H5 发布 | `defer` | 仅保留扩展点，Sprint 5 不实现 | 业务规则、授权、设备可靠性、资金安全未冻结 |

---

## 3. 已合并内容（`reuse`）

- **商城小程序客户端主链路**（`ebc65bc`）：登录、微信登录占位、目录、商品详情、购物车、结算预览、订单列表/详情、地址。
- **请求层根因修复**（`ebc65bc`）：任意 HTTP 状态码优先解析 `ApiResponse` 信封，避免 40170/40171/40172/40970/40372 退化为"网络错误"；认证接口自身失败不触发登录失效跳转。
- **定点金额工具**（`ebc65bc`）：`utils/decimal.ts` 以整数化实现精确求和与 HALF_UP，替换 `Number()` 浮点累加。
- **客户类型价后端**（`66d0190`）：`CustomerTypePrice*` 全套、`CustomerPriceResolver` 增加 `CUSTOMER_TYPE` 层级、`V34__create_customer_type_price_schema.sql`、批量导入整批回滚 + 审计表。
- **服务端价格指纹**（`66d0190`）：`MallCheckoutFingerprint` 覆盖客户/地址/SKU/数量/单价/来源/来源记录。
- **商城独立安全链**：`MallSecurityConfig` 仅拦截 `/api/mall/**`，`X-Mall-Token` 无状态、无 Cookie，与后台 Session/CSRF 并存。

---

## 4. 仍需适配内容（`adapt`）

| 编号 | 位置 | 现状 | 需适配为 |
| --- | --- | --- | --- |
| A1 | `types/mall.ts` / `utils/format.ts` / `components/ProductCard.tsx` | `PriceSource = 'AGREEMENT' \| 'MARKET' \| 'OVERRIDE'`，缺 `CUSTOMER_TYPE`；`priceSourceLabel` 对 `CUSTOMER_TYPE` 返回空；卡片仅在 `AGREEMENT` 时显示标签 | 补齐 `CUSTOMER_TYPE` 联合类型与标签；卡片按后端来源展示 |
| A2 | `pages/home/index.tsx`、`pages/category/index.tsx` | `loadProducts`/`categories` 用空 `catch` 吞错，无错误与重试入口 | 增加错误态与"重新加载"入口 |
| A3 | `subpackages/trade/product/index.tsx` | 缺价时展示"询价"，但"加入购物车/立即购买"仍可点 | 缺价时禁用购买入口并给出说明 |
| A4 | `subpackages/trade/checkout/index.tsx` | 依赖 `previewData.unavailableCount > 0` 提示，但后端预览恒返回 0 | 预览需返回失效/缺价行信息（依赖 R2 修复） |
| A5 | `MallCartService.list()` / `MallCheckoutService.price()` | 可见但缺价 SKU 传入 `lineAmount(qty, null)` | 显式缺价业务错误（见 R2） |
| A6 | 小程序数量步进器 | `min=1` 硬编码，未消费服务端起订量/步长 | 待 §5 合同冻结后由服务端下发约束 |

---

## 5. 延后内容（`defer`）

- 活动价参与成交、优惠券叠加、余额与在线支付。
- 起订量 / 增量步长 / 数量精度合同（后端当前无字段）。
- 分拣称重、配送、财务、溯源、供应商门户、硬件设备接入。
- 将后台 Ant Design / ProComponents、路由或权限模型移植到 Taro。
- 完整 H5 商城发布与通用拖拽式装修器。

## 6. 拒绝内容（`reject`）

- 整体合并 `feature/sprint4-miniprogram`（`956286e`）或其四个提交。
- 复制/重排目标分支 Flyway `V27–V35`；基线新增迁移只能从 `V35` 起。
- 直接复用目标分支的权限种子、`AuthorityRules`、`SecurityConfig` 改动。
- 目标分支的 `MallTraceController` 及 marketing / sorting / delivery / finance 域代码。
- 支付、库存预占、称重与硬件直连、复杂营销叠加、供应商门户、Sprint 6–12 业务域。
- 后台 Cookie / RBAC / 路由模型向商城身份体系迁移。

---

## 7. 小程序主链路逐项核对

| # | 核对项 | 结论 | 证据 / 缺口 |
| --- | --- | --- | --- |
| 1 | 登录 | 通过 | `services/auth.ts` → `POST /api/mall/auth/login`；登录页 ref 防连点，展示后端原文（40170/40370/40371） |
| 2 | 会话失效 | 通过 | `http.ts` 对 40171/40172 清会话并 `reLaunch` 登录页，`redirecting` 去抖 |
| 3 | 微信登录占位 | 通过 | `wechat-login` 固定契约；后端 `50170 WECHAT_LOGIN_UNAVAILABLE`，前端明确提示 |
| 4 | 首页 / 分类 | **部分** | 目录/分类 API 与分页正常；**无错误/重试态**（A2） |
| 5 | 商品详情 | **部分** | 40372/40470 有错误+重试；缺价仍可购买（A3） |
| 6 | 客户可见性 | 通过 | 后端 SQL 层按 `VisibilityPolicy` 过滤；`40372` 显式提示 |
| 7 | 协议价 / 客户类型价 / 市场价 | **部分** | 后端三级优先级已实现；客户端 `PriceSource` 缺 `CUSTOMER_TYPE`（A1） |
| 8 | 缺价展示 | **部分** | 目录/详情展示"询价"、不伪造 ¥0.00；但购物车/预览存在 NPE 风险（R2），预览 `unavailableCount` 恒 0 |
| 9 | 购物车 | 通过 | 小计对后端 `lineAmount` 用 `sumDecimal` 精确求和；改量请求时序保护；失效行保留并标原因 |
| 10 | 结算预览 | **部分** | 预览重解析价格并返回指纹；地址切换时序保护；但缺价路径未收敛（R2） |
| 11 | 价格指纹 | 通过 | 服务端生成，覆盖客户/地址/SKU/数量/单价/来源/来源记录 |
| 12 | Idempotency-Key | 通过 | 提交带 `Idempotency-Key`；键与"内容签名（商品+数量+地址+指纹）"绑定，仅内容变化或成功才换键；ref 级 in-flight 互斥 |
| 13 | 变价处理 | 通过 | `40970` 作废旧键、重新预览并提示；`40372/40470` 刷新预览 |
| 14 | 订单列表 | 通过 | 分页 + 状态筛选 + 订单号关键词；错误/重试态；`useDidShow` 刷新 |
| 15 | 订单详情 | 通过 | 加载/错误/重试；返回页面刷新；展示订购量与实重差异 |
| 16 | 地址归属 | 通过 | 后端 `selectActiveByIdAndCustomer(id, customerId)` 强制归属；前端仅消费自身列表 |
| 17 | 加载/空/错/重试 | **部分** | 购物车/订单/详情具备；首页/分类缺错误重试态（A2） |

### 7.1 重点风险项逐条回答

| 问题 | 结论 |
| --- | --- |
| 客户端是否错误决定成交价格？ | **否**。客户端只传 `skuId + quantity + addressId + priceFingerprint`，单价/金额一律用后端返回值。 |
| 是否把缺价当成 0 元？ | **否**。目录/详情用"询价"占位；但购物车与预览对可见缺价 SKU 会走 `lineAmount(qty, null)`，后端 NPE（R2）。 |
| 是否用 `Number` 做金额累计？ | **否**。金额累计走 `sumDecimal`（定点）；`Number` 仅出现在数量步进器 UI 与 `formatQuantity` 输入。 |
| 登录接口自身失败是否错误触发会话失效跳转？ | **否**。`http.ts` 的 `AUTH_PATHS` 对 `/auth/login`、`/auth/wechat-login` 跳过会话失效处理。 |
| 重复提交是否复用正确的幂等键？ | **是**。网络重试复用同一 key；内容变化或成功后作废重生成。 |
| 价格指纹是否覆盖客户/地址/SKU/数量/价格来源/来源记录？ | **是**。`MallCheckoutFingerprint` 覆盖全部六项；未含显式快照时间字段（§8-C4）。 |
| 后台员工身份与商城客户身份是否混用？ | **否**。商城独立 `SecurityFilterChain`（`@Order(1)`、无 Cookie、`X-Mall-Token`）；后台预览走 `MarketingController` 会话，不经商城令牌。 |

---

## 8. API 合同差异

| 编号 | 差异 | 位置 | 影响 | 建议 |
| --- | --- | --- | --- | --- |
| C1 | `PriceSource` 取值不一致：后端 `customer.service.PriceSource = {AGREEMENT, CUSTOMER_TYPE, MARKET}`，`order.entity.PriceSource` 额外含 `OVERRIDE`；小程序仅 `{AGREEMENT, MARKET, OVERRIDE}` | `types/mall.ts` vs 两个后端枚举 | 客户类型价在前端无标签；两个后端枚举本身也不同名 | 小程序补 `CUSTOMER_TYPE`；明确商城响应只可能返回 `customer.PriceSource` 三值 |
| C2 | `MallProductResponse` 无显式 `priceStatus` / `UNPRICED` 字段 | `MallProductResponse.java` | 客户端只能以 `unitPrice == null` 推断缺价，规格要求的"等价明确字段"缺失 | 增加显式缺价标记或确认以 null 为合同 |
| C3 | `MallCheckoutPreviewResponse.unavailableCount` 恒为 `0` | `MallCheckoutService.preview` | 结算页失效提示永不触发 | 预览返回真实失效/缺价行 |
| C4 | 指纹无显式"价格版本/快照时间"字段 | `MallCheckoutFingerprint` | 单价+来源+来源记录已隐含价格状态，但规格要求的价格版本语义不显式 | 明确是否以现有组合作为"价格版本" |
| C5 | 无起订量 / 步长 / 数量精度字段 | `MallProductResponse`、`MallProductRow`、product 域 | 客户端 `min=1` 硬编码，与规格"由交易 SKU 定义"不符 | 单独冻结合同后补字段 |
| C6 | 缺价时 `OrderAmountCalculator.lineAmount(qty, null)` 触发 NPE | `MallCartService.list`、`MallCheckoutService.price` | 可见但缺价 SKU 导致 500，而非稳定业务错误码 | 增加缺价业务错误码（沿用 07 域） |
| C7 | 首页/分类无错误态 | `pages/home`、`pages/category` | 网络失败静默 | 补错误/重试 UI |

---

## 9. 风险清单

| 编号 | 风险 | 等级 | 说明与缓解 |
| --- | --- | --- | --- |
| R1 | **Flyway 版本冲突**：目标分支 `V27=create_mall_customer_foundation … V34=create_marketing_theme_config`，基线 `V27=upgrade_purchase_receiving_rules … V34=create_customer_type_price_schema` | 高 | 严禁整体合并；基线新增迁移只能从 `V35` 起；不得编辑已应用迁移 |
| R2 | **缺价 NPE**：可见但无协议价/类型价/市场价的 SKU 在购物车与预览触发 `BigDecimal.multiply(null)` | 高 | 在 `MallCartService` / `MallCheckoutService` 增加显式缺价判断与业务错误码 |
| R3 | 客户端 `PriceSource` 缺 `CUSTOMER_TYPE` | 中 | 前端展示错误/空标签；补类型与标签 |
| R4 | 两个后端 `PriceSource` 枚举不同名 | 中 | 商城响应统一以 `customer.PriceSource` 为契约；订单侧映射显式化 |
| R5 | 零价 vs 缺价语义未冻结 | 中 | 默认价格非负、空值为缺价；零价是否允许需业务确认，测试固定 |
| R6 | Token 存于 `Taro.setStorageSync` | 中 | 会话有效期 168h、服务端只存摘要；需确认小程序本地存储安全边界 |
| R7 | 促销在首页展示但不参与成交 | 中 | 主题/活动不得改变成交价；未来接入须新增优先级与资格快照 |
| R8 | 幂等记录无 TTL/清理说明 | 低 | `IdempotencyService` 仅 claim/complete，未见过期回收策略 |
| R9 | 小程序无自动化测试、本次未跑构建 | 中 | `node_modules` 缺失，lint/type-check/weapp/h5 未复核 |
| R10 | 目标分支商城代码为旧版本 | 中 | 已确认缺少 `decimal.ts`/`error.ts`/`MallCheckoutFingerprint`，不得回退 |

---

## 10. 建议的最小后续提交顺序

1. `docs(sprint5): complete mall reuse audit` — 本文件（本次）。
2. `fix(mall): reject unpriced sku with stable error instead of NPE` — 修 R2 / C6（购物车与预览缺价收敛）。
3. `fix(mall): return unavailable lines in checkout preview` — 修 C3，使 A4 生效。
4. `feat(miniapp): add CUSTOMER_TYPE price source and unpriced purchase guard` — 修 A1 / A3 / C1。
5. `fix(miniapp): add error and retry states for home and category` — 修 A2 / C7。
6. `test(mall): cover unpriced, fingerprint and idempotency replay` — 固定缺价、指纹与幂等语义。
7. （独立规格）`feat(customer): freeze MOQ and step contract` — C5，先出规格再实现。

> 以上 2–6 项均在现有 `/api/mall/**` 合同内闭环，不触碰迁移与权限；第 7 项需先完成规格评审。

---

## 11. 已确认事实 / 建议 / 待确认事项

### 已确认事实（本次实测）

- 基线 `feature/sprint5` = `78666e6`，Sprint 5 商城客户端（`ebc65bc`）与客户类型价（`66d0190`）**已合并**。
- `feature/sprint4-miniprogram`（`956286e`）**未合并**，且其商城/小程序代码为旧版本。
- 目标分支 Flyway `V27–V34` 与基线**含义冲突**。
- 后端价格优先级为 `AGREEMENT > CUSTOMER_TYPE > MARKET`；商城客户端不决定成交价。
- 商城身份与后台身份在安全链、令牌、Cookie 三方面均隔离。
- 缺价 SKU 在购物车/预览存在 NPE 风险（C6/R2），预览 `unavailableCount` 恒 0（C3）。

### 建议

- 采纳 §10 的提交顺序，先修缺价与预览失效行，再补客户端类型与错误态。
- 明确 `PriceSource` 以 `customer.PriceSource` 为商城唯一契约，统一两个后端枚举。
- 所有基线新增迁移从 `V35` 起编号；禁止编辑已应用迁移。

### 待确认事项

- 是否允许录入并成交零价（R5）。
- 价格指纹是否需显式快照时间/价格版本字段（C4）。
- `MallProductResponse` 是否新增显式缺价状态字段，或以 `unitPrice == null` 为最终合同（C2）。
- 起订量/步长/数量精度的字段与迁移归属（C5）。
- 幂等记录是否需要 TTL 与定期清理（R8）。
- 小程序本地 Token 存储的安全边界要求（R6）。
- 外部参考（蔬东坡）中加工、积分、周转筐、CRM、税务凭证、AI 录单、复杂装修器是否进入后续 Sprint —— 当前一律不自动纳入。

---

## 评审门槛

本矩阵经人工确认后，才允许按 `2026-09-11-sprint5-price-center.md` 的冻结合同修改服务端、Web 或小程序。任何新发现的目标分支跨域代码默认归入 `defer`，除非有当前 Sprint 规格明确授权。
