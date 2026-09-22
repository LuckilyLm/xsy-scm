# 鲜蔬源智链 · 客户商城小程序（xsy-scm-miniapp）

面向下游客户的订货商城小程序。技术栈为 **uni-app + Vue3 + Vite**，微信小程序首发、H5 同步可跑。

> **基线来源**：本目录于 2026-09-22 整体替换为 SmartAdmin 官方 uni-app 移动端
> （`project-reference-examples/xsy-scm/xsy-app`）的框架基线，并按 uni-app 的真实版本约束
> 升级到最新稳定版本。**v1 的 Taro + React 客户端已废弃**（代码可回溯至 `git show 45412fb:xsy-scm-miniapp/...`）。
> 替换原因与后续分期见 [`docs/plan/2026-09-22-小程序分支重新规划.md`](../docs/plan/2026-09-22-小程序分支重新规划.md)。

## 技术栈与版本

版本不是"挑最新的"，而是**按 uni-app 的硬约束**挑的。`@dcloudio/vite-plugin-uni` 把
`vite` 精确锁在 `5.2.8`，且 uni-app 内部内置 Vue `3.4.21` 的编译器与运行时，
因此 vite 8 / pinia 4 / vue 3.5 **都装不上**（详见下方"为什么不上更高版本"）。

| 维度 | 选型 | 版本 |
| --- | --- | --- |
| 跨端框架 | uni-app（Vue3 线） | `3.0.0-5020620260917001` |
| 视图层 | Vue | `3.4.38` |
| 构建 | Vite | `5.2.8` |
| 状态 | Pinia | `2.1.7` |
| 路由 | vue-router | `4.4.4` |
| UI 组件 | uni-ui（easycom 自动引入） | `1.5.12` |
| 样式 | Sass | `1.104.1` |
| 代码检查 | ESLint（扁平配置） | `9.39.5` |
| 格式化 | Prettier | `3.9.8` |

### 为什么不上更高版本

| 想装的版本 | 实际约束 | 结论 |
| --- | --- | --- |
| `vite@8.3.0` | `vite-plugin-uni` 的 `peerDependencies.vite` **精确等于 `5.2.8`** | 上不去 |
| `vue@3.5.43` | uni-app 内置 `@vue/compiler-sfc` / `@vue/server-renderer` = `3.4.21`，跨 minor 会出现编译器与运行时不一致 | 留在 `3.4.x` |
| `pinia@4.0.3` / `pinia@2.3.1` | peer 要求 `vue ^3.5.11` | 只能 `2.1.7`（最后一个支持 vue 3.3+ 的版本） |
| `vue-i18n@11.x` | uni-app 内部依赖 `@intlify/shared@9.1.9`；且**本项目源码零处使用 vue-i18n** | 已移除该依赖 |

## 目录结构

