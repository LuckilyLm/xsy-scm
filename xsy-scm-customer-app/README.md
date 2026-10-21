# 鲜蔬源客户商城

`xsy-scm-customer-app/` 是客户商城正式移动工作区，采用 uni-app + Vue 3 + Vite，首发目标为微信小程序。旧 Taro/React 客户端已归档于 Git 历史；后续员工端使用独立工作区。

## 当前范围

本阶段仅提供：

- 后台开通后的手机号、密码登录；
- 临时密码首次登录强制修改；
- 登录资料与会话恢复；
- 首页商品流、分类、搜索、商品详情和常购商品；
- 服务端购物车：加购、改数量、移除、勾选与预估金额（失效/无价商品保留并标记原因）；
- 收货地址：列表、新增/编辑、设为默认、删除；
- 结算预览、幂等下单、订单列表与详情、订单取消、再来一单、配送进度及售后中心；
- 客户可见性、价格、库存状态均使用 `/scm/mall/**` 服务端结果。

订单取消、再来一单、配送进度和售后中心已有源码，仍需完整联调验收；短信登录和微信登录尚未开放。收货地址位置由高德地点搜索和逆地理编码返回 GCJ-02 坐标，保存前服务端重新解析省市区。

高德 Web Service Key 仅放在后端环境变量 `XSY_AMAP_WEB_SERVICE_KEY`。客户端地图选点使用 uni-app 原生地图组件的 GCJ-02 中心坐标，地点搜索与逆地理编码均走后端高德接口；微信小程序地图底图的实际服务商仍需真机核对，不能仅凭坐标系宣称底图来自高德。

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
