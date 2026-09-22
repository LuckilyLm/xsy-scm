# 当前模块优化计划（参考蔬东坡 ERP V17.40）

> 文件：`docs/plan/current-module-optimization-from-sdongpo-v17.4.md`  
> 项目：鲜蔬源智链 `xsy-scm`  
> 规划日期：2026-09-22  
> 仓库基线：`main @ 45412fb946b79fec7779f5a8d63190119567d014`  
> 当前 Flyway 最大版本：`V43`  
> 参考资料：`蔬东坡B2B系统操作手册17.4.pdf`（V17.40，271 页）+ 用户补充的 3 张业务流程图（订单→采购→库存→配送主链 / 系统业务操作流程 / 生鲜配送企业角色流程）
> 文档定位：**只对照 xsy-scm 当前已经存在的功能做增量优化，不把蔬东坡未在本项目落地的模块提前纳入本轮。**

---

## 1. 目标与边界

本计划的目标不是把 `xsy-scm` 重做成蔬东坡，而是：

1. 保留当前已经验证的领域模型、状态机、库存账、权限与并发控制；
2. 吸收成熟生鲜配送 ERP 在**录单效率、批量处理、信息联动、打印、待办、业务上下文**方面的做法；
3. 优先优化已经存在的模块，让系统从“功能已经有”继续提升到“高频业务人员每天好用”；
4. 每个波次必须保持最小闭环，可独立上线、独立验收、独立回退；
5. 不为参考系统中的功能机械增加表、参数、状态或第二套业务事实。

### 1.1 本文纳入范围

只包括当前项目已有能力：

- SmartAdmin 系统底座 / 权限 / 操作日志；
- 商品中心；
- 客户 / 供应商；
- 定价；
- 销售订单；
- 采购 / 收货；
- 库存 / 盘点；
- 数据大屏；
- 物流配送 L0-L2。

### 1.2 本轮明确不做

以下能力虽然蔬东坡手册存在，但不是本文件的优化对象：

- 新建营销模块；
- 商城 / 小程序；
- 客户积分；
- 财务结算 / 对账体系；
- 分拣模块；
- 溯源模块；
- 司机 APP；
- GPS 轨迹 / 实时 ETA；
- 自动最优排线 / OR-Tools；
- 在线支付；
- 供应商门户；
- 新建完整消息配置中心。

这些功能应继续服从项目原 Roadmap，不因为参考手册存在就提前实现。

---

## 2. 参考手册中与当前项目直接相关的部分

本计划只吸收以下已能与当前模块一一对应的能力：

| 当前模块 | 蔬东坡手册位置 | 可借鉴点 |
| --- | --- | --- |
| 商品 | 第 2.4 节，约 p45-p50 | Excel 导入/维护、商品图片批量维护、主图/详情图、分类与辅助资料 |
| 订单 | 第 2.5 节，约 p51-p65 | 录单草稿恢复、历史订单复用、最近成交价、列表自定义、订单变更可见性 |
| 采购 | 第 2.6 节，约 p66-p90 | 批量关闭、批量打印、按商品处理、收货工作台、导出字段配置 |
| 库存 | 第 2.7 节，约 p91-p115 | 盘点 Excel、复制/合并思路、操作历史、出入库查询效率 |
| 配送 | 第 2.8 节，约 p116-p118 | 线路订单视角、线路客户视角、按订单/客户打印、打印状态 |
| 客户 | 第 2.9 节，约 p119-p129 | 客户档案与订单历史/常用商品/协议价等业务上下文集中展示 |
| 首页/提醒 | 第 2.3 节、消息配置约 p9-p11 / p37-p38 | 待办入口、订单提醒、角色/人员通知 |
| 操作日志 | 约 p39-p41 | 操作人、模块、主体、IP、时间、变更内容、变更前后 |

### 2.1 只借鉴交互，不复制业务模型

下列蔬东坡设计**不得直接照搬**：

- 商品辅助单位自动改变库存记账单位；
- 大量业务逻辑全部参数化开关；
- 直接把商城规则塞入商品主档；
- 用一套超长系统配置页承载所有领域规则；
- 用参考系统状态机替换当前订单、采购、库存、配送状态机。

`xsy-scm` 现有架构、`AGENTS.md`、`docs/decisions.md`、已应用 Flyway 和当前测试是实施时的最高技术约束。

### 2.2 三张补充流程图的有效信息

三张图的价值主要不是增加新模块，而是把**现有订单、采购、库存、配送之间的衔接**画得更清楚。本文只提取已经能映射到当前项目的部分。

| 流程图 | 关键流程 | 当前 xsy-scm 对应能力 | 本计划处理方式 |
| --- | --- | --- | --- |
| 图 1：订单→库存判断→采购→入库→配送 | 客户下单后先汇总，再区分 0 库存 / 库存不足 / 库存充足；缺货进入采购，够货直接进入后续履约 | 销售订单、采购需求、采购单、收货、库存余额/预留/出库、Delivery L0-L2 都已存在 | **新增“订单汇总 + 库存缺口预览”能力**，但不提前实现分拣或 L3 发车 |
| 图 2：系统业务操作流程 | 录单、订单汇总、采购、打印、出库、盘点是连续操作链；其中“分拣/投框、后定价、结算”是独立阶段 | 录单/导入、采购、库存出库、盘点、配送打印已存在；分拣、后定价结算未实现 | 只优化已有阶段；未实现阶段继续排除 |
| 图 3：生鲜配送企业角色流程 | 运营→采购→仓库→配送之间是岗位交接，存在明显的“待处理任务” | SmartAdmin 权限、消息、采购、库存、配送已有基础 | Wave 4 增加**按权限显示的业务待办**，不把图中的岗位名称或凌晨时间硬编码进系统 |

从三张图可以得到一个新的高价值结论：

> 当前系统“每个领域的功能”已经很多，但**订单汇总 → 库存判断 → 缺口采购**这一段仍缺少一个让业务人员一眼看清的工作台。

这也是三张图对当前项目最直接的增量价值。

### 2.3 当前真实主链与流程图的差异

当前代码里的采购主链实际上是：

```text
已确认销售订单
  ↓
POST /scm/purchase/demand/generate
  ↓
按“销售订单行”生成 purchase_demand
  ↓
POST /scm/purchase/demand/allocate
  ↓
分配到已存在的采购单行
  ↓
采购单 → 收货单 → 入库 → inventory_balance / inventory_movement
```

其中 `PurchaseDemandService.generate()` 当前明确是：

```text
已确认订单行 → 采购需求
```

**不会先用库存抵扣需求量。** 前端 `purchase-demand-api.ts` / `purchase-demand-list.vue` 也明确保留了“去掉库存抵扣 / 不做库存汇总预览”的历史裁决。

另一方面，系统已经另外具备：

```text
销售订单 → 显式预留库存 → 出库单 → SALES_OUT 流水
```

以及：

```text
Delivery L0-L2 → 候选订单 → 线路组单 → 停靠点排序 → PLANNED → 打印 / 取消
```

但 Delivery L0-L2 当前**故意不扣库存、不创建出库单、不确认发车**。因此不能为了匹配流程图，把配送规划和库存出库直接绑死。

#### 本计划新增的衔接原则

