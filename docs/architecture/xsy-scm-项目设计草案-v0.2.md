# 鲜蔬源智慧供应链管理平台（xsy-scm）项目设计草案

> ## ⚠️ 技术路线部分已废止（2026-09-14）
>
> 本文档的**业务范围、领域边界、模块划分与迭代顺序**仍然有效，可作为业务语义参考。
>
> 但其中与**技术实现**相关的决策**已废止**，不再作为实现依据：
>
> - 「管理端采用 React」→ V2 管理端为 **Vue3 + TypeScript**（SmartAdmin 原生）。
> - 「旧 auth / system 模块自研」→ V2 **直接采用 SmartAdmin 系统能力**，旧 auth/system **不迁移**。
> - 「小程序采用 Taro + React」→ V2 目标为 **uni-app + Vue3**。
> - 后端技术栈以 **SmartAdmin v3.31 基线（Java 21 + Spring Boot 3.5.4 + Sa-Token + PostgreSQL）** 为准。
>
> 现行依据：[`../../SMARTADMIN_REFERENCE_RULES.md`](../../SMARTADMIN_REFERENCE_RULES.md)、
> [`2026-09-14-smartadmin-v2-迁移审计报告.md`](./2026-09-14-smartadmin-v2-迁移审计报告.md)。

> **负责人确认口径（2026-09-09）**：商城是一个逻辑商城，由不同展示端承载，当前手机端先做微信小程序；采购单由人工按固定时间段统计生成，并可显式选择是否计算实时库存；一张采购单对应多张收货记录，少收/多收必须留痕；收货支持直接入库或二次入库确认；当前价格先按协议价优先。详细决策与未决项见 [负责人确认口径](../requirements/2026-09-09-负责人确认口径.md)。

> 项目名称：鲜蔬源智慧供应链管理平台
> 页面简称：鲜蔬源智链
> 项目代码：`xsy-scm`
> 文档版本：v0.2
> 文档定位：项目启动阶段的总体设计草案，用于统一范围、技术路线、模块边界和迭代顺序。
> 说明：本文基于现有功能清单与已确定的技术方向整理，具体字段、状态机、权限颗粒度、单据流转规则将在详细设计阶段继续补充。

---

## 1. 项目概述

鲜蔬源智慧供应链管理平台面向生鲜、食材配送业务，覆盖商品、客户、营销、订单、采购、库存、分拣、配送、财务、商城、订单助手、溯源及后台管理等业务。

系统重点解决以下问题：

1. 商品、客户、供应商、仓库等基础资料统一管理。
2. 从客户下单到采购、收货、库存、分拣、发货、配送、结算形成完整业务闭环。
3. 支持标品、非标品、实重商品、多规格、多供应商等食材配送场景。
4. 支持不同客户类型、协议价、时价等差异化定价。
5. 支持按订单、供应商、采购员、品类自动汇总生成采购任务。
6. 支持实重收货、实重分拣、重量回传。
7. 支持商城/小程序下单以及后台快速录单。
8. 支持业务经营分析、采购分析、客户利润、单品利润等报表。
9. 支持数据大屏、配送大屏、分拣大屏、溯源大屏。
10. 支持一物一码扫码溯源。
11. 支持电子秤等现场硬件设备接入，实现采购收货、分拣、复核等场景的自动称重、重量回传与审计追踪。

---

## 2. 建设目标

### 2.1 核心目标

形成以下业务主链路：

```text
客户
  ↓
商品 / 客户价格体系
  ↓
商城下单 / 后台录单
  ↓
销售订单
  ↓
订单汇总
  ↓
自动生成采购任务
  ↓
采购单
  ↓
实重收货
  ↓
入库 / 库存
  ↓
分拣
  ↓
实重回写订单
  ↓
发货
  ↓
配送
  ↓
客户签收
  ↓
应收 / 应付 / 利润核算
```

### 2.2 第一阶段目标

第一阶段优先实现“可用的供应链业务主流程”，不追求一次性覆盖全部高级功能。

重点包括：

- 系统基础框架
- 用户登录与权限
- 商品管理
- 客户管理
- 供应商管理
- 仓库管理
- 订单管理
- 采购管理
- 收货管理
- 库存管理
- 分拣管理
- 发货与配送管理
- 基础财务报表
- 数据大屏基础版
- 电子秤接入协议调研与 `xsy-device-agent` 技术验证

---

## 3. 用户与角色

初步规划以下角色：