```
xsy-scm-miniapp/
├─ .env.*                  # 各环境接口地址与 mock 开关
├─ eslint.config.mjs       # ESLint 扁平配置（ESLint 9 起不再读 .eslintrc.*；用 .mjs 以免动 package.json 的模块类型）
├─ vite.config.js          # Vite 配置 + H5 本地开发代理 + mock 别名开关
├─ index.html              # H5 入口
├─ shims-uni.d.ts          # uni-app 类型增强
├─ mock/                   # mock 契约层（**必须在 src/ 之外**，见「Mock 契约层」）
│  ├─ index.js             #   分发器 + 路由注册 + USE_MOCK 开关
│  ├─ noop.js              #   关闭时的空实现（别名目标）
│  ├─ helpers.js           #   信封 / 延迟 / 分页
│  ├─ session.js           #   假会话（token → 客户）
│  ├─ fixtures.js          #   假数据
│  ├─ routes/              #   按域拆分的路由
│  ├─ smoke.test.mjs       #   冒烟测试
│  └─ run-smoke.mjs        #   测试运行器（esbuild 打包后执行）
└─ src/
   ├─ main.js              # 入口：createSSRApp + Pinia + 枚举插件
   ├─ App.vue              # 应用生命周期（onLaunch 恢复登录态）
   ├─ pages.json           # 路由 / 分包 / tabBar / easycom
   ├─ manifest.json        # 各端打包配置（mp-weixin AppID 待业主提供）
   ├─ uni.scss             # 全局 SCSS 变量（自动注入每个 style 块）
   ├─ api/
   │  └─ mall/             # 商城接口层（auth / home / catalog / cart / checkout / order / address / account）
   ├─ components/
   │  ├─ common/           # 通用组件（xsy-nav-bar / page-placeholder）
   │  ├─ business/         # 业务组件（商品卡、数量步进器等，待建）
   │  └─ feedback/         # 空态 / 错误态（待建）
   ├─ composables/         # 组合式函数（use-system-layout）
   ├─ lib/                 # smart-request（请求封装，含 mock 接入点）/ encrypt / sentry / support
   ├─ store/modules/       # Pinia store（system/user）
   ├─ constants/           # 常量与错误码
   ├─ styles/              # Design Tokens（tokens / mixins / global）
   ├─ utils/               # product-display（商品展示口径：库存文案 / 询价 / 可下单判断，列表与详情共用）
   ├─ pages/               # 主包：5 个 tab 页 + 登录
   ├─ pages-sub/           # 分包：auth / product / checkout / address / order / favorite / promotion / account
   ├─ plugins/             # smart-enums 插件
   ├─ static/              # 图片等静态资源
   └─ uni_modules/         # 内置组件（uni-icons / uni-data-picker / uni-scss / uni-load-more / uni-mescroll / y-tabs）
```

### 导航结构

底部 TabBar 按规划文档 §6 定为五项，订单作为一级入口（B2B 高频查单场景）：

```
首页  │  分类  │  购物车  │  订单  │  我的
```

TabBar 图标为品牌绿（选中 `#16A34A` / 未选中 `#8F959E`），由
[`tools/gen_tabbar_icons.py`](../tools/gen_tabbar_icons.py) 从参考工程的原始图标重新着色并补齐
「分类 / 购物车」两个缺失形状后生成，脚本幂等可重跑。

分包页面骨架由 [`tools/gen_pages_sub.py`](../tools/gen_pages_sub.py) 按规划 §7 页面树生成，
默认跳过已存在文件，不会覆盖已开发内容。

## 常用脚本

```bash
npm install                # 安装依赖
npm run dev:mp-weixin      # 编译微信小程序（监听，走 mock），产物 dist/dev/mp-weixin
npm run dev:mp-weixin:api  # 同上，但直连本机后端（不走 mock）
npm run dev:h5             # 本地跑 H5（Vite devServer，默认 5173 端口，走 mock）
npm run dev:h5:api         # 同上，但直连本机后端
npm run build:mp-weixin    # 生产构建微信小程序，产物 dist/build/mp-weixin
npm run build:h5           # 生产构建 H5
npm run lint               # ESLint 检查
npm run lint:fix           # ESLint 自动修复
npm run test:mock          # mock 契约层冒烟测试（47 项断言）
npm run format             # Prettier 格式化
```

微信小程序：`npm run dev:mp-weixin` 后用微信开发者工具打开 `dist/dev/mp-weixin`。

## Mock 契约层

后端 `/scm/mall/**` **尚未实现**（`xsy-scm-server` 里没有 `scm/mall` 模块，
只有 `order_source = MALL` 这个枚举值），而本分支只负责客户端。
为不让客户端开发被后端阻塞，`mock/` 目录按**冻结契约**提供了一层假后端。

**它挂在哪里**：请求层内部（`src/lib/smart-request.js`），而不是替换 `src/api/mall/**`。因此：

- 页面代码零改动，永远只调 `src/api/mall/**`；
- 返回的是真实信封 `{ code, msg, data }`（`code === 1` 为成功），
  请求层的错误处理、会话失效跳转、Toast 都会被真实走到；
- 后端就绪后**只需把 `VITE_APP_USE_MOCK` 置为 false**，无需改任何业务代码。

