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
| 报损报溢（V30） | 后端已验证，浏览器待验证 | 报损报溢单、`LOSS_REPORT` / `GAIN_REPORT` 流水、审批状态机（待审核 → 已完成 / 已驳回）、审批乐观锁 |
| 调拨（V31） | 后端已验证，浏览器待验证 | 调拨单、两步式（发出 → 在途 → 收货）、`TRANSFER_OUT` / `TRANSFER_IN` 流水、两仓单位一致性、在途阻塞仓库停用 |
| 阈值预警（V32） | 后端已验证，浏览器待验证 | 阈值配置（独立于余额表）、预警列表（按可用量读时算状态）、按异常默认过滤 |
| 规格转换（V33） | 后端已验证，浏览器待验证 | 转换单（整件拆零 / 组合拆分，跨 SKU 同仓库）、`CONVERT_OUT` / `CONVERT_IN` 流水、两行余额同一事务的全局锁序、审批乐观锁 |
| 移动加权成本（V34） | 后端已验证，浏览器待验证 | `inventory_balance.avg_cost`（Q3 裁决变更）、入库加权 / 出库不变均价但流水带成本、期初回填、余额页均价与金额列 |
| B7 数据大屏（V28） | 后端与前端代码完成，浏览器待验证 | 经营/库存/采购只读聚合、Screen Theme 1920×1080、Header 入口新窗口打开 |
| W6-2 小程序 | 未开始 | 需先处理下方待办 |

## 当前待办

- 引入非管理员业务角色前，处理 F0-DEBT-01：业务附件必须接入权限、归属/关系和 FileService 读取控制。
- 明确正式非管理员角色、数据范围、多角色库存验证和多仓默认选择规则；本次 E2E 临时账号不等同正式业务角色。
- 库存深化剩余项：**已完成**（入库侧、出库/预留、盘点、报损报溢、调拨、阈值预警、规格转换、移动加权成本）。
  下一阶段顺序见
  [`requirements/2026-09-19-需求覆盖与待办清单.md`](./requirements/2026-09-19-需求覆盖与待办清单.md)：
  财务与报表 → 分拣 → 物流配送 → 营销 → 后台补缺 → 订单助手 → 溯源 → 小程序。
- 出库 / 预留 / 盘点 / 报损报溢 / 调拨 / 阈值预警的**浏览器验收尚未执行**（Docker 未运行时
  PostgreSQL 与 Redis 同时不可用，需登录的 E2E 无法进行）。
- 预留的**并发**场景目前只有单线程 IT 覆盖（并发压测待补）。
- 报损报溢**没有消息通知**：驳回后录单人只能靠自己回来看状态。
  **阈值预警同样没有推送**：本波次的「提醒」只是一个可查的列表，推送采购 / 销售待定。
- **在途库存是否需要在余额上可见**（调拨波次的未决事项）：当前在途货不属于任何仓库余额，
  对账时必须把在途调拨单算进去。三种收敛方式见 `decisions.md`。
- F0 cloud/MinIO 环境未配置时，后端 5 项 cloud IT 与 Playwright 7 项 cloud 用例继续跳过；全量入口因此返回 INCOMPLETE，而非 FAIL。

## 追加记录

### 2026-09-20 销售订单录入增强（V35–V36）

- 后台新增原子“创建并推进”入口：纯标品自动确认；含任一非标品时整单待确认，标品实数量由系统回写，非标品等待电子秤联调。
- 新增销售订单 Excel 模板与整批导入：客户编码/SKU 编码精确解析、分组一致性、四位定点、人工改价权限、5 MiB/2000 行/200 单上限、文件 SHA-256 批次幂等与行级错误汇总；任何错误零写入，写入阶段任一失败整批回滚。
- 模板文件固定放在 `xsy-scm-server/sa-admin/src/main/resources/template/sales-order-import.xlsx`；页面“下载 Excel 模板”通过原接口读取 classpath 资源，随后端打包。**已于 2026-09-20 完成真实前后端联调**（见下方「模板下载与导入联调」）。
- V35 为 `sales_order.order_source` 增加 `IMPORT`；V36 增加 `scm:order:import` 权限 642。无新业务表、无外键、无平行订单账。
- 小程序只完成正式接口边界裁决，客户端与 mall 后端仍未实现；未来直接写同一订单聚合，商城提交要求事务内库存校验和预留。
- 复查修复：上传组件保留原始 File，导入中禁止关闭/换文件，成功后禁止再次提交；手工录单仅提供后台录单/补单来源。Excel 错误使用实际行号，并校验模板表头、公式、单工作表、不可售/无价商品、金额溢出与数值实际精度；写入阶段失败回滚后返回订单行定位。
- 当前验证：订单导入/服务/Web/订单及采购迁移定向后端 **38 项通过**，既有订单规则与未定价回归 **29 项通过**，合计 **67 项，零失败/错误/跳过**；覆盖纯标品自动确认、混合订单等待实重、幂等重放，以及第二单失败后订单/明细/地址/日志/幂等记录全部回滚。订单前端测试 **6 项通过**，production build 退出码 0；TypeScript 基线门禁通过（SCM 0 错误、新增 0，全仓既有 1949 项）。
- Playwright 上传弹窗组件验证通过：真实 Vue/Ant Design 上传、multipart 文件、幂等请求头、行/字段错误展示、成功后防重提；**接口响应为模拟数据**，不等同于登录后完整端到端验收。全量后端回归、完整浏览器端到端及电子秤联调未执行。

