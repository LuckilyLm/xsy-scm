# 鲜蔬源微信小程序

`xsy-scm-miniprogram/` 是给用户使用的正式微信小程序工作区，采用 uni-app + Vue 3 + Vite。旧 Taro/React 小程序已归档于 Git 历史；后续员工端使用独立工作区。

## 当前范围

本阶段仅提供：

- 后台开通后的手机号密码登录、微信手机号授权快捷登录；
- 密码方式首次登录强制修改临时密码，微信已验证手机号的会话免改密；
- 登录资料与会话恢复；
- 首页商品流、分类、搜索、商品详情和常购商品；
- 服务端购物车：加购、改数量、移除、勾选与预估金额（失效/无价商品保留并标记原因）；
- 收货地址：列表、新增/编辑、设为默认、删除；
- 订货预览、防重复处理下单、订单列表与详情、订单取消、再来一单、配送进度及售后中心；
- 客户可见性、价格、库存状态均使用 `/scm/mall/**` 服务端结果。

订单取消、再来一单、配送进度和售后中心已有源码，仍需完整联调验收。微信手机号授权入口已接入，真实取号仍需正确凭据及平台权限；短信和基于微信绑定身份的静默登录尚未开放。收货地址位置由高德地点搜索和逆地理编码返回 GCJ-02 坐标，保存前服务端重新解析省市区。

小程序仅下单，不选择付款方式、不扣余额、不接微信支付。新订单统一等待后台确认，后台读取同一张正式销售订单并补齐结算约定；历史订单及资金记录保留，钱包/账单主入口隐藏。旧支付路由由服务器明确拒绝，不能仅依赖新版客户端隐藏按钮。当前实现需要服务端 V181 迁移，源码交付不代表迁移或真机验收完成。

高德 Web Service Key 仅放在后端环境变量 `XSY_AMAP_WEB_SERVICE_KEY`。客户端地图选点使用 uni-app 原生地图组件的 GCJ-02 中心坐标，地点搜索与逆地理编码均走后端高德接口；微信小程序地图底图的实际服务商仍需真机核对，不能仅凭坐标系宣称底图来自高德。

## 约定

- 响应：`ResponseDTO{ code, ok, msg, data, dataType }`，`code === 0` 且 `ok !== false` 表示成功。
- 登录令牌：`Authorization: Bearer <token>`。
- 客户身份由服务端登录上下文派生，客户端不提交 `customerId` 作为授权依据。
- 金额与数量字段保持字符串展示，不用 JavaScript 浮点数计算业务金额。

## 开发

运行前在工作区创建被 Git 忽略的 `.env.local`，显式配置 API 地址。例如使用仓库默认 Docker Compose 部署时，可复用后台的 Nginx 入口：

```dotenv
VITE_APP_API_URL=http://127.0.0.1:7515/api
VITE_APP_USE_MOCK=false
```

端口以实际 Docker 映射为准，见 [部署说明](../deploy/README.md)。微信开发者工具需要完整地址，不能直接使用后台网页的相对路径 `/api`；本机 HTTP 联调需在工具本地设置中关闭合法域名校验。这里的回环地址仅用于同机开发者工具，真机应使用手机可访问且符合微信要求的服务地址。

```bash
npm ci
npm run dev:h5
npm run dev:mp-weixin
```

微信工具导入开发产物 `dist/dev/mp-weixin`；执行 `npm run build:mp-weixin` 后则导入 `dist/build/mp-weixin`。若导入工作区根目录，本机 `project.config.json` 的 `miniprogramRoot` 必须指向对应产物目录，不能直接运行 `src/` 中的 Vue 源码。

真机体验版使用 `npm run build:mp-weixin:test`，选择 `test` 环境并生成 `dist/build/mp-weixin`。构建前，在被 Git 忽略的 `.env.test.local` 中将 `VITE_APP_API_URL` 配置为手机可访问的完整 HTTPS 测试接口地址，并设置 `VITE_APP_USE_MOCK=false`；复用仓库 Nginx 入口时，接口地址需包含 `/api` 前缀。该文件按模式覆盖 `.env.test` 和通用 `.env.local`，无需改动本机联调地址。

