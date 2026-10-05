# XSY-SCM 登录页视觉改造指导

> 建议文件路径：`docs/login-ui-redesign-guide.md`  
> 适用项目：`xsy-scm-web`  
> 基线提交：`27ce989c7fe601d927a733eded63545a6a33ad74`  
> 当前登录路由：`/login -> src/views/system/login3/login.vue`  
> 编写目的：彻底去除 SmartAdmin 原登录页的视觉遗留，建立“鲜蔬源智链”自己的品牌登录体验，同时不触碰登录认证逻辑。

---

# 1. 改造背景

当前登录页虽然已经替换为“鲜蔬源智链”文案，但整体视觉仍然明显继承 SmartAdmin：

- 仍使用 `login-bg.png` 蓝白科技球背景；
- 左侧仍使用 `login-min.gif` 科技控制台动画；
- 登录按钮和副标题仍写死 `#1748FD` 蓝色；
- 左右两块固定 `460 × 600`，结构偏旧；
- 页面更像“通用后台模板”，而不是“生鲜供应链系统”；
- 当前 SCM 后台已经形成绿色主题、统一 Card、统一按钮和统一视觉基础，登录页与系统内部割裂；
- 登录页大量固定尺寸，对较小屏幕和窗口缩放适应较弱。

本次改造目标不是“换一张背景图”，而是：

> **让用户第一眼就知道这是鲜蔬源自己的供应链系统，而不是 SmartAdmin 换皮。**

---

# 2. 改造目标

最终登录页应体现以下关键词：

- 鲜蔬
- 供应链
- 专业
- 清爽
- 稳定
- 可信
- 企业后台
- 轻科技感
- 不浮夸
- 不模板化

视觉目标：

> **绿色品牌 + 浅色空间 + 清晰登录卡片 + 供应链业务表达**

不要做成：

- 蓝紫科技大屏；
- SaaS 官网落地页；
- 游戏登录页；
- 过度玻璃拟态；
- 大面积渐变；
- 高饱和霓虹；
- 大量动态粒子；
- 3D 球体；
- 卡通蔬菜插画。

---

# 3. 业务与技术边界

本次只改登录页 UI/UX。

## 3.1 不允许修改

以下逻辑必须保持不变：

- 用户名登录；
- 密码加密逻辑；
- `encryptData()`；
- Captcha 获取；
- Captcha 自动刷新；
- 点击验证码刷新；
- 邮箱二次验证码；
- 60 秒邮箱验证码倒计时；
- Enter 回车登录；
- Remember Password；
- `loginApi.login()`；
- 登录成功后的 token 保存；
- `useUserStore().setUserLoginInfo()`；
- 字典初始化；
- `buildRoutes()`；
- 登录后跳转 `/home`；
- SmartLoading；
- 错误捕获；
- 权限；
- 后端接口；
- 登录 DTO；
- Captcha DTO；
- 2FA DTO。

## 3.2 可以修改

允许调整：

- HTML 布局；
- CSS / LESS；
- 图标；
- 文案层级；
- 登录表单视觉；
- 左侧品牌区；
- 响应式；
- 背景；
- 按钮样式；
- 输入框高度；
- 间距；
- Logo；
- 页面 Footer；
- 当前 `login-min.gif` 的使用方式或彻底移除。

---

# 4. 当前文件

重点文件：

```text
xsy-scm-web/src/views/system/login3/login.vue
xsy-scm-web/src/views/system/login3/login.less
xsy-scm-web/src/router/system/login.ts
```

当前路由已经使用：

```text
/login
  -> /@/views/system/login3/login.vue
```

本次不要新建 `login4`。

直接把 `login3` 改造成正式的 XSY 登录页。

原因：

- 避免继续出现 login / login2 / login3 / login4 多套并存；
- 当前正式路由已经指向 login3；
- 本轮目标是“替换正式登录视觉”，不是再增加一个 Demo。

---

# 5. 推荐最终结构

桌面端建议采用：

