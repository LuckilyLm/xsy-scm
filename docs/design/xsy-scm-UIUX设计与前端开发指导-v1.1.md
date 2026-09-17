# 鲜蔬源智慧供应链管理平台 UI/UX 设计与前端开发指导

> 历史参考：本文保留原始业务/设计语义。技术栈、实施状态和进度均为 legacy 快照；当前实现以 V2 对应波次的 target-design、approval 与验收报告为准。已删除的旧计划及验证记录可从 Git 历史恢复。

> ## ⚠️ 前端技术实现部分已废止（2026-09-14）
>
> 本文档的**三套视觉系统划分（Admin / Screen / Mall）、品牌识别、信息架构意图与验收思路**
> 仍有参考价值。
>
> 但所有 **React 相关的技术实现约束已废止**，不再作为实现依据：
>
> - React / ProComponents / TanStack Query / Zustand / React Hook Form / Zod 约定 → **废止**。
> - 双级侧栏（一级 + 二级）布局要求 → **废止**（V2 采用 SmartAdmin 原生 Layout）。
> - V2 管理后台为 **Vue3 + TypeScript + Ant Design Vue**，Layout、菜单、Tabs、表格、表单、
>   弹窗、上传与权限指令**全部采用 SmartAdmin 原生方案**，不重新发明。
> - 数据大屏与商城端在各自迁移阶段再确定实现方式。
>
> 现行依据：[`../../SMARTADMIN_REFERENCE_RULES.md`](../../SMARTADMIN_REFERENCE_RULES.md)。
> 具体 Vue3 页面约定以 `xsy-scm-web` 既有实现为准。

> 项目名称：鲜蔬源智慧供应链管理平台
> 页面简称：鲜蔬源智链
> 项目代码：`xsy-scm`
> 文档版本：v1.1
> 文档用途：用于统一后台管理端、数据大屏、商城端的视觉风格、组件规范、AI Skill 使用方式、页面开发流程与验收标准。
> 适用范围：产品设计、前端开发、AI 辅助开发、代码 Review、UI 验收，以及电子秤等现场硬件交互页面。

---

## 1. 文档目标

本项目包含三类不同视觉场景：

1. ERP / SCM 管理后台
2. 数据可视化大屏
3. 商城 / 小程序

三类场景可以拥有不同视觉主题，但必须保持统一的品牌识别与工程规范。

本指导文档的目标是：

- 避免不同页面由 AI 或不同开发人员实现后风格不一致。
- 固定后台页面的信息架构、颜色、尺寸、间距和交互习惯。
- 固定大屏的深色科技风格。
- 明确商城页面与后台页面的视觉边界。
- 固定 Ant Design / ProComponents 使用方式。
- 规定 UI/UX Skill 的职责边界。
- 形成可被 Agent 直接读取和执行的前端规则。

---

# 2. 产品视觉定位

## 2.1 品牌关键词

```text
鲜蔬
供应链
高效
可靠
专业
数字化
现代企业
生鲜配送
```

后台管理端整体应呈现：

> 现代、专业、高信息密度、克制、清晰的中国企业 SaaS / ERP 风格。

不追求消费级 App 的视觉花哨，不使用营销 Landing Page 风格，也不使用过度艺术化的页面表现。

---

## 2.2 三套视觉主题

整个项目按以下三套 Theme 管理。

```text
xsy-scm
│
├─ Admin Theme
│   鲜蔬源绿
│
├─ Screen Theme
│   科技蓝
│
└─ Mall Theme
    可配置节日 / 生鲜主题
```

### Admin Theme

用于：

- 首页
- 商品
- 客户
- 订单
- 采购
- 库存
- 分拣
- 配送
- 财务
- 报表
- 系统管理

核心色：

```text
#00B96B
```

---

### Screen Theme

用于：

- 数据大屏
- 配送大屏
- 分拣大屏
- 溯源大屏

核心视觉：

```text
深蓝背景
+
电光蓝
+
青色
+
高对比信息展示
```

---

### Mall Theme

用于：

