# 项目进度

最后更新：2026-09-22

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
| 出库 / 预留（V25–V27） | 写流程 E2E 已验证（并发压测待补） | 独立出库单、`SALES_OUT` 流水、可用量门槛、预留与释放、订单「预留库存」显式动作 |
| 盘点（V29） | 写流程 E2E 已验证 | 盘点单、盘盈 / 盘亏流水、差异施加到确认瞬间的账面量、双下限保护 |
| 报损报溢（V30） | 写流程 E2E 已验证 | 报损报溢单、`LOSS_REPORT` / `GAIN_REPORT` 流水、审批状态机（待审核 → 已完成 / 已驳回）、审批乐观锁 |
| 调拨（V31） | 写流程 E2E 已验证 | 调拨单、两步式（发出 → 在途 → 收货）、`TRANSFER_OUT` / `TRANSFER_IN` 流水、两仓单位一致性、在途阻塞仓库停用、**成本随货平移（V37 修正转入清零）** |
| 阈值预警（V32） | 列表页浏览器已验证，推送未做 | 阈值配置（独立于余额表）、预警列表（按可用量读时算状态）、按异常默认过滤；「提醒」目前只是可查列表 |
| 规格转换（V33） | 写流程 E2E 已验证 | 转换单（整件拆零 / 组合拆分，跨 SKU 同仓库）、`CONVERT_OUT` / `CONVERT_IN` 流水、两行余额同一事务的全局锁序、审批乐观锁、**转入按转出腿总成本折算（V37）** |
| 移动加权成本（V34） | 后端与浏览器已验证 | `inventory_balance.avg_cost`（Q3 裁决变更）、入库加权 / 出库不变均价但流水带成本、期初回填、余额页均价与金额列、**V37 重放流水修正零成本余额** |
| B7 数据大屏（V28） | 后端已验证 + 浏览器已验证（V1 视觉版） | 经营/库存/采购/趋势四只读聚合、Screen Theme 1920×1080 等比缩放、10 面板 + 3 图趋势带、组件化拆分、Header 入口新窗口打开 |
| 商品中心 PCO-1 主档增强（V38–V39） | 后端与浏览器已验证 | 主档扩展字段与助记码搜索、计量单位 / 商品标签字典、列表高级筛选、批量上下架 / 改分类 / 打标签、商品与字典删除保护；Excel 与图片中心属 PCO-2 |
| 商品中心 PCO-2 导入导出 + 图片中心（V44–V45） | 后端 IT + 前端单测 / 类型 / 构建已验证；E2E 场景已落地待全栈环境执行 | Excel 模板下载 / 整批事务导入 / 按条件导出、图片中心（`image_type` 图集分组、单商品与按文件名批量维护、主图唯一）、独立菜单与权限；见追加记录 2026-09-22 |
| 采购订单缺口预览 Wave 2A（无迁移） | 后端 IT + 前端契约 / 类型 / Lint 已验证；E2E 场景已落地待全栈环境执行 | 只读「订单汇总 / 库存缺口预览」并入采购需求页 Tab、`POST /scm/purchase/demand/summary-preview` 复用 `generate()` 取数口径、缺口与可用量后端算好、0 迁移 0 菜单变更；见追加记录 2026-09-22 |
| 采购操作效率 Wave 2B（无迁移） | 后端单元 4/4 + IT 6/6 + 前端契约 8/8 / 类型 / Lint / 构建已验证；E2E 场景已落地待全栈环境执行 | 批量少收关单（整批原子、version 冲突显式拒绝）、采购单后端算列导出 + 前端本地记忆列勾选、纯前端打印、收货「按单据 / 按商品」双视角只读工作台；0 迁移 0 新权限，复用 `scm:purchase:short-close` / `:query` / `:receipt:query`；见追加记录 2026-09-22 |
| 地图 M0 地理数据地基（V40） | 后端与浏览器已验证 | `scm_region` 省市两级字典（34 省 + 414 市，带区划质心 GCJ-02）、三张主档六列省市区快照 + `longitude/latitude/geom_crs`（成对与 CRS CHECK、市级部分索引）、迁移内保守地址解析回填、客户 / 供应商 / 仓库表单升级为省市区三级 |
| 地图 M1 大屏真实地图（无迁移） | 后端与浏览器已验证 | 官方省界 GeoJSON 存档进仓库、`GET /scm/screen/data/geo` 只读聚合（省级在 Java 侧由市上卷）、省界着色 + 市级气泡 + 未归属覆盖度；流向层 `lines` 未做 |
| F0-DEBT-01 FA-0 附件分级与写侧收口（V41） | 后端 + 浏览器已验收，读侧未闭合 | 商品图片改上传 `public/image/`（新增 `PUBLIC_IMAGE(5)`）、`product_image` 新增/换绑只能引用公开前缀（`40038`，存量行沿用原 key 放行）、删除 `product_image.file_url` 改为按 `file_key` 现算；`getFileList()` 仍无逐用户过滤 |
| 物流配送 L0–L2（V42–V43） | 已实现，编译 / 构建 + 定向集成验证通过；浏览器与真实地图待验收 | 客户 / 仓库定位、订单地理快照、司机车辆、静态排线、规划锁定、取消释放、固定打印；高德配置待补，L3 未开始 |
| W6-2 小程序 | 未开始 | 需先处理下方待办 |

## 当前待办

- **F0-DEBT-01**：写侧绑定期限权与商品图公开化已于 2026-09-21 收口（FA-0 / V41）。
  剩余 FA-1～FA-3：**受控批量读取**（`getFileList(keys, user)`）、`scm_file_relation` 落地、
  OA `FileKeyVoSerializer` 收口与代码生成模板、存量商品图搬运到 `public/image/`。
  引入任何非管理员业务角色前必须完成读侧，方案见
  [`plan/attachment-asset-grading-and-file-access-plan.md`](./plan/attachment-asset-grading-and-file-access-plan.md)。
- 明确正式非管理员角色、数据范围、多角色库存验证和多仓默认选择规则；本次 E2E 临时账号不等同正式业务角色。
- 库存深化剩余项：**已完成**（入库侧、出库/预留、盘点、报损报溢、调拨、阈值预警、规格转换、移动加权成本）。
  下一阶段顺序见
  [`requirements/2026-09-19-需求覆盖与待办清单.md`](./requirements/2026-09-19-需求覆盖与待办清单.md)：
  财务与报表 → 分拣 → 物流配送 → 营销 → 后台补缺 → 订单助手 → 溯源 → 小程序。
- 出库 / 预留 / 盘点 / 报损报溢 / 调拨 / 阈值预警的**列表页**浏览器验收已于 2026-09-20 执行
  （与余额 / 流水 / 规格转换 / 数据大屏共 11 页全绿、0 pageerror）。
  **五条写流程 E2E 已于 2026-09-20 覆盖**（出库确认、盘点确认、报损报溢审批、调拨发出/收货、
  规格转换审批 + 只读账号越界），见下方「调拨 / 转换成本平移修复（V37）与库存写流程 E2E」。
- ~~调拨转入的成本为 0~~ **已修（2026-09-20，V37）**：转入成本改为取**同一明细行的转出腿
  `unit_cost`**，并与采购入库同样加权；规格转换的转入腿改为按转出腿**总成本 ÷ 目标数量**折算。
  开发库被清零的 3 行已由 V37 重放流水重算（6.20 / 132.00 / 2.60 回正）。
  链式转换的期初成本基准在审批开始时一次取齐并预解，环状引用按期初均价收敛且不写缓存。
- 预留的**并发**场景目前只有单线程 IT 覆盖（并发压测待补）。
- 报损报溢**没有消息通知**：驳回后录单人只能靠自己回来看状态。
  **阈值预警同样没有推送**：本波次的「提醒」只是一个可查的列表，推送采购 / 销售待定。
- **在途库存是否需要在余额上可见**（调拨波次的未决事项）：当前在途货不属于任何仓库余额，
  对账时必须把在途调拨单算进去。三种收敛方式见 `decisions.md`。
- F0 cloud/MinIO 环境未配置时，后端 5 项 cloud IT 与 Playwright 7 项 cloud 用例继续跳过；全量入口因此返回 INCOMPLETE，而非 FAIL。
- **地图 M2（底图 / 选点 / 地理编码）待路线裁决**，A 高德 / B 开源 MapLibre / C 天地图三选一，
  判据是**部署环境能否连公网**与**是否愿意为企业实名认证与商用授权付费**（配额与报价口径见
  `requirements/2026-09-21-地图模块分期实施方案.md` §5、§8）。M0/M1 不依赖它。
  在 M2 之前 `longitude` / `latitude` **全库为空**，地图上的位置一律是区划质心，不是单位地址。
- **地图 M0 的存量解析覆盖率低是预期结果**（客户 23 活动 / 10 已归属，供应商 20 / 7，启用仓库 2 / 1）：
  解析规则刻意保守，宁缺勿错。剩余未归属行需要人工在表单里补选省市区，
  大屏已把「未归属」量显性化，不会静默低估业务量。
- **地图 M1 未做流向层**（供应商 → 仓库 → 客户 `lines`）：需要跨域按单聚合，且没有真实点位时
  只能画省 → 省的粗线；等 M2 有坐标后再评估。
- **大屏各面板的数量类指标是「跨单位求和」**：库存总量 / 库存趋势 / 今日出入库把 kg、箱、把、颗、托、件
  直接相加，量纲不统一。这是既有口径（V28 起就是这样，设计稿也接受），但**不能当作重量或件数解读**，
  只适合看趋势与相对大小。若要精确，需要按单位分组或统一折算成标准单位。