```text
┌────────────────────────────────────────────────────────────────────────┐
│                                                                        │
│   鲜蔬源 Logo                                                          │
│                                                                        │
│     ┌──────────────────────────────────────────────────────────────┐   │
│     │                                                              │   │
│     │   品牌 / 产品价值                         欢迎回来             │   │
│     │                                          登录鲜蔬源智链        │   │
│     │   鲜蔬源智链                                                   │   │
│     │   智慧供应链管理平台                       用户名               │   │
│     │                                          [                ]   │   │
│     │   连接商品、客户、采购、                  密码                  │   │
│     │   仓储、配送与财务                        [                ]   │   │
│     │                                          验证码                │   │
│     │   [供应链视觉插画 / 业务节点图]           [          ][验证码]  │   │
│     │                                                              │   │
│     │                                          □ 记住密码           │   │
│     │                                          [     登录       ]   │   │
│     │                                                              │   │
│     └──────────────────────────────────────────────────────────────┘   │
│                                                                        │
│             © 2026 鲜蔬源智慧供应链管理平台                            │
└────────────────────────────────────────────────────────────────────────┘
```

---

# 6. 整体布局

## 6.1 页面背景

移除：

```less
background: url(/@/assets/images/login/login-bg.png) no-repeat center;
```

改为 CSS 背景。

建议：

```less
background:
  radial-gradient(circle at 12% 20%, rgba(22, 163, 74, .10), transparent 34%),
  radial-gradient(circle at 88% 12%, rgba(20, 184, 166, .08), transparent 30%),
  linear-gradient(135deg, #f7fbf8 0%, #f3f8f5 48%, #f8faf9 100%);
```

效果：

- 左上淡绿；
- 右上淡青；
- 中央仍以浅白为主；
- 没有强装饰图；
- 视觉更耐看。

可以再加入极轻网格，但透明度必须非常低，不要形成“科技大屏”质感。

---

# 7. 主 Card

推荐：

```text
max-width: 1040px
min-height: 580px
```

样式：

```less
border-radius: 20px;
background: rgba(255,255,255,.96);
border: 1px solid rgba(15,23,42,.06);
box-shadow:
  0 24px 64px rgba(15, 23, 42, .08),
  0 4px 16px rgba(15, 23, 42, .04);
overflow: hidden;
```

桌面比例：

```text
左侧：54%
右侧：46%
```

不要粗边框、强阴影、蓝色描边和重玻璃拟态。

---

# 8. 左侧品牌区

建议：

```text
[鲜蔬源 Logo]

鲜蔬源智链
智慧供应链管理平台
```

标题：

```text
鲜蔬源智链
```

副标题：

```text
智慧供应链管理平台
```

不要继续把“欢迎登录 鲜蔬源智链”当品牌标题。

右侧登录区负责表达“欢迎回来”。

---

# 9. 品牌价值文案

推荐二选一：

```text
连接商品、客户、采购、仓储、配送与财务，
让供应链协同更简单。
```

或：

```text
一套系统，贯通商品、订单、采购、库存、配送与结算。
```

控制在 2 行，不要写营销长文案。

---

# 10. 左侧视觉元素

当前 `login-min.gif` 建议彻底停止使用。

原因：

- 约 1.5 MB；
- 视觉偏通用“科技平台”；
- 与生鲜供应链业务弱相关；
- 动画持续抢注意力；
- 与系统内部绿色视觉不一致。

## 推荐：轻量供应链节点图

例如：

```text
      商品
       ↓
供应商 → 仓库 → 分拣 → 配送
               ↓
              客户
               ↓
              财务
```

使用现有 Ant Design Icons / 简单 SVG / CSS Card 表达。

建议图标：

```text
商品      AppstoreOutlined
供应商    ShopOutlined
仓库      InboxOutlined
分拣      SlidersOutlined
配送      CarOutlined
客户      TeamOutlined
财务      AccountBookOutlined
```

每个节点：

- 44～52px 高；
- 8～12px 圆角；
- 极浅绿色或白底；
- 1px 浅边框；
- 图标 18～20px；
- 主色绿色；
- 连线 1px 浅绿色。

不要做 AI 大脑、数字孪生、未来宇宙、旋转球。

---

# 11. 动效原则

允许：

- 页面初次进入轻微 fade；
- 左侧节点轻微 stagger；
- Login Card 300ms 上浮；
- Hover 微弱变化。

禁止：

- 无限循环大动画；
- 粒子；
- 旋转球；
- 闪光；
- 跳动；
- 背景持续移动；
- 视频背景；
- 循环 GIF。