**开关**：

| 环境 | 值 | 效果 |
| --- | --- | --- |
| `.env.development` | `true` | 默认开发环境走 mock，无需后端 |
| `.env.localhost` | `false` | 本地联调，直连本机后端 |
| `.env.test` / `.pre` / `.production` / `.docker` | `false` | 关闭 |

**两个必须知道的约束**：

1. **`mock/` 必须在 `src/` 之外。** uni-app 会把 `src/` 整棵树镜像进构建产物
   （未被引用的文件同样会被发射），假数据留在 `src/` 里就会被打进包里。
   已验证：生产构建产物中 `海岸城门店` / `本地小白菜` 等假数据命中数为 0。
2. **引入标识是 `@mock`，不是 `@/mock`。** uni-app 自带一条 `@` → `src/` 的别名
   且优先级更高，`@/mock` 会被它先命中并解析成 `src/mock` 而报
   `Cannot find module`。`@mock` 不匹配它的 `@` 规则，能稳定落到我们自己的别名。
   关闭时该别名指向 `mock/noop.js`，进一步保证假数据不进产物。

**mock 环境固定验证码**：`123456`（`sendSmsCode` 会把验证码回显在响应里，便于自助联调；
真实环境绝不会这样做）。任意 ≥6 位密码可登录。

**契约维护**：字段形状是**暂定**的，依据 v1 冻结契约 + V2 既有领域习惯
（见 [`docs/reference/2026-09-22-遗留商城v1契约参考.md`](../docs/reference/2026-09-22-遗留商城v1契约参考.md)）。
后端落地后逐字段核对 `mock/fixtures.js` 与 `mock/routes/**` 即可，页面与 api 层不用改。

改了 mock 后跑 `npm run test:mock` 验证（覆盖认证、分页、分类过滤、关键字搜索、
详情字段（起订量 / 步进量 / 可售量 / 描述）、价格与非标品契约、未覆盖路由回落、登出等）。

## 环境配置

接口基址通过 `VITE_APP_API_URL` 注入（`src/lib/smart-request.js` 读取 `import.meta.env`）：

| 文件 | 用途 | 接口地址 |
| --- | --- | --- |
| `.env.development` | 本地开发 | `http://127.0.0.1:18080`（V2 后端 dev 端口） |
| `.env.localhost` | 本地联调 | `http://127.0.0.1:18080` |
| `.env.test` | 测试环境 | `http://127.0.0.1:18082` |
| `.env.pre` | 预发布 | 待业主提供域名 |
| `.env.production` | 生产 | 待业主提供域名 |
| `.env.docker` | 容器构建 | `/api`（同源，由 Nginx 反代） |

> 小程序端不走 Vite devServer，`VITE_APP_API_URL` 必须是**可从手机访问的 HTTPS 域名**，
> 并在微信公众平台配置为 request 合法域名。`vite.config.js` 里的 `/scm` 代理只服务于 H5 本地开发。

## 与后端的约定

1. **认证**：令牌存本地存储（key 前缀 `xsy_mall_`，见 `src/constants/local-storage-key-const.js`），
   请求头 `Authorization: Bearer <token>`。
2. **响应信封**：后端统一返回 `{ code, msg, data }`，**`code === 1` 为成功**（SmartAdmin 约定，不是 0）。
   令牌过期码 `30007` / `30008` / `30012` 由请求层统一清理会话并跳登录页。
3. **金额与数量**：一律以字符串在前后端之间传递，**禁止用 `Number()` 做求和或格式化**。
4. **归属隔离**：客户身份由服务端从登录态派生，客户端提交的 `customerId` 一律被忽略。

## 当前进度与待办

已按规划文档 §42 完成 **Step 1–4**：

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| Step 1 | 清理 reference 中不属于客户商城的 Demo 页面 | 已完成 |
| Step 2 | 重建 `pages.json` 与 TabBar | 已完成 |
| Step 3 | 确定 Design Tokens | 已完成 |
| Step 4 | 定义商城 API Client | 已完成 |
| Step 4.5 | mock 契约层（后端未实现，解除数据阻塞） | 已完成 |
| Step 5 | 制作 Figma P0 页面 | 待设计侧产出 |
| Step 6 | 按 Figma + API 垂直切片开发 | 进行中 |

