# 当前模块优化计划（参考蔬东坡 ERP V17.40）

> 文件：`docs/plan/current-module-optimization-from-sdongpo-v17.4.md`  
> 项目：鲜蔬源智链 `xsy-scm`  
> 规划日期：2026-09-22  
> 仓库基线：`main @ 45412fb946b79fec7779f5a8d63190119567d014`  
> 当前 Flyway 最大版本：`V43`  
> 参考资料：`蔬东坡B2B系统操作手册17.4.pdf`（V17.40，271 页）+ 用户补充的 3 张业务流程图（订单→采购→库存→配送主链 / 系统业务操作流程 / 生鲜配送企业角色流程）
> 文档定位：**只对照 xsy-scm 当前已经存在的功能做增量优化，不把蔬东坡未在本项目落地的模块提前纳入本轮。**
> 审计修订：2026-09-22；本文件是待实施计划，新增验收项不代表已实现或已验证。既有模型约束直接沿用，尚未确认的业务口径列入 §1.3，不能由实施者自行补成永久规则。

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

### 1.3 实施门禁与待裁决项

本文件是**已有模块提效计划**，不代表参考资料中的接单、采购、分拣、履约、结算已全部闭环。

| 项目 | 当前事实 / 本轮处理 | 开工或扩展条件 |
| --- | --- | --- |
| 非标订单进入采购的时点 | 当前非标订单等待实重，停在 `PENDING`；W5 只读取已确认订单及其实数量。本轮不改变状态机，工作台显性说明未覆盖待实重订单 | 是否在实重前按订购数量参与采购、混合订单如何处理，须单独裁决后再实现 |
| 订单净采购缺口 | 独立出库单目前没有销售订单行关联，无法可靠扣除本批订单已履约量；Wave 2A 第一阶段只做限定口径的库存对比 | 净需求数量、履约扣减、在途采购抵扣、来源分摊、并发与重复生成口径明确后，才开放第二阶段 |
| 时间与仓库 | 现有需求按 `confirmed_at` 半开窗口读取；销售订单没有仓库字段 | 首期所选仓库仅是库存比较对象；按配送日期汇总、多仓需求归属或库存分配另行裁决 |
| 商品批量更新 | Wave 1 使用显式新增 / 更新模式，沿用已有领域校验；不做隐式 upsert 或按缺失行删除 SKU | 模板字段及清空语义须按 §5.4 固化并可预览 |
| 正式非管理员业务角色 | F0-DEBT-01 读侧仍未闭合；待办和聚合页不得扩大已有权限 | 引入此类角色前，须完成附件关系授权、受控 FileService 读取和对象存储模式验证；不因本计划而宣布债务关闭 |

上述门禁按功能适用，不阻塞无依赖的波次；任何未裁决项都不能用“按参考系统处理”代替负责人决定。

---

## 2. 参考手册中与当前项目直接相关的部分

本计划只吸收以下已能与当前模块一一对应的能力：

| 当前模块 | 蔬东坡手册位置 | 可借鉴点 |
| --- | --- | --- |
| 商品 | 第 2.4 节，约 p45-p50 | Excel 导入/维护、商品图片批量维护、主图/详情图、分类与辅助资料 |
| 订单 | 第 2.5 节，约 p51-p65 | 录单草稿恢复、历史订单复用、历史下单价参考、列表自定义、订单变更可见性 |
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
| 图 1：订单→库存判断→采购→入库→配送 | 客户下单后先汇总，再区分 0 库存 / 库存不足 / 库存充足；缺货进入采购，够货直接进入后续履约 | 销售订单、采购需求、采购单、收货、库存余额/预留/出库、Delivery L0-L2 都已存在 | 新增“已确认订单 + 当前库存对比”；净采购建议须另行裁决，不提前实现分拣或 L3 发车 |
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
销售订单 → 显式预留库存
独立出库单 → SALES_OUT 流水
```

两项能力都已存在，但当前独立出库单没有销售订单行关联，不能据此声称订单预留、实际出库和剩余履约量已经自动闭环。

以及：

```text
Delivery L0-L2 → 候选订单 → 线路组单 → 停靠点排序 → PLANNED → 打印 / 取消
```

但 Delivery L0-L2 当前**故意不扣库存、不创建出库单、不确认发车**。因此不能为了匹配流程图，把配送规划和库存出库直接绑死。

#### 本计划新增的衔接原则

```text
销售订单事实
  ↓
已确认订单汇总 / 当前库存对比（只读）
  ├─ 对比差额为 0 → 标识“所选订单总需求的库存对比差额为 0”
  └─ 对比差额大于 0 → 展示差额及口径限制，允许跳转采购工作台
                        ↓
                  仍复用现有采购需求 / 采购单 / 收货