- 微信公众号商城
- 小程序
- H5 商城
- 营销活动页
- 节日主题页

商城允许主题化，例如：

```text
默认生鲜绿
春节红
618 红
夏日蓝
端午绿
中秋紫
年货节橙红
```

商城视觉可丰富，但后台管理壳仍必须保持 Admin Theme。

---

# 3. UI Skill 使用规范

## 3.1 全局 Skill

建议保留：

```text
frontend-design
web-design-guidelines
ui-ux-pro-max
playwright-cli
playwright-best-practices
```

职责：

### frontend-design

用于：

- 页面视觉实现
- 布局优化
- 前端设计质量
- 视觉细节调整

---

### web-design-guidelines

用于：

- Web 设计基本规范
- 可用性
- 可访问性
- 一致性检查
- 页面结构审查

---

### ui-ux-pro-max

用于：

- UX 方案辅助
- 交互合理性
- 信息层级
- 用户流程检查

---

### playwright-cli

用于：

- 最终浏览器验收
- 页面截图检查
- 功能路径验证
- Console / Network 检查

---

### playwright-best-practices

用于：

- E2E 测试实现
- Playwright 测试结构
- 浏览器自动化规范

---

# 4. 项目级 Skill

鲜蔬源项目建议增加：

```text
impeccable
vercel-react-best-practices
vercel-composition-patterns
xsy-scm-design
```

---

## 4.1 Impeccable

作为本项目的主要 UI Skill。

主要负责：

- 企业 SaaS / Dashboard / ERP 页面
- 信息密度控制
- 页面层级
- 视觉一致性
- Dashboard 设计
- 表格、表单、统计页优化

本项目所有后台业务页面，应优先使用 Impeccable。

---

## 4.2 vercel-react-best-practices

用于保证：

- React 页面实现质量
- 性能
- 状态管理
- 数据请求
- 渲染策略
- Bundle 控制

它不负责决定品牌视觉。

---

## 4.3 vercel-composition-patterns

用于：

- 公共组件抽象
- Design System
- 复杂组件 API
- 页面组合模式
- 避免复制粘贴 UI

---

## 4.4 xsy-scm-design

这是本项目建议自定义的 Skill。

职责：

> 固定鲜蔬源项目自己的视觉规则、组件规则、页面模板、颜色、尺寸和禁止项。

建议项目目录：

```text
.skills/
└─ xsy-scm-design/
   └─ SKILL.md
```

---

# 5. 不建议默认启用的 Skill

主 ERP 后台不建议默认使用：

```text
Hallmark
Taste Skill
```

原因：

Hallmark 更适合：

- Landing Page
- 营销页面
- 品牌展示页

Taste 类 Skill 更适合：

- 创意页面
- 强视觉实验
- 品牌视觉探索

鲜蔬源后台属于：

```text
企业管理系统
+
高信息密度 ERP
+
大量 CRUD
```

因此不应让营销类 Skill 主导设计。

---

## 5.1 Hallmark 的适用场景

以下页面可以临时启用：

```text
商城首页
618 活动页
春节活动页
端午活动页
新品营销页
品牌介绍页
```

但不要让它修改 Admin Theme。

---

# 6. 管理后台 Design System

## 6.1 主色

```text
Primary          #00B96B
Primary Hover    #20C77A
Primary Active   #009A59

Success          #16A34A
Warning          #FAAD14
Error            #FF4D4F
Info             #1677FF
```

---

## 6.2 中性色

```text
页面背景          #F5F7F9
卡片背景          #FFFFFF

一级文字          #1F2329
二级文字          #4E5969
辅助文字          #86909C

边框              #E5E6EB
分割线            #F0F0F0
```

---

## 6.3 一级侧边栏

建议保留现有参考系统中的深色一级导航。

```text
背景：
#202631

默认文字：
#FFFFFF

默认图标：
#FFFFFF

Hover：
#2C3440

Active：
#00B96B
```

结构：