- **大屏环比（较昨日）在演示数据下多为「—」**：种子数据的 09-19 没有任何单据，基数为 0，
  而 `formatDelta` 对 0 基数返回 null（显示「—」）是刻意设计 —— 说「增长 0%」是错的。
  想让环比有意义，需要让前一天也有业务数据。
- **大屏未覆盖的验收**：仅验证了列表页与只读聚合的渲染；`/scm/screen/data/*` 四个接口仍是**只读渲染级**验证，
  没有跑过「跨天写流程后再核对环比数字」这一层。其数据源侧的写流程（出库 / 盘点 / 报损报溢 / 调拨 /
  规格转换 / 入库加权）已由 `e2e/scm-inventory-write.spec.ts` 覆盖。
- **商品中心 PCO-2 已实现（Wave 1，V44–V45）**：Excel 导入导出、图片中心（`image_type` 图集分组与批量维护）、
  独立菜单权限均已落地并通过后端 IT / 前端单测 / 类型 / 生产构建。唯一遗留：新增的 PCO-2 Playwright 场景
  需在有当前库的全栈环境（后端对当前 schema + Web 应用 + 对象存储）下跑一次浏览器验收，本轮无该运行环境未执行。
- **E2E 账号脚本的默认库已过期**：`tools/e2e_accounts.py` 默认 `XSY_V2_PG_DB=xsy_scm`，该库停留在 V17
  校验和冲突之前（F0 及以后未应用）；不带该变量跑 Playwright 会在陈旧库里建临时账号，登录得到 `30001`。
  运行入口必须显式带上 `XSY_V2_PG_DB` 指向当前开发库，或把脚本默认值与后端 profile 对齐后去掉这条约束。

## 追加记录

### 2026-09-22 采购操作效率（Wave 2B）：批量处理 + 导出 / 打印 + 按商品收货工作台

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 2B——采购单列表补齐
  批量少收关单、批量打印、导出与导出列设置（按登录用户本地记忆），采购收货页拆为「按单据 / 按商品」双视角，
  新增**只读**的「按商品收货工作台」。不重做采购状态机、不新增第二套收货事实、不引入巨型批量确认收货事务
  （确认仍走既有 `POST /scm/purchase/receipt/confirm`；计划 §6.4 的批量确认推迟项不在本 Wave 擅自决定）。
- **Flyway**：**0 迁移**。三个新端点全部复用已有权限（批量少收关单 `scm:purchase:short-close`、
  导出 `scm:purchase:query`、按商品工作台 `scm:purchase:receipt:query`），无新 `t_menu` 行、无表结构变更；
  计划 §6.7 的可选 `V46__scm_purchase_efficiency_permissions` 因选择复用权限而未启用。
- **API**：
  - `POST /scm/purchase/batch/short-close`（`scm:purchase:short-close`，入参 `PurchaseOrderBatchShortCloseForm`
    = `@Valid` 采购单版本列表（≤100，每项带 `version`）+ 关单原因；**整批原子**：任一单状态非法或 version 被他人
    改过即全批回滚并显式冲突，不留中间部分成功）。复用既有少收关单规则，未改状态机、不加幂等键。
  - `POST /scm/purchase/export`（`scm:purchase:query`，只读，入参 `PurchaseOrderExportForm` 继承列表查询条件 +
    `exportColumns` 白名单；FastExcel 后端逐列取值导出，强制忽略分页并设上限行数，列目录固定 15 列）。
  - `POST /scm/purchase/receipt/item-workbench/query`（`scm:purchase:receipt:query`，**只读**、无幂等键、无 `@OperateLog`；
    跨待收货单按 `skuId + purchaseUnit` 归并计划 / 已收 / 欠收 / 超收量，四位定点字符串下发）。
- **页面**：
  - `purchase-order-list.vue`：工具栏加「批量少收关单 / 批量打印 / 导出 / 导出设置」、行内加「打印」；
    勾选态门禁（批量删除只认 DRAFT、批量少收关单只认 PARTIALLY_RECEIVED）；导出列勾选存 `localStorage`
    （键含登录用户 + 场景，刷新后保留），弹窗复用采购导出目录常量。
  - `purchase-receipt-list.vue`：`a-tabs` 包「按单据」原列表 +「按商品」新页签；收货 / 入库确认抽屉与深链、入库守卫不变。
  - 新增 `components/purchase-receipt-item-workbench.vue`（只读、无重算、`skuId::purchaseUnit` 复合行键）与
    `purchase-order-print.ts`（无 DOM 的纯字符串 HTML 构建 + 转义，仅 `printPurchaseOrders` 触达浏览器 DOM）。
- **测试结果**：
  - 后端：`PurchaseOrderExportSupportTest`（列目录与取值，无库）**4/4 通过**；`PurchaseEfficiencyIT`
    （真实 PostgreSQL，批量少收关单整批原子 + version 冲突 + 只读工作台跨单聚合）**6/6 通过**。
  - 前端：新增 `test/w2b-purchase-efficiency-contract.test.mjs` **8/8 通过**——导出走 `postDownload`、
    批量少收关单走 `postRequest` 不带幂等封装、工作台只读且从不重算、打印工具零网络、导出目录 15 列与常量对齐、
    收货页 Tab 与既有确认 / 入库权限未动、采购页批量 / 导出 / 打印接线齐备。合并 `npm run test` = **111/111**；
    改动文件 ESLint 0 错、`vue-tsc` 全库 1946 条既有诊断中本 Wave 文件 0 条、`npm run build` 通过。
- **浏览器 / E2E**：`e2e/scm-purchase.spec.ts` 新增用例 11（导出只读 xlsx：响应类型 / 附件头 / 非空体、导出后采购状态不变、
  导出与导出设置入口可见）与用例 12（批量少收关单整批原子 + 混批含草稿单必须被拒且合法单不被部分关单；按商品工作台只读、
  四位定点数量、欠收与超收互斥、切页签渲染工作台表）。**本轮未执行**：需后端对当前代码重新构建部署 + Web 应用 + 临时账号的
  全栈环境；端点行为已由 6 条 IT、前端契约由单测 / 类型 / Lint 覆盖。
- **与计划的偏差**：① §6.4 建议的 `GET /scm/purchase/{id}/print` 落为**纯前端打印**（隐藏 iframe + `contentWindow.print`），
  零写接口、零新权限，符合 AGENTS §23「不为单页各自造打印设施」；② §6.7 的可选权限迁移 `V46` 未启用，因三端点复用既有权限即达成 0 迁移。
- **未完成 / 遗留**：Wave 2B 场景的全栈浏览器验收（见上）；批量确认收货（§6.4 推迟项，需另开设计明确失败语义）不在本 Wave。

### 2026-09-22 采购订单缺口预览（Wave 2A）：只读「订单汇总 / 库存缺口预览」

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 2A 第一阶段——
  在采购需求页新增**只读**的「订单汇总 / 库存缺口预览」：按时间窗 + 仓库（可选分类 / 关键字）把已确认销售订单的
  实发量按 `warehouseId + skuId + demandUnit` 归并，镜像 `PurchaseDemandService.generate()` 的取数 WHERE，
  与库存余额联表算出可用量与缺口。**第一阶段严格只读**：不生成需求、不改 `generate()` 的采购需求数量语义、
  不引入「在途采购抵扣缺口」（`openPurchaseQuantity` 刻意缺席，§6A.6 未裁决）。缺口与可用量全部由后端用
  `BigDecimal` 在 SQL 里算好、以四位定点字符串下发，前端不参与浮点运算、更不重算。
- **Flyway**：**0 迁移**。预览作为 Tab 并入既有采购需求页，复用其路由 / 菜单 / 权限 `scm:purchase:demand:query`，
  无新 `t_menu` 行、无表结构变更。
- **API**：`POST /scm/purchase/demand/summary-preview`（`@SaCheckPermission("scm:purchase:demand:query")`，
  只读查询、无 `@OperateLog`、无幂等键）。入参 `PurchaseDemandSummaryPreviewForm{startAt,endAt,warehouseId,categoryId?,keyword?,分页}`，
  出参 `PageResult<PurchaseDemandSummaryVO>`。`calculationStatus` 五值：`STOCK_ENOUGH / SHORTAGE / ZERO_STOCK /
  UNIT_MISMATCH / NO_BALANCE`；Q13 单位门禁——`demandUnit` 与余额记账单位不一致时判 `UNIT_MISMATCH` 且
  `shortageAgainstAvailable = null`，禁止猜换算率（与 `generate()` 同口径）。
- **页面**：`purchase-demand-list.vue` 用 `a-tabs` 把原列表包为「采购需求」页签，新增「订单汇总 / 缺口预览」页签挂
  新组件 `components/purchase-demand-summary-preview.vue`（自带时间窗 + 仓库选择器 + 关键字 + 表格，行内数量列走
  `quantity()` 三态渲染、状态列走 `SCM_DEMAND_SUMMARY_STATUS_ENUM`/`_COLOR`）。生成需求 / 分配弹窗留在 Tab 外不变。
