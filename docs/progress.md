# 项目进度

最后更新：2026-09-19

## 当前状态

| 阶段 | 状态 | 记录 |
| --- | --- | --- |
| W0 底座 | 完成 | SmartAdmin 原生系统能力作为 V2 底座 |
| W1 商品 | 完成 | 商品、SKU、分类和价格基础能力 |
| W2 客户与供应商 | 完成 | 客户、供应商及关联主数据 |
| W3 定价 | 完成 | 客户价格和价格历史 |
| W4 销售订单 | 完成 | 订单、明细、状态和操作记录 |
| W5 采购 | 完成 | 采购需求、采购单、多次收货和最小仓库主数据 |
| W5.5 原生功能同步 | 完成 | SmartAdmin 原生功能与 SCM 品牌配置 |
| F0 对象存储 | 完成 | FileService、S3/MinIO 和访问保护 |
| W6-1 库存第一阶段 | 后端与浏览器已验证 | 余额、不可变流水、双入库模式、仓库生命周期、历史回填和只读查询页 |
| 出库 / 预留（V25–V27） | 后端已验证，浏览器待验证 | 独立出库单、`SALES_OUT` 流水、可用量门槛、预留与释放、订单「预留库存」显式动作 |
| 盘点（V29） | 后端已验证，浏览器待验证 | 盘点单、盘盈 / 盘亏流水、差异施加到确认瞬间的账面量、双下限保护 |
| W6-2 小程序 | 未开始 | 需先处理下方待办 |

## 当前待办

- 引入非管理员业务角色前，处理 F0-DEBT-01：业务附件必须接入权限、归属/关系和 FileService 读取控制。
- 明确正式非管理员角色、数据范围、多角色库存验证和多仓默认选择规则；本次 E2E 临时账号不等同正式业务角色。
- 库存深化剩余项：报损报溢、调拨、单位转换、阈值预警、移动加权成本。顺序见
  [`requirements/2026-09-19-需求覆盖与待办清单.md`](./requirements/2026-09-19-需求覆盖与待办清单.md)。
- 出库 / 预留 / 盘点的**浏览器验收尚未执行**（Docker 未运行时 PostgreSQL 与 Redis 同时不可用，
  需登录的 E2E 无法进行）。
- 预留的**并发**场景目前只有单线程 IT 覆盖（并发压测待补）。
- F0 cloud/MinIO 环境未配置时，后端 5 项 cloud IT 与 Playwright 7 项 cloud 用例继续跳过；全量入口因此返回 INCOMPLETE，而非 FAIL。

## 追加记录

### 2026-09-19 盘点（V29）

- V29 把「盘点」纳入范围：扩 `inventory_movement` 类型白名单加 `STOCKTAKE_GAIN` / `STOCKTAKE_LOSS`，
  重建 `ck_inventory_movement_snap` 为**四方向分支**（V19 只有入库分支，V25 补了出库分支），
  新建 `inventory_stocktake` / `inventory_stocktake_item` 与 `inventory_stocktake_no_seq`，
  菜单 830–835（盘点单页 + 查询 / 新建 / 编辑 / 确认盘点 / 删除）。
- **核心口径：差异施加到「确认瞬间的账面量」，不是把账面改写成实盘数。**
  `delta = 实盘量 − 账面量快照`，`after = 确认瞬间账面量 + delta`。
  这样「保存草稿 → 确认」之间发生的收货 / 出库不会被盘点悄悄抹掉；期间无变动时 `after` 恰好等于实盘数。
  两种量都在单据行上留痕，流水的 `before` / `after` 是确认瞬间的真实账面，漂移完全可审计。
- 差异为 0 的行**不写流水**（`quantity` 恒为正，写不出「零差异」流水），但单位快照仍回写。
- 两条下限：调整后为负（Q10）报 41024；调整后低于已预留量报 41025 —— 已预留的货不能被盘点吃掉。
- 边界：盘点**不建零余额行**。记账单位（Q13）只能来自余额行，因此从未入库过的 SKU 不能在盘点里
  凭空盘盈（41023），必须先有入库事实。
- 入口校验：同一 SKU 在盘点单里出现两次直接拒绝（41027），不做静默去重 ——
  重复行会让同一份差异被施加两次，而结果看起来完全正常。
- 测试：新增 `ScmInventoryStocktakeIT`（15 例）、`ScmInventoryStocktakeRollbackIT`（2 例，
  `Propagation.NOT_SUPPORTED` 真实回滚）、`InventoryStocktakeNumberGeneratorTest`（4 例）；
  同步扩 `ScmInventoryConstantTest`（枚举 4 个 / 错误码 20 个）、`ScmInventoryMigrationIT`（四方向 CHECK 断言）、
  `ScmPurchaseMigrationIT`（版本清单加 28/29）；前端 `w6-inventory-contract.test.mjs` 新增盘点页契约。
- 验证：干净库 `xsy_scm_stk` 上 `Tests run: 622, Failures: 2, Errors: 2, Skipped: 5` ——
  4 项失败**全部**落在并行开发的未提交 `scm/screen`（数据大屏）模块，与盘点无关；
  盘点相关用例全绿。前端 `npm run test` 67/67、ESLint 0 错误 / 3 条既有警告。
- 未覆盖：浏览器验收；盘点并发压测；报损报溢 / 调拨 / 单位转换 / 阈值预警 / 移动加权成本。

### 2026-09-19 出库与预留（V25–V27）

- V25 扩流水类型加 `SALES_OUT`，重建方向感知快照约束，`inventory_balance` 加 `reserved_quantity`
  （可用量 = 现有量 − 预留量，`reserved_quantity <= quantity` 由 DB CHECK 兜底），
  新建 `inventory_outbound` / `inventory_outbound_item` / `inventory_reservation`，菜单 803/804/812/813/825–829。
