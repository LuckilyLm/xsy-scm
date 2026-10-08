# 首页「供应链工作台」改造设计

> 状态：批次 0～3 已有代码；3A / 4a / 4b 收尾已写入工作区，尚未运行验收（2026-10-08）。
> 已提交基线：`main @ b3df08d0`，Flyway 源码 V115。§1～4 的设计推导以 `4f10ee0c` 为初始依据，不代表当前仍未实施；实际交付与验证边界以[项目状态](../../status.md)为准。
> 规则依据：[SCM UI 长期规范](../../architecture/scm-ui-guidelines.md) §8；注释与文案约束见 [code-comment-guidelines](../../architecture/code-comment-guidelines.md)。

## 0. 结论摘要

1. 首页已从 SmartAdmin 示例骨架改为「供应链工作台」，本设计保留指标、数据范围与交互契约；代码实施状态与运行验收状态分别记录。
2. 首页要的数据后端**已经有了**（`screen` 模块 + `/scm/dashboard/todo`），不需要新写统计 SQL。
3. 但**不能直接让首页调 `/scm/screen/*`**：它挂 `scm:screen:query`，语义与授权范围都不对。
4. **真正的风险不是首页好不好看，而是口径已经分叉**：大屏与报表对「今日销售额」用的是**同一列、不同时间轴**（见 §2 实测）。首页如果自己再写一份，就是第三个数字。
5. 因此先收口径，再做界面：抽 `ScmBusinessMetricsService`，**一个业务指标只有一处口径定义**（不是「所有查询都必须走一个万能 Service」）。
6. 口径命名成为长期规则（见 §3.4）：`今日销售额 / 今日订单` 走**确认轴**，`今日下单金额 / 今日下单数` 走**创建轴**；**不得把 `created_at + CONFIRMED` 叫「今日销售额」**。

## 1. 实施前核对（初始设计依据）

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

### 3.1 原则：一个业务指标只有一套业务口径定义

大屏要「创建轴」、报表要「确认轴」都可以存在，问题不在口径不同，而在**同一个口径被写了两遍**、以及**同一个名字被用在两个口径上**。

所以目标是：

> 每个业务指标只有**一套业务口径定义**（时间轴 + 状态 + 金额列 + 指标名）；
> 因为**可见性范围策略不同**，SQL 实现允许多个，但必须受契约测试与跨端一致性测试约束。

「SQL 实现允许多个」这条是批次 1B 实测后补的（见 §3.6）：报表的销售 / 采购页面有自己的范围策略
（页面权限 + 只按仓库），强行与首页 / 大屏共用同一份 SQL 会把两边的权限边界搅在一起。因此
**统一的是口径，不是 SQL 位置**。

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
                      经营指标口径规则（轴 / 状态 / 金额列 / 指标名）
                              │
              ┌───────────────┴───────────────┐
              ↓                               ↓
   ScmBusinessMetricsService            ReportService
        首页 / 大屏 / Dashboard           报表专属范围策略
              │                               │
              ↓                               ↓
   ScmBusinessMetricsMapper.xml         ReportDao.xml
