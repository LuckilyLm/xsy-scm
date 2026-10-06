# 前端 UI 收尾审计 / Gap Checklist

盘点日期：2026-10-06。
基线 HEAD：`422cc988`（`docs(ui): record the local .num vs global .scm-quantity divergence`）。
状态：**只读静态盘点**（读主题定义与页面代码），未运行构建、测试或迁移，未访问数据库，未修改任何业务代码。
依据：[前端 UI 优化规划](frontend-ui-optimization-plan.md)、[后端字段缺口盘点](frontend-ui-backend-gap-inventory.md)、当前主线代码。

## 1. 这份文件解决什么

`frontend-ui-optimization-plan.md` 写于 UI Foundation 尚未落地时，其 §30 的 UI-0～UI-7 与 §10～§24 的逐页规划中，**相当一部分已经完成**。若再让 AI 从规划开头重新扫一遍，会重复修改已完成的页面，也容易把"已完成"误报成"待办"。

本文件只做两件事：

1. 按**当前代码**复核规划完成度，标出仍然成立的条目；
2. 把剩余项拆成三类，避免"前端视觉残差"和"后端字段缺口"混在同一张单里。

**不在本文件范围内**：不重新设计视觉，不改变 §3 非目标（不改接口 / DTO / VO / 表结构），不替代 `frontend-ui-backend-gap-inventory.md` 的 B1～B8。

## 2. 结论摘要

一句话判断：

> **UI Foundation 已建立并被广泛采用；主数据、采购、销售、库存、财务的列表与详情主体已完成收口。剩余问题集中在三处——操作列收尾、Drawer 宽度分级未铺开、`.num` 口径未决。**

| 批次 | 范围 | 复核结论 |
| --- | --- | --- |
| UI-0 | SCM UI Foundation | ✅ 已建立（`theme/scm/*`、`components/business/scm/*`），且已被 49 个页面引用 |
| UI-1 | 主数据（商品/客户/供应商/分类/客户类型/仓库） | ✅ 主体完成，操作列与 Drawer 宽度有残差 |
| UI-2 | 采购与销售（需求/订单/收货/销售单/退货/退款/日志） | 🟡 主体完成；退货、退款、日志已精简，采购收货仍受 B1/B2 制约 |
| UI-3 | 库存 | 🟡 主体完成；明细子表操作列未居中，主表操作列偏宽 |
| UI-4 | 价格与营销 | 🟡 价格侧完成度较高；优惠券列表受 B7 制约，操作列 200px |
| UI-5 | 财务 | 🟡 列表与详情已分区；详情"系统信息"段受 B3 制约 |
| UI-6 | 配送与分拣 | 🟡 线路/司机/车辆已统一；分拣任务操作列 220px |
| UI-7 | 报表 / 打印 / 大屏 | 🟡 报表侧金额/数量已统一；三张报表操作列未居中，收货报表受 B1 制约；大屏未逐页复核 |

## 3. §31 全局验收标准逐条核对

核对方式：机械扫描 + 抽样读码。**未做 1920×1080 实机验证**，标注"未验证"的条目仅代表静态层面未发现问题。

### 3.1 列表

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| 普通列表默认列 ≤ 8～10 | 未逐页验证 | 需实机列宽快照，本次未做 |
| 主数据列表不默认展示创建/更新时间 | ✅ 基本达成 | 全 `views/business/scm` 仅 3 处命中 `title: '创建时间'/'更新时间'/'创建人'`：`supplier-sku-list.vue`、`customer-detail.vue`（详情页，合规）、`print-template-list.vue`（模板配置，需判断是否属 §7.3 例外） |
| 技术编码不与业务名称重复占大列 | ✅ 已达成 | 复合单元 `.scm-cell-stack` 已建立并被引用；`2dee64d1` 把图片中心编码下沉为 secondary text |
| 业务单据号保留 | ✅ 已达成 | 单据号仍为独立列（如 `inventory-loss-gain-list.vue:321` 单据号 200px） |
| 操作列默认居中 | 🟡 主表基本达成，10 处遗漏 | 见 §5.A2 |
| 操作列 120～160px | ❌ 7 处超限 | 见 §5.A1 |
| 超过 3 个动作使用更多菜单 | ❌ 接入率低 | `ScmActionMore` 仅 4 个页面接入，与"超宽操作列"高度重合（见 §5.A1） |
| 金额 / 数量右对齐 | ✅ 已达成 | `.scm-money` / `.scm-quantity` 覆盖 **42 个文件** |
| 状态居中 | ✅ 已达成 | `ScmStatusTag` 组件接入 7 个页面；`scm-status-tag` 类与色语义被 29 个文件引用 |
| 单位居中 | 未验证 | 需实机确认 |
| 空值统一 `—` | 未验证 | 需逐页确认 |
| 标签不撑坏行高 | ✅ 已达成 | `.scm-tags` + `+N` 折叠规则已建立 |
| 1920×1080 无无意义横向滚动 | 未验证 | 需实机确认 |

