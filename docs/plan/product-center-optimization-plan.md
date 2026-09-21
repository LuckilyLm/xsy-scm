# 商品中心优化方案（Product Center Optimization Plan）

> 适用项目：鲜蔬源智链 `xsy-scm`  
> 目标路径：`docs/plan/product-center-optimization-plan.md`  
> 当前状态（2026-09-21）：**PCO-1 已落地**，但落地时用的是 **V38–V39**（不是本文的 V37）；PCO-2（Excel 导入导出 / 图片中心）未开始；V40 已被地图模块 M0 占用。本文的分期编号仅作规划期快照保留，**实施前必须从当前最大号之后整体重排**，见 [`../requirements/2026-09-21-地图模块分期实施方案.md`](../requirements/2026-09-21-地图模块分期实施方案.md) §9 D-3。  
> 规划日期：2026-09-20  
> 仓库基线：`main @ 9143e09a0b57d69b408e59cdf6bd049813fd46c9`  
> 基线最后提交：`feat(screen): 数据大屏 V1 — 视觉与信息架构重构（按设计稿）`  
> 上一版方案基线：`974dd263f572a1af20edecf3a54b5f10869dda23`  
> 本次增量：`974dd263..9143e09`，共 1 个提交；商品域与 Flyway 结构未直接变更，但数据大屏新增了与商品分析相关的 SPU 聚合口径。  
> 说明：本文基于最新 `main` 的实际代码、数据库迁移、测试与需求文档制定，不按旧版设计重做 W1/W2/W3，而是在现有能力上增量增强。

---

## 1. 文档目的

本方案用于规划鲜蔬源智链商品相关能力的下一轮优化，重点参考生鲜配送 SaaS 中成熟的商品档案设计方式，包括：

- 商品档案与高级筛选；
- 商品三级分类；
- 商品辅助资料；
- 多规格 / 多销售单位展示；
- 采购方式、默认供应商、默认采购员；
- 客户差异化定价；
- 指定客户售卖；
- 商品图片中心；
- 商品条码；
- 商品标签；
- 上下架与售卖分析；
- 生鲜商品扩展字段；
- 批量导入、批量维护。

参考对象只用于提炼业务能力和交互思路，不复制其代码、数据结构或页面实现。最终方案必须服从本项目现有架构、不变量、权限模型、Flyway 迁移规则和业务主链。

---

## 2. 当前仓库事实基线

### 2.1 当前商品域已经完成，不应重做

当前 W1 商品模块已经具备：

- `product_category`
  - 最多三级分类；
  - 分类编码、名称、排序、启停；
  - 删除时阻止存在子分类或商品的分类；
  - 商品只能选择已启用的三级分类。
- `product_spu`
  - SPU 编码；
  - 商品名称；
  - 别名；
  - 分类；
  - 简介；
  - 上架 / 下架；
  - 乐观锁、逻辑删除和审计字段。
- `product_sku`
  - 多 SKU；
  - SKU 编码；
  - 条码；
  - 规格名称；
  - JSONB 规格属性；
  - 销售单位；
  - 标品 / 非标品；
  - 市场价；
  - 上架 / 下架；
  - 默认 SKU；
  - 排序。
- `product_image`
  - SPU 图集；
  - 主图；
  - 排序；
  - 文件服务 `fileKey`；
  - 最多 20 张；
  - 图片管理独立权限。
- 当前前端已经有：
  - 商品档案列表；
  - 商品新增 / 编辑 Drawer；
  - 商品详情；
  - 分类管理；
  - SKU 编辑表格；
  - 商品图片上传；
  - 商品搜索、分类筛选、状态筛选、SKU 状态和商品类型高级筛选。
- 当前商品 E2E 已覆盖：
  - 三级分类创建；
  - 多 SKU；
  - SKU 增量更新；
  - 主图；
  - 条码搜索；
  - 上下架；
  - 商品详情深链；
  - 删除；
  - 只读角色权限。

结论：

> 本轮必须以当前 W1 为基础演进，禁止重新创建第二套 `product` / `goods` 体系。

---

### 2.2 当前供应商能力

W2 已存在 `supplier_sku`，是 **SKU 级商品-供应商关系**，已经包含：

- `supplier_id`
- `sku_id`
- 供应商快照
- SKU 快照
- `purchase_unit`
- `reference_price`
- `purchaser_id`
- `is_default`
- `status`

现有写入口按“供应商整表替换”维护关系。

特别注意：

当前 `supplier_sku.is_default` 有历史约束，**允许同一供应商多条默认行**，不能直接把这个字段解释为“某 SKU 唯一默认供应商”。

因此本轮如需实现生鲜 SaaS 常见的：

- 采购方式：市场自采 / 供应商送货；
- 某 SKU 默认供应商；
- 某 SKU 默认采购员；

应新建明确的“采购策略”模型，而不是修改既有 `is_default` 的历史语义。

---

### 2.3 当前价格能力

W3 已经完成：

- 客户协议价 `customer_agreement_price`
- 客户类型价 `customer_type_price`
- 价格批量维护
- 价格历史
- 价格操作日志
- 客户商品可见范围

当前 `PriceResolver` 的实际优先级为：

```text
客户协议价
    ↓
客户类型价
    ↓
SKU market_price
```

当前还没有真正的：

- 商品时价；
- 阶梯价；
- 促销价。

`docs/requirements/2026-09-19-需求覆盖与待办清单.md` 也明确指出：

> “时价”仍是商品管理缺口；当前 `product_sku.market_price` 只是基础展示 / 兜底价格，不是时价机制。

并且“协议价不存在后的客户价 / 时价 / 活动价优先级”仍是待确认口径。

因此：

> 本轮商品中心可以统一价格入口和展示，但不能在没有业务裁决的情况下擅自修改 `PriceResolver` 的取价优先级。

---

### 2.4 当前指定客户售卖能力

当前已有：

```text
customer.visibility_policy
ALL_ENABLED
ALLOWLIST
```

以及：

```text
customer_sku_visibility
```

所以“指定客户售卖”不应再创建第二张类似关系表。

商品中心应复用当前能力，并提供 **从商品视角反查 / 配置入口**。

---

### 2.5 当前库存单位不变量

当前库存已经形成强约束：

```text
(warehouse_id, sku_id)
只能有一个记账单位
```

即 Q13。

V33 已实现的是：

```text
跨 SKU 的规格转换
SKU-A → SKU-B
```

而不是：

```text
同一个 SKU 在库存里同时按 斤 / 包 / 箱 记账
```

因此本轮商品“多单位”设计必须遵守：

> 多销售单位 / 包装规格可以在商品界面上表现为多行，但不能让同一 `(仓库, SKU)` 同时存在多个库存记账单位。

---

### 2.6 当前数据库迁移基线

当前 `main` 已存在：

```text
V1 ... V36
```

最新：

```text
V35__scm_sales_order_import.sql
V36__scm_sales_order_import_permission.sql
```

