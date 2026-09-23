# XSY-SCM Wave 1～8 仓库审计问题修复计划

> 用途：交给 AI / Codex / WorkBuddy 直接执行修复。
>
> 审计基线：当前 `main`（审计时 HEAD：`dcf90e4`）。
>
> 对照材料：当前仓库代码、`docs/plan/current-module-optimization-from-sdongpo-v17.4.md`、`docs/progress.md`、各 Wave 已有测试。
>
> 目标：只修复 Wave 1～8 审计发现的问题与缺口，不扩展新业务模块，不改变已经稳定的领域边界和库存事实模型。

---

## 1. 执行总原则

本修复任务不是重新设计系统，也不是继续做新的 Wave。AI 必须遵守以下约束。

### 1.1 开工前必须先做

```text
git fetch
确认 origin/main
确认当前分支基线
确认当前最大 Flyway 版本
读取：
- AGENTS.md
- SMARTADMIN_REFERENCE_RULES.md
- docs/progress.md
- docs/decisions.md
- docs/plan/current-module-optimization-from-sdongpo-v17.4.md
- 对应 Wave 现有测试
```

如当前 `main` 已经有更新，必须基于最新代码重新确认本文问题是否仍存在，不能机械套补丁。

### 1.2 严禁事项

```text
禁止修改已经应用的历史 Flyway
禁止绕过 Sa-Token 权限
禁止创建第二套认证体系
禁止把库存余额当可直接 UPDATE 的普通业务状态
禁止直接修改 / 删除 inventory_movement
禁止为了修页面而复制第二份业务事实
禁止把打印等价成发货 / 出库 / 履约
禁止把采购缺口直接升级成自动采购建议，除非本文明确要求
禁止进入配送 L3 / GPS / 司机端 / 签收等新范围
禁止顺手实现财务、分拣、小程序等未审计模块
禁止把蔬东坡结构机械复制进当前系统
```

### 1.3 Flyway 原则

本文中提到“新增迁移”时，不要固定使用某个版本号。

执行时必须：

```text
扫描 xsy-scm-server/.../db/migration
找到当前最大版本
从 max + 1 连续分配
不得修改 V44 / V45 / V46 / V47 / V48 等已经存在的迁移
```

---

# 2. 修复优先级总表

| 优先级 | Wave | 问题 | 类型 |
| --- | --- | --- | --- |
| P0 | Wave 2A | 采购缺口预览绕过库存读权限 | 权限越界 |
| P0 | Wave 2A | 缺口公式把本批订单自身预留重复扣减 | 业务语义错误 |
| P0 | Wave 6 | 复制历史盘点前端 `pageSize=2000` 与后端 `@Max(100)` 冲突 | 明确功能 Bug |
| P0 | Wave 3 | 历史价格缓存按行 index，SKU 改变后显示旧 SKU 历史价 | 明确 UI Bug |
| P1 | Wave 6 | 盘点快照签名生产环境允许静默使用公开默认密钥 | 安全配置风险 |
| P1 | Wave 4 | 待办卡片 URL 带筛选，但目标列表页未消费筛选参数 | 流程断层 |
| P1 | Wave 4 | 报损报溢驳回消息只能看消息详情，不能进入业务单据 | 流程断层 |
| P1 | Wave 1 | 图片 `PRIMARY/DETAIL` 与最新设计 `GALLERY/DETAIL + is_primary` 漂移 | 模型语义漂移 |
| P1 | Wave 1 | 商品 Excel 当前偏 CREATE-only，未完成最新计划的 UPDATE 导入语义 | 功能缺口 |
| P1 | Wave 5 | 客户打印缺少 `customerStatusFilter` 契约 | 功能缺口 |
| P2 | Wave 2B | 批量少收关单缺真正事务边界外的整批回滚验证 | 测试缺口 |
| P2 | Wave 5 | 打印并发累加没有真实并发 IT | 测试缺口 |
| P2 | Wave 8 | 操作日志详情端只校验日志详情权限，未叠加领域权限 | 权限硬化 |
| P2 | Wave 8 | 敏感信息只在前端展示时脱敏，API/存储层仍可能含原值 | 安全硬化 |
| 验证 | Wave 7 | 当前未发现明确领域错误 | 仅回归 |
| 验证 | 全部 | `docs/progress.md` 记录 E2E 场景已落地但尚未全栈执行 | 验收缺口 |