### 2026-09-20 模板下载与导入联调（真实 PostgreSQL + 打包 JAR + 浏览器）

- 环境：`xsy-v2-postgres` / `xsy-v2-redis` 容器 + **打包后的 `sa-admin-dev-3.0.0.jar`** 跑 18080 + Vite 18081，Playwright 真实浏览器。
- 后端：`GET /scm/order/import/template` 改为一次性读入 classpath 字节再写出（长度取实际字节数），资源缺失时回写 JSON 错误而不是 500；`SmartResponseUtil.setDownloadFileHeader` 补 `filename*=UTF-8''`（RFC 6266），`filename=` 保留为 ASCII 回退。
- 实测响应：`200` + `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8` + `Content-Length: 6192` + 双文件名头，正文首两字节 `504b`（真 ZIP，非错误 JSON）。只读账号返回 `code 30005`、未登录返回 `30007`，均为 `application/json`，前端据此走失败分支、不会落盘成 Excel。
- 打包证明：`BOOT-INF/classes/template/sales-order-import.xlsx` 在 JAR 内；从 `C:/Windows/Temp` 这种与仓库无关的工作目录启动同一 JAR 仍可下载，证明不依赖开发机绝对路径。
- 前端：`getDownload`/`postDownload` 返回 Promise 以便按钮展示下载中状态；错误文案只接受 JSON `msg` 或 200 字符以内的非 HTML 文本。
- 验收：`scm-order.spec.ts` **8/8 通过**。用例 7：页面下载模板 → 填真实客户/SKU → 上传 → 纯标品已确认 + 非标品待称重；混入错误行后整批失败、零写入、展示实际行号与字段；同一 Idempotency-Key 重放不重复建单。用例 8：**原样上传未替换示例行**的模板 → `发现 2 个问题、零写入`，逐行展示实际行号（Excel 第 2 行）、订单标识、字段（客户编码 / SKU编码）与原因（客户编码不存在 / SKU 编码不存在）；**人工单价 + 改价原因**在有 `scm:order:price-override` 的账号下成交且 `lockedUnitPrice=1.2345`、`orderedTotalAmount=2.4690`；**只填人工单价不填改价原因** → `发现 1 个问题、零写入`，提示「填写人工单价时必须填写改价原因」。`OrderWebTest` **6/6**，前端单测 **83/83**，ESLint 改动文件 0 错误，TS 棘轮新增 0、SCM 0（全仓 1947，较基线 1974 下降 27）。
- 模板缺失分支实测：复制 JAR 并删掉其中 `BOOT-INF/classes/template/sales-order-import.xlsx` 后启动，接口返回 `application/json` + `code 30001`「导入模板缺失，请重新部署应用后再试」，不返回空文件、不返回 500。
- 全量后端回归 `mvn -pl sa-admin -am test`：**713 项 / 5 错误**，错误全部落在 `module/scm/inventory/**`（`ScmInventoryBackfillIT` ×3、`ScmInventoryLossGainRollbackIT`、`ScmInventoryStocktakeRollbackIT`），与本次改动零交集。原因是 **V19 Step 4 的对账断言是「全库」校验**（`inventory_balance` 全表 vs 流水全表，不限定本测试数据），而共享 `xsy_v2` 库里残留了其它库存 IT 写入的 `TRANSFER_OUT` 流水；隔离单跑这 3 个 IT 同样失败，属既有的测试数据隔离缺陷，非本次引入。
- 顺带修复：`tools/generate_import_template.py` 的 `verify()` 原先比对裸 XML，遇到 openpyxl + lxml 写出的字符引用（`&#27169;`）会误报乱码；改为反转义后比对，并追加对仓库内交付模板的校验。生成器契约与 `SalesOrderImportService.HEADERS` 逐列一致，模板可重建。
- 未完成项：小程序（W6-2）与电子秤实重回写联调继续延后；F0 云/MinIO 场景未配置，cloud IT 与 cloud Playwright 用例仍跳过；库存那 5 个既有 IT 失败需由库存波次负责人决定是清理测试库还是把对账断言限定到测试数据范围。