所以本文中的 `V37+` 仅是当前基线下的建议编号。

实施时如果 `main` 已新增迁移：

> 必须重新分配下一个空闲版本号，绝不能修改已应用的 V1-V36。

---


### 2.7 2026-09-20 最新提交增量核对

从上一版方案基线 `974dd263` 到当前 `9143e09`，仓库只新增了 1 个提交：

```text
feat(screen): 数据大屏 V1 — 视觉与信息架构重构（按设计稿）
```

该提交没有修改：

```text
module/scm/product
product_spu / product_sku / product_image / product_category
supplier_sku
pricing
V6 / V7 / V10 等历史商品与价格迁移
```

也没有新增 Flyway 版本，因此当前最高迁移仍然是：

```text
V36__scm_sales_order_import_permission.sql
```

所以本文的商品中心数据库规划仍可从 **V37** 起步；但真正开发前仍必须重新检查 `main`，不能把 V37 当成永久保留编号。

本次提交虽然没有直接改商品主数据，但新增了四条与商品中心后续设计直接相关的事实：

#### A. 数据大屏商品排行已经统一为 SPU 粒度

最新大屏把商品排行从：

```text
按 sku_id 分组
```

调整为：

```text
按 spu_id 聚合
```

原因是同一个商品可能有 5 斤装、10 斤装等多个 SKU；按 SKU 排行会让同一商品在“商品排行”中重复出现。

因此商品中心后续任何“商品级经营分析”都必须明确统计粒度：

```text
商品排行 / 商品销售分析       → SPU
规格排行 / 包装排行 / 单位排行 → SKU
```

禁止在页面标题写“商品排行”，SQL 却按 SKU 聚合。

#### B. 历史排行展示使用订单快照，而不是当前商品名称

最新大屏的商品排行按 `spu_id` 聚合，同时使用订单明细的：

```text
product_name_snapshot
```

作为历史展示名称。

这说明当前系统已经明确区分：

```text
商品主档当前值
订单发生时的历史快照
```

因此本文后续“售卖情况 / 商品分析”必须继续遵守：

> 交易事实优先使用订单快照，不因为商品后来改名而重写历史统计语义。

商品详情可以显示当前商品名，但历史订单排行、历史销售明细不应通过 JOIN 当前 `product_spu.name` 覆盖快照名称。

#### C. 经营时间口径统一为 Asia/Shanghai

最新提交修正了大屏“今日”窗口，从 UTC 日界改为：

```text
Asia/Shanghai
```

商品中心后续新增：

- 最近 30/60/90 天售卖情况；
- 最后销售日期；
- 新品统计；
- 导入时间筛选；
- 价格生效分析；

都应与现有经营口径保持一致。

数据库仍保存 `TIMESTAMPTZ`，按业务日统计时明确：

```sql
AT TIME ZONE 'Asia/Shanghai'
```

或在 Java 中统一使用项目业务时区。

#### D. 大屏继续坚持“只读聚合，不复制业务事实”

最新 B7 明确：

```text
数据大屏只读
不写业务表
不维护指标副本
```

因此商品中心增加标签、采购策略、扩展字段后：

- 大屏需要这些维度时，应从商品主数据查询 / 聚合；
- 不向 screen 域复制 `product_name`、`category`、`tag` 等可变商品事实；
- 新增商品经营指标时，优先共享明确的统计口径，而不是在两个模块各写一套不同算法。

---

### 2.8 最新提交对本方案的结论

上一版方案的主体设计仍然成立，不需要推翻。

需要强化的只有三点：

1. **商品级分析统一以 SPU 为统计粒度，SKU 分析必须明确标注为规格级。**
2. **历史销售分析继续以订单快照为事实，不能用当前主档名称改写历史。**
3. **所有“今日 / 最近 N 天”经营统计统一使用 `Asia/Shanghai` 业务日界。**


## 3. 本轮商品中心目标

优化后的商品中心应解决五类问题：

### 3.1 主数据更完整

商品不再只有：

```text
名称 + 分类 + SKU + 市场价 + 图片
```

而是形成：

```text
商品主档
├─ 基础资料
├─ SKU / 包装规格
├─ 采购策略
├─ 售卖策略
├─ 价格入口
├─ 图片资料
├─ 条码
├─ 标签
└─ 生鲜属性
```

### 3.2 运营维护效率更高

支持：

- 高级筛选；
- 批量导入；
- 批量导出；
- 批量分类；
- 批量上下架；
- 图片批量匹配；
- 快速发现无图商品；
- 快速发现长期未售商品。

### 3.3 不破坏现有业务链

必须继续保证：

```text
Product / Pricing
→ Sales Order
→ Purchase Demand
→ Purchase Order
→ Receiving
→ Inventory
→ Sorting
→ Delivery
→ Finance
```

### 3.4 不复制现有能力

以下能力只做整合和增强，不新建重复模型：

- 客户协议价；
- 客户类型价；
- 客户 SKU 可见范围；
- supplier_sku；
- FileService / MinIO；
- SmartAdmin RBAC；
- OperateLog；
- 库存规格转换。

### 3.5 为后续商城做准备，但不提前做商城

商品中心需要留下商城所需的字段和查询能力，但：

> 不因为商品优化提前启动小程序 / 商城主线。

---

# 4. 信息架构

建议“商品管理”逐步升级为“商品中心”，但首轮可以沿用现有一级菜单 `商品管理`，避免无意义改名。

建议二级菜单：

```text
商品管理
├─ 商品档案          已有，重点升级
├─ 商品分类          已有，增强
├─ 商品图片          新增
├─ 辅助资料          新增
└─ 售卖情况          新增

价格管理             继续使用现有 W3 页面，不复制
供应商管理           继续使用现有 W2 页面，不复制
```

“辅助资料”首轮包含：

```text
计量单位
商品标签
存储方式
```

后续可以扩展：

```text
品牌
产地
税收分类
```

---

# 5. 商品档案列表优化

## 5.1 页面布局

建议改为：

```text
商品档案                                      [新增商品]

商品分类 [全部]      搜索 [名称/编码/别名/条码/助记码] [查询]
商品状态 [全部]      售卖状态 [全部]                  [高级筛选]

[批量上架] [批量下架] [批量分类] [导入] [导出] [图片中心]

--------------------------------------------------------------------------------
□  主图  商品编码  商品名称  分类  默认规格/单位  类型  市场价  售卖状态  主档状态
   默认供应商  创建时间 / 更新时间                                      操作
--------------------------------------------------------------------------------
```

不要像参考页面一样默认展开十几个筛选控件。

默认只显示：

- 分类；
- 关键字；
- 主档状态；
- 售卖状态。

高级筛选展开后再显示：

- SKU 状态；
- 标品 / 非标品；
- 采购方式；
- 默认供应商；
- 默认采购员；
- 商品标签；
- 存储方式；
- 有无主图；
- 有无条码；
- 创建日期；
- 最近售卖日期。

