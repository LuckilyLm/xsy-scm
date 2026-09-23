# Finance R0 — 财务与报表只读地基实施方案（最终版）

> 目标路径：`docs/plan/finance-reporting-r0-plan.md`  
> 项目：`LuckilyLm/xsy-scm`  
> 规划日期：2026-09-23  
> 规划基线：`main @ c9f6954ded09f1c0c037e4bd00816381ad7202a9`  
> 规划时 Flyway 最大版本：`V49`  
> 状态：**R0 Core 已实施（2026-09-23，V50–V51；验证与未覆盖项见 `docs/progress.md` 同名记录）**  
> 参考来源：当前 `xsy-scm` 真实代码/数据库结构 + 项目需求文档 + 蔬东坡参考页面截图  
> 核心原则：**参考交互与信息组织，不机械复制对方财务口径。所有数字必须能由当前 XSY 已存在事实证明。**

---

# 0. 结论

Finance R0 不建设完整财务账。

本阶段只建设：

```text
已有订单事实
+ 已有采购事实
+ 已有收货事实
+ 已有库存流水
+ 已有移动加权成本事实
        ↓
可筛选 / 可下钻 / 可导出 / 可对账的报表中心
```

当前系统还没有稳定的：

```text
销售签收事实
应收事实
应付事实
收款 / 付款事实
核销事实
会计凭证
订单行 ↔ 销售出库成本完整归属链
```

所以 R0 禁止提前伪造：

```text
营业收入
已收款
待收款
应付款
客户利润
商品利润
销售毛利
财务毛利
资金流水
```

R0 完成后推荐主线：

```text
Finance R0（只读报表）
→ 分拣
→ Delivery L3（发货 / 出库联动 / 签收 / 完成）
→ Finance R1（应收 / 应付 / 收付款 / 核销）
→ Finance R2（利润 / 账龄 / 对账 / 财务分析）
```

---

# 1. 参考页面给出的设计启发

蔬东坡参考页面中值得借鉴的是页面结构，不是金额语义。

统一页面模式：

```text
快捷日期
+ 业务日期范围
+ 常用筛选
+ 可折叠高级筛选
+ 查询 / 重置 / 导出

↓

核心指标卡

↓

趋势 / TOP 排名 / 维度分布

↓

详细表格

↓

明细抽屉 / 原业务单下钻
```

本项目建议保留这些体验：

- `昨日 / 本周 / 上周 / 本月 / 上月` 快捷日期；
- “收起高级筛选”；
- 查询条件在页面顶部，不塞到表格 header；
- 重要金额/数量先展示指标卡；
- 排名图采用 TOP5 / TOP10；
- 多维统计采用 Tab，而不是复制多个几乎相同页面；
- 表格支持固定左列、横向滚动、合计行；
- “明细”尽量用右侧抽屉；
- 能跳原业务单的，不再造第二套详情页；
- 导出严格使用当前筛选口径；
- 所有金额/数量提示信息可以用 tooltip 解释口径。

明确不照搬：

- “下单金额 = 收入”；
- “采购金额 = 应付”；
- “收货金额 = 已付款”；
- “实际金额”这种当前 XSY 无对应事实的命名；
- 当前没有可靠历史期末平均成本时，伪造完整历史进销存金额；
- 当前没有订单成本归属链时，伪造毛利。

---

# 2. 当前 XSY 可证明的事实

## 2.1 销售订单

稳定事实：

```text
sales_order
sales_order_item
order_refund
order_return
```

已确认订单在确认时已经冻结：

```text
confirmed_at
settlement_total_amount
sales_order_item.actual_quantity
sales_order_item.locked_unit_price
sales_order_item.settlement_line_amount
```

因此 R0 的销售统计主口径：

```text
status = CONFIRMED
业务日期 = confirmed_at
金额 = settlement_total_amount / settlement_line_amount
```

不是：

```text
created_at
draft_unit_price
ordered_total_amount
```

`ordered_*` 只允许以“下单参考”字段展示。

---

## 2.2 采购订单

当前采购状态包含：

```text
DRAFT
SUBMITTED
PARTIALLY_RECEIVED
RECEIVED
SHORT_CLOSED
CANCELLED
```

可用事实：

```text
purchase_order.submitted_at
purchase_order.total_amount
purchase_order.supplier_id
purchase_order.purchaser_id
purchase_order.warehouse_id

purchase_order_item.planned_quantity
purchase_order_item.received_quantity
purchase_order_item.purchase_price
purchase_order_item.line_amount
```

R0 默认采购统计状态：

```text
SUBMITTED
PARTIALLY_RECEIVED
RECEIVED
SHORT_CLOSED
```

默认排除：

```text
DRAFT
CANCELLED
```

---

## 2.3 收货和入库必须分开

当前 XSY 已明确：

```text
收货确认
≠
库存入账完成
```

DIRECT：

```text
confirm
→ 同事务库存入账
```

WAREHOUSE_CONFIRM：