```text
┌──────┬──────────────┬─────────────────────┐
│一级栏│ 二级菜单      │ 页面内容             │
│      │              │                     │
│首页  │              │                     │
│商品  │ 商品档案      │                     │
│订单  │ 订单列表      │                     │
│采购  │ 采购单        │                     │
│库存  │ 库存查询      │                     │
│配送  │ 配送任务      │                     │
└──────┴──────────────┴─────────────────────┘
```

建议：

```text
一级菜单宽度：80px
二级菜单宽度：140px 左右
```

---

# 7. 后台尺寸规范

建议统一：

```text
Header Height        56px

Primary Sidebar      80px
Secondary Sidebar    140px

Page Padding         24px

Card Radius          6px

Button Height        32px
Input Height         32px

Table Row Height     48~52px

Body Font            14px
Small Text           12px

Page Title           18~20px
Section Title        16px
```

---

# 8. 后台页面设计原则

后台页面必须满足：

```text
清晰
克制
专业
紧凑
高信息密度
快速操作
```

禁止默认使用：

```text
大面积渐变
玻璃拟态
巨型圆角
超大留白
夸张阴影
超大标题
营销 Banner 风格
彩色卡片堆叠
过度动画
```

---

# 9. 标准业务页面结构

商品、客户、供应商、订单、采购、库存、分拣、配送等业务页面，优先使用统一模板。

```text
┌───────────────────────────────────────┐
│ 页面 Tab / 标题                       │
├───────────────────────────────────────┤
│ 查询条件                              │
│ 分类 [ ]  日期 [ ]  搜索 [ ]          │
│                  查询 重置 高级筛选    │
├───────────────────────────────────────┤
│ 新增 批量导入 导出 更多               │
├───────────────────────────────────────┤
│                                       │
│               Data Table              │
│                                       │
├───────────────────────────────────────┤
│ 汇总信息                    Pagination│
└───────────────────────────────────────┘
```

标准结构：

```text
Tab / Title
→ Search
→ Toolbar
→ Table
→ Summary
→ Pagination
```

---

# 10. 页面组件规范

建议建立公共组件。

```text
PageContainer
SearchPanel
PageToolbar
DataTable
StatusTag
AmountText
CustomerSelector
SupplierSelector
ProductSelector
WarehouseSelector
ScaleDeviceSelector
WeightDisplay
DeviceStatusBadge
DateRangeFilter
SummaryBar
OperationColumn
```

禁止：

每个业务页面自己重新写一套：

```text
搜索栏
按钮样式
表格容器
分页区域
状态颜色
```

---

# 11. Ant Design / ProComponents 规范

推荐：

```text
Ant Design
+
@ant-design/pro-components
```

优先使用：

```text
ProLayout
ProTable
ProForm
ModalForm
DrawerForm
ProDescriptions
ProCard
StatisticCard
```

---

## 11.1 列表页面

优先：

```text
ProTable
```

而不是自行拼：

```text
Form
+
Table
+
Pagination
```

---

## 11.2 新增 / 编辑

简单数据：

```text
ModalForm
```

复杂数据：

```text
DrawerForm
```

大型单据：

```text
独立详情 / 编辑页面
```

---

# 12. 表格设计规范

表格是 ERP 中最重要的 UI。

建议：

```text
表头背景：
浅灰

数据行：
白色

Hover：
浅绿色

Selected：
#E8F8F0
```

---

## 12.1 对齐规则

```text
名称 / 文本：
左对齐

数量：
右对齐

金额：
右对齐

日期：
居中或左对齐

状态：
居中

操作：
右侧固定
```

---

## 12.2 金额

金额统一：

```text
¥ 1,280.50
```

建议：

```css
font-variant-numeric: tabular-nums;
```

避免金额数字跳动。

---

## 12.3 状态

状态统一使用：

```text
Tag
```

示例：

```text
已完成    Green
处理中    Blue
待审核    Orange
已取消    Gray
异常      Red
```

---

## 12.4 操作栏

常用操作：

```text
查看
编辑
审核
打印
更多
```

危险操作：

```text
删除
作废
取消
```

必须使用红色语义，并进行二次确认。

---

# 13. 表单规范

字段布局：

