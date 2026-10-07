# 首页「供应链工作台」改造设计

> 状态：设计已定稿，待开工（按评审意见修订过一次）
> 依据版本：`4f10ee0c`。文中行号、SQL、字段名、组件名均来自该版本实际代码。
> 规则依据：[SCM UI 长期规范](../architecture/scm-ui-guidelines.md) §8；注释与文案约束见 [code-comment-guidelines](../architecture/code-comment-guidelines.md)。

## 0. 结论摘要

1. 首页现在是 SmartAdmin 示例骨架（加班统计 / 代码提交量 / 假销量 / 毒鸡汤 / 天气 iframe），与 SCM 业务无关，**整体换成「供应链工作台」**。
2. 首页要的数据后端**已经有了**（`screen` 模块 + `/scm/dashboard/todo`），不需要新写统计 SQL。
3. 但**不能直接让首页调 `/scm/screen/*`**：它挂 `scm:screen:query`，语义与授权范围都不对。
4. **真正的风险不是首页好不好看，而是口径已经分叉**：大屏与报表对「今日销售额」用的是**同一列、不同时间轴**（见 §2 实测）。首页如果自己再写一份，就是第三个数字。
5. 因此先收口径，再做界面：抽 `ScmBusinessMetricsService`，**一个业务指标只有一处口径定义**（不是「所有查询都必须走一个万能 Service」）。
6. 口径命名成为长期规则（见 §3.4）：`今日销售额 / 今日订单` 走**确认轴**，`今日下单金额 / 今日下单数` 走**创建轴**；**不得把 `created_at + CONFIRMED` 叫「今日销售额」**。

## 1. 现状核对

### 1.1 首页（`xsy-scm-web/src/views/system/home/`）

`index.vue` 当前挂载：

| 组件 | 内容 | 处置 |
| --- | --- | --- |
| `components/echarts/pie.vue` | 「加班统计」，赵六 / 张三假数据 | 删除 |
| `components/echarts/gradient.vue` | 「代码提交量」，孙七 / 周八假数据 | 删除 |
| `components/echarts/category.vue` | 「销量统计」，实为假数据 | 删除 |
| `components/echarts/gauge.vue` | 「业绩完成度」。**`index.vue` 并没有 import 它**，`saleTargetPercent` 全仓只有模板那一处引用、没有定义 → 该卡实际不渲染，只剩第 74 行一句死注释 `// 业绩完成百分比` | 删除 |
| `home-header.vue` | 天气 iframe（`//i.tianqi.com/...`）、毒鸡汤（`heart-sentence.ts`）、农历节气、上次登录 / IP | 大幅精简（见 §5.2） |
| `components/changelog-card.vue` | 系统更新日志 | 迁入 `components/system-changelog.vue`，降级到末行 |
| `components/to-be-done-card/home-to-be-done.vue` | 浏览器 `localStorage` 个人记事本 | **整套功能删除**（见 §6 批次 4b，不止是首页组件） |
| `components/business-todo-card/home-business-todo.vue` | SCM 真实业务待办（`GET /scm/dashboard/todo`） | **保留并强化** |
| `home-notice.vue` ×2 | OA 通知公告 | 保留，降视觉权重 |
| `components/quick-entry/*` | 快捷入口，默认项是「菜单 / 请求 / 缓存 / 字典 / 单号」 | **保留机制，入口全部换成 SCM 业务入口**（见 §5.2） |

顺带一个现存缺陷：`home-notice.vue` 第 7 行写死 `title="通知公告"`，组件 `props` 只声明了 `noticeTypeId`。`index.vue` 传的 `title="公告"` / `title="通知"` 是**死 prop**，两张卡标题实际相同。本次一并修掉。

### 1.2 已有后端能力（不需要新造）

`com.xsy.scm.screen`：