```

第一阶段只做**只读预览与业务辅助决策**，不直接改变现有 `purchase_demand` 数量语义。这样既吸收流程图优点，也不会悄悄改掉已验证的 W5 状态机。

对比必须区分本批来源订单已预留量与其他预留量，不能将本批已备好的货再次算成缺口。因未能可靠扣除订单已履约量，第一阶段不显示“无需新增采购”或“建议采购量”；完整口径见 §6A.4。

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

> **按实际最大版本 + 1 连续分配本波次需要的迁移；可选迁移跳过时不留空号。绝对不能修改、重命名或复用已经应用的迁移。**

---

# 4. 总体实施顺序

建议按以下顺序实施：

```text
Wave 1   商品 PCO-2：Excel + 图片中心
   ↓
Wave 2A  已确认订单 / 当前库存对比（净采购建议待裁决）
   ↓
Wave 2B  采购操作效率：批量处理 + 导出/打印
   ↓
Wave 3   订单录单效率：草稿恢复 + 历史复用 + 最近已确认订单价
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
- **Wave 2A 是三张流程图补充后新增的流程级优化**，先看清已确认需求、库存和预留，再提升采购执行效率；第一阶段不宣称给出净采购缺口；
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

#### 导入模式、分组与更新契约

`/import/template`、`/import` 显式区分 `CREATE` / `UPDATE` 模式；一个文件只允许一种模式，禁止自动判断为 upsert。

| 项目 | CREATE 新增 | UPDATE 更新 |
| --- | --- | --- |
| 行粒度 | 一行一个 SKU，以“导入商品标识”归并同一个新 SPU；名称不能充当分组键 | 一行一个既有 SKU，以服务端核验的 SPU / SKU 标识定位；编码用于交叉核验，不能靠名称匹配 |
| 同组主档字段 | 每行重复的 SPU 字段必须一致，冲突定位到原始行号 | 同一 SPU 的更新字段和值必须一致，整个 SPU 只执行一次主档更新 |
| 重复与新增 | 同文件重复 SKU 标识或与库中活动编码冲突整批拒绝 | 不存在的 SPU / SKU 拒绝；新增规格走已有商品编辑流程，本模式不隐式创建 |
| 空白 | 必填缺失报错；可选字段采用已公布的新增默认值 | 默认保留旧值；清空通过模板专用“清空字段”列显式指定，只允许可空字段白名单，不接受任意属性名 |
| 缺失行 | 不适用 | 未出现的 SKU 保持原样，不能推断为删除或停用 |
| 并发 | 编码唯一约束及现有新增校验兜底 | 维护用导出携带 SPU / SKU 标识和版本；缺少版本或版本过期整批失败，不允许盲覆盖 |

普通列表导出与“可回导维护文件”应明确区分；只有后者保证包含更新所需标识、版本、模板版本及固定列。UPDATE 中标签非空时表示替换为给定集合，空白保留，显式清空才解绑；停用标签的历史保留与新绑定限制沿用现有规则。

服务端应返回将新增 / 更新 / 清空的摘要供用户确认，写入时重新执行领域校验和版本检查。读取文件、全量校验与正式写入可以通过同一 `/import` 的预览 / 确认参数实现，不另建商品事实副本。预览不是成功写入，也不能代替提交时校验。

商品类型、SKU 单位等已有业务引用保护，以及状态 / 分类 / 标签等动作的既有权限，不能被 `scm:product:import` 绕过。导入权限是入口权限，涉及受独立权限保护的动作还须校验相应权限；确切模板列、长度、批量上限和各列允许操作需在本波次规格中列全。

UPDATE 对接现有全量编辑 / 差量同步服务时，必须在事务内读取当前完整聚合再合并已声明的更新字段，保留文件未涉及的 SKU、图片和标签；不能把 Excel 中的局部行直接当成完整商品表单，触发现有“缺失即移除”语义。

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
- 新增 / 换绑要求 `public/image/`；同一图片行沿用原有私有 key 的排序或主图调整继续按 FA-0 规则放行，不得扩大到其他行或 SPU；
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
GALLERY
DETAIL
```

`image_type` 表示图集 / 详情图分组，**主图仍只由现有 `is_primary` 表达**，与原 PCO 方案 §15.3 一致。现有 `primaryFlag` API 映射和活动主图唯一索引继续有效，不新增 `PRIMARY` 类型作为第二套主图事实。

迁移与兼容要求：存量图片统一回填 `GALLERY`，保留 `is_primary`、排序和 fileKey；不猜哪些历史图片属于详情图。类型限定为 `GALLERY / DETAIL`，主图只能属于 `GALLERY`。旧编辑入口未传类型时，既有图片保留已存类型，新图片默认 `GALLERY`，不能在一次普通编辑中把详情图全部改回图集。

图片中心和原商品编辑入口必须共用主图切换、版本校验与绑定校验；切主图、改类型和排序并发时不得产生双主图或覆盖过期修改。旧私有图片迁移到公开目录是独立的 F0 工作，本波次不能加一个全表公开前缀 CHECK 阻断历史合法行。

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
  - product_image.image_type（GALLERY / DETAIL，存量回填 GALLERY）
  - 类型及主图所属分组 CHECK；复用 is_primary 活动行唯一索引

V45__scm_product_pco2_permissions.sql
  - scm:product:import
  - scm:product:export
  - scm:product:image:query
  - scm:product:image:batch
  - 图片中心菜单
```