### 2026-09-20 移动加权成本（V34）

- V34 是库存深化的**最后一项**：前面七波做完「数量账」，本波次补「金额账」。
  设计口径见 [`requirements/2026-09-20-规格转换与成本核算设计.md`](./requirements/2026-09-20-规格转换与成本核算设计.md)，
  裁决见 `decisions.md` 的「移动加权成本」一节。
- **改变了 Q3 的既有裁决**：Q3 原本定「成本事实由 `movement.unit_cost` 承载、余额表不加 `avg_cost`」。
  移动加权是**顺序相关**的，且出库成本必须在出库那一刻确定 —— 事后从流水反推需要重放整条历史，
  而重放的前提是出库流水已带成本（正是本波次要建立的），鸡生蛋。因此新增 `avg_cost` 作为活状态承载者。
- **唯一会改变均价的路径是采购入库**：`newAvg = (旧量·旧均价 + 入量·入价) / 新量`，`HALF_UP` 到 4 位。
  出库**不改均价**，但流水**必须写当时的均价** —— 这是本波次的核心语义变更：
  出库 / 盘点 / 报损报溢 / 调拨转出 / 转换转出的 `unit_cost` 从 `NULL` 变成有值。
  其它入库（调拨转入 / 转换转入 / 报溢）按现有均价入账（均价不变），是最保守的选择，不凭空创造损益。
- **存量流水的 `unit_cost` 不回填**（Q7 append-only 禁改历史行）。上线后历史出库流水仍是 `NULL` ——
  那不是缺陷，而是「该笔出库发生在成本核算上线之前」的忠实记录，对账时按此解释。
- **期初 `avg_cost` = 最近一次采购入库单价，无历史则 0**。这是唯一有真实采购价格事实支撑、
  且不需引入新输入数据的起点；副作用是历史库存估价偏保守（不虚高）。**属可调整项**，
  要更精确需另开数据修正迁移（V34 已应用不可变）。
- 前端：余额页新增「移动加权均价」与「库存金额」两列；金额在 SQL 里 `ROUND(..., 2)` ——
  `NUMERIC(18,4) × NUMERIC(18,4)` 会得到 scale 8，而 `BigDecimal` 原样输出会让前端看到 `112.00000000`。
- 未覆盖：浏览器验收；调拨 / 转换的成本平移精确化（源仓均价与目标仓不同时按目标仓均价入账，
  属已知近似，已登记未决事项）。

### 2026-09-20 规格转换（V33）

- V33 把「规格转换」纳入范围，这是库存深化的最后一个库存**动作**（只剩移动加权成本）。
  设计口径见 [`requirements/2026-09-20-规格转换与成本核算设计.md`](./requirements/2026-09-20-规格转换与成本核算设计.md)。
- **先更正一处既有错误口径**：此前文档写着「单位转换会推翻 Q13」。**那是错的** ——
  参考项目的规格转换（`ConvertTypeEnum`: SPLIT 整件拆零 / COMBINE 组合拆分）是**跨 SKU** 的
  （源规格 → 目标规格），两个 SKU 各自仍只锁一个记账单位，**Q13 不受影响**。
  真正会推翻 Q13 的是「同一 `(仓库, SKU)` 多单位记账」——需求里从未提出过它，本波次**明确排除**。
- **核心难点：一次转换要在同一事务里改动同一仓库的两行余额**（源 SKU + 目标 SKU）。
  既有六条写入路径每个事务只碰一行，锁序天然成立；这里第一次碰两行，必须显式排序，
  否则「行 1 先锁 A 再锁 B、行 2 先锁 B 再锁 A」会死锁。
  做法：把每行拆成**两条腿**（转出腿 / 转入腿），全部收集后按 `(skuId, 方向)` 统一排序再执行；
  同一 skuId 上**先入后出**，让链式转换（A→B 且 B→C）成立。**IT 里有专门的链式用例**。