- `ScreenDataController` → `/scm/screen/data/{business,inventory,purchase,trend,geo}`，全部 `@SaCheckPermission(scm:screen:query)`。
- `ScreenDataService` 已接 `ScmDataScopeService`，每个请求解析一次 `ScmDataScopeContext` 再下传各 DAO；`ScreenDataDao` 的 `scope` 是必填参数。
- 已覆盖：今日订单 / 下单金额 / 结算金额 / 成交客户 / 活跃供应商 / 客户与 SKU 总数 / 客户与商品销售 TOP10 / 今日采购单与采购额 / 今日收货单 / 库存总量与在库 SKU / 启用仓库数 / 今日出入库笔数 / 库存健康五档 / 7d·30d 趋势八条序列 / 地理分布。

`com.xsy.scm.dashboard`：

- `ScmTodoController` → `GET /scm/dashboard/todo`，`scm:todo:query`；`ScmTodoVO{key,label,count,route}`。
- 服务端按「待办权限 ∩ 领域权限」逐卡计算，无权卡片直接省略，动作仍由各领域接口鉴权。

### 1.3 权限与路由现状

- `/screen` 是**前端静态路由**（`router/business/screen.ts`，`hideInMenu: true`），路由本身不拦人，只有 API 会 403 → 首页的「进入运营大屏」按钮必须按 `hasPermission('scm:screen:query')` 显隐。
- `scm:screen:query`（V28）与 `scm:todo:query`（V46）目前**只授给 role 1**。

## 2. 核心问题：口径已经分叉（实测）

同一件事「今天的销售金额」，大屏与报表用的是**同一列、不同时间轴**：

| 出口 | 文件 | 显示名 | 金额列 | 时间轴 | 过滤 |
| --- | --- | --- | --- | --- | --- |
| 大屏 `sumSettlementAmount` | `screen/ScreenDataMapper.xml` | **「今日销售额」** | `settlement_total_amount` | **`created_at`** | `deleted = FALSE AND status = 'CONFIRMED'` |
| 大屏 `countConfirmedOrders` | 同上 | 「今日订单」 | — | **`created_at`** | 同上 |
| 报表 `overviewKpi.confirmedOrderAmount` | `report/ReportDao.xml` | 「确认订单金额」 | `settlement_total_amount` | **`confirmed_at`** | 同上 |

补充两点实测事实：

- 大屏前端「今日销售额」这个标签绑的是 `todaySettlementAmount`，**不是** `todayOrderedAmount`。
- `todayOrderedAmount`（下单金额）**前端没有任何组件在使用** —— 后端算了，前端从来没显示过。它现在是死字段。

也就是说，一张 23:50 下单、次日 09:00 确认的订单，大屏算今天、报表算明天；**两个数各自都「对」，但没有任何一处写着它们不同**。订单数同理（大屏按 `created_at`，报表按 `confirmed_at`）。

首页一旦按自己的理解再写一份，就会出现：

> 首页今日销售额 ¥58 万 / 大屏 ¥57 万 / 销售报表 ¥59 万

而且这种偏差不会报错、不会告警，只会让业务人员不再相信任何一个数字。

### 2.1 采购侧还有一处同类分叉（实现批次 0 时发现）

| 出口 | 显示名 | 时间轴 | 状态过滤 |
| --- | --- | --- | --- |
| 大屏 `countPurchaseOrders` / `sumPurchaseAmount` | 「今日采购」 | **`created_at`** | **无**（含草稿、已取消） |
| 报表 `purchaseOverview` | 「已提交采购金额」 | **`submitted_at`** | `SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED` |

大屏的「今日采购」把草稿和已取消的采购单也算进金额，报表只算已提交。这比销售侧那处分叉更严重（数字差距可能很大），
但**它不是本次换轴能顺手解决的** —— 「今日采购额」到底指「今天新建了多少采购单」还是「今天提交了多少采购额」是个业务选择。

批次 0 的处理：**保持大屏现有行为不变**（不改变数字），只在 metrics 的方法名与注释里把口径写清楚
（`countPurchaseOrdersByCreatedAt` / `sumPurchaseAmountByCreatedAt`）。

