# 物流配送静态线路实现（L0–L2）

日期：2026-09-21。实现分支：`dev-map`。需求来源：[静态路线规划](plan/logistics-delivery-static-route-plan.md)。

## 交付范围

- L0：客户、仓库维护经纬度与坐标系；共享地图定位组件；订单创建时按完全一致的地址复制客户地理信息。
- L1：司机 / 车辆主档、线路创建、候选订单筛选、批量组单、按客户与地址聚合停靠点、拖动或按钮排序、地图 Marker + 直线 Polyline。
- L2：`DRAFT → PLANNED`、草稿 / 已规划取消、释放订单分配、固定线路发货单打印、SmartAdmin 操作日志和权限。
- 未进入 L3：没有确认发货、出库扣库存或完成配送入口。`outbound_id` 和后续状态仅为模型预留。实发数量来源仍待分拣规则裁决。
- 未实现 GPS、轨迹回放、导航、自动最优排线、司机端、签收与运费。

## 数据与事务

新增 V42（五张配送表、订单地址地理快照及坐标完整性约束）与 V43（菜单及权限）。原编号 V41 / V42：分配时远端 main 与本地 HEAD 均为 `304e0c5bbecab53a57a3dcaeb60a3d820e1aac0d`，但合并回主干时本地分支的 `V41__scm_product_image_drop_file_url` 已被真实开发库应用，按「保留已应用版本号」先例（V17/V18 勘误）将本模块迁移重排为 V42 / V43；V1–V41 未改。

- 不建外键。司机编码、车牌、线路停靠顺序和有效订单分配有数据库唯一索引。
- 经纬度、坐标系必须同时存在或同时为空；坐标范围和精度在表单 / 数据库校验。V40 中若存在有坐标但没有 CRS 的非标准存量，V42 会拒绝升级，必须先核实真实坐标系，不能猜测补值。
- 线路是事务锁边界。修改需携带线路 `version`；组单和规划按订单 ID 升序锁销售订单，ACTIVE 部分唯一索引兜底跨线路重复分配。
- 候选条件集中在 `DeliveryEligibilityPolicy`，当前为已确认、未删除且没有 ACTIVE 分配。分拣落地后从此处扩展，保持查询与命令口径一致。
- 同客户、完全相同地址合并一个停靠点，联系人采用首次组单的订单快照；不同地址不合并。线路最多 500 张订单。
- 地址优先取订单快照。历史订单缺坐标时，只有与客户当前非空地址完全相同才借用其准确点位；不使用市级质心。
- 线路起点在创建 / 编辑基础信息时复制仓库快照；停靠点在首次组单时复制订单信息。后续修改主档不会回写历史路线。仓库补定位后，须编辑草稿线路重新保存以刷新起点。
- 确认规划要求至少一张订单、所有点位完整，且起点与所有停靠点坐标系一致；已规划不能编辑订单、坐标或顺序。司机车辆允许暂未分配，已选择的主档必须启用。
- 移除订单：分配改为 RELEASED 并软删除，最后一张订单移除时软删除对应停靠点；重新压紧顺序。取消线路：全部 ACTIVE 改 RELEASED，保留可见订单历史和金额快照。
- 销售订单状态机保持原样；已确认订单原有规则已禁止取消（40960），配送不扩展销售订单状态。
- 线路金额采用订购金额快照。打印分别展示订购数量、实重、订购金额与结算金额；缺失实重显示“—”，不推导发货数量。规划与打印均不写库存。
- 编号为 `DR + 配送日期 + 全局递增序号`，序号不按日重置。

## 页面与权限

菜单：物流配送 → 线路管理 / 司机管理 / 车辆管理。线路详情为基础信息、线路订单、停靠点 / 路线地图三个 Tab。

V43 使用菜单 1000–1003、权限 1011–1016 / 1021–1022 / 1031–1032，仅授现有 SUPER_ADMIN。没有新增非管理员业务角色，不改变 F0-DEBT-01 前置条件。

权限为 `scm:delivery:route:{query,add,update,plan,cancel,print}`、`scm:delivery:driver:{query,edit}`、`scm:delivery:vehicle:{query,edit}`。主档选择器复用线路查询权限；查看地图复用线路查询权限。写入与打印接 SmartAdmin `@OperateLog`，组单、移除、取消记录操作原因。

## 高德配置