---

# 3. Wave 2A：采购订单 / 库存缺口预览

这是本轮最优先的修复项。

## 3.1 问题一：采购缺口预览绕过库存读取权限

### 当前问题

当前接口：

```http
POST /scm/purchase/demand/summary-preview
```

控制器目前只要求：

```text
scm:purchase:demand:query
```

但是返回内容包含库存敏感字段：

```text
onHandQuantity
reservedQuantity
availableQuantity
shortageAgainstAvailable
```

而正常库存余额接口：

```http
POST /scm/inventory/balance/query
```

要求：

```text
scm:inventory:balance:query
```

因此现在存在潜在越权路径：

```text
有采购需求查看权
无库存余额查看权
→ 调 summary-preview
→ 仍然可以看到库存余额与预留量
```

### 涉及文件

重点检查：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/controller/PurchaseDemandController.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/service/PurchaseQueryService.java
xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/purchase/PurchaseDemandSummaryPreviewIT.java
```

### 修复要求

接口必须同时要求：

```text
scm:purchase:demand:query
AND
scm:inventory:balance:query
```

建议直接使用 Sa-Token 的 AND 模式，不要在前端做权限补偿。

示意：

```java
@SaCheckPermission(
    value = {
        "scm:purchase:demand:query",
        "scm:inventory:balance:query"
    },
    mode = SaMode.AND
)
```

必须补权限负向测试：

```text
仅有 purchase demand query → 被拒绝
仅有 inventory balance query → 被拒绝
两者都有 → 正常返回
SUPER_ADMIN → 正常返回
```

### 验收标准

- [ ] 无库存查看权不能从采购聚合接口读到库存数量；
- [ ] 前端隐藏按钮不能作为权限保护手段；
- [ ] 不新增第二套库存查询接口；
- [ ] 不降低现有库存 API 权限。

---

## 3.2 问题二：缺口公式重复扣除“本批订单自身预留”

### 当前问题

当前 SQL 逻辑大致为：

```text
订单需求 = SUM(sales_order_item.actual_quantity)
available = inventory_balance.quantity - inventory_balance.reserved_quantity
shortage = max(订单需求 - available, 0)
```

这里的 `reserved_quantity` 是仓库当前总预留量，其中可能包含本次参与预览的这些订单自身已经占用的预留。

反例：

```text
本批订单需求 = 100
仓库现货 = 100
这批订单自身已经预留 = 100

available = 100 - 100 = 0
当前 shortage = 100
```

这会把已经为这些订单预留好的库存再次当成不可用库存，从而把采购缺口放大。

### 涉及文件

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/domain/vo/PurchaseDemandSummaryVO.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/purchase/service/PurchaseQueryService.java
xsy-scm-server/sa-admin/src/main/resources/mapper/scm/purchase/PurchaseDemandDao.xml
xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/purchase/PurchaseDemandSummaryPreviewIT.java
xsy-scm-web/src/views/business/scm/purchase/...
```

同时需要调研当前项目已有的销售订单预留事实和表，不允许新造一套 reservation 事实。

### 修复目标

至少把预留拆成以下概念：

```text
reservedQuantity                  当前仓库该 SKU 总预留
selectedOrderReservedQuantity     本次预览订单集合自身的预留
otherReservedQuantity             其他订单/业务占用的预留
```

推荐关系：

```text
otherReservedQuantity
= reservedQuantity - selectedOrderReservedQuantity

stockAvailableForSelectedOrders
= quantity - otherReservedQuantity
```

再把订单需求与“扣除其他业务占用后的可用库存”对比。

具体 SQL 必须以当前项目现有预留事实为准，不能凭字段名猜实现。

### 字段语义也要同步修正

当前：

```text
shortageAgainstAvailable
```

容易被理解成“建议采购量”。

最新计划已经要求把它降级成“库存对比差额”，建议命名或展示语义使用：

```text
stockComparisonGap
```

或等价清晰命名。

页面文案必须明确：

```text
这是已确认订单与当前库存/预留的对比结果
不是最终净采购建议
```

### 本轮明确不做

以下内容仍然需要业务裁决，不能由 AI 自行决定：

```text
在途采购是否抵扣采购建议
已履约/已出库数量如何从订单需求中扣除
非标品未录实重时是否进入采购口径
多仓订单如何分配到采购仓
是否自动生成采购需求/采购单
```