**已定稿（见 §3.4）**：今日采购额 / 采购单改为 `submitted_at` 轴 + 已提交状态集合；`created_at` 轴改名为
「今日采购建单金额 / 建单数」。切换放在**批次 1B** 单独做，因为它会明显改变大屏现有采购数字。

### 2.2 采购状态机核对结果（决定「今日采购额」是历史发生口径还是当前有效口径）

`ScmPurchaseStatusEnum` 六状态，`PurchaseOrderStateMachine` 的合法转换：

```
DRAFT               → SUBMITTED | CANCELLED
SUBMITTED           → PARTIALLY_RECEIVED | RECEIVED | CANCELLED
PARTIALLY_RECEIVED  → PARTIALLY_RECEIVED | RECEIVED | SHORT_CLOSED
RECEIVED / SHORT_CLOSED / CANCELLED 为终态
```

关键两点：

- **只有 `DRAFT` 与 `SUBMITTED` 可取消**；`PARTIALLY_RECEIVED` 不可取消，需要终止只能 `shortClose`。
  也就是说「已提交后又被取消」只发生在**一票货都没收**的单上。
- 因此「已提交」= 非 `DRAFT` 且非 `CANCELLED`，与报表现有那组状态名完全等价。

**结论：采用当前有效口径**（与报表现有行为一致）。理由来自状态机语义而不是 UI：`CANCELLED` 表示该单已退出履约链路，
且从未产生收货，所以它不该计入采购额。代价要写清楚 —— **取消一张已提交的单，会让它提交那天的采购额变小**
（历史数字会变动）。这是「当前有效」的固有代价，与销售的确认口径不同（后者一旦确认就不再回退）。

验收用例（批次 1B）：

```
采购单 A：10-06 23:50 创建 → 10-07 09:00 提交，金额 100
  10-06：今日采购建单金额 = 100，今日采购额 = 0
  10-07：今日采购建单金额 = 0，  今日采购额 = 100

采购单 B：10-07 创建，状态 DRAFT，金额 200
  今日采购额不得包含 200（草稿未进入履约链路）

采购单 C：10-07 创建并提交，随后 CANCELLED
  按当前有效口径：不计入今日采购额
```

## 3. 架构决策

### 3.1 原则：一个业务指标只有一处口径定义

大屏要「创建轴」、报表要「确认轴」都可以存在，问题不在口径不同，而在**同一个口径被写了两遍**、以及**同一个名字被用在两个口径上**。

所以目标是：

> 每个业务指标只有一处 SQL 定义，且这个定义有一个唯一的名字；任何界面要用某个数，都必须引用同一个定义。

**注意边界**：这不等于「所有报表查询都必须调一个万能 Service」。`ScmBusinessMetricsService` 只收**跨端共用的经营类指标**，收多了必然变成 God Service。

### 3.2 边界

进 metrics（首页 / 大屏 / 报表都要用的公共经营指标）：

```
今日确认销售额 / 今日确认订单数
今日下单金额 / 今日下单数
今日采购金额 / 今日采购单数 / 今日收货单数
库存健康度分档
经营趋势（7d / 30d 的销售、订单、采购、库存流转序列）
客户销售排行 / 商品销售排行
```

仍留在 Report 域（报表专有，不迁）：

```
按业务员分组 / 按分类分组
客户对账明细 / 供应商对账
账龄 / 利润
退款
异常订单
分页明细与导出
```

### 3.3 分层与目录

```
              ScmBusinessMetricsService        ← 唯一取数出口（只读）
                 /        |         \
                ↓         ↓          ↓
     DashboardService  ScreenDataService  ReportService
          首页             大屏              报表
```

```
com/xsy/scm/metrics/
├── service/ScmBusinessMetricsService.java   唯一取数出口
├── dao/ScmBusinessMetricsDao.java           方法名自带时间轴（ByConfirmedAt / ByCreatedAt）
├── domain/                                  SalesMetrics / MasterDataMetrics / PurchaseMetrics
│                                            InventoryMetrics / InventoryHealth / InventoryHealthRow
│                                            TrendMetrics / TrendPoint / RankItem
└── constant/ScmMovementDirections.java      流水方向清单，由枚举方向位派生
mapper/scm/metrics/ScmBusinessMetricsMapper.xml
```