- 折算关系与两个单位都由**单据显式声明**，系统不推断折算率（一箱是 9.5 还是 10 kg 取决于
  供应商与批次，猜错会直接污染两边余额）。源单位须等于源 SKU 余额单位（41059）、
  目标单位须等于目标 SKU 既有余额单位（41060），都不做隐式换算。
- 目标 SKU 无余额行时由转换**建立**（入方向才建行）；源 SKU 无余额行则失败（41058）。
- 状态机与报损报溢同构：转换会把两个 SKU 的余额同时改掉，且折算率是人工声明的，
  没有审批等于录单人可以单方面决定「一箱等于多少 kg」。
- 流水类型加 `CONVERT_OUT` / `CONVERT_IN`，快照约束扩到**十方向分组**（每组 5 个）；
  来源类型同样拆两个 —— 与调拨同一原因（同一条明细行两条流水，V19 冻结的唯一索引只认 `(type, itemId)`）。
- 测试：新增 `ScmInventoryConversionIT`（10 例，含链式转换不死锁、两个单位各自比对、
  入方向建行、乐观锁、驳回必填意见、append-only）；同步扩三处契约守卫
  （枚举 10 个 / 来源 9 个 / 错误码 57 个 / 版本清单加 33）。
- 顺带把两处「未落地类型」的占位断言改成 `UNKNOWN_IN`：十个真实类型已全部落地，
  再拿「未实现的业务类型」当反例会每落地一个就要改一次（V29/V30/V31/V33 各改过一次）。
- 验证：干净库 `xsy_scm_cv` 上 `ScmInventory*` 全绿。
- 未覆盖：浏览器验收；移动加权成本核算。

### 2026-09-20 B7 数据大屏（V28）

- V28 把「数据大屏」纳入范围：新增隐藏目录 900 + 查询权限 901（`scm:screen:query`），
  后端新增 `net.lab1024.sa.admin.module.scm.screen` 只读聚合接口
  `/scm/screen/data/business|inventory|purchase`。
- 前端新增独立全屏静态路由 `/screen`（不进 SmartLayout），Screen Theme 1920×1080，
  Header 消息通知左侧新增入口图标，新窗口打开；数据手动刷新。
- 指标全部来自现有业务域（销售订单、库存余额/流水、采购单/收货单、客户/供应商/SKU），
  不维护独立副本；大屏只读。
- 测试：`ScmScreenPermissionMigrationIT`（V28 菜单/授权/代码一致性）、
  `ScmScreenDataIT`（空库零值兜底）。后端 Java 21 环境缺失，Maven 测试未运行；
  前端 lint/typecheck/test/build 已通过。
- 未覆盖：大屏真实浏览器验收；后端集成测试需 Java 21 环境执行。

### 2026-09-19 阈值预警（V32）

- V32 把「阈值预警」纳入范围。它是本阶段**唯一不改变库存**的能力：
  只有一张配置表 + 一个只读的预警列表，没有任何写入路径碰 `inventory_balance`。
- **四处刻意与参考项目不同**（参考项目把 `warn_min`/`warn_max` 放在余额表上、
  按 SKU 配置、状态落库、按现有量比较）：
  1. **阈值单独建表**，不放在 `inventory_balance` 上。余额行是派生状态，只能由流水产生；
     阈值若放在余额表上，配置路径就必须为了写阈值而创建余额行 ——
     那会造出「没有任何流水支撑的余额行」，直接破坏「余额是流水的净和」。
     V19 的契约断言（余额表不含 `warn_min`/`warn_max`）**没有被削弱**，含义反而更明确了。
  2. **按 (仓库, SKU) 配置**：参考项目的「单仓库」是 G-03 的范围限制而非业务规则，
     V2 已有跨仓调拨，两仓的合理下限本就可能不同。
  3. **状态不落库、读时计算**：落库意味着**六条**余额写入路径都要顺手维护它，
     而它完全可由 `(阈值, 可用量)` 推导 —— 落库只会多出一个会漂移的副本。
  4. **基准是可用量（现有量 − 预留量）**：下限的业务含义是「还够不够发货」，
     20 kg 在库但 18 kg 已预留时可用只有 2 kg，必须触发补货预警。
- 预警列表**由配置驱动**（只列配置了阈值的 (仓库, SKU)），否则每个 SKU × 每个仓库都会因为
  「没有余额行 = 0 < 下限」而刷屏；配置了阈值却没有余额行时按 0 计并预警 ——
  这是本能力唯一能表达「还没进过货就要补货」的方式。
