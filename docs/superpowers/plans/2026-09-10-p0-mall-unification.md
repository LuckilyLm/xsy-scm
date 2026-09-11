# P0-1 跨端商城统一配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立统一商城首页与主题聚合协议，使后台配置同时驱动 Web 商城和 Taro 小程序首页。

**Architecture:** 后端 `mall` 提供客户端聚合接口，复用 marketing 的首页板块与促销查询，并以稳定的 `MallHomeResponse`/`MallThemeConfig` 作为跨端契约。Web 和小程序分别将同一主题协议映射为 CSS 变量与 Taro 样式，不在客户端复制运营规则。

**Tech Stack:** Spring Boot 3.5.x, Java 21, MyBatis-Plus, PostgreSQL, React 19, React Router, Taro 4.2.1, React 18, TypeScript。

**Spec:** `docs/2026-09-10-Web端P0与跨端商城统一设计.md`

## Global Constraints

- 后台只维护一套商城首页与主题配置。
- Web 商城和 Taro 小程序商城读取同一套聚合接口。
- 主题外观与促销活动分离；非法主题值使用客户端默认值降级。
- 客户端不直接拼接小程序路由，不复制后台营销业务规则。
- 保持现有 `ApiResponse`、鉴权、错误处理和代码风格。

---

### Task 1: 建立后端商城聚合契约

**Files:**
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/vo/MallThemeConfig.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/vo/MallHomeSectionResponse.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/vo/MallHomeResponse.java`
- Modify: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/controller/MallCatalogController.java` or add `MallHomeController.java`
- Modify/Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/service/MallCatalogService.java`

**Interfaces:**
- Produces `GET /api/mall/home -> ApiResponse<MallHomeResponse>`。
- Produces `GET /api/mall/theme -> ApiResponse<MallThemeConfig>`。
- `MallHomeResponse` 字段：`theme`、`sections`、`categories`、`promotions`、`generatedAt`。

- [ ] 为统一响应建立 Java VO，字段与设计文档 TypeScript 接口一一对应。
- [ ] 复用 marketing 的有效首页板块和促销查询，过滤未启用、未生效、已删除内容。
- [ ] 固定板块按 `sortOrder,id` 返回；主题缺失时返回默认主题。
- [ ] 仅暴露客户端需要的公开商城字段，不泄露后台审计字段。
- [ ] 增加 Controller 接口并保持 `/api/mall/**` 的现有鉴权策略。
- [ ] 执行 `D:\Maven\apache-maven-3.9.16\bin\mvn.cmd -o test-compile`。

### Task 2: 接入 Web 商城首页

**Files:**
- Create: `xsy-scm-web/src/api/mall.ts`
- Create: `xsy-scm-web/src/types/mall.ts`
- Create: `xsy-scm-web/src/pages/mall/MallHomePage.tsx`
- Create/Modify: `xsy-scm-web/src/pages/mall/mall-home.css`
- Modify: `xsy-scm-web/src/router/index.tsx`
- Modify: `xsy-scm-web/src/layouts/AdminLayout/navigation.tsx`

**Interfaces:**
- Consumes `GET /api/mall/home`。
- Maps `MallThemeConfig` to CSS custom properties.
- Renders Banner、分类、活动、推荐/新品板块和商品列表；空数组显示明确空状态。

- [ ] 定义 Web 类型与后端 VO 字段。
- [ ] 实现统一 HTTP 请求、加载态、错误态、空态。
- [ ] 使用 CSS 变量应用主题颜色、背景、圆角、卡片样式。
- [ ] 注册 `/mall` 路由，并使首页入口指向商城而不是占位页面。
- [ ] 保持导航使用可访问的 Link/button，图标按钮有 aria-label。
- [ ] 执行 Web `tsc --noEmit` 和生产构建。

### Task 3: 接入 Web 配置管理与预览

**Files:**
- Create: `xsy-scm-web/src/api/marketing.ts`
- Create: `xsy-scm-web/src/types/marketing.ts`
- Create: `xsy-scm-web/src/pages/marketing/HomeSectionPage.tsx`
- Create: `xsy-scm-web/src/pages/marketing/PromotionPage.tsx`
- Create: `xsy-scm-web/src/pages/marketing/CouponPage.tsx`
- Modify: `xsy-scm-web/src/router/index.tsx`
- Modify: `xsy-scm-web/src/layouts/AdminLayout/navigation.tsx`

- [ ] 提供首页板块查询、排序、上下线和有效期展示。
- [ ] 提供促销活动和优惠券列表，操作按钮遵循权限控制。
- [ ] 提供只读手机预览，预览数据复用商城聚合响应结构。
- [ ] 将“保存/发布”与“主题切换”分开表达，避免混淆。
- [ ] 对批量或破坏性操作提供确认对话框。
- [ ] 执行 Web 测试和构建。

### Task 4: 统一小程序首页协议

**Files:**
- Create/Modify: `xsy-scm-miniapp/src/services/mall.ts`
- Create/Modify: `xsy-scm-miniapp/src/types/mall.ts`
- Modify: `xsy-scm-miniapp/src/pages/home/*`
- Modify: `xsy-scm-miniapp/src/app.css`

- [ ] 小程序首页改为调用 `/api/mall/home`，不再维护独立首页配置。
- [ ] 按相同 `sectionType/sortOrder/payload` 解析板块。
- [ ] 将主题协议映射为页面样式，并对非法值回退默认主题。
- [ ] 保持游客/客户鉴权边界，价格仍由服务端计算。
- [ ] 执行小程序 `tsc --noEmit` 和 `taro build --type weapp`。

### Task 5: 跨端契约验证

**Files:**
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/mall/MallHomeContractTest.java`（若现有测试基础允许）
- Modify: `xsy-scm-web/src/pages/mall/MallHomePage.test.tsx`
- Modify: `xsy-scm-miniapp` 对应测试文件（若已有测试框架）

- [ ] 校验后端响应包含统一主题、板块顺序、活动和生成时间字段。
- [ ] 校验 Web 与小程序使用相同 `sectionType` 和主题枚举。
- [ ] 校验空态、错误态和非法主题降级行为。
- [ ] 统一执行后端、Web、小程序验证并记录未能在本地环境执行的集成项。
