# 物流配送模块规划方案（静态路线 MVP）

> 日期：2026-09-21  
> 适用仓库：`LuckilyLm/xsy-scm`  
> 规划基线：`main` / Git HEAD `304e0c5bbecab53a57a3dcaeb60a3d820e1aac0d` / Flyway 当前最大版本 `V40`  
> 性质：面向未来的实施规划，不代表功能已经完成。迁移号落地前必须基于当时最新主干重新分配，本文不预占 `V41`。  
> 核心口径：**负责人当前不要求实时配送，不做 GPS 实时轨迹；第一阶段只要求把配送线路规划出来并能在地图上显示。**
>
> 参考网站：https://scm.sdongpo.com/cc_wangyan/superAdmin/viewCenter/v1/deliveryMap
>
> 账号 shudongpo 密码：wy123123

---

## 1. 目标与范围

原产品需求中，物流配送包含三项核心能力：

1. 按客户区域、配送时间、订单数量和金额辅助规划线路；
2. 线路内订单分布可视化和线路轨迹记录；
3. 线路发货单打印。

结合负责人最新口径，本期把“轨迹记录”重新拆解：

- **本期要做的是“计划路线”**：仓库 → 客户停靠点 1 → 停靠点 2 → ...；
- 路线是根据后台人工编排后的停靠顺序展示的**静态计划路线**；
- 不要求车辆实时位置；
- 不要求 GPS 连续采样；
- 不要求轨迹回放；
- 不要求司机端持续上报；
- 不要求实时导航；
- 不要求自动最优排线；
- 不要求 OR-Tools / VRP 求解；
- 不要求距离矩阵。

因此本期目标不是做完整 TMS，而是先形成：

```text
待配送订单
   ↓
人工/辅助组线路
   ↓
设置配送顺序
   ↓
绑定司机、车辆
   ↓
查看静态路线地图
   ↓
打印线路发货单
```

这已经覆盖当前负责人最关心的“有个路线显示就可以”。

---

## 2. 参考网站可借鉴的部分

参考网站展示了以下能力：

- 线路列表
- 线路订单列表
- 线路客户列表
- 分组模板
- 打印范围
- 区域管理
- 司机管理
- 车辆管理
- 配送地图
- 实时监控
- 线路打印
- 客户下单量 / 订单数 / 金额 / 分拣进度等汇总

本项目不机械复制，建议按下表处理：

| 参考能力 | 本项目处理 |
| --- | --- |
| 线路列表 | **借鉴，MVP 必做** |
| 线路订单列表 | **借鉴，MVP 必做** |
| 线路客户列表 | 不单独做一级菜单，合并为“停靠点”Tab |
| 司机管理 | **借鉴，做最小主数据** |
| 车辆管理 | **借鉴，做最小主数据** |
| 配送地图 | **借鉴，MVP 核心** |
| 线路打印 | **借鉴，MVP 必做简单版** |
| 区域管理 | 暂不新建业务区域表，先复用现有省 / 市 / 区结构化地址 |
| 分组模板 | 延后；后续可演化为“常用线路模板” |
| 打印范围 | 延后；MVP 固定一套线路发货单 |
| 实时监控 | **明确不做** |
| GPS 轨迹 | **明确不做** |
| 所有车辆位置 | **明确不做** |
| 自动最优排线 | **明确不做** |
| 司机端权限 / 接单 / 签收 | 后续再做 |

---

## 3. 当前仓库现状

### 3.1 已经具备的基础

当前主干已经完成地图 M0 / M1：

- `V40__scm_geo_region_and_master_location.sql`
- `scm_region` 省 / 市两级区域字典
- `customer`
- `supplier`
- `warehouse`

上述三张主档已经在数据库层具备：

```text
province_code / province_name
city_code / city_name
district_code / district_name
longitude
latitude
geom_crs
```

其中：

- `geom_crs` 已支持 `GCJ02 / WGS84`
- 经纬度要求成对
- 坐标系与坐标存在关系已有数据库约束
- 大屏已经具备真实中国地图
- `ScreenDataController` 已提供地理聚合数据
- 前端已经有统一 ECharts 封装

这意味着物流配送不用再重建“地理数据地基”。

### 3.2 目前还缺的关键点

当前配送域仍是零实现。

仓库中没有正式的：

```text
delivery_route
delivery_stop
delivery_driver
delivery_vehicle
delivery_track
delivery_sign
```

