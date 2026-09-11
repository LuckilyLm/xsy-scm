# 鲜蔬源智链 · 客户商城小程序（xsy-scm-miniapp）

面向下游客户的订货商城小程序。采用 **Taro + React + TypeScript**，微信小程序首发、预留 H5。
与后台 `xsy-scm-web`（Ant Design 管理端）完全独立构建，复用同一套后端 `/api/mall/**` 接口与业务数据。

> 配套后端实现位于 `xsy-scm-server` 的 `com.xianshuyuan.scm.mall` 模块；接口设计见
> `docs/roadmap/商城与小程序规划.md`（M0–M2 首发闭环）。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 框架 | Taro 4.2.1 + React 18 |
| 语言 | TypeScript 5 |
| 状态 | zustand（登录态、结算中转） |
| 样式 | 原生 Taro 组件 + 自研 **Mall Theme**（生鲜绿主色，与后台 Admin Theme 视觉隔离） |
| 请求 | 自研 `services/http.ts`，封装 `X-Mall-Token`、信封解析、登录失效处理 |

## 目录结构

```
xsy-scm-miniapp/
├─ config/                 # Taro 构建配置（dev / prod / index）
├─ src/
│  ├─ app.config.ts        # 路由、tabBar、分包
│  ├─ app.tsx              # 启动守卫：未登录落地登录页
│  ├─ app.css              # Mall Theme 设计变量
│  ├─ config.ts            # API 基础地址（由编译期常量注入）
│  ├─ types/mall.ts        # 领域类型 + 错误码 + 信封
│  ├─ services/            # http / auth / catalog / cart / order / address / checkout
│  ├─ stores/              # session（登录态）、checkout（结算中转）
│  ├─ utils/               # decimal（定点金额）、format（价格/状态）、error（错误提示）、id（幂等键）、cart-badge
│  ├─ components/          # PriceTag / ProductCard / QuantityStepper / EmptyState
│  ├─ pages/               # 登录、首页、分类、购物车、我的（tabBar 主包）
│  └─ subpackages/
│     ├─ account/          # 收货地址列表、地址编辑
│     └─ trade/            # 商品详情、结算、订单列表、订单详情
├─ package.json
└─ project.config.json    # 微信开发者工具工程配置（appid 需替换为真实值）
```

## 常用脚本

```bash
npm install               # 安装依赖
npm run type-check        # tsc --noEmit 类型检查（CI / 提交前）
npm run lint              # eslint 检查
npm run dev:weapp         # 监听编译微信小程序（产物 dist/，用微信开发者工具打开）
npm run build:weapp       # 生产构建微信小程序
npm run dev:h5            # 监听编译 H5（本地预览，走 devServer 代理到后端）
npm run build:h5          # 生产构建 H5
```

## 环境配置

接口基址通过编译期常量 `process.env.TARO_APP_API_BASE` 注入（`config/index.ts` 的 `defineConstants`）：

| 场景 | 设置 | 说明 |
| --- | --- | --- |
| H5 本地开发 | `TARO_APP_API_BASE=/api` | 走 `h5.devServer.proxy` 代理到后端（默认 `http://127.0.0.1:8080`） |
| 微信小程序 | 真实可访问的后端 HTTPS 域名 | 需在微信公众平台配置 request 合法域名 |

```bash
TARO_APP_API_BASE=/api npm run dev:h5
```

## 与后端的约定（前端必须严格遵守）

1. **身份令牌**：登录返回 `token`，后续请求放在请求头 `X-Mall-Token`。
   令牌失效（`40171` 登录缺失 / `40172` 令牌无效）时，请求层自动清理本地会话并跳转登录页。
2. **错误信封与状态码**：后端业务错误统一返回 `{ code, message, data }`，且 HTTP 状态码与业务域一致
   （`40170`→401、`40372`→403、`40970`→409、`50170`→501）。请求层**无论 HTTP 状态码为何都优先解析信封**，
   并把 `code/message` 还原成 `ApiError`。否则登录失败、登录失效、变价与可见性提示都会退化成“网络错误”。
   调用方用 `isApiError(e, MallErrorCode.XXX)` 判定业务码，用 `showApiError(e)` 保证错误不被静默吞掉。
3. **数量一律字符串**：购物车/结算的 `quantity` 必须是 JSON 字符串（如 `"2"`、`"1.5"`），
   后端用 `DecimalStringDeserializer` 解析，避免浮点误差。前端 `QuantityStepper` 输出数字，
   提交时转字符串。
4. **价格服务端权威**：列表/详情/结算的价格与 `priceSource` 全部来自服务端 `CustomerPriceResolver`，
   前端只展示、不改价、不自行计算。金额展示走 `utils/decimal.ts` 的定点运算，
   **禁止用 `Number()` 做求和或格式化**；后端可能返回 `null` 价格，此时展示“询价”而非 `¥0.00`。
5. **变价确认（价格指纹）**：结算先 `POST /api/mall/orders/preview` 拿到 `priceFingerprint`，
   提交 `POST /api/mall/orders` 时原样带回；若服务端检测到价格变化返回 `40970`，
   前端重新 `preview` 并提示“价格已变化，请重新确认”。
6. **幂等提交**：下单请求带 `Idempotency-Key` 请求头（前端 `utils/id.ts` 生成 UUID）。
   幂等键与“下单内容签名（商品+数量+地址+价格指纹）”绑定：内容不变时网络重试**复用同一个 key**，
   只有内容变化或提交成功后才换新 key，避免重复建单。提交按钮另有 ref 级 in-flight 互斥，防止连点。
7. **客户归属隔离**：所有订单/地址/购物车查询都绑定登录客户，前端无法越权查看他人数据（后端强制过滤）。

## 接口速查

| 能力 | 方法 & 路径 |
| --- | --- |
| 客户登录 | POST `/api/mall/auth/login` |
| 微信登录（占位） | POST `/api/mall/auth/wechat-login`（当前返回 50170） |
| 退出 | POST `/api/mall/auth/logout` |
| 客户资料 | GET `/api/mall/auth/profile` |
| 分类 | GET `/api/mall/catalog/categories` |
| 商品分页 | GET `/api/mall/catalog/products?page&pageSize&keyword&categoryId` |
| 商品详情 | GET `/api/mall/catalog/products/{skuId}` |
| 购物车列表 | GET `/api/mall/cart` |
| 加购/改数量 | POST `/api/mall/cart/items` · PUT `/api/mall/cart/items/{skuId}`（body `{skuId, quantity}`） |
| 移除购物车 | DELETE `/api/mall/cart/items/{skuId}` |
| 地址列表/新增/改/设默认/删 | `/api/mall/addresses` 系列 |
| 结算预览 | POST `/api/mall/orders/preview` |
| 提交订单 | POST `/api/mall/orders`（带 `Idempotency-Key`） |
| 订单分页/详情 | GET `/api/mall/orders` · GET `/api/mall/orders/{id}` |

## 演示账号

后端迁移 `V27` 已写入演示账号：

- 用户名：`demo`
- 密码：`Xsy@Demo2026`
- 绑定客户：`CUST-DEMO-001`

> 微信小程序真机预览需在 `project.config.json` 填入真实 `appid`，并在公众平台配置后端合法域名。
> H5 预览需后端开启对应跨域（CORS）或走 devServer 代理。