支持：

```css
@media (prefers-reduced-motion: reduce)
```

自动关闭动画。

---

# 12. 右侧登录区

标题：

```text
欢迎回来
```

副标题：

```text
登录鲜蔬源智链管理后台
```

建议：

```text
标题 28px / 700
副标题 14px / secondary
```

不再使用孤立的“账号登录”。

---

# 13. 表单布局

推荐：

```text
用户名
[ icon 请输入用户名                    ]

密码
[ icon 请输入密码                  eye ]

验证码
[ 请输入验证码             ][验证码图片]

□ 记住密码

[              登录                 ]
```

右侧不要再套第二层明显 Card。

---

# 14. 输入框规范

建议：

```text
高度：46px
圆角：8px
```

默认：

```text
border #e5e7eb
background #fff
```

Focus：

```text
border 主题绿
box-shadow: 0 0 0 3px rgba(primary, .10)
```

输入图标建议：

```text
用户名：UserOutlined
密码：LockOutlined
验证码：SafetyCertificateOutlined
```

图标默认 secondary，focus 时可变主题色。

---

# 15. 密码提示

当前：

```text
请输入密码：至少三种字符，最小 8 位
```

过长。

建议 Placeholder：

```text
请输入密码
```

如果当前业务确实需要提示密码规则，可在输入框下以辅助文案显示：

```text
至少 8 位，包含三类字符
```

如果登录阶段实际上不校验这条规则，则不要凭空增加提示。

---

# 16. 验证码

Input 与图片统一高度：

```text
46px
```

图片建议：

```less
width: 120px;
height: 46px;
border-radius: 8px;
border: 1px solid #e5e7eb;
cursor: pointer;
```

Hover 使用主题边框。

图片增加：

```text
alt="登录验证码"
```

可以加 Tooltip：

```text
点击刷新验证码
```

---

# 17. 登录按钮

当前 `#1748FD` 必须删除。

使用当前系统主题绿色：

```text
var(--scm-primary)
```

或复用 `themeColors[colorIndex]` / ConfigProvider token。

建议：

```text
height: 48px
border-radius: 8px
font-weight: 600
```

Hover：

- 略深；
- 非常轻的阴影。

不要蓝色、渐变和发光。

当前 `<div class="btn">` 建议换为 Ant Design Button，但必须保持 `onLogin()`、validate、loading 逻辑不变。

---

# 18. 记住密码

保留原功能。

放在验证码后 12～16px。

不要单独占太大垂直空间。

---

# 19. 邮箱二次验证码

`emailCodeShowFlag = true` 时自然插入：

```text
邮箱验证码
[ 输入邮箱验证码              ][获取验证码]
```

按钮：

- Secondary；
- 46px 高；
- 110～120px 宽；
- disabled 状态明显。

不改变倒计时逻辑。

---

# 20. Logo

复用当前项目正式鲜蔬源 Logo。

不要：

- 临时重新画品牌 Logo；
- 用 favicon 放大；
- 用 emoji；
- 自己做一套品牌字。

桌面高度建议：

```text
34～40px
```

---

# 21. Footer

页面底部：

```text
© 2026 鲜蔬源智慧供应链管理平台
```

12～13px secondary color。

不要出现：

- SmartAdmin；
- 模板作者；
- GitHub；
- 开源框架版本。

---

# 22. 响应式

## >= 1200px

完整左右布局。

主 Card：

```text
960～1040px
```

## 900～1199px

Card：

```text
width: calc(100vw - 64px)
```

左侧适当收窄。

## <= 900px

隐藏左侧品牌视觉区。

只保留：

- Logo；
- 欢迎回来；
- 登录表单；
- Footer。

Card：

```text
width: min(440px, calc(100vw - 40px))
```

## <= 576px

页面：

```text
padding: 20px
```

登录区域：

```text
padding: 28px 24px
```

验证码保持 input + image，不要溢出。

---

# 23. CSS 命名

建议把旧的：

```text
.box-item
.desc
.login
.more
.title-box
```

换成语义类：

```text
.xsy-login
.xsy-login__shell
.xsy-login__brand
.xsy-login__logo
.xsy-login__headline
.xsy-login__description
.xsy-login__visual
.xsy-login__panel
.xsy-login__title
.xsy-login__subtitle
.xsy-login__form
.xsy-login__captcha
.xsy-login__footer
```