### 必须新增的测试

至少覆盖：

```text
1. 本批订单无预留，其他订单无预留
2. 本批订单部分预留
3. 本批订单全额预留
4. 存在其他订单预留
5. 本批预留 + 其他预留同时存在
6. reservedQuantity = 0
7. NO_BALANCE
8. UNIT_MISMATCH
9. 多订单同 SKU 聚合
10. 仓库隔离
```

核心反例必须作为固定测试：

```text
需求 100
现货 100
本批自身预留 100
其他预留 0
→ 不得显示库存差额 100
```

---

# 4. Wave 6：盘点效率

## 4.1 问题一：复制历史盘点必然撞分页参数校验

### 当前问题

前端复制历史盘点：

```text
xsy-scm-web/src/views/business/scm/inventory/inventory-stocktake-list.vue
```

`openCopy()` 中当前请求：

```ts
inventoryBalanceApi.query({
  warehouseId: whId,
  pageNum: 1,
  pageSize: 2000
})
```

但后端：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/inventory/domain/form/InventoryBalanceQueryForm.java
```

明确：

```java
@Max(100)
public Long getPageSize()
```

因此当前“复制到新建”会因为 `pageSize=2000` 被参数校验拒绝。

### 修复原则

不要为了这个页面把库存余额通用查询的上限直接改成 2000。

优先方案：

```text
方案 A：前端按 100 分页拉取，直到覆盖来源盘点的全部 SKU
```

更优方案（如果当前库存域已有可复用能力）：

```text
方案 B：增加受控的“按仓库 + SKU ID 集合查询余额”只读能力
```

如果新增 API，必须：

```text
仍要求 scm:inventory:balance:query
限制 SKU 数量上限
不能变成任意大表全量接口
不能返回超出所需字段的大量敏感数据
```

### 推荐实现

若没有现成批量接口，优先先用前端分页修复，改动最小：

```text
1. 收集历史盘点 sourceItems 的 skuId Set
2. pageSize 固定 100
3. 循环查询当前仓库余额
4. 找齐全部 source SKU 后提前停止
5. 到最后一页仍缺 SKU → 明确提示缺失并整单不复制
```

### 验收标准

- [ ] 复制历史盘点不再触发 400 参数错误；
- [ ] 任一来源 SKU 当前余额不存在时整单拒绝；
- [ ] 不静默丢 SKU；
- [ ] 不复制历史账面量、实盘量、差异、version；
- [ ] 实盘量继续留空；
- [ ] 点击“复制到新建”本身不写数据库。

---

## 4.2 问题二：生产环境允许静默使用默认快照签名密钥

### 当前问题

文件：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/inventory/support/StocktakeSnapshotSigner.java
```

当前配置存在默认值：

```text
xsy-scm-stocktake-snapshot-dev-secret-change-in-production
```

如果生产环境忘记配置密钥，系统仍然能启动并使用代码仓库里公开可知的 secret。

### 修复目标

开发 / 测试环境允许使用 dev secret；生产环境必须显式配置，缺失时启动失败。

### 推荐做法

不要只靠注释提醒。

实现一种明确的 fail-fast：

```text
prod profile：
scm.inventory.stocktake.snapshot.secret 为空或仍等于默认 dev 值
→ Application 启动失败
```

可以通过：

```text
@ConfigurationProperties + @Validated
@PostConstruct 环境检查
或专用配置 Bean
```

实现。

同时补：

```text
application-local / application-test
允许 dev secret

生产部署说明
明确配置：SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET
```

具体环境变量映射按项目现有 Spring 配置命名规范执行。

### 测试

至少新增：

```text
local/test：默认开发密钥允许启动
prod + 缺密钥：启动失败
prod + dev 默认密钥：启动失败
prod + 自定义高熵密钥：正常启动
```

---

# 5. Wave 3：销售订单录单效率

## 5.1 历史价格缓存按 table index，SKU 变更后会串数据

### 当前问题

文件：

```text
xsy-scm-web/src/views/business/scm/order/components/order-item-editable-table.vue
```

当前历史价格缓存：

```ts
recentMap[index]
```

加载逻辑又会：

```ts
if (recentMap.value[index]) return;
```

