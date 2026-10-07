# 前端文案「去牛皮癣」清理计划（第二期）

> 第一期（报表中心）已完成：提交 `29b4df9c`，报表模块的 `report-note` 与指标卡 ⓘ 归零，契约已钉住。
> 本期处理**其余模块**。规则依据 [SCM UI 长期规范](../architecture/scm-ui-guidelines.md) §8。

## 1. 实测现状（167 个 SCM 页面，AST 扫描）

`hint` 属性全仓库只剩 **0 处** —— 那是报表模块独有的问题，已随第一期清掉。真正需要处理的是下面四类载体：

| 载体 | 处数 | 性质 |
| --- | --- | --- |
| `class` 带 `hint` 的元素 | 54 | 混合：约 15 处是实现解释，约 35 处是真实操作约束 |
| 常驻 `a-alert type="info"` | 19 | 多数是「不告知就会做错」的操作约束 |
| `description` 属性 | 22 | 几乎全是空态文案（「暂无…」「请先…」）——**只在空的时候出现，不算常驻** |
| 渲染文本 > 40 字 | 14 | 详情抽屉里的操作后果说明（不可删除、版本冲突、幂等） |
| 渲染文本含实现细节 | 3 | 2 处该删，1 处是装饰标题 |
| `extra` / `help` | 1 / 2 | 分别是「结束时间不包含」与两条价格规则，都该留 |

按模块分布（同一模块内可并行，模块间可并行）：

| 模块 | hint 类 | 常驻 info | description | 长句 |
| --- | --- | --- | --- | --- |
| finance | 10 | 3 | 4 | 0 |
| purchase | 10 | 2 | 5 | 2 |
| inventory | 2 | 6 | 1 | 7 |
| product | 6 | 2 | 2 | 0 |
| promotion | 8 | 0 | 0 | 0 |
| pricing | 3 | 2 | 0 | 0 |
| order | 4 | 2 | 1 | 2 |
| sorting | 2 | 2 | 1 | 2 |
| supplier | 2 | 0 | 1 | 0 |
| delivery | 2 | 0 | 3 | 0 |
| print | 2 | 0 | 2 | 0 |
| 其余（screen / customer / components / common / report） | 3 | 0 | 2 | 1 |

**结论：这不是「一大片废话」，而是「一批能删的 + 一大批该压缩的」。** 一刀切全删会把「超收容差整笔拒绝」「零价有效」「流水不可删除」这类真正防止操作错误的提示一起删掉，那是把护栏当噪声。

## 2. 判据（把 §8.1 收紧成可执行的六条）

### 2.1 必删

1. **解释实现**：SQL / 聚合、枚举名、字段名、`幂等` / `事务` / `精度`、内部流程驱动（「券的占用与核销由下单 / 退款流程驱动」）。
2. **解释正常状态**：「同一供应商可以同时有多条『默认来源』，这不是配置错误」「司机与车辆可稍后分配」。
3. **自我介绍与装饰**：「SUPPLY CHAIN INTELLIGENCE」这类。
4. **看控件就知道的操作**：「左右滑动表格查看其余金额、时点和操作列」（6 处重复）、「点供应商名称看商品维度明细」。
5. **能改造成 `placeholder` / 字段校验 message 的示例值与格式**：「填 95 表示按原价的 95% 计价」。
6. **计算链与推导**：「活动价在基础价（协议价 → 客户类型价 → 市场价）之后计算」。

### 2.2 必留（压缩到 ≤ 1 行，或改成就地提示）

1. **不告知就会做错**：半开区间（「开始包含，结束不包含」）、「零价也是有效价格」、「只有『接受』才写入分拣结果」、超收容差整笔拒绝。
2. **不可逆 / 不可改**：流水不可修改删除、审批后不可改、版本冲突需刷新后重审。
3. **导入格式与上限**：`.xlsx` / 5 MiB / 最多 500 行 / 模板列要求。
4. **权限与数据缺失**：Warning / Error，只在真的发生时出现。
5. **空态与引导**：「暂无…」「请先选择…」——空态不是常驻，保留原样。

### 2.3 形态改造优先于删除

常驻正文不是唯一的落点，能改形态的优先改：

| 现在 | 改成 |
| --- | --- |
| 常驻正文里的示例值 / 格式 | `placeholder` |
| 常驻正文里的规则说明 | 字段级 `help`（≤ 1 行）或提交时校验 message |
| 常驻正文里的不可逆警告 | 操作时二次确认 |
| 常驻正文里的按钮禁用原因 | 按钮 `Tooltip` |

## 3. 分批

### 批次 0：先把契约的盲区补上（前置，必须先做）