| 角色 | 主要职责 |
|---|---|
| 系统管理员 | 用户、角色、权限、参数、日志管理 |
| 商品管理员 | 商品、分类、单位、价格、图片维护 |
| 客户管理员 / 业务员 | 客户资料、客户商品权限、客户价格、账期管理 |
| 订单员 | 后台录单、改单、补单、订单核算 |
| 采购员 | 采购任务、采购单、采购价格、收货跟进 |
| 仓库管理员 | 入库、出库、盘点、报损、报溢、库存调整 |
| 分拣员 | 分拣任务、称重、标签打印、差异处理 |
| 配送调度 | 配送线路、车辆、司机、发货任务 |
| 配送司机 | 配送任务、轨迹、签收 |
| 财务人员 | 应收、应付、收付款、利润、财务报表 |
| 供应商 | 查看/确认采购任务、回传价格和重量（按后续范围确认） |
| 下游客户 | 商城下单、查看订单、重量、配送、账单 |
| 管理层 | 首页经营分析、数据大屏、经营报表 |

权限建议采用：

```text
用户
  ↓
角色
  ↓
菜单权限
  ↓
按钮 / 操作权限
  ↓
数据权限（仓库 / 客户 / 供应商 / 业务员）
```

---

## 4. 总体功能架构

```text
鲜蔬源智慧供应链管理平台
│
├─ 首页 / 工作台
│  ├─ 经营数据
│  ├─ 待办事项
│  ├─ 预警信息
│  └─ 快捷入口
│
├─ 商品中心
│  ├─ 商品档案
│  ├─ 商品分类
│  ├─ 商品规格
│  ├─ 商品图片
│  ├─ 客户价格
│  ├─ 商品供应商
│  └─ 商品上下架
│
├─ 客户中心
│  ├─ 客户档案
│  ├─ 客户分类
│  ├─ 集团客户
│  ├─ 客户商品权限
│  ├─ 客户账期
│  ├─ 客户业务员
│  └─ 推广二维码
│
├─ 营销中心
│  ├─ 首页装修
│  ├─ 新品推荐
│  ├─ 抢购活动
│  ├─ 满减满赠
│  ├─ 优惠券
│  └─ 限时特价
│
├─ 订单中心
│  ├─ 订单列表
│  ├─ 后台快速录单
│  ├─ 补单
│  ├─ 订单核算
│  ├─ 退货退款
│  ├─ 异常订单
│  ├─ 订单日志
│  └─ 订单汇总
│
├─ 采购中心
│  ├─ 采购需求
│  ├─ 自动汇总
│  ├─ 采购单
│  ├─ 采购退回
│  ├─ 收货
│  ├─ 采购历史
│  ├─ 供应商
│  └─ 采购员
│
├─ 库存中心
│  ├─ 库存查询
│  ├─ 入库
│  ├─ 出库
│  ├─ 盘点
│  ├─ 报损
│  ├─ 报溢
│  ├─ 规格转换
│  ├─ 库存预警
│  └─ 库存流水
│
├─ 分拣中心
│  ├─ 按商品分拣
│  ├─ 按客户订单分拣
│  ├─ 实重分拣
│  ├─ 分拣差异
│  ├─ 标签/小票打印
│  └─ 分拣任务
│
├─ 设备中心
│  ├─ 电子秤设备
│  ├─ 设备绑定
│  ├─ 设备状态
│  ├─ 称重记录
│  ├─ 通讯配置
│  └─ 设备日志
│
├─ 配送中心
│  ├─ 发货任务
│  ├─ 配送线路
│  ├─ 车辆管理
│  ├─ 司机管理
│  ├─ 配送任务
│  ├─ 轨迹记录
│  ├─ 签收
│  └─ 发货单打印
│
├─ 财务中心
│  ├─ 应收
│  ├─ 已收款
│  ├─ 待收款
│  ├─ 应付款
│  ├─ 客户账单
│  ├─ 供应商账单
│  ├─ 单品利润
│  └─ 客户利润
│
├─ 报表中心
│  ├─ 经营数据
│  ├─ 销售明细
│  ├─ 客户订单明细
│  ├─ 销售员业绩
│  ├─ 采购明细
│  ├─ 库存报表
│  └─ 财务报表
│
├─ 商城
│  ├─ 首页
│  ├─ 商品搜索
│  ├─ 商品分类
│  ├─ 常用菜品
│  ├─ 购物车
│  ├─ 再来一单
│  ├─ 订单
│  ├─ 支付
│  └─ 数据查询
│
├─ 订单助手
│  ├─ 手机端改单
│  ├─ 手机端改价
│  └─ 手机端订单入库
│
├─ 溯源中心
│  ├─ 溯源码
│  ├─ 批次
│  ├─ 商品追溯
│  └─ 扫码查询
│
├─ 数据大屏
│  ├─ 数据大屏
│  ├─ 配送大屏
│  ├─ 分拣大屏
│  └─ 溯源大屏
│
└─ 系统管理
   ├─ 用户
   ├─ 角色
   ├─ 权限
   ├─ 字典
   ├─ 系统参数
   ├─ 单据模板
   ├─ 操作日志
   ├─ 待办事项
   └─ 帮助中心
```