---

## 5.2 搜索范围

当前关键字已经能查：

- 商品名；
- 编码；
- 条码。

建议扩展为：

```text
SPU 编码
商品名称
商品别名
助记码
SKU 编码
SKU 条码
```

助记码建议字段：

```text
mnemonic_code VARCHAR(64)
```

例如：

```text
红皮红心土豆
HONGPIHONGXINTUDOU
HPHXTD
```

首轮只存运营维护值，不强制系统自动生成拼音，避免额外拼音规则和歧义。

---

## 5.3 列表字段

建议默认列：

| 字段 | 来源 |
| --- | --- |
| 主图 | `product_image` |
| SPU 编码 | `product_spu.spu_code` |
| 商品名称 | `product_spu.name` |
| 分类 | `product_category` |
| 默认 SKU | `product_sku` |
| 销售单位 | 默认 SKU `sale_unit` |
| 商品类型 | 默认 SKU `product_type` |
| 市场价 | SKU 价格区间 |
| 主档状态 | 新增生命周期状态 |
| 售卖状态 | 现有 `status` |
| 默认供应商 | 新增采购策略 + supplier |
| 更新时间 | `updated_at` |
| 操作 | 编辑 / 上下架 / 更多 |

可选列：

- 别名；
- 助记码；
- 标签；
- 存储方式；
- 保质期；
- SKU 数；
- 是否有图；
- 最近售卖时间。

---

# 6. 商品详情 / 编辑页重构

当前 Drawer 已经能完成核心编辑，但商品能力增加后继续塞进一个长 Drawer 会迅速失控。

建议保留新增 Drawer 的轻量体验，同时把“编辑已有商品”升级为完整详情页。

## 6.1 新增商品

首轮新增仍可使用 Drawer，仅保留：

```text
基础信息
SKU / 规格
主图
```

新增成功后：

```text
保存并关闭
保存并继续完善
```

“继续完善”跳完整详情页。

---

## 6.2 完整商品详情页

建议：

```text
红皮红心土豆                [启用] [已上架]
SPU00002312

[基本信息]
[规格与单位]
[采购设置]
[价格设置]
[售卖范围]
[图片资料]
[条码]
[扩展信息]
[变更记录]
```

其中：

### 基本信息

- 商品分类
- 商品编码
- 商品名称
- 商品别名
- 助记码
- 商品简介
- 主档状态
- 售卖状态

### 规格与单位

继续以 SKU 为核心。

### 采购设置

整合采购方式、默认供应商、默认采购员。

### 价格设置

只做现有 W3 的聚合入口，不复制价格表。

### 售卖范围

复用 `customer_sku_visibility`。

### 图片资料

复用 FileService + `product_image`。

### 条码

当前主条码 + 后续多条码扩展。

### 扩展信息

保质期、储存方式、产地、损耗率等。

---

# 7. “多单位”在当前架构下的正确实现方式

参考系统把：

```text
斤
包/3斤
包/5斤
```

直接展示为同一商品下的单位行。

鲜蔬源当前已经有 SPU/SKU 模型，因此不建议再创建一个与 SKU 平行的大型“商品单位实体”。

## 7.1 推荐建模

一个 SPU：

```text
红皮红心土豆
```

下面建立：

```text
SKU-A：散装
销售单位：斤

SKU-B：包/3斤
销售单位：包

SKU-C：包/5斤
销售单位：包
```

前端“规格与单位”页可以按参考系统的方式展示为：

| 规格 / 单位 | 描述 | 折算提示 | 市场价 | 是否售卖 |
| --- | --- | --- | --- | --- |
| 斤 | 基础销售规格 | 1 斤 | 2.00 | 是 |
| 包/3斤 | 1 包 3 斤 | 3 斤 | 5.40 | 是 |
| 包/5斤 | 1 包 5 斤 | 5 斤 | 9.00 | 是 |

底层仍然是不同 SKU。

这样有三个优点：

1. 不破坏当前 SKU 定价；
2. 不破坏 `supplier_sku`；
3. 不破坏库存 Q13。

---

## 7.2 静态折算配置

为了减少规格转换时手工输入，可以后续给 SKU 增加可选包装折算信息：

```text
base_sku_id
base_quantity
```

示例：

```text
SKU-B 包/3斤
base_sku_id = SKU-A
base_quantity = 3
```

用途：

- 页面展示“1 包 = 3 斤”；
- 创建库存规格转换单时自动带入建议比例；
- 订单 / 分拣界面展示；
- 不直接偷偷改变库存。

原则：

> 静态折算规则只提供建议和标准关系，真正库存变化仍走现有 V33 规格转换事务和流水。

---

# 8. 计量单位辅助资料

新增单位字典，但第一阶段不改现有业务表的字符串字段。

建议表：

```sql
scm_uom
-------
id
uom_code
name
category
precision_scale
status
sort_order
version
deleted
created_at
updated_at
created_by
updated_by
```

示例：

```text
WEIGHT:
斤
kg
公斤
克

COUNT:
件
包
袋
箱
瓶
罐
桶
盒
筐
根
个
```

现有：

```text
product_sku.sale_unit
supplier_sku.purchase_unit
inventory_balance.unit
```

继续保留字符串快照。

前端从 `scm_uom` 选择后，把单位名称 / 代码写入现有字符串字段。

这样既获得统一字典，又避免一次性重构订单、采购、库存历史数据。

---

# 9. 商品采购策略

## 9.1 为什么不能直接复用 supplier_sku.is_default

既有 `supplier_sku.is_default` 的历史语义不能保证：

```text
一个 SKU 只能有一个默认供应商
```

因此新增一张明确的采购策略表。

建议：

```sql
product_sku_purchase_policy
---------------------------
id
sku_id
purchase_mode
default_supplier_id
default_purchaser_id
temporary_supplier_allowed
version
deleted
created_at
updated_at
created_by
updated_by
```

`purchase_mode`：

```text
SELF_PURCHASE
SUPPLIER_DELIVERY
```

规则：

### SELF_PURCHASE

允许：

```text
default_purchaser_id != null
default_supplier_id 可以为空
```

### SUPPLIER_DELIVERY

要求：

```text
default_supplier_id != null
```

且：

```text
supplier 必须 ENABLED
supplier_sku(supplier_id, sku_id) 必须存在且 ENABLED
```

---

## 9.2 商品详情展示

```text
采购方式：供应商送货

默认供应商：西安小猪佩奇
默认采购员：张三

可供货供应商：
------------------------------------------------
供应商             采购单位   参考价    状态
西安小猪佩奇       箱         38.0000   启用
供应商 B            袋         85.0000   启用
------------------------------------------------
```

“可供货供应商”直接反查现有 `supplier_sku`。

不新建第二套商品供应商关系。

---

# 10. 商品主档状态与售卖状态拆分

当前：

```text
product_spu.status
product_sku.status
```

只有：