```text
confirm
→ putaway_status=PENDING
→ putaway
→ 库存真正入账
```

因此页面必须同时表达：

```text
收货确认状态
库存入账状态
```

不能把 `receipt.status=CONFIRMED` 直接叫“已入库”。

---

## 2.4 库存流水是 R0 的成本事实中心

`inventory_movement` 是 append-only 账本。

当前类型：

```text
PURCHASE_IN       采购入库
SALES_OUT         销售出库
STOCKTAKE_GAIN    盘盈
STOCKTAKE_LOSS    盘亏
LOSS_REPORT       报损
GAIN_REPORT       报溢
TRANSFER_OUT      调拨转出
TRANSFER_IN       调拨转入
CONVERT_OUT       规格转换出
CONVERT_IN        规格转换入
```

数量恒为正，方向由枚举表达。

当前流水已有：

```text
warehouse_id
sku_id
movement_type
source_document_type
source_document_id
source_document_item_id
quantity
unit_snapshot
unit_cost
before_quantity
after_quantity
occurred_at
operator
```

其中销售出库流水已经冻结：

```text
SALES_OUT.unit_cost = 出库时库存 avg_cost
```

所以 R0 可以可靠展示：

```text
出库成本流水
```

但当前不能可靠把某笔出库成本分配到：

```text
某张销售订单
某个客户
某个销售员
```

因此仍禁止做订单利润 / 客户利润。

---

# 3. R0 信息架构

参考蔬东坡页面后，不建议做“一个页面塞 7 个大 Tab”。

最终建议一级菜单：

```text
报表中心
```

子菜单：

```text
经营概览
销售分析
采购分析
收货与入库
库存分析
```

这样既保留 ERP 报表的独立入口，也避免复制十几个页面。

前端建议：

```text
xsy-scm-web/src/views/business/scm/report/
├─ overview/
│  └─ index.vue
├─ sales/
│  └─ index.vue
├─ purchase/
│  └─ index.vue
├─ receipt/
│  └─ index.vue
├─ inventory/
│  └─ index.vue
├─ components/
│  ├─ report-date-filter.vue
│  ├─ report-kpi-card.vue
│  ├─ report-section-title.vue
│  ├─ report-top-chart.vue
│  └─ report-detail-drawer.vue
└─ report-model.ts

xsy-scm-web/src/api/business/scm/report-api.ts
```

后端独立只读域：

```text
net.lab1024.sa.admin.module.scm.report
```

不要把 R0 查询代码塞进未来 `finance` 写领域。

---

# 4. 页面 1：经营概览

参考：

```text
蔬东坡：营业数据 / 订单统计
```

但 XSY 使用自己的事实口径。

## 4.1 筛选

基础：

```text
快捷日期：昨日 / 本周 / 上周 / 本月 / 上月
确认日期
仓库
客户
销售员
```

高级：

```text
订单来源
客户类型（只有现有事实可靠时）
商品分类
商品关键字
```

第一版可以不做：

```text
线路 / 区域 / 集团
```

除非当前正式表已有稳定关联，禁止为了参考页面新建字段。

---

## 4.2 KPI 卡

第一行：

```text
已确认订单数
下单客户数
已确认订单金额
已完成退款金额
已提交采购金额
采购入库成本金额
当前库存账面金额
```

定义：

### 已确认订单数

```sql
sales_order.status = 'CONFIRMED'
confirmed_at in range
```

### 下单客户数

```sql
COUNT(DISTINCT customer_id)
```

同一销售确认范围。

### 已确认订单金额

```sql
SUM(settlement_total_amount)
```

必须命名：

```text
已确认订单金额
```

不得命名：

```text
营业收入
销售收入
实收金额
```

### 已完成退款金额

```text
order_refund.status = COMPLETED
completed_at in range
```

独立展示，不自动冲减订单金额。

### 已提交采购金额

```text
SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED
submitted_at in range
SUM(purchase_order.total_amount)
```

### 采购入库成本金额

```text
inventory_movement.movement_type = PURCHASE_IN
occurred_at in range
SUM(quantity * unit_cost)
```

### 当前库存账面金额

```text
SUM(inventory_balance.quantity * inventory_balance.avg_cost)
```

必须显示：

```text
当前库存账面金额
截至 yyyy-MM-dd HH:mm:ss
```

它不受查询历史范围影响。

---

## 4.3 趋势图

参考蔬东坡“订单统计趋势”。

首期只做三条：

```text
已确认订单金额
已完成退款金额
采购入库成本金额
```

粒度：

```text
按日
```

超过 90 天可自动切：

```text
按周
```

但第一版也可先固定按日，查询跨度限制 366 天。

---

## 4.4 每日统计表

列：

```text
业务日期
已确认订单数
下单客户数
已确认订单金额
已完成退款金额
已提交采购金额
采购入库成本金额
```

点击某日可跳带日期 deep-link 的对应分析页。

---

# 5. 页面 2：销售分析