---

## 5. 技术架构

### 5.1 推荐技术栈

#### 管理后台 / 数据大屏

```text
React
Vite
TypeScript
Ant Design
@ant-design/pro-components
React Router
TanStack Query
Axios
Zustand
React Hook Form
Zod
Apache ECharts
DataV-React（大屏按需）
高德地图 JS API（配送地图）
```

#### 后端

```text
Java 21
Spring Boot
Spring Security
MyBatis-Plus
Spring Validation
Flyway
OpenAPI 3
PostgreSQL
Redis（按需加入）
```

#### 现场设备接入

```text
xsy-device-agent（Windows 本地设备接入服务）
WebSocket / HTTP
串口 RS232 / USB 虚拟串口 / TCP/IP
厂商 SDK / DLL（如设备要求）
```

原则：

- React 管理后台不直接绑定某一电子秤厂商协议。
- 电子秤、标签打印机、扫码枪等现场设备优先通过 `xsy-device-agent` 统一接入。
- 浏览器仅通过 `localhost` WebSocket / HTTP 获取设备状态与实时重量。
- 具体协议适配封装在 Device Adapter 中，厂商或型号变化时尽量不影响 ERP 主系统。
- 如现场设备确认支持标准 Web Serial 且部署环境可控，可作为备选接入方式，但不作为默认生产方案。

第一阶段不引入：

```text
微服务
Kafka
Kubernetes
复杂分布式事务
独立搜索引擎
```

待业务量、并发量或部署规模明确后再评估。

### 5.2 总体架构

```text
┌─────────────────────────────────────┐
│          Web 管理后台 React          │
│  Ant Design + ProComponents         │
└────────────────┬────────────────────┘
                 │
                 │ REST / JSON
                 ▼
┌─────────────────────────────────────┐
│          Spring Boot API            │
│                                     │
│ product / customer / order          │
│ purchase / inventory / sorting      │
│ delivery / finance / system         │
└───────────┬─────────────┬───────────┘
            │             │
            ▼             ▼
      PostgreSQL        Redis
                        （按需）
            │
            ▼
      Object Storage
       商品图片/附件

┌─────────────────────────────────────┐
│      数据大屏 / 配送大屏 React       │
│ ECharts + DataV + AMap              │
└────────────────┬────────────────────┘
                 │
                 └────── 同一套 API

┌─────────────────────────────────────┐
│          现场工作站浏览器             │
│ 收货称重 / 分拣称重 / 复核           │
└────────────────┬────────────────────┘
                 │ localhost WebSocket / HTTP
                 ▼
┌─────────────────────────────────────┐
│        xsy-device-agent             │
│ 串口 / USB / TCP / 厂商 SDK         │
│ 协议解析 / 稳定重量 / 断线重连        │
└────────────────┬────────────────────┘
                 │
                 ▼
          电子秤 / 打印机 / 扫码枪
```

---

## 6. 前端设计

### 6.1 后台 UI

后台 UI 延续现有参考图的结构：

```text
顶部导航
+
一级侧边栏
+
二级业务菜单
+
页面标题 / Tab
+
查询筛选区域
+
操作按钮区域
+
数据表格
+
分页 / 汇总
```

建议使用：

- `ProLayout`：后台整体布局
- `ProTable`：列表、查询、分页
- `ProForm`：新增/编辑
- `ProDescriptions`：详情页
- `ModalForm / DrawerForm`：轻量新增编辑
- `ECharts`：首页统计图表

### 6.2 大屏 UI

数据大屏采用独立布局，不复用后台导航。

设计基准：

```text
1920 × 1080
```

结构：

```text
顶部标题 / 当前时间
│
├─ 左侧指标区域
├─ 中部核心地图 / 业务图
├─ 右侧排行 / 消息
└─ 底部大屏导航
```

大屏类型：

1. 数据大屏
2. 配送大屏
3. 分拣大屏
4. 溯源大屏

大屏建议采用统一组件：