- **测试结果**：
  - 后端：`PurchaseDemandSummaryPreviewIT`（真实 PostgreSQL、`@Transactional`）**8/8 通过**——含只读语义（跑预览后
    需求表计数不变）、`generate()` 口径一致、五态判定、`UNIT_MISMATCH → 缺口 null`、`NO_BALANCE`、分页 GROUP BY。
  - 前端：新增 `test/w2a-demand-summary-preview-contract.test.mjs` **5/5 通过**——枚举 5 值与后端对齐、
    `summaryPreview` 走只读 `postRequest` 且不带 `purchaseCommand`（写命令仍带）、预览组件渲染后端数量且**从不重算**
    （无非定点运算 / 无 `availableQuantity ±`）、`openPurchaseQuantity` 缺席、Tab 接线与生成 / 分配权限未动。
    合并跑 `w2a` + `w5` = **30/30**；改动文件 `vue-tsc --noEmit` 与 ESLint 均无错。
- **浏览器 / E2E**：`e2e/scm-purchase.spec.ts` 新增用例 10（预览只读且数量后端算好：建单后 `summary-preview` 命中本 SKU、
  每行状态在五值内且数量匹配四位定点、跑完需求表仍为 0、浏览器切页签渲染 + 截图）。**本轮未执行**：需后端对当前代码
  重新构建部署 + Web 应用 + 临时账号的全栈环境；端点行为已由 8 条 IT、前端契约由单测 / 类型 / Lint 覆盖。
- **与计划的偏差**：无。计划 §6A.11 要求「0 迁移、并入既有页」，落地一致。
- **未完成 / 遗留**：预览场景的全栈浏览器验收（见上）；预览的 Phase 2「一键把缺口转采购需求」不在本 Wave（只读阶段不写库）。
- **旁注（非本 Wave 引入）**：全量前端测试里 `w4-order-contract.test.mjs` 有一条**既有**正则用例因 `\r\n` 跨行匹配失败
  （`.` 不匹配换行），与本次改动无关、未纳入 Wave 2A 范围，按 §34「不顺带改无关代码」保持原样。

### 2026-09-22 商品中心 PCO-2（Wave 1）：Excel 导入导出 + 图片中心（V44–V45）

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 1（商品 PCO-2）——
  商品 Excel 模板下载 / 导入 / 按条件导出，以及独立的商品图片中心（图集分组 + 单商品维护 + 按文件名批量维护）。
  不新建第二套导入/文件框架：导入解析校验走 Apache POI、写库复用既有事务化商品新增；
  图片一律走 `FileService` + `public/image/`，URL 按 `file_key` 现算、不重新持久化预签名地址（延续 FA-0）。
- **Flyway**：
  - `V44`（结构 + 数据）：`product_image.image_type`（`PRIMARY`/`DETAIL` 两值 CHECK），并加
    `ck_product_image_type_primary CHECK ((image_type='PRIMARY') = is_primary)`，杜绝「两张主图 / 主图却标 DETAIL」
    这类与 `is_primary` 分叉的第二套事实；存量主图回填为 `PRIMARY`。第一版不引入图片类型表与更多类型。
  - `V45`（data-only）：PCO-2 菜单与权限，沿用 V7/V39 商品编号段——导入 418 / 导出 419（商品档案页 402 动作）、
    图片中心页面 406（挂 401 下）、图片查询 496 / 图片批量维护 497；仅授 SUPER_ADMIN。
    原 `scm:product:image`(416，单商品编辑) 保持不变、未删除。
- **API**：
  - `GET /scm/product/import/template`（`scm:product:import`）真实 xlsx 模板；
    `POST /scm/product/import`（`scm:product:import`，≤10MiB `.xlsx`，经 `securityFileService` 校验）；
    `POST /scm/product/export`（`scm:product:export`，复用查询条件 + `SmartExcelUtil`）。
  - 图片中心 `GET /scm/product/image/query`（`scm:product:image:query`）；
    `POST .../batch-bind`、`batch-remove`、`set-primary`、`reorder`（`scm:product:image:batch`，均 `@OperateLog`）。
    所有图片写接口经 `ProductImageSyncManager.sync`：只接受合法 `public/image/` fileKey、清旧主图、保证每 SPU 至多一张 PRIMARY。
- **导入语义**：整批事务——任一行有错则 0 行写入；错误定位到「Excel 行 + 列 + 原因码」，可准确指认 SPU/SKU 编码重复、
  分类 / 单位 / 标签不存在、停用单位用于新商品等；不把历史批次落库（第一版即时查看 / 下载失败明细）。
- **页面**：
  - 商品列表工具栏新增「导入 / 导出 / 图片中心」入口（按权限显示）；导入弹窗下载模板、选 `.xlsx`、
    loading + 未选文件禁止提交、失败逐行完整展示并可即时导出 CSV。
  - 新增图片中心页（路由 `/product/image-center`）：左列按关键字 / 「仅无主图」筛商品，右列单 SPU 图集维护
    （设主图 / 移除 / 拖动或按钮排序 / 上传绑定到当前 SPU），顶部「按文件名批量导入」先出命中·歧义·未匹配预览，
    仅对命中项写入，未匹配与歧义绝不静默丢弃；无 `image:batch` 权限时批量写入口隐藏。
- **测试结果**：
  - 后端：`ProductImageCenterPgIT` 全量 `@SpringBootTest` + `@Transactional` 打真实 PostgreSQL **8/8 通过**
    （含每 SPU 至多一张 PRIMARY、并发设主图不产生两张 PRIMARY、私有前缀 fileKey 拒绝、无权限/操作日志）。
    `ProductImportServiceTest` 单元级 **13/13 通过**（POI 解析 + 各类拒绝原因 + 错一行整批不写的判定）。
  - 前端：`test/product-import-model.test.mjs`（按行分组、行 0 文件级错误、整批拒绝、忽略大小写与扩展名匹配、
    未匹配/歧义显性暴露）**5/5 通过**；`scm/product` 范围 `vue-tsc --noEmit` 与 ESLint 均无错；Vite 生产构建通过。
- **浏览器 / E2E**：`e2e/scm-product.spec.ts` 扩展了 PCO-2 场景（导入入口 + 提交闸门、图片中心筛无图 + 单商品维护 +
  批量预览 0 命中禁止绑定、只读账号看不到导入 / 导出）。**本轮未执行**：Playwright 需要后端对当前 schema 运行 +
  Web 应用 + 对象存储 + 临时账号脚本的全栈环境，本轮无该运行环境；后端契约已由 IT、前端逻辑由单测 / 类型 / 构建覆盖。
- **与计划的偏差**：
  - 计划要求 `ProductImportIT`（真实库集成测试）；实际以 `ProductImportServiceTest`（单元级校验逻辑）覆盖导入判定，
    写侧原子性由复用的既有事务化新增保证、且 `ProductImageCenterPgIT` 已在真实库验证图片写路径。若后续要给导入补
    一条真实库 IT，再单独加。
  - 迁移号：计划把 PCO-2 排为 V44/V45，落地一致，未与配送 V42/V43 或 FA-0 的 V41 冲突；校验和守卫 `check` 通过（45 迁移、0 漂移）。
- **未完成 / 遗留**：PCO-2 场景的全栈浏览器验收（见上）；「查看历史导入批次」按需再建（第一版明确不做）。

### 2026-09-21 F0-DEBT-01 FA-0：附件资产分级与商品写侧收口（V41）

- **范围**：本轮按裁决只做「出裁决 + 写侧收口」。商品图公开化 + 绑定期限权 + 停止持久化预签名 URL；
  读侧受控批量读取、`scm_file_relation`、OA VO 收口、存量商品图搬运全部留到 FA-1～FA-3，
  分期见 [`plan/attachment-asset-grading-and-file-access-plan.md`](./plan/attachment-asset-grading-and-file-access-plan.md)。
- **分级落点**：`FileFolderTypeEnum` 新增 `PUBLIC_IMAGE(5, "public/image/")`。该目录的 ACL、
  前缀策略与 MinIO 匿名读策略在 F0 就已就位，本次没有新增存储侧设施。
  前端 `file-const.ts` 同步加枚举，商品上传组件不再写死 `folder=1`。
- **写侧规则**：`ProductImageSyncManager.sync()` 在存在性校验之后加 `requirePublicImageKey` ——
  新增或换绑的 `product_image` 只能引用 `public/image/` 前缀，违反即 `IMAGE_NOT_PUBLIC(40038)`
  （原 40030，因该号先被 W3 定价域 `PRICE_INVALID` 占用，按「先提交者保留」换号）；
  **例外**是「行沿用自己本次修改前已有的 key」，否则 PCO-1 之前落在 `private/common/` 的存量行
  会让历史商品连改排序、切主图都保存不了。前缀常量取自枚举，业务侧不另写字符串。
- **不再持久化 URL**：删 `product_image.file_url`（V41）与 `ProductImageEntity` / `ProductImageForm`
  的对应字段；展示 URL 由 `ProductQueryService` 按 `file_key` 现算覆盖。刻意不加 `file_key` 前缀
  CHECK，理由写在迁移头注释（存量私有行合法，硬约束会把历史变成故障）。
- **验证（2026-09-21）**：在一次性临时库（本次从 V1 全链建到 V41）跑目标套件
  **10 个类 76 用例全绿**：`ProductPgIT` 21（含新增 `rejectsBindingPrivateDirectoryImage` 与
  `allowsLegacyPrivateKeyButRejectsRebindingToAnother`，正反两面都断言）、`ProductMasterDataPgIT` 11、
  `ProductControllerTest` 4、`FileAccessGuardTest` 24、`ScmPurchaseMigrationIT` 4（追加性守卫已扩到 V41）等。
  前端 `npm run lint` 0 error（3 个既有 warning，不在本次文件）、`npm run test` 93/93、
  `ts_baseline_ratchet.py check` 判 `scm errors = 0` 且总量 -28（工具整体仍报 FAIL，
  是其记录在案的跨 checkout 绝对路径噪声：TS7016 消息含 `node_modules` 路径，
  换目录后同一错误以「新增 + 修复」成对出现，不代表类型回归）。