当第 N 行 SKU 改变后，旧缓存没有清理。

例如：

```text
第 1 行 SKU = 苹果
打开历史价 → 缓存 recentMap[0] = 苹果历史价
把第 1 行改成香蕉
再次打开历史价
→ 因 recentMap[0] 已存在，不再请求
→ 显示苹果历史价
```

删除 / 插入中间行导致 index 变化时也可能串缓存。

### 修复要求

缓存 key 不能依赖行序号。

推荐：

```text
customerId + ':' + skuId
```

示意：

```ts
const recentKey = `${props.customerId}:${record.skuId}`;
```

或者 SKU / customer 改变时显式失效当前行缓存。

更推荐前者，因为行重排也不会串。

### 还要处理的状态

当客户发生变化时：

```text
旧 customer + SKU 的历史价不得显示给新 customer
```

因此缓存必须至少包含 customerId。

### 必须新增前端测试

```text
1. 同一行 Apple → Banana，第二次请求 Banana 历史价
2. 删除第一行后第二行上移，不能拿前一行缓存
3. customer A → customer B，同 SKU 必须重新请求
4. 相同 customer + sku 可复用缓存
5. 请求失败不能把 loading 永久卡死
```

---

## 5.2 草稿恢复“防丢”能力需要补真实离页场景

这不是当前最严重 Bug，但要补验收。

现在主要在 Drawer `closeDrawer()` 时写本地草稿。

需要确认以下场景：

```text
浏览器刷新
路由切换
关闭标签页 / 异常退出
```

是否仍能满足计划中的“录单过程防丢失”。

优先不要引入复杂后台草稿表，仍沿用本地草稿方案。

如需要增强，可考虑：

```text
onBeforeUnmount
onBeforeRouteLeave
或对关键表单字段做节流 localStorage 持久化
```

但不能把服务端解析价格、version、锁定价等写进草稿。

---

# 6. Wave 4：业务待办与站内提醒

## 6.1 待办卡片带了筛选 URL，但目标页面没有真正消费

### 当前后端路由

文件：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/dashboard/constant/ScmTodoCardEnum.java
```

示例：

```text
/purchase/purchase-receipt-list?status=CONFIRMED&receiptMode=WAREHOUSE_CONFIRM&putawayStatus=PENDING
/inventory/inventory-loss-gain-list?status=PENDING
/delivery/routes?status=DRAFT
```

首页：

```text
xsy-scm-web/src/views/system/home/components/business-todo-card/home-business-todo.vue
```

直接：

```ts
router.push(todo.route)
```

### 当前断层

审计时发现：

```text
purchase-receipt-list.vue
只处理了 receiptNo 深链，没有完整应用 status / receiptMode / putawayStatus

inventory-loss-gain-list.vue
没有消费 route.query.status

delivery/route-list.vue
没有消费 route.query.status
```

因此：

```text
待办数字 5
点击进去
→ 目标页面不一定显示同样的 5 条
```

### 修复要求

三个目标页面都要支持 URL query 初始化与缓存路由复用。

至少：

```text
purchase receipt：
status
receiptMode
putawayStatus

loss gain：
status