> 开工时按实际最大版本连续分配，仅为本波次真实需要的迁移选号。

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
- [ ] CREATE / UPDATE 不混用；同 SPU 字段冲突、重复 SKU、版本缺失 / 过期均整批拒绝；
- [ ] UPDATE 空白保留、显式清空、缺失 SKU 不删除，且预览与提交之间的修改被版本校验发现；
- [ ] 已存在业务引用商品的关键历史字段保护与原有动作权限不能被导入绕过；
- [ ] 图片新增 / 换绑只能引用合法公开 key；历史图片原 key 的合法排序 / 主图修改不被误拒绝；
- [ ] 存量 image_type 回填不改变主图、排序或 fileKey；原编辑入口保留既有 DETAIL；
- [ ] 每个 SPU 最多一张 is_primary=true，且该行属于 GALLERY；
- [ ] 图片中心与原编辑入口并发设置主图不能产生双主图或丢失版本冲突；
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

# 6A. Wave 2A — 已确认订单 / 当前库存对比（净采购建议待裁决）

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

库存对比使用后端的可用量，并另外识别本批订单的 ACTIVE 预留；不能只减可用量，也不能用前端浮点运算。

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

接口入口复用采购需求查询权限，同时校验库存读取权限：

```text
scm:purchase:demand:query
scm:inventory:balance:query
```

两项均满足才能返回库存对比；仅有采购需求权限不能通过新聚合接口取得原本无权读取的库存。来源订单详情、采购单详情等跳转仍由目标接口按原有权限校验，不因汇总页可见而放开。正式非管理员角色还须满足 §1.3 的 F0 门禁。

### 请求建议

继续沿用 `PurchaseDemandGenerateForm` 的窗口语义：

```text
startAt
endAt
warehouseId
```

`startAt / endAt` 是 `sales_order.confirmed_at` 的半开区间 `[startAt, endAt)`，页面明确标为“订单确认时间”，展示时区为 `Asia/Shanghai`。不得改用创建日期或承诺配送日期却继续沿用相同字段说明。

`warehouseId` 必填且必须有效启用；**只表示与所选订单比较的一个仓库**，不是来源订单已有的仓库归属。同一来源订单不能在多个仓库视图求和后被解释为多份需求。订单无仓库字段，跨仓分配不在本轮推导；已有需求指定仓库、预留仓库与当前选择不同的行须提示，不自动改归属。

并允许筛选：

```text
supplierId?
purchaserId?
categoryId?
keyword?
```

其中 category / keyword 可按现有商品查询语义过滤；supplier / purchaser 只有在存在明确的采购需求关联时，才能按该关联过滤。没有生成需求的订单行不应凭空推断供应商 / 采购员，需作为“未指定”保留；若混合来源无法提供统一口径，第一版暂不提供这两个筛选。任何筛选都不自动决定采购关系。

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
orderDemandQuantity        // 已确认来源行 actual_quantity 总和，不是剩余履约量
onHandQuantity
reservedQuantity           // 所选仓库该 SKU 的全部 ACTIVE 预留量
selectedOrderReservedQuantity // 其中明确关联到本批来源订单行的 ACTIVE 预留量
otherReservedQuantity
availableQuantity
stockComparisonGap         // 限定口径的对比差额，不是建议采购量
calculationScope
calculationStatus
calculationNote
calculatedAt
```

来源只取未删除的 `CONFIRMED` 订单与有效明细，沿用 W5 的 `actual_quantity > 0` 及销售单位快照；不得混用 `ordered_quantity`。本批订单预留以 `inventory_reservation` 的来源类型、订单行 ID、仓库、SKU、单位及 `ACTIVE` 状态精确关联，先独立聚合再 join，避免一对多连接重复累计数量。

在单位、来源关联和预留一致性均可验证时，第一阶段只计算：

```text
otherReservedQuantity = reservedQuantity - selectedOrderReservedQuantity
stockComparisonGap = max(orderDemandQuantity - selectedOrderReservedQuantity - availableQuantity, 0)
calculationScope = CONFIRMED_GROSS_DEMAND_VS_CURRENT_STOCK
```

例如本批已确认需求 10 kg、现有量 10 kg、已全部预留给本批时，对比差额是 0；若这 10 kg 全部预留给其他订单，则对比差额是 10。不得将其他订单的预留充作本批可用库存。

**该公式没有扣除本批订单已履约量，也未扣开放采购量**。当前独立出库事实无法可靠归属到销售订单行，所以不能虚构 `fulfilledQuantity=0` 或把同 SKU 的全部出库量按比例分摊。页面固定说明“已确认订单总需求与当前库存对比，未扣订单已履约量及开放采购量”，不显示“建议采购量 / 无需采购 / 剩余待履约量”。同一库存对不同筛选窗口的结果也不能相加理解为独立可分配库存。

数量使用 `BigDecimal` 和四位定点字符串；无法比较时返回真正的 `null`，不能被通用数值序列化器改写成 `0.0000`。预留超过本批需求、来源单位不明、余额预留汇总不一致等异常返回 `NEEDS_RULE` 或对应明确原因，不用 `max` 把异常掩盖成 0。一次响应中的订单、余额和预留须来自一致的读取视图，不能拼接不同时间的数值。

`PENDING` 非标 / 混合订单不计入本口径，并在页面提示这一限制；它们没有确认时间，不能在同一确认时间窗口中伪造“未纳入数量”。是否按订购量提前参与采购，以及净需求扣减的正式规则，列入 §1.3 待裁决。

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
stockComparisonGap = null
```