- **不设「已读 / 已忽略」状态**：预警只是 `(阈值, 可用量)` 的当前计算结果，
  引入已读会让它与真实库存脱钩。
- 测试：新增 `ScmInventoryWarningIT`（9 例，含「SQL 过滤与 Java 判定等价」的交叉验证）、
  `ScmInventoryConstantTest` 增加预警状态判定用例（边界取等号两侧都钉住）；
  同步扩 `ScmInventoryMigrationIT`（V32 的两条结构性事实 + 区间判据）、
  `ScmPurchaseMigrationIT`（加 32）；前端 `w6-inventory-contract.test.mjs` 新增预警契约。
- **实现期踩到一个坑**：配置编辑最初用 `updateById`，而实体上 `updateStrategy = ALWAYS`
  会把 `created_at` 也写进 SET 子句（更新实体里它是 null）→ 违反 NOT NULL。
  已改为手写 SQL（与全仓其它模块一致），顺带天然支持把边界清空成 NULL。
- 验证：干净库 `xsy_scm_wn3` 上 `ScmInventory*` 全绿；前端 `npm run test` 78/78、
  ESLint 0 错误 / 3 条既有警告。
- 未覆盖：浏览器验收；推送 / 通知机制；单位转换 / 移动加权成本。

### 2026-09-19 调拨（V31）

- V31 把「调拨」纳入范围：扩 `inventory_movement` 类型白名单加 `TRANSFER_OUT` / `TRANSFER_IN`，
  把 `ck_inventory_movement_snap` 扩到**八方向分组**，新建 `inventory_transfer` /
  `inventory_transfer_item` 与 `inventory_transfer_no_seq`，菜单 850–856。
- **参考项目没有任何调拨实现**（`t_stock_adjust` 只有报损 / 报溢 / 盘点调整 / 规格转换），
  因此本波次是 **V2 原创设计**，每条口径都是裁决结果而非沿袭。
- **核心裁决：两步式（发出 → 在途 → 收货）**。① 语义正确 —— 货在卡车上时既不在源仓也不在目标仓；
  ② 并发安全 —— 两步各自只锁一个仓库的余额行，既有锁序纪律完全不用改。一步式要锁跨仓两行，
  锁序就得升级为跨仓排序，而那是六条既有写入路径都要跟着改的事。
- 在途期间这批货**不在任何余额行里**（没有虚拟在途仓），全仓总库存会暂时减少。
  这是两步式的必然结果，已登记为未决事项（是否引入「在途仓」或报表增列）。
- **调拨占两个来源类型**（`TRANSFER_OUT_ITEM` / `TRANSFER_IN_ITEM`）：同一条明细行产生两条流水，
  而 `uk_inventory_movement_source_active` 只认 `(source_document_type, source_document_item_id)`，
  共用一个类型会让第二条插不进去。该索引是 V19 冻结的 Q7/Q11 契约，不能为调拨放宽。
- 两仓记账单位必须一致（41044），不做隐式换算；目标仓从未有过该 SKU 时由本次调入建立余额行
  （入方向允许建行，与采购入库同一取向）。
- **在途调拨阻塞仓库停用**（41009）：调拨波次新增的第四条停用阻塞条件，源仓与目标仓都算；
  草稿不阻塞。
- **本波次的自测抓出一个真实缺陷**：V31 初版的 `ck_inventory_transfer_shipped` 只写了正向判据
  （「已发出必须有发出人」），漏了反向（「草稿不得带发出人」）——
  `ScmInventoryMigrationIT` 的「待审核却带了发出人」用例直接把它抓了出来，已改为双侧判据。
  同一形状的缺陷在 V30 的 `ck_inventory_loss_gain_audit` 上因为一开始就写成双侧而没有发生。
- 测试：新增 `ScmInventoryTransferIT`（15 例）、`ScmInventoryTransferRollbackIT`（1 例，
  `Propagation.NOT_SUPPORTED` 真实回滚，并在 `@AfterEach` 里软删本类建的仓库以免破坏
  「唯一启用仓库」口径）、`InventoryTransferNumberGeneratorTest`（4 例）；
  同步扩 `ScmInventoryConstantTest`（枚举 8 个 / 错误码 41 个 / 调拨状态机 + 方向分组计数）、
  `ScmInventoryMigrationIT`（八方向 CHECK + V31 四条 DB 层判据）、`ScmPurchaseMigrationIT`（加 31）、
  `PurchaseErrorCodeTest`（仓库域 7 → 8，合计 46 → 47）、`ScmInventoryInboundIT`（白名单用例改用
  `CONVERT_IN`，因为 `TRANSFER_IN` 已落地）；前端 `w6-inventory-contract.test.mjs` 新增调拨契约。