普通业务表单：

```text
2 列
```

复杂页面：

```text
2~3 列
```

不要出现：

```text
一行 6~8 个输入框
```

---

## 13.1 必填项

使用：

```text
*
```

并在提交时统一校验。

---

## 13.2 金额

统一使用：

```text
InputNumber
```

---

## 13.3 日期

统一：

```text
DatePicker
RangePicker
```

---

## 13.4 业务对象选择

统一使用业务 Selector：

```text
ProductSelector
CustomerSelector
SupplierSelector
WarehouseSelector
DriverSelector
VehicleSelector
ScaleDeviceSelector
```

不要在多个页面分别实现不同版本。

---

# 14. Dashboard 首页设计

首页应以经营信息与待办为核心。

推荐结构：

```text
经营指标
+
趋势图
+
待办事项
+
业务排行
+
预警
```

首页不要成为：

```text
功能入口拼盘
```

建议：

顶部：

```text
下单金额
订单数量
客单价
客户数
```

中部：

```text
订单趋势
采购趋势
库存预警
配送状态
```

右侧：

```text
待审核订单
待采购
待分拣
待发货
待售后
```

---

# 15. 数据大屏 Design System

## 15.1 颜色

```text
Background          #06152F
Panel               #071E42
Panel Secondary     #092851

Border              #1565B8

Primary Blue        #00A8FF
Cyan                #20E3FF

Primary Text        #EAF6FF
Secondary Text      #8FB7D9

Highlight           #FFD166
Danger              #FF5B5B
```

---

# 16. 大屏结构

统一按照：

```text
顶部标题 / 时间
│
├─ 左侧 KPI / 进度
├─ 中央地图 / 核心图
├─ 右侧排名 / 消息
└─ 底部大屏切换
```

---

## 16.1 大屏基准尺寸

统一设计：

```text
1920 × 1080
```

采用整体等比例缩放。

不要使用普通 Web 页面那种自由响应式布局。

---

# 17. 大屏公共组件

建立：

```text
ScreenLayout
ScreenHeader
ScreenPanel
ScreenTitle
KpiCard
ProgressPanel
RankingChart
ChartPanel
MapPanel
ScreenFooterNav
```

---

# 18. 大屏技术建议

```text
ECharts
DataV-React
高德地图 JS API
```

用途：

### ECharts

```text
折线
柱状
环形
排行
面积图
地图分布
```

### DataV-React

```text
科技边框
装饰
数字翻牌
发光元素
```

### 高德地图

```text
车辆定位
配送轨迹
配送线路
客户配送点
司机位置
```

---

# 19. 大屏与后台数据关系

原则：

> 大屏只读取业务数据，不独立维护业务数据。

例如：

```text
ERP 订单
    ↓
Spring Boot
    ↓
/api/screens/data/overview
    ↓
数据大屏
```

禁止：

```text
大屏自己维护订单数量
大屏自己维护客户数
```

---

# 20. 商城 UI 设计原则

商城与后台完全分离。

后台：

```text
专业
高密度
克制
```

商城：

```text
视觉丰富
消费级
促销感
品牌感
```

---

# 21. 商城模板

商城支持：

```text
默认主题
春节
618
夏至
端午
中秋
年货节
开业
季节主题
```

管理后台只负责：

```text
预览
选择
编辑
发布
```

商城主题不影响 Admin Theme。

---

# 22. 设计 Token 工程规范

建议：

```text
src/styles/
├─ tokens.ts
├─ admin-theme.ts
├─ screen-theme.ts
└─ mall-theme.ts
```

---

## 22.1 Admin Token 示例

```ts
export const adminTokens = {
  colorPrimary: '#00B96B',
  colorBgLayout: '#F5F7F9',
  colorBgContainer: '#FFFFFF',
  colorText: '#1F2329',
  colorTextSecondary: '#4E5969',
  colorBorder: '#E5E6EB',
};
```

---

## 22.2 ConfigProvider

```tsx
<ConfigProvider
  theme={{
    token: adminTokens,
  }}
>
  <App />
</ConfigProvider>
```