- **浏览器验收（2026-09-21 补，dev 栈）**：日常开发库已应用 V41（执行前先 `pg_dump` 备份
  `product_image`，实测该表 0 活动行；重启后端后 `flyway_schema_history` version 41 success）。
  `e2e/scm-product.spec.ts` **3/3 全绿、0 pageerror**：新增商品上传图片落到
  `public/image/`（folder 5）并保存成功，商品详情页 **reload 后**图片 `naturalWidth > 0`
  ——即展示 URL 确实由 `file_key` 现算，删列没有打断显示链路。
  验收中修掉一处既有的用例竞态：抽屉打开会并发拉分类树与单位/标签字典，`load()` 返回时整体替换
  `form`，而 Playwright 的 `fill` 不受 `a-spin` 遮罩约束，早期填入的值（尤其是三级分类）会被静默
  清空，`submit()` 便在前端校验处无声返回、`/scm/product/add` 从不发出。dev 库字典体量增长后该
  竞态才暴露；用例改为等本轮加载结束再填写。人工操作路径本就被遮罩与 `保存商品` 的 `disabled` 挡住，
  因此这是测试稳定性问题，不是产品缺陷。
- **未验证 / 未做**：
  ① 存量商品图仍在 `private/common/`，读侧路径未收口，因此 F0-DEBT-01 仍是**未关闭**的门禁；
  ② 本地存储模式 `/upload/**` 静态直出无守卫，权限行为只能在对象存储模式下判断。

### 2026-09-21 地图 M0 / M1：地理数据地基与大屏真实地图（V40）

- **范围**：方案 §3 / §4 的 M0 + M1 同期落地（用户裁决「M0 + M1 一起开工」）。
  M2（街道底图 / 选点 / 地理编码）**未动工**，路线 A/B/C 待裁决；M3 配送与排线未动。
- **V40 数据**：新建 `scm_region`（省 / 市两级，实测 **34 省 + 414 市**，编码与名称逐字取自
  `area-cascader` 静态字典，质心取 DataV.GeoAtlas 2026-09-21 快照）；`warehouse` / `customer` /
  `supplier` 各加省市区**编码 + 名称六列**与 `longitude` / `latitude` / `geom_crs`，
  配「经纬度成对」「有坐标必须有 CRS」「CRS 只允许 GCJ02/WGS84」三组 CHECK 与市级部分索引；
  迁移末尾按自由文本地址做保守解析回填。日常开发库已应用（`flyway_schema_history` version 40 success）。
- **回填结果（解析覆盖率低是预期）**：客户 23 活动行 / 10 已归属到市，供应商 20 / 7，启用仓库 2 / 1。
  只解析到省的行保留省级归属（省级统计不丢行）。**经纬度与 `geom_crs` 三张表全为 0 行**，
  地图上的位置目前一律是区划质心。
- **后端 M1**：新增只读接口 `GET /scm/screen/data/geo`（沿用 `scm:screen:query`），
  市级聚合 + 覆盖度；省级分布在 **Java 侧由市上卷**，不写第二份 SQL。
  `ScreenGeoVO` 明确标注质心语义与「字典缺码不成为气泡」。
- **前端**：三张主档表单的「地址」升级为「所在地区（省 / 市 / 区）+ 详细地址」并存；
  路径 ↔ 六列的双向转换集中在 `views/business/scm/common/scm-area.ts`（清空要六列整体归 null，
  回填必须是连续路径）。大屏中间面板由内联 SVG 抽象网络替换为 ECharts 省界着色 + 市级气泡，
  底图是存档进仓库的 `public/screen/china-province.json`（576 KB / 35 要素），运行时不请求外部域名。
- **实施中发现并修掉的静默缺陷**：省级着色原本按**名称**匹配底图要素，
  而字典用简称「香港」/「澳门」、官方边界用全称，这两省会**不着色且 ECharts 不报错**。
  改为按 `adcode`（= `province_code`）匹配，并由新增的 `test/screen-geo-contract.test.mjs`
  把这条命名差异钉成断言（3 项，含「每个种子省码必须能在底图里找到」）。
- **验证**：后端 `ScmGeoMigrationIT` 6 项（种子完备性与质心、重名市唯一清单、半套坐标与非法 CRS 被拒、
  市级部分索引、保守解析只命中完整市名、区县列绝不被解析触碰）+ `ScmScreenDataIT` 7 项全绿；
  前端 `npm run test` 93/93、`eslint src`（含本次改动的 `e2e/scm-customer.spec.ts`）0 error、SCM 区 vue-tsc 0 错误
  （`ts_baseline_ratchet.py check` 仍因基线含他机绝对路径假 FAIL，见下方既有遗留条目）。
  真实浏览器：`scm-customer` 2/2（本次扩展 —— 新增省市区后必须落成六列快照、编辑回填显示连续路径、
  再次保存不丢归属）、`scm-supplier` 2/2、`scm-purchase` 9/9、`scm-inventory` 仓库启停 1/1、
  大屏真实浏览器核验 ✅（五接口 code=0、11 面板、13 指标、0 pageerror）。
- **未做**：`lines` 流向层、区县级后端字典与区划查询接口、经纬度人工录入入口（三者都等 M2 决策）。
- **新增风险**（见方案 §8 R-6 / R-7）：港澳下辖市级字典名含「东区 / 南区 / 大堂区」等通名，
  与按整名做 `strpos` 的保守解析组合存在误挂可能，现网命中 0 行（已实测 `city_code` 落在
  710000–829999 为 0）；一旦要收紧规则必须新开一个迁移号（按 `AGENTS.md` 从当时最大号之后选号），不能改 V40。
- **迁移号冲突提示**：`docs/plan/product-center-optimization-plan.md` 把 PCO 各期排为 V37–V40，
  与已入库的 V37 / V38–V39 以及本次 V40 全部撞号，该文档落地前必须整体重排（见方案 §9 D-3）。

### 2026-09-20 商品中心优化 PCO-1：主档增强（V38–V39）

- **范围**：只做方案里的 PCO-1（主档字段 + 辅助资料字典 + 列表高级筛选 + 批量维护 + 删除保护）。
  Excel 导入导出与图片中心（PCO-2）本轮未动。
- **V38 数据**：`product_spu` 增加助记码 / 品牌 / 产地 / 储存方式 / 保质期天数 / 损耗率 /
  采购预警天数 / 开票品名 / 税收分类编码 / 是否免税 / 税率 / `master_status` 十二个标量与对应 CHECK，
  另建 `created_at` 部分索引；新建 `scm_uom`（24 条种子，编码与名称在**活动行内**各自唯一）、
  `product_tag`、`product_tag_relation`。单位字典**只做取值来源**：`product_sku.sale_unit`、
  `supplier_sku.purchase_unit`、`inventory_balance.unit` 继续存字符串快照，不回写、不改类型。
- **V39 权限**：辅助资料页 405（商品目录下）+ 单位 / 标签 query|add|update|delete（488–495）
  以及商品批量维护 417，全部只授 SUPER_ADMIN；`/options` 类下拉数据源**不另开权限**，
  复用 `scm:product:query`，否则只读角色连筛选条件都渲染不出来。
- **`master_status` 口径（已裁决）**：`DISABLED`（停止引用）**只作用于商品可选范围** ——
  仅 `ProductSkuOptionDao.options` 过滤，订单 / 采购 / 库存写入路径一行未动，历史单据照旧解析。
  `ARCHIVED` 必须下架：DB CHECK 兜底，服务层同名校验负责给出可解释的业务错误。
- **批量维护**：上下架 / 主档启停、改分类、打标签三种命令共用 `scm:product:batch`，
  逐行携带乐观锁版本，**预校验不通过整批不写**，`failures` 最多回 50 行。
- **删除保护**：商品删除前检查业务引用，命中即 `40931` 并提示改停用或归档；
  刻意**不**把供应商关系与客户可见性算作引用（那是可维护的辅助绑定）。
  单位被引用只能停用不能删，标签下仍有商品不能删。
- **前端**：列表页高级筛选（SKU 状态 / 商品类型 / 主档状态 / 储存方式 / 标签 / 主图 / 条码 / 创建时间）
  与「助记码进同一个关键字模糊搜索」；新增辅助资料页（计量单位 + 商品标签两个 Tab）；
  商品抽屉补主档扩展字段与标签多选；SKU 单位下拉取字典，**字典外 / 已停用**的历史值保留在选项里以便回显原值。
- **验证**：后端 `ProductMasterDataPgIT` 11 项 + 全量 734 项通过；前端 `npm run lint` 0 error、
  `npm run test` 86/86、vue-tsc 基线 SCM 区新增 0；`e2e/scm-product.spec.ts` 3 项
  （商品试点全流程 / 字典锁定与退役 / 只读越界含批量命令）在真实浏览器通过。
- **顺带修掉两处既有 E2E 脆弱性**（均非 PCO-1 引入）：
  `scm-pricing.spec.ts` 的取价夹具会复用 option-list 首行的演示商品，而该商品挂在**二级分类**上，
  回写 `/scm/product/update` 必被三级规则拒掉，加上当时没检查返回码，下架断言以「仍然可售」假通过 ——
  改为夹具自建商品并断言保存返回码；商品分类树是虚拟列表，节点一多目标行不在 DOM 里 ——
  改为先按标题搜索再点唯一节点。