页面明确提示“单位不一致，不能直接计算对比差额”，**禁止自动换算、禁止猜折算率**。

## 6A.6 在途采购必须先展示、后裁决

流程图 2 提到：

```text
待采购量 = 订单汇总 - 库存 - 在途库存
```

但当前项目对“在途是否抵扣采购缺口”尚不能从现有代码直接推导出唯一规则，而且当前调拨在途库存也没有进入 `inventory_balance`。

因此第一阶段默认不返回开放采购量数字。若希望增加：

```text
openPurchaseQuantity
```

须先明确所选仓库、SKU、单位、采购状态、部分收货剩余量及已收待上架数量的归属，再作为独立信息列展示，**不参与 `stockComparisonGap`**。未确定这些口径时隐藏该列或返回 `null + 未定义口径`，不能用 0 或所有采购行数量之和冒充在途量。

开放采购量展示及后续第二阶段需要分别裁决：

1. 哪些采购状态算“在途”；
2. 已创建未提交是否算；
3. 已部分收货如何计算剩余量；
4. 调拨在途是否参与采购建议；
5. 正式生成采购需求时是否再次锁定 / 重算；
6. 预览与正式生成之间库存变化如何提示。

没有这些裁决，AI 不得自行生成 `openPurchaseQuantity` 数值或将其扣掉。第二阶段还必须补齐销售订单行与履约 / 采购来源分摊的关系，不能以增加一个 API 代替事实关联设计。

## 6A.7 页面设计

在现有：

```text
purchase-demand-list.vue
```

增加：

```text
[采购需求] [已确认订单 / 库存对比]
```

两个 Tab，或新增同目录页面：

```text
purchase-demand-summary.vue
```

第一版采用现有页面中的 Tab，以复用菜单。若后续改为独立页面，必须补正式菜单 / 隐藏深链及权限路由设计，并按需要增加菜单数据迁移，不能仍承诺固定 0 migration。

表格建议：

```text
商品 / 规格 / 分类
订单数 / 订单需求量
现有量 / 总预留 / 本批预留 / 其他预留 / 可用量
库存对比差额（未扣履约与开放采购，非采购建议）
状态
操作
```

状态：

```text
COMPARISON_ZERO   对比差额为 0（不代表无需采购）
COMPARISON_GAP    对比差额大于 0
ZERO_STOCK        当前可用量为 0
UNIT_MISMATCH     单位不可直接比较
NO_BALANCE        尚无库存余额行
NEEDS_RULE        来源或数量关系不足以可靠比较
```

`ZERO_STOCK / NO_BALANCE` 是库存事实标记，不覆盖差额计算状态：可用量为 0 但货已预留给本批时，差额仍可为 0。`NO_BALANCE` 的现有量按 0 展示，不创建余额行；若同时存在对应 ACTIVE 预留，则应报一致性异常而非按零库存计算。

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
- 明确剩余需求、已履约量、本批 / 他批预留和跨仓归属；
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
inventory_reservation
purchase_demand / purchase_demand_allocation
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
0 schema migration；使用现有页面 Tab 时无新增菜单迁移
```

理由：

- 只读聚合；
- 复用需求查询和库存余额查询权限的交集；
- 不加表、不加列、不加状态。

## 6A.12 验收标准

- [ ] 同一 SKU 多张已确认订单可以正确聚合；
- [ ] 确认时间半开区间及 Asia/Shanghai 展示准确；PENDING / DRAFT 不计入，非标待实重限制显性可见；
- [ ] 仓库只作单仓比较对象，不冒充订单归属；不同仓库 / 筛选窗口的同源需求不能重复汇总；
- [ ] `availableQuantity = quantity - reservedQuantity`，页面不得自行重算口径；
- [ ] 0 库存余额行显示 0，但不产生任何库存写入；
- [ ] 单位不一致时不返回伪造对比差额；
- [ ] 需求 10、在库 10、本批 ACTIVE 预留 10 时差额为 0；全部为他批预留时差额为 10；
- [ ] 本批部分预留、释放预留、跨仓预留和异常来源均有用例，join 不重复累计；
- [ ] UNIT_MISMATCH / NEEDS_RULE 返回 null，序列化与页面均不得显示为零差额；
- [ ] 无可靠订单履约关联时，不猜已出库量，不展示建议采购量或“无需采购”；
- [ ] 开放采购口径未定义时不返回伪造的 0；定义后也不参与首期对比公式；
- [ ] 一次响应内订单、余额和预留来自一致读取视图；
- [ ] 缺少需求查询或库存查询任一权限时接口拒绝，深链按原领域权限验证；
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

# 7. Wave 3 — 订单录单效率：草稿恢复 + 历史复用 + 最近已确认订单价

## 7.1 参考点

蔬东坡订单录入有两个非常适合当前系统的设计：

1. 离开新增页时自动保存本地草稿，再次新增时提示恢复；
2. 单价旁显示该客户该商品最近 5 笔非关闭订单的下单单价（手册 p56）；本项目不直接把参考系统的“非关闭”映射为“非 CANCELLED”，实际采用下述已确认订单口径。

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

## 7.5 最近已确认订单价

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
下单时间 / 确认时间
订单来源
订单行 ID / 单位快照
下单数量
锁定单价 lockedUnitPrice
锁定价格来源 lockedPriceSource
```