---

# 23. 禁止硬编码颜色

禁止：

```css
color: #27ad73;
background: #03a95f;
border: 1px solid #cccccc;
```

应该使用：

```text
Design Token
CSS Variable
Theme Token
```

---

# 24. 项目级 xsy-scm-design Skill 模板

建议创建：

```text
.skills/xsy-scm-design/SKILL.md
```

内容可采用：

```md
# XSY SCM Design Skill

## Product

鲜蔬源智慧供应链管理平台

## Product Style

现代中国企业 SaaS / ERP。

设计目标：

- 专业
- 高信息密度
- 清晰
- 克制
- 高效率
- 生鲜供应链品牌感

## Admin Theme

Primary: #00B96B
Sidebar: #202631
Background: #F5F7F9
Container: #FFFFFF
Text: #1F2329

## Layout

Header: 56px
Primary Sidebar: 80px
Secondary Sidebar: 140px
Page Padding: 24px

## Standard Page

Tab
→ Search
→ Toolbar
→ Table
→ Summary
→ Pagination

## Components

优先：

Ant Design
ProComponents

## Rules

- 使用现有 Design Token。
- 使用现有 AdminLayout。
- 优先复用公共组件。
- 不为单一页面引入新 UI 框架。
- 不随意改变品牌主色。
- 不重新设计项目整体风格。

## Avoid

- Glassmorphism
- 巨型圆角
- Landing Page 风格
- 大面积渐变
- 过度动画
- 过度阴影
```

---

# 25. AI 开发页面的标准流程

以后不允许只给 Agent：

```text
帮我开发商品页面
```

应使用固定流程。

---

## Step 1：读取规则

Agent 必须先读取：

```text
docs/design/DESIGN_SYSTEM.md
docs/design/ADMIN_UI.md

或：

.skills/xsy-scm-design/SKILL.md
```

---

## Step 2：读取现有实现

优先查找：

```text
已有 Layout
已有 Table
已有 Selector
已有 SearchPanel
已有类似业务页面
```

---

## Step 3：分析页面

必须先明确：

```text
页面目的
用户角色
查询条件
Tab
列表字段
操作按钮
状态
分页
汇总
新增方式
编辑方式
详情方式
```

---

## Step 4：确定复用组件

例如：

```text
PageContainer
SearchPanel
ProTable
CustomerSelector
StatusTag
SummaryBar
```

---

## Step 5：实现

使用：

```text
Ant Design
ProComponents
React Query
已有 Design Token
```

---

## Step 6：自检

至少运行：

```text
npm run lint
npm run typecheck
npm run test
npm run build
```

---

## Step 7：浏览器验收

Playwright 检查：

```text
Console
Network
布局
滚动
1920px
1440px
常见笔记本分辨率
主要操作路径
```

---

# 26. Agent 页面开发提示词模板

可以直接复用：

```text
开发鲜蔬源智慧供应链管理平台的【页面名称】。

项目：
xsy-scm

在开始编码前必须读取：

1. .skills/xsy-scm-design/SKILL.md
2. docs/design/DESIGN_SYSTEM.md
3. 当前 AdminLayout
4. 现有同类页面和公共组件

UI 主 Skill：
impeccable

实现规范：
vercel-react-best-practices
vercel-composition-patterns

UI Review：
frontend-design
web-design-guidelines

要求：

1. 不重新设计项目整体风格。
2. 严格沿用鲜蔬源 Admin Theme。
3. 主色使用 #00B96B 对应 Design Token。
4. 使用现有一级菜单 + 二级菜单布局。
5. 优先使用 Ant Design 和 ProComponents。
6. 不引入新的主 UI 组件库。
7. 优先复用 SearchPanel、PageContainer、Selector、StatusTag 等公共组件。
8. 页面结构遵循：
   Tab → Search → Toolbar → Table → Summary → Pagination。
9. 所有颜色使用 Design Token，不硬编码随机颜色。
10. 保持企业 ERP 的高信息密度，不做 Landing Page 风格。
11. 表格金额右对齐，状态使用 Tag，操作列固定在右侧。
12. 删除、作废等危险操作必须二次确认。

开始编码前先输出：

- 页面信息架构
- 可复用组件
- 需要新增的组件
- 页面数据模型
- 实现计划

完成后执行：

npm run lint
npm run typecheck
npm run test
npm run build

最后用 Playwright 检查：

- Console
- Network
- 布局
- 关键交互
- 页面滚动
- 常见桌面分辨率
```