参考：

```text
商品销量
订单统计
客户统计
```

最终使用 Tab：

```text
按商品
按分类
按客户
按销售员
订单明细
```

默认 `按商品`。

---

# 6. 销售分析 — 按商品

## 6.1 筛选

```text
确认日期
商品关键字
商品分类
客户
销售员
订单来源
仓库（仅当当前订单事实有可靠仓库归属时）
```

高级筛选收起。

---

## 6.2 TOP 图

参考蔬东坡商品销量的 TOP5。

XSY 使用：

```text
已确认订单金额 TOP5
已完成退款金额 TOP5（能按 SKU 稳定归属退款时才开放）
```

如果当前退款事实无法可靠拆到 SKU：

```text
首期只做订单金额 TOP5
```

不能按比例猜退款。

---

## 6.3 明细表粒度

```text
一行 = 一个 SKU
```

列：

```text
商品名称
分类
SPU 编码
SKU 编码
规格
销售单位

订单笔数
客户数
确认数量
成交均价
确认订单金额
金额排名
```

成交均价：

```text
SUM(settlement_line_amount)
/
SUM(actual_quantity)
```

必须同单位聚合。

数量为 0：

```text
avgPrice = null
```

不是 0。

---

# 7. 销售分析 — 按分类

参考蔬东坡“按分类统计”。

粒度：

```text
一级分类 + 二/三级分类（以当前正式分类模型为准）
```

列：

```text
一级分类
末级分类
确认订单金额
订单笔数
客户数
金额排名
```

图：

```text
分类确认订单金额 TOP5
```

不要加入“实际金额”这种 XSY 当前无独立定义的字段。

---

# 8. 销售分析 — 按客户

参考蔬东坡“客户统计”。

KPI：

```text
订单笔数
客户数
确认订单金额
已完成退款金额
```

图：

```text
客户确认订单金额 TOP5
```

表：

```text
客户编码
客户名称
销售员
订单笔数
SKU 种类数
确认订单金额
已完成退款金额
最近确认时间
金额排名
```

退款只能在能可靠按 `order_id -> customer_id` 汇总时展示。

不生成：

```text
已收
未收
应收余额
```

---

# 9. 销售分析 — 按销售员

名称：

```text
销售员订单业绩
```

不是：

```text
销售收入
利润
提成
```

列：

```text
销售员
订单笔数
客户数
确认订单金额
已完成退款金额
最近确认时间
```

`NULL seller_id`：

```text
未分配销售员
```

---

# 10. 销售分析 — 订单明细

粒度：

```text
一行 = 一个 sales_order_item
```

只取：

```text
order.status = CONFIRMED
```

列：

```text
确认时间
订单号
客户编码
客户名称
销售员
订单来源
结算方式

SPU 编码
商品名称
SKU 编码
规格
商品类型
销售单位

订购数量
实际数量
锁定成交单价
价格来源
结算金额
是否手工改价
手工改价原因
```

优先展示订单快照字段，不实时覆盖历史名称。

订单号下钻：

```text
复用现有订单详情
```

---

# 11. 页面 3：采购分析

参考：

```text
采购汇总
供货统计
价格波动
```

R0 使用 Tab：

```text
采购概览
按商品
按供应商
按采购员
采购明细
```

价格波动作为 `R0-B` 安全扩展，见后文。

---

# 12. 采购分析 — 采购概览

## 12.1 KPI

参考蔬东坡的卡片布局，但改成 XSY 可证明口径：

```text
已提交采购单
已提交采购金额
已确认收货单
收货参考金额
采购入库成本金额
待入库收货单
```

禁止：

```text
应付金额
```

---

## 12.2 供应商 TOP10

参考蔬东坡“供应商入库金额 TOP10”。

XSY 第一版用：

```text
供应商采购入库成本 TOP10
```

来源：

```text
PURCHASE_IN movement
→ purchase_receipt_item
→ purchase_order_item
→ purchase_order
→ supplier
```

不是简单用采购单金额。

---

# 13. 采购分析 — 按商品

粒度：

```text
一行 = SKU
```

列：

```text
商品
SKU
分类
采购单位

采购单数
计划采购数量
已收数量
采购订单金额
采购成交均价
采购入库数量
采购入库成本金额
```

均价采用：

```text
金额 / 数量
```

不能平均多个单价字段。

---

# 14. 采购分析 — 按供应商

参考截图“按供应商”。

粒度：

```text
一行 = supplier_id
```

列：

```text
供应商编码
供应商名称
采购单数
SKU 种类数
计划采购数量（按单位分组，不跨单位总计）
采购订单金额
已收参考金额
采购入库成本金额
最近采购时间
```

图：

```text
供应商采购入库成本 TOP10
```

支持点击供应商：

```text
右侧抽屉
→ 展示该供应商商品维度明细
```

类似参考系统“市场自采采购明细”的抽屉体验，但不复制其采购模式字段。

---