`scm-ui-copy-contract` 现在只扫 `placeholder / help / title / message / description / label / extra / empty-text`，`hint` 类元素与 `class="*hint*"` 只数总量、不看内容，长句完全不扫。**先按当前实测值设基线**（`hintClass 54`、`residentInfoAlert 19`、`description 22`、`longText 14`），再逐批往下压。否则每批清完都会长回来。

### 批次 1：必删的 6 类（纯删除，无交互变化，约 20 处）

跨 12 个模块，改动小、风险低，建议先做。清单：

| 文件 | 文案 | 归类 |
| --- | --- | --- |
| finance × 6 个列表页 | 「左右滑动表格查看其余金额、时点和操作列」等 | 2.1-4 |
| product/product-form-drawer | 「规格项用于描述不同商品规格；采购、销售与库存都按具体的商品规格记录。」 | 2.1-1 |
| promotion/promotion-activity-list | 「活动价在基础价（协议价 → 客户类型价 → 市场价）之后计算；互斥组决定可否叠加」 | 2.1-6 |
| promotion/promotion-activity-list | 「规则是受控键值：每种类型只接受自己的键」 | 2.1-1 |
| promotion/promotion-coupon-list | 「券的占用与核销由下单 / 退款流程驱动；试算不占用券」 | 2.1-1 |
| supplier/components/supplier-sku-editable-table | 「同一供应商可以同时有多条『默认来源』，这不是配置错误。」 | 2.1-2 |
| supplier/supplier-detail | 「未采集点位；点位用于地图分布与供应商位置查询」 | 2.1-2 |
| delivery/components/route-form-drawer | 「司机与车辆可稍后分配」 | 2.1-2 |
| screen/components/screen-header | 「SUPPLY CHAIN INTELLIGENCE」 | 2.1-3 |
| inventory/inventory-warning-list | 「…只在状态发生跃迁时发送，重复点击不会重复发信」 | 2.1-1 |
| purchase/purchase-demand-generate-modal | 「回看冻结时的解释行与逐行建议；数字不会随后续库存变化重算」 | 2.1-1 |
| order/order-detail | 「券需手动选择；试算不占用券，确认下单才占用。」 | 2.1-5 → 改 placeholder |

### 批次 2：54 处 `hint` 类，逐条三分类

- 删（≈15）：按 2.1 判据；
- 压缩到 ≤ 1 行（≈30）：按 2.2 保留，砍掉推导与后半句；
- 改 `placeholder` / 校验 message（≈9）：示例值与格式说明。

### 批次 3：19 处常驻 info alert，逐条判定

保留项压到 ≤ 1 行；「操作不可逆」类改成操作时确认。已知需保留的（不许为数字好看删掉）：
`pricing/customer-type-price-batch` 的「任一行失败则整批不写入；最多 500 行」、
`pricing/price-preview` 的「零价是有效价格」、
`pricing/agreement-price-form-drawer` 的「开始时间包含，结束时间不包含」、
`purchase/purchase-receipt-confirm-modal` 的超收容差整笔拒绝、
`inventory/inventory-conversion-list` 的「审批通过才调整库存」。

### 批次 4：14 处长句（详情抽屉里的操作后果）

这些多数**该留**（不可删除、版本冲突、幂等），但应从常驻正文挪进「操作后果」区并压缩。逐条判定后，能改操作时确认的改掉。

### 批次 5：收口——拆掉「解释位」

只删文案不删位，下一轮会被顺手填回去（第一期已踩过）。本批做：
- 把 `table-scroll-hint`、`scm-form-section__hint` 的**默认文案**从组件里拿掉，只保留显式传入的；
- 报表模块已做过的同类收口（`ReportKpiCard.hint`、图表 `extra`）作为模板；
- 契约按批下调基线，只降不升。

## 4. 每批的验收

1. `cd xsy-scm-web && node --experimental-strip-types --test test/*.test.mjs`（全绿）
2. `python tools/ts_baseline_ratchet.py check`（scm errors 必须为 0）
3. 改动文件 eslint
4. **契约反向验证**：注入一处违规确认变红再还原（改规则时的铁律）
5. 不跑 build、不部署（AGENTS.md：未要求不跑；线上前端产物本来就比仓库旧）

## 5. 明确不做的事

- 不动 Error / Warning / 空态文案；
- 不动 `placeholder` 里的示例（只把常驻说明挪进去）；
- 不为让计数好看而删掉操作约束（2.2 清单是硬边界）；
- 不跨模块一把梭：一批一提交，出问题可单独回退。

## 6. 待确认

- 批次 1 的 12 条清单是否都要删？（`order-detail` 那条建议改成 placeholder 而不是直接删）
- 54 处 `hint` 里的 35 处「真实操作约束」，是否接受压缩到 ≤ 1 行（会损失一部分上下文）？
- 批次 3 的 19 处常驻 info alert，是否允许把不可逆类改成操作时二次确认（会产生交互变化）？