```text
销售订单事实
  ↓
订单汇总 / 库存缺口预览（只读）
  ├─ 可用库存足够 → 标识“当前无需新增采购”
  └─ 可用库存不足 → 标识缺口并进入采购工作台
                        ↓
                  仍复用现有采购需求 / 采购单 / 收货
```

第一阶段只做**只读预览与业务辅助决策**，不直接改变现有 `purchase_demand` 数量语义。这样既吸收流程图优点，也不会悄悄改掉已验证的 W5 状态机。

#### 明确不从流程图直接带入的能力

以下内容在图中出现，但本文件仍不实施：

- 分拣 / 投框 / 称重任务；
- 后定价 / 收货结算；
- 司机 App / GPS；
- “无库存商品虚拟入库”——当前库存必须通过受控库存命令产生 append-only 流水，禁止虚构余额；
- 固定“晚上 11 点、凌晨 0-3 点”等时间规则——流程图中的时间表示业务节奏，不是系统定时任务需求。

---

## 3. 当前基线（AI 开工前必须重新核对）

截至本文基线：

```text
main HEAD = 45412fb946b79fec7779f5a8d63190119567d014
Flyway max = V43
```

已完成且本计划不能重做的关键能力：

- W0 SmartAdmin 系统底座；
- W1 商品 SPU / SKU / 三级分类 / 图片；
- W2 客户 / 供应商；
- W3 客户协议价 / 客户类型价 / 价格历史；
- W4 销售订单 / 退货退款 / 订单日志 / Excel 导入；
- W5 采购需求 / 采购单 / 多次收货；
- W6 库存余额 / append-only 流水 / 出库 / 预留 / 盘点 / 报损报溢 / 调拨 / 阈值预警 / 规格转换 / 移动加权成本；
- B7 数据大屏；
- PCO-1 商品主档增强；
- M0 / M1 地理数据和真实中国地图；
- Delivery L0-L2：司机 / 车辆 / 线路 / 候选订单 / 组单 / 停靠点 / 规划 / 取消 / 打印。

### 3.1 Flyway 选号规则

本文后续写出的 `V44+` **只是基于当前基线的建议编号**。

任何 AI 真正开工前必须先执行：

```bash
git fetch
# 确认 origin/main 真值
# 确认本地 HEAD 与 origin/main 的祖先关系
# 扫描 db/migration 当前最大版本
```

如果当时最大版本已经不是 V43：

> **整体顺延本文建议版本号，绝对不能修改、重命名或复用已经应用的迁移。**

---

# 4. 总体实施顺序

建议按以下顺序实施：

```text
Wave 1   商品 PCO-2：Excel + 图片中心
   ↓
Wave 2A  订单汇总 / 库存缺口采购建议（只读预览优先）
   ↓
Wave 2B  采购操作效率：批量处理 + 导出/打印
   ↓
Wave 3   订单录单效率：草稿恢复 + 历史复用 + 最近成交价
   ↓
Wave 4   业务待办与站内提醒
   ↓
Wave 5   配送线路操作体验：订单/客户双视角 + 打印状态
   ↓
Wave 6   盘点效率：Excel + 复制历史盘点
   ↓
Wave 7   客户 360° 业务上下文
   ↓
Wave 8   SCM 审计与表格体验统一
```

顺序原因：

- Wave 1 是当前已有 PCO 规划中的明确未完成项；
- **Wave 2A 是三张流程图补充后新增的流程级优化**，先把“订单汇总 → 库存判断 → 缺口”看清楚，再提升采购执行效率；
- Wave 2B / 3 直接改善采购、录单两个高频岗位；
- Wave 4 把当前“只能自己回页面看状态”的缺陷收口，并承接流程图里的跨岗位交接；
- Wave 5 只增强已经上线的 Delivery L0-L2，不进入 L3；
- Wave 6 只优化现有盘点，不改库存账；
- Wave 7 / 8 属信息整合与体验统一，风险最低但收益偏长期。

---

# 5. Wave 1 — 商品 PCO-2：Excel 导入导出 + 图片中心

## 5.1 参考点

蔬东坡商品档案支持：

- 手动新增；
- Excel 导入；
- Excel 批量修改；
- 商品图片批量导入；
- 主图替换；
- 详情图按顺序维护。

当前 `xsy-scm` 的 PCO-1 已完成主档、标签、UOM、高级筛选和批量维护，因此此波次只补**运营维护效率**。

## 5.2 当前代码位置

### 后端

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/
├─ controller/ProductController.java
├─ service/ProductSpuService.java
├─ service/ProductQueryService.java
├─ service/ProductBatchService.java
├─ manager/ProductImageSyncManager.java
├─ domain/entity/ProductImageEntity.java
└─ ...
```

对象存储继续复用：

```text
xsy-scm-server/sa-base/.../file/
FileService
```

### 前端

```text
xsy-scm-web/src/views/business/scm/product/
├─ product-list.vue
├─ product-detail.vue
├─ components/product-form-drawer.vue
├─ components/product-image-upload.vue
└─ ...

xsy-scm-web/src/api/business/scm/product-api.ts
```

### 现有 API

```http
POST /scm/product/query
GET  /scm/product/detail/{spuId}
POST /scm/product/add
POST /scm/product/update
POST /scm/product/updateStatus
POST /scm/product/delete
POST /scm/product/batch/updateStatus
POST /scm/product/batch/updateCategory
POST /scm/product/batch/updateTags
```

## 5.3 本波次新增页面

```text
/business/scm/product/image-center.vue
```

商品列表继续保留：

```text
导入
导出
图片中心
```

三个入口。

## 5.4 建议新增 API

### Excel

```http
GET  /scm/product/import/template
POST /scm/product/import
POST /scm/product/export
```

要求：

- 模板版本必须可校验；
- 表头必须严格校验；
- 所有行先校验，0 错误才开始写；
- 任意写失败整批回滚；
- 错误必须定位到 Excel 行号 + 字段 + 原因；
- 不允许“部分成功”；
- 重复文件提交要有防重复机制；
- 导出字段至少覆盖当前列表可见主档字段、SKU、标签、单位。

### 图片中心

```http
POST /scm/product/image/query
POST /scm/product/image/batch-bind
POST /scm/product/image/batch-remove
POST /scm/product/image/set-primary
POST /scm/product/image/reorder
```

建议支持：

- 无图商品筛选；
- 按 SPU / 名称 / 助记码筛选；
- 多文件上传后按文件名匹配商品；
- 匹配结果必须先预览；
- 未匹配、重复匹配、歧义匹配必须显性报错；
- 自动匹配不能直接落库；
- 每个 SPU 最多一张主图；
- 继续要求图片绑定到 `public/image/`；
- 展示 URL 继续由 `file_key` 动态计算，不恢复 `file_url`。

## 5.5 是否需要新表

**不强制新增业务表。**

继续复用：

```text
product_spu
product_sku
product_image
product_tag
product_tag_relation
scm_uom
```

建议给现有 `product_image` 增加：

```text
image_type
```

取值第一版控制在：

```text
PRIMARY
DETAIL
```

不要第一版就加视频、营销图、证书图等类型。

### 可选表：本波次不默认创建

```text
product_import_batch
```

第一版如果错误结果只要求本次即时查看 / 下载，不需要长期追溯，则**不要建表**。

只有产品明确要求“查看历史导入批次”时再新增。

## 5.6 Flyway

当前基线下建议：

```text
V44__scm_product_pco2.sql
  - product_image.image_type
  - CHECK / index（如需要）