也没有配送页面与配送 API。

另外有几个非常关键的现状：

#### A. 当前业务主档数据库虽然已有经纬度列，但业务表单还没有真正进入 M2 点位补录阶段

目前客户、仓库 Java 实体主要已经接入省市区字段，但经纬度并没有成为正常业务维护入口。

所以：

> **V40 解决的是“能存坐标”，还没有解决“业务人员怎么把准确坐标录进去”。**

这正是静态路线显示的第一个前置。

#### B. 当前销售订单地址快照没有经纬度

`order_address_snapshot` 当前只有：

```text
receiver_name
receiver_phone
address
```

没有省市区和经纬度。

因此路线不能长期直接依赖“客户当前地址”来画。

否则客户主档后续改地址后，历史线路会跟着漂移。

#### C. 当前销售订单状态不应该为了配送随意扩展

当前：

```text
DRAFT
PENDING
CONFIRMED
CANCELLED
```

本期不要为了配送把销售订单状态硬改成：

```text
SHIPPED
DELIVERING
SIGNED
```

配送状态应该首先放在配送域自己管理，避免把现有订单状态机一次改得过重。

#### D. 库存出库已经预留了配送接入点

`InventoryOutboundEntity` 当前源码已经明确说明：

> 发货模块落地后，由发货确认创建并确认出库单即可，无需重做库存出库表结构。

所以物流阶段不要重新做一套扣库存逻辑。

后续“确认发货”应复用现有：

```text
InventoryOutboundService
InventoryCommandService
inventory_movement
inventory_reservation
```

#### E. 分拣模块目前还未开始

按当前项目进度，分拣仍是后续模块。

因此配送设计不要现在把“可配送订单”写死成某个不存在的分拣字段。

建议抽一层：

```java
DeliveryEligibilityPolicy
```

MVP 可以先基于订单已确认等现有事实查询；分拣模块完成后，再把“已分拣完成”接入同一个候选订单查询，不需要推翻配送表。

---

## 4. 推荐的模块边界

建议新增一级模块：

```text
物流配送
```

MVP 菜单保持克制：

```text
物流配送
├── 线路管理
├── 司机管理
└── 车辆管理
```

**不建议**第一版直接照参考网站拆出：

```text
线路
物流排线
区域
配送地图
实时监控
司机绩效
```

原因是当前负责人要的是最小可用路线展示，不需要先搭一个大而全的配送产品。

### 4.1 线路管理

线路列表字段建议：

| 字段 | 说明 |
| --- | --- |
| 配送日期 | 当天计划配送日期 |
| 线路编号 | 系统生成 |
| 线路名称 | 人工填写，例如“南山 1 线” |
| 仓库 | 起点仓库 |
| 司机 | 可空，计划后再分配 |
| 车辆 | 可空 |
| 客户停靠点数 | 去重后的客户 / 地址停靠点 |
| 订单数 | 当前线路订单数量 |
| 订单金额 | 线路订单金额合计 |
| 定位覆盖 | 例如 `8 / 10` |
| 状态 | 草稿 / 已规划 / 已发车 / 已完成 / 已取消 |
| 操作 | 编辑、查看路线、打印、取消等 |

筛选条件：

```text
配送日期
线路名称 / 编号
仓库
省 / 市 / 区
司机
车辆
状态
```

### 4.2 线路详情

采用一页三 Tab：

```text
基础信息
线路订单
停靠点 / 路线地图
```

不必照参考网站把“线路订单”和“线路客户”拆成两个一级菜单。

### 4.3 线路订单

展示：

```text
订单号
客户
配送地址
配送时间
订单金额
商品行数
定位状态
所属停靠点
```

支持：

- 批量加入线路
- 从线路移除
- 按客户 / 区域 / 配送时间筛选
- 同一客户多个订单自动合并到一个停靠点
- 若订单地址不同，则拆成不同停靠点

### 4.4 停靠点

展示：

```text
顺序
客户
收货人
电话
配送地址
订单数
订单金额
经纬度
定位状态
```

支持拖拽调整：

```text
仓库
  ↓
1 客户 A
  ↓
2 客户 C
  ↓
3 客户 B
```

地图按照这个顺序画线。

---

## 5. 本期路线的定义

本期“路线”定义为：

> **根据线路中停靠点的经纬度与人工排序，绘制一条静态 Polyline。**

也就是：

```text
仓库坐标
   ↓
停靠点 1
   ↓
停靠点 2
   ↓
停靠点 3
```