微信后台配置对应的服务器合法域名，按实际功能补充上传、下载域名及隐私声明。核对本节下方的 AppID 配置后，用微信开发者工具导入构建产物并上传，在后台版本管理中选为体验版、添加体验成员，再提供二维码扫码。上传开发版本不等于提交审核或正式发布；体验版下单会写入所连接的测试后端。

API 地址在编译时写入产物，修改环境变量后需要重新编译；若磁盘产物已更新而运行时仍使用旧地址，清理微信工具的编译缓存后重新编译，不必清除登录和本地存储。

Windows调用微信CLI时，项目路径应与工具中已打开的项目一致，建议使用PowerShell的`(Resolve-Path .).Path`取得原生规范路径，避免同一目录因斜杠写法不同被工具识别为两个项目。uni-app完整构建后，若出现多处文字、图片和组件同时缺失，先核对模拟器实际页面数据与磁盘WXML是否对应；清理文件与编译缓存，再完整编译。不要通过清除登录资料处理编译缓存问题，也不要用H5显示正常代替微信端验收。

直连本机后端时，将 API 地址改为实际后端端口；以下命令选择 `localhost` 模式，需要自行提供对应模式的环境配置：

```bash
npm run dev:h5:api
npm run dev:mp-weixin:api
```

开发环境默认可通过 `VITE_APP_USE_MOCK=true` 使用 `mock/` 中与正式约定一致的认证和目录数据；test/pre/prod/docker 环境均关闭。生产别名只引用 `mock/noop.js`，构建产物不得包含 fixtures。

## 微信手机号授权配置

密码表单保持常驻。其下的微信图标使用原生 `open-type="getPhoneNumber"`，用户同意后只把 `event.detail.code` 发往 `/scm/mall/auth/wechat/phone-login`。这不是 `wx.login` 的 code，也不需要客户端提交手机号；账号与会话规则见根目录 `docs/decisions.md`。

在部署后端的环境中配置以下变量（模板见根目录 `.env.example`），不能写入 `VITE_*` 或客户端：

```dotenv
XSY_WECHAT_APP_ID=YOUR_MINIPROGRAM_APP_ID
XSY_WECHAT_APP_SECRET=YOUR_MINIPROGRAM_APP_SECRET
```

AppID 必须与实际小程序一致；更换小程序时同步核对 `src/manifest.json` 的 `mp-weixin.appid`、开发者工具的 `project.config.json` 与后端配置，重新编译后通过 `wx.getAccountInfoSync()` 核对实际运行的 AppID。微信后台需具备手机号获取权限和可用额度，完成隐私声明、服务端调用IP白名单及小程序请求域名等实际要求。配置后重新部署后端使变量生效；仅轮换后端密钥无需重新编译小程序。