```text
ON_SHELF
OFF_SHELF
```

它同时承担了“主数据是否可用”和“是否允许销售”的含义。

参考成熟 ERP，建议拆开。

## 10.1 保留现有 status

现有 `status` 继续表示：

```text
sale_status
ON_SHELF
OFF_SHELF
```

为了避免大范围改字段，可以先不重命名数据库列，只在代码注释和 VO 中明确语义。

---

## 10.2 新增主档生命周期状态

新增：

```text
master_status
```

值：

```text
ENABLED
DISABLED
ARCHIVED
```

语义：

### ENABLED

商品主数据正常，可用于业务。

### DISABLED

停止新业务引用，但保留历史。

### ARCHIVED

长期停用 / 已退出经营，仅保留历史。

---

## 10.3 状态组合

例如缺货暂停售卖：

```text
master_status = ENABLED
status = OFF_SHELF
```

商品彻底不再经营：

```text
master_status = ARCHIVED
status = OFF_SHELF
```

禁止：

```text
master_status = ARCHIVED
status = ON_SHELF
```

---

# 11. 删除策略优化

当前商品服务允许逻辑删除商品，同时删除 SKU 和图片。

这在商品尚未被业务引用时可接受，但未来订单 / 采购 / 库存都已经依赖 SKU。

建议调整为：

## 11.1 从未发生业务引用

允许逻辑删除：

```text
product_spu
product_sku
product_image
```

## 11.2 已经被业务引用

禁止 delete。

返回：

```text
商品已产生业务数据，不能删除，请停用或归档。
```

可归档：

```text
master_status = ARCHIVED
status = OFF_SHELF
```

历史：

- 订单；
- 采购单；
- 收货；
- 库存；
- 流水；
- 价格记录；

全部保持可读。

---

# 12. 商品标签

新增：

```sql
product_tag
-----------
id
tag_code
name
status
sort_order
version
deleted
...

product_tag_relation
--------------------
id
spu_id
tag_id
...
```

标签示例：

```text
有机
进口
学校专供
高校食品
当季
新品
高毛利
易损耗
兴宁农产品
```

首轮用途：

- 商品列表筛选；
- 批量打标签；
- 商品详情展示；
- 后续商城、采购和报表查询。

不要把标签做成 `product_spu.tag1/tag2/tag3`。

---

# 13. 生鲜商品扩展信息

建议 P1/P2 增加以下字段。

## 13.1 建议直接放 product_spu 的稳定标量

```text
brand_name
origin
storage_method
shelf_life_days
loss_rate
purchase_warning_days
invoice_name
tax_category_code
tax_exempt
tax_rate
mnemonic_code
```

原因：

- 这些字段是一对一稳定属性；
- 单独建扩展表只会增加查询复杂度；
- 当前 SPU 表本身就是商品主档。

---

## 13.2 字段定义

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `mnemonic_code` | VARCHAR(64) | 助记码 |
| `brand_name` | VARCHAR(100) | 品牌，先文本，后续有需求再字典化 |
| `origin` | VARCHAR(100) | 商品产地 |
| `storage_method` | VARCHAR(20) | AMBIENT / CHILLED / FROZEN |
| `shelf_life_days` | INTEGER | 保质期天数 |
| `loss_rate` | NUMERIC(8,4) | 损耗率 |
| `purchase_warning_days` | INTEGER | 采购预警天数 |
| `invoice_name` | VARCHAR(100) | 发票名称 |
| `tax_category_code` | VARCHAR(32) | 税收分类编码 |
| `tax_exempt` | BOOLEAN | 是否免税 |
| `tax_rate` | NUMERIC(8,4) | 税率 |

校验：

```text
shelf_life_days >= 0
loss_rate >= 0
loss_rate <= 100
purchase_warning_days >= 0
tax_rate >= 0
tax_rate <= 100
```

当前财务模块尚未全部完成，因此税务字段先作为主数据，不能提前改变现有订单金额口径。

---

# 14. 商品条码

当前每个 SKU 只有一个：

```text
product_sku.barcode
```

首轮继续把它定义为：

```text
主条码
```

如果业务确认存在：

- 箱码；
- 包装码；
- 秤码；
- 供应商条码；
- 多个兼容条码；

再新增：

```sql
product_sku_barcode
-------------------
id
sku_id
barcode
barcode_type
is_primary
status
...
```

并保留 `product_sku.barcode` 作为主条码快照 / 兼容字段。

不要在第一波为了“看起来完整”无业务依据地增加大量条码类型。

---

# 15. 图片中心

参考系统的“商品图片”独立页面非常值得做。

当前商品图片只能进入单个商品编辑页维护。

建议新增：

```text
商品管理 / 商品图片
```

## 15.1 页面能力

筛选：

- 分类；
- 商品编码 / 名称 / 条码；
- 有主图 / 无主图；
- 有详情图 / 无详情图；
- 图片数量；
- 商品状态。

列表：

```text
商品
主图
详情图
操作
```

操作：

- 添加主图；
- 添加详情图；
- 批量上传；
- 批量移除；
- 设主图；
- 图片排序。

---

## 15.2 文件名自动匹配

支持：

```text
SPU000001_1.jpg
SPU000001_2.jpg
SKU000001_main.jpg
```

匹配优先级：

```text
SKU 编码
    ↓
SPU 编码
```

匹配前必须预览：

```text
文件名                   匹配商品       结果
SPU001_1.jpg             土豆           可导入
ABC_1.jpg                —              未匹配
SKU009_main.jpg          西红柿 5kg     可导入
```

用户确认后才真正绑定。

---

## 15.3 现有表的演进

当前：

```text
product_image
```

首轮无需替换。

可新增：

```text
image_type
```

值：

```text
GALLERY
DETAIL
```

主图继续通过：

```text
is_primary
```

表达。

视频上传属于 P2：

```text
product_media
```

或独立视频字段，待商城 / 商品详情需求确认后再做。

---

# 16. 售卖范围

不要重新建“指定客户商品表”。

复用：

```text
customer.visibility_policy
customer_sku_visibility
```

商品详情新增“售卖范围”标签页。

提供两种视角：

## 16.1 当前 SKU 对哪些客户可售

反查：

```text
SKU
→ customer_sku_visibility
→ customer
```

## 16.2 当前客户允许哪些 SKU

继续使用现有客户管理页。

两端最终修改的是同一套关系。

---

# 17. 价格设置整合

商品中心不重做 W3。

商品详情“价格设置”显示：

```text
SKU：土豆 / 斤

基础市场价     ¥2.0000
客户类型价     3 条
协议价         12 条

[查看客户类型价]
[查看协议价]
[价格预览]
```

按钮跳现有价格管理页面并带 SKU 筛选参数。

---

## 17.1 时价

当前明确缺失。

建议后续表：

```sql
sku_time_price
--------------
id
sku_id
unit_price
effective_from
effective_to
status
version
deleted
...
```

规则：