本轮展示名固定为“最近已确认订单价”，是录单参考，不代表付款、签收或最终财务结算。查询契约：

- 只取未删除的 `CONFIRMED` 订单及未删除明细，排除 `DRAFT / PENDING / CANCELLED`；
- limit 最大 10；
- 按 `confirmed_at DESC, order_id DESC` 稳定排序，先取最近 N 张包含该 SKU 的订单，再返回这些订单的匹配明细；
- 必须按客户 + SKU；
- 同单同 SKU 多行分别展示行 ID、单位和锁定单价，不能任取一行、相加单价或未经定义求平均；limit 约束订单数，返回契约需明确为订单分组；
- 只读历史 `locked_unit_price` 与单位快照；锁定价异常缺失时标记异常，不用草稿价或当前价兜底；
- 沿用 `scm:order:query` 及原数据范围；没有订单查看权时不能仅凭商品 / 客户权限读取历史价格；
- 与当前 SKU 销售单位不同时显示历史单位及不可直接比较提示，不自动换算价格；
- 不回算当前价格，不赋值给新订单价格，不修改 PriceResolver。

单价输入框右侧增加：

```text
“最近已确认订单价”图标 / Popover
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
- [ ] 最近已确认订单价只查当前客户 + 当前 SKU，并沿用订单查询授权；
- [ ] DRAFT / PENDING / CANCELLED 及删除行不进入结果，锁定价缺失不回退到草稿价；
- [ ] 最近 N 张订单排序稳定，同单重复 SKU 多行与单位快照完整展示；
- [ ] 历史价格只用于参考，不改变 PriceResolver；
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

`scm:todo:query` 只授权入口，不隐含库存、收货、审批或配送权限。服务端按以下交集计算并返回卡片，前端隐藏只作为展示配合：

| 待办 | 所需领域权限（还须具备待办入口权限） | 数据口径 |
| --- | --- | --- |
| 库存异常 | `scm:inventory:warning:query` | 复用预警查询 / 状态判定，不能另写一套异常算法 |
| 待确认入库 | `scm:purchase:receipt:query` + `scm:purchase:receipt:putaway` | 与收货列表的待上架状态、仓库及数据范围一致 |
| 待审批报损报溢 | `scm:inventory:loss-gain:query`，并具有 `scm:inventory:loss-gain:approve` / `scm:inventory:loss-gain:reject` 至少其一 | 与审批列表待审筛选及数据范围一致 |
| 草稿配送线路 | `scm:delivery:route:query` + `scm:delivery:route:plan` | 只计现有 DRAFT 线路，与列表筛选一致 |

无权的卡片从响应中省略，不能返回数字或统一填 0；有权但无任务才返回 0。各卡片点击目标必须带相同筛选和数据范围，动作仍由原接口授权。首期仍只授 SUPER_ADMIN；权限负向夹具不等于正式业务角色上线，正式引入非管理员人员前必须完成 §1.3 的 F0-DEBT-01 读侧门禁。

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

消息必须与业务提交一致：优先在原业务事务中同步写入原生消息表，事务回滚不留成功通知；不通过未经可靠投递设计的异步回调冒充保证送达。并发 / 重复驳回只能对应一次有效状态变更和一次通知，验收重点是重复命令而非重复读取消息。接收人需仍具备目标单据读取资格；权限撤销后打开旧消息也不能绕过目标接口授权。

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
- [ ] 重复 / 并发驳回只有一次有效变更与一条通知，业务回滚不留成功通知；
- [ ] 消息点击可进入目标业务单据或列表；
- [ ] 无 `scm:todo:query` 权限账号不能访问待办接口；
- [ ] 仅有待办权限不能取得领域计数；无权限卡片省略、有权限零任务返回 0；
- [ ] 卡片列表筛选与数据范围一致，旧消息链接在权限撤销后仍被原接口拒绝；
- [ ] 非管理员正式上线前有 F0 读侧收口及对象存储模式验证证据，不以管理员验收替代；
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
PARTIAL
```

