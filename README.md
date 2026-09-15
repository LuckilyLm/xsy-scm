# 鲜蔬源智慧供应链管理平台

> ## SmartAdmin V2（W0–W3 已完成，2026-09-15）
>
> 本仓库的 SmartAdmin V2 正式工作区固定为根目录前后端、工具和文档目录：
>
> | 目录 | 状态 |
> |---|---|
> | `xsy-scm-server/` | **V2 正式后端**（SmartAdmin 基线 + SCM 业务，Java 21 + PostgreSQL） |
> | `xsy-scm-web/` | **V2 正式后台**（SmartAdmin Vue3 + TypeScript） |
> | `tools/` | **V2 正式工具** |
> | `docs/` | **V2 正式文档** |
> | `xsy-scm-miniapp/` | LEGACY，冻结只读（W6 迁 uni-app Vue3） |
>
> 关键变化：
>
> - **系统底座改为 SmartAdmin**：登录、认证、用户、员工、部门、角色、菜单、权限、数据权限、
>   日志、字典、文件、统一异常/响应、前端 Layout 与系统页面全部采用 SmartAdmin；
>   旧 `auth` / `system` 实现**不迁移**。
> - **管理后台改为 Vue3 + TypeScript**，旧 React 后台停止开发。
> - **只迁供应链业务**：product / customer / supplier / order / purchase / inventory / mall
>   （`marketing` 为 DEFERRED）。
> - **保持 PostgreSQL**（不切 MySQL），V2 使用独立 schema `xsy_v2`。
> - 当前不是 SaaS，不引入 `tenant_id` / 多租户；不引入 Spring Cloud。
>
> 规则见 [`SMARTADMIN_REFERENCE_RULES.md`](./SMARTADMIN_REFERENCE_RULES.md)，
> 审计与迁移方案见 [`docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md`](./docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md)。
>
> **下文描述的是 legacy 实现（Sprint 1–4），保留作为业务语义与字段参考。**

---

当前分支已实现 Sprint 1 商品档案、Sprint 2 客户定价/销售订单/售后、Sprint 3 采购收货与库存，以及 Sprint 4 登录权限、商城和小程序基础。PostgreSQL、Spring Boot REST API、React 管理端与 Taro 小程序已经形成工程链路，后端保持按领域分包的模块化单体结构。当前完成事实以代码和验证记录为准，后续方向见 [Sprint 5–12 产品路线规划](docs/roadmap/2026-09-11-Sprint5-12产品路线规划.md)。

## 已实现范围

### Sprint 1：商品档案

- 三级商品分类。
- SPU 商品档案分页、筛选、上下架和软删除。
- 一个 SPU 下多个 SKU，且必须有且仅有一个默认 SKU。
- 编辑时保留已有 SKU 的数据库 ID，不删除重建保留规格。

### Sprint 2：客户、订单与售后

- 客户类型、客户档案、启停状态，以及 `ALL_ENABLED` / `ALLOWLIST` 两种 SKU 可见策略。
- 客户 SKU 协议价；有效期采用 `[生效时间, 失效时间)`，同一客户与 SKU 的有效区间不可重叠。
- 销售订单草稿、提交、实际数量录入、确认和取消；提交时重新校验并锁定协议价或市场价。
- 草稿阶段可人工改价，但必须填写原因；后台始终负责价格解析、金额计算和最终校验。
- 标品提交时将订购数量复制为实际数量；非标品在待确认阶段录入实际重量后才能确认。
- 补单使用正常订单流程，可关联同客户的已确认原订单，并必须填写补单原因。
- 已确认订单可发起退货；审批后按原订单锁定价格创建待处理退款，退款完成仅记录状态及可选外部参考号。
- 业务命令支持 `Idempotency-Key`，并写入持久化操作日志；当前操作人固定为 `SYSTEM`。

### Sprint 3：供应商、采购、收货与库存

- 供应商与仓库基础资料维护，以及采购业务所需的启用状态校验。
- 从已确认销售订单生成采购需求，支持需求查询、分配和采购订单追溯。
- 采购订单草稿、编辑、提交和取消；保留采购行 ID/version 与需求来源，服务端计算采购数量和金额。
- 一个采购订单支持多次收货和多个确认批次，支持部分收货直至收齐或少收关闭。
- 标品按本次收货数量入库；非标品保留申报数量并按人工确认的实际重量入库，不用实际重量覆盖计划数量。
- 收货确认拒绝零数和负数；超收按已配置容差规则校验，要求 `Idempotency-Key`，重复请求不会重复累计采购进度或库存。
- 收货确认、采购进度、库存余额、`PURCHASE_IN` 流水、日志和幂等结果在同一数据库事务中提交。
- 库存余额粒度为仓库 + SKU；库存流水记录变动前、变动量和变动后数量，并在数据库边界保持追加只读。
- 库存页面只提供余额和流水查询，不提供通用库存调整入口。