- 时间范围不能重叠，或必须明确同一时刻选哪一条；
- 历史价格不可覆盖；
- 下单写订单价格快照；
- 生效与失效时间用 `TIMESTAMPTZ`；
- 价格仍使用 `NUMERIC(18,4)`。

实施前必须确认：

```text
协议价不存在时
客户类型价 / 时价 / 活动价 / 基础价
到底如何排序
```

---

## 17.2 阶梯价

参考系统有很强的阶梯价能力，但当前项目尚无明确规则。

建议作为 P2：

```sql
sku_tier_price
--------------
id
sku_id
scope_type
customer_type_id
customer_id
min_qty
max_qty
unit_price
effective_from
effective_to
status
...
```

但必须先确认：

- 阶梯数量按下单单位还是库存基础单位；
- 阶梯价与协议价如何组合；
- 一个订单多行同 SKU 是否合并计算；
- 跨单位是否折算；
- 是否允许 0 元价格；
- 改价权限如何影响阶梯价。

未确认前不要进入编码。

---

# 18. 商品辅助资料

新增页面：

```text
商品管理 / 辅助资料
```

Tab：

```text
计量单位
商品标签
存储方式
```

## 18.1 计量单位

支持：

- 新增；
- 编辑；
- 启停；
- 排序；
- 被引用后禁止删除。

## 18.2 标签

支持：

- 新增；
- 编辑；
- 启停；
- 排序；
- 查询商品数量。

## 18.3 存储方式

首轮可以先枚举：

```text
AMBIENT  常温
CHILLED  冷藏
FROZEN   冷冻
```

若业务后续需要自定义，再字典化。

---

# 19. 商品分类增强

现有三级分类逻辑已经比较完整，不重写树。

建议增加：

- 分类图片；
- 分类商品数量；
- 展开 / 收起全部；
- 分类状态筛选；
- 导入；
- 导出。

分类继续严格限制三级：

```text
1 → 2 → 3
```

不要在本轮改成无限级。

原因：

- 当前 DB CHECK 已限制 1~3；
- 当前商品选择器明确只选三级；
- 需求基线也明确“商品三级分类”。

---

# 20. 商品导入 / 导出

这应作为本轮 P0。

最新销售订单导入已经形成了可复用范式：

```text
下载系统内置模板
→ 解析固定版本模板
→ 汇总全文件错误
→ 全部通过后一个事务写入
→ 任一写入失败整批回滚
```

商品导入直接复用这一套工程思路。

---

## 20.1 模板下载

接口：

```http
GET /scm/product/import/template
```

权限：

```text
scm:product:import
```

模板文件：

```text
xsy-scm-server/sa-admin/src/main/resources/template/product-import.xlsx
```

下载头继续复用：

```text
SmartResponseUtil.setDownloadFileHeader
```

不要重新实现 Content-Disposition。

---

## 20.2 商品导入字段

第一版模板建议：

```text
SPU编码
商品名称
商品别名
助记码
一级分类
二级分类
三级分类
SKU编码
规格名称
销售单位
商品类型
市场价
条码
是否默认SKU
商品主档状态
售卖状态
储存方式
保质期天数
产地
备注
```

供应商、客户价、协议价不要塞到商品基础模板。

它们分别属于：

- 供应商商品模板；
- 价格模板。

避免一张 Excel 横跨多个聚合产生不可控事务。

---

## 20.3 导入规则

解析阶段必须一次性收集所有错误。

例如：

```text
第 12 行：三级分类不存在
第 18 行：SKU 编码 SKU001 已存在
第 26 行：市场价格式错误
第 31 行：销售单位“斤斤”不存在
第 42 行：同一 SPU 出现两个默认 SKU
```

只有：

```text
0 个错误
```

才能开始写数据库。

写入事务：

```text
@Transactional
整批 SPU + SKU
```

任意失败：

```text
整批回滚
```

禁止：

```text
前 80 行成功
第 81 行失败
页面却提示“部分成功”
```

除非未来单独设计“部分成功导入模式”。

---

## 20.4 导出

接口：

```http
POST /scm/product/export
```

导出范围：

- 当前筛选条件；
- 勾选商品；
- 全部商品（需要独立确认）。

至少包含：

- 商品主档；
- 默认 SKU；
- 分类；
- 状态；
- SKU 数；
- 默认供应商；
- 扩展字段。

完整 SKU 明细可以使用第二个 Sheet。

---

# 21. 批量维护

P0 建议支持：

- 批量上架；
- 批量下架；
- 批量启用；
- 批量停用；
- 批量改分类；
- 批量标签；
- 批量导出。

P1：

- 批量采购策略；
- 批量存储方式；
- 批量保质期。

价格批量修改继续走 W3，不放进商品批量接口。

所有批量命令需要：

- 单独权限；
- OperateLog；
- 原子事务或明确的逐条失败结果；
- 乐观锁 / 版本冲突处理；
- 失败行可定位。

---

# 22. 售卖情况

新增：

```text
商品管理 / 售卖情况
```

不新增事实表，第一版从订单数据聚合。

## 22.1 默认粒度：SPU

最新数据大屏已经明确“商品排行”按 `spu_id` 归并，因此本页面默认也应以 **SPU** 为商品粒度：

| 商品 | 售卖状态 | 最后下单时间 | 30天销量 | 30天客户数 | 30天销售额 | SKU数 |
| --- | --- | --- | --- | ---: | ---: | ---: |

点击商品后再展开 SKU 明细：

| SKU | 规格 | 销售单位 | 30天数量 | 30天金额 | 最后下单时间 |
| --- | --- | --- | ---: | ---: | --- |

不要让 5 斤装 / 10 斤装两个 SKU 在“商品排行”里成为两条同名商品。

## 22.2 历史名称口径

历史交易统计应优先使用订单明细快照，例如：

```text
product_name_snapshot
sku_code_snapshot
spec_name_snapshot
```

而不是只 JOIN 当前 `product_spu.name`。

推荐返回时同时区分：

```text
currentProductName       当前主档名称
historicalDisplayName    统计窗口内使用的历史快照名称
```

如果业务页面只需要一个名称，则“历史销售榜单”应使用快照名称；商品档案详情仍显示当前名称。

## 22.3 时间口径

所有：

```text
今日
最近 7 天
最近 30 天
最近 60 天
最近 90 天
```

按 `Asia/Shanghai` 业务日界统计。

不要使用 JVM / PostgreSQL 默认时区隐式截日。

快捷筛选：

```text
30 天无销售
60 天无销售
90 天无销售
从未销售
已上架但无销售
```

批量操作：

```text
批量下架
```

注意：

> “最后下单时间”是报表派生值，不要为了页面方便直接写入 `product_spu`，避免订单回滚 / 导入 / 补单后数据不一致。

## 22.4 与数据大屏的复用边界

大屏已有 SPU 商品排行，不建议商品中心再复制一套不同统计定义。

建议逐步收敛为：