V45__scm_product_pco2_permissions.sql
  - scm:product:import
  - scm:product:export
  - scm:product:image:query
  - scm:product:image:batch
  - 图片中心菜单
```

> 开工时若最大版本已变化，整体顺延。

## 5.7 权限

建议新增：

```text
scm:product:import
scm:product:export
scm:product:image:query
scm:product:image:batch
```

原有：

```text
scm:product:image
```

不要删除，单商品编辑仍使用原权限。

## 5.8 验收标准

### 后端

- [ ] 模板是真实 xlsx，不是 JSON 错误伪装文件；
- [ ] 错一行时 0 行写入；
- [ ] SKU 编码 / SPU 编码重复可准确定位；
- [ ] 分类、单位、标签不存在时拒绝；
- [ ] 停用单位不能用于新商品；
- [ ] 已存在业务引用商品的关键历史字段不能被导入绕过保护；
- [ ] 商品图片只能绑定合法 `public/image/` fileKey；
- [ ] 每个 SPU 最多一张 PRIMARY；
- [ ] 并发设置主图不能产生两张 PRIMARY；
- [ ] 所有写接口有权限校验和操作日志。

### 前端

- [ ] 商品列表有导入/导出入口；
- [ ] 导入过程有 loading 且禁止重复提交；
- [ ] 错误一次性完整展示，可下载失败明细；
- [ ] 图片中心能筛选无图商品；
- [ ] 批量匹配先预览再确认；
- [ ] 未匹配文件不会静默丢弃；
- [ ] 主图切换刷新后保持正确；
- [ ] 无权限账号看不到批量入口。

### 测试

至少增加：

```text
ProductImportIT
ProductImageCenterIT
product-import-model.test.mjs
scm-product.spec.ts 扩展 PCO-2 场景
```

---

# 6A. Wave 2A — 订单汇总 / 库存缺口采购建议（只读预览优先）

## 6A.1 为什么三张流程图让这一项进入当前优化范围

流程图 1 和流程图 2 都把以下动作放在采购之前：

```text
订单汇总
→ 对比库存
→ 识别 0 库存 / 库存不足 / 库存充足
→ 得出待采购缺口
```

这不是新建一个“智能采购模块”，而是补当前已经存在的：

```text
销售订单 + 采购需求 + 库存余额
```

之间的只读决策视图。

当前 W5 的 `purchase_demand.generate` 是按销售订单行生成需求，且历史设计**刻意没有做库存抵扣**。所以本波次第一阶段不能直接修改 `required_quantity` 算法，而应先新增汇总预览。

## 6A.2 当前代码位置

### 采购需求

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/
├─ controller/PurchaseDemandController.java
├─ service/PurchaseDemandService.java
├─ service/PurchaseQueryService.java
├─ domain/form/PurchaseDemandGenerateForm.java
└─ ...

xsy-scm-web/src/views/business/scm/purchase/
├─ purchase-demand-list.vue
└─ components/purchase-demand-generate-modal.vue

xsy-scm-web/src/api/business/scm/purchase-demand-api.ts
```

### 库存

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/inventory/
├─ controller/InventoryBalanceController.java
├─ service/InventoryBalanceQueryService.java
└─ domain/vo/InventoryBalanceVO.java

xsy-scm-web/src/api/business/scm/inventory-balance-api.ts
```

现有库存余额已经明确提供：

```text
quantity
reservedQuantity
availableQuantity = quantity - reservedQuantity
```

采购缺口预览应优先使用 `availableQuantity`，不能用前端自行做浮点运算。

## 6A.3 现有 API

```http
POST /scm/purchase/demand/query
POST /scm/purchase/demand/generate
POST /scm/purchase/demand/allocate