```text
ScreenLayout
ScreenHeader
ScreenPanel
KpiCard
RankingChart
ProgressPanel
MapPanel
ChartPanel
ScreenFooterNav
```

---

## 7. 后端模块设计

建议采用“按业务领域分包”，避免所有 Controller / Service / Mapper 堆在统一目录。

```text
com.xianshuyuan.scm
│
├─ common
│  ├─ config
│  ├─ exception
│  ├─ response
│  ├─ security
│  ├─ pagination
│  └─ util
│
├─ auth
├─ system
├─ product
├─ customer
├─ supplier
├─ order
├─ purchase
├─ inventory
├─ sorting
├─ device
├─ delivery
├─ finance
├─ marketing
├─ traceability
├─ report
└─ screen
```

每个业务模块内部：

```text
product
├─ controller
├─ service
├─ repository
├─ mapper
├─ entity
├─ dto
├─ vo
└─ converter
```

---

## 8. 核心领域设计

### 8.1 商品

核心实体：

```text
ProductCategory
Product
ProductSku
ProductImage
ProductSupplier
CustomerProduct
CustomerPrice
ProductPriceHistory
```

商品需要支持：

- 三级分类
- 标品 / 非标品
- 多规格
- 多供应商
- 默认采购模式
- 默认采购员
- 默认供应商
- 商品图片
- 商品上下架
- 客户可见/不可见
- 客户差异价格
- 时价
- 协议价

---

### 8.2 客户

核心实体：

```text
Customer
CustomerType
CustomerGroup
CustomerAddress
CustomerContact
CustomerCreditTerm
CustomerSalesman
CustomerSupplierBinding
```

需要考虑：

- 普通客户
- 集团客户
- 集团下属单位
- 独立采购
- 集团统一结算
- 客户商品权限
- 客户价格
- 客户账期
- 指定业务员
- 指定供应商

---

### 8.3 订单

核心实体：

```text
SalesOrder
SalesOrderItem
OrderAdjustment
OrderRefund
OrderReturn
OrderOperationLog
```

建议订单来源：

```text
MALL
ADMIN
MOBILE_ASSISTANT
IMPORT
```

主要状态建议：

```text
DRAFT
PENDING
CONFIRMED
PURCHASING
SORTING
READY_TO_SHIP
SHIPPING
COMPLETED
CANCELLED
```

实际状态将在详细设计阶段结合业务确认。

---

### 8.4 采购

核心实体：

```text
PurchaseDemand
PurchaseOrder
PurchaseOrderItem
PurchaseReceipt
PurchaseReceiptItem
Supplier
Purchaser
```

采购核心流程：

```text
销售订单
  ↓
采购需求汇总
  ↓
按供应商 / 采购员 / 品类拆分
  ↓
采购单
  ↓
供应商确认 / 采购执行
  ↓
多次收货
  ↓
实重收货
  ↓
入库
```

---

### 8.5 库存

核心实体：

```text
Warehouse
Inventory
InventoryBatch
InventoryMovement
Stocktaking
StockLoss
StockGain
UnitConversion
```

库存变化必须形成流水。

建议统一库存流水类型：

```text
PURCHASE_IN
SALES_OUT
RETURN_IN
PURCHASE_RETURN_OUT
STOCKTAKE_IN
STOCKTAKE_OUT
LOSS
GAIN
TRANSFER_IN
TRANSFER_OUT
ADJUSTMENT
```

成本核算：

```text
移动加权平均 / 加权平均
```

具体核算口径需财务确认。

---

### 8.6 分拣

核心实体：

```text
SortingTask
SortingTaskItem
SortingRecord
SortingWeight
SortingLabel
```

支持：

- 按商品分拣
- 按客户订单分拣
- 标品一键分拣
- 非标品实重分拣
- 分拣误差阈值
- 分拣员
- 配送时间
- 配送线路
- 供应商代分拣
- 分拣打印

关键点：

```text
订单数量 ≠ 最终实重数量
```

非标品最终结算数量可以由分拣实重回写。

---

### 8.7 配送

核心实体：

```text
DeliveryRoute
DeliveryTask
DeliveryOrder
Vehicle
Driver
VehicleLocation
DeliveryTrack
DeliverySign
```

配送流程：

```text
待发货订单
  ↓
生成配送任务
  ↓
规划线路
  ↓
分配车辆 / 司机
  ↓
装车
  ↓
发车
  ↓
轨迹上传
  ↓
客户签收
```

地图场景：

- 客户分布
- 配送点
- 配送线路
- 车辆实时位置
- 行驶轨迹

---