- **只读账号夹具拆分**：`tools/e2e_accounts.py` 的 `_read` 已带只读角色（全部页面菜单 + 只读按钮），
  因此它**无法**再验证「完全没有权限」；新增 `_none`（不挂任何角色），
  `scm-inventory.spec.ts` 第 4 项改用它，`_read` 继续承担「页面能打开、写按钮被隐藏」。
- **已知遗留**：商品类 E2E 沿用各波次既有约定 —— 只回收临时账号，不删商品（商品被订单 / 收货 /
  库存行引用，删除会撞删除保护）。本次全量跑完后 dev 库留有 44 行未删除的 `W*_E2E_*` 商品，
  每跑一轮线性增长；需要清理策略时由测试数据隔离统一处理，不在 PCO-1 内单独收窄。
- **已知遗留**：`tools/ts_baseline_ratchet.py` 的错误标识把消息原文一起纳入，而 TS7016 / TS7053 的
  提示里带 `node_modules` **绝对路径**，换机器或换 checkout 路径就会得到「新增 83 / 修复 109」的
  **假 FAIL**（实际总量下降、SCM 区为 0）。在其 key 归一化之前，判定只看 `scm errors` 计数与总量方向。

### 2026-09-20 调拨 / 转换成本平移修复（V37）与库存写流程 E2E

- **缺陷**：V34 之后只有采购入库按加权公式更新 `avg_cost`，调拨转入与转换转入按**目标行现有均价**
  入账；目标行是本次调入现建的（`insertOnConflictDoNothing`，`avg_cost` 默认 0）时成本被整体清零
  —— 数量对、金额账全丢。完整口径与取舍见 [`decisions.md`](./decisions.md)。
- **代码口径**：`InventoryCommandService` 的写入路径由六条增至八条（补 `postConvertOut` / `postConvertIn`），
  三条入方向腿统一走 `weightedAvgCost`。调拨转入成本由 `transferInCost` 按
  `(TRANSFER_OUT_ITEM, 明细行 id)` **回读发出腿流水**，不在明细行上另存成本副本（同一事实只有一处可漂移）；
  转换两条腿的基准由 `lockCostBasis` 在写任何腿之前按持锁快照一次取齐，转入腿单价 =
  转出腿总成本 ÷ 目标数量（`convertedUnitCost`，守恒的是总成本不是单价）。
  链式转换（同一 SKU 在本单内既进又出）的成本基准由 `resolveOutboundCostBasis` 在期初快照上预解，
  环状引用按期初均价收敛且不写缓存、不写日志。
- **V37 数据修正**（`V37__scm_inventory_reprice_zero_cost_balances.sql`）：候选刻意收窄为
  `quantity > 0 AND avg_cost = 0`（即缺陷症状），逐行按 `(occurred_at, id)` 重放流水重算均价；
  入方向由 `after = before + quantity` 判定（该等式由 `ck_inventory_movement_snap` 按类型钉死），
  不硬编码类型清单。找不到配对转出腿、或重放数量与存量不符时 **`RAISE EXCEPTION`** 而不是给个
  看起来合理的均价。只 `UPDATE inventory_balance.avg_cost`，一行流水都不改（Q7 / V21）。
  `ScmInventoryRepriceMigrationIT` 8 项覆盖重算、幂等、非零行不动、两类失败必须 RAISE、
  纯盘盈不造价、只改派生状态、候选口径。
- **真实库应用证据**：日常开发库于 20:32:52 应用 V35 / V36 / V37，
  迁移自报 `V37 repriced 3 zero-cost balance row(s) from the movement ledger`。
  冷库备用仓 3 行均价由 0 回正为 6.2000 / 132.0000 / 2.6000，金额 3720.00 / 26400.00 / 2080.00
  （与两条腿的发出腿 `unit_cost` 逐条一致）。
- **写流程 E2E**：新增 `xsy-scm-web/e2e/scm-inventory-write.spec.ts`，**6/6 通过（59.6s，0 pageerror）**，
  真实浏览器 + 真实 PG + 真实 HTTP（每次操作换新 `Idempotency-Key`，否则命中幂等回放拿不到真实错误码）。
  每个入口同时断言三件事：单据状态机 · 余额与流水（append-only 链） · 金额守恒。
  覆盖：出库确认（15.0000 与 `SALES_OUT` 成本 6.2000、重复确认 41014）、盘点（盘盈不改均价 / 盘亏、
  41020、负实盘量被校验拒绝）、报损报溢（缺意见驳回 41037、过期版本 40921、重复审批 41029）、
  调拨（在途期间目标仓无余额行、停用态收货 41048、收货后 `in.unit_cost == out.unit_cost`
  且目标仓均价 6.2、跨仓总成本守恒）、规格转换（`CONVERT_OUT` 6.2000 / `CONVERT_IN` 2.48、
  两边金额 24.8 相等、目标行均价 2.48 而非 0）、只读账号三个写接口一律 30005 且页面无写按钮。
- **过程中修掉的真实环境问题**（都不是应用缺陷）：
  · 后端进程陈旧（01:50 启动、库只到 V34）→ 重新打包并把后端指向当前开发库后重启，
    陈旧判据见「后端启动时刻 vs 迁移文件 mtime vs `flyway_schema_history.installed_on`」。
  · `t_employee` 的 identity 序列落后于种子显式插入的主键（`max=6`、序列=2），
    建 E2E 账号直接撞主键 → `setval` 推进序列（只动序列，不改行）。
  · 开发库种子分类**只播到二级**，而 SPU 只允许挂在三级且 ENABLED 的分类下
    （`requireSelectableCategory` → 40011）→ 新 spec 自建 1→2→3 级分类链，不依赖种子深度。
    既有 `scm-inventory.spec.ts` 沿用「从分类树找 level===3」，在当前开发库上同样会失败（未改它）。
- **测试自曝的两处假红**（已修，未削弱断言）：`ScmInventoryTransferIT` 按明细行 id 查流水时未带来源类型，
  而明细行 id 只在其来源表内唯一 → 查询补 `source_document_type IN (...)` 谓词（与
  `uk_inventory_movement_source_active` 的冲突域一致）；两个回滚 IT 用**固定**来源行 id 建预留，
  在本类无外层事务、预留会提交的情况下第二次运行撞 uk 假红（41016）→ 改为按本次 `skuId` 派生。
- **已知遗留**：脏库上 `ScmInventory*IT` 的 V19 Step 4 全库对账会被历史提交型用例残留挡下
  （5 例，与本轮改动无交集）；干净库上库存 IT 全绿。收尾验证：在新建的一次性空库复跑
  `ScmInventory*IT + ScmPurchaseMigrationIT`（V37 从 V1 起整链迁移）→
  **17 个测试类 / 114 项，Failures 0、Errors 0、Skipped 0，BUILD SUCCESS**（含
  `ScmInventoryRepriceMigrationIT` 8 项）。前端侧：`npm run test` **83/83**，
  `tsc --noEmit` 对新 spec 干净，Playwright 写流程 **6/6**。
  脏库的 V19 全库对账当时仍是既有的测试数据隔离缺陷；2026-09-22 选择**收窄断言范围**而非清理测试库，
  见下方「2026-09-22 已应用迁移字节还原与库存对账判据收窄」。
- **环境注记（非本轮引入，未改动）**：开发库现有**两个**启用仓库（WH001 与造数时启用的冷库备用仓），
  启用仓库不唯一时依赖「默认仓库」的路径会直接 41018；本轮 E2E 自建的目标仓已在收尾时反向调回并停用。

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

- 环境：本地开发栈（Docker 内 PostgreSQL + Redis、打包后的后端 fat jar、Vite dev 前端），Playwright 真实浏览器。
- 后端：`GET /scm/order/import/template` 改为一次性读入 classpath 字节再写出（长度取实际字节数），资源缺失时回写 JSON 错误而不是 500；`SmartResponseUtil.setDownloadFileHeader` 补 `filename*=UTF-8''`（RFC 6266），`filename=` 保留为 ASCII 回退。
- 实测响应：`200` + `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8` + `Content-Length: 6192` + 双文件名头，正文首两字节 `504b`（真 ZIP，非错误 JSON）。只读账号返回 `code 30005`、未登录返回 `30007`，均为 `application/json`，前端据此走失败分支、不会落盘成 Excel。
- 打包证明：`BOOT-INF/classes/template/sales-order-import.xlsx` 在 JAR 内；从 `C:/Windows/Temp` 这种与仓库无关的工作目录启动同一 JAR 仍可下载，证明不依赖开发机绝对路径。
- 前端：`getDownload`/`postDownload` 返回 Promise 以便按钮展示下载中状态；错误文案只接受 JSON `msg` 或 200 字符以内的非 HTML 文本。
- 验收：`scm-order.spec.ts` **8/8 通过**。用例 7：页面下载模板 → 填真实客户/SKU → 上传 → 纯标品已确认 + 非标品待称重；混入错误行后整批失败、零写入、展示实际行号与字段；同一 Idempotency-Key 重放不重复建单。用例 8：**原样上传未替换示例行**的模板 → `发现 2 个问题、零写入`，逐行展示实际行号（Excel 第 2 行）、订单标识、字段（客户编码 / SKU编码）与原因（客户编码不存在 / SKU 编码不存在）；**人工单价 + 改价原因**在有 `scm:order:price-override` 的账号下成交且 `lockedUnitPrice=1.2345`、`orderedTotalAmount=2.4690`；**只填人工单价不填改价原因** → `发现 1 个问题、零写入`，提示「填写人工单价时必须填写改价原因」。`OrderWebTest` **6/6**，前端单测 **83/83**，ESLint 改动文件 0 错误，TS 棘轮新增 0、SCM 0（全仓 1947，较基线 1974 下降 27）。
- 模板缺失分支实测：复制 JAR 并删掉其中 `BOOT-INF/classes/template/sales-order-import.xlsx` 后启动，接口返回 `application/json` + `code 30001`「导入模板缺失，请重新部署应用后再试」，不返回空文件、不返回 500。
- 全量后端回归 `mvn -pl sa-admin -am test`：**713 项 / 5 错误**，错误全部落在 `module/scm/inventory/**`（`ScmInventoryBackfillIT` ×3、`ScmInventoryLossGainRollbackIT`、`ScmInventoryStocktakeRollbackIT`），与本次改动零交集。原因是 **V19 Step 4 的对账断言是「全库」校验**（`inventory_balance` 全表 vs 流水全表，不限定本测试数据），而共享 `xsy_v2` 库里残留了其它库存 IT 写入的 `TRANSFER_OUT` 流水；隔离单跑这 3 个 IT 同样失败，属既有的测试数据隔离缺陷，非本次引入。
- 顺带修复：`tools/generate_import_template.py` 的 `verify()` 原先比对裸 XML，遇到 openpyxl + lxml 写出的字符引用（`&#27169;`）会误报乱码；改为反转义后比对，并追加对仓库内交付模板的校验。生成器契约与 `SalesOrderImportService.HEADERS` 逐列一致，模板可重建。
- 未完成项：小程序（W6-2）与电子秤实重回写联调继续延后；F0 云/MinIO 场景未配置，cloud IT 与 cloud Playwright 用例仍跳过；库存那 5 个既有 IT 失败需由库存波次负责人决定是清理测试库还是把对账断言限定到测试数据范围。
### 2026-09-20 数据大屏 V1（视觉与信息架构重构）