### 3.2 Drawer / Modal

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| 复杂表单按业务 Section 分区 | ✅ 已达成 | `.scm-form-section` 已建立；`b01d4a02`、`e2d93376` 完成客户/供应商/订单/财务分区 |
| Footer 右对齐 / 保存为 Primary / 取消为普通按钮 | ✅ 已达成 | `33edff84` 已统一 Drawer footer |
| 危险操作不放在主保存区 | ✅ 已达成 | `.scm-btn-icon-danger` 为图标按钮，`a5bfe5cb` 把低频/危险动作收进"更多" |
| 数字字段使用正确数字控件 | 未验证 | 需逐表单确认 |
| 单位显示在输入控件附近 | 未验证 | 需逐表单确认 |
| 移动端自动单列 | ✅ 已达成 | `theme/scm/responsive.less` 在 768px 下把 `.scm-form-section` 退回单列 |
| **Drawer 宽度分级** | ❌ 基本未铺开 | 见 §5.A3 |

### 3.3 详情 / 3.4 状态与操作

| 验收项 | 结论 | 证据 |
| --- | --- | --- |
| 详情信息按"核心 → 明细 → 日志 → 系统"排序 | ✅ 已达成 | `b01d4a02`（客户/供应商/订单）、`e2d93376`（财务） |
| 同一状态跨模块颜色语义一致 | ✅ 已达成 | `theme/scm/scm-status.ts` 的 `ScmStatusTone` 五档 + `scmStatusColor()` 归一化 `gray` |
| 状态不只靠颜色表达 | ✅ 已达成 | `.scm-status-dot` 色点与文字同时出现 |
| 删除/作废二次确认 | 未验证 | 需逐页确认 |
| 当前状态下一步动作最显眼 | 🟡 部分达成 | 分拣、退货已按状态给动作；采购/库存部分页面仍为固定按钮组 |

### 3.5 工程质量

本次**未运行** TypeScript 校验、ESLint、前端构建、Playwright / E2E 与 contract tests。本节结论为空，不代表通过。

## 4. Design System 实际表面（供后续引用）

已建立并可用的公共资产：

```text
theme/scm/
├── action.less      .scm-btn-secondary / -tertiary / -icon-danger / -add-row / -icon
├── form.less        .scm-form-section / __head / __title / __hint / __extra / .scm-form-readonly
├── table.less       .scm-table-actions / .scm-cell-stack / .scm-money / .scm-quantity
│                    .scm-tags / .scm-cell-hint
├── status.less      .scm-status-tag / .scm-status-dot
├── footer.less
├── responsive.less  Drawer/Modal max-width 96vw；768px 单列；小屏取消固定列
├── scm-drawer.ts    SCM_DRAWER_WIDTH { s: 600, m: 780, l: 940 }
└── scm-status.ts    ScmStatusTone 五档 + SCM_STATUS_TONE_COLOR + scmStatusColor()

components/business/scm/
├── scm-status-tag/    props: color | tone | label（兼容存量色名，渐进接入）
├── scm-action-more/
├── customer-select / customer-type-select / supplier-select / sku-select / warehouse-select
├── product-category-tree-select
└── map
```

**注意**：主题里**没有等宽工具类**（如 `.scm-mono`）。这是 F1 悬而未决的直接原因，见 §5.A4。

## 5. 残差清单