### 8.9 电子秤与现场设备接入

核心实体：

```text
Device
DeviceBinding
WeightRecord
DeviceEvent
```

电子秤主要服务以下业务：

```text
采购收货
分拣称重
复核称重
库存称重（按需）
```

推荐架构：

```text
电子秤
  ↓
xsy-device-agent
  ↓
localhost WebSocket / HTTP
  ↓
xsy-scm-web
  ↓
Spring Boot API
  ↓
WeightRecord / PurchaseReceipt / SortingRecord / InventoryMovement
```

### 设备接入原则

1. ERP 主系统不直接依赖某一厂家 SDK。
2. 每种协议通过 Adapter 适配，例如 `SerialScaleAdapter`、`TcpScaleAdapter`、`VendorSdkAdapter`。
3. 本地 Agent 负责设备发现、连接、断线重连、协议解析和稳定重量判断。
4. 前端只消费标准化后的重量事件，不解析原始串口协议。
5. 关键称重记录需要同时保存标准化重量和必要的原始报文，便于审计。
6. 支持毛重、皮重、净重；如设备不支持某项能力，则由配置明确标记。
7. 对于实重商品，业务确认后才允许将稳定重量写入订单、收货或分拣记录。
8. 人工修改称重结果必须记录修改前后值、操作人、时间和原因。

标准重量事件建议：

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

采购收货示例：

```text
采购单
  ↓
选择待收货商品
  ↓
电子秤稳定重量
  ↓
确认本次收货
  ↓
PurchaseReceiptItem
  ↓
InventoryMovement(PURCHASE_IN)
```

分拣示例：

```text
客户订单
  ↓
分拣任务
  ↓
电子秤稳定重量
  ↓
确认分拣
  ↓
SortingRecord
  ↓
实重回写 SalesOrderItem
  ↓
打印标签 / 小票
```

---

### 8.8 财务

第一阶段财务重点：

```text
客户应收
客户收款
供应商应付
供应商付款
销售收入
采购成本
商品利润
客户利润
```

后续再评估是否扩展完整财务会计功能。

---

## 9. 数据库初步规划

预计核心表按领域拆分。

### 9.1 系统

```text
sys_user
sys_role
sys_permission
sys_user_role
sys_role_permission
sys_operation_log
sys_dictionary
sys_config
```

### 9.2 商品

```text
product_category
product
product_sku
product_image
product_supplier
product_price
customer_product
customer_product_price
```

### 9.3 客户

```text
customer
customer_type
customer_group
customer_address
customer_contact
customer_credit_term
customer_salesman
```

### 9.4 采购

```text
supplier
purchaser
purchase_demand
purchase_demand_item
purchase_order
purchase_order_item
purchase_receipt
purchase_receipt_item
```

### 9.5 订单

```text
sales_order
sales_order_item
order_refund
order_return
order_operation_log
```

### 9.6 库存

```text
warehouse
inventory
inventory_batch
inventory_movement
stocktaking
stocktaking_item
stock_loss
stock_gain
```

### 9.7 分拣

```text
sorting_task
sorting_task_item
sorting_record
sorting_label
```

### 9.8 配送

```text
delivery_route
delivery_task
delivery_task_order
vehicle
driver
vehicle_location
delivery_track
delivery_sign
```

### 9.9 财务

```text
receivable
receipt
payable
payment
customer_statement
supplier_statement
```

### 9.10 溯源

```text
trace_batch
trace_code
trace_event
```

### 9.11 设备与称重

```text
device
device_binding
weight_record
device_event
```

建议 `weight_record` 至少保留：

```text
device_id
business_type
business_id
business_item_id
gross_weight
tare_weight
net_weight
unit
stable
raw_data
operator_id
weighed_at
created_at
```

`business_type` 初步支持：

```text
PURCHASE_RECEIPT
SORTING
RECHECK
INVENTORY
```

以上为初步领域表，不代表最终表数量。

---

## 10. API 设计规范

统一前缀：

```text
/api
```

示例：

```http
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}

GET    /api/customers
POST   /api/customers

GET    /api/orders
POST   /api/orders
POST   /api/orders/{id}/confirm

GET    /api/purchase-orders
POST   /api/purchase-orders/generate

POST   /api/purchase-receipts

GET    /api/inventories
GET    /api/inventory-movements

GET    /api/sorting-tasks
POST   /api/sorting-tasks/{id}/weigh

GET    /api/devices
GET    /api/devices/{id}
POST   /api/devices/{id}/bind
GET    /api/weight-records
POST   /api/weight-records

GET    /api/delivery-tasks
POST   /api/delivery-tasks/{id}/dispatch

GET    /api/screens/data/overview
GET    /api/screens/delivery/overview
```