POST /scm/inventory/balance/query
GET  /scm/inventory/balance/detail/{id}
```

## 6A.4 第一阶段建议新增 API

```http
POST /scm/purchase/demand/summary-preview
```

权限第一阶段直接复用：

```text
scm:purchase:demand:query
```

避免为了一个只读工作台再造权限碎片。

### 请求建议

继续沿用 `PurchaseDemandGenerateForm` 的窗口语义：

```text
startAt
endAt
warehouseId
```

并允许筛选：

```text
supplierId?
purchaserId?
categoryId?
keyword?
```

其中 supplier / purchaser / category 第一阶段只负责过滤和辅助决策，不负责自动决定采购关系。

### 返回行建议

按：

```text
warehouseId + skuId + demandUnit
```

聚合，至少返回：

```text
skuId
skuCode
productName
skuName
categoryName
demandUnit
sourceOrderCount
sourceLineCount
orderDemandQuantity
onHandQuantity
reservedQuantity
availableQuantity
openPurchaseQuantity        // 第一阶段可展示，但不默认参与缺口扣减
shortageAgainstAvailable
calculationStatus
```

其中：

```text
shortageAgainstAvailable = max(orderDemandQuantity - availableQuantity, 0)
```

必须在后端使用 `BigDecimal` 计算并以四位定点字符串输出。

## 6A.5 单位规则是硬门禁

流程图里的“订单数量 - 库存量”看起来简单，但当前项目存在 Q13：

> 一个 `(warehouse_id, sku_id)` 只有一个库存记账单位。

因此只有在：

```text
demandUnit == inventoryBalance.unit
```

时才能直接做数量相减。

如果单位不一致：

```text
calculationStatus = UNIT_MISMATCH
shortageAgainstAvailable = null
```

页面明确提示“单位不一致，不能直接计算缺口”，**禁止自动换算、禁止猜折算率**。

## 6A.6 在途采购必须先展示、后裁决

流程图 2 提到：

```text
待采购量 = 订单汇总 - 库存 - 在途库存
```

但当前项目对“在途是否抵扣采购缺口”尚不能从现有代码直接推导出唯一规则，而且当前调拨在途库存也没有进入 `inventory_balance`。

因此第一阶段：

```text
openPurchaseQuantity
```

只能作为独立列展示，**不默认进入 `shortageAgainstAvailable` 公式**。

第二阶段若业务确认，需要单独裁决：

1. 哪些采购状态算“在途”；
2. 已创建未提交是否算；
3. 已部分收货如何计算剩余量；
4. 调拨在途是否参与采购建议；
5. 正式生成采购需求时是否再次锁定 / 重算；
6. 预览与正式生成之间库存变化如何提示。

没有这些裁决，AI 不得自行把 `openPurchaseQuantity` 扣掉。

## 6A.7 页面设计

在现有：

```text
purchase-demand-list.vue
```

增加：

```text
[采购需求] [订单汇总 / 缺口预览]
```

两个 Tab，或新增同目录页面：

```text
purchase-demand-summary.vue
```

第一版推荐独立页面，避免现有需求列表越来越复杂。

表格建议：

```text
商品 / 规格 / 分类
订单数 / 订单需求量
现有量 / 已预留 / 可用量
在途采购量（信息列）
当前缺口
状态
操作
```

状态：

```text
STOCK_ENOUGH      库存足够
SHORTAGE          库存不足
ZERO_STOCK        当前可用量为 0
UNIT_MISMATCH     单位不可直接比较
NO_BALANCE        尚无库存余额行
```

`NO_BALANCE` 的库存量按 0 展示，但不能为此创建 `inventory_balance` 行。

## 6A.8 与现有采购需求的关系

第一阶段禁止：

```text
直接改 PurchaseDemandService.generate()
直接把 purchase_demand.required_quantity 改成 shortage 数量
自动删除“库存足够”的来源需求
自动按供应商生成采购单
```

页面可以提供：

```text
查看来源订单
查看库存余额
跳转采购需求
跳转采购单
```

但所有写操作仍走当前 W5 接口。

## 6A.9 第二阶段：正式采购建议生成（业务裁决后才开工）

如果负责人确认库存抵扣口径，再新增独立命令，而不是偷偷改变旧接口：

```http
POST /scm/purchase/demand/generate-by-shortage
```

旧的：

```http
POST /scm/purchase/demand/generate
```

继续保持原语义，以免已有调用方和历史数据失去解释。

正式命令必须：

- 重新读取并校验库存；
- 明确是否以 `availableQuantity` 为基准；
- 明确是否扣开放采购量；
- 保持幂等；
- 保留来源销售订单行映射，不能只留下一个无法追溯的 SKU 汇总数字；
- 不创建第二套销售订单或库存事实。

## 6A.10 是否需要新表

**第一阶段不需要。**

只读预览直接查询：

```text
sales_order / sales_order_item
inventory_balance
purchase_order / purchase_order_item / purchase_receipt
product_spu / product_sku / product_category
```

不要创建：

```text
purchase_summary_snapshot
purchase_suggestion_cache
stock_compare_result
```

之类事实副本。

如果以后为了性能需要缓存，必须先有真实慢查询证据，再独立设计。

## 6A.11 Flyway

第一阶段：

```text
0 migration
```

理由：

- 只读聚合；
- 复用 `scm:purchase:demand:query`；
- 不加表、不加列、不加状态。

## 6A.12 验收标准

- [ ] 同一 SKU 多张已确认订单可以正确聚合；
- [ ] 不同仓库绝不能混算；
- [ ] `availableQuantity = quantity - reservedQuantity`，页面不得自行重算口径；
- [ ] 0 库存余额行显示 0，但不产生任何库存写入；
- [ ] 单位不一致时不返回伪造缺口数量；
- [ ] 库存足够时显示缺口 0；
- [ ] 库存不足时缺口按四位定点计算；
- [ ] `openPurchaseQuantity` 与缺口公式保持解耦，除非后续裁决；
- [ ] 预览接口零业务写入、零库存流水、零采购需求写入；
- [ ] 预览结果中的来源订单数 / 行数可追溯；
- [ ] 原 `/demand/generate` 语义和现有 W5 测试完全不变；
- [ ] 大数据量查询不得出现逐 SKU N+1；自定义聚合 SQL 放 Mapper XML。

---

# 6. Wave 2B — 采购操作效率：批量处理 + 导出/打印

## 6.1 参考点

蔬东坡采购单强调：

- 合并 / 批量关闭；
- 批量打印；
- 一键收货；
- 切换“按商品”处理；
- 批量修改收货数量 / 单价 / 金额；
- 导出字段按使用场景自定义并记忆。

本项目已经有采购和多次收货，不需要重做采购状态机。

## 6.2 当前代码位置

### 后端

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/
├─ controller/PurchaseOrderController.java
├─ controller/PurchaseReceiptController.java
├─ service/PurchaseOrderService.java
├─ service/PurchaseReceiptService.java
├─ service/PurchaseQueryService.java
└─ ...
```

### 前端

```text
xsy-scm-web/src/views/business/scm/purchase/
├─ purchase-order-list.vue
├─ purchase-receipt-list.vue
├─ purchase-log-list.vue
├─ components/purchase-order-form-drawer.vue
├─ components/purchase-order-detail-drawer.vue
└─ ...

xsy-scm-web/src/api/business/scm/purchase-order-api.ts
xsy-scm-web/src/api/business/scm/purchase-receipt-api.ts
```

### 现有采购单 API

```http
POST /scm/purchase/query
GET  /scm/purchase/detail/{id}
GET  /scm/purchase/item/{orderId}
GET  /scm/purchase/log/{orderId}
POST /scm/purchase/create
POST /scm/purchase/update
POST /scm/purchase/submit
POST /scm/purchase/cancel
POST /scm/purchase/short-close
POST /scm/purchase/delete
POST /scm/purchase/batch-delete
```

### 现有收货 API

```http
POST /scm/purchase/receipt/query
GET  /scm/purchase/receipt/detail/{id}
GET  /scm/purchase/receipt/item/{receiptId}
POST /scm/purchase/receipt/create
POST /scm/purchase/receipt/update
POST /scm/purchase/receipt/confirm
POST /scm/purchase/receipt/putaway
POST /scm/purchase/receipt/delete
POST /scm/purchase/receipt/batch-delete
```

## 6.3 本波次页面优化

采购单列表增加：

```text
批量短关
批量打印
导出
导出设置
```

采购收货增加“按单据 / 按商品”双视角：

```text
按单据：维持当前收货单模型
按商品：跨待收货单查看 SKU 维度的计划量 / 已收 / 未收 / 本次收货
```

**按商品页面只是工作台视图，不新增第二套收货事实。**

## 6.4 建议新增 API

```http
POST /scm/purchase/batch/short-close
POST /scm/purchase/export
GET  /scm/purchase/{id}/print
POST /scm/purchase/receipt/item-workbench/query
```

第一版**不要直接增加“巨型批量确认收货事务”**。

原因：

- 收货确认会写库存；
- 还可能涉及 DIRECT / WAREHOUSE_CONFIRM；
- 需要严格保持现有幂等、单位、库存、移动加权成本规则。

建议“按商品工作台”只负责编辑 / 汇总，最终确认仍按现有收货单调用：

```http
POST /scm/purchase/receipt/confirm
```

如果后续确实需要真正批量确认，另开独立设计并明确失败语义：

```text
整批原子
or
逐单提交 + 每单独立结果
```

本波次不擅自决定。

## 6.5 导出字段配置

第一版建议前端本地记忆：

```text
localStorage key = 登录用户 + 导出场景
```

场景至少区分：

```text
采购单列表导出
采购单详情导出
```

不用为字段勾选偏好新建业务表。

## 6.6 是否需要新表

**不需要。**

工作台查询直接读：

```text
purchase_order
purchase_order_item
purchase_receipt
purchase_receipt_item
```

不要建立：

```text
purchase_item_workbench_snapshot
```

之类副本表。

## 6.7 Flyway

如果仅复用已有权限，可以 0 schema migration。

如果新增独立按钮权限，当前基线建议：

```text
V46__scm_purchase_efficiency_permissions.sql
```

可新增：

```text
scm:purchase:batch-short-close
scm:purchase:export
scm:purchase:print
```

## 6.8 验收标准