`screen/ScreenDataDao` 收窄到只剩大屏专属的四条（仓库分布条、供应链网络节点、地理分布），
`ScreenDataService` 只做「取指标 + 装面板」。

迁移动作：

- `screen/ScreenDataMapper.xml` 里的经营 / 采购 / 库存聚合 SQL **迁入** metrics mapper；`ScreenDataService` 改为调用 metrics service，**大屏 VO 与前端契约不变**。
- `report/ReportDao.xml` 里与 metrics 重叠的部分（订单数、销售额、采购额）改为引用 metrics service；报表特有的部分留在报表域。
- `dashboard` 模块只做「面向首页的装配 + 权限裁剪」，**不写 SQL**。

### 3.4 口径命名规则（长期规则）

| 指标名 | 时间轴 | 状态 | 金额列 |
| --- | --- | --- | --- |
| 今日销售额 | `confirmed_at` | `CONFIRMED` | `settlement_total_amount` |
| 今日订单 | `confirmed_at` | `CONFIRMED` | `COUNT(*)` |
| 今日下单金额 | `created_at` | `CONFIRMED` | `ordered_total_amount` |
| 今日下单数 | `created_at` | `CONFIRMED` | `COUNT(*)` |
| 今日采购额 | `submitted_at` | 已提交状态集合 | `total_amount` |
| 今日采购单 | `submitted_at` | 已提交状态集合 | `COUNT(*)` |
| 今日采购建单金额 | `created_at` | 任意状态 | `total_amount` |
| 今日采购建单数 | `created_at` | 任意状态 | `COUNT(*)` |

统一语言是：**销售看「确认」，采购看「提交」** —— 这两个时间点都是「业务正式生效」的节点。

- **不得把 `created_at + CONFIRMED` 叫「今日销售额」**，也不得把 `created_at` 轴上的采购单叫「今日采购额」。
- `created_at` 轴的指标必须带「建单」二字（今日采购建单金额 / 建单数），一眼能看出它不是「今天发生了多少业务」。

**「已提交状态集合」不得在 metrics 里硬编码**，必须从采购领域的枚举派生：

```
ScmPurchaseStatusEnum.isCommitted()  →  committedNames()
        ↓
ScmBusinessMetricsService
        ↓
首页 / 大屏 / 报表
```

当前实现为**排除法**（非 `DRAFT` 且非 `CANCELLED`），而不是正列举四个状态名。理由与 `ScmMovementDirections` 同源：
以后新增 `CLOSED` / `PARTIALLY_CLOSED` 这类状态时，正列举会**静默漏算**（数字看起来正常、只是偏小），排除法则默认把它算进来。
采购状态机的合法转换（`PurchaseOrderStateMachine`）与 `ck_purchase_order_status` 白名单是这套派生的依据。

**大屏的处置**：把「今日销售额」从 `created_at` 轴切到 `confirmed_at` 轴，与报表同名同义。若决定保留创建轴，则**必须改名**为「今日下单金额」。`todayOrderedAmount` 这个死字段要么删掉，要么改名后真正展示出来。

**换轴的影响面比「一个 KPI」大**（实现时实测确认）：大屏的「今日订单」「成交客户」、客户 / 商品两张销售排行、以及趋势里的 `sales` / `orders` 两条序列，与「今日销售额」是**同一个口径**，必须一起换。否则会出现两种自相矛盾：趋势最后一点与 KPI 不相等，而大屏的环比正是拿这两者相减（`core-metrics.vue` 的 `deltaOf(business.todaySettlementAmount, trend.sales)`）；客单价 = 销售额 / 订单数也会变成跨口径的比值。