统一响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 20,
    "total": 100
  }
}
```

---

## 11. 权限与安全

### 11.1 权限

第一阶段采用 RBAC：

```text
用户
角色
菜单
按钮
API
```

后续加入数据权限：

```text
仓库
客户
供应商
采购员
业务员
部门
```

### 11.2 操作审计

重要操作记录：

- 新增
- 修改
- 删除
- 审核
- 取消
- 订单改价
- 实重修改
- 称重记录人工覆盖
- 设备绑定与解绑
- 设备参数修改
- 库存调整
- 财务操作
- 权限修改
- 登录失败

日志至少包含：

```text
用户
时间
模块
操作
对象ID
请求IP
操作前数据
操作后数据
```

---

## 12. 打印与二维码

系统需要统一打印能力。

打印类型：

- 采购单
- 收货单
- 分拣小票
- 商品标签
- 发货单
- 配送单
- 对账单

二维码类型：

- 采购任务二维码
- 商品溯源码
- 订单二维码
- 业务推广二维码

建议统一建立：

```text
PrintTemplate
QrCodeService
```

避免各模块各自实现。

---

## 13. 商城 / 小程序设计

商城与管理后台共用后端业务能力。

```text
小程序 / H5
     │
     ▼
Spring Boot API
     │
     ├─ 商品
     ├─ 客户价格
     ├─ 营销活动
     ├─ 购物车
     ├─ 订单
     └─ 支付
```

商城重点：

- 客户身份识别
- 不同客户不同商品
- 不同客户不同价格
- 常用菜品
- 再来一单
- 购物车
- 订单
- 账期支付
- 货到付款
- 在线支付
- 余额支付

“智能语音下单”建议作为后续独立能力，不影响商城基础版上线。

---

## 14. 大屏设计

### 14.1 数据大屏

指标：

- 下单金额
- 下单客户
- 客单价
- 复购率
- 发货订单进度
- 商品分类下单金额占比
- 发货商/客户区域分布
- 商品排行
- 客户排行
- 消息中心

### 14.2 配送大屏

指标：

- 客户总数
- 订单金额
- 商品总数
- 今日配送进度
- 司机配送完成情况
- 司机装车完成情况
- 车辆实时位置
- 配送轨迹
- 配送线路

### 14.3 分拣大屏

建议：

- 今日分拣任务
- 已完成
- 待分拣
- 分拣异常
- 分拣人员进度
- 商品分拣排行
- 客户分拣进度
- 实重差异

### 14.4 溯源大屏

建议：

- 今日溯源码数量
- 商品批次数
- 供应商来源
- 采购来源分布
- 溯源扫码次数
- 重点商品追溯链路

---

## 15. 前端目录建议

```text
xsy-scm-web/
│
├─ src/
│  ├─ api/
│  ├─ assets/
│  ├─ components/
│  │  ├─ common/
│  │  ├─ business/
│  │  └─ screen/
│  │
│  ├─ layouts/
│  │  ├─ AdminLayout/
│  │  └─ ScreenLayout/
│  │
│  ├─ pages/
│  │  ├─ dashboard/
│  │  ├─ product/
│  │  ├─ customer/
│  │  ├─ supplier/
│  │  ├─ order/
│  │  ├─ purchase/
│  │  ├─ inventory/
│  │  ├─ sorting/
│  │  ├─ delivery/
│  │  ├─ finance/
│  │  ├─ marketing/
│  │  ├─ traceability/
│  │  ├─ system/
│  │  └─ screen/
│  │
│  ├─ router/
│  ├─ stores/
│  ├─ hooks/
│  ├─ utils/
│  └─ types/
│
└─ package.json
```

---

## 16. 后端目录建议

```text
xsy-scm-server/
│
├─ src/main/java/com/xianshuyuan/scm/
│  ├─ common/
│  ├─ auth/
│  ├─ system/
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
│  ├─ report/
│  └─ screen/
│
├─ src/main/resources/
│  ├─ db/migration/
│  ├─ mapper/
│  └─ application.yml
│
└─ pom.xml
```

---

## 17. 项目仓库建议

```text
xsy-scm/
│
├─ xsy-scm-web
├─ xsy-scm-server
├─ xsy-device-agent
├─ docs
│  ├─ 01-项目设计.md
│  ├─ 02-数据库设计.md
│  ├─ 03-API设计.md
│  ├─ 04-权限设计.md
│  ├─ 05-订单流程.md
│  ├─ 06-采购库存流程.md
│  ├─ 07-部署文档.md
│  └─ 08-电子秤与设备接入设计.md
│
├─ deploy
│  ├─ docker
│  └─ nginx
│
├─ docker-compose.yml
└─ README.md
```

---

## 18. 部署设计

### 18.1 开发环境

```text
React Vite
Spring Boot
PostgreSQL
Docker Compose
xsy-device-agent（需要进行电子秤联调的 Windows 工作站）
```

### 18.2 生产环境

第一阶段建议：

```text
Nginx
  │
  ├─ /        → xsy-scm-web
  └─ /api     → xsy-scm-server