# 15. 采购分析 — 按采购员

参考截图“按采购员”。

粒度：

```text
一行 = purchaser_id
```

列：

```text
采购员
采购单数
SKU 种类数
采购订单金额
已收参考金额
采购入库成本金额
最近采购时间
```

点击采购员：

```text
抽屉查看商品采购明细
```

---

# 16. 采购分析 — 采购明细

粒度：

```text
一行 = purchase_order_item
```

默认状态：

```text
SUBMITTED
PARTIALLY_RECEIVED
RECEIVED
SHORT_CLOSED
```

日期：

```text
purchase_order.submitted_at
```

列：

```text
提交时间
采购单号
状态
供应商
采购员
仓库
计划到货日期

SPU
商品
SKU
规格
采购单位

计划数量
累计收货数量
采购单价
采购行金额
```

支持：

```text
采购单号 → 原采购单详情
```

---

# 17. 页面 4：收货与入库

这个页面是 XSY 与参考系统最需要“借结构、不抄语义”的地方。

Tab：

```text
收货明细
入库明细
待入库
```

---

# 18. 收货明细

粒度：

```text
一行 = purchase_receipt_item
```

默认：

```text
receipt.status = CONFIRMED
```

列：

```text
收货确认时间
收货单号
采购单号
供应商
仓库
收货模式
入库状态

商品
SKU
规格
采购单位

本次收货数量
累计收货数量
剩余数量
超收数量
收货差异

采购单价
收货参考金额
```

收货参考金额：

```text
receipt_item.received_quantity
×
purchase_order_item.purchase_price
```

必须叫：

```text
收货参考金额
```

禁止：

```text
应付金额
```

---

# 19. 入库明细

事实来源：

```text
inventory_movement
movement_type = PURCHASE_IN
```

粒度：

```text
一行 = 一条采购入库 movement
```

列：

```text
入库时间
仓库
收货单号
采购单号
供应商
商品
SKU
单位

入库数量
入库单位成本
入库成本金额
操作人
```

金额：

```text
quantity * unit_cost
```

`unit_cost IS NULL`：

```text
显示 —
costMissing=true
```

禁止当 0。

---

# 20. 待入库

只看：

```text
WAREHOUSE_CONFIRM
+ receipt.status = CONFIRMED
+ putaway_status = PENDING
```

列：

```text
收货单号
采购单号
供应商
仓库
确认时间
商品种类
收货数量
操作
```

操作：

```text
查看原收货单
```

Finance R0 是只读报表页，不能在这里新增“确认入库”写入口。

---

# 21. 页面 5：库存分析

参考截图：

```text
进销存
损耗概况
采购损耗
库房损耗
```

XSY R0 不照抄完整历史成本进销存。

Tab：

```text
库存流水
损耗分析
当前库存价值
收发存（数量版）
```

---

# 22. 库存流水

粒度：

```text
一行 = inventory_movement
```

筛选：

```text
发生日期
仓库
商品
SKU
流水类型
来源单据类型
```

列：

```text
发生时间
仓库
商品
SKU
流水类型
方向
来源单据类型
来源单号
来源单据 ID / 行 ID

数量
单位
单位成本
成本金额
变动前数量
变动后数量
操作人
```

成本金额：

```text
quantity * unit_cost
```

方向必须由：

```text
ScmInventoryMovementTypeEnum.inbound
```

派生。

禁止报表再维护一份 IN / OUT 类型硬编码。

---

# 23. 损耗分析

参考蔬东坡“损耗概况”，但映射到 XSY 已存在流水类型。

XSY 当前可证明：

```text
STOCKTAKE_LOSS  盘亏
LOSS_REPORT     手工报损
```

当前没有明确独立：

```text
采购损耗
退货损耗
```

所以 R0 首期不伪造这两类。

KPI：

```text
盘亏数量（按单位）
盘亏成本金额
报损数量（按单位）
报损成本金额
损耗总成本金额
```

图：

```text
损耗类型金额占比
损耗金额按日趋势
```

表：

```text
商品
SKU
仓库
损耗类型
数量
单位
单位成本
损耗成本金额
来源单号
发生时间
操作人
```

其中：

```text
STOCKTAKE_LOSS
→ 盘亏

LOSS_REPORT
→ 报损
```

如果未来增加采购损耗 / 退货损耗的正式业务事实，再扩报表枚举。

---

# 24. 当前库存价值

粒度：

```text
一行 = inventory_balance
```

列：

```text
仓库
商品
SKU
当前数量
预留数量
可用数量
单位
当前移动平均成本
当前账面金额
```

金额：

```text
quantity * avg_cost
```

顶部 KPI：

```text
当前库存账面金额
有库存 SKU 数
零库存 SKU 数（若页面选择显示）
```

必须标注：

```text
当前时点
```

不能和历史查询区间混淆。

---

# 25. 收发存（数量版）

参考蔬东坡“进销存”，但 R0 只做当前事实能可靠重建的部分。