#### 客户聚合与部分打印

客户状态仅按当前线路内**有效关联订单**聚合，排除已经移出或取消释放的历史关系；客户下没有有效订单时不生成一条虚假的“未打印客户”。

| 状态 | 客户维度判定 |
| --- | --- |
| UNPRINTED | 有效订单均为 `print_count = 0` |
| PRINTED | 有效订单均为 `print_count > 0` |
| PARTIAL | 同时存在已生成和未生成打印的有效订单 |

订单维度只有 PRINTED / UNPRINTED，PARTIAL 只适用于客户聚合。为避免部分打印客户的剩余订单被遗漏，客户打印请求显式区分 `customerStatusFilter`（选择哪些客户）和 `orderPrintFilter`（这些客户中的哪些订单）；预览必须列出本次实际包含的订单号及数量，确认请求不能仅提交含义不明的 `ALL`。

例如同客户 A、B 两单仅 A 已打印：客户为 PARTIAL；选择 PARTIAL 客户并限定 UNPRINTED 订单时，只生成 B 并增加 B 的计数。按客户排版仍需按停靠点 / 地址保留分组，不把同一客户不同地址合成一个送货地址。

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

打印计数表示历史生成次数，不证明当前修改后的内容已经打印。继续沿用现有可打印状态；若草稿可打印，之后修改不把旧计数解释为新内容已打印。需要“当前内容版本已打印”的产品要求时须另行设计版本追踪，本轮不冒充支持。

新增 POST 生成命令复用现有幂等能力：同一请求重试只计一次，用户明确重打使用新请求标识；并发累加不能丢失。服务端在同一事务中重新校验线路状态 / 版本、有效订单集合及筛选，生成对应数据并更新这些订单的计数；预览后线路或集合变化应提示刷新，不能打印一批却标记另一批。

旧 `GET /routes/{id}/print` 保持只读，不暗加计数写入；正式生成入口统一调用 POST，旧 GET 仅作预览并明确不计次。取消浏览器打印仍可能已生成数据，所以不会回退已生成计数，也不宣称物理出纸成功。

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
- [ ] 客户 PRINTED / UNPRINTED / PARTIAL 互斥；同客户两单仅一单打印时为 PARTIAL；
- [ ] PARTIAL 客户中的未打印订单可单独生成，仅更新实际包含的订单；不同地址仍分组；
- [ ] 移出 / 取消释放的历史关联不进入有效订单计数，零有效订单不制造未打印客户；
- [ ] 同请求重试只计一次、明确重打再计次，并发累加不丢失；版本或订单集合变化拒绝旧请求；
- [ ] GET 预览不写计数，全部正式生成入口走 POST；历史次数不冒充当前版本已打印；
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
```

不新增后端 copy 写命令。复制通过既有详情查询、余额查询及前端新建表单完成；用户填完实盘量后才调用已有 create。

### Excel 模板

建议导出当前仓库参与盘点的：

```text
SKU 编码
商品名称
规格
记账单位
账面数量快照
余额行 ID / version / 导出时刻（只读校验元数据）
实盘数量（空）
备注（空）
```

导入后创建**新的 DRAFT 盘点单**，实盘数量必须由用户填写；空白不等于 0，任何空白或错误行均不落库。

#### 导出快照有效性与并发门禁

现有盘点差异为 `actual_quantity - book_quantity`，确认时将差异加到持锁读取的当前余额。现有 create / update 会重新快照保存时余额，因此离线 Excel 不能无条件调用 create。

本轮采用保守规则：**从导出到导入，任一参与行的余额版本变化即整批拒绝，要求重导并重新核对实盘数**；即使数量变动后又恢复，版本变化也不能忽略。

1. 导出在一致读取视图中记录仓库、SKU、记账单位、余额 ID、版本、账面量及导出时刻，附服务端可验证的签名快照凭证。凭证覆盖模板版本及来源集合，绑定操作者 / 适用权限并设置有效期；不另建业务快照表。
2. 只读元数据不能信任 Excel 隐藏列或保护工作表；导入验证凭证、操作者、仓库和完整来源集合。用户只编辑实盘数与备注，不能增删 / 替换来源行；要调整盘点范围应重新导出。
3. 导入在一个事务内按 `(warehouse_id, sku_id)` 顺序锁定所有来源余额，核验 ID、版本、单位及账面量仍与受保护快照一致；任一过期、缺失、被替换或不一致即整体失败，不产生 DRAFT。
4. 持有上述锁直至既有 create 完成，保证其重新快照得到的值与导出快照一致，消除“先校验、再保存”竞态；数量和来源必须来自已验证内容，不能在校验后改用客户端另一份数据。
5. 保存后发生的合法入出库，仍由原确认公式保留。导入复用幂等记录；响应丢失后的同请求重试不再创建第二张盘点单。

数值反例纳入验收：导出账面 100、清点 90、导入前又入库 20 时必须拒绝，不能用新账面 120 配旧清点数 90 写入盘亏 30。重新导出必须提示重新核对实盘，不能无提示复用旧数。

若后续需要允许离线期间持续收发货，应单独裁决清点时点、差异归属与可信服务端快照方案；不在本轮接受未经校验的 Excel 账面量，也不为方便而改动已应用 V29。

### 复制历史盘点

只复制到**未保存的前端新建表单**：

```text
仓库
SKU 集合
当前余额的记账单位（历史单位只供核对）
```

不复制：

```text
历史账面量
历史实盘量
历史差异
历史状态
历史 version
```

当前接口 `actualQuantity` 与数据库 `actual_quantity` 均不允许为空，因此复制操作本身不创建数据库 DRAFT，不默认填 0、历史实盘量或当前账面量冒充实盘。当前仓库 / SKU / 余额不存在时显性报错，不能悄悄丢弃行。

用户重新清点并填写每一行实盘量后，才提交已有 create；账面量仍由服务端按当前余额读取。表单明确区分“未保存”与“已保存 DRAFT”。若未来需要保存未填写实盘量的后台草稿，须另开模型设计，不能在本波次偷偷移除 NOT NULL。

## 10.4 不做“直接覆盖库存”

Excel 导入及复制后的提交：

```text
复制：未保存表单 → 填完实盘量 → create DRAFT
Excel：填完实盘量 → 验证签名快照并持锁核验版本 → create DRAFT
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