---

# 27. 新页面 UI Review Checklist

每个页面完成后检查：

## 布局

- [ ] 是否使用统一 AdminLayout
- [ ] 一级/二级菜单是否正确
- [ ] Page Padding 是否一致
- [ ] 页面内容是否过宽
- [ ] 是否存在不必要的大面积空白

## 色彩

- [ ] 主色是否使用 Token
- [ ] 是否出现随机绿色
- [ ] 是否出现过多彩色
- [ ] Error / Warning / Success 是否语义正确

## 表格

- [ ] 表头一致
- [ ] 行高一致
- [ ] 金额右对齐
- [ ] 状态使用统一 Tag
- [ ] 操作列固定
- [ ] 分页一致

## 表单

- [ ] Label 对齐
- [ ] 必填规则一致
- [ ] 输入框高度一致
- [ ] 业务对象使用 Selector
- [ ] 金额使用 InputNumber
- [ ] 日期使用 DatePicker

## 交互

- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Success
- [ ] 删除确认
- [ ] 保存反馈
- [ ] 防止重复提交

## 工程

- [ ] 没有重复组件
- [ ] 没有引入第二套 UI 框架
- [ ] 没有硬编码随机颜色
- [ ] 没有无意义 CSS
- [ ] 类型完整
- [ ] lint 通过
- [ ] typecheck 通过
- [ ] build 通过

---

# 28. 第一批建议建设的公共 UI

项目启动阶段优先完成：

```text
AdminLayout
PrimarySidebar
SecondarySidebar
TopHeader

PageContainer
PageTabs
SearchPanel
PageToolbar
SummaryBar

StatusTag
AmountText
EmptyState

ProductSelector
CustomerSelector
SupplierSelector
WarehouseSelector

ScreenLayout
ScreenHeader
ScreenPanel
KpiCard
RankingChart
MapPanel
```

---

# 29. 前端目录建议

```text
src/
├─ api/
├─ assets/
├─ components/
│  ├─ common/
│  ├─ business/
│  └─ screen/
│
├─ layouts/
│  ├─ AdminLayout/
│  └─ ScreenLayout/
│
├─ pages/
│  ├─ dashboard/
│  ├─ product/
│  ├─ customer/
│  ├─ supplier/
│  ├─ order/
│  ├─ purchase/
│  ├─ inventory/
│  ├─ sorting/
│  ├─ delivery/
│  ├─ finance/
│  ├─ marketing/
│  ├─ traceability/
│  ├─ system/
│  └─ screen/
│
├─ styles/
│  ├─ tokens.ts
│  ├─ admin-theme.ts
│  ├─ screen-theme.ts
│  └─ mall-theme.ts
│
├─ router/
├─ stores/
├─ hooks/
├─ utils/
└─ types/
```

---

# 30. 推荐的最终 Skill 组合

```text
全局
├─ frontend-design
├─ web-design-guidelines
├─ ui-ux-pro-max
├─ playwright-cli
└─ playwright-best-practices

xsy-scm 项目
├─ impeccable
├─ vercel-react-best-practices
├─ vercel-composition-patterns
└─ xsy-scm-design
```

商城营销页按需临时使用：

```text
Hallmark
```

不建议把多个视觉设计 Skill 同时作为主导。

---

# 31. 最终执行原则

鲜蔬源项目所有前端页面必须遵循以下原则：