首期字段：

```text
商品
SKU
单位

期内采购入库数量
期内销售出库数量
期内盘盈数量
期内盘亏数量
期内报溢数量
期内报损数量
期内调拨入
期内调拨出
期内转换入
期内转换出

期内净变动量
```

所有数量按：

```text
warehouse + sku + unit
```

聚合。

禁止跨单位相加。

### 历史期初 / 期末数量

只有实施时验证：

```text
inventory_movement 从系统库存起点完整覆盖
```

才能加入：

```text
期初数量
期末数量
```

如果无法证明完整覆盖：

```text
R0 不展示
```

### 历史期初 / 期末均价、金额

R0 明确不做。

当前 movement 保存的是：

```text
本次 movement unit_cost
```

不是：

```text
每次 movement 后 avg_cost
```

所以不能直接还原蔬东坡截图中的：

```text
期初均价
期初金额
期末均价
期末金额
```

除非未来新增可靠成本快照 / 成本重放算法。

---

# 26. R0-B 安全扩展：采购价格波动

参考蔬东坡“价格波动”。

这项可以做，但不阻塞 R0 Core 上线。

页面：

```text
采购分析 → 价格波动
```

筛选：

```text
日期
商品 / SKU
供应商
采购员
仓库
```

折线：

```text
采购成交价
```

事实来源：

```text
purchase_order_item.purchase_price
+ purchase_order.submitted_at
```

同一天多笔：

```text
按数量加权平均成交价
```

公式：

```text
SUM(purchase_price * planned_quantity)
/
SUM(planned_quantity)
```

按单位分别计算。

禁止：

```text
箱价 + kg 价混到一条线
```

当前 R0 不复制参考系统的：

```text
各客户类型销售价曲线
```

因为 XSY 当前定价体系不是“每日客户类型价历史表”。

如果后续要做销售成交价波动，应使用：

```text
CONFIRMED sales_order_item.locked_unit_price
```

并单独命名：

```text
销售成交价波动
```

---

# 27. 暂不进入 R0 的参考页面

以下参考页面价值很大，但属于后续：

```text
客户结算
批量对账
客户费用
结算单
客户账款
客户余额
在线支付流水
资金流水
利润表
预计毛利
销售毛利
财务毛利
```

对应规划：

```text
Finance R1：
应收 / 应付
收款 / 付款
核销
对账 / 结算单
客户账款

Finance R2：
销售成本归属
商品利润
客户利润
销售员利润
利润表
资金与经营分析
```

不要让 AI 因为参考截图存在就提前实现。

---

# 28. 公共日期规则

前端统一：

```text
startDate: LocalDate
endDate: LocalDate
```

用户输入闭区间：

```text
2026-09-01 ~ 2026-09-30
```

后端统一转为 Asia/Shanghai 半开区间：

```text
[2026-09-01 00:00:00,
 2026-10-01 00:00:00)
```

建议公共类：

```text
ScmReportDateRange
ScmReportTimeRangeResolver
```

所有报表禁止自己写一套日界转换。

默认：

```text
本月
```

或：

```text
近 7 天
```

具体默认值可统一选一个，建议报表中心使用：

```text
本月
```

最大同步查询跨度：

```text
366 天
```

超过显式报错。

---

# 29. 金额与数量精度

当前正式业务事实大量使用：

```text
NUMERIC(18,4)
ScmFixedScale4Serializer
```

因此 R0：

```text
继续 4 位精度
不自行 ROUND 到 2 位
不把 BigDecimal 转 double 再计算
```

响应：

```json
{
  "amount": "1234.5600",
  "quantity": "8.0000",
  "unitCost": "6.2350"
}
```

前端：

```text
只格式化展示
不参与财务计算
```

Finance R1 再裁决正式财务单据是否采用 2 位金额。

---

# 30. API 设计

建议统一 Controller：

```text
/scm/report
```

## 经营概览

```http
POST /scm/report/overview
POST /scm/report/overview/trend
POST /scm/report/overview/daily
```

## 销售

```http
POST /scm/report/sales/product
POST /scm/report/sales/category
POST /scm/report/sales/customer
POST /scm/report/sales/seller
POST /scm/report/sales/item/query

POST /scm/report/sales/product/export
POST /scm/report/sales/customer/export
POST /scm/report/sales/item/export
```

## 采购

```http
POST /scm/report/purchase/overview
POST /scm/report/purchase/product
POST /scm/report/purchase/supplier
POST /scm/report/purchase/purchaser
POST /scm/report/purchase/item/query

POST /scm/report/purchase/product/export
POST /scm/report/purchase/supplier/export
POST /scm/report/purchase/item/export
```

## 收货 / 入库

```http
POST /scm/report/receipt/query
POST /scm/report/inbound/query
POST /scm/report/pending-putaway/query

POST /scm/report/receipt/export
POST /scm/report/inbound/export
```

## 库存