用户已确认：先完成可配置接入，Key 与安全代理稍后补。配置样例见 [delivery.env.example](../xsy-scm-web/delivery.env.example)。将相关项放入实际运行模式对应的本地环境文件，重新启动 Vite 或重新构建前端：

```dotenv
VITE_AMAP_KEY=Web_JSAPI_应用Key
VITE_AMAP_SERVICE_HOST=https://你的地图代理域名/_AMapService
```

生产采用安全代理，把安全密钥留在代理服务端；开发环境可选 `VITE_AMAP_SECURITY_CODE`，该变量与其他 `VITE_*` 一样会进入浏览器代码，不能当作服务端秘密。优先级为 `SERVICE_HOST` 高于 `SECURITY_CODE`。本次不创建地图账号、不购买服务、不填入任何真实凭据。

配置与代理规则参考[高德 JSAPI 安全密钥说明](https://developer.amap.com/api/javascript-api-v2/guide/abc/jscode)，地址搜索参考[高德地理编码说明](https://developer.amap.com/api/javascript-api-v2/guide/services/geocoder)。

- 高德对象仅存在于共享 `map-provider.ts`，业务页不直接引用 SDK。
- 未配置或加载失败时明确提示；手工坐标、线路组单、排序、规划和打印仍可使用。
- 高德选点保存 GCJ02；允许手工录入已知 WGS84 点位。WGS84 仅在地图显示时调用供应商转换，原始快照不改写、不直接重新标为 GCJ02。
- 不同坐标系不可混合确认规划；需补录成相同坐标系。
- 地址改变自动清空旧定位。搜索有多个匹配时要求补全地址，不静默选择第一个。
- 地图按人工顺序连接相邻已定位点，缺失点会断线；不跨过缺失点制造完整路线假象。
- 界面标明“计划线路，仅表示配送停靠顺序，不代表实时车辆轨迹或导航路径”。

## 接口

均采用 SmartAdmin `ResponseDTO` / `PageResult`。GET 列表使用 `pageNum` / `pageSize`，每页最多 500。

| 方法与路径 | 用途 |
| --- | --- |
| GET /scm/delivery/routes | 线路分页；日期、关键字、仓库、司机、车辆、状态、省市区筛选 |
| POST /scm/delivery/routes | 创建草稿 |
| GET /scm/delivery/routes/{id} | 详情与汇总 |
| PUT /scm/delivery/routes/{id} | 草稿基础信息，携带 version |
| POST /scm/delivery/routes/{id}/plan | 规划锁定，携带 version |
| POST /scm/delivery/routes/{id}/cancel | 取消，携带 version 与 reason |
| GET /scm/delivery/candidate-orders | 候选池；日期、区域、客户、时间段、金额、商品行数、已定位筛选 |
| POST /scm/delivery/routes/{id}/orders | 批量加入，version / orderIds / reason |
| DELETE /scm/delivery/routes/{id}/orders/{orderId} | 移除，请求体含 version / reason |
| PUT /scm/delivery/routes/{id}/stops/reorder | 完整 stopIds 有序数组及 version；不能缺项或重复 |
| PUT /scm/delivery/routes/{id}/stops/{stopId} | 位置三元组、到达时间、备注及线路 version |
| GET /scm/delivery/routes/{id}/map | 与详情同构：route 含起点与覆盖数，stops 含有序点位，orders 含订单关联 |
| GET /scm/delivery/routes/{id}/print | 已规划线路打印数据：detail + items |
| GET / POST /scm/delivery/drivers | 司机分页 / 创建与编辑（编辑带 id / version） |
| GET / POST /scm/delivery/vehicles | 车辆分页 / 创建与编辑（编辑带 id / version） |
| GET /scm/delivery/options/{warehouses,drivers,vehicles} | 线路维护使用的启用主档选择项 |

错误码使用未占用的 41100–41109；版本冲突沿用 40921。对任意旧版本失败，应刷新线路后重新操作。

## 验证记录

- 后端 Maven compile、前端生产构建已通过。
- Docker Desktop PostgreSQL 独立库中 `DeliveryRouteServiceIT`：2/2 通过（0 failure / 0 error），Flyway 至当时的 V42（= 重排后的 V43）。测试库已清理，日常开发库未迁移。
- 前端全库类型检查有 1946 条诊断；本次修改 / 新增文件 0 条。UI 静态检测无 findings。不声称全库类型检查通过。
- 未执行全量回归、并发压力测试、浏览器 E2E、真实高德联调和实体打印机验证。