Step 1 已移出（备份于 `%TEMP%/xsy-backup/removed-admin-ia`，亦可从参考工程
`project-reference-examples/xsy-scm/xsy-app/` 直接取回）：管理端页面
`enterprise` / `finance` / `form` / `goods` / `list` / `list2` / `message` / `mine`(旧) /
`notice` / `order`(旧) / `purchase` / `select-people` / `support`，以及
`api/business`、`api/support`、`constants/business`、`constants/support`、
`theme/index.scss`、`api/system/login-api.js` 和 5 个无人引用的管理端组件
（`dict-select` / `smart-card` / `smart-detail-tabs` / `smart-enum-radio` / `smart-enum-select`）。

### 已完成

- **分类页**（`pages/category/index`）：左侧一级分类 + 右侧二级分类横滑 + 商品列表，
  含分页加载、切换分类重置、空态。商品可见性与价格全部来自服务端。
- **搜索页**（`pages-sub/product/search`）：搜索历史（本地维护，最多 10 条）、
  热门搜索（服务端）、搜索结果分页、无结果态。
- **商品详情页**（`pages-sub/product/detail`）：主图占位、客户价 + 价格来源、
  库存状态与可售量、非标品「按实重结算」说明块、配送说明、商品描述、
  购买数量步进（起订量 / 步进量取自服务端契约）、底部固定加购条。
  商品不存在 / 对该客户不可见时走页面内空态，不强制跳回列表。
- **商品展示口径**（`utils/product-display.js`）：库存文案、询价判定、
  可下单判定、非标品说明等规则只写一份，商品卡与详情页共用，避免两处判断漂移。
- **商品卡组件**（`components/business/product-card.vue`）：统一承载
  「客户价 + 价格来源 + 库存状态 + 非标品标记」四项展示规则，
  无报价时展示「询价」而非 ¥0.00。

### 尚未完成

- **其余 27 个页面**仍是骨架（`PagePlaceholder` 占位），待按 §39 顺序继续：
  购物车 → 结算 → 订单 → 我的。
- **加入购物车**：商品卡与详情页的加购按钮目前只弹提示，等 §39 第 10 项（购物车）
  的 cart 契约接入后替换（`mallCartApi.addItem` 已就绪，注意 `quantity` 必须传字符串）。
- **非标品「预计金额」不在详情页**：规划 §11.2 的 `预计 ¥12.20` 依赖下单数量，
  而 §17.1 明确把「非标品预计金额」放在 `POST /orders/preview` 的响应里、§38.6 又禁止
  客户端自行算价。因此详情页只展示「按实际称重结算」规则说明，
  金额留给结算页由服务端给出；如业主坚持要在详情页看到预估，需后端在详情接口里
  一并返回该字段，客户端不自行乘算。
- **待业主提供**：微信小程序 AppID、request 合法域名、在线支付商户号、商品图片 fileKey。
- **待评估**：`src/plugins/smart-enums-plugin.js` 与 `src/constants/index.js` 是管理端枚举体系的
  残留（当前仅剩 `FLAG_NUMBER_ENUM` / `GENDER_ENUM` / `USER_TYPE_ENUM`）。
  商城侧不自行维护状态枚举（规划 §18 要求状态映射以服务端为准），
  若确认无用途可连同 `main.js` 中的注册一并移除。
- **注意**：`src/static/images/login/` 下的图标仍是参考工程的蓝色系，
  登录页之外的视觉已统一为品牌绿，后续替换登录插图时应一并改为绿色系。
- **H5 宽屏**：规划 §12 要求分类页在 H5 宽屏下改为「顶部一级分类 + 内容区分类」，
  当前只有左右栏一种布局，宽屏适配待做。