### A. 前端视觉残差（可直接做，不依赖后端）

#### A1 操作列超过 §8 上限（7 处）

§8 规定普通操作列 120～160px，少数复杂工作台可放宽到 180px。以下超出：

| 文件 | 宽度 | 备注 |
| --- | --- | --- |
| `sorting/sorting-task-list.vue:311` | 220px | 分拣工作台，已超 180px 放宽上限 |
| `product/components/category-tree-table.vue:50` | 250px | 分类树表格 |
| `promotion/promotion-coupon-list.vue:333` | 200px | 同时受 B7 制约 |
| `inventory/inventory-loss-gain-list.vue:327` | 200px | |
| `inventory/inventory-outbound-list.vue:273` | 200px | |
| `inventory/inventory-stocktake-list.vue:303` | 200px | |
| `inventory/inventory-transfer-list.vue:323` | 180px | 处于放宽上限内，可保留或一并收窄 |

**与 `ScmActionMore` 接入率的关系**：`ScmActionMore` 目前仅接入 `supplier-list`、`customer-list`、`sorting-task-list`、`order-return-list` 四个页面，而**上表 7 处正是尚未接入的页面**。也就是说，操作列收窄与"更多菜单"是同一件事的两面，应合并为一批处理。

#### A2 操作列未居中（主表 5 处 + 明细子表 5 处）

`f8fd2ce1` 已处理 `components/` 下的 7 个文件 9 处，但下列位置未覆盖：

主表（缺 `align: 'center'`）：

| 文件 | 位置 | 说明 |
| --- | --- | --- |
| `report/report-customer-statement.vue:80` | `{title: '操作', dataIndex: 'action'}` | 既无 align 也无 width |
| `report/report-finance-aging.vue:108` | `{title: '操作', dataIndex: 'action', fixed: 'right', width: 100}` | 缺 align |
| `report/report-order-exceptions.vue:100` | 同上 | 缺 align |
| `order/order-detail.vue:234` | `{title: '操作', dataIndex: 'action', width: 120}` | 缺 align |
| `pricing/customer-type-price-batch.vue:53` | `{title: '操作', dataIndex: 'action', width: 80}` | 缺 align |

明细子表（Drawer 内嵌表格，缺 align）：

| 文件 | 位置 |
| --- | --- |
| `inventory/inventory-conversion-list.vue:355` | `{title: '操作', dataIndex: 'action', width: 70}` |
| `inventory/inventory-loss-gain-list.vue:334` | `{title: '操作', dataIndex: 'action', width: 80}` |
| `inventory/inventory-outbound-list.vue:288` | 同上 |
| `inventory/inventory-stocktake-list.vue:311` | 同上 |
| `inventory/inventory-transfer-list.vue:330` | 同上 |

#### A3 Drawer 宽度分级基本未铺开

`scm-drawer.ts` 定义了 S/M/L 三级（600 / 780 / 940），但：

- 使用 `<a-drawer>` 的文件：**33 个**
- 引用 `scmDrawerWidth` / `SCM_DRAWER_WIDTH` 的文件：**4 个**（`customer-form-drawer`、`supplier-form-drawer`、`route-form-drawer`、`agreement-price-form-drawer`）

其余页面直接硬编码，且出现规范外的宽度：

```text
inventory-conversion-detail-drawer.vue   1000
inventory-loss-gain-detail-drawer.vue     860
inventory-stocktake-detail-drawer.vue     860
inventory-transfer-detail-drawer.vue      860
inventory-outbound-list.vue (详情)         760
customer-type-price-form-drawer.vue       620
（另有 1120 等更宽取值）
```

`1000 / 860 / 1120` 都不落在 S/M/L 三级内，属于 §6.2 规范之外的宽度。

**判断**：这不是"清理"，而是一次**规范铺开**——把 29 个未接入的 Drawer 映射到 S/M/L，会改变实际视觉宽度。建议作为独立批次，先决定"是否允许 S/M/L 之外的第四级（如 XL 1000～1120）"，再迁移。

#### A4 `.num` 与 `.scm-quantity` 口径分歧（即缺口盘点中的 F1）

现状（与 `422cc988` 登记一致，本次复核确认）：