### 5.1 本期不做真实道路导航线

第一期不要调用：

- 驾车路径规划
- 实时路况
- 距离矩阵
- ETA
- 自动重新规划
- 导航 SDK

这样可以大幅减少：

- API 调用量
- 商业地图配额压力
- 服务端复杂度
- 路线算法复杂度

页面上需要明确显示：

> “计划线路，仅表示配送停靠顺序，不代表实时车辆轨迹或导航路径。”

### 5.2 为什么不能只复用当前大屏中国地图

当前 M1 地图适合：

- 全国 / 省级分布
- 市级气泡
- 经营大屏

但生鲜配送通常是同城配送。

如果深圳市内十个客户都落到“深圳市质心”，十个点会全部重叠。

因此真正可用的配送路线至少要有：

```text
客户详细地址 → 精确经纬度
仓库详细地址 → 精确经纬度
```

这也是本期唯一真正必须补的地图基础。

---

## 6. 地图方案：做 M2-Lite，不做完整 M2

当前地图规划里的 M2 包含：

- 地图容器
- 地址选点
- 地理编码
- 底图
- 后续距离能力

本期建议拆成：

```text
M2-Lite：只做点位补录 + 静态底图 + Polyline
```

### 6.1 M2-Lite 要做

客户 / 仓库表单增加：

```text
省市区
详细地址
[定位]
经度
纬度
坐标系
```

正常业务页面不一定直接展示经纬度文本，可以表现为：

```text
地图定位：已定位 / 未定位
```

点击“定位”打开地图选择。

保存：

```text
longitude
latitude
geom_crs
```

### 6.2 地图供应商

当前已有 A / B / C 三条路线调研。

对于当前“只展示路线”的需求，若生产环境允许公网访问，最小实现路径建议优先评估：

```text
高德 JSAPI
```

用途仅限：

- 底图
- Marker
- Polyline
- 地址搜索 / 地理编码
- 手工拖点修正

**不启用**：

- 实时定位
- 驾车导航
- 距离矩阵
- 路线优化

若生产环境不能访问公网，再评估：

```text
MapLibre + 自托管瓦片
```

但不要为了避免一次地图服务选型，就在业务层写大量自研地图代码。

### 6.3 地图组件隔离

建议新建：

```text
xsy-scm-web/src/components/business/scm/map/
├── scm-map.vue
├── scm-map-marker.vue
├── scm-map-picker.vue
├── scm-route-polyline.vue
├── map-provider.ts
└── types.ts
```

业务页面不得直接到处写：

```text
AMap.xxx
```

统一经过 `map-provider`。

这样以后更换高德 / MapLibre 时，不需要重写配送页面。

---

## 7. 数据模型

建议第一期新增 5 张核心表。

### 7.1 `delivery_driver`

司机最小主档。

```text
id
driver_code
driver_name
phone
status
remark
version
deleted
created_at / updated_at
created_by / updated_by
```

第一期不要做：

- 司机 App 账号
- 司机权限矩阵
- 证件上传
- 绩效
- 实时定位状态

### 7.2 `delivery_vehicle`

```text
id
vehicle_no
vehicle_type
load_weight
load_volume
status
remark
version
deleted
created_at / updated_at
created_by / updated_by
```

其中载重 / 体积第一期只是资料字段，不参与自动排线算法。

### 7.3 `delivery_route`

建议字段：

```text
id
route_no
route_name
delivery_date

warehouse_id
warehouse_name_snapshot
warehouse_address_snapshot
start_longitude
start_latitude
start_geom_crs

driver_id
driver_name_snapshot
driver_phone_snapshot

vehicle_id
vehicle_no_snapshot

planned_departure_time
status

outbound_id        -- 后续确认发货接库存出库用，可空
remark

version
deleted
created_at / updated_at
created_by / updated_by
```

状态：

```text
DRAFT       草稿
PLANNED     已规划
DISPATCHED  已发车 / 已确认发货
COMPLETED   已完成
CANCELLED   已取消
```

### 7.4 `delivery_route_stop`

这张表是静态路线展示的核心。

```text
id
route_id
stop_seq

customer_id
customer_name_snapshot

receiver_name_snapshot
receiver_phone_snapshot
address_snapshot

province_code / province_name
city_code / city_name
district_code / district_name

longitude
latitude
geom_crs

planned_arrival_time
remark

version
deleted
created_at / updated_at
created_by / updated_by
```