口径名落到界面上（沿用 §8 的「指标名自解释」约定，不写解释性长句）：首页固定显示确认口径，指标名直接写「今日销售额」，不额外加「（确认口径）」；只有同时出现两个口径的地方才需要显式区分。

### 3.5 防回归

**不做全仓 `SUM(...)` 扫描** —— 订单、支付、结算、打印、财务里合法出现金额聚合，一刀切会误杀。改成三条针对性规则 + 一层结果守卫：

1. **`screen/ScreenDataMapper.xml`**：迁移完成后不得再存在目标经营 KPI 的独立聚合 SQL（这些 `select` 应已删除，只留大屏独有的大屏专属聚合）。
2. **`report/ReportDao.xml`**：不得独立实现已迁入 metrics 的同名 KPI（断言对应 `select` 不再含 `sales_order` / `purchase_order` 的金额聚合）。
3. **`dashboard` 模块**：不得直接访问 `sales_order` / `purchase_order` 写统计 SQL（只许调 metrics service）。
4. **PG IT 结果一致性守卫**：同一区间下，metrics service 与各消费端返回值必须相等；用例覆盖 0 值、跨日边界（23:50 创建 / 次日确认）与数据范围收窄。

规则 1–3 走源码契约（可被注入违规打红），规则 4 走 IT。

## 4. 后端需求

### 4.1 接口

沿用已有 `/scm/dashboard` 命名空间（`ScmTodoController` 已在此），新增三个只读端点：

| 端点 | 返回 | 说明 |
| --- | --- | --- |
| `GET /scm/dashboard/overview` | KPI 卡列表 | 今日销售额 / 今日订单 / 今日采购额 / 今日收货 / 库存异常 |
| `GET /scm/dashboard/trend?range=7d\|30d&metric=sales\|purchase\|inventory` | 一条主序列 | 复用 `ScreenTrendVO` 的 8 条序列，按 metric 取金额 + 笔数两条 |
| `GET /scm/dashboard/ranking?dimension=customer\|product&limit=5` | 排行行 | 复用客户 / 商品销售排行 |

约束：

- 全部只读，`@Transactional(readOnly = true)`，不写业务表、不发消息、不落快照。
- 每次请求解析一次 `ScmDataScopeContext` 并下传，**不得绕过数据范围**（与大屏同口径）。
- 「今日」一律用 `Asia/Shanghai` 日界（`ScreenDataService` 已有 `BUSINESS_ZONE`，迁移时保留这条口径理由）。
- KPI 返回**列表**而非固定字段：卡片由服务端决定给不给，前端按返回数量自适应（见 §5.5）。

### 4.2 权限

- 新增 `scm:dashboard:query`（V115：1200 隐藏目录 + 1201 权限点），**不复用** `scm:screen:query`。
- 首版数据库只授 role 1；随后由角色配置授给销售 / 采购 / 仓库 / 管理角色。
- KPI 卡与待办同范式做**卡片级裁剪**：缺 `scm:inventory:warning:query` 就不返回「库存异常」格，缺采购域权限就不返回采购格；**省略而不是给 0**（0 会被读成「今天真的没有」）。
- 「进入运营大屏」入口仍需 `scm:screen:query`，前端按权限显隐。

### 4.3 不做的事

- 不在首页做地图（地图留在大屏）。
- 不在首页做明细列表（明细留在各业务列表页，首页只给数字与跳转）。
- 不为首页新写任何统计 SQL。

## 5. 前端需求

### 5.1 目录

```
views/system/home/
├── index.vue                 # 装配
├── home-header.vue           # 欢迎区：问候 + 日期 + 部门 + 快捷入口 + [刷新数据] [进入运营大屏]
├── components/
│   ├── metric-cards.vue      # KPI 卡（列数自适应，见 §5.5）
│   ├── business-trend.vue    # 唯一主图（7d/30d 切换）
│   ├── business-todo.vue     # 由 business-todo-card/home-business-todo.vue 迁入并强化
│   ├── customer-ranking.vue  # 客户销售 TOP5
│   ├── product-ranking.vue   # 商品销售 TOP5
│   ├── inventory-health.vue  # 库存健康分档
│   ├── home-notice.vue       # 补 title prop
│   └── system-changelog.vue  # 由 changelog-card.vue 迁入
└── styles/home.less
```