### Sprint 4：登录权限、商城和小程序基础

- 后台登录、会话、强制改密、用户、部门、角色、菜单、权限、字典、登录日志和操作日志。
- 商城客户身份、地址、商品目录、购物车、订单预览与提交、订单查询。
- 商城首页、主题和部分营销配置基础。
- 独立的 Taro + React + TypeScript 小程序工程，支持微信小程序与 H5 构建。

当前仍不包含完整库存预占、批次库位、分拣出库、配送签收、应收应付、完整成本利润、真实支付、真实称重设备接入和全链路经营分析。商城与营销属于基础实现，尚不能视为完整客户试运行交付。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 22+
- PostgreSQL 18（其他受当前 Flyway 版本支持的 PostgreSQL 版本也可）

后端从以下环境变量读取数据库连接信息：

```text
XSY_DB_URL
XSY_DB_USERNAME
XSY_DB_PASSWORD
```

集成测试使用 `XSY_TEST_DB_URL` 及相同的数据库用户名和密码变量。请在本机或密钥管理系统中配置真实值，不要提交密码或 `.env` 文件。

## 启动后端

在 PowerShell 中选择 Java 21，并从 `xsy-scm-server` 启动：

```powershell
cd xsy-scm-server
$env:JAVA_HOME='D:\Java\JDK21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd spring-boot:run
```

首次启动由 Flyway 自动迁移数据库。默认 API 地址为 `http://127.0.0.1:8080/api`。

## 启动前端

```powershell
cd xsy-scm-web
npm install
npm run dev
```

Vite 开发服务器将 `/api` 代理到本机 `8080` 端口。主要页面包括：

- `/products`：商品档案
- `/customers`：客户档案
- `/customer-agreement-prices`：客户协议价
- `/orders`、`/orders/new`、`/orders/:id`：销售订单
- `/order-returns`、`/order-returns/new`、`/order-returns/:id`：退货单
- `/order-refunds`、`/order-refunds/:id`：退款单
- `/purchases/demands`：采购需求
- `/purchases/orders`、`/purchases/orders/new`、`/purchases/orders/:id`：采购订单
- `/purchases/orders/:id/receipts`、`/purchases/receipts`、`/purchases/receipts/:id`：采购收货
- `/warehouses/suppliers`、`/warehouses/settings`：供应商与仓库资料
- `/warehouses/inventories`：库存余额
- `/warehouses/inventory-movements`：库存流水
- `/system/*`：用户、部门、角色、菜单、字典和日志
- `/mall/*`、`/shop/*`：商城配置及 Web 商城基础页面

## 核心业务状态

```text
销售订单：DRAFT -> PENDING -> CONFIRMED
             \         \
              ---------> CANCELLED

退货单：PENDING -> APPROVED | REJECTED | CANCELLED
退款单：PENDING -> COMPLETED

采购订单：DRAFT -> SUBMITTED -> PARTIALLY_RECEIVED -> RECEIVED
             \       \
              -------> CANCELLED

收货单：DRAFT -> PARTIALLY_CONFIRMED -> CONFIRMED
```

- 销售订单 `DRAFT` 可编辑、提交或取消；`PENDING` 可录入非标品实际数量、确认或取消；`CONFIRMED` 和 `CANCELLED` 为编辑终态。
- 待审批和已审批退货数量都会占用原订单行的可退数量，累计不得超过实际数量。
- 采购订单 `DRAFT` 可编辑；`SUBMITTED` 可收货或取消；`PARTIALLY_RECEIVED` 仅可继续收货；`RECEIVED` 和 `CANCELLED` 只读。
- 收货单可分批确认；每批只处理本次增量，所有行收齐后进入 `CONFIRMED`。
- 价格、数量、重量和金额通过 API 传输时使用十进制字符串；服务端使用 `BigDecimal`，禁止以浮点数作为业务权威值。
- 所有可变聚合携带乐观锁 `version`；过期版本及幂等键冲突返回 HTTP 409。

历史 Sprint 规格、实施计划和验证记录统一从 [文档总览](docs/00-文档总览与索引.md)进入。

## 验证

后端（需要可用的 PostgreSQL 测试数据库）：

```powershell
cd xsy-scm-server
mvn.cmd clean verify
```

前端：

```powershell
cd xsy-scm-web
npm run lint
npm run typecheck
npm test
npm run build
```

浏览器验收前安装项目锁定版本的 Chromium，并确保 PostgreSQL 和后端已运行：

```powershell
cd xsy-scm-web
npx playwright install chromium
npm run e2e -- product-flow.spec.ts
npm run e2e -- sprint2-order-pricing.spec.ts
npm run e2e -- sprint2-after-sales.spec.ts
```

浏览器验收必须先启动 PostgreSQL 和后端。Sprint 4 合并后的完整 Playwright 主链路仍需补充当前版本证据；不要用 lint、构建或历史 E2E 结果替代浏览器验收。