为什么不直接拿客户表画：

> 路线一旦规划后，必须保存当时使用的地址和坐标快照。客户以后修改地址，历史线路不能跟着变化。

### 7.5 `delivery_route_order`

```text
id
route_id
stop_id
order_id
customer_id

order_no_snapshot
order_amount_snapshot
expect_delivery_time_snapshot

assignment_status    -- ACTIVE / RELEASED

version
deleted
created_at / updated_at
created_by / updated_by
```

建议约束：

- 同一个订单同一时间只能存在一条 `ACTIVE` 配送分配；
- 取消草稿 / 已规划线路时，把对应分配改为 `RELEASED`；
- 已发车后不允许随意释放订单。

---

## 8. 为什么建议拆“停靠点”和“线路订单”

参考网站同时存在：

- 线路订单
- 线路客户

这不是纯 UI 重复。

例如：

```text
客户 A
├── 订单 SO001
├── 订单 SO002
└── 订单 SO003
```

地图上应该只有：

```text
1 个停靠点
```

但业务统计里是：

```text
3 个订单
```

如果只建 `delivery_route_order`，后续会反复在接口里临时做“按地址合并停靠点”。

直接建：

```text
route
  ├── stop
  │    └── order
```

模型更稳定。

---

## 9. 地址与坐标快照策略

推荐优先级：

### 9.1 创建配送停靠点时

取数顺序：

1. 优先取订单地址快照；
2. 如果订单地址与客户当前地址一致，使用客户当前坐标；
3. 如果没有准确坐标，标记为 `UNLOCATED`；
4. 用户必须完成定位后才能把线路从 `DRAFT` 转为 `PLANNED`。

不要偷偷用市级质心冒充客户坐标。

### 9.2 是否立即修改 `order_address_snapshot`

建议做。

当前订单地址快照只有：

```text
address
```

建议未来迁移追加：

```text
province_code / province_name
city_code / city_name
district_code / district_name
longitude
latitude
geom_crs
```

这样：

```text
订单创建时
    ↓
冻结配送地址 + 经纬度
    ↓
配送规划时直接复制到 stop
```

是最干净的链路。

但对于历史数据：

- 不应盲目用客户“现在”的坐标覆盖历史订单；
- 可在 `snapshot.address == customer.address` 时做保守补齐；
- 其余历史订单在路线规划时提示人工定位。

---

## 10. 线路规划方式

### 10.1 MVP：人工 + 系统辅助

本期“辅助规划”不要解释成自动最优排线。

页面提供候选订单池，并按：

```text
配送日期
省 / 市 / 区
期望配送时间
客户
订单数量
订单金额
是否已定位
```

筛选。

支持：

```text
批量勾选订单
    ↓
加入线路
    ↓
自动按客户 + 地址聚合停靠点
    ↓
人工拖拽停靠顺序
```

页面实时计算：

```text
客户停靠点数
订单数
订单金额
已定位数
未定位数
```

这已经满足原始需求中：

> 按客户区域、配送时间、订单数量和金额辅助规划线路。

### 10.2 后续才考虑自动排线

只有负责人明确要求：

- 最短距离
- 最短时间
- 载重约束
- 时间窗
- 多车辆
- 自动分车

时，再进入：

```text
距离矩阵
+ OR-Tools
+ VRP / VRPTW
```

当前完全没必要。

---

## 11. API 设计建议

### 11.1 线路

```http
GET    /scm/delivery/routes
GET    /scm/delivery/routes/{id}
POST   /scm/delivery/routes
PUT    /scm/delivery/routes/{id}
POST   /scm/delivery/routes/{id}/plan
POST   /scm/delivery/routes/{id}/cancel
POST   /scm/delivery/routes/{id}/complete
```

### 11.2 候选订单

```http
GET /scm/delivery/candidate-orders
```

参数：

```text
deliveryDate
provinceCode
cityCode
districtCode
customerId
deliveryTimeFrom
deliveryTimeTo
locatedOnly
keyword
```

### 11.3 线路订单

```http
POST   /scm/delivery/routes/{id}/orders
DELETE /scm/delivery/routes/{id}/orders/{orderId}
```

批量加入时后端负责：

- 校验订单是否可配送；
- 校验是否已经在其他 ACTIVE 线路；
- 创建 / 合并 stop；
- 固化地址与坐标快照。

### 11.4 停靠点顺序