---

# 24. 推荐 DOM 结构

```vue
<template>
  <main class="xsy-login">
    <section class="xsy-login__shell">
      <aside class="xsy-login__brand">
        <img class="xsy-login__logo" ... />

        <div class="xsy-login__brand-copy">
          <h1>鲜蔬源智链</h1>
          <p>智慧供应链管理平台</p>
        </div>

        <p class="xsy-login__description">
          一套系统，贯通商品、订单、采购、库存、配送与结算。
        </p>

        <div class="xsy-login__visual">
          ...
        </div>
      </aside>

      <section class="xsy-login__panel">
        <header>
          <h2>欢迎回来</h2>
          <p>登录鲜蔬源智链管理后台</p>
        </header>

        <a-form ...>
          ...
        </a-form>
      </section>
    </section>

    <footer class="xsy-login__footer">
      © 2026 鲜蔬源智慧供应链管理平台
    </footer>
  </main>
</template>
```

---

# 25. 当前资源处理

本轮停止引用：

```text
src/assets/images/login/login-min.gif
src/assets/images/login/login-bg.png
```

本轮暂时不要删除其余旧资源：

```text
login.gif
login-min.gif
login-bg*.png
left-bg*.png
ali-icon.png
douyin-icon.png
feishu-icon.png
google-icon.png
qq-icon.png
wechat-icon.png
weibo-icon.png
```

确认无路由、页面、测试引用后，再单独提交：

```text
chore(ui): remove legacy smartadmin login assets
```

不要把视觉重构和大规模资产清理混成一个提交。

---

# 26. 不建议做的设计

禁止：

- 蓝紫主视觉；
- 大面积玻璃拟态；
- 1920×1080 大 PNG 背景；
- GIF 核心动画；
- 粒子；
- 霓虹；
- 重阴影；
- 渐变登录按钮；
- 复杂插画抢过表单；
- 大段营销文案；
- 额外引入 UI / 动画框架。

---

# 27. 交互细节

必须保留：

- Tab 顺序；
- Enter 登录；
- 密码显示/隐藏；
- Captcha 点击刷新；
- Email Code；
- Countdown；
- Remember Password；
- 登录失败 Captcha 刷新；
- 登录成功立即进入系统。

不要增加无意义 Toast 或登录成功动画。

---

# 28. 无障碍

建议：

```text
autocomplete="username"
autocomplete="current-password"
```

验证码：

```text
alt="登录验证码"
```

Logo：

```text
alt="鲜蔬源智链"
```

登录按钮不要继续用纯 div 模拟。

建议：

```vue
<a-button
  type="primary"
  block
  class="xsy-login__submit"
  @click="onLogin"
>
  登录
</a-button>
```

前提是验证现有 validate / click 行为完全不变。

---

# 29. 推荐实施步骤

1. 只调整 `login3/login.vue` DOM 结构；
2. `script` 中业务逻辑尽量逐行不动；
3. 重写 `login3/login.less`；
4. 去掉 `login-bg.png`；
5. 去掉 `login-min.gif`；
6. 去掉所有 `#1748FD`；
7. 实现轻量供应链视觉；
8. 实现 900px / 576px 响应式；
9. 验证 Email 2FA；
10. 验证 Captcha；
11. 验证 Enter 登录；
12. 运行前端门禁；
13. 截图人工检查。

---

# 30. 推荐最终文案

左侧：

```text
鲜蔬源智链

智慧供应链管理平台

一套系统，贯通商品、订单、采购、库存、配送与结算。
```

右侧：

```text
欢迎回来

登录鲜蔬源智链管理后台
```

字段：

```text
用户名
请输入用户名

密码
请输入密码

验证码
请输入验证码

记住密码

登录
```

---

# 31. 验收标准

## 视觉

- [ ] 第一眼不再像 SmartAdmin 默认登录页。
- [ ] 不再出现蓝白球体背景。
- [ ] 不再出现旧科技 GIF。
- [ ] 不再使用 `#1748FD` 登录主色。
- [ ] 主色与后台鲜蔬源绿色一致。
- [ ] Card 比例合理。
- [ ] 左右层级清晰。
- [ ] 表单不空、不挤。
- [ ] Logo 清晰。
- [ ] 1920×1080 下视觉重心合理。
- [ ] 1440×900 下完整可见。
- [ ] 1366×768 不垂直溢出。
- [ ] 小屏不卡死、不溢出。