delivery route：
status
```

必须注意 SmartAdmin keep-alive / route cache：

```text
首次进入要应用
从别的对象再次跳进来要重新应用
普通站内切页不能被旧 query 污染
reset 查询时应恢复页面默认值，而不是永久保留旧 deep-link
```

可参考当前 `purchase-receipt-list.vue` 对 `route.name` / `route.query.receiptNo` 的处理方式，扩展成统一模式。

### 必须新增测试

```text
1. 从待办进入待确认入库 → 查询条件完全一致
2. 从待办进入 PENDING 报损报溢 → status=PENDING
3. 从待办进入 DRAFT 配送线路 → status=DRAFT
4. 切换到普通菜单进入时不残留上次 deep-link 条件
5. keep-alive 重用组件时能重新套用新 query
```

---

## 6.2 报损报溢驳回消息不能进入目标业务

### 当前状态

后端已经在驳回事务内调用原生 `MessageService`，并写：

```text
dataId = lossGainId
```

但是个人消息页面目前点击消息只打开消息详情。

重点文件：

```text
xsy-scm-web/src/views/system/account/components/message/index.vue
xsy-scm-web/src/views/system/account/components/message/components/message-detail.vue
xsy-scm-server/sa-base/src/main/java/net/lab1024/sa/base/module/support/message/...
```

### 修复目标

对于“报损报溢被驳回”这类明确业务消息，提供“查看业务单据”入口。

推荐不要靠解析中文标题判断业务类型。

优先检查原生消息模型是否已有可扩展的业务类型 / template 机制；如果已有就复用。

如果当前只有 `dataId`，本轮最小实现可以在消息模板 / 发送参数中补稳定业务标识，但不得新建第二套消息中心。

目标跳转：

```text
/inventory/inventory-loss-gain-list?status=REJECTED&... 或 detail deep-link
```

优先直接进入目标单据详情；如果当前页面不支持 detail deep-link，则进入列表并用业务 ID 精确筛选。

### 权限要求

消息跳转绝不能绕过目标接口权限。

```text
即使旧消息仍在
用户后来失去 scm:inventory:loss-gain:query
→ 目标接口仍应 403
```

---

# 7. Wave 1：商品中心 PCO-2

## 7.1 图片类型模型与最新计划漂移

### 当前问题

现有 V44 已落地 `image_type`，当前语义仍偏：

```text
PRIMARY / DETAIL
```

同时表中又已有：

```text
is_primary
```

这样会存在两个潜在“主图事实”：

```text
image_type = PRIMARY
is_primary = true
```

最新计划已经改为：

```text
image_type = GALLERY / DETAIL
is_primary = 唯一主图事实
```

### 涉及文件

至少检查：

```text
xsy-scm-server/sa-admin/src/main/resources/db/migration/V44__scm_product_image_type.sql
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/controller/ProductImageCenterController.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/service/ProductImageCenterService.java
xsy-scm-web/src/views/business/scm/product/...
所有 image_type / PRIMARY / DETAIL 引用
```

### 修复要求

**不能修改 V44。**

必须新增新迁移：

```text
当前最大 Flyway + 1
```

迁移目标：

```text
PRIMARY → GALLERY
CHECK / 约束同步调整
保留 is_primary 作为唯一主图标记
保证一个商品最多一个 is_primary=true
```

服务层、VO、前端文案统一成：

```text
GALLERY 图集
DETAIL 详情图
is_primary 主图
```

### 回归要求

```text
存量 PRIMARY 数据迁移后仍正常显示
主图切换仍只有一个 primary
DETAIL 不参与主图唯一约束
批量绑定 / 批量删除 / 排序保持兼容
```

---

## 7.2 商品 Excel 仍偏 CREATE-only，未完成 UPDATE 导入设计

### 当前问题

当前实现主要围绕：

```text
ProductSpuAddForm
ProductSpuService.add()
```

完成商品创建导入。

最新计划要求区分：

```text
CREATE
UPDATE
```

并且 UPDATE 不能通过“再导入一个新商品”实现。

### 涉及文件

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/controller/ProductExcelController.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/service/ProductImportService.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/service/ProductImportWriteService.java
相关 Excel DTO / VO / validator
前端商品导入页面
```

### 修复目标

导入必须显式选择或识别模式：

```text
CREATE
UPDATE
```

#### CREATE

维持现有能力：

```text
新增 SPU / SKU
整批校验
整批写入
失败不产生半批数据
```

#### UPDATE

必须做到：

```text
SPU ID / SKU ID 明确
version 明确
版本冲突整批拒绝或按当前计划的既定语义处理
未出现在 Excel 的 SKU ≠ 删除 SKU
空白字段 ≠ 默认清空
需要显式清空的字段必须有明确机制
不能覆盖锁定/系统派生字段
不能绕过已有更新服务的校验
```

AI 不得自行发明“空字符串就是删除字段”规则，必须按最新计划和现有 Form 语义明确设计。

### 建议拆分

如果 UPDATE 设计改动较大，可以：

```text
先完成模型 / API / 预览 / 冲突检查
再完成写入
```

但最终 `docs/progress.md` 不能继续把 Wave 1 标成“完整完成”，除非 UPDATE 模式真正落地并验收。

---

# 8. Wave 5：配送打印追踪

## 8.1 客户打印缺少 `customerStatusFilter`

### 当前状态

后端已有：

```text
customerIds
orderPrintFilter = ALL / PRINTED / UNPRINTED
```

文件：