```

两边**必须一致**：时间轴、状态、金额列。两边**允许不同**：范围策略（大屏按「归属 ∩ 仓库」，
报表按「页面权限 + 只按仓库」）。采购的已提交状态清单由 `ScmPurchaseStatusEnum.committedNames()`
传参给两边的 SQL，只有一处来源。

```
com/xsy/scm/metrics/
├── service/ScmBusinessMetricsService.java   跨端共用经营指标的编排（口径规则在这里）
├── dao/ScmBusinessMetricsDao.java           方法名自带时间轴（ByConfirmedAt / ByCreatedAt）
├── domain/                                  SalesMetrics / SalesRangeMetrics / SalesFilter
│                                            MasterDataMetrics / PurchaseMetrics / PurchaseRangeMetrics
│                                            PurchaseFilter / InventoryMetrics / InventoryHealth
│                                            InventoryHealthRow / TrendMetrics / TrendPoint / RankItem
└── constant/ScmMovementDirections.java      流水方向清单，由枚举方向位派生
mapper/scm/metrics/ScmBusinessMetricsMapper.xml
```

`screen/ScreenDataDao` 收窄到只剩大屏专属的四条（仓库分布条、供应链网络节点、地理分布），
`ScreenDataService` 只做「取指标 + 装面板」。

迁移动作：

- `screen/ScreenDataMapper.xml` 里的经营 / 采购 / 库存聚合 SQL **迁入** metrics mapper；`ScreenDataService` 改为调用 metrics service，**大屏 VO 与前端契约不变**。
- `report/ReportDao.xml` 保留自己的销售 / 采购聚合 SQL（范围策略不同），但**口径必须与 metrics 一致**，采购状态清单改由枚举传参。
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

当前实现为**穷尽 switch、不写 default**：enum 一旦新增状态（如 `PENDING_APPROVAL` / `REJECTED` / `CLOSED`），
`committed()` 直接编译不过，逼着开发者显式裁决新状态算不算已提交。

不用「排除 `DRAFT` 与 `CANCELLED`」的写法：那样确实不会漏算新状态，但会把**尚未进入履约**的状态
（审批中、已驳回、备货中）默认算进来，而这类错法同样是静默的 —— 数字看起来正常，只是偏大。

采购状态机的合法转换（`PurchaseOrderStateMachine`）与 `ck_purchase_order_status` 白名单是这套派生的依据。

**大屏的处置**：把「今日销售额」从 `created_at` 轴切到 `confirmed_at` 轴，与报表同名同义。若决定保留创建轴，则**必须改名**为「今日下单金额」。`todayOrderedAmount` 这个死字段要么删掉，要么改名后真正展示出来。

**换轴的影响面比「一个 KPI」大**（实现时实测确认）：大屏的「今日订单」「成交客户」、客户 / 商品两张销售排行、以及趋势里的 `sales` / `orders` 两条序列，与「今日销售额」是**同一个口径**，必须一起换。否则会出现两种自相矛盾：趋势最后一点与 KPI 不相等，而大屏的环比正是拿这两者相减（`core-metrics.vue` 的 `deltaOf(business.todaySettlementAmount, trend.sales)`）；客单价 = 销售额 / 订单数也会变成跨口径的比值。

口径名落到界面上（沿用 §8 的「指标名自解释」约定，不写解释性长句）：首页固定显示确认口径，指标名直接写「今日销售额」，不额外加「（确认口径）」；只有同时出现两个口径的地方才需要显式区分。

### 3.5 防回归

**不做全仓 `SUM(...)` 扫描** —— 订单、支付、结算、打印、财务里合法出现金额聚合，一刀切会误杀。改成三条针对性规则 + 一层结果守卫：

1. **`screen/ScreenDataMapper.xml`**：不得再存在目标经营 KPI 的独立聚合 SQL（这些 `select` 应已删除，只留大屏专属的聚合）。
2. **`report/ReportDao.xml`**：允许保留自己的 SQL（范围策略不同），但**口径必须与 metrics 一致** —— 断言销售聚合用 `confirmed_at` + 结算金额列 + `CONFIRMED`，采购状态必须来自 `committedStatuses` 参数而不是字面量。
3. **`dashboard` 模块**：不得直接访问 `sales_order` / `purchase_order` 写统计 SQL（只许调 metrics service）。
4. **PG IT 结果一致性守卫**：同一区间下，metrics service 与各消费端返回值必须相等；用例覆盖 0 值、跨日边界（23:50 创建 / 次日确认）与数据范围收窄。

规则 1–3 走源码契约（可被注入违规打红），规则 4 走 IT。

**注意规则 2 的形态变化**：它守的是「口径一致」，不是「不许有第二份 SQL」。这是 §3.6 的直接后果 ——
契约也要跟着从「同 SQL」改成「同口径」。

### 3.6 口径同源 ≠ 范围同源（1B 实测出的边界）

`ScmBusinessMetricsService` 同时管两件事：**口径**（时间轴、状态、金额列）与**范围收窄**（按业务员 / 采购归属 / 仓库）。
1B 想把报表的采购 KPI 也接进 metrics 时发现：**这两件事必须分开谈**。

现状是两处刻意不同的可见性策略：

| 出口 | 销售 | 采购 |
| --- | --- | --- |
| 大屏 | 按业务员收窄（`scopeSo`） | 按采购归属 ∩ 仓库收窄（`scopePo`） |
| 报表概览 / 采购分析 | **完全不收窄**（`sales_order` 没有仓库列，可见性由页面权限承担） | **只按仓库收窄** |

强行统一到 metrics 的范围语义会撞两堵墙（都实测过）：

1. 报表销售页的其它查询（`byCustomer` / `byProduct` / `bySeller` / `salesBySeller`）也都不收窄 ——
   只让概览收窄，同一页上两个「销售额」就会互相矛盾。`ScmReportDataScopePgIT` 的用例注释写得很清楚：
   「可见性只由页面权限承担」。
2. 报表采购页同理：`bySupplier` / `byPurchaser` / `topSupplier` 都只按仓库收窄。
   把概览改成「采购归属 ∩ 仓库」后，`ScmReportDataScopePgIT` 的 `scopedToBoth`（持两仓授权）**看不到**甲/乙仓
   采购员建的单 —— 因为它的采购归属只有自己（实测 `expected: 79.6 but was: 0`）。这不是夹具能绕过的：
   两个员工的采购归属互斥，夹具改成「各自造单」只会把另一个用例弄坏。

因此 1B 的结论是：

- **口径统一**（时间轴 + 状态 + 金额列）：报表与大屏共用同一套定义，采购状态清单由
  `ScmPurchaseStatusEnum.committedNames()` 派生后**传参**进 SQL，不再各自硬编码。
- **范围策略保持现状**：报表继续按「页面权限 + 仓库」控制可见性，大屏继续按「归属 ∩ 仓库」。
- 要真正统一范围策略，必须**整页一起改**（报表的六个销售 / 采购查询 + 它们的夹具），这是一次独立的权限变更，
  需要单独裁决 —— 不在本设计范围内。

## 4. 后端需求

### 4.1 接口

沿用已有 `/scm/dashboard` 命名空间（`ScmTodoController` 已在此），新增三个只读端点（`ScmDashboardController`）：

| 端点 | 返回 | 说明 |
| --- | --- | --- |
| `GET /scm/dashboard/overview` | KPI 卡列表 | 今日销售额 / 今日订单 / 今日采购额 / 今日收货 / 库存预警 |
| `GET /scm/dashboard/trend?metric=sales\|purchase\|inventory&range=7d\|30d` | 薄 VO：`{metric, range, dates, primarySeries, secondarySeries}` | 主 / 次两条序列由 metric 决定；不再复制 `ScreenTrendVO` 的八条序列 |
| `GET /scm/dashboard/ranking?dimension=customer\|product&limit=5` | `List<RankItem>` | metrics 已取回 TOP10，这里只截断（`limit` 默认 5、上限 10） |
| `GET /scm/dashboard/inventory-health` | `{total, normal, low, high, unconfigured, outOfStock}` | 供首页的库存健康卡；直接取 `metricsService.inventory(scope).health()`，不新增 SQL |

**「库存预警」卡片与「库存健康」是两个指标，不能互相替代**：

- 顶部卡片取的是**库存预警列表的分页总数**（复用 `InventoryWarningQueryService.queryWarningPage`，与待办卡同源），
  因此「卡片数字 = 点进去的条数」严格成立。
- 健康度五档更宽（含缺货与未配置阈值），若拿它求和当卡片数字，会出现「首页 12、点进去只有 8」——
  数字与明细对不上，比数字本身更伤信任。它只用于首页的库存健康卡。

**接口只带业务语义，不带 UI 决策**：卡片返回 `{key, value, unit, route}`，中文文案、图标、配色、卡片样式全由前端决定；
后端只回答「该不该给 / 多少 / 跳哪里」。把 label / color / icon 放进接口等于把 UI 设计固化进 Java。

约束：

- 全部只读，`@Transactional(readOnly = true)`，不写业务表、不发消息、不落快照。
- **不写统计 SQL**：一律调 `ScmBusinessMetricsService`，数据范围沿用大屏那一套「归属 ∩ 仓库」，不沿用报表的宽范围语义。
- 「今日」一律用 `Asia/Shanghai` 日界（口径由 metrics 服务负责，这里不重复实现）。
- KPI 返回**列表**而非固定字段：卡片由服务端决定给不给，前端按返回数量自适应（见 §5.5）。
- `metric` / `dimension` 的未知取值按参数不合法拒绝（40000），不静默回落。

### 4.2 权限

- 新增 `scm:dashboard:query`（V115：**2000** 隐藏目录 + **2001** 权限点），**不复用** `scm:screen:query`。
  1200 段被报表中心占用（1200 报表中心 / 1201 经营概览 / 1211-1230 各报表权限点），2000 段已核对空闲。
- 首版数据库只授 role 1；随后由角色配置授给销售 / 采购 / 仓库 / 管理角色。
- KPI 卡与待办同范式做**卡片级裁剪**：缺 `scm:inventory:warning:query` 就不返回「库存预警」格；**省略而不是给 0**（0 会被读成「今天真的没有」）。
  卡片与领域权限的对应：销售额 / 订单 → `scm:order:query`；采购额 → `scm:purchase:query`；收货 → `scm:purchase:receipt:query`；库存预警 → `scm:inventory:warning:query`。
- 趋势与排行是单指标端点，没有「省略」的形态，**按指标 / 维度逐个校验领域权限**（销售额走订单查询权、采购额走采购查询权、库存趋势走库存流水查询权），缺权直接拒绝 —— 入口权限不隐含任何领域可见性。
- `inventory-health` 除入口权限外还要求 `scm:inventory:warning:query`（看到分档等于看到库存状况）。
- 「进入运营大屏」入口仍需 `scm:screen:query`，前端按权限显隐。

### 4.3 不做的事

- 不在首页做地图（地图留在大屏）。
- 不在首页做明细列表（明细留在各业务列表页，首页只给数字与跳转）。
- 不为首页新写任何统计 SQL。

### 4.4 已知优化（P2，不阻塞）

`overviewFor` 目前无论用户持有哪些领域权限，都会先取齐销售 / 采购两组指标与库存预警总数，再按权限裁卡。
这不构成越权（SQL 本身按范围收窄、结果也不返回），但只有库存预警权的人也会触发销售与采购的聚合。
优化方向是**按可见卡片惰性取数**（`ScmTodoQueryService.todosFor` 已是这个范式），需要加一层按组记忆，
避免两张卡共用一组指标时重复取数。

## 5. 前端需求

### 5.1 目录

```
views/system/home/
├── index.vue                 # 装配：按权限决定发不发请求、区块显不显示
├── home-header.vue           # 欢迎区：问候 + 日期 + 部门 + 业务快捷入口 + [运营大屏] [刷新数据]
├── home-notice.vue           # 合并展示当前员工可见的全部通知类型，独立错误与重试
├── home-metric-meta.ts       # KPI / 趋势 / 排行 / 库存五档的中文名、图标与语义色
├── components/
│   ├── use-region-data.ts    # 每个区块各自的 loading / error / 重试（不做全局一把抓）
│   ├── region-error.vue      # 区块失败的统一提示 + 重试入口
│   ├── metric-cards.vue      # KPI 卡（列数自适应，见 §5.5）
│   ├── business-trend.vue    # 唯一主图（指标 tab 只列有权项，7d/30d 切换）
│   ├── ranking-card.vue      # 客户 / 商品销售 TOP5（同一组件按 dimension 复用）
│   ├── inventory-health.vue  # 库存健康五档
│   ├── business-todo-card/home-business-todo.vue   # 业务待办，使用 useRegionData 与区块重试
│   ├── default-home-card.vue # 统一 SCM 主题的卡片壳
│   ├── quick-entries.ts     # 六类业务入口白名单、权限与缓存标识
│   ├── quick-entries.vue    # 欢迎区轻量入口与勾选设置
│   └── changelog-card.vue   # 系统更新，独立加载/失败重试并接入首页刷新
└── index.less
```

客户与商品排行没有拆成两个文件：两者的数据形状、空态与展示方式完全一致，只有标题与
空态文案不同，拆开就是两份只差一个字符串的重复代码。`ranking-card.vue` 收一个 `dimension`
参数，两个实例各自持有独立的加载状态。

通知公告保留一张合并卡，调用员工可见性接口时不传类型筛选，标题固定为「通知公告」，与「更多」列表语义一致。
快捷入口只从 SCM 目录勾选，缓存保存入口标识，旧版任意路径配置不再生效；清空全部勾选会保留为空，不恢复默认项。
示例图表、心语、旧快捷入口组件及本地待办组件已物理删除；SmartAdmin 正式消息能力保留。

业务待办在首页通过 `v-if` 权限判断挂载；无入口权限时不创建组件、不发送请求。趋势只展示与当前指标和区间匹配的响应，图例、轴与格式化均依据响应的 `metric`；错误时保留图表容器，以便重试后继续使用同一实例。

### 5.2 首屏布局

```
┌ 欢迎区 ───────────────────────────────────────────────┐
│ 晚上好，XX · XX部门        快捷入口  运营大屏  刷新数据  │
└───────────────────────────────────────────────────────┘

