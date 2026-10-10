# 鲜蔬源客户商城

`xsy-scm-customer-app/` 是客户商城正式移动工作区，采用 uni-app + Vue 3 + Vite，首发目标为微信小程序。它与冻结的 `xsy-scm-miniapp/`、后续员工端相互独立。

## 当前范围

本阶段仅提供：

- 后台开通后的手机号、密码登录；
- 临时密码首次登录强制修改；
- 登录资料与会话恢复；
- 首页商品流、分类、搜索、商品详情和常购商品；
- 客户可见性、价格、库存状态均使用 `/scm/mall/**` 服务端结果。

购物车、结算、下单、订单、地址、短信登录和微信登录尚未开放。可售商品只展示“购物车暂未开放”，不会伪装加购成功。

## 契约

- 响应：`ResponseDTO{ code, ok, msg, data, dataType }`，`code === 0` 且 `ok !== false` 表示成功。
- 登录令牌：`Authorization: Bearer <token>`。
- 客户身份由服务端登录上下文派生，客户端不提交 `customerId` 作为授权依据。
- 金额与数量字段保持字符串展示，不用 JavaScript 浮点数计算业务金额。

## 开发

```bash
npm ci
npm run dev:h5
npm run dev:mp-weixin
```

直连本机后端：

```bash
npm run dev:h5:api
npm run dev:mp-weixin:api
```

开发环境默认可通过 `VITE_APP_USE_MOCK=true` 使用 `mock/` 中与正式契约一致的认证和目录数据；test/pre/prod/docker 环境均关闭。生产别名只引用 `mock/noop.js`，构建产物不得包含 fixtures。

## 验证

```bash
npm run lint
npm test
npm run build:h5
npm run build:mp-weixin
```

本地构建或 mock 测试不代表微信主体、合法域名、真机兼容、审核或发布已完成。