1. Admin、Screen、Mall 三套 Theme 明确分离。
2. ERP 后台以鲜蔬源绿色作为唯一主品牌色。
3. 大屏采用科技蓝，不继承后台绿色主视觉。
4. 商城允许主题化，但商城后台仍使用 Admin Theme。
5. Ant Design + ProComponents 作为唯一主 UI 体系。
6. Impeccable 作为 ERP 页面主要 UI Skill。
7. React 最佳实践与组件组合 Skill 负责实现质量，不改变视觉基线。
8. 自定义 `xsy-scm-design` Skill 固化项目自己的设计规则。
9. 页面优先复用公共组件，不重复实现。
10. 不允许 Agent 随意重构整体视觉。
11. 所有颜色、尺寸优先通过 Design Token 管理。
12. 所有新页面必须经过 Playwright 浏览器验收。
13. ERP 页面追求高效率，而不是追求视觉炫技。
14. 大屏追求视觉展示，但不得自己维护业务数据。
15. 商城和营销页面的视觉创新不能污染管理后台。
16. 电子秤等硬件页面使用统一设备状态与称重组件。
17. 实时重量与已确认业务重量必须在 UI 上严格区分。
18. 设备厂商协议不得进入 React 业务页面。

---

# 32. 推荐落地顺序

建议按以下顺序实施：

```text
1. 创建 xsy-scm-design Skill
2. 创建 Design Token
3. 创建 AdminLayout
4. 创建一级 / 二级导航
5. 创建 PageContainer
6. 创建 SearchPanel
7. 创建通用 ProTable 页面模板
8. 完成商品档案页面
9. 基于商品页沉淀 CRUD Template
10. 扩展客户 / 供应商 / 采购 / 订单页面
11. 创建 Screen Theme
12. 创建 ScreenLayout
13. 开发第一张数据大屏
14. 在采购/分拣开发前完成 WeightDisplay、DeviceStatusBadge 与 Mock Weight Provider
15. 电子秤到场后完成 Device Agent 联调与称重页面验收
16. 最后进入 Mall Theme 与商城页面
```

第一阶段必须先把后台基础设计体系稳定下来，再大规模生成业务页面。

---

# 33. 电子秤与现场设备交互 UI 规范

电子秤属于生产现场关键设备，其交互页面不能按普通表单处理。

适用场景：

```text
采购实重收货
分拣实重
复核称重
库存称重（按需）
```

## 33.1 核心设计目标

称重界面优先保证：

```text
一眼看清
少点击
防误操作
设备状态明确
重量变化实时
稳定重量突出
异常可恢复
人工修改可审计
```

现场人员可能佩戴手套、操作频繁，因此按钮、重量显示和状态反馈应明显大于普通后台表单。

---

## 33.2 推荐页面结构

```text
┌────────────────────────────────────────────┐
│ 业务对象：采购单 / 分拣任务 / 客户订单      │
├────────────────────────────────────────────┤
│ 商品：大白菜             计划：5.00 kg      │
│ 客户：XX食堂             规格：散装          │
├────────────────────────────────────────────┤
│                                            │
│                当前净重                    │
│                                            │
│                  5.28                      │
│                   kg                       │
│                                            │
│            ● 重量稳定 / 等待稳定            │
│                                            │
├────────────────────────────────────────────┤
│ 设备：SCALE-01   已连接   毛重/皮重信息      │
├────────────────────────────────────────────┤
│ [重新称重] [去皮] [确认称重] [确认并打印]    │
└────────────────────────────────────────────┘
```

---

## 33.3 重量显示

重量是页面最高视觉优先级。

建议：

```text
重量数字：48~72px
单位：18~24px
使用 tabular-nums
```

状态：

```text
STABLE      稳定，可确认
UNSTABLE    重量变化中
DISCONNECTED 设备断开
ERROR       设备异常
```

颜色语义必须通过 Token 管理，不允许页面自行定义随机颜色。

建议：

```text
稳定：Success
变化中：Warning
断开：Error
异常：Error
空闲：Info / Secondary
```

---

## 33.4 设备状态

称重页面必须始终显示：

```text
设备名称
设备编号
连接状态
最后数据时间
当前单位
```

禁止出现：

> 页面显示旧重量，但用户不知道设备已经断开。