```text
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/delivery/domain/form/DeliveryPrintCustomersForm.java
xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/delivery/service/DeliveryRouteService.java
```

但是计划要求把两个维度分开：

```text
customerStatusFilter
orderPrintFilter
```

原因：

```text
先选哪些客户状态：ALL / PRINTED / UNPRINTED / PARTIAL
再决定这些客户中哪些订单要打印：ALL / PRINTED / UNPRINTED
```

典型需求：

```text
选择 PARTIAL 客户
只补打这些客户的 UNPRINTED 订单
```

### 修复要求

补充表单字段：

```text
customerStatusFilter = ALL / PRINTED / UNPRINTED / PARTIAL
orderPrintFilter = ALL / PRINTED / UNPRINTED
```

服务端不能信任前端传来的客户状态，必须在持有线路锁后按当前 ACTIVE 订单重新聚合客户状态并筛选。

### 还要保证

```text
RELEASED / 已移出订单不参与聚合
同客户不同地址仍按 stop / 地址分组打印
零有效订单客户不能生成虚假 UNPRINTED 客户
预览后 ACTIVE 集合变化必须拒绝旧请求
```

---

## 8.2 缺少真实并发打印累加测试

当前实现通过线路聚合锁理论上可以避免：

```text
两个并发打印请求同时读 print_count=1
然后都写成 2
导致丢一次累加
```

但现有 IT 主要验证：

```text
同 key 重放
新 key 重打
版本过期
PARTIAL 客户
```

还需要真并发 IT。

### 测试要求

至少：

```text
两个线程
同一线路
不同 Idempotency-Key
同一订单
同时调用 printOrders
```

期望：

```text
两个请求都成功（如果业务允许明确重打）
最终 print_count 精确 +2
无死锁
无 Lost Update
```

同时测试同 key 并发：

```text
最终只计一次
```

---

# 9. Wave 2B：采购效率——主要补测试

当前代码未发现明确领域越界，重点补“整批原子回滚”的真实证据。

## 9.1 批量少收关单事务回滚测试不足

现有 `PurchaseEfficiencyIT` 已说明：测试自己处在外层事务中，所以很难在同一事务内部观察“前一个已经写了但后一个失败后最终是否回滚”。

### 需要补的测试

用独立事务边界执行：

```text
订单 A：合法 PARTIALLY_RECEIVED
订单 B：非法 RECEIVED

调用 batchShortClose(A, B)
→ 整批失败
```

然后在新的事务 / 新查询上下文中断言：

```text
A 仍然是 PARTIALLY_RECEIVED
B 仍然是 RECEIVED
A 没有留下 SHORT_CLOSE 日志
B 没有异常日志
```

版本冲突同样补一例。

不要因为补测试去改动当前已经正确的状态机。

---

# 10. Wave 8：操作日志安全硬化

Wave 8 第一阶段整体成立，本节属于 P2 硬化，不要改成“大重构”。

## 10.1 详情接口需要重新评估领域权限交集

当前列表结构化业务筛选：

```text
support:operateLog:query
+
对应领域 query 权限
```

这是对的。

但是详情接口：

```http
GET /operateLog/detail/{operateLogId}
```

当前主要只检查：

```text
support:operateLog:detail
```

### 风险

未来如果通用日志权限下放给非管理员，用户可能通过直接访问日志 ID 查看自己没有业务读取权的领域日志详情。

### 修复建议

不要简单给详情接口强加所有 SCM 权限。

应该先判断日志对应的业务类型是否可以可靠识别：

```text
PRODUCT / CUSTOMER / DELIVERY_ROUTE
```

若能识别，则叠加对应领域读取权限；不能识别的通用系统日志仍按原日志权限处理。

如果现阶段无法可靠识别，则至少：

```text
在 docs/decisions.md 明确记录风险
在正式下放日志权限前禁止把 support:operateLog:detail 授给普通业务角色
```

优先最小安全修复，不要新建第三套审计表。

---

## 10.2 当前脱敏只发生在前端展示层

文件：

```text
xsy-scm-web/src/views/support/operate-log/operate-log-mask.ts
```

它可以防止 UI 直接展示：

```text
password
token
secret
authorization
apiKey
...
```

但它不能保证：

```text
数据库 t_operate_log 没有原值
API 响应里没有原值
浏览器 Network 里没有原值
```

### 本轮建议