- V26/V27 增加订单「预留库存」显式动作（权限 620）与操作日志类型 `RESERVE_STOCK`。
- **预留不挂在销售订单确认上**：本业务库存在订单确认之后才产生，在确认时校验可用量等于要求「货先到才能接单」。
  实测把预留挂到确认上会让 82 个既有集成测试报 41011。触发点仍未决，见 `decisions.md`。
- 契约守卫抓出两处「硬编码白名单」漏改：查询表单 `@Pattern`（漏了会「数据写进去但筛不出来」）
  与操作日志 `operation_type`（漏了会让业务动作整个失败）。

### 2026-09-18 B1 收口

- V22 为 `purchase_receipt` 增加显式 `DIRECT` / `WAREHOUSE_CONFIRM` 与 `PENDING` / `COMPLETED` 生命周期，扩展 `RECEIPT_PUTAWAY` 审计类型；V23 增加确认入库、仓库启用、仓库停用权限 822/823/824。
- `DIRECT` 确认收货与库存入账保持同事务；`WAREHOUSE_CONFIRM` 确认收货不改库存，putaway 独立事务写余额和 `PURCHASE_IN`，流水 `occurred_at` 等于物理入库时刻。重复、并发和单位不一致均有回滚/防重测试。
- 仓库停用采用严格阻塞：正库存、在途采购单、待入库收货单任一存在即拒绝；停用仓库拒绝新的业务引用，历史记录保留可查。
- 修复全量 E2E 基础设施漂移：W1–W4 账号脚本改用本地 PostgreSQL Docker 容器，W5 full 测试账号继承管理员原生菜单以覆盖 SmartAdmin 页面，验证工具在 Windows 非 UTF-8 控制台打印失败日志时不再崩溃；工具回归 6/6 通过。
- B0+B1 定向浏览器验收 `scm-inventory.spec.ts` 8/8 通过；覆盖 DIRECT、WAREHOUSE_CONFIRM、二次入库发生时刻、重复防重、严格停用及仓库启停往返。W5 采购 9/9、W4 订单 6/6、SmartAdmin 原生 17/17 定向通过。
- 最终全量入口：`.runtime/verify/20260918-184859-883696/summary.json`，后端 `Tests run: 583, Failures: 0, Errors: 0, Skipped: 5`；TS 基线 1974、当前 1953、SCM 0、新增 0；lint 0 错误/3 条既有警告；前端 unit 64/64；production build 通过。
- 最终 Playwright：48 passed、0 unexpected、0 flaky、7 skipped；F0 cloud 环境未配置导致 7 项跳过。全量无 FAILED，因后端 5 项和 E2E 7 项环境 skip 返回 `INCOMPLETE (exit 2)`。

### 2026-09-18

- 工作区收尾：采购单拆分保留原事务与需求重算边界；修复编辑新增采购行时分配缺少 `purchase_order_item_id`，新增真实 PG 回归用例，先复现 NOT NULL 失败再修复。既有测试断言未削弱。
- V15/V19/V20 保留工作区的恢复改动：与 `d7dbea3` 之前的文件逐字节相同，Flyway 校验和分别为 `1541405941` / `602319876` / `1019773777`，与本机数据库一致；未运行 repair，未新增迁移。
- 工程验证：Surefire 显式发现 `*IT`；跨平台入口、类型门禁及其测试纳入 Git 白名单；历史 TS 基线从 Git 恢复到 `xsy-scm-web/quality/`，未以当前错误重新放宽基线。入口遇失败返回 1，未覆盖返回 2。
- `mvn -B -pl sa-admin -am test`：`Tests run: 567, Failures: 0, Errors: 0, Skipped: 5`，`BUILD SUCCESS`。其中 V21 不可改删/截断、库存回填、并发、回滚、入库测试通过；F0 云存储 5 个用例因未启用 cloud ENV 跳过，故验证入口整体为 INCOMPLETE。
- 前端 `npm run lint`：0 错误、3 条既有警告；`npm run test`：63 通过、0 失败、0 跳过。TS 棘轮：历史基线 1974，当前 1953，SCM 0、新增 0；上游全量 typecheck 仍有既有错误，未标记为零错误。
- 验证工具 6 条回归测试通过。构建发现采购日志和仓库页的列设置 `v-model` 绑定常量数组，已改为 `ref`，以支持列设置回写。
- 最终 `npm run build` 通过（1m 30s），两处常量赋值警告消失；仍有上游 `vue3-json-viewer` 图标路径和大包警告。修复后两文件 ESLint、TS 棘轮再次通过。PowerShell 与 Bash 入口均实测在 E2E 服务缺失时返回退出码 2。
- 未覆盖：后端 18080、前端 18081 未运行，E2E 就绪检查返回 INCOMPLETE；MinIO 云端往返未执行；E2E 本机账号工具未纳入 Git，仍需供给。未启动 W6-2、未引入新角色。
- 完成 W6-1 代码交付和静态复核；新增库存账本不可改删约束及相关修复。
- 删除重复的波次文档、旧 UI 指导、截图和过程性记录；文档收敛为本入口、进度和决策三份。
- 前次文档整理没有运行测试或业务命令；当前验证以本日上方工作区收尾记录为准。

### 2026-09-17

- F0 对象存储完成验收；W5.5 SmartAdmin 原生功能同步完成。

### 2026-09-16

- W4 销售订单和 W5 采购完成阶段交付。

### 2026-09-14 至 2026-09-15

- 完成 W0 底座、W1 商品、W2 客户供应商、W3 定价和 V2 根目录整理。