### 5.2 首屏布局

```
┌ 欢迎区 ───────────────────────────────────────────────┐
│ 晚上好，XX · XX部门        快捷入口  运营大屏  刷新数据  │
└───────────────────────────────────────────────────────┘

┌ 今日销售额 ┐ ┌ 今日订单 ┐ ┌ 今日采购额 ┐ ┌ 今日收货 ┐ ┌ 库存异常 ┐

┌──────────────────────────────┬───────────────────────┐
│      近 7 / 30 天经营趋势      │       业务待办         │
└──────────────────────────────┴───────────────────────┘

┌ 客户销售 TOP5 ─────┐ ┌ 商品销售 TOP5 ─────┐ ┌ 库存健康 ─────┐

┌ 通知公告 ─────────────────────────┐ ┌ 系统更新 ────────────┐
```

- **快捷入口不再单独占一张大卡**，放欢迎区右侧，4~6 个轻量入口。
- `home-header.vue` 只保留：问候语、日期、所属部门、快捷入口、[刷新数据] [进入运营大屏]。删掉天气 iframe、毒鸡汤、农历节气、上次登录 / IP（后两者属个人中心 / 安全信息）。
- 快捷入口**能力保留、入口全部换掉**：默认项从「菜单 / 请求 / 缓存 / 字典 / 单号」换成 SCM 业务入口（新建销售订单 / 采购需求 / 采购收货 / 库存预警 / 配送线路 / 经营报表），并**按当前用户权限过滤**（无权限的入口不出现）。

### 5.3 视觉

- 背景浅灰（`#F5F7FA` 一档），卡片 12px 圆角、`1px solid` 极淡描边、很淡阴影、`padding: 20px`。
- 颜色只承担业务语义：绿 = 正常 / 完成 / 增长，橙 = 待处理 / 低库存，红 = 缺货 / 异常 / 逾期，蓝 = 普通指标，灰 = 辅助。
- 字号：核心 KPI 26–30px，卡片标题 14–16px，辅助 12–13px。
- 全部走 `--scm-*` 变量（本仓库不开 cssVar）；不新增 `--ant-color-*` 引用，`ui-foundation-contract` 会抓。
- 排行榜不用 ECharts，用带进度条的列表。

### 5.4 文案

- 遵守 §8：默认不解释。口径名进指标名，不写解释性长句。
- `hint` / 常驻正文长句 / markdown 残迹三条棘轮都在扫 `xsy-scm-web/src`，新增卡片文案不得把基线顶上去；确实要顶必须在 PR 里说明「不告知会导致什么操作错误」。

### 5.5 KPI 卡列数自适应（验收项）

因为 KPI 按领域权限裁剪，返回数量不固定。`metric-cards.vue` **不要固定五等分后留空**，按返回数量自适应：

```
5 个 → 5 列    4 个 → 4 列    3 个 → 3 列    2 个 → 2 列
1 个 → 1 列，但限制最大宽度（避免一个数字横铺整屏）
```

## 6. 分批与验收