- `.num` 在 `views/business/scm` 下由 **19 个文件**各自定义（16 个为 `font-family: ui-monospace, …` 等宽字体，3 个为 `font-variant-numeric: tabular-nums`）；
- `.num` 作为 class 被使用 **约 55 处，分布在 17 个文件**；
- 全局 `theme/scm/table.less` 只有 `.scm-money` / `.scm-quantity`（比例字体 + `tabular-nums`），**没有等宽工具类**。

因此二者**不是同一件事**：`.num` 是等宽字体，`.scm-quantity` 是比例字体仅数字等宽。直接替换会静默改变字体，属设计变更而非清理。`422cc988` 未做机械替换是正确的。

**倾向方案**（需作为一次有意识的视觉决策提交，而非 CSS 清理）：

```css
.scm-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
```

并明确三者分工：

```text
.scm-money     金额
.scm-quantity  业务数量 / 普通数字
.scm-mono      单号 / 编码 / 技术数字 / 需要字符等宽的内容
```

#### A5 少量列表仍展示审计时间

`supplier-sku-list.vue`、`print-template-list.vue` 仍默认展示创建/更新时间。需按 §7.3 判断是否属"时间字段例外"；`customer-detail.vue` 属详情页，合规。

### B. 后端字段依赖（不可在 UI 提交里做）

B1～B8 的完整证据、前置条件与补齐方式见 [后端字段缺口盘点](frontend-ui-backend-gap-inventory.md)，本文件不重复展开，只标注影响面：

| 编号 | 影响的 UI 段落 |
| --- | --- |
| B1 | 收货报表页面级汇总（§21）——**前端不得用当前页数据求和冒充全量** |
| B2 | 收货明细经办人 |
| B3 | 财务详情"系统信息"段（§20.6 第 7 段）——前端已在 `finance-detail-drawer.vue:97` 注释登记不渲染 |
| B4 | 订单详情"库存 / 配送状态"段（§14.3 第 5 段） |
| B5 | 订单详情"退货 / 退款"段（§14.3 第 6 段） |
| B6 | 退货列表原订单号 / 客户名 |
| B7 | 优惠券列表发放 / 领取 / 使用量 |
| B8 | 库存预留规格值 |

### C. 待决口径（需先定决策再迁移）

- **C1**：`A4` 的 `.scm-mono` 方案与 `.num` 迁移范围（是否保留部分页面 `.num`，还是全量迁移）。
- **C2**：`A3` 是否新增 S/M/L 之外的第四级宽度，以及 33 个 Drawer 的分级映射表。

## 6. 建议收尾顺序

按"先定口径、再批量套用、最后逐页收尾"推进，每批独立提交：

1. **Sprint F-A｜口径决策（先做，无代码风险）**
   定 C1（`.scm-mono`）与 C2（Drawer 分级映射）。产出：一份口径决策 + 映射表。
2. **Sprint F-B｜数值口径统一**
   主题新增 `.scm-mono`，迁移 `.num`（A4）。独立视觉决策提交，需说明字体变化。
3. **Sprint F-C｜操作列收尾**
   A1 + A2 合并处理：7 处超宽列接入 `ScmActionMore`，10 处补 `align: 'center'`。
4. **Sprint F-D｜Drawer 宽度铺开**
   按 C2 映射表迁移 29 个 Drawer（A3）。
5. **Sprint F-E｜剩余判断项**
   A5 审计时间、§31 中"未验证"条目（单位居中、空值 `—`、二次确认、数字控件）逐页确认。
6. **后端侧**：B1～B8 独立开单排期，与上述前端批次**不合并**；后端补齐后前端再单独提交"段落开启 + 契约测试"。

## 7. 未覆盖 / 未验证

- 未运行构建、TypeScript 校验、ESLint、Playwright / E2E、contract tests。
- 未做 1920×1080 / 1440×900 / 1280 实机视觉验证；§31 中"未验证"条目仅代表静态扫描未发现问题。
- `screen/`（运营大屏）与 SmartAdmin 系统/支撑页面（§23 / §24）未逐页复核，二者属 P2。
- 未统计各列表"默认列数"，该结论需要实机列宽快照。