```http
POST /scm/report/inventory/movement/query
POST /scm/report/inventory/loss/summary
POST /scm/report/inventory/loss/query
POST /scm/report/inventory/value/query
POST /scm/report/inventory/flow-summary/query

POST /scm/report/inventory/movement/export
POST /scm/report/inventory/loss/export
POST /scm/report/inventory/value/export
```

## R0-B 价格波动

```http
POST /scm/report/purchase/price-trend
```

---

# 31. 后端结构

```text
module/scm/report/
├─ controller/
│  └─ ScmReportController.java
├─ service/
│  ├─ ScmOverviewReportService.java
│  ├─ ScmSalesReportService.java
│  ├─ ScmPurchaseReportService.java
│  ├─ ScmReceiptReportService.java
│  └─ ScmInventoryReportService.java
├─ dao/
│  └─ ScmReportDao.java
├─ domain/
│  ├─ form/
│  └─ vo/
└─ support/
   ├─ ScmReportTimeRangeResolver.java
   ├─ ScmReportExportGuard.java
   └─ ScmReportSortWhitelist.java
```

SQL：

```text
xsy-scm-server/sa-admin/src/main/resources/mapper/scm/report/ScmReportMapper.xml
```

遵守 AGENTS：

```text
禁止 mapper annotation SQL
```

---

# 32. 权限

建议：

```text
scm:report:overview:query
scm:report:sales:query
scm:report:purchase:query
scm:report:inventory:query
scm:report:cost:query
scm:report:export
```

映射：

```text
经营概览
→ overview:query

销售分析
→ sales:query

采购分析
→ purchase:query

收货与入库
→ purchase:query + cost:query（涉及入库成本时）

库存流水 / 损耗
→ inventory:query

库存单位成本 / 账面金额 / 出入库成本
→ cost:query
```

成本权限要单独存在。

这样普通仓管可以：

```text
看数量流水
```

但未必可以：

```text
看采购成本 / 库存金额
```

导出规则：

```text
对应 query 权限
AND scm:report:export
```

例如成本流水导出至少：

```text
scm:report:inventory:query
AND scm:report:cost:query
AND scm:report:export
```

---

# 33. 非管理员角色门禁

R0 可以先 SUPER_ADMIN 验收。

如果要正式上线：

```text
财务
采购员
仓管
销售
```

则不能只做按钮权限。

还必须处理：

```text
正式数据范围
F0 FA-1 / FA-2 文件授权债
成本敏感字段权限
```

完成前 `docs/progress.md` 必须写：

```text
“功能权限已验证，正式岗位数据范围未宣称完成。”
```

---

# 34. Flyway

R0 新财务事实表：

```text
0
```

不创建：

```text
receivable
payable
payment
voucher
report_snapshot
```

需要的 migration 仅：

```text
菜单
权限
必要索引
```

规划时最大版本：

```text
V49
```

如果开工时远端仍为 V49，可考虑：

```text
V50__scm_report_center_permissions.sql
```

但 AI 必须在创建前：

```bash
git fetch
检查 origin/main
重新扫描最大 Flyway
```

本文件中的 V50 只是设计占位，不是锁号。

---

# 35. 索引策略

不先拍脑袋建大量索引。

实施时对这些核心查询执行：

```sql
EXPLAIN (ANALYZE, BUFFERS)
```

重点检查：

```text
sales_order(status, confirmed_at)
purchase_order(status, submitted_at)
purchase_receipt(status, confirmed_at)
inventory_movement(occurred_at, movement_type)
inventory_movement(warehouse_id, sku_id, occurred_at)
```

如果现有索引不足，再单独迁移新增。

建议候选：

```sql
CREATE INDEX ... ON sales_order (confirmed_at DESC, id DESC)
WHERE deleted = FALSE AND status = 'CONFIRMED';
```

是否真正添加，以 EXPLAIN 为准。

---

# 36. SQL 纪律

## 36.1 报表只读冻结事实

只能聚合：

```text
settlement_total_amount
settlement_line_amount
purchase_price
purchase line_amount
inventory_movement.unit_cost
inventory_balance.avg_cost
```

禁止重新调用：

```text
PriceResolver
MovingAverageCost 重新演算当前历史
采购定价规则
订单定价规则
```

否则历史报表会随今天规则变化。

---

## 36.2 防 JOIN 放大

例如：

```text
订单
× 订单行
× 退款
```

不能直接 JOIN 后 SUM。

必须先：

```text
refund 按 order_id 聚合
```

再 JOIN。

同理：

```text
receipt_item
× movement
```

必须利用：

```text
source_document_type
source_document_item_id
```

稳定锚点。

---

## 36.3 数量不跨单位总计

禁止：

```text
10kg + 5箱 + 2件 = 17
```

所有数量合计：

```text
按 unit 分组
```

如果 UI 只有一个指标卡，展示：

```text
SKU 数
单据数
金额
```

不要展示无量纲总数量。

---

# 37. 明细抽屉