- 验证：干净库 `xsy_scm_trf2` 上 `Tests run: 666+ / Failures: 0（除数据大屏）/ Skipped: 5`；
  调拨相关用例全绿。前端 `npm run test` 74/74、ESLint 0 错误 / 3 条既有警告。
- 未覆盖：浏览器验收；调拨并发压测；阈值预警 / 单位转换 / 移动加权成本。

### 2026-09-19 报损报溢（V30）

- V30 把「报损报溢」纳入范围：扩 `inventory_movement` 类型白名单加 `LOSS_REPORT` / `GAIN_REPORT`，
  把 `ck_inventory_movement_snap` 重建为**六方向分组**（改成「按方向分组」的写法：
  每个类型必须恰好落在一个方向组里，漏分类的类型会插不进流水 —— 响亮的失败而不是静默错账），
  新建 `inventory_loss_gain` / `inventory_loss_gain_item` 与 `inventory_loss_gain_no_seq`，
  菜单 840–846（报损报溢页 + 查询 / 新建 / 编辑 / 审批 / 驳回 / 删除）。
- **引入审批状态机**：`PENDING`（创建即待审核）→ `COMPLETED`（写流水、调余额）/ `REJECTED`。
  报损是「把货从账上抹掉」，由同一人录单并批准就失去了制衡，因此「新建」与「审批」是两个独立权限；
  「审批」与「驳回」也拆开（允许主管审批、由另一角色驳回）。
- **比参考项目更严的两处**（刻意的）：参考项目对 `update` / `delete` 没有状态守卫，
  会让「已完成（已写流水）」的单据被改内容或被删掉，账与单从此对不上 ——
  本波次两者都加 `status = 'PENDING'` 守卫，且守卫写在 SQL 的 `WHERE` 里而不只是服务层。
- **审批乐观锁**：审批请求必须携带审批人打开单据时读到的 `version`，不符则 40921。
  审批是一道控制，审批人必须批准自己读到的内容；否则「打开 → 审批」之间被静默改掉数量，
  这道控制就形同虚设。前端因此**不得**在打开审批弹窗时重新拉取单据。
- 报损两条下限：调整后为负（Q10）报 41033；调整后低于已预留量报 41034。
  报溢只增不减，无上限判断。两者都**不建零余额行**（记账单位只能来自余额行，报 41032）。
- 驳回必须填审核意见（41037）：本波次没有消息通知，驳回是唯一把「为什么不行」传达给录单人的渠道。
- 边界：报损 / 报溢与盘亏 / 盘盈**不合并**成同一组流水类型 —— 两者都改变库存但业务含义不同
  （盘亏是「账实不符」的结果，报损是「对已知损耗的申报」），合并后「这个月损耗了多少」
  就无法从流水里直接读出来。
- 测试：新增 `ScmInventoryLossGainIT`（15 例）、`ScmInventoryLossGainRollbackIT`（1 例，
  `Propagation.NOT_SUPPORTED` 真实回滚）、`InventoryLossGainNumberGeneratorTest`（4 例）；
  同步扩 `ScmInventoryConstantTest`（枚举 6 个 / 错误码 30 个 / 报损报溢类型与状态机）、
  `ScmInventoryMigrationIT`（六方向 CHECK + V30 四条 DB 层判据）、
  `ScmPurchaseMigrationIT`（版本清单加 30）；前端 `w6-inventory-contract.test.mjs` 新增报损报溢契约。
- 验证：干净库 `xsy_scm_lg` 上 `Tests run: 645, Failures: 2, Errors: 2, Skipped: 5` ——
  4 项失败**全部**落在并行开发的未提交 `scm/screen`（数据大屏）模块，与报损报溢无关；
  报损报溢相关用例全绿。前端 `npm run test` 71/71、ESLint 0 错误 / 3 条既有警告。
- 落库核对：`flyway latest=30`、0 条失败迁移、菜单 840–846 权限正确、
  两张新表注释 16/16 与 12/12、快照约束六个类型全部落在一个方向组里。
- 未覆盖：浏览器验收；报损报溢并发压测；调拨 / 单位转换 / 阈值预警 / 移动加权成本。

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