```text
SalesAnalysisQuery / DAO
        │
        ├─ 商品中心售卖情况
        └─ 数据大屏商品排行
```

不要求首轮立即重构 B7，但新代码至少应遵守同一规则：

```text
商品 = SPU
历史交易名称 = 快照
业务日界 = Asia/Shanghai
```

数据量大后再考虑专门报表聚合表或物化视图。

---

# 23. API 规划

以下接口为建议，不要求一次全部实现。

## 23.1 商品档案

沿用：

```http
POST /scm/product/query
GET  /scm/product/detail/{spuId}
POST /scm/product/add
POST /scm/product/update
POST /scm/product/updateStatus
POST /scm/product/delete
```

新增：

```http
POST /scm/product/updateMasterStatus
POST /scm/product/batchSaleStatus
POST /scm/product/batchMasterStatus
POST /scm/product/batchCategory
POST /scm/product/batchTag
POST /scm/product/export
```

---

## 23.2 导入

```http
GET  /scm/product/import/template
POST /scm/product/import/preview
POST /scm/product/import/confirm
```

如果实现时仍采用“一次上传直接校验并导入”，可以合并 `preview/confirm`，但产品体验优先建议两阶段。

---

## 23.3 采购策略

```http
GET  /scm/product/purchase-policy/{skuId}
POST /scm/product/purchase-policy/save
```

反查供应商：

```http
POST /scm/supplier/sku/query
```

继续复用现有能力。

---

## 23.4 图片中心

```http
POST /scm/product/image/query
POST /scm/product/image/batchBind
POST /scm/product/image/batchRemove
POST /scm/product/image/setPrimary
POST /scm/product/image/reorder
```

文件本身仍通过现有 FileService 上传。

---

## 23.5 辅助资料

```http
POST /scm/product/uom/query
POST /scm/product/uom/add
POST /scm/product/uom/update
POST /scm/product/uom/updateStatus

POST /scm/product/tag/query
POST /scm/product/tag/add
POST /scm/product/tag/update
POST /scm/product/tag/updateStatus
```

---

## 23.6 售卖情况

```http
POST /scm/product/sales/query
```

---

# 24. 权限规划

保留现有：

```text
scm:product:query
scm:product:add
scm:product:update
scm:product:status
scm:product:delete
scm:product:image

scm:product:category:query
scm:product:category:add
scm:product:category:update
scm:product:category:delete
```

新增建议：

```text
scm:product:master-status
scm:product:batch
scm:product:import
scm:product:export
scm:product:purchase-policy
scm:product:image:query
scm:product:image:batch
scm:product:uom:query
scm:product:uom:update
scm:product:tag:query
scm:product:tag:update
scm:product:sales:query
```

权限菜单 ID：

> 实施时基于最新 `t_menu` / 最新迁移分配，不在规划文档中硬编码当前假设 ID。

---

# 25. 数据库迁移规划

基于当前 `main @ 9143e09`，下一空闲迁移仍是 V37。最新数据大屏提交没有新增数据库迁移。

如果开工前 main 已新增迁移，则整体顺延。

建议拆分：

## V37 — 商品中心主数据增强

```text
product_spu
+ mnemonic_code
+ brand_name
+ origin
+ storage_method
+ shelf_life_days
+ loss_rate
+ purchase_warning_days
+ invoice_name
+ tax_category_code
+ tax_exempt
+ tax_rate
+ master_status

product_sku
+ master_status（如确认 SKU 也需要独立主档生命周期）

product_image
+ image_type

CREATE scm_uom
CREATE product_tag
CREATE product_tag_relation
CREATE product_sku_purchase_policy
```

不要修改 `V6__scm_product.sql`。

---

## V38 — 商品中心菜单与权限

新增：

- 商品图片菜单；
- 辅助资料菜单；
- 售卖情况菜单；
- 商品导入 / 导出等按钮权限。

---

## V39 — 商品导入审计（可选）

如果需要长期追溯导入：

```sql
product_import_batch
--------------------
id
batch_key
file_name
template_version
total_rows
success_rows
failed_rows
status
error_data JSONB
created_at
created_by
```

若第一版只要求即时错误回显，可以先不建审计表。

---

## V40 — 时价 / 阶梯价

只有业务规则确认后才创建。

不要为了占版本号提前建空表。

---

# 26. 建议新增 / 修改代码位置

## 26.1 后端