- [ ] 批量短关仅允许合法状态；
- [ ] 批量短关必须做全量预校验，不允许中间部分成功；
- [ ] 被其他人修改过的 version 必须显式冲突；
- [ ] 打印 / 导出绝不能改变采购状态；
- [ ] 按商品工作台汇总量与底层收货单逐行求和一致；
- [ ] 非标品实重规则不能被工作台绕过；
- [ ] DIRECT 与 WAREHOUSE_CONFIRM 行为保持现有逻辑；
- [ ] 导出设置刷新页面后仍保留；
- [ ] 无权限角色不显示批量操作；
- [ ] 原 W5 / Inventory E2E 全部回归不退化。

---

# 7. Wave 3 — 订单录单效率：草稿恢复 + 历史复用 + 最近成交价

## 7.1 参考点

蔬东坡订单录入有两个非常适合当前系统的设计：

1. 离开新增页时自动保存本地草稿，再次新增时提示恢复；
2. 单价旁显示该客户该商品最近 5 次非关闭订单成交价。

## 7.2 当前代码位置

### 后端

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/
├─ controller/SalesOrderController.java
├─ service/SalesOrderService.java
├─ service/SalesOrderQueryService.java
└─ ...
```

### 前端

```text
xsy-scm-web/src/views/business/scm/order/
├─ order-list.vue
├─ order-detail.vue
├─ components/order-form-drawer.vue
├─ components/order-import-modal.vue
├─ order-form-model.*
└─ ...

xsy-scm-web/src/api/business/scm/order-api.ts
```

### 现有 API

```http
POST /scm/order/query
GET  /scm/order/detail/{orderId}
POST /scm/order/log/query
POST /scm/order/price/preview
POST /scm/order/create
POST /scm/order/create-and-progress
POST /scm/order/update
POST /scm/order/submit
POST /scm/order/confirm
POST /scm/order/cancel
POST /scm/order/item/actual-quantity
POST /scm/order/delete
POST /scm/order/batch-delete
POST /scm/order/reserve-stock/{orderId}
```

## 7.3 草稿恢复

只保存**未提交的新建表单**，第一版使用浏览器本地存储，不入库。

建议 key：

```text
xsy-scm:order-draft:{loginUserId}
```

草稿内容：

```text
客户
发货/需求日期
备注
订单来源
订单行 SKU / 数量 / 人工单价 / 改价原因
```

不要保存：

```text
权限结果
PriceResolver 返回的最终价格
库存可用量
后端 version
服务端计算字段
```

打开“新增订单”时：

```text
检测草稿
→ 弹窗：恢复 / 丢弃
→ 恢复后重新请求客户、SKU、价格可用性
```

创建成功后必须清除草稿。

## 7.4 历史订单复用

不增加后端“复制订单”命令。

前端从：

```http
GET /scm/order/detail/{orderId}
```

读取历史单，只复制允许字段，形成新的“新增订单草稿”。

必须重新解析：

- 客户当前状态；
- SKU 当前是否可选；
- 当前价格；
- 商品当前主档 / 上下架；
- 当前权限。

绝不能把历史订单的：

```text
orderId
orderNo
version
状态
历史 lockedUnitPrice
历史库存预留
退款/退货
日志
```

直接复制为新单事实。

## 7.5 最近成交价

建议新增只读 API：

```http
GET /scm/order/reference/recent-prices
    ?customerId={id}
    &skuId={id}
    &limit=5
```

返回：

```text
订单号
下单时间
订单来源
下单数量
成交单价
价格来源（如有）
```

过滤建议：

- 排除 CANCELLED；
- limit 最大 10；
- 按订单创建时间倒序；
- 必须按客户 + SKU；
- 仅查询历史快照，不回算当前价格。

单价输入框右侧增加：

```text
“最近成交价”图标 / Popover
```

## 7.6 是否需要新表

**不需要。**

查询现有：

```text
sales_order
sales_order_item
```

草稿使用浏览器本地存储。

## 7.7 Flyway

**不需要。**

如果未来产品要求“多设备共享草稿”，才考虑服务端草稿表；本波次禁止提前建。

## 7.8 验收标准

- [ ] 新建订单填一半关闭 Drawer，再打开可恢复；
- [ ] 不同登录用户草稿互不串；
- [ ] 创建成功自动删除草稿；
- [ ] 手动丢弃后不再恢复；
- [ ] 从历史订单复制不会复制旧状态和 version；
- [ ] 历史商品已停用时明确提示，不静默删除行；
- [ ] 最近成交价恰好只查当前客户 + 当前 SKU；
- [ ] CANCELLED 不进入最近成交价；
- [ ] 最近成交价只用于参考，不改变 PriceResolver；
- [ ] 人工改价仍要求原有权限与改价原因；
- [ ] Excel 导入与普通录单现有 E2E 全绿。

---

# 8. Wave 4 — 业务待办与站内提醒

## 8.1 参考点

蔬东坡首页把待办和订单提醒放在高频入口，并支持按角色/操作员通知。

当前项目已经有 SmartAdmin 原生消息系统，因此**不要新建第二套消息中心**。

## 8.2 当前可复用代码

### 后端原生消息

```text
xsy-scm-server/sa-base/src/main/java/net/lab1024/sa/base/module/support/message/
├─ controller/MessageController.java
├─ service/MessageService.java
├─ constant/MessageTemplateEnum.java
└─ ...
```

现有 `MessageService` 已支持：

```text
sendMessage
sendTemplateMessage
未读数
标已读
```

### 前端原生消息

```text
xsy-scm-web/src/views/support/message/message-list.vue
xsy-scm-web/src/api/support/message-api.ts
```

现有接口：

```http
POST /support/message/queryMyMessage
GET  /support/message/getUnreadCount
GET  /support/message/read/{messageId}
```

## 8.3 本波次分两类

### A. 待办聚合（Pull）

建议新增：

```http
GET /scm/dashboard/todo
```

第一版只统计当前已经存在的待处理项：

```text
库存异常数量
待仓库确认入库数量
待审批报损报溢数量
草稿/未规划配送线路数量
```

返回只读数字 + 跳转 route，不复制业务数据。

前端可放：

```text
现有首页 / 数据大屏头部右侧 / 独立“业务待办”卡片
```

不得把待办数据落一张快照表。

### B. 事件消息（Push）

第一版只做**明确发生时点**的事件，不做阈值轮询消息。

优先事件：

```text
报损报溢被驳回 → 通知制单人
```

可以追加：

```text
WAREHOUSE_CONFIRM 收货确认后 → 通知有入库权限的目标人员
```

但接收人规则如果当前没有明确负责人，先只做制单人 / 明确用户，不猜角色。

库存阈值预警仍然通过 `/scm/dashboard/todo` 展示，不在本波次引入定时扫描、去重事件表或复杂调度。

## 8.4 建议新增代码位置

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/dashboard/
├─ controller/ScmTodoController.java
├─ service/ScmTodoQueryService.java
└─ domain/vo/ScmTodoVO.java
```

消息调用继续依赖：

```text
sa-base MessageService
```

不要复制 MessageEntity / MessageDao 到 scm 包。

## 8.5 是否需要新表

**不需要。**

继续复用 SmartAdmin 原生消息表。

待办实时聚合现有业务表。

## 8.6 Flyway

如果新增专用待办权限：

```text
V47__scm_todo_permission.sql
```