参考截图中“本期入库明细 / 出库明细 / 报损报溢明细”的交互值得保留。

XSY 使用右侧抽屉：

## 库存流水来源明细

点：

```text
采购入库
```

可展示：

```text
收货单号
采购单号
供应商
入库数量
单位成本
成本金额
```

点：

```text
销售出库
```

展示：

```text
出库单号
数量
出库单位成本
出库成本金额
```

点：

```text
盘亏 / 报损
```

展示：

```text
来源单号
数量
单位成本
成本金额
原因/备注（当前领域已有时）
```

能跳原业务详情的继续使用 deep-link。

---

# 38. Excel 导出

R0 首期同步导出。

统一上限建议：

```text
100000 行
```

如果性能验证不通过则下调。

必须：

```text
列表筛选 = 导出筛选
列表口径 = 导出口径
服务端权限
完整结果
```

超过上限：

```text
明确拒绝
```

不能静默只导前 N 行。

沿用项目现有 Excel 技术：

```text
FastExcel / SmartExcelUtil
```

不为 R0 新造异步导出任务中心。

---

# 39. 与现有数据大屏关系

已有：

```text
ScreenDataService
```

不能直接当 Finance R0 的数据源。

原因：

当前部分大屏统计使用：

```text
created_at
```

R0 销售财务型报表使用：

```text
confirmed_at
```

所以：

```text
大屏 = 实时运营视角
报表 = 冻结业务事实视角
```

可以共享：

```text
Asia/Shanghai 时间工具
movement direction enum
公共金额格式
```

不能把同名指标复制成两套不同 SQL 后不写口径说明。

---

# 40. 后端测试

必须有真实 PostgreSQL IT。

## 销售

```text
DRAFT 不计
PENDING 不计
CANCELLED 不计
CONFIRMED 计

startDate 00:00 计入
endDate+1 00:00 不计入

金额取 settlement
```

## 退款

```text
未完成不计
COMPLETED 计

多订单行不能把退款放大
```

## 采购

```text
DRAFT/CANCELLED 默认不计

SUBMITTED
PARTIALLY_RECEIVED
RECEIVED
SHORT_CLOSED
计入

业务日期按 submitted_at
```

## 收货 / 入库

```text
WAREHOUSE_CONFIRM + PENDING：
收货明细有值
入库明细无值

putaway：
产生 PURCHASE_IN
入库明细出现
成本金额精确
```

## 销售出库成本

```text
SALES_OUT unit_cost = 当时 avg_cost
库存流水成本金额正确
```

但不得把它自动归给订单。

## 损耗

```text
STOCKTAKE_LOSS → 盘亏
LOSS_REPORT → 报损

STOCKTAKE_GAIN / GAIN_REPORT 不进入损耗成本
```

## 权限

```text
无 query → 拒绝
有 query 无 cost → 成本字段/API 拒绝
有 query 无 export → 不能导出
有 export 无 query → 不能导出
```

---

# 41. 前端测试

至少覆盖：

```text
快捷日期
高级筛选展开/收起
Tab 切换不串条件
旧请求不能覆盖新请求
reset 回默认范围
null cost 显示 —
金额字段不 Number 二次计算
导出使用当前筛选
成本权限隐藏 + 服务端拒绝
```

图表：

```text
空数据能显示空态
0 与 null 区分
TOP5 排序正确
tooltip 精度正确
```

---

# 42. Playwright E2E

至少：

```text
1. 打开经营概览 0 pageerror
2. 本月快捷范围正确
3. 已确认订单指标只统计 CONFIRMED
4. 销售商品 TOP 与测试数据一致
5. 客户明细可下钻订单
6. 采购 DRAFT/CANCELLED 默认不进入
7. 供应商 TOP10 正确
8. WAREHOUSE_CONFIRM 收货后出现在“待入库”
9. putaway 后从待入库消失并进入入库明细
10. 库存流水 PURCHASE_IN / SALES_OUT 成本正确
11. LOSS_REPORT / STOCKTAKE_LOSS 进入损耗分析
12. 成本无权限账号不能查看成本字段/API
13. 导出 xlsx 真文件可打开
14. 导出行数与筛选一致
```

---

# 43. 性能验收

核心列表：

```text
20 / 50 条分页
```

要求：

```text
无 N+1
total 正确
排序稳定
```

对：

```text
销售明细
采购明细
库存流水
```

做 EXPLAIN。

TOP 图：

```text
数据库直接聚合 TOP N
```

禁止：

```text
全量查 Java 再排序
```

---

# 44. 实施波次

## R0-0：基线与权限

```text
git fetch
确认 origin/main
确认最大 Flyway
新增报表菜单与权限
```

## R0-1：公共报表能力

```text
时间范围
排序白名单
导出 guard
公共筛选组件
KPI 组件
```

## R0-2：销售分析

```text
经营概览的销售部分
商品 / 分类 / 客户 / 销售员
订单明细
```

## R0-3：采购分析