现有商品包：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/
```

建议继续在这里扩展：

```text
product/
├─ controller/
│  ├─ ProductController.java
│  ├─ ProductCategoryController.java
│  ├─ ProductImageCenterController.java
│  ├─ ProductUomController.java
│  ├─ ProductTagController.java
│  ├─ ProductImportController.java
│  └─ ProductSalesAnalysisController.java
│
├─ service/
│  ├─ ProductSpuService.java
│  ├─ ProductQueryService.java
│  ├─ ProductPurchasePolicyService.java
│  ├─ ProductImageCenterService.java
│  ├─ ProductUomService.java
│  ├─ ProductTagService.java
│  ├─ ProductImportService.java
│  └─ ProductSalesAnalysisService.java
│
├─ dao/
├─ domain/
│  ├─ entity/
│  ├─ form/
│  └─ vo/
└─ manager/
```

不要创建：

```text
module/goods/
module/productV2/
```

---

## 26.2 前端

继续：

```text
xsy-scm-web/src/views/business/scm/product/
```

建议：

```text
product/
├─ product-list.vue
├─ product-detail.vue
├─ category-list.vue
├─ image-center.vue
├─ auxiliary-data.vue
├─ sales-analysis.vue
│
├─ components/
│  ├─ product-form-drawer.vue
│  ├─ product-basic-form.vue
│  ├─ product-sku-editable-table.vue
│  ├─ product-purchase-policy.vue
│  ├─ product-price-summary.vue
│  ├─ product-sale-scope.vue
│  ├─ product-image-upload.vue
│  ├─ product-tag-select.vue
│  └─ product-import-modal.vue
```

共享选择器继续优先放：

```text
xsy-scm-web/src/components/business/scm/
```

不要在每个页面重复造：

- 商品选择器；
- 供应商选择器；
- 分类选择器；
- 员工选择器；
- 单位选择器。

---

# 27. 前端交互原则

继续遵守项目 `AGENTS.md`：

- SmartAdmin Layout；
- Ant Design Vue；
- 表格为 ERP 主交互；
- 金额右对齐；
- 数量右对齐；
- 状态居中；
- 操作右对齐；
- 危险操作必须确认；
- 复杂表单使用 Drawer / 独立详情页；
- 不引入第二套 UI 框架。

商品编辑页不要复刻参考系统的超长滚动表单。

建议：

```text
主信息简洁
复杂信息拆 Tab
批量能力独立页面
```

---

# 28. 与现有业务模块的边界

## 商品域负责

- 商品是什么；
- 商品有哪些 SKU；
- 商品属于什么分类；
- 商品图片；
- 商品标签；
- 商品主档属性；
- 商品销售基础状态；
- SKU 采购策略；
- 商品基础辅助资料。

## Pricing 负责

- 客户类型价；
- 协议价；
- 时价（未来）；
- 阶梯价（未来）；
- 价格解析。

## Supplier 负责

- 供应商主档；
- 供应商可供货 SKU；
- 采购单位；
- 供应商参考价。

## Customer 负责

- 客户主档；
- 客户类型；
- SKU 可见范围。

## Inventory 负责

- 库存余额；
- 库存记账单位；
- 库存流水；
- 规格转换；
- 成本。

## Screen / Report 负责

- 只读经营聚合；
- 商品排行；
- 趋势；
- KPI 展示。

Screen / Report 不拥有商品事实，不维护 `product_spu` / `product_sku` 的副本。

商品中心新增经营分析时必须与当前 B7 口径一致：

```text
商品级 = SPU
规格级 = SKU
历史交易展示 = 订单快照
业务日界 = Asia/Shanghai
```

商品域禁止复制库存、订单、价格和大屏聚合事实。

---

# 29. P0 / P1 / P2 分阶段实施

## P0：商品维护效率

优先做：

1. 商品列表筛选升级；
2. 助记码；
3. 主档状态与售卖状态拆分；
4. 商品标签；
5. 计量单位字典；
6. 商品导入；
7. 商品导出；
8. 批量上下架 / 分类 / 标签；
9. 商品图片中心；
10. 删除保护。

目标：

> 先把运营维护几千商品的效率做起来。

---

## P1：供应链商品属性

做：

1. 商品采购策略；
2. 默认供应商；
3. 默认采购员；
4. 生鲜扩展字段；
5. SKU 包装折算提示；
6. 商品详情完整 Tab；
7. 售卖范围商品视角；
8. 售卖情况页面。

目标：

> 把商品从“基础 CRUD”升级为“供应链商品主数据”。

---

## P2：高级定价与商城准备

业务口径确认后做：

1. 时价；
2. 阶梯价；
3. 多条码；
4. 详情图类型；
5. 商品视频；
6. 商城展示属性；
7. 商品营销标签；
8. 更复杂的价格优先级。

目标：

> 为后续营销和小程序提供稳定商品能力，但不提前实现商城。

---

# 30. 推荐实施波次

为了不和现有“财务 / 分拣 / 配送”等 Roadmap 强绑定，建议用独立 Wave 表达。

## PCO-1：商品主档增强

后端：

- V37 主数据增强；
- 标签；
- UOM；
- master_status；
- 查询增强；
- 删除保护。

前端：

- 列表高级筛选；
- 状态展示；
- 标签；
- 辅助资料页。

---

## PCO-2：导入导出与图片中心

- product-import.xlsx；
- 全量校验；
- 原子导入；
- 导出；
- 图片中心；
- 文件名匹配；
- 批量操作。

---

## PCO-3：采购策略与详情页

- purchase policy；
- 默认供应商；
- 默认采购员；
- 商品详情 Tab 化；
- 生鲜扩展字段；
- 商品视角客户售卖范围。

---

## PCO-4：售卖分析与高级价格

- 售卖情况；
- 时价；
- 阶梯价；
- 高级条码。

其中高级价格必须以负责人确认价格优先级为前置条件。

---

# 31. 后端验收标准

## 商品主档

- 新增商品仍必须有至少一个 SKU；
- 一个 SPU 仍只能有一个默认 SKU；
- SKU 编码唯一；
- 条码唯一规则保持；
- 商品只能选择启用三级分类；
- `master_status=ARCHIVED` 时必须 `OFF_SHELF`；
- 已有业务引用商品不能删除；
- 乐观锁冲突返回明确业务错误；
- 批量操作不能绕过权限。

## 标签

- 标签编码唯一；
- 被引用标签不能物理删除；
- 关系不能重复。

## UOM

- 单位编码唯一；
- 已引用单位禁止删除；
- 停用单位不得用于新商品配置；
- 历史字符串快照不回写。

## 采购策略

- SKU 必须存在；
- 供应商送货必须指定默认供应商；
- 默认供应商必须是该 SKU 的有效 `supplier_sku`；
- 默认采购员必须存在；
- 版本冲突整笔失败。

## 导入

- 模板版本校验；
- 表头严格校验；
- 所有行先校验；
- 0 错误才落库；
- 事务内任意失败整批回滚；
- 错误定位到行和字段；
- 不能生成“错误 JSON 伪装成 xlsx”。

---

# 32. 前端验收标准

商品列表：

- 搜索商品名、SPU、SKU、条码、别名、助记码；
- 分类支持选择父级并查询所有后代；
- 高级筛选收起后不影响当前查询值；
- 表格横向滚动不遮挡固定操作列；
- 批量操作只有有权限角色可见。

商品详情：

- 刷新页面仍能通过 URL 直接打开；
- Tab 可以深链或至少刷新保持商品上下文；
- 价格页只读取 / 跳转 W3，不维护第二份价格；
- 供应商列表来自 supplier_sku；
- 客户售卖范围来自 customer_sku_visibility。

图片中心：

- 无图商品可筛；
- 上传失败有明确错误；
- 自动匹配必须先预览；
- 未匹配文件不会静默丢弃；
- 设主图后每个 SPU 最多一张主图。

导入：

- 能下载模板；
- 校验错误全部展示；
- 可下载失败明细；
- 导入按钮防重复提交；
- 大文件上传有 loading；
- 成功后刷新商品列表。

---

# 33. 测试规划

## 33.1 后端

在现有：

```text
module/scm/product/
```

测试基础上新增：

```text
ProductMasterStatusIT
ProductDeleteGuardIT
ProductTagIT
ProductUomIT
ProductPurchasePolicyIT
ProductImportIT
ProductImageCenterIT
ProductSalesAnalysisIT
```

重点并发 / 边界：

- 两人同时编辑同一商品；
- 批量上下架时部分版本过期；
- 删除与新订单同时发生；
- 商品归档与 supplier_sku 编辑并发；
- 同一文件重复导入；
- 同一 SKU 重复条码；
- 同一 SPU 多主图竞争。

---

## 33.2 前端单测

扩展：

```text
product-form-model.test.mjs
```

新增：

```text
product-import-model.test.mjs
product-batch-action.test.mjs
product-purchase-policy.test.mjs
```

---

## 33.3 Playwright

扩展：

```text
xsy-scm-web/e2e/scm-product.spec.ts
```

新增场景：

1. 主档启停与销售上下架相互独立；
2. 标签筛选；
3. UOM 维护；
4. 导入模板下载；
5. 错误导入零写入；
6. 正确导入批量创建；
7. 商品图片批量匹配；
8. 默认供应商配置；
9. 已被业务引用商品无法删除；
10. 只读角色看不到批量维护按钮。

---

# 34. 不建议本轮做的事情

以下能力看起来“像完整商品中心”，但现在做容易引入不必要复杂度。

## 34.1 不把同一个 SKU 做成库存多单位

禁止：

```text
仓库 A + SKU001
同时存在 斤 / 包 / 箱 三种 inventory_balance
```

会直接破坏 Q13。

---

## 34.2 不重建 pricing

不要再建：

```text
product_customer_price
product_agreement_price_v2
```

现有 W3 已经存在。

---

## 34.3 不重建 supplier_sku

不要新建第二套：

```text
product_supplier
```

只为显示可供货供应商。

默认采购策略单独建一对一策略表即可。

---

## 34.4 不因为参考系统有“云商品库”就照搬

云商品库涉及：

- 外部标准商品；
- 数据同步；
- 图片版权；
- 编码映射；
- 外部来源版本；

当前没有明确业务收益，暂不进入范围。

---

## 34.5 不提前把商城属性塞满 product_spu

商城还没启动。

只保留对后台也有价值的字段。

---

# 35. 关键业务决策清单

开发 PCO-1 前建议确认：

### Q-PC-01 商品编码

是否：

```text
人工维护
```

还是：

```text
系统生成 + 有权限人员可改
```

当前代码要求前端传 `spuCode` / `skuCode`。

---

### Q-PC-02 主档停用

`DISABLED` 后是否允许：

- 已有订单继续履约：建议允许；
- 新订单引用：建议禁止；
- 新采购单引用：建议禁止；
- 库存处理：建议允许历史库存清理。

---

### Q-PC-03 默认供应商粒度

建议按：

```text
SKU
```

而不是 SPU。

因为供应商关系本身已经是 SKU 级。

---

### Q-PC-04 采购方式

是否支持：

```text
SELF_PURCHASE
SUPPLIER_DELIVERY
```

同时存在？

建议每个 SKU 只选一个默认采购方式，但允许临时采购覆盖。

---

### Q-PC-05 单位换算

包装折算是否只是建议关系，还是订单提交时必须自动归一化？

建议第一阶段：

> 只用于展示和规格转换自动带值，不改变库存 Q13。

---

### Q-PC-06 时价 / 阶梯价优先级

必须先确认：

```text
协议价
客户类型价
促销价
时价
阶梯价
market_price
```

完整顺序。

没有裁决前不改 `PriceResolver`。

---

# 36. 第一轮推荐实际落地范围

如果只选一轮开发，建议做：

```text
PCO-1 + PCO-2
```

也就是：

### 数据

- `product_spu` 扩展字段；
- `master_status`；
- `scm_uom`；
- `product_tag`；
- `product_tag_relation`；
- `product_image.image_type`。

### 后端

- 商品高级查询；
- 批量状态；
- 批量分类；
- 批量标签；
- 商品导入；
- 商品导出；
- 图片中心；
- 删除保护。

### 前端

- 商品列表升级；
- 辅助资料；
- 商品详情基础 Tab；
- 图片中心；
- 导入弹窗；
- 批量操作。

这一轮结束后，运营层面的商品能力会明显接近成熟生鲜配送 SaaS，同时不会大范围扰动订单、采购、库存与价格核心链路。

---

# 37. 实施注意事项

1. **先更新 main 再编码**  
   本文基于 `9143e09`。开工时重新确认最新提交和最新 Flyway 版本；本次从 `974dd263` 到 `9143e09` 只有数据大屏 V1 一个提交，当前迁移仍停在 V36。

2. **不可修改历史迁移**  
   V1-V36 已视为不可变。所有 DB 改动只新增迁移。

3. **保持 PostgreSQL 方言**  
   不照抄参考项目中的 MySQL SQL。

4. **价格精度继续 NUMERIC(18,4)**  
   不因为 UI 显示两位就改变数据库精度。

5. **所有业务时间继续 TIMESTAMPTZ**。

6. **继续使用逻辑删除 + 乐观锁**。

7. **继续使用 Sa-Token 权限**  
   不引入 Spring Security / Spring Session。

8. **继续复用 FileService**  
   不把永久 URL 当商品图片事实源。

9. **批量能力优先保证原子性和错误可解释性**。

10. **库存事实继续由 Inventory 域负责**  
    商品列表不得保存“库存数量”字段作为事实副本。

---

# 38. 最终目标形态

优化完成后的商品中心应形成：

```text
                          ProductCategory
                                │
                                ▼
                           Product SPU
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
          ▼                     ▼                     ▼
      Product SKU          Product Image          Product Tag
          │
          ├──────────── Supplier SKU ─────────── Supplier
          │
          ├──────────── Purchase Policy ─────── Purchaser
          │
          ├──────────── Agreement Price ─────── Customer
          │
          ├──────────── Customer Type Price ─── CustomerType
          │
          ├──────────── Customer Visibility ─── Customer
          │
          └──────────── Inventory Balance ───── Warehouse