建议：

```text
scm:todo:query
```

第一版只授 SUPER_ADMIN，后续正式业务角色确认后再拆。

## 8.7 验收标准

- [ ] 待办数字与业务列表查询条件一致；
- [ ] 点击待办能直接跳转到正确列表并带筛选条件；
- [ ] 待办接口不写任何业务表；
- [ ] 报损报溢驳回后制单人收到一条消息；
- [ ] 重复读取消息不会产生第二条消息；
- [ ] 消息点击可进入目标业务单据或列表；
- [ ] 无 `scm:todo:query` 权限账号不能访问待办接口；
- [ ] 不新增 Kafka / MQ / 事件总线；
- [ ] 不新增独立 SCM 消息表。

---

# 9. Wave 5 — 配送线路操作体验：订单/客户双视角 + 打印状态

## 9.1 参考点

蔬东坡线路支持：

- 线路订单列表；
- 线路客户列表；
- 按订单打印；
- 按客户打印；
- 已打印 / 未打印筛选。

这与当前 Delivery L0-L2 高度匹配，而且不需要进入司机端 / GPS L3。

## 9.2 当前代码位置

### 后端

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/delivery/
├─ controller/DeliveryRouteController.java
├─ service/DeliveryRouteService.java
├─ service/DeliveryRouteQueryService.java
├─ service/DeliveryCandidateOrderQueryService.java
└─ ...
```

### 前端

```text
xsy-scm-web/src/views/business/scm/delivery/
├─ route-list.vue
├─ route-detail.vue
├─ route-print.vue
├─ driver-list.vue
├─ vehicle-list.vue
└─ ...

xsy-scm-web/src/api/business/scm/delivery-api.ts
```

### 现有 API

```http
GET    /scm/delivery/routes
GET    /scm/delivery/routes/{id}
GET    /scm/delivery/routes/{id}/map
GET    /scm/delivery/routes/{id}/print
POST   /scm/delivery/routes
PUT    /scm/delivery/routes/{id}
POST   /scm/delivery/routes/{id}/plan
POST   /scm/delivery/routes/{id}/cancel
GET    /scm/delivery/candidate-orders
POST   /scm/delivery/routes/{id}/orders
DELETE /scm/delivery/routes/{id}/orders/{orderId}
PUT    /scm/delivery/routes/{id}/stops/reorder
PUT    /scm/delivery/routes/{id}/stops/{stopId}
```

## 9.3 页面设计

`route-detail.vue` 增加 Tab：

```text
停靠点
按订单
按客户
地图
```

### 按订单

字段：

```text
订单号
客户
地址
配送顺序
商品数
金额
打印次数
最后打印时间
```

### 按客户

按：

```text
customer_id + 当前线路
```

聚合：

```text
订单数
商品行数
金额
打印状态
```

## 9.4 建议新增 API

```http
GET  /scm/delivery/routes/{id}/orders-view
GET  /scm/delivery/routes/{id}/customers-view
POST /scm/delivery/routes/{id}/print/orders
POST /scm/delivery/routes/{id}/print/customers
```

请求支持：

```text
ALL
PRINTED
UNPRINTED
```

### 打印语义必须写清楚

系统只能可靠记录：

> **“生成过打印预览 / 打印任务”**

不能宣称浏览器 / 打印机一定物理出纸成功。

因此字段文案建议：

```text
打印次数
最后生成打印时间
```

不要写：

```text
打印成功次数
```

## 9.5 是否需要新表

**不需要新表。**

建议扩展现有：

```text
delivery_route_order
```

增加：

```text
print_count
last_printed_at
last_printed_by
```

客户打印状态可由该客户在线路内订单的字段聚合得出，不建 `delivery_route_customer` 副本表。

## 9.6 Flyway

当前基线建议：

```text
V48__scm_delivery_print_tracking.sql
```

现有权限：

```text
scm:delivery:route:print
```

可以继续复用，不需要新增打印权限。

## 9.7 验收标准

- [ ] 按订单视角总订单数与线路订单关系表一致；
- [ ] 按客户聚合的订单数/金额与按订单求和一致；
- [ ] 生成订单打印后对应订单 `print_count + 1`；
- [ ] 客户打印会更新本次包含的订单打印记录；
- [ ] PRINTED / UNPRINTED 筛选正确；
- [ ] 生成打印绝不扣库存；
- [ ] 生成打印绝不生成出库单；
- [ ] 生成打印绝不推进订单状态；
- [ ] PLANNED 后既有写锁规则不被破坏；
- [ ] 取消线路后订单仍按现有逻辑释放；
- [ ] 当前 Delivery L0-L2 验收用例全部继续通过。

---

# 10. Wave 6 — 盘点效率：Excel + 复制历史盘点

## 10.1 参考点

蔬东坡盘点支持：

- 按仓库导出 / 导入 Excel；
- 历史盘点复制；
- 盘点操作历史；
- 待审核盘点的进一步批量处理。

当前项目已经有严格盘点状态机和库存差异规则，本波次不能改变库存语义。

## 10.2 当前代码位置

### 后端

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/inventory/
├─ controller/InventoryStocktakeController.java
├─ service/InventoryStocktakeService.java
├─ service/InventoryStocktakeQueryService.java
└─ ...
```

### 前端

```text
xsy-scm-web/src/views/business/scm/inventory/inventory-stocktake-list.vue
xsy-scm-web/src/api/business/scm/inventory-stocktake-api.ts
```

### 现有 API

```http
POST /scm/inventory/stocktake/query
GET  /scm/inventory/stocktake/detail/{id}
POST /scm/inventory/stocktake/create
POST /scm/inventory/stocktake/update/{id}
POST /scm/inventory/stocktake/confirm/{id}
POST /scm/inventory/stocktake/cancel/{id}
POST /scm/inventory/stocktake/delete/{id}
```

## 10.3 建议新增 API

```http
GET  /scm/inventory/stocktake/import/template?warehouseId={id}
POST /scm/inventory/stocktake/import
POST /scm/inventory/stocktake/copy/{id}
```

### Excel 模板

建议导出当前仓库参与盘点的：

```text
SKU 编码
商品名称
规格
记账单位
账面数量快照
实盘数量（空）
备注（空）
```

导入后创建**新的 DRAFT 盘点单**。

### 复制历史盘点

复制：

```text
仓库
SKU 集合
单位
```

不复制：

```text
历史账面量
历史实盘量
历史差异
历史状态
历史 version
```

新盘点单必须重新按当前余额快照账面量。

## 10.4 不做“直接覆盖库存”

无论 Excel 还是复制历史：

```text
只能创建 DRAFT
→ 人员填写/确认
→ confirm 时走现有 InventoryStocktakeService
```

禁止导入 Excel 后直接写：

```text
inventory_balance.quantity
inventory_movement
```

## 10.5 是否需要新表

**不需要。**

继续复用：

```text
inventory_stocktake
inventory_stocktake_item
```

操作记录先复用 `@OperateLog`。

第一版不做蔬东坡的“盘点单合并”。

原因：当前项目的盘点规则明确依赖创建/更新时账面快照，合并两个不同时间点的盘点草稿会重新引入快照口径歧义。

如果后续确实需要合并，必须另写设计，明确：