```http
PUT /scm/delivery/routes/{id}/stops/reorder
```

请求：

```json
{
  "version": 3,
  "stops": [
    {"stopId": 101, "seq": 1},
    {"stopId": 103, "seq": 2},
    {"stopId": 102, "seq": 3}
  ]
}
```

必须带线路版本号做乐观锁。

### 11.5 地图

```http
GET /scm/delivery/routes/{id}/map
```

返回：

```text
route
warehouse
stops[]
coverage
```

前端负责画 Marker + Polyline。

### 11.6 司机 / 车辆

```http
GET/POST/PUT /scm/delivery/drivers
GET/POST/PUT /scm/delivery/vehicles
```

---

## 12. 路线地图返回结构

建议：

```json
{
  "routeId": 1,
  "routeNo": "DR202609210001",
  "routeName": "南山1线",
  "status": "PLANNED",
  "warehouse": {
    "name": "深圳仓",
    "longitude": 113.93,
    "latitude": 22.54,
    "geomCrs": "GCJ02"
  },
  "stops": [
    {
      "stopId": 101,
      "seq": 1,
      "customerName": "客户A",
      "address": "深圳市南山区...",
      "orderCount": 2,
      "longitude": 113.94,
      "latitude": 22.53,
      "geomCrs": "GCJ02"
    }
  ],
  "coverage": {
    "total": 10,
    "located": 9,
    "unlocated": 1
  }
}
```

路线展示：

```text
Warehouse → Stop1 → Stop2 → Stop3
```

Polyline 不需要后端存。

前端每次按 stop 顺序生成即可。

---

## 13. 线路状态机

```text
DRAFT
  │
  ├── plan ─────→ PLANNED
  │                 │
  │                 ├── dispatch ─→ DISPATCHED
  │                 │                  │
  │                 │                  └── complete ─→ COMPLETED
  │                 │
  │                 └── cancel ───→ CANCELLED
  │
  └── cancel ─────→ CANCELLED
```

规则：

### DRAFT

允许：

- 编辑基础信息
- 增删订单
- 调整停靠点
- 修改司机车辆
- 修改顺序

### PLANNED

允许：

- 查看
- 打印
- 如业务允许，退回草稿
- 确认发货
- 取消

### DISPATCHED

禁止：

- 删除线路
- 重新分配订单
- 修改配送顺序

可以：

- 查看
- 打印
- 手工完成

### COMPLETED / CANCELLED

终态。

---

## 14. 与现有库存模块的衔接

### 第一阶段

**不要自动扣库存。**

路线规划仅是计划行为。

### 第二阶段增加“确认发货”

点击：

```text
确认发货
```

后端在一个事务边界内：

```text
校验线路 PLANNED
    ↓
读取线路订单商品实发数量
    ↓
按 SKU 聚合
    ↓
调用 InventoryOutboundService 创建出库单
    ↓
确认出库
    ↓
保存 outbound_id
    ↓
线路 → DISPATCHED
```

这样直接复用现有库存出库能力。

禁止在配送模块自己写：

```text
UPDATE inventory_balance
```

### 数量来源

建议优先级留待分拣模块最终裁决：

```text
分拣实重 / 实发量
> sales_order_item.actual_quantity
> ordered_quantity
```

不要在配送实现时擅自决定。

---

## 15. 与分拣模块的衔接

当前分拣还没实现。

所以不要在 SQL 中把候选条件永久写死成：

```sql
sales_order.status = 'CONFIRMED'
```

建议建立查询策略：

```java
public interface DeliveryEligibilityPolicy {
    boolean isEligible(...);
}
```

或在 QueryService 中单独封装：

```java
DeliveryCandidateOrderQueryService
```

等分拣模块落地后，把条件升级成：

```text
订单已确认
+ 分拣已完成
+ 未被有效配送线路占用
```

这样配送主表不用返工。

---

## 16. 发货单打印

原需求明确要求：

> 线路发货单打印。

MVP 不做“打印模板中心”。

先做固定版：

```text
线路编号
线路名称
配送日期
司机
车辆
仓库

停靠顺序
客户
联系电话
配送地址
订单号
商品
数量 / 实重
金额
备注
```

前端：

```text
delivery-route-print.vue
```

使用浏览器打印 CSS 即可。

后续后台管理“单据模板”完成后，再把固定打印升级成配置化模板。

---

## 17. 权限

建议新增：