导入和实际 create 的操作记录先复用 `@OperateLog`；仅打开复制表单不伪造一条已创建单据的业务日志。

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
```

前端复制复用 `scm:inventory:stocktake:query` + `scm:inventory:stocktake:add`，当前余额查询仍需原有读取权限；不为一个无独立写命令的入口新建 copy 权限。导入入口还须有新增权限，模板中的余额字段受库存查询权限约束。

## 10.7 验收标准

- [ ] Excel 模板按仓库生成；
- [ ] 导入错误不产生 DRAFT；
- [ ] 实盘空白报错、显式 0 合法；签名、模板版本、操作者或来源集合被篡改 / 过期时拒绝；
- [ ] 100 / 90 / 后续入库 20 的离线反例整批拒绝，不落库、不丢掉后续入库量；
- [ ] 导出后余额变动再恢复、其他 SKU 版本冲突、锁前并发修改均被发现；锁内校验到创建无竞态；
- [ ] 导入响应丢失后同请求重试不重复创建，草稿保存后发生入出库仍按原差异公式处理；
- [ ] 重复 SKU 必须报错或按明确规则处理，不能静默合并；
- [ ] 单位不匹配直接拒绝；
- [ ] 复制仅打开未保存表单，不新建数据库草稿；缺失当前余额不静默漏行；
- [ ] 复制历史不带历史实盘量，也不自动填 0 / 当前账面量；填齐后才 create 并读取当前余额；
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

常购商品沿用 §7.5 的已确认订单状态白名单，建议取：

```text
近 N 天已确认且未删除订单（confirmed_at，Asia/Shanghai 日界）
按 SKU + sale_unit_snapshot 聚合
订单次数（COUNT DISTINCT order_id）
下单数量
最近确认时间
最近已确认订单价（复用 §7.5，不从同 SKU 多行任取价格）
```

注意：数量可能跨不同单位，不要在总计区把不同单位直接相加成一个“总量”。

`days` 和 `limit` 必须设服务端上限并校验；各分组数量为该组 `ordered_quantity` 之和，明确标注“订购量”，不是实重或结算量。相同 SKU 历史单位改变时分组显示，不能仅在总计区避免求和而在行内先混算。常购聚合需要客户查询与订单查询授权；价格字段遵循源订单价格读取规则，不因挂在客户页面就降低权限。

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

现有隐藏详情菜单已经存在；基础资料沿用客户查询权限，订单 / 价格 / 可见性仍由原领域权限约束，新增常购聚合按上述权限交集执行。

## 11.6 验收标准

- [ ] 客户详情 URL 刷新可恢复；
- [ ] 5 个 Tab 都以同一个 customerId 为上下文；
- [ ] 最近订单和订单列表按同样口径；
- [ ] 协议价数据与价格中心一致；
- [ ] 可售商品与 `customer_sku_visibility` 事实一致；
- [ ] 常购商品只做聚合，不落副本；
- [ ] 不同单位不错误求和；
- [ ] 草稿 / 待确认 / 取消单不进入常购统计，同单重复 SKU 不增加订单次数；
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

现有日志查询的 `requestKeywords` 是对 URL / method / param / response 的字符串包含搜索，无法保证对象精确归属：ID `12` 可能匹配 `112` 或数量字段。因此不能仅拼接关键词并声称得到该对象的全部日志。

本轮在原生日志查询中增加可选的结构化业务筛选，不新建第三套日志存储：

- 请求使用白名单 `businessType=PRODUCT|CUSTOMER|DELIVERY_ROUTE` 与 `businessId`；同时校验原操作日志读取权限及该领域对象读取权限，不能以商品查询权限获取完整通用日志。
- 为实际存在的写接口建立匹配清单：精确匹配 HTTP 方法及路径模板；路径参数按完整段比对；JSON 请求参数按明确的 `spuId / customerId / id` 等字段比对，而不是任意全文包含。具体字段以各接口真实合同为准。
- 批量命令只按指定的对象 ID 数组 / 明细路径做精确成员匹配；不能把 SKU ID、员工 ID 等同值字段误认为 SPU / 客户 / 线路 ID。
- 新增操作的对象 ID 可能在成功响应内，须按该接口真实响应结构解析；失败新增没有业务 ID 时不能猜归属。老日志参数截断、格式不合法或未记录 ID 时标为不可关联，不通过模糊兜底混入结果。
- 结构化筛选在后端分页和 total 计算前完成；不能先取一页日志再在前端筛选。日志 JSON 不是一律可解析，查询须避免脏值导致全页失败；自定义 SQL 继续放 Mapper XML。
- 前端日志页接收并校验路由中的业务类型 / ID，首次加载、刷新和对象切换都重新应用；旧本地筛选不能覆盖业务上下文。

验收目标为“能查看可精确关联的现有操作痕迹”。页面说明历史日志覆盖限制，不承诺完整变更历史或 before / after；无法识别的旧记录只能从原日志页单独检索，不能算作该对象的确定记录。

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

- [ ] 商品 / 客户 / 配送详情可进入带业务类型和 ID 的精确筛选，刷新和切换对象不丢上下文；
- [ ] ID 12 不命中 112、数量 12 或其他领域同值 ID；批量命令能精确匹配每个真实对象；
- [ ] 新增响应 ID、路径 ID、请求体 ID 分别覆盖；脏 JSON / 截断日志不使查询报错或误归属；
- [ ] 过滤先于分页，total 与结果一致；覆盖缺口明确说明，不冒充完整业务审计；
- [ ] 缺少原日志权限或对应领域读取权限时，直接访问筛选接口被拒绝；
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
| V44 | Wave 1 | `image_type=GALLERY/DETAIL`、存量回填和主图分组约束，复用 `is_primary` | 否 |
| V45 | Wave 1 | 商品 PCO-2 菜单/权限 | 否 |
| 0 migration | Wave 2A | 已确认订单 / 当前库存只读对比，采用现有页面 Tab；独立菜单则另加数据迁移 | 否 |
| V46 | Wave 2B | 采购批量/导出/打印权限（如需要） | 否 |
| V47 | Wave 4 | SCM 待办查询权限 | 否 |
| V48 | Wave 5 | `delivery_route_order` 打印记录字段 | 否 |
| V49 | Wave 6 | 盘点导入权限；复制复用既有查询 / 新增权限 | 否 |

Wave 3 / 7 / 8 默认不需要迁移。

> 表内编号仅作内容映射。每波开工同步远端后按实际最大版本 + 1 连续分配；可选迁移不实施时，后续不能为保留本文编号而跳号。不要修改已应用迁移。

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
```