```text
合并后的账面快照取什么时点
重复 SKU 如何处理
期间入出库如何处理
```

## 10.6 Flyway

数据表不变。

如果新增独立按钮权限，当前基线建议：

```text
V49__scm_stocktake_efficiency_permissions.sql
```

权限：

```text
scm:inventory:stocktake:import
scm:inventory:stocktake:copy
```

## 10.7 验收标准

- [ ] Excel 模板按仓库生成；
- [ ] 导入错误不产生 DRAFT；
- [ ] 重复 SKU 必须报错或按明确规则处理，不能静默合并；
- [ ] 单位不匹配直接拒绝；
- [ ] 复制历史单后账面数量取当前余额快照；
- [ ] 复制历史不带历史实盘量；
- [ ] 导入/复制都不改变库存；
- [ ] 只有 confirm 改库存；
- [ ] confirm 仍保持整单回滚；
- [ ] append-only movement 规则完全不变；
- [ ] 现有盘点 E2E 全绿。

---

# 11. Wave 7 — 客户 360° 业务上下文

## 11.1 参考点

蔬东坡把客户档案周边的：

```text
常用商品
订货历史
协议价
商品屏蔽/可见性
账期
```

集中到客户维度查看。

当前项目已经有这些事实，只是分散在不同模块。

## 11.2 当前代码位置

### 客户

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/
├─ controller/CustomerController.java
├─ service/CustomerQueryService.java
└─ ...

xsy-scm-web/src/views/business/scm/customer/
├─ customer-list.vue
├─ customer-detail.vue
├─ customer-sku-visibility-list.vue
└─ ...
```

当前已经存在隐藏深链详情：

```text
/customer/customer-detail?customerId=...
```

### 价格

```text
xsy-scm-web/src/views/business/scm/pricing/agreement-price-list.vue
xsy-scm-web/src/api/business/scm/pricing-api.ts
```

协议价 API：

```http
POST /scm/pricing/agreement-price/query
```

### 订单

```http
POST /scm/order/query
```

### 商品可见性

现有权限：

```text
scm:customer:visibility:query
scm:customer:visibility:update
```

## 11.3 页面设计

扩展现有 `customer-detail.vue`，不要再建第二个详情页。

建议 Tab：

```text
基础资料
最近订单
常购商品
协议价
可售商品
```

### 基础资料

继续使用当前：

```http
GET /scm/customer/detail/{customerId}
```

### 最近订单

复用：

```http
POST /scm/order/query
```

强制带当前 `customerId`。

### 协议价

复用：

```http
POST /scm/pricing/agreement-price/query
```

### 可售商品

复用现有客户 SKU 可见性查询。

### 常购商品

需要新增只读聚合 API：

```http
GET /scm/customer/{customerId}/frequent-skus?days=90&limit=20
```

建议取：

```text
近 N 天非取消订单
按 SKU 聚合
订单次数
下单数量
最近下单时间
最近成交价
```

注意：数量可能跨不同单位，不要在总计区把不同单位直接相加成一个“总量”。

## 11.4 是否需要新表

**不需要。**

所有内容来自现有事实表。

禁止创建：

```text
customer_360_snapshot
customer_frequent_product
```

作为事实副本。

## 11.5 Flyway

默认 **不需要**。

现有隐藏详情菜单已经存在，查询也可沿用现有客户查询权限。

## 11.6 验收标准

- [ ] 客户详情 URL 刷新可恢复；
- [ ] 5 个 Tab 都以同一个 customerId 为上下文；
- [ ] 最近订单和订单列表按同样口径；
- [ ] 协议价数据与价格中心一致；
- [ ] 可售商品与 `customer_sku_visibility` 事实一致；
- [ ] 常购商品只做聚合，不落副本；
- [ ] 不同单位不错误求和；
- [ ] 无价格权限时不泄露敏感价格字段；
- [ ] 客户 query 权限之外的领域数据仍受各自权限约束。

---

# 12. Wave 8 — SCM 审计与表格体验统一

此波次分两部分：

```text
A. 审计入口统一
B. 表格/查询体验统一
```

不要求一次重做所有页面。

---

## 12.1 A — SCM 审计入口统一

### 当前能力

SmartAdmin 已有：

```text
xsy-scm-web/src/views/support/operate-log/operate-log-list.vue
```

后端 `t_operate_log` 已记录：

```text
操作人
用户类型
module
content
url
method
param
response
ip
userAgent
successFlag
failReason
时间
```

同时：

- 订单有自己的业务操作记录；
- 采购有自己的业务操作记录；
- 商品 / 客户 / 配送写接口大量使用 `@OperateLog`。

### 本波次目标

不是新建第三套日志，而是统一“从业务对象看操作痕迹”。

建议：

```text
订单详情 → 订单日志
采购详情 → 采购日志
商品详情 → 通用操作日志筛选入口
客户详情 → 通用操作日志筛选入口
配送线路详情 → 通用操作日志筛选入口
```

第一版可以使用：

```text
URL + 请求参数中的业务 id
```

做筛选。

如果后续确认需要像蔬东坡一样稳定展示：

```text
业务主体
字段级 before / after
```

再单独设计 `scm_change_log`，不要本波次顺手加一个不完整的通用变更表。

### 是否需要新表

第一版：**不需要。**

### Flyway

第一版：**不需要。**

### 验收标准

- [ ] 商品/客户/配送关键写操作都能从详情页跳到对应操作日志；
- [ ] 人工改价、取消、驳回等必须记录原因；
- [ ] 操作日志不显示密码、Token 等敏感字段；
- [ ] 订单/采购现有专用日志不被替换。

---

## 12.2 B — 表格 / 查询体验统一

### 当前代码

项目已经大量使用：

```text
TableOperator
TABLE_ID_CONST
```

例如：

```text
商品
采购
库存
系统日志
```

均已有列配置能力。

### 本波次目标

统一以下体验：

```text
列显示/隐藏
列顺序
列宽
分页大小
查询条件记忆
高级筛选展开状态
导出字段记忆
```

优先页面：

```text
商品列表
订单列表
采购单列表
收货列表
库存余额
库存流水
盘点
配送线路
客户列表
```

### 原则

- 列显示/顺序优先复用 TableOperator；
- 查询条件记忆第一版放浏览器本地；
- 不为每个页面新建 user_preference 表；
- 不把筛选值持久化到业务表；
- 新增 Table ID 必须唯一。

### 是否需要新表

**不需要。**

### Flyway

**不需要。**

### 验收标准

- [ ] 同一页面刷新后列设置保持；
- [ ] 不同页面的设置不串；
- [ ] 不同用户的本地 key 不串；
- [ ] 清空查询可以恢复默认条件；
- [ ] URL 深链页面不能被旧筛选条件污染；
- [ ] TableOperator 配置不影响 Playwright 的 DOM id。

---

# 13. 建议 Flyway 总表

以当前 `V43` 为基准的**暂定**版本：

| Version | 波次 | 内容 | 新表 |
| --- | --- | --- | --- |
| V44 | Wave 1 | `product_image.image_type` | 否 |
| V45 | Wave 1 | 商品 PCO-2 菜单/权限 | 否 |
| 0 migration | Wave 2A | 订单汇总 / 库存缺口只读预览 | 否 |
| V46 | Wave 2B | 采购批量/导出/打印权限（如需要） | 否 |
| V47 | Wave 4 | SCM 待办查询权限 | 否 |
| V48 | Wave 5 | `delivery_route_order` 打印记录字段 | 否 |
| V49 | Wave 6 | 盘点导入/复制权限 | 否 |

Wave 3 / 7 / 8 默认不需要迁移。

> 若实施时远端已占用这些版本，全部顺延；不要修改已应用迁移。

---

# 14. API 增量总表

## 商品

```http
GET  /scm/product/import/template
POST /scm/product/import
POST /scm/product/export
POST /scm/product/image/query
POST /scm/product/image/batch-bind
POST /scm/product/image/batch-remove
POST /scm/product/image/set-primary
POST /scm/product/image/reorder
```

## 采购

Wave 2A：

```http
POST /scm/purchase/demand/summary-preview
```

业务裁决后才允许进入第二阶段：

```http
POST /scm/purchase/demand/generate-by-shortage
```

Wave 2B：

```http
POST /scm/purchase/batch/short-close
POST /scm/purchase/export
GET  /scm/purchase/{id}/print
POST /scm/purchase/receipt/item-workbench/query
```

## 订单

```http
GET /scm/order/reference/recent-prices
```

## 待办

```http
GET /scm/dashboard/todo
```

## 配送

```http
GET  /scm/delivery/routes/{id}/orders-view
GET  /scm/delivery/routes/{id}/customers-view
POST /scm/delivery/routes/{id}/print/orders
POST /scm/delivery/routes/{id}/print/customers
```

## 盘点

```http
GET  /scm/inventory/stocktake/import/template
POST /scm/inventory/stocktake/import
POST /scm/inventory/stocktake/copy/{id}
```

## 客户

```http
GET /scm/customer/{customerId}/frequent-skus
```

---

# 15. AI 实施统一约束

每个 Wave 开工前，AI 必须执行以下动作。

## 15.1 先读

```text
AGENTS.md
docs/progress.md
docs/decisions.md
SMARTADMIN_REFERENCE_RULES.md
本文件
对应已有设计文档
对应已有 E2E / IT
```

商品必须额外读：

```text
docs/plan/product-center-optimization-plan.md
```

配送必须额外读：

```text
docs/plan/logistics-delivery-static-route-plan.md
docs/delivery-static-route-implementation.md
```

## 15.2 先同步远端

```text
git fetch
确认 origin/main
确认迁移最大版本
确认目标文件是否在开工前已发生变化
```

不要根据本文日期直接选号。

## 15.3 禁止事项

补充流程图只能作为**业务流程参考**，不能覆盖当前已落地的领域不变量。尤其禁止因为流程图看起来是“一条直线”就把以下模块强耦合：

```text
订单确认 ≠ 自动扣库存
采购需求生成 ≠ 自动按库存抵扣（除非执行 Wave 2A 第二阶段且口径已裁决）
配送规划 ≠ 发货出库
打印发货单 ≠ 改订单 / 库存状态
库存盘点 ≠ 直接覆盖余额
```

- 禁止修改历史已应用 Flyway；
- 禁止创建数据库外键；
- 禁止引入第二套认证；
- 禁止引入 Spring Security 登录体系；
- 禁止创建 React 页面；
- 禁止修改冻结的 `xsy-scm-miniapp`；
- 禁止绕过 FileService；
- 禁止绕过 Sa-Token 权限；
- 禁止 SQL mapper annotation；
- 禁止把库存修改写到非 Inventory 领域；
- 禁止直接 UPDATE / DELETE `inventory_movement`；
- 禁止创建“为了页面方便”的业务事实副本；
- 禁止为了参考蔬东坡而机械复制其表、状态、系统参数或 UI。

## 15.4 每个 Wave 的完成条件

一个 Wave 只有同时满足以下条件才算完成：

```text
后端编译通过
前端 production build 通过
新增单测/IT 通过
受影响既有测试通过
真实浏览器核心流程通过
无新增 pageerror
权限正向/反向验证通过
Flyway 连续且可从空库执行
关键负向用例通过
更新 docs/progress.md
必要时更新 docs/decisions.md
```

不能用：

```text
“代码写完了”
```

作为完成标准。

---

# 16. 推荐的 AI 单波次执行模板

后续把某一个 Wave 交给 AI 时，可以直接使用下面的指令骨架：

```text
请实施 docs/plan/current-module-optimization-from-sdongpo-v17.4.md 的 Wave X。