```text
scm:delivery:route:query
scm:delivery:route:add
scm:delivery:route:update
scm:delivery:route:plan
scm:delivery:route:cancel
scm:delivery:route:dispatch
scm:delivery:route:complete
scm:delivery:route:print

scm:delivery:driver:query
scm:delivery:driver:edit

scm:delivery:vehicle:query
scm:delivery:vehicle:edit
```

地图查看可以复用：

```text
scm:delivery:route:query
```

不单独造一枚 map 权限。

---

## 18. 后端包结构

建议：

```text
module/scm/delivery/
├── constant/
├── controller/
├── dao/
├── domain/
│   ├── entity/
│   ├── form/
│   └── vo/
├── manager/
├── service/
└── support/
```

核心类：

```text
DeliveryRouteController
DeliveryDriverController
DeliveryVehicleController

DeliveryRouteService
DeliveryRouteQueryService
DeliveryCandidateOrderQueryService
DeliveryMapService

DeliveryRouteDao
DeliveryRouteStopDao
DeliveryRouteOrderDao
DeliveryDriverDao
DeliveryVehicleDao
```

不要把配送逻辑塞进：

```text
SalesOrderService
InventoryOutboundService
ScreenDataService
```

通过明确调用边界集成。

---

## 19. 前端目录

```text
xsy-scm-web/src/views/business/scm/delivery/
├── route-list.vue
├── route-detail.vue
├── route-print.vue
├── driver-list.vue
├── vehicle-list.vue
└── components/
    ├── route-form-drawer.vue
    ├── candidate-order-modal.vue
    ├── route-order-table.vue
    ├── route-stop-table.vue
    ├── route-map.vue
    └── route-summary.vue
```

API：

```text
src/api/business/scm/delivery-route-api.ts
src/api/business/scm/delivery-driver-api.ts
src/api/business/scm/delivery-vehicle-api.ts
```

地图通用组件仍放：

```text
src/components/business/scm/map/
```

不要做成配送私有。

---

## 20. 推荐实施分期

## L0：M2-Lite 点位能力

目标：

> 让仓库和客户拥有可用于同城地图展示的真实坐标。

实现：

- 地图 Provider 统一封装
- 客户地图定位
- 仓库地图定位
- 保存 `longitude / latitude / geom_crs`
- 未定位提示
- 补订单地址快照地理字段（推荐）
- 历史数据保守补齐

验收：

- 新增客户能选省市区并完成地图定位
- 编辑后坐标不丢
- 仓库可定位
- GCJ02 / WGS84 不混用
- 经纬度半套值 DB 拒绝
- 不调用实时定位

---

## L1：静态线路 MVP

目标：

> 能从待配送订单组一条线路，并看到路线。

实现：

- driver / vehicle
- route / stop / route_order
- 候选订单池
- 批量加入线路
- 同客户同地址聚合 stop
- 拖拽停靠顺序
- 线路汇总
- 地图 Marker + Polyline
- 定位覆盖提示

验收：

```text
创建线路
→ 加入 5 张订单
→ 自动生成 3 个停靠点
→ 调整顺序
→ 地图按 1/2/3 显示
→ 刷新页面顺序不变
```

---

## L2：规划锁定 + 打印

目标：

> 线路能正式成为一张可执行的配送计划。

实现：

- DRAFT → PLANNED
- 未全部定位不能规划
- 已规划后订单 / 停靠顺序默认锁定
- 简单线路发货单打印
- 取消线路释放 ACTIVE 订单分配
- 操作日志

验收：

- 一个订单不能同时出现在两条有效线路
- 取消后订单重新回候选池
- 已规划线路可打印
- 历史线路不会因客户地址修改而漂移

---

## L3：发货出库衔接

目标：

> 从“计划路线”进一步进入“确认发货”。

实现：

- `确认发货`
- 复用现有库存出库
- route 保存 `outbound_id`
- PLANNED → DISPATCHED
- 手工完成 DISPATCHED → COMPLETED

**仍然不做：**

```text
GPS
实时位置
司机端
轨迹回放
自动导航
签收图片
实时配送进度
```

---

## 21. 明确延后范围

第一版请明确写入范围排除，防止 AI 或开发人员自动扩需求：

```text
OUT OF SCOPE
```

- `delivery_track`
- GPS 采集
- 轨迹回放
- 司机实时定位
- 司机小程序
- 司机接单
- 客户签收
- 签收照片
- 拒收 / 部分签收
- 运费
- 司机提成
- 实时 ETA
- 导航
- 自动最优排线
- 距离矩阵
- OR-Tools
- 路况
- 配送绩效
- 实时监控大屏
- “显示所有车辆当前位置”