按设计稿把大屏从「一个页面堆 KPI」重做成**供应链运营中心**：三列布局 420/1000/420 + 底部趋势带，
10 个面板、3 张图表，`index.vue` 只负责布局。

**后端新增（只读聚合，不改任何业务表）**

- **今日时区修正**：`ScreenDataService.todayRange()` 原用 `ZoneOffset.UTC` 的日界，
  实际窗口是「北京时间 08:00 → 次日 08:00」—— 早上 07:00 下的单会被算进前一天。
  改为 `Asia/Shanghai` 日界，与 `demand_date` 既有约定一致。
- **`GET /scm/screen/data/trend?range=7d|30d`**（`ScreenTrendVO`）：一次返回 8 条等长序列
  （`sales` / `orders` / `purchaseAmounts` / `purchaseOrders` / `inventoryQuantity` /
  `inboundQuantity` / `outboundQuantity` + 日期轴），避免大屏为 3 张图打十几个接口。
  未知 range 一律兜底 7d。日期轴与序列长度、末点=北京时间今天均有断言。
- **库存健康度 `health`**：复用 `ScmInventoryWarningStatusEnum.evaluate`，**不新编算法**。
  四档**互斥且之和等于 `totalSkuCount`**：缺货（可用量 ≤ 0，优先判定，不要求配阈值）→
  未配置阈值 → 预警 / 积压 / 正常。`totalSkuCount` 取「参与评估的行数」而不是 product_sku 总数，
  否则四档之和会对不上面板上显示的总数。
- **供应链网络 `warehouseNodes`**：启用仓库的名称 / 库存量 / 今日出库量。
- **`todayCustomerCount` / `todaySupplierCount`**：今日有已确认订单的客户数、今日有采购单的供应商数。
- **商品排行按 SPU 归并**（原按 `sku_id`）：同一商品的多规格 SKU 会让榜单里出现两条同名行
  （实测「智利车厘子」出现两次 ¥3,450 / ¥1,260），现在合并为一条 ¥4,710。
- **出入库方向集合改由枚举派生**（`ScreenDataService.INBOUND_MOVEMENT_TYPES` /
  `OUTBOUND_MOVEMENT_TYPES`）：原先趋势 SQL 把 10 个 `movement_type` 硬编码成两份清单，
  将来新增第 11 个类型时会**静默少算**（数字看着正常、只是偏小）—— 这是本项目最忌讳的失败方式。
  现在两份清单由 `ScmInventoryMovementTypeEnum` 的方向位派生后作为参数传入，
  而方向位本身已被 `ScmInventoryConstantTest#movementDirectionMatchesSnapshotConstraint`
  钉住（10 个类型恰好 5 入 / 5 出，与 `ck_inventory_movement_snap` 的方向分支同源），
  等于把三处口径一并接进那道契约守卫。
- **随之统一「出库」的口径**：今日出入库次数（`countMovementsByTypeAndRange`）原先只数
  `PURCHASE_IN` / `SALES_OUT` 两个类型，供应链网络节点的「今日出库」原先只算 `SALES_OUT`，
  而趋势的出入库量算的是方向全集 —— 同一屏上「出库」有三个定义。现在三处统一为方向全集，
  与前端「今日出库 / 今日入库」的文案一致（调拨转入、盘盈、报溢也是入库作业）。
  **这会改变已展示的数字**：有调拨/盘点/报损报溢发生时，出入库次数会比原来大。

**前端重构**（`views/business/scm/screen/`）

- `index.vue` 只做布局；组件 14 个（`screen-header` / `screen-panel` / `metric-card` /
  `rank-bar-list` / `business-overview` / `customer-ranking` / `product-ranking` / `core-metrics` /
  `supply-chain-map` / `purchase-overview` / `inventory-health` / `warehouse-ranking` / `trend-section`）；
  composables 4 个（`use-screen-scale` / `use-screen-data` / `use-echarts` / `use-screen-clock`）；
  `styles/` 3 个（`variables` / `screen` / `panel`）；另加 `types.ts` / `format.ts`。
- **视觉层级**：今日销售额是整屏最大数字（hero 62px），订单数次级，客户/出库/采购再次，
  不再让每个 KPI 一样大。排行用**自定义条形**而不是 ECharts bar（ECharts 类目轴做不出
  「序号+名称+金额一行、条形另起一行」的排布，10 条柱状图在 420px 面板里标签必然截断）。
- **仓库分布不用饼图**：改成横向条形 + 百分比 + 绝对量（饼图标签在 420px 下会被截断成「默…」）。
- **三态**：Loading（仅首屏）/ Error（仅首屏全挂）/ 静默刷新失败保留旧数据 + 头部状态栏变黄。
  30 秒自动刷新，页面不可见时跳过、重新可见时立刻补一次。
- **等比缩放**：设计稿尺寸固定 1920×1080，只做整体 `transform: scale`；实测 1440×810 → scale 0.75，
  文档高度 1080 无溢出。ECharts 实例 `dispose` 于 `onBeforeUnmount`（大屏是独立路由，反复进出会泄漏）。

**顺带修掉的布局缺陷**：`.scm-panel` 原本是 `flex: 0 1 auto`，在列方向**不会撑满父容器** ——
侧栏三个面板只占上半屏、中列地图内容仅 286px（可用 494px），而每块面板内部看起来都「正常」。
改为 `flex: 1 1 0%` 后实测：侧栏 3×229、中列 210+494、趋势带 224，合计 1080 精确填满。

**演示数据**：`inventory_warning_threshold` 原本 0 行，健康度会退化成一根「未配置」灰条。
新增幂等脚本 `.workbuddy-ai/seed/live/seed-thresholds.py`（走 HTTP API，非直插）造出
13 条阈值，覆盖正常/预警/积压/缺货四种状态。副作用是**库存预警列表开始有数据**（7 条异常）。

**验证**：21 个新增前端模块全部通过 Vite 编译；ESLint 0 问题；`vue-tsc` 大屏目录 0 类型错误
（全仓 1948 个报错全在 SmartAdmin 底座，属既有状态）；前端契约测试 83 项全过；
Playwright 实测 10 面板 / 3 canvas / 0 pageerror；11 个库存与业务页面巡检全绿。

**未覆盖**：地图仍是抽象网络（`warehouse` / `customer` 只有自由文本 `address`，无经纬度与省市区字段）；
「较昨日」环比在种子数据下多为「—」（09-19 无任何单据，基数为 0）；
V2 高德地图 / V3 配送线路 / V4 分拣绩效未开始。

### 2026-09-20 演示数据造数（数据大屏「今日」指标）与调拨成本发现

- 现象：数据大屏 12 个 KPI 的「今日」类指标全为 0、两张排行图空白。原因是种子数据
  （`.workbuddy-ai/seed/`）的时间固定在 2026-09-01~09-18，而当天是 09-20，没有当日单据。
- 处理：**全程走 HTTP API 驱动真实业务链路造数，不直插业务表**。直插 `inventory_movement`
  会造成「流水有、余额没变」的账实不一致（Q7 append-only + V19 重放会打架），
  且会绕过 Q13 单位不变量与 V34 移动加权成本。产出脚本
  `.workbuddy-ai/seed/live/seed-today.py`（**幂等，可重跑**）：
  销售订单创建→提交→非标品补实数量→确认；采购单创建→提交；收货单创建→确认（DIRECT 直接入库）；
  出库单创建→确认；调拨创建→发出→收货。