要求：
1. 先读取 AGENTS.md、docs/progress.md、docs/decisions.md、本 Wave 涉及的现有设计与测试；
2. git fetch 并确认当前 main、当前最大 Flyway，若本文迁移号过期则整体顺延；
3. 只实施本 Wave，不顺手实现下一 Wave；
4. 保留现有领域模型和状态机，不创建第二套事实；
5. 后端、前端、权限、迁移、测试、E2E 一起交付；
6. 所有新写接口加 Sa-Token 权限与必要的 @OperateLog；
7. 高风险写操作必须覆盖负向、并发/版本冲突、权限越界；
8. 完成后更新 docs/progress.md，并给出：
   - 改了什么
   - 没改什么
   - DB/Flyway
   - API
   - 页面
   - 测试结果
   - 剩余风险
9. 不提交/不推送，除非我另行明确要求。
```

---

# 17. 实施优先级结论

按当前项目成熟度，不应该继续优先“扩模块数量”，而应该先提高已有业务链的操作效率。

最终优先级：

```text
P0
- Wave 1 商品 PCO-2
- Wave 2A 订单汇总 / 库存缺口预览
- Wave 2B 采购操作效率
- Wave 3 订单录单效率
- Wave 4 业务待办/提醒

P1
- Wave 5 配送线路操作体验
- Wave 6 盘点效率
- Wave 7 客户 360°
- Wave 8 审计与表格体验统一
```

这套顺序保留当前系统已经形成的核心优势：

```text
明确状态机
严格权限
幂等写入
乐观锁
库存 append-only
移动加权成本
领域边界
真实 E2E
```

同时补上成熟 ERP 最容易影响实际使用体验的部分：

```text
批量维护
录单容错
历史复用
业务参考信息
待办提醒
打印工作流
上下文聚合
列表个性化
```

---

# 18. 最终原则

三张补充流程图带来的最终补充原则是：

> **先把跨模块的“看见与交接”补齐，再考虑把模块自动串起来。**

当前项目已有订单、采购、库存和配送的可靠业务事实；短期最有价值的是让用户能从订单汇总快速看到库存缺口、从缺口进入采购、从待办进入对应单据。只有业务口径和并发语义明确后，才把这些只读辅助能力升级为自动写流程。


参考蔬东坡 V17.40 后，当前项目的优化方向应保持：

```text
不是复制蔬东坡
而是把成熟 ERP 的高频操作经验
映射到 xsy-scm 已经稳定的领域模型上
```

具体执行口诀：

```text
已有事实不复制
已有状态机不重写
已有库存账不绕过
已有权限不降级
高频操作做批量
录入过程防丢失
上下文信息就近展示
待办能点进去处理
打印和导出服务于业务岗位
每一波都必须可验收
```