xsy-scm-server
  │
  ├─ PostgreSQL
  ├─ Redis（按需）
  └─ Object Storage

现场称重工作站
  │
  ├─ 浏览器
  ├─ xsy-device-agent
  └─ 电子秤 / 标签打印机 / 扫码枪
```

优先采用单体部署。

暂无必要拆：

```text
order-service
purchase-service
inventory-service
delivery-service
```

业务规模和组织规模达到需要时再拆分。

---

## 19. 迭代规划

### Phase 0：项目初始化

目标：

- 创建前后端工程
- 数据库
- Docker Compose
- CI 基础
- 登录
- Layout
- 统一响应
- 异常处理
- 数据库迁移
- 获取电子秤品牌、型号、通讯协议、驱动/SDK和测试工具
- 完成 `xsy-device-agent` 最小 PoC
- 使用模拟串口/模拟重量数据验证 WebSocket 实时回传
- 确认现场 Windows 工作站、USB/串口、驱动和浏览器环境

---

### Phase 1：基础资料

包含：

- 商品分类
- 商品档案
- 商品规格
- 供应商
- 客户
- 仓库
- 用户
- 角色权限

验收目标：

```text
商品 → 客户 → 供应商 → 仓库
```

基础数据可以正常维护。

---

### Phase 2：订单

包含：

- 客户价格
- 后台录单
- 订单列表
- 订单详情
- 订单修改
- 订单日志
- 订单审核
- 订单汇总

验收目标：

客户可以形成有效销售订单。

---

### Phase 3：采购 + 收货 + 库存

包含：

- 采购需求
- 自动汇总
- 采购单
- 多次收货
- 实重收货
- 电子秤自动采集（设备协议确认后）
- 多次称重与称重记录
- 自动入库
- 库存查询
- 库存流水
- 库存预警

验收目标：

```text
销售订单
→ 采购单
→ 收货
→ 库存
```

闭环跑通。

---

### Phase 4：分拣 + 发货 + 配送

包含：

- 分拣任务
- 实重分拣
- 电子秤自动采集
- 稳定重量判断
- 人工复核 / 重新称重
- 打印
- 分拣误差
- 发货
- 配送任务
- 线路
- 司机
- 车辆
- 签收

验收目标：

```text
库存
→ 分拣
→ 实重
→ 发货
→ 配送
→ 签收
```

闭环跑通。

---

### Phase 5：财务与报表

包含：

- 应收
- 收款
- 应付
- 付款
- 客户账单
- 供应商账单
- 销售明细
- 采购明细
- 客户利润
- 商品利润

---

### Phase 6：数据大屏

包含：

- 数据大屏
- 配送大屏
- 分拣大屏
- 溯源大屏

---

### Phase 7：商城 / 小程序

包含：

- 首页
- 商品
- 客户价格
- 购物车
- 下单
- 订单
- 营销
- 支付
- 再来一单
- 数据查看

---

### Phase 8：高级能力

包含：

- 智能语音下单
- 更复杂线路规划
- 高级经营分析
- 一物一码完整链路
- 供应商协同
- 智能采购建议

---

## 20. 第一个 Sprint 建议

第一个 Sprint 不做全部 ERP，仅跑通第一条技术链路。

### Sprint 1

#### 前端

- React + Vite + TypeScript
- Ant Design
- AdminLayout
- 左侧一级菜单
- 二级菜单
- 商品档案页面
- 商品查询
- 新增商品
- 编辑商品
- 删除商品

#### 后端

- Spring Boot
- PostgreSQL
- Flyway
- 统一返回结构
- 商品分类 API
- 商品 API

#### 数据库

第一批表：

```text
sys_user
product_category
product
supplier
warehouse
```

#### 验收

```text
PostgreSQL
    ↓
Spring Boot
    ↓
REST API
    ↓
React
    ↓