先调研 `@OperateLog` 切面的写入点。

如果切面已有统一参数序列化入口，优先在服务端写日志前做统一敏感字段脱敏。

原则：

```text
后端先脱敏
前端继续二次兜底
```

但必须避免破坏业务对象精确筛选字段，例如：

```text
spuId
customerId
routeId
```

不能因为脱敏重构导致 Wave 8 结构化过滤失效。

如果服务端脱敏改动风险过高，本次可先记录安全债务，但 `docs/progress.md` 不能把“前端展示脱敏”描述成“日志数据已安全脱敏”。

---

# 11. Wave 7：只做回归，不要重写

当前审计未发现 Wave 7 客户 360° 的明显领域错误。

必须保持：

```text
基础资料
最近订单
常购商品
协议价
可售商品
```

仍然围绕同一个 `customerId`，且继续复用各领域事实，不创建：

```text
customer_360_snapshot
customer_frequent_product
```

常购商品继续保持：

```text
CONFIRMED only
SKU + unit 分组
COUNT(DISTINCT order_id)
最近锁定价
不跨单位求和
null 不回退当前价
customer query AND order query
```

修其他 Wave 时必须跑 Wave 7 回归，避免公共订单 / 客户接口改动把它破坏。

---

# 12. 全 Wave 共性验收缺口：真实全栈 E2E 尚未闭环

`docs/progress.md` 当前多处明确写：

```text
E2E 场景已落地待全栈环境执行
```

因此本修复完成后必须统一执行一次完整验收，不能继续只写“场景代码已存在”。

## 12.1 后端

至少执行：

```text
后端编译
相关模块单测
真实 PostgreSQL IT
Flyway 空库从 V1 跑到当前最新版本
受影响既有测试
权限正向 / 反向
并发 / 版本冲突关键测试
```

重点新增：

```text
Wave 2A 双权限
Wave 2A 自身预留反例
Wave 2B 事务外原子回滚
Wave 5 真并发打印
Wave 6 prod secret fail-fast
```

## 12.2 前端

执行：

```text
unit / contract test
type check
eslint
production build
```

重点新增：

```text
Wave 3 历史价缓存切 SKU / 切客户
Wave 4 deep-link query
Wave 6 复制盘点分页
```

## 12.3 Playwright / 浏览器

至少跑以下真实登录场景：

### Wave 1

```text
商品 Excel CREATE
商品 Excel UPDATE（修复后）
图片中心 GALLERY / DETAIL
设置主图
批量维护
```

### Wave 2A

```text
有双权限正常查看
缺库存权限被拒绝
自身预留场景结果正确
```

### Wave 2B

```text
批量少收关单
导出
打印
按商品收货视角
```

### Wave 3

```text
本地草稿恢复
历史订单复用
历史价切 SKU 不串
切客户不串
```

### Wave 4

```text
首页待办数字
点击后目标列表筛选一致
驳回消息
消息进入目标业务
```

### Wave 5

```text
按订单打印
按客户打印
PARTIAL → 只补 UNPRINTED
GET 预览不计次
POST 正式生成计次
```

### Wave 6

```text
导出模板
填实盘导入
快照漂移拒绝
复制历史盘点
复制后实盘为空
```

### Wave 7

```text
5 Tab 正常
权限不足不泄露订单/价格信息
```

### Wave 8

```text
商品 / 客户 / 配送详情进入日志上下文
ID 精确匹配
刷新不丢上下文
查询记忆按用户隔离
```

统一要求：

```text
0 pageerror
0 未处理 promise rejection
0 权限绕过
```

---

# 13. `docs/progress.md` 修正规则

修复完成后必须更新 `docs/progress.md`，但必须据实描述。

禁止继续写：

```text
已完成
```

如果实际只做到：

```text
代码完成，但全栈 E2E 未执行
```

应该明确写：

```text
后端/前端自动化已通过；全栈浏览器验收待执行
```

Wave 1 在 UPDATE 导入和图片类型模型未对齐前，不应继续描述为“最新计划完整完成”。

Wave 2A 在权限和自身预留问题未修前，不应描述为“可作为采购建议”。

Wave 8 前端脱敏未升级为服务端脱敏前，不应写成“日志数据已脱敏”，只能写“页面展示已脱敏”。

---

# 14. 建议修复顺序