| 批次 | 内容 | 验收 |
| --- | --- | --- |
| 0 | 抽 `ScmBusinessMetricsService`；大屏改接它并**切到确认口径**（见 §3.4） | 大屏 IT 与前端契约全绿；除「销售额 / 订单数 / 成交客户 / 两张销售排行 / 趋势的 sales 与 orders 序列」因换轴而变化外，其余数字与改动前逐项一致；换轴后与报表同区间取值相等 |
| 1A | **销售闭环**：报表概览的销售三件套迁入 metrics（含客户 / 业务员 / 订单来源筛选），报表不再自己写销售 SQL；补 §3.5 第 2 条契约与跨日 IT | 报表与大屏对同一区间同数；跨日边界（昨天创建今天确认）两端都算今天；`overviewKpi` 不再出现 `sales_order` / `settlement_total_amount` |
| 1B | **采购口径正式切换**：`ScmPurchaseStatusEnum.committed()` 派生已提交状态 → metrics 采购方法改 `submitted_at` 轴 + 状态过滤 → 大屏「今日采购」切过来 → `created_at` 轴改名为建单金额 / 建单数 | §2.2 的三张验收单；大屏与报表的采购数字一致；新增采购跨日 / 草稿 / 取消 IT |
| 2 | `scm:dashboard:query` 权限种子 + 三个只读端点 + IT | 缺领域权限的卡片被省略而不是给 0；越权 403；数据范围用例通过 |
| 3 | 首页 UI 重构（删假数据组件、欢迎区、KPI、主图、待办、双排行、库存健康） | 截图复核；`verify.py frontend` PASS；§5.5 的列数自适应逐档验证 |
| 4a | 首页清理：删 `echarts/*`、`heart-sentence.ts`；修 `home-notice` 死 prop；快捷入口换业务入口并按权限过滤 | 无残留引用；lint / 契约全绿 |
| 4b | **整套删除 SmartAdmin 本地待办能力**（清单见下） | 顶部铃铛只剩未读消息；全仓无 `TO_BE_DONE` 残留 |

批次 0 与 1B 是会改变现有数字的两步（换轴），各自单独提交、单独验证；批次 1A 不改变数字，只把定义收口。

### 批次 4b 清单（不止首页组件）

```
删除 views/system/home/components/to-be-done-card/（home-to-be-done.vue + to-be-done-modal.vue）
删除 layout/components/header-user-space/header-message.vue 的「待办工作」Tab 与列表
铃铛 badge 只统计 unreadMessageCount（去掉 + toBeDoneCount）
删除 store/modules/system/user.ts 的 toBeDoneCount 与 queryToBeDoneList()
删除 constants/local-storage-key-const.ts 的 localKey.TO_BE_DONE
删除 user.ts 里登录后初始化待办数与 logout 时的 TO_BE_DONE 清理
```

只删首页组件会让顶部铃铛里留下半套遗留功能，因此这五处必须一起动。

## 7. 已定稿决策

| 项 | 决策 |
| --- | --- |
| 首页今日销售额 / 今日订单 | **确认口径**：`confirmed_at` + `status='CONFIRMED'`，金额取 `settlement_total_amount` |
| 今日采购额 / 今日采购单 | **提交口径**：`submitted_at` + 已提交状态（非 `DRAFT` 且非 `CANCELLED`，由采购枚举派生） |
| 今日采购建单金额 / 建单数 | 创建轴（`created_at`），名称必须带「建单」二字 |
| 大屏 / 报表 | 允许不同指标存在，**禁止同名不同义**。大屏「今日销售额」已切确认口径；「今日采购」在批次 1B 切提交口径 |
| 已提交状态集合 | **从采购枚举派生**（`ScmPurchaseStatusEnum.committed()` → `committedNames()`），不在 metrics 硬编码状态名 |
| 「今日采购额」语义 | **当前有效口径**（取消后不计入），依据是状态机：`CANCELLED` 已退出履约链路且从未产生收货。代价是取消会让历史数字变动，已在 §2.2 写明 |
| 报表概览的销售指标 | 按调用者的**业务员范围**收窄，与大屏经营面板一致（原实现完全不收窄，见批次 1A） |
| `scm:dashboard:query` | 保留该基础权限；首版只授 role 1，随后由角色配置授给销售 / 采购 / 仓库 / 管理角色，并继续叠加领域权限裁剪 |
| `ToBeDoneCard` | **整套功能删除**，不是只从首页移走（清单见 §6 批次 4b） |
| 快捷入口 | 保留机制，默认项全部换成 SCM 业务入口并按权限过滤 |

已无待定夺项。批次顺序：0（已完成）→ 1A → 1B → 2 → 3 → 4a → 4b。