商品档案 ProTable
```

真实数据完整跑通。

---

## 21. 当前阶段暂缓事项

以下功能不建议项目一开始同时开发：

- 微服务拆分
- Kafka
- Kubernetes
- 完整 BI 平台
- 复杂 AI 能力
- 智能语音下单
- 高级线路算法
- 财务总账
- 复杂供应商协同门户
- 所有大屏一次性开发

优先保证核心供应链业务正确。

---

## 22. 后续需要重点确认的问题

正式进入详细设计前，需要和客户进一步确认：

### 商品

1. 标品、非标品的具体定义。
2. 商品规格之间如何转换。
3. 时价由谁维护、什么时候生效。
4. 协议价是否存在有效期。
5. 一个商品多个供应商时采购优先级如何判断。

### 订单

1. 什么时间点锁定销售价格。
2. 实重商品最终金额如何计算。
3. 分拣重量回写后是否允许再次改单。
4. 订单审核规则。
5. 补单业务规则。
6. 退款退货流程。

### 采购

1. 自动生成采购单的拆单规则。
2. 是否允许人工调整采购数量。
3. 是否存在临时供应商。
4. 多次收货如何关闭采购单。
5. 采购价格什么时候确定。

### 库存

1. 是否启用批次。
2. 是否需要效期。
3. 是否允许负库存。
4. 多仓调拨规则。
5. 成本核算具体口径。

### 分拣

1. 称重设备型号与对接方式。
2. 打印机型号与打印协议。
3. 实重误差范围。
4. 超差后的处理方式。
5. 是否存在复核岗位。

### 电子秤 / 现场设备

1. 电子秤品牌、具体型号。
2. 通讯接口：RS232、RS485、USB、USB 虚拟串口、TCP/IP 或蓝牙。
3. 是否提供通讯协议文档。
4. 串口参数：波特率、数据位、停止位、校验位。
5. 设备数据帧格式与示例报文。
6. 数据发送方式：持续主动上传、稳定后上传或主机轮询。
7. 是否提供“重量稳定”标志。
8. 是否支持置零、去皮、毛重、净重读取。
9. Windows 驱动及安装方式。
10. 是否提供 SDK / DLL / Demo 程序。
11. 厂家是否提供协议调试工具。
12. 同一工作站是否会同时连接多台秤。
13. 是否需要同时对接标签打印机、扫码枪、PDA 等设备。

### 配送

1. 司机是否使用小程序 / APP。
2. GPS 位置由什么设备上传。
3. 是否需要自动路线规划。
4. 是否存在固定配送线路。
5. 签收方式：拍照 / 签名 / 验证码 / 二维码。

### 财务

1. 账期计算规则。
2. 客户集团统一结算规则。
3. 退款如何影响应收。
4. 采购退货如何影响应付。
5. 利润统计使用什么成本口径。

---

## 23. 项目设计原则

1. **先单体，后拆分。**
2. **先主链路，后高级能力。**
3. **业务数据必须可追溯。**
4. **库存变化必须有流水。**
5. **订单、采购、库存、分拣、配送状态需要明确状态机。**
6. **涉及价格、重量、库存、财务的修改必须记录操作日志。**
7. **大屏只读取业务数据，不独立维护业务数据。**
8. **商城与后台共用统一商品、价格、订单体系。**
9. **打印、二维码、附件统一封装。**
10. **现场硬件通过 Device Agent 隔离厂商协议，ERP 主业务不直接绑定设备实现。**
11. **称重数据必须可审计，自动采集与人工修改均保留来源和操作记录。**
12. **数据库约束、索引、事务优先于“代码兜底”。**

---

## 24. 当前推荐结论

项目建议按照以下路线启动：

```text
项目：
鲜蔬源智慧供应链管理平台

代码：
xsy-scm

前端：
React + Vite + TypeScript
Ant Design + ProComponents

现场设备：
xsy-device-agent
电子秤 / 标签打印机 / 扫码枪统一适配

后端：
Java 21 + Spring Boot

数据库：
PostgreSQL

架构：
模块化单体

部署：
Docker Compose 起步

开发顺序：
基础资料
→ 订单
→ 采购
→ 收货
→ 库存
→ 分拣
→ 发货配送
→ 财务报表
→ 大屏
→ 商城 / 小程序
→ 溯源与高级能力
```

第一阶段最重要的不是一次性做完所有模块，而是先把：

```text
商品
→ 客户
→ 订单
→ 采购
→ 收货
→ 库存
→ 分拣
→ 配送
```

这一条供应链主流程正确地跑通。