严格按下面顺序执行，降低交叉影响：

```text
01. Wave 2A 权限交集
02. Wave 2A 自身预留 / 其他预留口径
03. Wave 6 复制盘点 pageSize Bug
04. Wave 6 prod secret fail-fast
05. Wave 3 历史价格缓存
06. Wave 4 待办 deep-link
07. Wave 4 消息 → 业务单据
08. Wave 1 图片模型迁移
09. Wave 1 UPDATE 导入
10. Wave 5 customerStatusFilter
11. Wave 2B 原子回滚 IT
12. Wave 5 并发打印 IT
13. Wave 8 权限 / 服务端脱敏硬化
14. 全量回归 + Playwright + docs/progress.md
```

每完成一个步骤先跑对应定向测试，不要等到最后一起发现回归。

---

# 15. AI 修复任务指令

可直接把下面内容作为 AI 执行指令。

```text
请修复《xsy-scm-wave1-8-audit-fix-plan.md》中列出的 Wave 1～8 审计问题。

执行要求：

1. 开工先 git fetch，确认最新 origin/main、当前 HEAD、当前最大 Flyway；
2. 先读 AGENTS.md、SMARTADMIN_REFERENCE_RULES.md、docs/progress.md、docs/decisions.md、docs/plan/current-module-optimization-from-sdongpo-v17.4.md；
3. 按文档优先级逐项修，不能顺手实现新业务 Wave；
4. 禁止修改任何历史已应用 Flyway；需要数据迁移时从当前 max + 1 连续新增；
5. 保留 Sa-Token、库存 append-only、幂等、乐观锁、现有状态机和领域边界；
6. 任何聚合查询都不能绕过其底层领域读取权限；
7. 任何页面筛选 deep-link 都必须由后端权限兜底，不能把 UI 隐藏当权限；
8. 任何涉及库存 / 预留 / 采购缺口的公式必须用 BigDecimal / PostgreSQL NUMERIC，不允许前端浮点计算业务事实；
9. 新增/修改 API、页面、Flyway、测试要同步更新；
10. 每修一项先跑定向测试，再进行总回归；
11. 最终必须执行：后端编译、相关单测/IT、前端测试、type check、lint、production build、Flyway 空库验证、真实浏览器 Playwright；
12. 测试失败时先修失败，不允许通过删除测试、放宽断言、跳过用例来“通过”；
13. 完成后更新 docs/progress.md，按真实结果写清：代码 / Flyway / API / 页面 / 测试 / E2E / 剩余风险；
14. 不提交、不 push，除非我明确要求。

最终输出一份修复报告，逐项列出：
- 问题
- 根因
- 修改文件
- 修改方案
- 是否新增 Flyway
- API 变化
- 页面变化
- 新增测试
- 实际测试结果
- 是否还有业务裁决项
- 是否与本文修复要求完全一致
```

---

# 16. 最终完成定义

只有同时满足下面条件，本次“Wave 1～8 审计修复”才能标完成：

```text
P0 问题全部关闭
P1 问题全部关闭或有明确业务裁决记录
P2 测试/安全硬化完成或明确记录技术债
Flyway 连续且空库可执行
后端编译通过
新增/受影响单测与 PG IT 通过
前端测试通过
type check 通过
lint 通过
production build 通过
Playwright 核心流程通过
0 pageerror
权限负向验证通过
docs/progress.md 与实际状态一致
```

以下状态不能算完成：

```text
只写了代码没跑测试
只跑单元测试没跑 PG IT
只跑 API 没跑浏览器
只用 SUPER_ADMIN 验证权限
E2E 文件存在但没有真正执行
通过修改历史 Flyway 解决漂移
通过放宽权限绕过问题
通过删除测试掩盖失败
```

---

## 17. 本次仍然保留为“业务裁决”的事项

AI 不得自行决定以下业务规则：

```text
1. 在途采购是否抵扣净采购建议
2. 已履约/已出库数量如何进入采购需求公式
3. 非标品未实重前是否进入采购
4. 多仓订单需求如何分配采购仓
5. 配送打印是否要追踪“当前内容版本是否打印过”
6. 是否需要完整字段级 before/after 业务审计表
```

如果修复过程中必须依赖这些规则，应停止该子项并在修复报告里标记：

```text
需要业务裁决
```

不要自行猜规则继续实现。