- 结果：今日 7 张订单（全部 CONFIRMED）、2 张采购单、2 张收货单（CONFIRMED）、2 张调拨单（RECEIVED）、
  16 条库存流水；12 个 KPI 全部非零，3 张图表有数据，11 个页面浏览器巡检全绿、0 pageerror。
  **`inventory_balance` 与 `inventory_movement` 重放逐行一致（差异 0 行）**，余额未被污染。
- 为让「仓库库存分布」饼图不止一个扇区，启用了原本 DISABLED 的「冷库备用仓」（id=2）
  并发起一张 默认仓库 → 冷库备用仓 的调拨（苹果 300kg / 车厘子10斤装 100箱 / 上海青 400把）。
  这是一次**主数据状态变更**，如需还原：`POST /scm/warehouse/disable {id:2,version:...}`。
- 顺带修复大屏饼图：ECharts 默认外置标签在约 420px 宽的面板里被截断成「默…」「冷…」。
  改为仓库名走底部图例、扇区内只标占比（<5% 不标）+ 显式浅色调色板 + 深色文字描边，
  并补上空数据时的「暂无库存数据」提示（与两张排行图的「今日暂无数据」一致）。
- **新增踩坑（造数时会撞到）**：
  · `/scm/order/{create,submit,confirm,item/actual-quantity}`、`/scm/purchase/{create,submit}`、
    `/scm/purchase/receipt/{create,confirm}` **全部强制要求 `Idempotency-Key` 头**；
    控制器签名里的 `required=false` 是假象，服务层才校验（缺了报 40069 / 40084）。
  · 调拨查询表单对 `getPageSize()` 有 `@Max(100)`，传 200 会被统一校验拦成 30001，
    **且失败时 `list` 为 `None`** —— 不看 `code` 就会静默当成「没有数据」。
  · 销售订单/详情的主键字段是 `orderId`，采购单是 `id`。
  · 数量/价格必须匹配 `[0-9]{1,14}\.[0-9]{4}`（小数点必须有且恰好 4 位）；但**调拨**的
    `quantity` 是普通 `BigDecimal`（`@Digits(14,4)`），与订单相反。
- 本节标题里的「调拨成本发现」= 调入腿 `unit_cost` 全为 0（转出腿 6.20 / 132.00 / 2.60）。
  该缺陷已于同一天修复并由 V37 回正数据，见下方「调拨 / 转换成本平移修复（V37）与库存写流程 E2E」。

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
- 验证：一次性干净临时库上 `ScmInventory*` 全绿。
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
- **2026-09-20 故障修复：数据大屏打不开。** 两个独立原因叠加：
  1. **前端运行实例里的源码是旧快照**，`src/views/business/scm/screen/` 等 **15 个文件**
     （大屏、调拨、盘点、报损报溢、规格转换、阈值预警的前端）当时**并不存在于运行实例**；
     Vite 找不到 `.vue` 时会把请求回退成 `index.html`（HTTP 200 + `text/html`），页面白屏。
     修复是把当前源码同步进运行实例并确认 Vite 真的在编译 `.vue`。
  2. **Header 入口 URL 缺 hash 前缀**：应用是 `createWebHashHistory`，
     原 `window.open('/screen')` 命中服务端路径后被 SPA 回退，hash 为空最终落到首页。
     改为 `window.location.origin + import.meta.env.BASE_URL + '#/screen'`。
- 顺带修掉两个同源缺陷：
  - 大屏 1920×1080 设计稿从未应用缩放（`transform-origin` 声明了却没有 `transform`），
    小于 1920×1080 的视口右侧/底部内容被 `overflow:hidden` 裁掉 → 补 `fitScreen()` 等比缩放 +
    容器 flex 居中；实测 1280×720 下 scale=0.6667、边界正好落在视口内。
  - `src/lib/smart-watermark.ts`：水印容器只存在于 SmartLayout 内，
    而 `set()` 注册的 `window.onresize` 是全局的、离开 Layout 不注销，
    在大屏（独立路由）上缩放窗口必抛 `TypeError: ... reading 'appendChild'` → 加空值守卫。
- 大屏浏览器验收覆盖：登录 → 点 Header 入口验证新标签 URL → 深链 `/#/screen` →
  校验 3 个接口 code=0、12 个 KPI 有真实数据、3 张 canvas 图表挂载、小视口不裁切、无 pageerror。
- 结果：验收 20 项全绿，0 pageerror；`库存总量` 由 `4051.0000` 改为 `4,051`，
  今日排行图为空时显示「今日暂无数据」（当日无订单时不再像页面坏了）。

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
- 验证：一次性干净临时库上 `ScmInventory*` 全绿；前端 `npm run test` 78/78、
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
- 验证：一次性干净临时库上 `Tests run: 666+ / Failures: 0（除数据大屏）/ Skipped: 5`；
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
- 验证：一次性干净临时库上 `Tests run: 645, Failures: 2, Errors: 2, Skipped: 5` ——
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
- 验证：一次性干净临时库上 `Tests run: 622, Failures: 2, Errors: 2, Skipped: 5` ——
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
- 最终全量入口：汇总产物留在本机被 gitignore 的运行目录（未入库），后端 `Tests run: 583, Failures: 0, Errors: 0, Skipped: 5`；TS 基线 1974、当前 1953、SCM 0、新增 0；lint 0 错误/3 条既有警告；前端 unit 64/64；production build 通过。
- 最终 Playwright：48 passed、0 unexpected、0 flaky、7 skipped；F0 cloud 环境未配置导致 7 项跳过。全量无 FAILED，因后端 5 项和 E2E 7 项环境 skip 返回 `INCOMPLETE (exit 2)`。

### 2026-09-18

- 工作区收尾：采购单拆分保留原事务与需求重算边界；修复编辑新增采购行时分配缺少 `purchase_order_item_id`，新增真实 PG 回归用例，先复现 NOT NULL 失败再修复。既有测试断言未削弱。
- V15/V19/V20 保留工作区的恢复改动：与 `d7dbea3` 之前的文件逐字节相同，Flyway 校验和分别为 `1541405941` / `602319876` / `1019773777`，与本机数据库一致；未运行 repair，未新增迁移。
- 工程验证：Surefire 显式发现 `*IT`；跨平台入口、类型门禁及其测试纳入 Git 白名单；历史 TS 基线从 Git 恢复到 `xsy-scm-web/quality/`，未以当前错误重新放宽基线。入口遇失败返回 1，未覆盖返回 2。
- `mvn -B -pl sa-admin -am test`：`Tests run: 567, Failures: 0, Errors: 0, Skipped: 5`，`BUILD SUCCESS`。其中 V21 不可改删/截断、库存回填、并发、回滚、入库测试通过；F0 云存储 5 个用例因未启用 cloud ENV 跳过，故验证入口整体为 INCOMPLETE。
- 前端 `npm run lint`：0 错误、3 条既有警告；`npm run test`：63 通过、0 失败、0 跳过。TS 棘轮：历史基线 1974，当前 1953，SCM 0、新增 0；上游全量 typecheck 仍有既有错误，未标记为零错误。
- 验证工具 6 条回归测试通过。构建发现采购日志和仓库页的列设置 `v-model` 绑定常量数组，已改为 `ref`，以支持列设置回写。
- 最终 `npm run build` 通过（1m 30s），两处常量赋值警告消失；仍有上游 `vue3-json-viewer` 图标路径和大包警告。修复后两文件 ESLint、TS 棘轮再次通过。PowerShell 与 Bash 入口均实测在 E2E 服务缺失时返回退出码 2。
- 未覆盖：后端与前端开发服务未运行，E2E 就绪检查返回 INCOMPLETE；MinIO 云端往返未执行；E2E 本机账号工具未纳入 Git，仍需供给。未启动 W6-2、未引入新角色。
- 完成 W6-1 代码交付和静态复核；新增库存账本不可改删约束及相关修复。
- 删除重复的波次文档、旧 UI 指导、截图和过程性记录；文档收敛为本入口、进度和决策三份。
- 前次文档整理没有运行测试或业务命令；当前验证以本日上方工作区收尾记录为准。

### 2026-09-17

- F0 对象存储完成验收；W5.5 SmartAdmin 原生功能同步完成。

### 2026-09-16

- W4 销售订单和 W5 采购完成阶段交付。

### 2026-09-14 至 2026-09-15

- 完成 W0 底座、W1 商品、W2 客户供应商、W3 定价和 V2 根目录整理。


## 物流配送静态路线 L0–L2（2026-09-21）

在 `dev-map` 按未提交的静态路线规划实现。新增 V42 / V43（原编号 V41 / V42；合并回主干时因
F0-DEBT-01 的 `V41__scm_product_image_drop_file_url` 已被真实开发库应用，按「保留已应用版本号」
先例重排），保留现有主线与旧迁移。
详见 [实现与配置说明](delivery-static-route-implementation.md)。

验证范围遵循本轮“不要做过多测试”：后端编译、前端类型诊断、Docker Desktop PostgreSQL 独立库中的 2 个定向集成场景。
真实高德地图与浏览器操作、打印机效果、并发压力测试未验证；地图 Key / 安全代理由用户后续配置。
本期不接发货扣库存，不做 GPS 或签收；L3 仍需先明确实发量来源。

本轮验证结果：