```text
采购概览
商品
供应商
采购员
采购明细
```

## R0-4：收货 / 入库

```text
收货明细
入库明细
待入库
```

## R0-5：库存分析

```text
库存流水
损耗分析
当前库存价值
收发存数量版
```

## R0-6：经营概览收口

```text
统一 KPI
趋势
每日统计
```

经营概览放后面，是为了复用已经验证的明细口径。

## R0-7：Excel 导出

```text
所有核心表
```

## R0-8：测试

```text
PG IT
frontend tests
lint
type check
build
Playwright
```

## R0-9：文档

```text
docs/progress.md
需求覆盖清单
必要的 decisions
```

## R0-B：安全增强（非 Core 阻塞）

```text
采购价格波动
销售成交价波动（如果决定做）
```

---

# 45. 完成定义

Finance R0 只有同时满足以下条件才算完成：

- [ ] 不新增应收/应付/收付款/核销/凭证事实表；
- [ ] 经营概览来自已存在事实；
- [ ] 销售按 `CONFIRMED + confirmed_at + settlement_*`；
- [ ] 采购按提交后的采购事实；
- [ ] 收货与入库明确分离；
- [ ] 采购入库成本追到 `PURCHASE_IN`；
- [ ] 销售出库成本追到 `SALES_OUT`；
- [ ] 不把销售出库成本猜归属到订单；
- [ ] 损耗只统计当前真实类型；
- [ ] 当前库存金额明确为“当前时点”；
- [ ] 历史期初/期末成本未伪造；
- [ ] 数量不跨单位混加；
- [ ] 金额/数量保持 4 位事实精度；
- [ ] Asia/Shanghai 日界；
- [ ] query / cost / export 权限反向测试；
- [ ] SQL 无 N+1；
- [ ] EXPLAIN 核心查询通过；
- [ ] PG IT 通过；
- [ ] 前端测试 / lint / build 通过；
- [ ] Playwright 核心流程通过；
- [ ] 0 pageerror；
- [ ] docs/progress.md 据实更新。

---

# 46. Finance R1 前必须裁决

R0 做完后，Finance R1 仍必须等待这些明确答案：

```text
1. 应收到底在“发货 / 签收 / 配送完成”哪个事件生成？
2. 部分签收如何产生应收？
3. 退款 / 退货如何冲应收？
4. 应付在收货确认还是库存入账时生成？
5. 部分收货是一笔一笔生成应付，还是采购单结束汇总？
6. 超收 / 少收关单如何影响应付？
7. 一笔收款能否核销多笔应收？
8. 一笔应收能否分多次核销？
9. 预收 / 预付是否允许？
10. 销售订单行如何绑定真正 SALES_OUT 成本？
11. 财务金额最终采用 2 位还是继续与业务事实 4 位对账？
```

未裁决前：

```text
AI 不得自行决定
```

---

# 47. 给 AI 的执行指令

```text
请实施 docs/plan/finance-reporting-r0-plan.md。

这是 Finance R0：只读报表地基，不是完整财务模块。

开工前必须：
1. git fetch；
2. 确认 origin/main 最新 HEAD；
3. 阅读 AGENTS.md、docs/progress.md、docs/decisions.md；
4. 阅读本计划；
5. 重新确认最大 Flyway；
6. 以当前代码 / 表结构 / 状态机为事实源。

严格执行 R0-0 → R0-9。

硬边界：
- 不建 receivable/payable/payment/voucher；
- 不改销售订单状态机；
- 不实现分拣；
- 不实现 Delivery L3；
- 不把 CONFIRMED 订单金额叫营业收入；
- 不生成已收/未收/应收/应付；
- 不用当前 inventory_balance.avg_cost 回算历史订单利润；
- 不把独立 SALES_OUT 成本猜归某张订单；
- 不造历史期初/期末平均成本；
- 不跨单位汇总数量；
- 不建立报表事实副本；
- SQL 放 Mapper XML；
- 所有金额使用 BigDecimal / NUMERIC；
- 所有日期使用 Asia/Shanghai；
- 所有成本数据必须受 scm:report:cost:query 控制；
- export 同时要求 query + export 权限；
- 核心行为必须有真实 PostgreSQL IT；
- 关键用户路径必须 Playwright 实跑；
- 完成后据实更新 docs/progress.md。

如果实现中发现必须决定：
应收、应付、签收、核销、财务利润、费用、正式收入确认，
停止该部分，不自行裁决。
```

---

# 48. 最终定位

Finance R0 不是“财务模块的简化版”。

它是：

```text
订单
采购
收货
库存
成本
损耗
        ↓
统一只读分析层
```

参考蔬东坡的价值在于：

```text
它告诉我们怎样让用户看懂数据
```

当前 XSY 代码的价值在于：

```text
它决定我们到底能展示什么数据
```

两者结合后的原则是：

> **页面可以像成熟 ERP 一样好用，但数据口径只能来自 XSY 自己已经成立的事实。**