┌ 今日销售额 ┐ ┌ 今日订单 ┐ ┌ 今日采购额 ┐ ┌ 今日收货 ┐ ┌ 库存预警 ┐

┌──────────────────────────────┬───────────────────────┐
│      近 7 / 30 天经营趋势      │       业务待办         │
└──────────────────────────────┴───────────────────────┘

┌ 客户销售 TOP5 ─────┐ ┌ 商品销售 TOP5 ─────┐ ┌ 库存健康 ─────┐

┌ 通知公告 ─────────────────────────┐ ┌ 系统更新 ────────────┐
```

- **快捷入口不再单独占一张大卡**，放欢迎区右侧，4~6 个轻量入口。
- `home-header.vue` 只保留：问候语、日期、所属部门、快捷入口、[刷新数据] [进入运营大屏]。删掉天气 iframe、毒鸡汤、农历节气、上次登录 / IP（后两者属个人中心 / 安全信息）。
- 快捷入口**能力保留、入口全部换掉**：默认项从「菜单 / 请求 / 缓存 / 字典 / 单号」换成 SCM 业务入口（新建销售订单 / 采购需求 / 采购收货 / 库存预警 / 配送线路 / 经营报表），并**按当前用户权限过滤**（无权限的入口不出现）。
- 新建销售订单要求查询与新增权限，进入订单列表后一次性消费 `action=create` 并打开现有新建表单；其他入口使用正式列表路由与查询权限。

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

上述列数适用于宽屏（≥1600px）；较窄桌面最多 3 列，≤991px 最多 2 列，≤575px 为 1 列。

## 6. 分批与验收

| 批次 | 内容 | 验收 |
| --- | --- | --- |
| 0 | 抽 `ScmBusinessMetricsService`；大屏改接它并**切到确认口径**（见 §3.4） | 大屏 IT 与前端契约全绿；除「销售额 / 订单数 / 成交客户 / 两张销售排行 / 趋势的 sales 与 orders 序列」因换轴而变化外，其余数字与改动前逐项一致；换轴后与报表同区间取值相等 |
| 1A | **销售口径闭环**：把报表概览的销售三件套与大屏对齐到同一套口径定义（确认轴 + 结算金额列 + CONFIRMED），并补跨日 IT 与口径契约 | 报表与大屏对同一区间同数；跨日边界（昨天创建今天确认）两端都算今天；口径断言可被注入违规打红（范围策略未变，见 §3.6） |
| 1B | **采购口径正式切换**：`ScmPurchaseStatusEnum.committed()` 派生已提交状态 → metrics 采购方法改 `submitted_at` 轴 + 状态过滤 → 大屏「今日采购 / 活跃供应商」切过来 → 趋势的采购序列同步 → 收货单切 `confirmed_at + CONFIRMED` → 报表采购 SQL 的状态清单改由枚举传入（范围策略不动，见 §3.6） | §2.2 的验收单；大屏 KPI、趋势末点、报表采购概览三者同数；新增采购跨日 / 草稿 / 取消 / 收货草稿的 IT |
| 2 | `scm:dashboard:query` 权限种子 + `ScmDashboardController` 三个只读端点（只调 metrics，不写 SQL） | 缺领域权限的卡片被省略而不是给 0；趋势 / 排行按指标校验领域权限；数据范围与大屏一致；7d / 30d 与 ranking limit 用例 |
| 2A | 收尾：库存顶部 KPI 改「库存预警」并复用预警列表总数（原先用健康度分档求和，与明细对不上）；新增 `/scm/dashboard/inventory-health`；修正趋势 VO 关于「末点等于 KPI」的注释 | 卡片数字 == 预警列表 total；五档之和 == total；缺库存预警权时 health 拒绝 |
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
| 报表概览 / 采购分析的范围策略 | **保持现状**：销售不按归属收窄（可见性由页面权限承担），采购只按仓库收窄。与 metrics 的「归属 ∩ 仓库」不同源，理由与实测见 §3.6；要统一必须整页一起改，属独立裁决 |
| `scm:dashboard:query` | 保留该基础权限；首版只授 role 1，随后由角色配置授给销售 / 采购 / 仓库 / 管理角色，并继续叠加领域权限裁剪 |
| `ToBeDoneCard` | **整套功能删除**，不是只从首页移走（清单见 §6 批次 4b） |
| 快捷入口 | 保留机制，默认项全部换成 SCM 业务入口并按权限过滤 |

首页设计已无待定夺项。批次 0 / 1A / 1B / 2 / 2A / 3 已有提交；3A（待办与趋势修复）、4a、4b 已在工作区实施，尚未提交或运行验证。按负责人要求，本轮仅开发，不执行验收或处理 GitHub CI；本设计保留至待验收项完成。

批次 1B 的两点补充说明：

- **「今日采购建单金额 / 建单数」暂未实现**：当前没有任何界面消费这两个数，加进来就是死代码。口径与命名已定稿（见 §3.4），
  将来真要展示时按那个名字新增方法即可 —— 关键是**不要**把创建轴上的数叫成「采购额」。
- **累计采购口径也跟着切了**：`totalPurchaseOrderCount` / `totalPurchaseAmount` 与今日同源（也是已提交口径）。
  它们目前在前端没有消费点，但留着旧的「含草稿」口径会变成一颗定时炸弹。