```

关键原则：

> 商品中心定义“商品是什么、如何维护、如何采购、如何售卖”；  
> 价格域定义“卖多少钱”；  
> 供应商域定义“谁能供货”；  
> 客户域定义“谁能看到”；  
> 库存域定义“现在还有多少、以什么单位记账”；  
> 订单域保存发生交易时的快照。

这比直接复制参考系统页面更适合当前 `xsy-scm` 已经形成的业务边界。

---

# 39. 结论

当前 `xsy-scm` 商品模块并不是从零开始，W1/W2/W3 已经建立了：

- SPU / SKU；
- 三级分类；
- 图片；
- 多供应商；
- 客户类型价；
- 客户协议价；
- 客户 SKU 可见范围；
- 完整权限和测试基础。

因此商品优化的正确方向不是“重写一个像蔬东坡的商品模块”，而是：

```text
保留现有核心模型
        +
增强主数据
        +
提升批量维护能力
        +
增加采购策略
        +
整合价格 / 客户可见性
        +
补图片中心和售卖分析
```

优先实施：

```text
P0：维护效率
→ P1：供应链商品属性
→ P2：高级定价 / 商城准备
```

这样既能吸收成熟生鲜配送系统的优点，又不会破坏鲜蔬源智链当前已经完成的订单、采购、库存和价格主链。


---

# 40. 本次更新记录（9143e09）

相较上一版 `974dd263`：

- 仓库 HEAD 更新到 `9143e09a0b57d69b408e59cdf6bd049813fd46c9`；
- 新增提交仅为数据大屏 V1 重构；
- 商品域源码、商品表结构、supplier_sku、pricing 均未直接改变；
- Flyway 最高版本仍为 V36，因此商品中心规划仍从 V37 起；
- 新增并固化三条商品分析约束：
  1. 商品经营排行按 SPU 聚合；
  2. 历史交易名称使用订单快照；
  3. 经营日期按 Asia/Shanghai；
- “售卖情况”章节已经按上述口径重写；
- 增加商品中心与 Screen / Report 的职责边界，避免未来商品中心和数据大屏出现两套统计规则。