盘点复制不增加写 API：详情 / 当前余额 → 未保存表单 → 用户填齐实盘量后调用原 create。

## 操作日志

扩展既有原生查询的可选参数 `businessType / businessId`，不新增日志事实表；精确筛选、授权交集与历史覆盖限制见 §12.1。

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

实施报告须逐项注明 §1.3 门禁是否适用、是否已裁决；只完成库存对比不代表已完成净采购建议，只完成管理员验证不代表正式非管理员角色可上线。本计划的新增 API / 字段 / 测试项均是未来工作，不得写入已完成进度。

---

# 16. 推荐的 AI 单波次执行模板

后续把某一个 Wave 交给 AI 时，可以直接使用下面的指令骨架：

```text
请实施 docs/plan/current-module-optimization-from-sdongpo-v17.4.md 的 Wave X。

要求：
1. 先读取 AGENTS.md、docs/progress.md、docs/decisions.md、本 Wave 涉及的现有设计与测试；
2. git fetch 并确认当前 main、当前最大 Flyway，按实际需要从 max + 1 连续分配，跳过可选迁移时不留空号；
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
- Wave 2A 已确认订单 / 当前库存对比
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

当前项目已有订单、采购、库存和配送的业务事实；短期最有价值的是让用户看清已确认订单与当前库存 / 预留的对比、进入采购查询、从待办进入对应单据。非标待实重订单覆盖、订单履约扣减和净采购建议仍受 §1.3 门禁约束；只有业务口径和并发语义明确后，才将只读辅助能力升级为自动写流程。


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