- 后端 `mvn -pl sa-admin -am -DskipTests compile` 通过；前端 `npm run build` 通过。
- 验证跑在本地 Docker 的 PostgreSQL / Redis 上；单独新建的一次性临时库，Flyway 已升级至当时的 V42（= 重排后的 V43）。
- `DeliveryRouteServiceIT` **2/2 通过**（0 failure / 0 error）：同客户同地址聚合、重复分配拒绝、旧版本拒绝、排序落库、定位与同坐标系门槛、规划后锁定、历史快照不漂移、打印商品、规划不写库存、取消保留历史并释放订单、空停靠点移除与重新加入。未做并发压力测试。
- 测试记录自动回滚；独立测试库已清理，日常开发库未执行本次迁移。后续启动此分支后端时由 Flyway 应用 V42–V43。
- 前端全库类型检查仍有 **1946 条**诊断，集中在既有文件；**本次修改 / 新增文件 0 条**，不宣称全库类型检查通过。UI 静态检测 0 findings。
- 构建存在已有依赖资源 `icon.svg` 与大分包提醒；没有因此扩大范围重构。

## V43 部署后验收（2026-09-21）

对本地开发栈的 V43 运行实例（后端为 `35e0fa4` 构建、前端 Vite dev、schema `xsy_v2`）做完整验收：
Flyway V41 / V42 / V43 均 `success = true`（V42/V43 于 16:22 应用到日常开发库，即上述「未执行」已过去）。
35 条路由加载扫描 0 console error / 0 pageerror / 0 HTTP≥400；V41 图片链路可用且 `private/*` 绑定按 `40038` 拒绝；
配送 L0–L2 主流程与 10 条负向用例全部按预期收敛，规划与打印对库存 / 出库 / 订单状态零副作用（同一条 SQL 前后逐值相同）。

计数：共执行 80 项 = PASS 67 / FAIL 5 / BLOCKED 2 / NOT TESTED 6；**P0 = P1 = P2 = 0，P3 = 6**（无阻断项）。
未覆盖：高德可视化与地理编码（Key 未配置）、打印机物理输出、非管理员授权负向用例、并发抢单、`41109` 上限、对象存储模式权限语义、远端 `xsy.leyingiot.com` 实例。
判定可进入 L3，但 L3 前需处理订单定点小数契约（`OrderValidator.decimal` 与 `ScmDecimalStrings.PATTERN` 不一致）、确认远端实例版本，并补非管理员授权回归 + F0-DEBT-01 读侧收口。

完整矩阵、证据与缺陷记录见 [V43 部署后验收报告](test-report/2026-09-21-v43-deployment-acceptance.md)。

### P3-1 修复：订单定点小数契约（同日落地）

`OrderValidator.decimal` 不再自己写 `[0-9]{1,14}\.[0-9]{4}`，改为委托 `ScmDecimalStrings.parseScale4Required`
—— 订单域此前的「恰好 4 位小数」比全项目唯一规则更严，`"10"` / `"1.5"` 这类合法字符串会被拒。
形态与非正拆成两个码：新增 `ORDER_QUANTITY_FORMAT_INVALID(40076)`，`40063` 回归「数量必须大于零」，
`40066` 文案由「四位定点数」改为「非负、至多 4 位小数」（编号不动）。返回值仍统一 `setScale(4)`，
落库精度与对外序列化形态不变。采购域的同类严格形态有 W5 类头设计依据，未一并改动。
验证：`OrderRulesTest` 32/32、`OrderWebTest` 6/6、`SalesOrderImportServiceTest` 15/15、
`SalesOrderServiceIT` 12/12、`OrderUnpricedIT` 1/1（一次性临时库，V1→V43 全量迁移，用后删除）。

跑该批 IT 时暴露一条与本次改动无关的既有问题（记为 P3-7）：`ScmOrderMigrationIT` 断言「订单 8 表所有
numeric 列 = `NUMERIC(18,4)`」，而 V42 给 `order_address_snapshot` 加了 `NUMERIC(11,8)` / `NUMERIC(10,8)`
经纬度列，因此该用例自 V42 起在任何已迁移库上常红。已于 2026-09-22 收口：排除项改为 `(表,列)` 组合谓词，
只豁免 `order_address_snapshot` 的两个坐标列（按列名全局排除会让同名列在任何订单表上逃过金额精度校验），
并正向断言这两列保持 `11,8` / `10,8`。

### 2026-09-22 已应用迁移字节还原、库存对账判据收窄与校验和守卫

**阻断项（仓库级）**：`8c0ab90`「style: format source files」重排了 41 个**已应用** Flyway 迁移的字节。
Flyway 校验和按行内容算 CRC32，不看 SQL 语义，因此格式化后的工作副本让**任何**存量库在启动期
`flyway.validate()` 失败（`FlywayValidateException`），库存 IT 也在 Spring 上下文阶段就报错，看起来像业务缺陷。
处理方式是仓库侧还原字节：迁移目录恢复后与格式化前逐字节一致，只剩该提交之后新增或改号的迁移不同。
未执行 `flyway repair` —— 那是改历史去迁就错误的文件字节，方向相反；已应用迁移依旧不可编辑。

**防再犯**：新增 `tools/migration_checksum_guard.py` 与 `tools/migration_checksum_snapshot.json`（冻结 43 条校验和）。
`check` 把内容漂移与迁移消失判为阻断，改号判失败，重号和非法文件名直接报错，新增未应用的迁移只提示不阻断；
`sync` 未给 `--force` 时拒绝覆盖已冻结的校验和。算法复刻 Flyway 11 的 `ChecksumCalculator`
（逐行 UTF-8 字节累计 CRC32、去 BOM、不含行分隔符、末尾 `long → int` 有符号截断），因此与 CRLF/LF 无关，
`.gitattributes` 按文件钉住迁移 SQL 检出换行的既有约定不受影响。快照不自证：与一台按格式化前字节迁移过的
长驻开发库的 `flyway_schema_history` 逐条比对，**43/43 相同**。`tools/verify.py` 在后端阶段前跑一次守卫，
避免把这类失败拖进分钟级的 Surefire 运行；`tools/test_verification.py` 增 6 条守卫自测
（含「改号必须失败」「重号必须报错」「`sync` 不得为变绿而覆盖基线」）。

**库存对账判据收窄**：`ScmW6PgITBase` 不再重放 V19 Step 4 的全库对账原文，改为按 `(warehouse, sku)` 的方向净额对账
（方向从 `ck_inventory_movement_snap` 的快照等式推导，不写 `movement_type` 清单，与 V37 同一取向）。
判据做过可判别性验证：同一份干净库上原全库判据报 3 组、方向净额化后只剩 1 组，而那组是
`ScmInventoryStocktakeRollbackIT` 故意提交的 `quantity = 3` 夹具 —— 误报来自判据过期，不是脏数据。
V19 本身不改：出库类流水类型在 V19 之后才出现，Step 4 在它自己的时刻是自洽的。
同一轮补掉一条**假绿**：`id` 传错时流水与余额两侧聚合皆为 `NULL`，差额判据会静默通过；现在先要求活动余额行存在，
并把「绕过流水改账面必须报错」「查不存在的 `(warehouse, sku)` 必须报错」固化成用例。

**验证**：定向 `ScmInventory*IT + ScmOrderMigrationIT` 11 项全绿，工具自测 12 项 OK，守卫 `check` 为
drift 0 / missing 0；全量后端回归 `mvn -pl sa-admin -am test` 在一台从 V1 整链迁移的一次性临时库上
→ **751 项，Failures 1、Errors 0、Skipped 5**（5 项为未配置对象存储环境的 cloud IT，按既有约定跳过）。
本轮未跑前端与浏览器验收：改动只落在迁移字节、测试判据与工具链，没有触及页面或接口行为。

**P3-8 同日修掉（顺延编号，非本批引入）**：全量回归唯一失败是
`ScmGeoMigrationIT.masterGeoColumnsRejectHalfCoordinatesAndUnknownCrs`。根因不是数据也不是迁移，而是 V42 的聚合约束
`ck_<table>_location_complete` 把 V40 的成对、取值域与 CRS 白名单规则整体重写了一遍：一行负向数据同时违反
细粒度约束和聚合约束，而 PostgreSQL 只报它先求值到的那一条，于是「必须报出 V40 那个约束名」的断言变成依赖求值顺序。
V42 之前没有第二个覆盖同列的约束，所以 M0 当时验过是绿的。

原拟修法「让每个负向用例只违反一个约束」经核对**不可达** —— 越界、半套坐标、未知 CRS 任一种都必然连带违反聚合约束。
落地改成两层：负向用例断言拒绝出自「该规则的细粒度约束或该表的聚合约束」这一小组合法名字（仍能挡住列名写错、
语法错误、无关 NOT NULL 这类误因）；另新增 `geoCheckConstraintsExistOnEveryMaster` 逐表钉住 V40 五条细粒度 CHECK
与 V42 聚合 CHECK 的存在性，删掉任何一条都会变红 —— 放宽名字就必须下面有兜底。同轮补强：经度越界用例配齐合法 CRS
（让拒绝原因归到取值域本身，而不是掺杂缺 CRS），并新增纬度越界用例（原来只测经度侧）。

修的过程中查出一个此前没人记录的事实：**V42 的聚合约束只加了 `warehouse` / `customer` / `order_address_snapshot`，
`supplier` 没有**（配送链路不用供应商地址），所以 supplier 的地理守卫只有 V40 细粒度五条 —— 这正是三张主档里只有两张
报出聚合约束名的原因。该不对称已写成常量与逐表断言，不再靠巧合。
验证：`ScmGeoMigrationIT` 7/7（一次性临时库，V1→V43 整链）。随后在全新一次性临时库重跑全量后端回归
→ **752 项，Failures 0、Errors 0、Skipped 5，BUILD SUCCESS**（5 项仍是未配置对象存储环境的 cloud IT）。