当超过设定时间未收到设备数据时，应自动进入：

```text
STALE / DISCONNECTED
```

并禁止直接确认旧重量。

---

## 33.5 稳定重量确认

默认原则：

```text
电子秤实时数据
→ 判断稳定
→ UI 显示“重量稳定”
→ 用户确认
→ 写入业务记录
```

重量未稳定时：

- “确认称重”默认不可用，或必须进行明显的风险确认。
- 不自动把实时跳动重量写进正式订单。
- 不因 WebSocket 高频消息反复弹 Toast。

---

## 33.6 人工输入 / 人工覆盖

系统应保留人工输入能力作为异常兜底，但不能与自动称重混淆。

人工覆盖时至少要求：

```text
修改后的重量
修改原因
操作人
时间
原始自动重量（如有）
```

UI 上必须明确标识：

```text
自动采集
人工录入
人工修正
```

不能让后续审计无法判断重量来源。

---

## 33.7 断线与异常状态

设备断开：

```text
电子秤已断开
请检查 USB / 串口 / 电源或本地 Device Agent
```

提供操作：

```text
重新连接
刷新设备
切换设备
查看诊断信息
```

但普通操作人员不展示复杂串口原始参数。

设备诊断与串口配置应放在：

```text
系统管理
→ 设备管理
```

---

## 33.8 称重操作按钮

建议关键按钮尺寸高于普通后台按钮：

```text
Height: 40~48px
```

主要操作：

```text
确认称重
确认并打印
确认收货
完成分拣
```

次要操作：

```text
重新称重
去皮
切换设备
```

危险操作：

```text
覆盖重量
撤销称重
作废记录
```

必须二次确认。

---

## 33.9 公共组件

建议建立：

```text
WeightDisplay
ScaleDeviceSelector
DeviceStatusBadge
WeightStatusIndicator
WeightActionBar
WeightHistoryDrawer
DeviceDiagnosticDrawer
```

业务页面不得重复实现不同版本的称重组件。

---

## 33.10 与 Device Agent 的前端边界

React 前端只处理标准化事件，例如：

```json
{
  "deviceId": "SCALE-01",
  "status": "STABLE",
  "grossWeight": 12.56,
  "tareWeight": 0.35,
  "netWeight": 12.21,
  "unit": "kg",
  "timestamp": "2026-09-02T15:20:30"
}
```

前端禁止：

```text
解析不同厂商的串口报文
硬编码 COM3 / COM4
把波特率散落在业务页面
直接把厂商 SDK 逻辑写入 React 页面
```

协议、驱动、串口与 SDK 由 `xsy-device-agent` 负责。

---

## 33.11 Playwright / 联调验收

正式设备接入前，前端应支持 Mock Weight Provider，用模拟数据验证：

- [ ] 未连接状态
- [ ] 已连接空闲
- [ ] 重量连续变化
- [ ] 重量稳定
- [ ] 设备断线
- [ ] 自动重连
- [ ] 重量为 0
- [ ] 超出误差阈值
- [ ] 人工覆盖
- [ ] 确认并打印
- [ ] 同一记录避免重复提交

真实设备到场后增加：

- [ ] USB / 串口拔插
- [ ] Device Agent 重启
- [ ] 浏览器刷新
- [ ] 同时开启两个页面
- [ ] 长时间连续称重
- [ ] 标签打印联动

---

# 34. UI 开发新增原则：硬件页面

涉及电子秤、标签打印机、扫码枪的页面额外遵循：

1. 设备状态永远可见。
2. 实时数据和正式业务数据严格区分。
3. 稳定重量才进入默认确认流程。
4. 自动采集与人工录入必须显示来源。
5. 设备断开后禁止误用缓存旧值。
6. 高频设备消息不得造成页面抖动或 Toast 风暴。
7. 设备异常应支持恢复，不要求操作人员重启整个 ERP。
8. 所有称重页面统一复用设备组件。
9. 厂商协议不进入 React 业务组件。
10. 真实设备未到场前必须使用 Mock Provider 完成 UI 和业务流程联调。