## 功能

- [ ] 用户名可输入。
- [ ] 密码可输入。
- [ ] 密码显示/隐藏正常。
- [ ] Captcha 正常加载。
- [ ] Captcha 点击刷新。
- [ ] Enter 可登录。
- [ ] Remember Password 正常。
- [ ] Email 2FA 正常。
- [ ] Email countdown 正常。
- [ ] 登录失败正常刷新 Captcha。
- [ ] 登录成功正常跳首页。
- [ ] 权限路由初始化正常。

## 工程

- [ ] 不新增 UI 框架。
- [ ] 不引入大体积动画库。
- [ ] 不增加 1MB+ 新背景图。
- [ ] 不修改登录 API。
- [ ] 不修改后端。
- [ ] TypeScript 通过。
- [ ] ESLint 通过。
- [ ] Build 通过。
- [ ] 现有登录相关 tests / e2e 通过。

---

# 32. 推荐截图尺寸

必须人工检查：

```text
1920 × 1080
1440 × 900
1366 × 768
1024 × 768
390 × 844
```

重点看：

- Card 是否垂直溢出；
- Captcha 是否挤压；
- Footer 是否盖住内容；
- 左侧是否应隐藏；
- Logo 是否过大；
- 登录按钮是否过宽；
- 输入框是否够高。

---

# 33. 提交建议

本次独立提交：

```text
feat(ui): redesign xsy login experience
```

只包含：

```text
login3/login.vue
login3/login.less
必要的轻量登录视觉资源
相关测试
```

不要同时：

- 删除全部旧资源；
- 改首页；
- 改 Sidebar；
- 改 Theme Foundation；
- 改登录后端。

---

# 34. 给 AI 的执行指令

```text
基于当前仓库最新 HEAD 改造正式登录页：

xsy-scm-web/src/views/system/login3/login.vue
xsy-scm-web/src/views/system/login3/login.less

目标：彻底去除 SmartAdmin 默认登录页视觉，改为“鲜蔬源智链”自己的绿色供应链登录体验。

必须：
1. 去掉 login-bg.png 背景引用；
2. 去掉 login-min.gif；
3. 去掉 #1748FD 蓝色主视觉；
4. 使用鲜蔬源现有主题绿色；
5. 桌面端使用左右双栏，左品牌右登录；
6. 左侧使用轻量供应链视觉节点，不引入大 GIF / 视频；
7. 右侧标题改为“欢迎回来 / 登录鲜蔬源智链管理后台”；
8. 登录 Card 整体 960~1040px 左右，圆角 20px，轻阴影；
9. 输入框高度约 46px，圆角 8px；
10. 登录按钮使用 Ant Design 主按钮 / 当前主题色；
11. 验证码输入和图片高度一致；
12. <=900px 隐藏左侧，只显示登录区；
13. <=576px 适配手机宽度；
14. 支持 prefers-reduced-motion；
15. 图片 alt / 输入 autocomplete 等基础无障碍补齐。

严禁修改：
- loginApi
- encryptData
- captcha 获取与刷新逻辑
- email 2FA
- Remember Password
- Enter 登录
- token 保存
- userStore 初始化
- dict 初始化
- buildRoutes
- 登录后跳转
- 后端接口
- DTO

先只重构模板和样式，script 中业务逻辑尽量保持逐行不动。

不要：
- 新建 login4
- 加蓝紫渐变
- 使用大面积玻璃拟态
- 使用科技球 / 粒子 / 霓虹
- 使用循环 GIF
- 引入新的 UI 框架
- 引入动画库
- 顺便删除所有旧登录资源

完成后执行：
- TypeScript
- ESLint
- build
- 现有登录相关 tests / e2e

并提供 1920×1080、1440×900、390×844 三档截图或人工验证结果。
```

---

# 35. 最终判断标准

改造成功不是“更炫”。

而是用户打开登录页时能感受到：

> **这是一个成熟、稳定、清爽的生鲜供应链业务系统。**

而不是：

> “这是 SmartAdmin 的登录模板换了公司名字。”