这些不是永远不做，而是：

> **当前负责人没有要求，不应进入本轮交付。**

---

## 22. 关键验收场景

至少覆盖：

### 路线组单

```text
订单 A、B 属于同一客户同一地址
订单 C 属于另一客户
```

结果：

```text
订单数 = 3
停靠点 = 2
```

### 防重复分配

订单 A 已在 Route-1 ACTIVE。

再尝试加入 Route-2：

```text
必须失败
```

### 坐标缺失

10 个 stop，9 个已定位：

```text
coverage = 9 / 10
```

允许草稿保存，但：

```text
不能 PLANNED
```

### 顺序

原：

```text
A → B → C
```

拖成：

```text
C → A → B
```

刷新后必须仍是：

```text
C → A → B
```

地图 Polyline 同步变化。

### 地址修改

路线规划后修改客户主档地址。

历史线路：

```text
地址 / 坐标不变化
```

### 线路取消

DRAFT / PLANNED 取消：

```text
route_order ACTIVE → RELEASED
```

订单重新进入候选池。

### 发货

若后续启用 L3：

```text
PLANNED
→ 创建并确认 inventory_outbound
→ route.outbound_id 有值
→ DISPATCHED
```

不得重复发货。

---

## 23. 测试建议

后端集成测试：

```text
ScmDeliveryMigrationIT
DeliveryRouteServiceIT
DeliveryRouteWebTest
DeliveryRouteConcurrencyIT
DeliveryDispatchInventoryIT   -- L3
```

重点：

- 状态机
- 乐观锁
- 同订单防重复分配
- stop 顺序唯一性
- route snapshot 不漂移
- 取消释放
- 坐标覆盖
- 坐标系一致
- 发货幂等

前端：

```text
e2e/scm-delivery-route.spec.ts
test/delivery-route-model.test.mjs
```

浏览器 E2E：

```text
新建线路
加入订单
拖拽
地图
保存
刷新
打印
取消
```

---

## 24. 当前最重要的两个技术决策

### 决策 D1：静态路线到底画什么

推荐第一版：

```text
Marker + 直线 Polyline
```

不要做道路贴合。

页面标明：

> 计划路线，仅表示停靠顺序。

这与负责人“有个路线显示就可以”的口径最匹配。

### 决策 D2：地图底图用什么

如果生产网络允许访问地图公网：

> 优先落高德 M2-Lite。

如果不允许：

> 再评估 MapLibre 自托管。

这项决定只影响地图 Provider 层，不应该影响配送业务表、API 和状态机。

---

## 25. 推荐最终一期界面

### 线路列表

```text
配送日期 | 线路编号 | 线路名称 | 仓库 | 司机 | 车辆
停靠点 | 订单数 | 金额 | 定位覆盖 | 状态 | 操作
```

操作：

```text
详情
查看路线
编辑
打印
更多
```

### 线路详情左侧

```text
线路信息
司机
车辆
汇总
停靠点列表
```

### 线路详情右侧

地图：

```text
仓库 [0]
  \
   [1 客户A]
       \
        [2 客户B]
             \
              [3 客户C]
```

Marker 点击展示：

```text
客户
地址
电话
订单数
订单金额
```

这一版已经能明显达到参考网站“配送地图 / 线路”的产品效果，但实现成本远低于完整实时配送。

---

# 最终建议

当前不要按旧方案直接进入“完整 M3 配送域 + GPS 轨迹 + OR-Tools”。

应该把现有规划重新切成：

```text
V40 / M0   地理数据地基          ✅ 已完成
M1         中国地图大屏          ✅ 已完成

M2-Lite    客户/仓库精确定位
    ↓
L1         静态配送线路
    ↓
L2         线路锁定 + 发货单打印
    ↓
L3         对接现有库存出库

Realtime   GPS / 司机端 / 轨迹   暂缓
Optimizer  自动排线 / OR-Tools   暂缓
```

这一方案能最大程度复用当前仓库已经完成的：

- V40 地理字段
- 客户 / 仓库主档
- 销售订单
- 订单地址快照
- 库存预留
- 库存出库
- 操作日志
- 权限体系
- 地图前端基础

同时避免为了“路线显示”提前建设一整套实时 TMS。