微信公众平台的“设置 → 服务内容声明 → 用户隐私保护指引”需按实际功能声明：手机号码用于识别客户账号和登录，地址用于填写收货资料和配送，位置信息用于地图选点。填写开发者联系方式，保存对应的开发版指引后再测试预览或真机调试；前端的 `__usePrivacyCheck__` 不能代替微信后台声明。平台返回 `errno=112`、`api scope is not declared in the privacy agreement` 或 `appid privacy api banned` 时，微信会在返回手机号授权码之前拒绝调用，应先修正平台指引，不能当作系统账号未开通。参考[微信隐私协议开发指南](https://developers.weixin.qq.com/miniprogram/dev/framework/user-privacy/PrivacyAuthorize.html)。

自行开发的小程序以微信公众平台页面的实际指引和能力权限为准。第三方代开发的 `getprivacysetting` 接口返回 `code_exist=0`，可能只是没有通过第三方 `commit` 接口上传代码，不能据此认定后台未配置指引；设置接口返回成功也应重新读取或在平台页面核对结果。参考[查询隐私保护指引](https://developers.weixin.qq.com/doc/oplatform/openApi/miniprogram-management/privacy-management/api_getprivacysetting.html)。

未配置时后端返回业务码50381，授权code失效为40098，手机号未开通采购账号为40381；停用和登录锁定仍使用原业务码。拒绝授权或缺少code不发起登录请求。真实微信取号单独验证，不能以模拟事件、模拟HTTP响应或密码登录成功替代。

官方文档：[手机号授权](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html)、[服务端取号](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-info/phone-number/api_getphonenumber.html)、[stable_token](https://developers.weixin.qq.com/miniprogram/dev/server/API/mp-access-token/api_getstableaccesstoken.html)。

## 商城配置同步

后台“营销中心 → 商城装修”可配置主题、首页组件、弹窗、四类说明页和店铺资料。说明页与首页主题发布后生效；店铺资料及弹窗保存后生效。小程序返回对应页面或恢复前台时读取当前配置，无需重新编译；已停留的页面不做实时推送。

首页组件为空时仍可发布主题，默认商品与活动区域继续显示。首页和“我的”共用顶部渐变，默认浅绿白；后台四季及春节、端午、中秋、国庆预设同时切换全局主色与顶部背景，也能分别微调色值，保存后需发布，回滚同时恢复。所有正式页面的按钮、链接、选中项和导航文字读取同一主题；登录背景与表单浅底、原生业务弹窗确认色也一起切换。其他品牌区、主要动作和领券入口继续使用主题派生渐变；交付图片保留原色，成功、警告、错误等颜色保持业务含义。店铺名称用于首页、我的和登录页；有效 HTTPS 链接在“我的”中提供复制入口。

商品分类图标在后台“商品管理 → 商品分类”独立选择或上传，保存后首页和分类页显示同一张公开图片；未配置显示通用图标。分类页支持二级及三级筛选，启用空分类保留入口，商品数量不决定分类是否展示。

分类、快捷与空状态使用用户交付的轻3D PNG。分类图片从正式分类配置读取，统一入口为 `src/utils/category-icon.js`；快捷与空状态映射集中于 `src/constants/mall-visual-const.js`。来源与原图哈希见后台图标库及 `media/` 各组 `sources.json`。主图与专题通过后台上传发布；图片完整展示自带文案。发布营销内容后仍保留真实常购、新品和商品目录。

## 图片资源发布

首页、启动与共享代码、全局样式、导航和表单小图保持包内。分类、快捷、我的功能、空状态与登录装饰原图保存在 `media/`，通过 MinIO 在线加载；该目录不进入代码包。商品图、轮播图、广告图及说明正文继续由正式接口提供，不另建静态副本。

部署前先发布原图，再构建小程序：

```bash
python -m pip install -r tools/requirements-media.txt
# 通过环境变量提供 XSY_FILE_ACCESS_KEY 和 XSY_FILE_SECRET_KEY
python tools/upload-media.py --endpoint https://storage.example.com --bucket images --public-url-prefix https://assets.example.com/images
```

脚本核对 `media/manifest.json` 的原图摘要，上传到 `public/image/mall-ui/<digest>`，匿名下载逐张校验，并输出公开资源根地址；重复执行不会重传相同对象。已有对象内容不符时停止，不覆盖。存储桶须允许匿名读取此公开目录；存储凭据只能用于发布环境。

将输出地址填入对应构建环境的 `VITE_APP_MEDIA_URL`（模板见 `.env.example`），然后执行微信构建；资源目录摘要必须匹配当前清单。正式环境使用 HTTPS 并配置微信图片/下载合法域名。更换图片时更新清单并发布新目录，保留旧目录供已发布客户端使用，不修改已发布对象。远程插画失败时显示标准图标；登录装饰失败保留背景，文字和操作继续可用。恢复网络后再次进入页面可重试。

Logo 和导航图保持本地 PNG；微信按钮图标使用无损 WebP，其他表单小图仍为本地 PNG。组件沿用 easycom 按需引用及 `lazyCodeLoading: requiredComponents`，不额外引入打包重排插件。

主包保留首页、登录、Tabbar页面及基础共享逻辑；其他业务页面以 `pages-sub` 为普通分包根目录，内部仍按业务子目录组织，页面URL保持不变。仅业务分包使用的接口、组合函数、映射及优惠券组件位于 `pages-sub/shared`；分包复用主包能力，主包不得反向引用分包。首页预加载该业务分包，避免不同业务页共享模块被留在主包或跨包读取。

## 验证

默认不运行测试或构建；用户授权后按需使用下列入口，结果保留在对应验证记录中。

```bash
npm run lint
npm test
npm run build:h5
npm run build:mp-weixin
node tools/verify-media.mjs
node tools/verify-package.mjs
```

本地构建或 mock 测试不代表微信主体、合法域名、真机兼容、审核或发布已完成。

“我的”页头不显示手机号，点击头像进入个人资料查看当前客户名称和登录手机号；个人资料页面需要登录，只读展示。
