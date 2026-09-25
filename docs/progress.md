# 项目进度

最后更新：2026-09-26

## 当前状态

| 阶段 | 状态 | 记录 |
| --- | --- | --- |
| P0 基线收口 | **完成**（FA-1 / FA-2 / FA-2b / FA-3 全部落地；对象存储模式保密性已实测并据此修掉一处真实授权缺陷；正式非管理员角色、显式数据范围、库存并发与 Delivery L0–L2 均已通过真实角色浏览器验收） | 见「2026-09-24 P0 基线收口（第三批）」「（第二批）」「（第一批）」 |
| P1 分拣管理 | **完成**（V60–V62；后端全量 1057 项 0 失败 0 错误、浏览器 129/0/8、前端四闸门全绿；实发事实不回写订单、不写库存；配送资格接分拣完成事实） | 见「2026-09-24 P1 分拣管理」；裁决第 1–22 条 |
| P2 物流配送 L3 | **完成**（V63–V64；后端全量 1076 项 0 失败 0 错误、浏览器 136/8 按设计跳过（1 项未复现的既有夹具脆弱）、前端四闸门全绿；实发量取分拣 sorted_quantity，库存事实只由库存域一条原子命令产生） | 见「2026-09-25 P2 物流配送 L3」；裁决第 1–23 条 |
| Finance R1 应收与成本归属 | **F1-0.5 裁决收口 + F1-1 数据地基完成**（V65–V67：8 张财务事实表、13 个权限点、SCM_FINANCE 授权、Java 骨架、schema/权限契约 IT + 财务域只读契约测试）；F1-2…F1-8 未开始 | 27 条 Q 裁决 + 10 条全局不变量 + D-1…D-5 见 `docs/decisions.md`「P3 Finance R1 裁决」；设计见 `docs/plan/finance-r1-design.md`；落地记录见「2026-09-26 P3 Finance R1 F1-0.5 + F1-1」 |
| W0 底座 | 完成 | SmartAdmin 原生系统能力作为 V2 底座 |
| W1 商品 | 完成 | 商品、SKU、分类和价格基础能力 |
| W2 客户与供应商 | 完成 | 客户、供应商及关联主数据 |
| W3 定价 | 完成 | 客户价格和价格历史 |
| W4 销售订单 | 完成 | 订单、明细、状态和操作记录 |
| W5 采购 | 完成 | 采购需求、采购单、多次收货和最小仓库主数据 |
| W5.5 原生功能同步 | 完成 | SmartAdmin 原生功能与 SCM 品牌配置 |
| F0 对象存储 | 完成 | FileService、S3/MinIO 和访问保护 |
| W6-1 库存第一阶段 | 后端与浏览器已验证 | 余额、不可变流水、双入库模式、仓库生命周期、历史回填和只读查询页 |
| 出库 / 预留（V25–V27） | 写流程 E2E + 并发 IT 已验证 | 独立出库单、`SALES_OUT` 流水、可用量门槛、预留与释放、订单「预留库存」显式动作；**预留只有 `ACTIVE / RELEASED` 两个状态被真正产生，`CONSUMED` 无生产者**，消费端由 P2 收口 |
| 盘点（V29） | 写流程 E2E 已验证 | 盘点单、盘盈 / 盘亏流水、差异施加到确认瞬间的账面量、双下限保护 |
| 报损报溢（V30） | 写流程 E2E 已验证 | 报损报溢单、`LOSS_REPORT` / `GAIN_REPORT` 流水、审批状态机（待审核 → 已完成 / 已驳回）、审批乐观锁 |
| 调拨（V31） | 写流程 E2E 已验证 | 调拨单、两步式（发出 → 在途 → 收货）、`TRANSFER_OUT` / `TRANSFER_IN` 流水、两仓单位一致性、在途阻塞仓库停用、**成本随货平移（V37 修正转入清零）** |
| 阈值预警（V32） | 列表页浏览器已验证，推送未做 | 阈值配置（独立于余额表）、预警列表（按可用量读时算状态）、按异常默认过滤；「提醒」目前只是可查列表 |
| 规格转换（V33） | 写流程 E2E 已验证 | 转换单（整件拆零 / 组合拆分，跨 SKU 同仓库）、`CONVERT_OUT` / `CONVERT_IN` 流水、两行余额同一事务的全局锁序、审批乐观锁、**转入按转出腿总成本折算（V37）** |
| 移动加权成本（V34） | 后端与浏览器已验证 | `inventory_balance.avg_cost`（Q3 裁决变更）、入库加权 / 出库不变均价但流水带成本、期初回填、余额页均价与金额列、**V37 重放流水修正零成本余额** |
| B7 数据大屏（V28） | 后端已验证 + 浏览器已验证（V1 视觉版） | 经营/库存/采购/趋势四只读聚合、Screen Theme 1920×1080 等比缩放、10 面板 + 3 图趋势带、组件化拆分、Header 入口新窗口打开 |
| 商品中心 PCO-1 主档增强（V38–V39） | 后端与浏览器已验证 | 主档扩展字段与助记码搜索、计量单位 / 商品标签字典、列表高级筛选、批量上下架 / 改分类 / 打标签、商品与字典删除保护；Excel 与图片中心属 PCO-2 |
| 商品中心 PCO-2 导入导出 + 图片中心（V44–V45） | 后端 IT + 前端单测 / 类型 / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | Excel 模板下载 / 整批事务导入（CREATE + UPDATE 既存商品维护）/ 按条件导出、图片中心（`image_type` 图集 GALLERY / 详情 DETAIL 分组由 V49 重建、主图唯一性回到 `is_primary` 单一事实、单商品与按文件名批量维护）、独立菜单与权限；见追加记录 2026-09-22 |
| 采购订单缺口预览 Wave 2A（无迁移） | 后端 IT + 前端契约 / 类型 / Lint 已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 只读「订单汇总 / 库存缺口预览」并入采购需求页 Tab、`POST /scm/purchase/demand/summary-preview` 复用 `generate()` 取数口径、缺口与可用量后端算好、0 迁移 0 菜单变更；见追加记录 2026-09-22 |
| 采购操作效率 Wave 2B（无迁移） | 后端单元 4/4 + IT 6/6 + 前端契约 8/8 / 类型 / Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 批量少收关单（整批原子、version 冲突显式拒绝）、采购单后端算列导出 + 前端本地记忆列勾选、纯前端打印、收货「按单据 / 按商品」双视角只读工作台；0 迁移 0 新权限，复用 `scm:purchase:short-close` / `:query` / `:receipt:query`；见追加记录 2026-09-22 |
| 订单录单效率 Wave 3（无迁移） | 后端 Web 7/7 + IT 2/2 + 单元 1/1 + 前端契约 5/5 + 模型 6/6 / 类型 / Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 草稿本地恢复、历史订单「复用为新单」（只读 detail + 当前价重解，不引后端复制命令）、明细「最近已确认订单价」只读旁证（CONFIRMED-only、confirmed_at 倒序、订单分组 limit、单位不一致仅提示）；0 迁移 0 新权限，复用 `scm:order:query` / `:add`；见追加记录 2026-09-22 |
| 业务待办与站内提醒 Wave 4（V46） | 后端单元 4/4 + 报损报溢 IT 15/15 + 前端契约 4/4 / 类型 / Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 首页「业务待办」只读 Pull（`GET /scm/dashboard/todo`，按登录人权限裁剪卡片、复用各领域既有分页查询读 total，无权卡片省略、有权零任务返回 0，不写任何业务表）；报损报溢驳回经原生 `t_message` 站内信通知录单人（同步写在驳回事务内、恰一条，不新建第二套消息中心/消息表）；1 data-only 迁移（菜单/权限 1100-1101 `scm:todo:query`，仅授 SUPER_ADMIN），库存读写路径 / Q7 / Q13 / 状态机零改动；见追加记录 2026-09-22 |
| 配送打印追踪 Wave 5（V47） | 后端 IT 5/5（打印 3 + 线路 2）+ 前端契约 4/4 + 合并单测 131/131 / 类型（本 Wave 文件）/ Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 配送线路详情新增「配送打印」标签：**按订单 / 按客户两个只读视角**（`GET /routes/{id}/orders-view`、`/customers-view`，客户视角聚合打印状态 PRINTED/UNPRINTED/PARTIAL）+ 正式生成打印登记（`POST /routes/{id}/print/orders`、`/print/customers`，带 Idempotency-Key、复用通用 `idempotency_record`）；`delivery_route_order` 加 `print_count/last_printed_at/last_printed_by`（1 迁移、0 新表 0 新菜单/权限，复用 `scm:delivery:route:query`/`:print`）；打印仅计次、**不扣库存、不改线路状态、不做 L3 发车/出库/GPS/签收**；见追加记录 2026-09-22 |
| 客户 360° 业务上下文 Wave 7（无迁移） | 后端 IT 6/6 + 前端契约 4/4 + 合并单测 139/139 / 类型（本 Wave 文件）/ Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | 客户详情扩为 5 Tab（基础资料 / 最近订单 / 常购商品 / 协议价 / 可售商品）**只读整合、以同一 customerId 为上下文、复用各领域既有查询接口不建第二套**；仅新增 `GET /scm/customer/{id}/frequent-skus` 只读聚合（CONFIRMED-only、按 SKU+单位分组、订购量标注且**不跨单位求和**、最近成交价取**最新非均值**、空价不回退、同单重复 SKU 不增订单次数）；权限为 `scm:customer:query` ∧ `scm:order:query`（SaMode.AND，不因挂客户页降权）；0 迁移 0 新表 0 新权限；见追加记录 2026-09-22 |
| 操作日志业务上下文与表格 / 查询体验 Wave 8（无迁移） | 后端 PgIT 6/6 + 读权限 Guard 单元 6/6 + 前端契约 9/9 + 合并单测 148/148 / 类型（本 Wave 文件）/ Lint / 构建已验证；E2E 场景已于 2026-09-23 在全栈环境真实浏览器执行通过（见「Wave 1–8 审计修复与全栈验收」记录） | **A 通用操作日志按业务对象精确下钻**：`OperateLogQueryForm` 加 `businessType`/`businessId`、`OperateLogMapper.xml` 对 PRODUCT/CUSTOMER/DELIVERY_ROUTE 按既有 param/url 结构做 STRPOS 精确匹配（不新建审计表、不改写入侧），`AdminOperateLogController` 按业务类型白名单校验读权限、无匹配类型回 `1=0` 不放全表；前端 `operate-log-list.vue` 接收并校验路由业务类型 / ID，首载 / 刷新 / 换对象三处重套、重置不残留、脏行逐行 try/catch 退化，商品 / 客户 / 配送线路详情各带类型入口，`operate-log-mask.ts` 展示前递归脱敏 password/token 等，并由 `OperateLogParamMask` 在**写入侧服务端**对敏感字段名递归脱敏（结构保持、只处理 param 文本），库里存的即是脱敏值；**B 列表查询条件按用户本地记忆**：`query-filter-key.ts` + `query-filter-memory.ts` 复用既有 `xsy-scm:...:${employeeId}:...` 偏好约定（不建 user_preference 表），customer-list 接入（查询存 / 重置清 / 挂载恢复回第 1 页）、深链详情不接入避免污染；0 迁移 0 新表 0 新权限；见追加记录 2026-09-22 |
| 地图 M0 地理数据地基（V40） | 后端与浏览器已验证 | `scm_region` 省市两级字典（34 省 + 414 市，带区划质心 GCJ-02）、三张主档六列省市区快照 + `longitude/latitude/geom_crs`（成对与 CRS CHECK、市级部分索引）、迁移内保守地址解析回填、客户 / 供应商 / 仓库表单升级为省市区三级 |
| 地图 M1 大屏真实地图（无迁移） | 后端与浏览器已验证 | 官方省界 GeoJSON 存档进仓库、`GET /scm/screen/data/geo` 只读聚合（省级在 Java 侧由市上卷）、省界着色 + 市级气泡 + 未归属覆盖度；流向层 `lines` 未做 |
| F0-DEBT-01 FA-0 附件分级与写侧收口（V41） | 后端 + 浏览器已验收；**F0-DEBT-01 已随 FA-1～FA-3 于 2026-09-24 整体关闭** | 商品图片改上传 `public/image/`（新增 `PUBLIC_IMAGE(5)`）、`product_image` 新增/换绑只能引用公开前缀（`40038`）、删除 `product_image.file_url` 改为按 `file_key` 现算；`FileKeyVoSerializer` 旁路逐 key 过 `FileAccessGuard.filterReadable`（依赖未注入 / 无身份时 fail closed）；其后 FA-1 把 `FileService` 收成唯一受控入口、FA-2 落地 `t_file_relation`（V52）、FA-3 搬存量并加 CHECK（V58）—— 本行「未落地」的三项均已完成，见「2026-09-24 P0 基线收口（第三批）」 |
| 物流配送 L0–L2（V42–V43、V47） | **完成**（后端 IT + 真实浏览器 E2E 已验收，见「2026-09-24 P0 基线收口（第三批）」与下方 P0-B 记录） | 客户 / 仓库定位、订单地理快照、司机车辆、静态排线、规划锁定、取消释放、固定打印与打印计次；`DISPATCHED / COMPLETED` 只是 CHECK 里预留的值，L0–L2 无任何写入路径 —— 由 P2 承接 |
| 报表中心 R0 财务与报表只读地基（V50–V51） | 后端 IT + 前端单测 / Lint / 构建 + 浏览器 E2E 已验证 | 经营概览 / 销售 / 采购 / 收货与入库 / 库存五张只读分析页与 41 个只读端点；不建 receivable / payable / payment / voucher 任何事实表；见追加记录 2026-09-23 |
| W6-2 小程序 | 未开始 | 需先处理下方待办 |

## 当前待办

- ~~**F0-DEBT-01（未关闭）**~~ **已关闭**（2026-09-24，FA-1 / FA-2 / FA-2b / FA-3 全部落地，V52–V58）：
  商品图写侧绑定（V41）→ `FileService` 成为服务端展开 URL 的唯一受控入口（FA-1）→
  对象级授权落在通用 `support/file` 层的 `t_file_relation`（FA-2，V52）→
  未绑定上传落 `private/common/scratch/` 并按「无关系行且无业务列直接引用」双确认回收（FA-2b，V53）→
  存量商品图搬 `public/image/` 并下沉为数据库 CHECK（FA-3，V58）。
  `private/notice/`、`private/help-doc/` 的前缀级放行已移除。
  **仍然有效的两条约束**：对象搬运必须先成功再改库里的 key，顺序错了商品图静默 404；
  附件权限行为只在对象存储模式下取证（本地存储把 `/upload/**` 静态直出，「private」在本地不等于保密）。
- ~~明确正式非管理员角色、数据范围、多角色库存验证和多仓默认选择规则~~ **角色与数据范围已收口**
  （P0，V54–V57；P1 再补 `SCM_SORTER` / `SCM_STOREKEEPER_LEAD` 分拣授权，V62）。
  **仍未裁决**：多仓默认选择规则（订单无仓库字段，靠「唯一启用仓库」解析，见 41018）；
  E2E 临时账号只是取证手段，不等同正式业务角色。
- 库存深化剩余项：**已完成**（入库侧、出库/预留、盘点、报损报溢、调拨、阈值预警、规格转换、移动加权成本）。
  **主线顺序以 [`plan/pre-enhancement-mainline-development-guide.md`](./plan/pre-enhancement-mainline-development-guide.md)
  第 8–19 行为准**：… → P1 分拣 → **P2 物流配送 L3 → P3 Finance R1 → P4 Finance R2** → 营销 / 支付结算 → W6-2 小程序。
  早期需求清单里「财务与报表 → 分拣 → 物流配送 → 营销 …」那一行是分期方案成形前的旧排序，已失效。
- 出库 / 预留 / 盘点 / 报损报溢 / 调拨 / 阈值预警的**列表页**浏览器验收已于 2026-09-20 执行
  （与余额 / 流水 / 规格转换 / 数据大屏共 11 页全绿、0 pageerror）。
  **五条写流程 E2E 已于 2026-09-20 覆盖**（出库确认、盘点确认、报损报溢审批、调拨发出/收货、
  规格转换审批 + 只读账号越界），见下方「调拨 / 转换成本平移修复（V37）与库存写流程 E2E」。
- ~~调拨转入的成本为 0~~ **已修（2026-09-20，V37）**：转入成本改为取**同一明细行的转出腿
  `unit_cost`**，并与采购入库同样加权；规格转换的转入腿改为按转出腿**总成本 ÷ 目标数量**折算。
  开发库被清零的 3 行已由 V37 重放流水重算（6.20 / 132.00 / 2.60 回正）。
  链式转换的期初成本基准在审批开始时一次取齐并预解，环状引用按期初均价收敛且不写缓存。
- ~~预留的**并发**场景目前只有单线程 IT 覆盖（并发压测待补）~~ **已补**：
  `ScmInventoryReservationConcurrencyIT` 五条真并发用例（`Propagation.NOT_SUPPORTED` + 各自事务），
  并按用户要求定向重复 20 次作为进 P1 前的稳定性闸门；最初那次红是**用例自身的缺陷**
  （按下标对齐输赢方 + 非单调的 `source_document_item_id` 种子），已只改测试修好，产品不变量未动。
  仍然真实存在的缺口是**预留的消费端**：发车前没有任何流程产生 `CONSUMED`，由 P2 收口。
- 消息通知只做了一条事件：**报损报溢驳回**经 SmartAdmin 原生 `t_message` 站内信通知录单人
  （V46 起，随驳回事务同步写、恰一条），首页「业务待办」是只读 Pull。**其余事件仍无通知**：
  待审批、待入库、配送等仍靠用户自己进页面看状态；**外部渠道（短信 / 邮件 / 企业微信）未做**。
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
- **商品中心 PCO-2 已实现并通过全栈验收（Wave 1，V44–V45 + V49）**：Excel 导入（CREATE / UPDATE 两种模式）、
  导出、图片中心（`image_type` 图集 / 详情分组与批量维护）、独立菜单权限均已落地，后端 IT / 前端单测 / 类型 /
  生产构建 / 真实浏览器场景全部执行通过（见「2026-09-23 Wave 1–8 审计修复与全栈验收」）。
- **E2E 账号脚本不再猜库**：`tools/e2e_accounts.py` 原先有一个过期的默认库（停在 F0 之前），不带变量运行会在
  陈旧库里建临时账号、登录得到 `30001`。审计轮已改为**未显式提供 `XSY_V2_PG_DB` 即拒绝运行**（`sys.exit` 并说明原因），
  把「跑在错误的库上」从静默错误变成启动期失败。运行入口仍需带上该变量指向当前开发库。

## 追加记录

### 2026-09-24 P0 基线收口（第一批）：预留并发、FA-1 读侧收口、FA-2 关系授权、Delivery L0–L2 浏览器验收

- **基线与裁决**：`git fetch` 后本地 HEAD 与 `origin/main` 同为 `742f3a7`。本轮按用户 15 条正式裁决实施
  （写入 [`decisions.md`](./decisions.md)「P0 基线收口裁决」），其中 FA 上线顺序定为
  **FA-1 → FA-2 → 回归 → FA-3**，取代此前「先搬运再收口」的倾向；数据范围机制定为
  「显式、集中、fail-closed 的 SCM Scope Resolver」，不扩展底座 `@DataScope`。
- **Flyway**：新增 **1** 个迁移 `V52__sa_file_relation.sql`（`t_file_relation` 建表 + 五段存量关系回填），
  当前最大版本 V52、连续无空洞；`migration_checksum_guard.py check` PASS（历史 0 漂移 / 0 缺失 / 0 改名，
  新增号已 `sync` 落快照）。`ScmPurchaseMigrationIT` 的冻结版本清单同步追加 `52`。
  列宽按真实来源取 `VARCHAR(200)`（对齐 `t_file.file_key`），未沿用规划稿的 250。
- **P0-A 库存预留并发**：新增 `ScmInventoryReservationConcurrencyIT` **5 例**（真并发、`NOT_SUPPORTED` 无外层事务、
  多线程独立事务）：3×4 抢 10 只有 2 笔成功且第三笔报 41011；同一订单行并发重复预留只落 1 行 ACTIVE、
  占用不双计；并发释放同一预留恰一次回补；并发释放两条预留不丢失更新且可用量恢复后可出满 10；
  预留与出库并发争同一余额行时「可用量 = 现有量 − 预留量」口径唯一。
  每个用例都断言「成功数 + 失败错误码 + 最终账」三者一致，并用 `assertLedgerBalanced` 复核余额仍是流水净额。
- **FA-1 读侧收口**：`FileService` 现在是服务端展开 URL 的唯一受控入口 ——
  `getFileList(keys, requestUser)` 逐 key 过 `FileAccessGuard`，**不可读的 key 不出现**（不返回空 URL）；
  无身份 `getFileList(keys)` 只服务 `public/` 前缀，私有 key 不查询、不解析、不返回；
  写路径改用新的 `getFileMetadata(keys)`（只证存在性，永不产出 URL）。
  商品详情与图片中心的读路径改传当前请求人；`ProductImageSyncManager` 不再要求元数据里带 URL。
  删除了无引用且会把原始 key 回显给调用方的 `FileKeySerializer`；`FileKeyVoSerializer` 收口为调用受控入口
  （原直连 `FileService` 的批量入口已不存在于任何业务 VO 链路）。
  同步修掉一处真实缺陷：`AdminFileAccessIdentity` 在无 Sa-Token 上下文的线程（异步导出 / SmartJob / IT）
  调 `StpUtil` 会抛「上下文未初始化」，现按 fail-closed 返回非超管，不再让异常冒泡。
- **FA-2 关系授权（SCM + OA 一起做，不留半成品）**：`t_file_relation (file_key, biz_type, biz_id)` +
  三元组部分唯一索引 + 双向部分索引；`FileRelationBizTypeEnum` 白名单与 DB CHECK 同值；
  读判定改为「公开前缀 / 管理员 / 任一业务对象可读 / 上传者本人」四条，
  **`private/notice/`、`private/help-doc/` 的前缀级放行已移除**（原断言相应改写为负向用例）；
  「任一」而非「全部」，共享附件不会因某一方无权而对所有人不可见；库里出现未知 `biz_type` 时按不放行处理。
  业务可见性由 sa-admin 侧策略回答（sa-base 不依赖业务模块）：
  `PRODUCT` 取 `scm:product:query` **且 SPU 未删除**（删除商品不回收关系行，只看权限会留孤儿授权）、
  `NOTICE` 复用公告既有可见范围（全部可见或员工/部门命中）、`HELP_DOC` 要求确有该文档、
  `FEEDBACK`/`ENTERPRISE` 不额外放行（维持上传者/管理员，企业档案的查看权随正式业务角色一起落地）。
  五条写入链路在同一事务内绑定/换绑关系：商品图同步、公告新增与更新、企业新增与更新、帮助文档新增与更新、
  反馈新增；更新按**实际落库值**换绑（MyBatis-Plus 跳过 null 列，照表单值换绑会误收回旧附件的读取权）。
- **P0-B Delivery L0–L2 最终验收**：新增 `xsy-scm-web/e2e/scm-delivery.spec.ts`（8 条，真实浏览器 + 真实 PG，
  夹具全部自建、`afterAll` 取消线路而非删除）。**0 迁移、0 新权限、0 新端点。**
- **验证（本轮实跑）**：受影响类定向 **93 项全绿 / 0 失败 / 0 错误 / 0 跳过**（一次性干净库 V1→V52）——
  `ScmInventoryReservationConcurrencyIT` 5、`FileRelationPgIT` 6、`FileAccessGuardTest` 32、
  `FileServiceReadTest` 4、`FileKeyVoSerializerTest` 4、`ProductPgIT` 21、`ProductImageCenterPgIT` 16、
  `ScmPurchaseMigrationIT` 4、`SmartAdminMapperPgValidationIT` 1（该校验器把每条 mapper 语句拿去 PostgreSQL
  `PREPARE`，正是它抓出我新写的 `IS NOT NULL` 裸参数无法推断类型）。
  前端 `npm run lint` 0 error（3 条既有 warning）、`npm run test` 全绿、
  `tools/ts_baseline_ratchet.py check` PASS 且新增诊断 0；新 spec 单独 `tsc --noEmit` 干净。
  后端全量回归与浏览器执行结果见本节末「最终闸门」。
- **过程中由验证暴露的自身缺陷（都已改，不改断言强度）**：`-Dtest` 用 `+` 分隔不生效（必须逗号）；
  裸 SQL 里的 `?`（含 PostgreSQL `?:` 简写）会被 JDBC 当参数占位符；
  `TransactionTemplate` 缺失会让「命令服务要求调用方持事务」的异常冒充 41011 业务拒绝（假绿）；
  旁路 `UPDATE` 后仍用 mapper 读回会命中 MyBatis 一级缓存。
- **未完成 / 明确不算已验收**（本条写于第一批，其中 ① 与 ⑤ 已在第二批完成，见下一节）：
  ① FA-2 的第二段未做——`private/common/scratch/` 暂存前缀当前**无法经上传接口产生**
  （`folderType` 白名单 1–5，`COMMON` 固定落 `private/common/`），D-2 的「7 天回收 + 每用户 100 上限」
  需要一个新枚举值与 SmartJob 清理任务，尚未实现；
  ② FA-3 存量商品图搬运未开始（按裁决排在 FA-2 回归之后），因此 Java 侧「新绑定必须公开」的判断
  与 `ProductImageSyncManager` 的历史 key 例外都还在；
  ③ 删除类入口不回收关系行（商品/公告/帮助文档/企业删除后行仍在），当前靠策略侧的
  「对象必须存活」判定失效，属可接受但不是目标形态；
  ④ **权限行为的保密性仍未在对象存储模式下取证**：本轮全部断言跑在本地存储 + 本地 PostgreSQL 上，
  本地模式 `/upload/**` 静态直出无守卫，`t_notice` 一类预签名语义要等 MinIO 模式单独跑一轮；
  ⑤ ~~正式非管理员角色与 `employee_warehouse_scope`、`delivery_driver.employee_id`、
  `purchaser_id`/`seller_id` 服务端强制等 schema 变更均未开始~~ → 第二批已交付。

### 2026-09-24 P0 基线收口（第二批）：FA-2b 暂存回收、正式非管理员角色与显式数据范围

- **基线**：`git fetch` 后本地 HEAD 与 `origin/main` 同为 `742f3a7`（远端是本地祖先）。
  本轮开工前最大 Flyway 为 V51，第一批补到 V52，本轮再加 V53–V56，**V52 及以前 0 改动**。
- **FA-2b 暂存生命周期（裁决 D-2）**：新增 `FileFolderTypeEnum.SCRATCH(6)` → `private/common/scratch/`
  成为唯一可产生「未绑定暂存」的目录；上传时按用户统计未绑定暂存数，
  **超过 100 直接拒绝**（`SCRATCH_MAX_UNBOUND_PER_USER`，超限不会静默覆盖旧暂存）；
  `FileScratchCleanupJob`（SmartJob，V53 幂等种子：cron `0 30 3 * * ?`、参数保留天数默认 7、启用）
  每批 200 条，删除前**双重确认**「无任何关系行」且「没有任何业务表直接引用该 key」
  （业务列清单在代码里显式登记，含 `product_image.file_key`、`t_notice.attachment`、`t_help_doc.attachment`、
  `t_feedback.feedback_attachment`、`t_oa_enterprise.enterprise_logo/business_license`、`t_employee.avatar`），
  先删对象再删 `t_file` 行。`FileScratchCleanupPgIT` 3 例钉住这三条不可逆语义。
- **P0-F schema（V54）**：`employee_warehouse_scope`（一人一仓一行、活动行部分唯一索引、
  反向部分索引、无外键）+ `delivery_driver.employee_id`（`CHECK > 0`、
  `uk_delivery_driver_active_employee ... WHERE deleted = FALSE AND employee_id IS NOT NULL`）。
- **P0-F 权限点（V55，菜单块 1300–1349）**：分配权 `scm:customer:assign` / `scm:purchase:assign`；
  范围放宽 `scm:customer|order|purchase|inventory|delivery:scope:all:query`；
  仓库授权维护 `scm:warehouse:scope:query|update`；司机金额独立可见性 `scm:delivery:amount:query`。
  全部 `api_perms == web_perms`，只授 SUPER_ADMIN，正式角色在 V56 按需分配。
- **P0-F 正式角色（V56，按 role_code 种、不硬编码 role_id）**：
  `SCM_SALES` / `SCM_SALES_LEAD` / `SCM_PURCHASER` / `SCM_PURCHASER_LEAD` /
  `SCM_STOREKEEPER` / `SCM_STOREKEEPER_LEAD` / `SCM_DISPATCHER` / `SCM_DRIVER` / `SCM_FINANCE`
  九个角色与逐域菜单授权矩阵；**分拣角色按裁决第 11 条不建**。
  授权矩阵是本轮按既有领域边界配置出来的实现基线，需业务复核（见 decisions 未决事项）。
- **范围解析机制**：`common/scope/ScmValueScope`（`all` 与 `none` 都是显式值，无「null = 全部」第三态）+
  `ScmDataScopeContext`（仓库 / 客户业务员 / 订单业务员 / 采购员 / 司机五个维度 + `costVisible`）+
  `ScmDataScopeService.resolve()`（集中解析、每请求一次、无登录态即 fail-closed、
  `administratorFlag` 保留 break-glass）+ `ScmDataScopeException`
  （由 `ScmExceptionHandler` 映射为 30005，与功能权限不足同一信封，不给探测主键的差异化信号）。
  刻意**不用**底座 `@DataScope`：实测空清单与 `ALL` 返回空串即不加限制、`ME` 硬编码
  `create_user_id`（SCM 全库只有 `created_by VARCHAR(64)` 存 `"userType:employeeId"`）、
  且其 SQL 改写在 CTE 语句上不安全。
- **读侧下传**：18 个 mapper 语句 + 报表 `ReportDao.xml` 的 17 条聚合/明细语句按上式收口，
  报表的三条共享片段（`purchaseFilters` / `purchaseInboundMovementFrom` / `lossMovementWhere`）
  承载范围谓词并注明别名契约；无授权直接返回空分页（`total=0`、`emptyFlag=true`）而不是 `IN ()`；
  聚合类端点在「本维度必然为空」时把归属字段留 `null`（页面渲染 `—`），不写 0。
  销售类报表**刻意不按仓库收窄**：`sales_order` 没有仓库列，且不允许拿 `created_by` 顶替。
- **写侧守卫**：`ScmWarehouseScopeGuard.require/requireAll/requireAny` 挂到库存族 33 个命令调用点
  （出库建/改/确认/取消/删、盘点建/改/确认/取消/删/导入入口、报损报溢建/改/审/驳/删、
  规格转换建/改/审/驳/删、预留释放、阈值建/改/删、收货上架），
  判定一律取**已落库行**的仓库而非表单值；调拨按裁决分裂：建/改要求两端、`ship` 只看 `from`、
  `receive` 只看 `to`、草稿取消/删除沿用查询侧的任一端语义。守卫在状态/版本/自审校验之前，
  越界请求不会先 answers 更精确的问题。
- **负责人口径修正（裁决第 6、7 条）**：新建客户的 `seller_id` 与新建采购单/需求的 `purchaser_id`
  由服务端强制为当前员工，客户端传入值被忽略；改派走独立端点
  `POST /scm/customer/reassignSeller`、`POST /scm/purchase/reassign`
  （`@SaCheckPermission` 分配权 + `@OperateLog` + 乐观锁 `version`，冲突 40921）；
  `/update` 一律不再顺手改归属。订单创建时若所选客户不在调用者业务员范围内则拒绝。
- **字段级管控**：库存余额 / 流水 / 报表的成本与金额列在无 `scm:report:cost:query` 时置 `null`（复用
  `ScmReportAccess.maskCost`，语义是「无权知道」而非「确实是 0」）；
  配送读接口新增 `DeliveryVisibility`，缺 `scm:delivery:amount:query` 时把线路合计、
  停靠点合计、订单金额快照、候选订单金额一并抹为 `null`，前端 `money()` 渲染 `—`。
- **API 新增**：`GET /scm/warehouse/scope/employees`、`GET /scm/warehouse/scope/warehouses`、
  `POST /scm/warehouse/scope/update`（整组替换语义，空数组即回收全部）；
  客户/采购改派端点；司机表单新增 `employeeId` 且「启用前必须绑定员工」在服务端判定。
- **前端**：仓库管理页新增「授权维护 / 授权员工」（`warehouse-scope-modal.vue`，员工维度整组替换）；
  客户列表与采购单列表新增「改派」动作并把表单里的业务员/采购员字段改成诚实态
  （无分配权时新建不可编辑、编辑只读并指向改派）；司机管理新增「绑定员工」列与启用前必填校验；
  客户/采购/线路三页的空态文案区分「授权范围内没有」与「系统没有数据」。
  顺带修掉一处会**直接打断构建**的缺陷：`candidate-order-modal.vue` 引用了未导入的
  `useDeliveryPermission`（SCM 零错误区 TS2304）。
- **E2E 账号能力**：`tools/e2e_accounts.py` 支持 `E2E_BUSINESS_ROLES` 为**已存在的正式角色**
  建 `administrator_flag=false` 的临时账号（角色缺失即拒绝执行，避免「全 30005」假绿），
  并支持 `E2E_SECOND_ADMIN` 建第二个管理员账号（报损报溢禁止自建自审后，审批链用例必须有第二个人）；
  `tools/w8_e2e_accounts.py` 为本波次薄封装；TS 侧 `provisionTempAccounts` 解析脚本回显拿到
  `角色码 → login_name / employee_id`，不在两边重复实现名字派生规则。
- **最终闸门（本轮实跑，第二批）**：
  - 后端全量回归 **988 项 / 1 失败 / 0 错误 / 5 跳过**（一次性干净库 V1→V56）。唯一失败是
    `ScmDeliveryDataScopePgIT.employeeSeesOnlyRoutesOfBoundDriver` 的**测试自身隔离缺陷**：
    全量范围分支用 `containsExactlyInAnyOrder` 断言「库里线路恰好等于本用例的三条夹具」，
    而同一 IT 库里其他类留下的线路本就应该被全量范围看到 —— 已改成 `contains(r1, r2, r3)`
    （产品语义没被削弱：普通司机的 `containsExactly(自己那一条)` 与逐接口 30005 断言原样保留）。
  - 浏览器：本轮 8 个 spec 复跑 **55 通过 / 2 失败**，两处都已定位为夹具问题并修正 ——
    `scm-delivery` 用例 2 里「重复编码」的反例被新增的「启用必须绑定员工」校验抢先返回 41113
    （改为以 DISABLED 提交，唯一性判定才是被验的那一条），且 `scm-report` 用例 2 的
    **既有 UTC 日期缺陷**（`toISOString().slice(0,10)`）在东八区跨天时把当天确认的订单正当排除，
    改为 Asia/Shanghai 业务日后 11/11 通过。修正后单独复跑：报表 11 条全绿。
  - 之前一轮全量浏览器（18 个 spec / 67 条）为 **39 通过 / 5 失败 / 23 未跑**，
    其中 3 处是本轮新规则打到的旧夹具（自建自审 ×2、司机必须绑定 ×1，均已修），
    2 处是并发跑 IT 时的资源竞争（订单导入与报表在 10s 断言窗口内没跑完），
    复跑时订单与其余 spec 全绿、后端日志全程 0 条 ERROR。
  - 修正后在同一构建上复跑：`ScmDeliveryDataScopePgIT` **8/8**、`scm-delivery.spec.ts` **8/8**、
    `scm-report.spec.ts` **11/11**；连同此前的 `scm-dashboard-todo` 8/8、`scm-inventory-write` 6/6、
    `scm-data-scope` 6/6、`scm-delivery-print` 6/6，P0-F 相关 IT 与浏览器用例已全绿。
    全量后端回归的 988 项只输在那一个 IT 隔离缺陷上（改的是断言不是产品语义）。
    **随后在另一座一次性干净库（V1→V56，库名 `xsy_scm_it_p0all`）重跑全量：988 项 / 0 失败 / 0 错误 / 5 跳过，BUILD SUCCESS** —— 这是第二批的整库口径。
- 为**已存在的正式角色**
  建 `administrator_flag=false` 的临时账号（角色缺失即拒绝执行，避免「全 30005」假绿），
  `tools/w8_e2e_accounts.py` 为本波次薄封装；TS 侧 `provisionTempAccounts` 解析脚本回显拿到
  `角色码 → login_name / employee_id`，不在两边重复实现名字派生规则。
- **验证（本轮实跑）**：
  - 新增 `e2e/scm-data-scope.spec.ts` **6/6 全绿**（真实浏览器 + 真实登录 + 真实 PG，
    断言账号全部 `administrator_flag=false`）：授权维护换仓即换可见行且**不重登立即生效**、
    无授权空分页、仓管看不到「授权维护」按钮、用户自带 `warehouseId` 只能缩小、
    `*:scope:all:query` 是显式授予的范围值而非默认、客户改派可见性翻转与过期 `version` 被拒、
    司机绑定唯一（第二条撞约束）、司机只看自己线路且查不到全量候选、
    成本权限与仓库范围互不隐含、财务报表随授权仓收窄。
  - 既有配送两条 spec 一起跑 **14/14 全绿**（`scm-delivery.spec.ts` 8 + `scm-delivery-print.spec.ts` 6）。
  - **角色授权矩阵取证**（查 `t_role_menu × t_menu`，非人工推断）：五个基础业务角色
    `SCM_SALES` / `SCM_PURCHASER` / `SCM_STOREKEEPER` / `SCM_DRIVER` 均**不**持有任何
    `*:scope:all:query`；`scm:report:cost:query` 只在 `SCM_FINANCE` 与 `SCM_STOREKEEPER_LEAD`；
    两个分配权各只落在自己的主管角色；`scm:delivery:amount|scope:all:query` 只在 `SCM_DISPATCHER`；
    `SCM_FINANCE` 持三条跨负责人范围但**不持** `scm:inventory:scope:all:query`
    ——它的仓库范围只能由授权行配置出来，正是裁决第 10 条要的「范围是配置值，不是代码写死」。
  - 新增数据范围/写守卫 IT：`ScmInventoryDataScopePgIT` 8/8、`ScmInventoryWriteScopePgIT` 10/10、
    `ScmPurchaseDataScopePgIT` 13/13、`ScmDeliveryDataScopePgIT` 8/8、`ScmReportDataScopePgIT` 8/8、
    `ScmOrderCustomerDataScopePgIT` 17 例。
  - 前端：`npm run lint` 0 error（3 条既有 warning）、`npm run test` **223/223**、
    `tools/ts_baseline_ratchet.py check` PASS（新增 0、SCM 区 0、附带修掉 26 条既有诊断）、
    `npm run build` 成功。
  - 后端全量回归见本节末「最终闸门」。
- **过程中由验证暴露的缺陷（都已改，不改断言强度）**：
  `BusinessException(UserErrorCode.NO_PERMISSION)` 经底座全局处理器会降级成 **10001** 而不是 30005
  （构造器只取 msg、丢弃 code）——改为专用 `ScmDataScopeException` + SCM 自己的 `@ExceptionHandler`；
  数据范围测试夹具以 `employeeId=1` 建 `RequestEmployee` 却没带 `administratorFlag`，
  与新写守卫冲突（并发用例被守卫挡住，等于没测到竞态）；
  一条 IT 断言写成 `isExactlyInstanceOf(BusinessException)`，其子类出现即红；
  xlsx 用 `ZipInputStream` 读会因 data descriptor 抛 `invalid entry size`，改 `ZipFile`；
  同一员工并发会话（Playwright 里二次登录）会把已建客户端顶成 30007；
  `CustomerVO` 的行键是 `customerId` 而非 `id`，写成 `id` 得到的是 NaN 路径 + 「参数错误」；
  分页上限 100，`pageSize: 500` 直接 30001。
- **未完成 / 明确不算已验收**：
  ① **FA-3 存量商品图搬运仍未做**，因此 Java 侧「新绑定必须公开」判断与历史 key 例外保留；
  ② **对象存储模式下的保密性取证未跑**（当前全部断言在本地存储 + 本地 PG；
  本地 `/upload/**` 静态直出无守卫，「private」在本地不等于保密）；
  ③ 数据大屏 `module/scm/screen` 的聚合读**尚未接范围**：今天 `scm:screen:query` 只授 SUPER_ADMIN
  因此无实际泄漏路径，但任何业务角色一旦被授大屏权限，这条就是洞，必须在其之前补；
  ④ 采购的两条聚合读**已收口**（`summaryPreview` 整页短路 + `receiptItemWorkbench` 按授权仓过滤，
  `generate()` 命令路径显式传 `all()` 不受影响，IT 从 13 例扩到 17 例）；
  收口过程中暴露出两处**需要业务裁决而不能自行实现**的边界：
  (a) 收货确认 `receipt.confirm` 在 `DIRECT` 模式下同事务写 `PURCHASE_IN`，
  但仓库守卫只挂在 `putaway` 上 —— 第 8 条把「收货」列进必须落在授权仓内的清单，
  而第 7 条把采购员的可见性定义为 `purchaser_id`；若给 `confirm` 加仓库守卫，
  未配授权仓行的采购员就**无法确认自己创建的收货单**，因此留给人裁决，不自行选边；
  (b) `GET /scm/warehouse/list` 仍不按授权仓收窄（它是所有表单的仓库选择器，
  收窄后任何新单据都无法选仓），代价是该接口会披露调用者读不到数据的仓库名称与存在性；
  (c) `purchase_receipt` / `purchase_demand` 的列表与明细只按 purchaser 收窄、不按仓库收窄，
  与 (a) 是同一条口径问题。
  ⑤ 删除类入口仍不回收 `t_file_relation` 行（靠策略侧「对象必须存活」失效）；
  ⑥ 预留的**触发点**仍按裁决未挂订单确认（`reserve` 不做仓库守卫，其仓库来自
  「唯一启用仓库」解析，见 decisions 未决事项）。

### 2026-09-24 P0 基线收口（第三批）：三条新裁决落地、FA-3 收口、对象存储保密性取证

> P0 的两道遗留入口（FA-3、对象存储模式取证）在本批关闭，另有三条口径由用户裁决后落地。
> 全量闸门数字见本节末「验证」。

- **基线**：HEAD 仍为 `742f3a7`，**全部改动未提交、未推送**（用户禁令）。工作树约 209 项变更。
  Flyway：本轮开工前最大 V56，新增 **V57**（角色矩阵增量，data-only）与 **V58**（FA-3 商品图 key 搬运
  + `ck_product_image_public_file_key`），`migration_checksum_guard.py sync` 已把 57/58 冻进快照
  （frozen=58，`check` PASS），`ScmPurchaseMigrationIT` 的冻结版本清单同步追加。
- **三条裁决已落地**（全文见 [`decisions.md`](./decisions.md)「P0 基线收口裁决（第二批，2026-09-24）」
  第 16–19 条）：
  1. 「活动司机」= `deleted=false`：**V54 保持现状，没有新迁移**；`DISABLED` 不解除绑定，
     而「停用司机不得分配新线路」由 `DeliveryRouteService` 的 `MASTER_DISABLED` 判据承担（已核实存在）。
  2. 角色矩阵：V56 不可改，差值补在 **V57** —— 销售主管补退货批准(623)/驳回(624)/退款完成(632)，
     财务补 `scm:inventory:scope:all:query`(1331)；采购员的库存**数量**权 V56 已给（811），
     成本列由独立的 `scm:report:cost:query`(1215) 控制且采购员不持有 → 「数量权 ≠ 成本权」无需变更，
     只在迁移注释与 `ScmBusinessRoleMatrixPgIT` 里钉住口径。
  3. `receipt.confirm` 的 `DIRECT` 取「采购范围 ∩ 仓库范围」交集：新增
     `PurchaseOwnerResolver#requireVisible`，并落在 `PurchaseOrderService#lockOrder`
     （一次覆盖编辑/改派/提交/取消/少收关单）+ `delete`、收货单 create/update/confirm/delete、
     需求分配的**两头**；`WAREHOUSE_CONFIRM` 模式确认时不写库存，仓库判定仍留在 `putaway`。
  4. 仓库选择器收窄：`/scm/warehouse/list`、`/query`、`/detail` 三个读入口都按仓库授权范围过滤，
     未授权仓的 id / 名称 / 地址一律不给；历史单据继续读自己行上的
     `warehouse_name_snapshot`（`ProductPgIT` 之外由 `WarehouseDataScopePgIT` 取证）。
     **连带后果**：`/scm/delivery/options/warehouses` 同样收窄，调度岗若需跨仓选仓必须配授权行。
  5. 数据大屏接范围：`ScreenDataService` 每次请求解析一次上下文，作为单个 `scope` 参数下传
     27 条 Dao 方法；谓词按各面板的事实维度落点（经营与趋势销售序列=业务员、库存/地理仓库段=仓库、
     采购面板=采购归属 ∩ 仓库），`inventoryHealthRows` 两支各自落谓词，`trendByDay` 只能落进
     7 个标量子查询；供应商与 SKU 主档没有范围维度，**保持共享读是决定不是漏判**。
- **FA-3 的实测结论与用户前提不一致（重要）**：跨全部库取证
  `count(*) FILTER (WHERE deleted=FALSE AND file_key NOT LIKE 'public/%')` 在
  `xsy_scm_b0`（26 行活商品图）与所有一次性 IT 库里都是 **0**，且 `file_key IS NULL` 也是 0；
  早前子代理报的「dev 库有 5 行私有商品图」不成立（那是 IT 夹具形态）。
  所以「先复制 5 个对象」这一步在本环境没有对象可搬。V58 仍然按裁决落地为
  **可重入的搬运 + 数据库 CHECK**（含折叠冲突与目标占用预检、`t_file.folder_type` 同步、
  私有 key 关系行软删），搬运正确性由 `ScmProductImageKeyMigrationPgIT` 重放迁移里的同一段 SQL 取证；
  Java 侧「沿用本行原有私有 key」的过渡例外已删除（`ProductImageSyncManager#requirePublicImageKey`
  只看 public 前缀），`ProductPgIT` 改成两层各证一次。
  **上线约束不变**：真实部署若存在私有商品图，必须先 `CopyObject` 到 `public/image/` 再应用 V58。
- **对象存储（MinIO）取证已跑通，并抓到一个真实缺陷**：
  - 现场：本机对象存储栈按 [`../deploy/README.md`](../deploy/README.md) 与 `deploy/minio/bootstrap.sh`
    的同一份 bucket 策略起（匿名只放通 `public/*` 的 GetObject），后端以 `XSY_FILE_STORAGE_MODE=cloud`
    + path-style + `SEND_OBJECT_ACL=false` 连接；桶名与凭据都是本机占位值，只出现在启动命令里、**不入库**。
  - 结果：`F0FileStorageCloudIT` **5/5 绿**（此前一直按设计 skip 的那 5 项），
    `e2e/f0-file-storage.spec.ts` 在 cloud 模式下 **8/8 绿**。
  - **抓到的缺陷**：`FileRelationService.rebind()` 原本只做「收回不再引用的 key」，
    **不补新增的 key**，而公告 / 帮助文档 / 企业档案 / 商品图五条写链路都只用 `rebind`
    → 新建带私有附件的对象对任何非上传者都永远 30005，即 FA-2 的「能看对象即可看其附件」
    在授权方向上根本没生效（本地模式被 `/upload/**` 静态直出掩盖，只在对象存储模式下暴露）。
    已改为「同步成当前这一组 key：缺的补、多的收回」，并新增
    `FileRelationPgIT#rebindGrantsCurrentKeysAndReclaimsRemovedOnes` 钉住。
  - 顺带修掉的取证夹具问题：预签名地址必须用**不带 `Authorization` 头**的上下文取
    （否则 S3 判「多种鉴权并存」直接 400，看着像权限坏了）；文件页预览遮罩拦截整页点击，
    下载断言改到预览之前；`f0` 用例改为自建临时账号（原先依赖外部注入令牌）。
    为此新增 `tools/f0_e2e_accounts.py`、`WavePrefix` 增 `'f0'`、`tools/e2e_accounts.py`
    的登录名守卫放宽为 `^[a-z][a-z0-9]*_e2e_`，并把 `.gitignore` 白名单补上
    `w7 / w8 / f0` 三个封装脚本 —— **此前 `scm-customer-360` 与 `scm-data-scope` 在干净检出上跑不了**。
- **验证（本轮实跑数字）**：
  - **后端全量**：一次性干净库（V1→V58）上 `mvn -o -pl sa-admin -am test` =
    **1018 项 / 0 失败 / 0 错误 / 5 跳过，BUILD SUCCESS**。这 5 项跳过按设计就是 cloud 门控的
    `F0FileStorageCloudIT`，而它已在对象存储栈上单独实跑 **5/5 绿** —— 两者相加才是文件域完整口径。
  - **前端**：`npm run lint` 0 error（3 条既有 warning）、`npm run test` 223/223、
    `tools/ts_baseline_ratchet.py check` PASS、`npm run build` 成功；
    改动过的 spec 单独 `tsc --noEmit --skipLibCheck` 干净。
  - **浏览器全量（本地模式）**：**121 passed / 0 failed / 8 skipped**（15.8 分钟）；
    8 项 skipped 就是 `f0-file-storage.spec.ts`，它在 cloud 模式下单独实跑 **8/8 绿**，
    所以 129 项在本轮全部有真实执行记录。
  - 定向 IT 也逐域跑过：采购交集 / 仓库可见性 / 大屏范围 / 角色矩阵 / 库存读写范围 / 配送 /
    订单客户 / 商品与图片中心 / FA-3 搬运 / FA-2 与 FA-2b 文件层。
  - 期间修掉四处真因：`ScmInventoryConcurrencyIT` 子线程身份缺 `administratorFlag=true`
    （会被两条新守卫双双拒掉而「测不到竞态」）；`t_role` **没有** `administrator_flag` 列，
    角色验收只能按 `role_code` 判；`scm-data-scope.spec.ts` 第 6 条仍按「财务靠授权行收窄报表」
    断言（V57 之后财务显式持有全部仓库，属用例漂移，已改成「扣权账号证收窄 + 财务证显式全量」）；
    以及上面那条 `rebind` 授权缺陷。
- **剩余（都不是 P0 阻塞项）**：
  1. 本轮新建的一次性 IT 库与 dev 库里 `f0_e2e_*` / `w8_e2e_*` 临时账号尚未回收，等确认后按类别删。
  2. 数据大屏是否开放给某个正式角色，仍是业务决定（范围已经接好，`scm:screen:query` 目前只授超管）。
  3. 调度等岗位若需跨仓选仓，走 `employee_warehouse_scope` 授权行，不把选择器改回全量。
- **复现口径**：后端全量与定向 IT 用一次性库 + `XSY_V2_DB_URL` 的 `jdbc:p6spy:` 前缀；
  对象存储取证需先起 MinIO 并按 `deploy/minio/bootstrap.sh` 配好 bucket 策略，
  再以 `XSY_FILE_STORAGE_MODE=cloud` 重启后端，然后跑 `e2e/f0-file-storage.spec.ts`
  （同一环境变量既是 Playwright 的门控，也是后端存储模式开关）。

### 2026-09-24 P0 追加：预留并发用例重开并定位（进入 P1 前的 20× 稳定性闸门）

远端合并记录里 `ScmInventoryReservationConcurrencyIT#concurrentReservesCannotOversell` 出现过
「一次红、复跑绿」，因此按裁决在同步后的新基线上把该用例定向重复 20 次。**第 2 次即复现**，
按「任意一次失败就停止 P1、重开 P0 定位」执行，结论如下。

- **产品语义在每一次跑里都成立**：3×4 抢 10 恒为「恰 2 笔落地、`reserved_quantity = 8.0000`、
  `quantity` 仍是 10.0000、被拒的那笔不留行」——没有出现超卖、双计或残留，锁一直持有到事务结束。
- **红的是用例本身，两处缺陷都由「重复跑」暴露**（单次跑与全量各跑一次都可能碰不到）：
  1. `sourceIdSeed()` 用 `MAX(id) + 100000` 取种：一行预留只消耗一个主键，而一个用例消耗
     `seed+1..seed+3` 三个来源行 id，seed 每轮只前进成功笔数（≈2），下一轮就与上一轮
     **已成功落地的行同号**。第 3 次跑的失败是 expected 41011 / was **41016**——即防重索引
     对「上一轮的真实活动行」正确报错，却被本用例当成竞态结果。已改为
     `MAX(source_document_item_id) + 1000`：种子严格高于库内已用来源行 id，跨轮不重叠。
  2. 断言写死了「第三笔就是被拒的那笔」（`activeReservationRowsForItem(items.get(2))`）。
     竞态里输的是哪一笔不固定，第 2、5 次跑输的是第一笔，于是成功那笔留下一行 →「不得留行」假红。
     已改为**逐笔对齐** `outcomes[i]` 与 `items[i]`：被拒必须 0 行、成功必须恰 1 行，
     并保留原有 41011 与余额断言。这比原写法更强，因为它同时证「不超卖」和「谁被拒都不留痕」。
- **判定**：不是库存域回归，是用例的幂等性与断言定位问题；只改测试，产品代码零改动。
- **闸门执行口径**：一次性干净库 `xsy_scm_it_p1conc`（V1→V59），
  `-Dtest='ScmInventoryReservationConcurrencyIT'`（整类 5 条）连跑 20 次，任一红立即中断并回报。
  结果：**修复后 20/20 全绿，100 项用例 0 失败 0 错误**。修复前的对照同样有据：
  旧写法在**同一份产品代码**上第 2、3、5 次分别红（两种失败信息正是上面两处缺陷的指纹），
  第 1、4 次绿 —— 即「一次红一次绿」来自用例自身，与库存域无关。**P0 的库存预留并发结论维持不变**，
  本条按「测试债当场修、产品代码零改动」收口，P1 前置条件重新成立。
- **过程中另踩到一条运维口径**：后台闸门被停止时，只有包装 shell 退出，
  已 fork 的 `mvn → surefire java` 子树仍在跑，导致两份闸门并发写同一库与同一 `target/`；
  本轮因此作废一次闸门并重建库重跑。判据只能是按命令行枚举活进程，不能是停止通知的状态。

### 2026-09-24 P1 分拣管理：实发事实的产生地（V60–V62）

裁决依据：[`decisions.md`](./decisions.md)「P1 分拣管理裁决（2026-09-24）」第 1–14 条 +
「P1 分拣管理裁决补充」第 15–22 条（后一批是本轮实现过程中提问、用户当日答复的口径）。

- **落地范围**：新模块 `module/scm/sorting`（`sorting_task` + `sorting_task_item` 两张表、
  控制器 / 服务 / Dao / Mapper XML、9 个权限点、正式角色 `SCM_SORTER` 与仓库主管的队列管理权）。
  按计划 P1 首期范围交付：按客户订单分拣（写入口）、按商品汇总分拣（只读）、标品数量确认、
  非标品实重手工录入、分拣标签与小票打印、分拣操作日志、权限与数据范围。
- **四条"越界即违反裁决"的边界已钉进实现与用例**：不回写 `sales_order_item.actual_quantity` /
  `settlement_*`（第 1、3 条）；不写余额与流水、不出库单、不动预留触发点（第 6 条，用例断
  `inventory_movement` 总数在整条分拣链前后不变）；一条订单行只被一条活动明细占用
  （第 2 条，部分唯一索引 + 真并发用例）；打印只计次且**不 bump version**（第 8 条，
  否则会把别人的编辑顶成假冲突）。
- **占用位写在明细行上**（`occupation_status`，与 `delivery_route_order.assignment_status` 同形态），
  因为部分唯一索引的谓词不能跨表看任务状态。代价是同一条纪律：
  **取消任务必须在同一事务里把该任务全部明细置为 `RELEASED`**，任务锁是唯一入口，
  新增任何状态迁移路径都必须一并维护它。
- **配送资格交接**（第 11 条与补充第 18 条）：`CONFIRMED` ∧「订单每条有效明细行都被
  `COMPLETED` 任务覆盖」，判定落在**任务状态**而不是明细有没有结果 —— 这样重开只需把任务退回
  `SORTING` 就能让订单掉出候选，不必清空已录入的实重与原因（清空等于破坏审计）。
  历史 `ACTIVE` 配送占用一律不自动释放；已排线订单允许重开，接受"已排线但不合格"的中间态（第 20 条）。
- **本轮由实现发现、经用户当日裁决的三处口径**（都是我不自行选边的地方）：
  1. 第 10 条的「已有真实出库则禁止重开」在 P1 **不实现**，整条推给 P2（补充第 21 条）——
     现状没有任何数据链路把 `SALES_OUT` 归到订单行：出库单行不带订单行来源，
     `inventory_reservation.status='CONSUMED'` 全库无生产者。任何写法都只能是
     「按仓库+SKU+时间窗」近似，会把别的订单出库算到本任务头上，宁可不造判据。
  2. 建单用的「候选订单行」是**分拣队列视图**，不按 `seller_id` 收窄（补充第 22 条）：
     仓库岗位默认不持任何订单范围，沿用收窄会让主管一个候选行都选不到，第 15 条的
     「主管建单并指派」直接无法执行。代价由两头兑住 —— 入口只授建单权、返回列不含任何价格金额。
     这是 P0「option 列表要问它隐含哪个维度」的**唯一显式例外**，不是可复用范式。
  3. `CONFIRMED` 订单每条有效行的 `actual_quantity` 必然非空且 `> 0`（标品提交时按订购量落
     SYSTEM 值、非标确认前必须已录实重），因此"计划量 = 冻结实发量"不会遇到 null，
     V60 直接把它定为 `NOT NULL CHECK (> 0)`。这条是查出来的，不是猜的。
- **一处 P0 口径缺口被本轮暴露并补上**：「受指派人 = 本人」是直接比员工 id、
  不在 `ScmValueScope` 里，所以 `administrator_flag` 的 break-glass 原本不会作用到它 ——
  同一个超管账号会「仓库看得见、人看不见」。已把超管判定收成
  `ScmDataScopeService.isAdministrator()` 单一出处，`resolve()` 与分拣守卫共用，
  并补一条 IT 同时取证「超管可读 / 同一任务对分拣员仍是 30005」。
- **验证（定向）**：`SortingTaskPgIT` 19 项 + `SortingTaskConcurrencyPgIT` 3 项真并发
  （无外层事务，独立事务抢同一订单行 / 同一明细 / 同一任务完成，断"成功数 + 失败码 + 最终账"三者一致）；
  配送四个 IT 改走「先分拣完成再组单」的真实前置链后与分拣用例同批跑 **41/41 绿**；
  `ScmBusinessRoleMatrixPgIT` 7/7（含 `SCM_SORTER` 与队列管理权的授权断言，
  以及"库里不存在 `scm:sorting:scope*` 第二个范围权限"这条负向）；
  `SmartAdminMapperPgValidationIT` 通过（新增两张表的 DAO 使跳过基线 443 → 453，逐条确认全是
  `BaseMapper` 泛型 CRUD）；迁移冻结清单与校验和快照同步到 V62。
  竞态用例的两处断言写法教训（不许按提交顺序假定谁输、重复跑的种子必须建立在已用最大值上）
  见上面「P0 追加」一节。
- **前端**：`sorting-api.ts` + `sorting-task-list.vue`（任务列表 + 详情抽屉逐行录入 +
  建单弹窗 + 打印预览/登记）+ `sorting-summary.vue`（只读汇总）+ 契约用例 13 项；
  录入载荷逐行带自己的 `version`、`Idempotency-Key` 只加在建单与登记打印、
  完成条件不在前端复刻（服务端 41125 是唯一真相）。四道闸门见下方最终数字。

- **最终验证数字**：
  * 后端全量：一次性干净库（V1→V62）`mvn -o -pl sa-admin -am test` =
    **1057 项 / 0 失败 / 0 错误 / 5 跳过，BUILD SUCCESS**（5 项跳过仍是 cloud 门控的
    `F0FileStorageCloudIT`，它已在对象存储栈上单独实跑过，见 P0 第三批）。
  * 浏览器全量：`npx playwright test` = **129 passed / 0 failed / 8 skipped**，
    8 项是云端对象存储专用 spec 在本地存储下的按设计 skip。dev 库已随重启应用到 V62。
  * 前端四闸门（改过共享组件后重跑）：`npm run lint` 0 error（3 条既有 warning）、
    `npm run test` 237/237、`ts_baseline_ratchet.py check` **PASS（新增 0，SCM 区 0）**、
    `npm run build` 成功。棘轮现值 1946 < 基线 1974，另有 29 条已消失项可收编（未擅自改基线快照）。
- **验收过程中抓到的三件事（都不算 P1 产品缺陷，但都是真问题）**：
  1. **读侧可见性开关漏了 break-glass**：`SortingAccess.crossAssignee()` 原来只看权限码，
     而它同时是「未指派队列在列表/详情 SQL 里拼不拼 `assignee = 本人`」的那个布尔参数。
     定向 IT 全绿、**1057 项全量跑才红 1 条**。现与写侧守卫共用 `ScmDataScopeService.isAdministrator()`，
     并保留「同一行对角色账号仍 30005」的反向断言，避免把 break-glass 做成单向放宽。
  2. **共享组件 `EmployeeSelect` 按姓名搜索永远「暂无数据」**：`a-select` 默认按 `value` 过滤，
     而它的 `value` 是 `employeeId`。建单弹窗要按姓名挑受指派人，于是直接挑不出来。
     已按 `warehouse-select` 既有做法补 `:label` + `option-filter-prop="label"`，
     并把 `ref([])`（推成 `never[]`）显式定型，闸门重新全绿。这条影响所有用它的页面（客户、采购）。
  3. **两处 E2E 断言把「dev 库只有本轮数据」当前提**（测试债，已改成可重跑的写法）：
     主档司机列表按 `driver_code` 升序分页且 E2E 只回收临时账号、不回收业务行，
     dev 库累计 29 条司机后新行落到第一页之外 → 改成先按编码搜索再断「唯一一行」；
     分拣任务页同理，点行内按钮前先按单号筛。另修一处自己写反的时序：
     前置分拣的「必须已完成」断言读在了 `complete` 调用之前。
- **一条未复现的观察项**：`smartadmin-native.spec.ts` 缓存管理页在整跑里出现过一次
  `waitForResponse` 20s 超时，单独重跑该 spec + `scm-delivery` 共 25/25 绿，后续两次整跑也未再出现。
  按口径记为观察，不当成已定位缺陷；若再次出现，先查后端冷启动/JIT 而不是直接改断言。
- **未完成 / 刻意不做（不得当成已完成）**：
  1. 第 10 条的「已产生真实出库则禁止 REOPEN」整条推给 P2（补充第 21 条：没有可精确归属的链路）。
  2. 列配置（`TableOperator`）已接，但两页的枚举未注册进 `src/constants/index.ts` 的 SmartEnum 聚合，
     因此 `$smartEnumPlugin` 取不到分拣枚举 —— 现在页面直接用枚举常量渲染，不影响功能，属后续统一整理。
  3. 分拣绩效大屏、预包装状态、一键分拣批量动作、可配置打印模板、设备称重均未做（按计划属 P6 / 设备波次）。
- **复现口径**：P1 定向 IT 与全量同上；浏览器侧需先重建 fat jar 并以
  `XSY_V2_DB_URL=jdbc:p6spy:...:15432/xsy_scm_b0?currentSchema=xsy_v2` 重启后端（V59–V62 由 Flyway 自动应用），
  再 `bash tools/dev_up.sh sync` 同步容器快照，最后
  `XSY_V2_PG_DB=xsy_scm_b0 npx playwright test e2e/scm-sorting.spec.ts`。

### 2026-09-25 P2 物流配送 L3：发车即正式出库（V63–V64）

裁决依据：[`decisions.md`](./decisions.md)「P2 物流配送 L3 裁决（2026-09-25）」第 1–20 条，
以及实现期补记的第 21–23 条。主线顺序 **P2 → Finance R1 → Finance R2**；本轮不跳 Finance，
也不回头扩商品 / 采购 / 小程序的业务接口。开工基线：`git fetch` 后本地 HEAD 与 `origin/main`
同为 `0dfb68a`，Flyway 实际 max = V62，因此迁移从 **V63** 起（不是照抄规划稿的号）。

- **数据模型（V63，含反例取证）**：`inventory_outbound_item` 补 `sales_order_id / sales_order_item_id`
  （成对 CHECK + 部分索引，**同 SKU 不同订单行不合并**）；`inventory_outbound` 补 `source_document_*`
  并加部分唯一索引 `uk_inventory_outbound_source_active`，把「一条线路最多一张出库单」钉进库里；
  `delivery_route_order` 补履约状态 `PENDING / IN_TRANSIT / SIGNED / EXCEPTION` + 签收时点 / 人 / 原因
  （异常必填原因、终态必留时点与操作人，都是 CHECK）；`delivery_route` 补发车与完成时点，
  并用「状态到了就必须有时点」的 CHECK 拦住任何绕过服务端的迁移。四条约束各自用一条必然失败的
  INSERT 探过（`ck_delivery_route_dispatched`、`ck_delivery_order_exception_reason`、
  `ck_inventory_outbound_item_source_pair`、`uk_inventory_outbound_source_active`）。
- **权限（V64）**：1017 发车 / 1018 订单签收 / 1019 完成线路。发车授调度 + 仓库主管，
  签收授调度 + 司机，完成线路授调度；按 `role_code` 种，`ScmBusinessRoleMatrixPgIT` 用查询取证
  而不是人工推断。**配置要求**：发车走库存域既有的仓库范围守卫，所以调度岗必须有
  `employee_warehouse_scope` 授权行 —— 浏览器用例先撤授权证 30005、再补授权证成功，
  两端都在同一条用例里，避免「看着绿其实没跑守卫」。
- **库存域唯一写入口** `InventoryFulfillmentService`：一条命令在同一事务内完成
  「锁预留 → 按 (warehouse_id, sku_id) 升序预锁余额（含跨仓预留所在行）→ 整条归还预留 →
  逐行产生 `SALES_OUT` → 出库单直生 `CONFIRMED`」。`module/scm/delivery` 里没有任何
  余额 / 预留 / 流水写入（前端契约用例也钉住配送 API 触不到库存域接口）。
- **实现期发现并修掉的真实缺陷**：发车与分拣重开原本不在同一批行上加锁，可交错到
  「出库单已 CONFIRMED」与「任务已 SORTING」同时成立，于是已出货的订单行还能改分拣量。
  现 `reopen` 先按订单 id 升序锁订单行再判定（与 `dispatch` 同序），并发用例把两种合法结局钉死。
- **验收计数**：后端一次性干净库（V1→V64）全量 **1076 项 / 0 失败 / 0 错误 / 5 云端跳过**
  （P1 是 1057；新增 19 项 = 履约命令 7 + 发车链路 9 + 发车并发 2 + 角色矩阵 1）；
  新增 IT 定向复跑 49/49 绿（含 P1 分拣 `SortingTaskPgIT` 20 项未受影响）。
  前端四闸门：`lint` 0 error（3 条既有 warning）、`npm test` **247/247**、
  `ts_baseline_ratchet.py check` PASS（SCM 0 / 新增 0 / 修复 29 / 总量 1940 < 基线 1974）、
  `npm run build` 成功。浏览器全量 **145 项：136 passed / 8 按设计跳过 / 1 failed**，
  新增 `e2e/scm-delivery-l3.spec.ts` **8/8**（整链：备货→订单→少拣分拣→组单规划→发车→出库与流水取证→
  签收/异常→完成线路，含幂等重放、重开互斥、非超管与司机范围、履约标签页真实渲染）。
- **未复现的观察（不当成已修）**：唯一那 1 项失败是 `smartadmin-native.spec.ts` 的
  「登录登出记录」在 `page.waitForResponse` 上超时 20s —— 该页请求在监听器挂上前就已完成时必然假失败。
  随后单独把整个 spec 连跑两次都是 **17/17 绿**。P1 收尾时同一处也出现过一次同样的一次性红，
  因此记为共享夹具的已知脆弱点而不是 P2 回归；没有把它当噪声删断言，也没有谎称已修。
- **既有 L0–L2 用例的必要改写**：`scm-delivery.spec.ts` 第 6 条原本断言「L3 端点根本不存在」，
  L3 落地后该断言按定义失效。改成「存在且被守住」：用错误 version 撞发活得 40921、
  PLANNED 直接完成得 41101、未发车签收得 41101 —— 既证明路由已注册，又不在这条用例里真扣库存。
- **打印语义未动**：端点、载荷、计次与幂等键全部保持原样；发车是独立端点，
  契约用例继续钉「打印面板里没有任何 L3 动作」。整条线路实发为 0（全缺）时不生成出库单，
  `outboundNo` 为 `null` 是**成功**，前端文案必须解释为什么没有单号。
- **环境**：dev 库 `xsy_scm_b0` 已随重启的 fat jar 自动应用 V63/V64（Flyway 日志
  `Successfully applied 2 migrations ... now at version v64`），web 容器快照已同步。

### 2026-09-26 P3 Finance R1：F1-0.5 裁决收口 + F1-1 数据地基（V65–V67）

裁决依据：[`decisions.md`](./decisions.md)「P3 Finance R1 裁决（2026-09-25）」27 条 Q + 10 条全局不变量，
以及本轮新增的「第三批正式裁决（D-1 … D-5，2026-09-26）」；设计依据
[`plan/finance-r1-design.md`](./plan/finance-r1-design.md)（F1-0.5 收口版）。
**本轮只做 F1-0.5 与 F1-1，F1-2…F1-8 一律未开始。**

**F1-0.5｜D-1…D-5 全部裁决为 A，设计稿收口为无待裁决状态。** 五条落点：D-1 不回填上线前既有的
`SIGNED` / `CONFIRMED` / `APPROVED` 事实（本期无补生成 API、无回填权限，生成器仍须可重放）；
D-2 自动红字的可生成额度**不扣**已核销额；D-3 收付款纠错走 append-only 反向事实
（`entry_type` / `reverse_of_id` / `reason` + 一条 NORMAL 最多一条 REVERSE + 反向前已用额必须为 0）；
D-4 少拣导致的超额合法退货**全额**生成 RED、净应收可为负、以 `openAmount` / `overAppliedAmount`
两个只读派生值表达（页面文案禁用「客户余额 / 钱包余额 / 可用余额」）；D-5 收款按
`customerSellerScope`、付款供应商侧沿用采购团队共享读、核销随 target，禁止 `if role == FINANCE then bypass`。

同时修掉设计稿五处内部不一致：应付 MANUAL 红字的来源唯一索引谓词必须带 `source_id IS NOT NULL`
（少了它，`NULL` 不等于任何值，PostgreSQL 会**放行任意多条**手工红字）；删除 `external_reference`
的 UNIQUE 设计（它只是资金凭据文本，银行流水号跨客户重复是真实存在的，也不得当幂等键）；
作废「自动红字超额 → 41137 → `OrderReturn approve` 回滚」这条旧测试口径（41137 只剩手工红字应付
一个使用者）；R0 接轨必须区分发生额 Flow 与期末余额 Stock（否则「8 月形成应收 100、9 月核销 100」
查询 9 月会得到待收 −100），且数据源是 `finance_write_off` 的指标一律叫「已核销金额」而不是
「已收款 / 已付款」；财务事实表禁止用 `deleted = true` 模拟删除。

**F1-1｜Flyway**：新增 3 个迁移，当前最大版本 **V67**、连续无空洞。开工前 `git fetch` 重扫确认
本地 HEAD 与 `origin/main` 同为 `caace54a`，`db/migration/` 实际 max = V64、`t_menu` 种子实际
max = 1421、SCM 错误码实际 max = 41128，故 V65–V67 / 菜单 1500–1531 / 错误码 41130–41143 均为实扫空闲，
不是沿用规划稿的假定值。`migration_checksum_guard.py check` PASS（**67 冻结 / 0 漂移 / 0 缺失 /
0 改名 / 0 未入快照**，新增三号已 `sync`，既有校验和一字未动）。

- **V65（8 张表 + 5 条序列）**：`finance_receivable(_item)` / `finance_payable(_item)` /
  `finance_receipt` / `finance_payment` / `finance_write_off` / `finance_operation_log`。
  **零既有表改动**、无外键、**无状态列与余额列**（`status` / `settled_amount` / `open_amount` /
  `due_date` / `approver` 一律不存在，结清与已核销读时派生）。方向编码在 `entry_type`
  （应收应付 `NORMAL/RED`、收付款与核销 `NORMAL/REVERSE`，刻意两套枚举），金额与数量恒 `> 0`、
  `unit_price >= 0`、全表 `NUMERIC(18,4)`。七张事实表带 `CHECK (deleted = FALSE)`，
  `finance_operation_log` 连 `deleted` 列都没有（结构性不可删）。
  **5 条来源唯一索引**（应付 / 应付明细 / 付款的谓词含 `source_id IS NOT NULL`）+
  **3 条反向唯一索引**（`uk_finance_{write_off,receipt,payment}_single_reverse`）+
  5 条单号唯一索引 + 18 条查询索引；`external_reference` 只建普通索引。
- **V66（仅数据）**：财务管理目录 1500 + 五个页面 1501–1505 + 13 个权限点
  （查询 1511–1515、写与破坏性动作 1521–1527、导出 1531），四条种子约定逐条满足
  （`menu_id == sort`、`context_menu_id == parent`、`api_perms == web_perms`、`perms_type = 1`），仅授 SUPER_ADMIN。
- **V67（仅数据）**：按 `role_code` 把 19 个菜单全部授 `SCM_FINANCE`（不硬编码 `role_id`）；
  销售 / 采购 / 仓库 / 配送 / 分拣 / 司机**一个财务权限都没有**（审批过退货不代表能操作资金）。

**F1-1｜后端骨架**：`module/scm/finance` 下 9 个 entity（含审计基类 `FinanceRecord`，
**刻意不带 `@TableLogic`** —— 它表达「可被软删的实体」，与 append-only 语义相反，沿用
`InventoryMovementEntity` 的同一取舍）、8 个 DAO、13 个枚举、`FinanceConstant`、
`FinanceErrorCode`（41130–41143）、`FinanceOperationLogRecorder`（唯一写入口，八值白名单）、
5 个 Service 空骨架。共享层新增 `common/json/JsonbObjectMapTypeHandler`（日志 before/after 快照用，
不复用 `order/support` 里那份以避免 finance → order 耦合）。

**刻意的取舍**：本轮**不建** form / VO / 只读业务 DAO / mapper XML。指令允许建，但那些 SQL 在 F1-1
一个调用方都没有、也无法被测试覆盖，属「无调用方的死代码」；它们随 F1-2 / F1-5 的第一个调用方与
第一个测试一起落地。**未接触**任何业务触发点：`DeliveryRouteService.sign` /
`PurchaseReceiptService.confirm` / `OrderReturnService.approve` 一行未改，
`generateOnSign` / `generateOnReceiptConfirm` / `generateRedOnReturnApproved` 未实现（属 F1-2）。

**F1-1｜测试（本轮只测 schema / permission 契约，未提前写 F1-2 业务 IT）**：

- `ScmFinanceSchemaPgIT` **18/18**（真实 PostgreSQL）：8 表 + 5 序列存在、零外键；
  不得出现状态 / 余额 / 账期 / 币种 / 税列；七张表 append-only CHECK 生效（软删被库级拒绝）、
  日志表无 `deleted` 与 `version` 列；金额与数量恒正、单价非负、`version >= 0`、
  **全部 numeric 列精度逐列断言为 (18,4)**；应收 / 应付 / 收付款 / 核销四类配对 CHECK 逐条以
  SAVEPOINT 隔离的坏数据验证真的会拒绝（含「手工红字带 `source_id`」「反向付款沿用
  `ORDER_REFUND` 来源」「来源与方向错配」「收付款跨侧核销」「日志类型越界」「JSONB 非 object」）；
  来源唯一索引谓词逐字核对、并实测重复生成被拒；三条反向唯一索引实测「一条 NORMAL 只能反向一次」；
  **`external_reference` 实测不唯一**（同凭据号两笔收款都成功，防止有人把 UNIQUE 加回去）；
  **手工红字应付实测不受来源唯一索引约束**（两条 MANUAL 并存）；
  **13 个 Java 枚举与 DB CHECK 白名单双向逐值相等**（少一个值＝Java 能造出库不接受的事实，
  多一个值＝库放行了页面无法解释的取值），并断言方式枚举不含任何 P5 支付能力；
  V66 种子四条约定 + 19 个菜单 + 13 个权限串逐条核对、页面 path 与 component 逐条对应设计稿、
  **不存在任何回填 / 补生成 / 金额字段级 / `scope:all` 权限**；V67 授权实测
  （`SCM_FINANCE` 19 条含四个破坏性动作、超管 19 条、九个非财务角色 0 条）。
- `FinanceReadOnlyContractTest` **4/4**（静态扫描，不依赖数据库）：finance 包的 Java 字符串字面量与
  mapper XML 内**不存在**针对 `sales_order*` / `order_return*` / `order_refund` / `purchase_*` /
  `inventory_*` / `delivery_*` / `sorting_*` 的 INSERT / UPDATE / DELETE / TRUNCATE；
  mapper 注解里不写 SQL（AGENTS.md §8）；**8 个实体的 `@TableName` 全部以 `finance_` 开头**
  （这条是最强的边界证据：BaseMapper 的写方法因此够不到业务表）；finance 包不 `import` report 包。
  刻意做成静态扫描而不是运行时拦截：F1-1 一条写路径都还没有，等 F1-2 接上生成器再补断言，
  恰好错过唯一一次「新增代码是否越界」的廉价评审时机。

**本轮实跑证据**：`mvn -B -pl sa-admin -am test` 全量 **1106 项 / 0 失败 / 0 错误 / 5 跳过**
（5 项跳过为 `F0FileStorageCloudIT` 的云端门控用例，与 P2 基线同一口径，需对象存储单独跑）；
`migration_checksum_guard.py check` PASS。V65–V67 是在一个**一次性干净库**上随 V1→V67 全链
由 Flyway 真实应用后取证的（用完即删），因此「8 张表 / 序列 / CHECK / 唯一索引 / 菜单与角色种子」
全部是实测而非纸面推演。开发库当时停在 V59，**未被本轮触碰**。
两处既有门禁因本轮新增而按设计变红、并已按其「只增不减需显式确认」的口径显式更新：
`ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly` 的冻结版本清单追加 65/66/67（与 P2 追加 63/64 同一做法），
`SmartAdminMapperPgValidationIT` 的跳过项基线 453 → **493**（8 个新 DAO × 5 条 BaseMapper 泛型方法，
与 P1「每张表 5 条」同一形态；该用例另一条断言已保证跳过项方法名必属 BaseMapper，
故增量不可能是手写语句被漏掉）。

未覆盖（不得当成已完成）：

- **F1-1 没有任何业务行为可测**：应收 / 应付 / 收付款 / 核销的生成与写入路径一条都没实现，
  因此「金额公式对不对」「红字会不会阻塞 approve」「并发核销会不会超额」全部**尚未取证**，属 F1-2…F1-4。
- **前端未动**：V66 已种下五个页面的菜单与 `component` 路径，但对应 `.vue` 文件属 F1-6。
  在 F1-6 落地前，超管与 `SCM_FINANCE` 账号在侧栏点这五个页面会落到空组件 ——
  这是本轮按指令拆分阶段的**已知中间态**，不是缺陷；本轮未擅自用 `visible_flag = false` 掩盖它，
  因为设计稿 §16 没有这一条，改它等于就地新增未裁决的口径。
- **浏览器 / E2E 未跑**：本轮无前端改动，`verify.py frontend` 与 `e2e` 未执行。
- 错误码 41130–41143 已声明但**全部无使用者**（写路径在 F1-2…F1-4）；提前声明是为了一次占齐码段，
  避免后续各阶段各自取号造成冲突或重排。
- `JsonbObjectMapTypeHandler` 与 `order/support/OrderJsonbTypeHandler` 是等价的两份；
  合并到 `common/json` 是一次纯 Java 重构（不涉及 migration、不改对外行为），
  与 `ScmCommonErrorCode` 里记录的 40921 重复声明同一处置取向：先记为已知技术债，不顺手重构。


### 2026-09-23 第三轮复核收尾（P2 三项 + 一处自测夹具过期）

在远端 `main @ f3b3f20` 基础上做的收尾，不含新业务能力：

- **`FileKeyVoSerializer` 最后一个 fail-open 分支已堵。** 原先 `fileService == null` 时把原始
  `value` 直接 `writeString` 出去——虽然不生成可访问 URL，但仍把私有附件的 **key 与存在性**
  回给了调用者，与本类「依赖未注入 / 无身份即 fail closed」的设计目标矛盾。现改为
  `fileService == null || fileAccessGuard == null` 一律输出空数组，不再有任何回退分支写出 `value`。
  新增回归 `neverEmitsTheRawKeyIfFileServiceWasNeverWired`（断言 `writeString` 从未被调用），
  并把 `failsClosedIfTheGuardWasNeverWired` 收紧为「guard 未注入时连 `getFileList` 都不许调」。
  `FileAccessGuardTest` + `FileKeyVoSerializerTest` 合计 **31/31 通过**（JDK 21）。
- **文档与代码事实对齐。** `docs/progress.md`、`docs/decisions.md`、`CONTRIBUTING.md`、
  `PROPOSAL-2026-09-18-团队技术提升方案.md` 与
  `docs/plan/attachment-asset-grading-and-file-access-plan.md` 中「`FileKeyVoSerializer` →
  `getFileList()` 无逐用户过滤」一类旧描述，统一改写为「旁路已**临时收口**，但
  `FileService.getFileList(keys)` 仍是无身份批量入口、代码生成模板未改、`scm_file_relation` 未落地」。
  `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` 加了醒目状态头并标注其
  `main @ 45412fb` / `Flyway max V43` / 「待实施计划」三处已过期（实际 `f3b3f20` / V49），
  防止被当成现行计划再次实施而重复造功能。
- **E2E 的 Python 依赖显式化。** 三个 xlsx 夹具生成器与订单导入用例内联 Python 都依赖 `openpyxl`，
  但仓库此前没有任何 requirements 文件，「干净检出可跑」实际隐含「机器上已装好 openpyxl」。
  新增 `tools/requirements-dev.txt`（`openpyxl==3.1.5`）并加入 `.gitignore` 白名单，
  安装口径固定为 `python -m pip install -r tools/requirements-dev.txt`；
  `tools/verify.py` 的 E2E 就绪检查新增该依赖与三个夹具脚本的探测，缺失时计入未覆盖项（退出码 2）
  而不是让用例跑到一半 `ModuleNotFoundError`——那种失败会被误读成「用例本身坏了」。
- **迁移守卫自测有一处过期夹具已修。** `test_renumbering_an_applied_migration_fails_the_guard`
  写死「V43 改号为 V44」，而 Wave 1 已落地真实的 `V44`，于是 `scan_migrations` 先抛
  「版本号重复」，用例想验的改号路径根本走不到（自测 12 项里报 1 个 ERROR）。
  改为按临时树现有最大版本号动态取空闲号，断言强度不变。`python tools/test_verification.py` **12/12 通过**。
- **未覆盖项（如实声明）**：上述 31/31、12/12 均为**本地实跑证据**；仓库当前**没有 GitHub Actions /
  commit status**，远端 CI 尚未独立复跑过这些数字。E2E 未在本轮执行（本轮无前端与业务流程改动）。
  F0-DEBT-01 仍未关闭，FA-1 / FA-2 / FA-3 状态见「当前待办」。

### 2026-09-23 Wave 1–8 审计修复与全栈验收（`docs/xsy-scm-wave1-8-audit-fix-plan.md`）

- **范围与纪律**：按该审计计划的 §14 固定顺序逐项修复 Wave 1–8 的 P0 / P1 / P2 问题，并补齐 §12 的验收缺口。
  零历史迁移改写（图片模型冲突以新增 `V49` 追加修正）、零 Sa-Token 绕过、零库存余额直改、零 `inventory_movement` 改写、
  零第二套业务事实、打印不等于发货 / 出库、缺口预览不升级为采购建议、不进配送 L3。§17 的六条业务规则一律未自行裁决。
- **Flyway**：新增 **1** 个迁移 `V49__scm_product_image_type_gallery.sql`（结构 + 数据），当前最大版本 V49、连续无空洞；
  `tools/migration_checksum_guard.py check` 通过（历史迁移 0 漂移 / 0 缺失 / 0 改名）。空库 V1→V49 在一次性 IT 库上实际
  跑通（`ScmPurchaseMigrationIT` / `ScmInventoryMigrationIT` / `ScmPurchasePermissionMigrationIT` 等迁移级 IT 同一轮全绿）。
- **逐项修复**：
  1. **Wave 2A 权限交集（P0）**：`POST /scm/purchase/demand/summary-preview` 原只要 `scm:purchase:demand:query`，
     返回体却含库存现有量与预留量——只有采购需求查看权的人可经聚合接口读库存。改为
     `@SaCheckPermission(value={"scm:purchase:demand:query","scm:inventory:balance:query"}, mode=SaMode.AND)`，
     前端隐藏按钮不再充当权限。测试：`PurchaseDemandSummaryPreviewPermissionTest` 3/3 + 浏览器反例用例 13。
  2. **Wave 2A 自身预留口径（P0）**：`availableQuantity = quantity - reserved_quantity` 把**本批订单自己**已占的
     ACTIVE 预留当成不可用库存，缺口虚增。SQL 内拆出 `selectedOrderReservedQuantity`（读既有
     `inventory_reservation`：ACTIVE + `SALES_ORDER_ITEM` + 来源单落在同一确认窗口）与 `otherReservedQuantity`，
     本批可用 = `quantity - otherReserved`；派生列改名 `stockComparisonGap` 并注释「不是净采购建议」。
     全部 PostgreSQL NUMERIC 计算，Java / 前端不做浮点减法。测试：`PurchaseDemandSummaryPreviewIT` 11/11。
  3. **Wave 6 复制盘点 pageSize（P0）**：复制历史盘点一次拉 2000 行余额，被后端 `@Max(100)` 拒成 400。
     提取纯函数 `resolveStocktakeCopyUnits`（按页 100 取、找齐即停、翻完仍缺的 SKU 显性返回并整单拒绝），
     不为单页放宽通用查询上限、不静默丢行。测试：`w6-stocktake-copy-units.test.mjs` + 浏览器复制盘点用例。
  4. **Wave 6 生产密钥 fail-fast（P1）**：`StocktakeSnapshotSigner` 的仓库内默认密钥在 pre / prod 下会静默充当
     快照签名密钥。构造期新增 `requireNonPublicSecret(secret, activeProfiles)`：profile 含 `pre` / `prod` /
     `production` 且密钥缺失或仍等于公开默认值即抛 `IllegalStateException`（启动失败），口径与
     `FileConfig#validateCloudConfig` 一致；生产用环境变量 `SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET` 配置。
     计划要求的另外两件交付物本轮补齐：四份 `application.yaml` 显式声明 `scm.inventory.stocktake.snapshot.secret`
     （`dev` / `test` 取仓库内开发默认值，`pre` / `prod` 取 `${SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET:}` 的空默认值，
     让缺失在启动期即失败而不是静默退回公开密钥），生产部署说明落在 `deploy/README.md`（变量 → 配置键 → 缺失后果）。
     测试：`StocktakeSnapshotSignerTest` 11/11。
  5. **Wave 3 历史价缓存串数据（P0）**：「最近已确认订单价」缓存键含明细行序号，切 SKU / 删行上移 / 切客户会
     显示上一个 SKU 或上一个客户的价。改为 `customerId:skuId` 复合键纯模型（`recentPriceKey` 及读写函数，
     区分「未查过」与「查过但为空」，请求异常必落回加载态且不写缓存以便重试），浮层挂到抽屉内部、
     关抽屉与换客户时收起并作废在途结果。测试：`w3-recent-price-cache.test.mjs` + 浏览器用例 9 / 11（11 专测切 SKU 与
     切客户两条串数据路径）。
  6. **Wave 4 待办 deep-link（P1）**：首页卡片跳列表只改路由、目标页不读 query，导致「数字说有 7 条、列表是全量」。
     新增 `query-deep-link.ts`（逐键白名单解析，白名单外按未提供、绝不把任意串透传给后端；无 query 进入时
     整体回落默认筛选，不残留上一次 deep-link 条件），列表页首载与再次进入都重套；待办数字与列表条件同源同口径。
     测试：`w4-todo-deep-link.test.mjs` + `scm-query-form-submit.test.mjs` + 浏览器待办用例。
  7. **Wave 4 消息 → 业务单据（P1）**：报损报溢驳回消息的 `messageType` 原写死 `MAIL`，前端无法识别跳转。
     新增 `MessageTypeEnum.SCM_INVENTORY_LOSS_GAIN(3)`（复用原生 `t_message.message_type` + `data_id`，不建第二套
     消息中心、不加列），发送端改写业务类型；前端 `message-business-link.ts` **只认数值类型**（不用中文标题判断）
     产出站内路由，目标页接口自带权限校验，用户失去权限后旧消息仍跳不出数据。测试：`w4-message-business-link.test.mjs` + 浏览器驳回消息用例。
  8. **Wave 1 图片类型模型（P1）**：V44 用 `image_type='PRIMARY'` 与 `is_primary` 同时表达主图，最新计划改为
     `GALLERY`/`DETAIL`。按「不改历史迁移」新增 V49：移除两条旧 CHECK → 存量 `PRIMARY` 改 `GALLERY` → 加新 CHECK →
     先降级重复主图（保留同 SPU 内 id 最小的一张并 `RAISE NOTICE`）→ 建「每 SPU 至多一张主图」唯一索引。
     实体 / DAO / 同步管理器与图片中心页同步改为 `is_primary` 单一事实。测试：`ProductImageCenterPgIT` 11/11。
  9. **Wave 1 UPDATE 导入（P1）**：原导入只有 CREATE，计划 §7.2 的更新语义整体缺失。新增 `mode=CREATE|UPDATE`：
     模板带 `模板版本` + 四个定位键（`SPU ID`/`SPU版本`/`SKU ID`/`SKU版本`）、未出现的 SKU 不删、空白列保持原值、
     `(清空)` 仅放开六列可清空字段、锁定与派生列 `FIELD_LOCKED`、跨 SPU 的 SKU 报 `SKU_NOT_OWNED`、
     任一行版本冲突整批 `VERSION_CONFLICT(40921)` 0 行写入；UPDATE 额外要求 `scm:product:update`（下载更新模板同样）。
     弹窗按模式切换文案与模板，结果 VO 带 `mode` / `updatedProducts`。测试：`ProductImportServiceTest` 22/22、
     `ProductImportUpdatePgIT`（新增真实库）4/4、浏览器 CREATE / UPDATE 两个真实页面往返用例。
  10. **Wave 5 `customerStatusFilter`（P1）**：按客户正式打印原把客户状态当输入直接用，预览后计数已变仍会按过期状态重打，
     且零选择 + ALL 会无选择地重打整条线路。改为 `customerIds` 只是候选范围、状态在线路锁内按当前 ACTIVE 订单
     重新聚合判定（口径与只读 `customerView` 一致），`ALL|PRINTED|UNPRINTED|PARTIAL` 由 `@Pattern` 约束，
     零选择 + ALL 与展开后空清单一律拒绝；聚合用 `LinkedHashMap` 保证两次同请求生成同一份清单。
     测试：`DeliveryPrintTrackingIT` 7/7 + 浏览器 PARTIAL 只补未打印用例。
  11. **Wave 2B 原子回滚 IT（P2）**：补 `PurchaseShortCloseRollbackIT` 2/2（批量少收关单中途失败时整批回滚、
     已关单与新增关单记录都不残留）。
  12. **Wave 5 并发打印 IT（P2）**：补 `DeliveryPrintConcurrencyIT` 2/2（并发正式打印不丢计次、幂等键重放只计一次）。
  13. **Wave 8 权限与服务端脱敏硬化（P2）**：操作日志按业务对象下钻的读权限白名单收敛到
     `OperateLogBusinessType`（sa-base 单一枚举，前端 `message-const` 同值镜像由单测核对），无匹配业务类型回 `1=0`；
     新增 `OperateLogParamMask` 挂在 `OperateLogAspect` 唯一的序列化出口，按字段名递归脱敏 password / token /
     secret 等并保持 JSON 结构，库里存的即脱敏值（此前只在前端展示时脱敏）。测试：`OperateLogParamMaskTest` 5/5、
     `OperateLogBusinessTypeTest` 3/3、`AdminOperateLogBusinessGuardTest` 6/6、浏览器零权限账号反例用例。
  14. **Wave 7（仅回归）**：无代码改动，纳入全量回归与真实浏览器验收。
- **验收基建（§12）**：E2E 不再靠外部注入一次性令牌、缺令牌即整组 skip（那正是「文件存在被当成已验收」的成因），
  改为 `e2e/scm-e2e-account.ts` 自给自足链路：脚本建临时管理员 / 只读 / 零角色 / 扣权账号、走真实登录
  （验证码 + SM4 传输加密 + Sa-Token）、令牌只活在内存里、`afterAll` 删账号；`e2e/scm-test-base.ts` 把
  「0 未捕获异常 / 0 未处理 promise rejection」收敛成 page fixture 的统一口径（rejection 经 init script
  转成未捕获异常进入同一通道），用例不再各自挂监听导致漏挂；`tools/e2e_accounts.py` 改为未显式提供目标库即拒绝运行，
  把「跑在过期库上」从静默错误变成启动期失败；`tools/ts_baseline_ratchet.py` 遮蔽绝对路径使基线身份跨检出可移植；
  `playwright.config.ts` 关闭 `fullyParallel`（文件内用例共享模块级夹具）。
- **测试结果（本轮实跑）**：后端全量回归 **884 通过 / 0 失败 / 0 错误 / 5 跳过**（跳过的 5 项为需要云端对象存储的
  F0 IT，与 Wave 1–8 无关），在同一轮内先于空库迁移校验；前端 `node --test` **176/176**（第三轮新增
  `w3-draft-off-page` 3 条，前两轮为 173/173）、`npm run lint`
  **0 error**（3 warning 均在未触碰的历史文件）、`tools/ts_baseline_ratchet.py check` **新增诊断 0 / scm 区 0**、
  `npm run build` 成功。
- **全栈浏览器验收（§12.3）**：Playwright 全量 **96 通过 / 7 跳过 / 0 失败**（12.8 分钟、串行 1 worker；第三轮按
  最终代码全量重跑，前一轮为 95/7/0，多出的 1 条即新增的未保存草稿离页用例）。
  跳过的 7 项是 `f0-file-storage.spec.ts` 在本地存储模式下的按设计跳过（对象存储为云端时才执行），**Wave 1–8 的
  场景无一 skip**。逐 Wave 覆盖：商品 CREATE / UPDATE 导入（两个模式各自真实上传：CREATE 建一个 SPU 两行 SKU
  的商品并验证含一行错分类编码的批次整批 0 写入，UPDATE 验证空白列保原值与过期定位键重放被拒）、图片中心
  GALLERY / DETAIL、设主图、批量维护（8/8）；2A 双权限与缺权限被拒（用例 13）；2B 批量少收 / 导出 /
  按商品收货 + 真实点击行内与批量打印（用例 14，离线捕获打印文档）；3 草稿恢复 + 刷新与路由切换各自可归因的
  离页落盘 + 历史复用 + 切 SKU / 切客户不串（12/12）；4 待办数字与列表条件一致、驳回消息、消息进业务单据；5 按订单 / 按客户打印、
  PARTIAL 只补 UNPRINTED、GET 预览不计次、POST 正式生成计次；6 模板导出、填实盘导入、快照漂移整批拒绝、
  复制历史盘点、复制后实盘为空（5/5）；7 客户 360° 5 Tab 与权限不足不泄露；8 日志上下文下钻、ID 精确匹配、
  刷新不丢上下文、查询记忆按用户隔离（4/4）。本轮把 §12.3 逐条与用例清单对表时发现三处「文件存在但场景没真跑」，
  已各自补成真实浏览器用例（均不借用其他用例留下的数据，单独执行亦成立）：Wave 1 商品 Excel CREATE 原本只验到
  「选文件前禁止提交」的弹窗闸门、从未真的以新增模式写库；Wave 2B「打印」原本只验到入口按钮；Wave 3 的
  切 SKU 与切客户两条串数据路径原本没被同一条完整链路同时锁死。统一口径 0 pageerror / 0 未处理 rejection / 0 权限绕过由
  上述 fixture 与负向用例共同钉死，权限负向均用**真实非管理员临时账号**验证，不只用 SUPER_ADMIN。
  **第二轮独立复核补的三项不在上面的数字里**：2A 缺口预览的**反向**权限运行时用例（保留库存查询、扣掉采购需求查询）、
  Wave 3 未保存草稿的**离页三通道**（`beforeunload` / `onBeforeRouteLeave` / `onBeforeUnmount` + 浏览器刷新与路由切换用例
  + 前端契约单测）、Wave 6 快照密钥的**配置声明与生产部署说明**（四份 `application.yaml` + `deploy/README.md`）。
  这三项是在那次全量跑之后落进代码的。**定向复跑结果（2026-09-23）据实记录**：后端定向 **18/18**（含真实库
  `ProductImportUpdatePgIT` 4/4，同时证明新写入的 `test/application.yaml` 配置键下 Spring 上下文与 Flyway 正常启动）、
  权限注解与快照签名密钥测试全绿；前端契约 `w3-draft-off-page` 3/3。
  该轮受影响 spec 定向跑 **35 通过 / 1 失败 / 1 未执行**，唯一失败是本轮**新增**的浏览器用例 12，第三轮已定论为
  **真实界面缺陷而非测试编排问题**：本次会话**首次**挂载订单抽屉时，「发现上次未提交的订单草稿」确认框的「恢复」按钮
  点不到（截图里抽屉是空白表单且完全看不到确认框）。根因是 `open()` 先置 `visible = true`（Vue 异步更新，抽屉的
  teleport 容器稍后才挂到 `body`），随后同步调用 `Modal.confirm`——确认框容器反而**先进** `body`，两者 z-index
  同为 1000，后入的抽屉整体压在确认框之上；同一会话的第二次新建（抽屉容器已存在）不受影响，所以既有的用例 10 一直是绿的。
  修复是给该确认框显式抬高 `zIndex`（与仓库内 `employee-password-dialog` / `message-receiver-modal` 浮于抽屉之上的
  同一量级），并做了**因果核验**：临时删掉这一行复跑即恢复为红，加回即绿，确认该断言是承重的而非顺带通过。
  用例 12 同时重写为两条离页通道**各自可归因**：路由切换后直接读本地草稿存储（全程未点「关闭」，只有
  `onBeforeRouteLeave` 能写），刷新前先改一个只存在于该版的备注值（刷新后草稿带新值才证明是 `beforeunload` 写的），
  避免把 keep-alive 保留组件状态误当成防丢已验收。因此 2A 反向权限组、Wave 6 配置/部署交付物与 Wave 3 离页场景
  **均已取证**，**未取证即不计入已验收**。同一轮还把三处「缺夹具即 `test.skip`」改为硬失败（Wave 6 挑不到带余额仓库、
  Wave 7 无客户、Wave 8 无商品），全仓 E2E 仅剩 `f0-file-storage.spec.ts` 的云端存储条件 skip。
- **与审计计划的偏差**：仅一处口径差异——商品**普通列表导出**（24 列，分类路径 + 标签名称）与**可回导的 UPDATE
  维护模板**（25 列，分类编码 + 标签编码）是刻意不同的两套列集合，回传普通导出会得到逐列 `HEADER_INVALID`。
  计划未规定二者必须同构，本轮按「安全拒绝」现状保留并提交裁决（见下）。其余 §7 各项均按计划要求落地。
- **需要业务裁决（未自行实现）**：
  (a) 商品「导出即可回导」是否作为产品契约（若是，需要第三份「维护导出」口径或让导出携带编码列）；
  (b) §17 六条（在途采购是否抵扣净采购建议、已履约量入公式、非标品未实重是否入采购、多仓需求分配采购仓、
  打印是否追踪当前内容版本、是否需要字段级 before/after 审计表）本轮一律未动，缺口预览因此仍只是「库存对比差额」。
- **本轮另发现的环境侧缺陷（已就地修复，非业务代码）**：带显式主键的旁路种子数据会让 PostgreSQL identity 序列
  落在最大值之后，后续新增分类撞唯一索引并被 `catch (DuplicateKeyException)` 误映射成「编码重复」
  （`ProductCategoryService` / `ProductSpuService`）。已用一次性 `setval` 扫描修正序列；**建议**（未改，避免作废
  本轮已取证的回归结果）：这两处 `catch` 应索引冲突前先判定冲突约束，或在 AGENTS §8 补一条
  「显式主键种子写入后必须同步序列」。
- **剩余风险**：预留的并发压测、阈值预警推送、M2 地图路线、F0 读侧（FA-1～FA-3）、W6-2 小程序不变；
  本轮全栈验收跑在本地存储 + 本地 PostgreSQL 栈上，F0 云端模式下的 7 条用例仍需在真实云端环境单独执行。
  另有两项待处理：(1) **已闭合**——E2E 生成上传文件所需的 `tools/patch_product_create_xlsx.py` /
  `tools/patch_product_update_xlsx.py` / `tools/fill_stocktake_template.py` 原先落在 `.gitignore` 的
  `/tools/*` 之下，干净检出跑不了这几条上传类用例；三者只按 argv 收文件路径、不含本机路径与凭据，已加入白名单入库；
  (2) **已闭合**——导入的「分类必须是三级」原先只在写入路径生效（单元格校验只查存在与 ENABLED），因此填 1/2 级
  分类编码会表现为整批写入错误而非逐列校验错误；第二轮已把同口径的层级判断前置到逐行校验，回
  `CATEGORY_LEVEL_INVALID` 并指到「分类编码」单元格（见下「第二轮复核追加」）。

#### 第二轮复核追加（同一 Wave 1–8 线，2026-09-23）

- **触发**：对 `main` 的第二次代码级审计给出 1 个新功能缺陷（P1）+ 1 个验收可复现性问题（P1）+ 2 个收尾项（P2）。
  本轮只做这四项，未触碰任何其他业务功能；`§17` 的待裁决项与「导出即可回导」契约一律未动。
- **P1｜DETAIL 图被静默改成 GALLERY**：`ProductImageForm` 没有 `imageType`，而 `ProductImageSyncManager.entity()`
  对**更新行与新增行一律** `setImageType("GALLERY")`，于是「Excel 只改市场价」「切主图」「调整排序」「删除其他图片」
  都会把库里 `DETAIL` 的详情图降级成图集图——V49 建立的正交模型被写入链破坏。修复取更接近事实的边界：
  表单加 `imageType`（只约束取值 `GALLERY|DETAIL`），但**已有行不写这一列**——只有新增行落内容角色（缺省 `GALLERY`），
  更新行留 `null`，让 MyBatis-Plus 的非空更新策略把 `image_type` 整列排除在 UPDATE 之外，库内值天然存活。
  刻意不采用「读现值再原样回写」：同一 `SqlSession` 内的旁路改库不刷新 MyBatis 一级缓存，按过期实体回写会
  重新引入同一污染（本轮第一版实现正是被新增回归抓红后才改成列排除）。
- **回归（审计点名的 5 条 + 1 条加固）**：`ProductImportUpdatePgIT.priceOnlyUpdateKeepsDetailImageType`、
  `ProductImageCenterPgIT.setPrimaryOnGalleryKeepsDetailType` / `reorderKeepsEveryImageType` /
  `bindingNewGalleryImageLeavesExistingDetailType` / `batchRemoveLeavesSurvivingDetailType`，另加
  `syncIgnoresImageTypeClaimedByExistingRow`（已有行谎报 `GALLERY` 也不生效）。**因果核验**：这 6 条在修复前 6/6 全红，
  修复后商品模块定向 **46/46**（`ProductImageCenterPgIT` 16 / `ProductImportServiceTest` 23 / `ProductImportUpdatePgIT` 5 /
  `ProductImageChangeSetTest` 2）。
- **P2｜三级分类前置校验**：Excel 逐行校验补 `level == 3` 判断，回 `CATEGORY_LEVEL_INVALID` 并指到「分类编码」单元格，
  与写入路径 `ProductCategoryService.requireSelectableCategory` 同口径，填 1/2 级分类不再等到整批写库才收到 40011。
  浏览器侧同步扩到真实页面：Wave 1 CREATE 用例的拒绝批次增加一个填二级分类的**独立 SPU** 行，断言该行
  **只有** `CATEGORY_LEVEL_INVALID` 一条诊断——并入同一 SPU 会被「同商品分类必须一致」先炸出无关错误，测不到这一层。
- **P1｜干净检出可复现**：三个 xlsx 生成器已入库（`5409477`）；本轮把 spec 引用的 `../tools/*.py` 逐条
  `git ls-files --error-unmatch` 验全，并**实跑一次干净检出**——克隆 `HEAD` 到临时目录后上传类用例 13/13 通过、
  第二轮版 CREATE 用例 1/1 通过，这几条用例不再依赖任何未入库的本机文件。
- **P2｜`progress.md` 过期描述**：「报损报溢没有消息通知」按事实改写为「驳回站内信 + 首页只读待办已交付，
  缺的是其余事件与外部渠道」，避免下一轮重复实现。
- **本轮实跑证据**：Wave 1 商品 spec 8/8、Wave 6 三条库存 spec 19/19、Playwright 全量 **96 通过 / 7 跳过 / 0 失败**
  （12.8 分钟、串行 1 worker；7 项 skip 与 `f0-file-storage.spec.ts` 自身 7 条云端用例逐条对得上，Wave 1–8 无一落 skip；
  0 pageerror / 0 未处理 rejection 由 `scm-test-base.ts` 的 fixture 统一钉死）。后端 product 模块全量 **91/91**
  （0 失败 / 0 错误 / 0 跳过，一次性 IT 库，含 16 条图片中心与 5 条 UPDATE 导入真实库 IT）；前端 `src/` 本轮零改动
  （只改 E2E 用例与文档），故不重跑 lint / build。
- **入库方式**：本轮四项改动按关注点拆成提交（图片类型保持 / 三级分类前置校验 + E2E / 文档），
  已推送远端 `main`（其后的 VO 附件序列化收口与第三轮收尾见本节上方记录）；
  `§17` 待裁决项与「导出即可回导」契约仍待业务裁决，未随本轮入库。

### 2026-09-22 操作日志业务上下文与表格 / 查询体验（Wave 8，无迁移）：按业务对象精确下钻 + 查询条件本地记忆

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 8（§12）的**两部分**，均为**只增强、不新建事实存储**：
  - **A 通用操作日志按业务对象下钻**：把 SmartAdmin 原生操作日志升级为「可从某个业务对象精确回看其相关操作记录」。后端在既有 `t_operate_log` 读取路径上加业务过滤，**不新建审计 / 关联表、不改任何写入侧操作日志埋点**；前端在商品 / 客户 / 配送线路详情提供入口，携带业务上下文跳到统一日志页，并在详情弹窗**展示前递归脱敏**敏感字段。
  - **B 列表查询条件按用户本地记忆**：给列表页一个「记住上次筛选 / 分页」的便利，**只存浏览器本地、以 employeeId 隔离**，沿用仓库既有偏好约定（订单草稿 `xsy-scm:order-draft:${employeeId}`、导出列 `xsy-scm:export-columns:${employeeId}:${scene}`），**不建 `user_preference` 表、不把筛选值写进业务表**。第一版只接入客户列表，其余页面按「不要求一次重做所有页面」增量采用。
- **Flyway**：**0 迁移**。`t_operate_log` 结构与既有索引不变，业务过滤纯读侧 SQL；查询记忆落浏览器 localStorage。当前最大版本仍 V48，`migration_checksum_guard check` PASS（drift/missing/renamed/unbaked 均 0）。
- **API**：**无新增端点**，仅扩展既有 `POST /support/operateLog/queryPage`（`OperateLogQueryForm` 新增可选 `businessType`/`businessId`）。`OperateLogMapper.xml` 按业务类型分支做 STRPOS 精确匹配：PRODUCT 命中 `"spuId":<id>,` / `"spuId":<id>}`，CUSTOMER 命中 `"customerId":<id>` 边界，DELIVERY_ROUTE 命中 `/scm/delivery/routes/<id>` 与 `/<id>/` 两种 url 形态；**未知业务类型回 `1=0`，绝不退化成放全表**。`AdminOperateLogController` 以业务类型→读权限白名单校验：带 businessType/businessId 时必须持有一个既有领域读权限（`scm:product:query`/`scm:customer:query`/`scm:delivery:route:query`）才能按对象下钻，`administratorFlag` 仍绕过；无匹配类型即拒绝。
- **页面**：`operate-log-list.vue` 接收并**校验**路由 `businessType`（须在 `['PRODUCT','CUSTOMER','DELIVERY_ROUTE']`）/ `businessId`（须正整数），首次加载、刷新与切换对象（`watch` 路由业务参数）三处都重套上下文并回第一页，重置只按当前路由重算、旧本地筛选不得覆盖业务视图；把逐行 `JSON.parse(response)` + `uaparser` 提取为 `normalizeRow`，单行脏数据 try/catch 退化为 `null` 而不整页失败。`operate-log-detail-modal.vue` 展示参数 / 返回结果前 `maskedJson → maskSensitive`。商品 `product-detail.vue`、客户 `customer-detail.vue`、配送线路 `route-detail.vue` 各加「操作日志」入口（`v-privilege="'support:operateLog:query'"`），带各自 businessType 跳统一日志页。`customer-list.vue` 接入 `useQueryFilterMemory`（查询 `save`、重置 `clear`、挂载 `load` 后强制 `pageNum:1`）。**新增纯函数**：`operate-log-mask.ts`、`query-filter-key.ts`、`query-filter-memory.ts`（组合式，仅 customer-list 调用；深链 `customer-detail.vue` 刻意不接入，避免旧筛选污染 customerId 上下文）。
- **测试结果**：后端 `OperateLogBusinessFilterPgIT` **6/6**（三类精确匹配 + 前缀数字不误伤 + 无权限 30005 + 未知类型不放全表）、`AdminOperateLogBusinessGuardTest` **6/6**（读权限白名单）；前端新增 `w8-operate-log-audit-contract.test.mjs`（6）+ `w8-query-filter-memory.test.mjs`（3）= **9/9**，合并 `npm run test` **148/148**；`npm run lint` 0 error（3 warning 均在未触碰的历史文件）；`vue-tsc` 本 Wave 文件无新增诊断（已消除 businessType/businessId 赋值、routeBusinessContext 联合类型、normalizeRow/maskedJson 隐式 any）；`npm run build` 成功（2m）。
- **浏览器 / E2E 验证**：场景为 `e2e/scm-wave8-log-context.spec.ts`（临时账号真实登录：商品详情「操作日志」入口带 PRODUCT 上下文下钻、日志页刷新与重置不丢上下文且返回行落在该客户、零权限账号按业务对象查询被服务端拒绝且不泄露任何行、客户列表查询条件按登录人分别记忆）。执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录（Wave 8 组 4/4 通过、0 pageerror）。脱敏、下钻、查询记忆同时由后端 IT + 前端契约 / 纯函数单测钉死。**关键约束**：本地存储模式下上传/静态资源无 guard，本 Wave 不涉及文件访问。
- **未完成 / 遗留**：(1) **归属覆盖有天然上限**——操作日志按 `param`/`url` 里是否含该 ID 做 STRPOS 匹配，**新建类操作（对象 ID 尚不存在，如 add 的入参不含 id）与更早的、未写入该 ID 的历史记录无法归属到该对象**，故某对象的时间线**不保证是其全部历史**；已在列表页用 `a-alert` 显性声明该限制，不改写历史日志去「凑齐」。(2) 查询记忆仅接入 customer-list，其余列表页按需增量。(3) 深链 `businessId` 与后端既有权限耦合点：下钻要求持有对应领域读权限，若后续引入正式非管理员角色需连同 §F0-DEBT-01 读侧一并复核。
- **与计划的偏差**：计划 §12.1/§12.2 明确 Wave 8「0 迁移、0 新表」，实际一致；未新建 `scm_operate_log_relation` 之类关联表，而是复用 `t_operate_log` 既有 `param`/`url` 结构做读侧精确匹配——这是刻意的最小改动，代价即上述归属覆盖上限（已在 UI 显性提示而非假装完整）。查询记忆不落 `user_preference` 表，符合「第一版只放浏览器本地」的计划取向。

### 2026-09-22 客户 360° 业务上下文（Wave 7，无迁移）：5 Tab 只读整合 + 常购商品聚合

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 7（§11）——把客户档案周边分散在订单 / 价格 / 可见性三个模块、**当前项目已经存在的事实**集中到客户维度查看。**扩展现有隐藏深链 `customer-detail.vue`，不建第二个详情页**，把它从单页基础资料升级为 5 个 Tab：基础资料 / 最近订单 / 常购商品 / 协议价 / 可售商品，全部以**同一个 `customerId` 为上下文**。核心取向是**只读整合、不造事实副本、不建第二套查询**：最近订单复用 `POST /scm/order/query`（强制带当前 customerId、与订单列表同口径），协议价复用 `POST /scm/pricing/agreement-price/query`（与价格中心同源），可售商品复用客户 SKU 可见性查询（与 `customer_sku_visibility` 事实一致），基础资料沿用 `GET /scm/customer/detail/{customerId}`。**唯一新增的是「常购商品」只读聚合接口**，且明确禁止把 `customer_360_snapshot` / `customer_frequent_product` 作为事实副本落库。
- **Flyway**：**0 迁移、0 新表、0 新权限**。常购聚合的读权限用现有权限做**交集**（`scm:customer:query` ∧ `scm:order:query`，`@SaCheckPermission(mode=SaMode.AND)`），基础资料沿用客户查询权限，订单 / 价格 / 可见性仍由各自领域权限约束，**不因挂在客户页面就降低价格字段的读取门槛**。
- **API**（`CustomerController`，新增 1 个只读端点）：
  - `GET /scm/customer/{customerId}/frequent-skus?days=90&limit=20`，`@SaCheckPermission(value={"scm:customer:query","scm:order:query"}, mode=SaMode.AND)`。
  - 取数口径（`CustomerQueryService.frequentSkus` + 新增 `CustomerFrequentSkuDao`（跨域只读 sales_order / sales_order_item，自定义 SQL 走 mapper XML））：**只统计近 N 天、未删除、状态 = CONFIRMED 的订单**（`confirmed_at`，按 `Asia/Shanghai` 日界），**按 `sku_id` + `sale_unit_snapshot` 分组**（同一 SKU 历史单位改变时分行显示）；`order_count` = 组内 `COUNT(DISTINCT order_id)`（同单重复 SKU 不增订单次数）、`ordered_quantity` = 组内 `ordered_quantity` 之和（标注为「订购量」，**不是实重或结算量**）、`last_confirmed_at` = 组内最近确认时间、`recent_unit_price` = 该组**最近一单**（`confirmed_at DESC, order_id DESC` 取 `ROW_NUMBER()=1`）的 `locked_unit_price`，**取最新值而非均值、空值不回退**。`days` 服务端 clamp 到 `[1,365]`、`limit` clamp 到 `[1,100]`；未知 `customerId` 抛 `CUSTOMER_NOT_FOUND(40430)`。金额/数量沿用 `ScmFixedScale4Serializer` 4 位定点字符串。
- **页面**（`customer-detail.vue` 全量重写 + `customer-api.ts` + `customer.d.ts`）：`customerId` 由 `route.query.customerId` 经 `/^\d+$/` 校验，**URL 刷新可恢复**。5 Tab 用一个通用 `useTab<T>` 工厂（`rows/total/loading/error/loaded` + `ensure()` 仅在「customerId 有效且未加载且不在加载中」时拉取 + 局部 `seq` 竞态守卫）；切 Tab / 换 customerId 时 `resetAllTabs()` 后按当前 Tab `ensure`，各 Tab 只锁同一个 customerId，**API 层无 `customerApi.add/update/updateStatus/delete` 调用**。常购表格：数量列直显 4 位定点「订购量」原始串（非金额格式化）、行内按 `${skuId}-${unit}` 唯一、最近成交价用 `formatAmountOrDash`（null→「—」）、页脚提示「不跨单位求和」，`days` 可切换（默认 90）。
- **测试结果**：
  - 后端：新增 `CustomerFrequentSkuIT`（继承 `ScmW3PgITBase`，真实 PostgreSQL + 直接调 `SalesOrderService` 造单） **6/6**：① 聚合订单次数 / 订购量、**最近成交价取最新（改价单 9.0000）而非均值 5.1 或最旧 1.2**；② 草稿 / 待确认 / 已取消订单**不进入统计**；③ `limit` 按频次高→低截断；④ `confirmed_at` 在窗口外（200 天前）不计数、放宽到 365 天计入；⑤ `locked_unit_price` 为 NULL 时 `recent_unit_price` 返回 null 不回退；⑥ 未知客户 40430。既有 `ScmInventoryStocktakeIT` 等无回归。
  - 前端：新增 `test/w7-customer-360-contract.test.mjs` **4/4**（常购是带 `days/limit` 的只读 GET 且非 POST、5 Tab 复用既有查询接口且不触达 `customerApi` 写方法、各 Tab 锁同一 customerId + `ensure()` 守卫 + watcher 里 `resetAllTabs`、常购数量列为原始 4 位「订购量」+ 单位级 row-key + 价格 `formatAmountOrDash`）；合并 `npm run test` = **139/139**；改动文件 ESLint 0 错、`vue-tsc` **本 Wave 文件 0 条诊断**、`npm run build` **退出码 0**；`python tools/migration_checksum_guard.py check` **PASS**（48 个迁移，drift 0 / missing 0）。
- **浏览器 / E2E**：`e2e/scm-customer-360.spec.ts`（临时账号真实登录进客户详情深链、断言 5 Tab 可见、切「常购商品」等 `frequent-skus` 响应且断言为 GET + `code=0`、缺 `scm:order:query` 的角色拿不到常购数据、0 pageerror）。原实现靠外部注入的一次性管理员令牌门控、缺令牌即整组 skip，审计轮已改为与其余用例同款的自给自足临时账号链路；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① 严格零迁移（计划 §11.5 即「默认不需要」），未新建任何快照表；② 常购聚合的订单次数用 `COUNT(DISTINCT order_id)`、最近价用 `ROW_NUMBER()=1` 取最新，均在一条 CTE SQL 内完成，不在 Java 侧二次聚合；③ 「最近订单」直接复用列表页 `orderApi.query` 的既有分页与口径，未新造订单查询端点。
- **未完成 / 遗留**：「无价格权限不泄露敏感价格」目前由**接口级 SaMode.AND 双权限**保证（无 `scm:order:query` 即整个常购端点 403），未做「有订单查询权但价格字段级脱敏」的更细粒度分层——若后续引入正式非管理员业务角色，需连同 §F0-DEBT-01 读侧一并评估。

### 2026-09-22 盘点效率（Wave 6，V48）：签名快照 Excel 导入 + 复制历史盘点

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 6（§10）——盘点单补齐两项效率：
  ① 按仓库**导出带签名快照凭证的 Excel 模板**、仓管离线填实盘量后**整批导回为草稿**；② 从历史盘点单
  「复制到新建」。**复用既有 `InventoryStocktakeService.create`、余额锁、append-only 流水与 DRAFT→CONFIRMED 状态机，
  绝不另造库存账、绝不在导入路径写 `inventory_balance.quantity` 或 `inventory_movement`**——只有后续 `confirm` 才调整库存。
  改动的核心边界是「导入≠调整」：导入只建草稿，账面量 / 单位 / version 一律不信任单元格、由签名凭证给权威值并与持锁读取的
  当前余额逐项复核（消除「先校验再保存」竞态）；任一空行 / 空白实盘 / 来源集合增删替换 / 凭证被篡改或过期 / 快照漂移都
  **整批拒绝且不产生草稿**。
- **Flyway**：**1 个 data-only 迁移 `V48__scm_stocktake_import_permission.sql`**（选号依据：扫描 `db/migration/`
  当前最大为 V47，先 `migration_checksum_guard.py sync` 落快照、再 `check` 通过）。仅新增按钮权限菜单
  `scm:inventory:stocktake:import`（menu 837，挂盘点页 830 下，仅授 SUPER_ADMIN），无表结构变更。
- **API**（`InventoryStocktakeController`，`/scm/inventory/stocktake`，两端点均 `@SaCheckPermission("scm:inventory:stocktake:import")`）：
  - `GET /import/template?warehouseId`（只读，返回 xlsx 字节；凭证按当前操作者 + 实时余额签发，TTL 可配默认 240 分钟）；
  - `POST /import`（multipart `file` + `@RequestHeader Idempotency-Key`，**可选幂等**：响应丢失后同键重发命中重放、不建第二张草稿）。
  返回 `InventoryStocktakeImportResultVO`——整批语义走信封 `code=0` + `totalErrors>0`（`errors[]` 逐行给 `row/skuCode/column/code/message`），
  成功时 `stocktakeId` 为新草稿 id、`replayed` 标注重放。错误码是字符串（`BLANK_ACTUAL / CREDENTIAL_INVALID / OPERATOR_MISMATCH /
  SOURCE_UNKNOWN / SOURCE_MISSING / DUPLICATE_SKU / SNAPSHOT_STALE` 等），不新增 SCM 数字错误码。
- **页面**（`inventory-stocktake-list.vue` + `inventory-stocktake-api.ts` + `inventory-types.ts`）：工具栏加「导出快照模板」
  与「导入盘点」（两者均 `v-privilege="scm:inventory:stocktake:import"`）；导入结果用数据驱动弹窗（成功给草稿号、失败给逐行错误表）；
  同一文件的幂等键用 `WeakMap<File,string>` 稳定复用、仅成功才清除。行内加「复制到新建」（全状态、`scm:inventory:stocktake:add`）——
  **纯前端**：只读 `detail` + 余额 `query`，把仓库 / SKU 集合 / 当前记账单位带入未保存的新建表单，实盘量一律留空要求重新清点，
  任一 SKU 已无余额即显性报错、整单不复制、绝不悄悄丢行，也不自动落库。编辑明细表加只读「记账单位」列。
- **测试结果**：
  - 后端：`ScmInventoryStocktakeIT` **15/15** 无回归；新增 `ScmStocktakeImportPgIT`（真实 PostgreSQL + 真签名凭证 + 真 POI 读写 xlsx）
    **7/7**：成功导入只建草稿、余额与盘点流水均不变，确认才写 `STOCKTAKE_LOSS`（证明导入≠写库）；同 `Idempotency-Key` 重放返回同一草稿；
    空白实盘 `BLANK_ACTUAL`、篡改凭证 `CREDENTIAL_INVALID`、来源替换 `SOURCE_UNKNOWN`+`SOURCE_MISSING`、重复行 `DUPLICATE_SKU`
    全部整批拒绝且不落草稿；**核心漂移用例**：账面 10 导出 → 出库 2（版本自增）→ 填实盘恰等于当前账面 8 仍 `SNAPSHOT_STALE` 整批拒绝。
  - 前端：新增 `test/w6-stocktake-import-contract.test.mjs` **4/4**（模板走只读 `getDownload`、导入走带 `Idempotency-Key` 的 POST 且不触达
    confirm、两端点由 `scm:inventory:stocktake:import` 把关、复制只用 `detail`+余额 `query` 且不建草稿、API 层无后端复制命令）；
    合并 `npm run test` = **135/135**；改动三文件 ESLint 0 错、`vue-tsc` 本 Wave 文件 0 条诊断（存量 `system/role` / `business/oa` 与本次无关）。
- **浏览器 / E2E**：`e2e/scm-stocktake-import.spec.ts`（临时账号真实登录，5 项）：只读账号取模板与导入均被服务端 30005 把关；
  页面选仓库后导出快照模板并带回该仓库；填好实盘的模板经页面上传 → 建出草稿且逐行实盘量落库；确认改余额后用确认前
  导出的凭证再导 → `SNAPSHOT_STALE` 整批拒绝且不落草稿；复制历史盘点按 `pageSize<=100` 分页取余额、实盘列整列为空、
  不新增单据。原一次性令牌门控（缺令牌即整组 skip）已在审计轮改为临时账号真实登录；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① 迁移选号 **V48**（计划文档写的号以 `db/migration/` 当前最大号之后为准、不改历史 Flyway）；
  ② 整批拒绝以 `code=0`+`totalErrors` 字符串码回报，**不新增 SCM 数字错误码、不改 `InventoryErrorCode`**（与商品 / 订单导入同一取向）；
  ③ 「复制历史」严格做成纯前端只读组合（复用 `detail`+余额查询），不引入后端复制命令、不新增幂等端点。
- **未完成 / 遗留**：无。「导入成功且填好实盘」原先只有后端 IT，审计轮已在浏览器里以改写后的真实模板上传并
  断言建出草稿、逐行实盘量落库（`e2e/scm-stocktake-import.spec.ts`，同文件覆盖权限把关、快照漂移整批拒绝、
  复制历史盘点），上一版此处写的「不做」已失效。

### 2026-09-22 配送打印追踪（Wave 5，V47）：按订单 / 按客户双视角 + 幂等打印登记

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 5——在既有 L0–L2
  配送静态路线之上补「配送打印」：**按订单 / 按客户两个只读视角** 让调度核对整条线路的打印覆盖度，
  再以**带幂等键的正式生成打印**登记计次。**只在 `delivery_route_order` 上扩展三列、不新建副本表、
  不新增菜单或权限、不触碰库存 / 出库 / 发车 / GPS / 签收等 L3 能力**；打印登记仅累加历史计次，
  既不代表物理出纸、也不改线路状态、不产生任何 `inventory_movement`。
- **Flyway**：**1 个结构迁移 `V47__scm_delivery_print_tracking.sql`**（选号依据：扫描 `db/migration/`
  最大已应用为 V46，先 `migration_checksum_guard.py sync` 落快照、再 `check` 通过）。
  `ALTER TABLE delivery_route_order` 加 `print_count INTEGER NOT NULL DEFAULT 0 CHECK(print_count >= 0)`、
  `last_printed_at TIMESTAMPTZ`、`last_printed_by VARCHAR(64)`（存 `userType:userId`），并写字段 COMMENT；
  **无新表、无索引、无 data-only 授权**（打印权限沿用 V43 已有的 `scm:delivery:route:print`）。
- **API**（`DeliveryRouteController`，`/scm/delivery`）：
  - `GET /routes/{id}/orders-view`、`GET /routes/{id}/customers-view`（`@SaCheckPermission("scm:delivery:route:query")`，
    只读、无 `@OperateLog`、无幂等键）；订单视角逐单返回 `printCount / lastPrintedAt / printStatus(PRINTED|UNPRINTED)`；
    客户视角聚合该客户线路内订单，`printStatus ∈ {PRINTED, UNPRINTED, PARTIAL}`（`partially` = 部分订单已打印）。
  - `POST /routes/{id}/print/orders`、`POST /routes/{id}/print/customers`（`scm:delivery:route:print` +
    `@OperateLog` + `@RequestHeader Idempotency-Key`），入参携带 `version` 乐观锁；仅当线路处于
    `PLANNED / DISPATCHED / COMPLETED` 才可打印，否则拒绝。客户视角 `print/customers` 支持
    `orderPrintFilter = ALL | PRINTED | UNPRINTED` 过滤本次纳入的订单。**正式入口统一走 POST 计次，
    既有 `GET /routes/{id}/print` 仅预览、绝不计次**。
  - 计次经**通用 `idempotency_record`**（`OrderIdempotencyService.claim/replay/complete`，
    scope `DELIVERY_PRINT_ORDERS:{id}` / `DELIVERY_PRINT_CUSTOMERS:{id}`，按操作者前缀隔离）：
    同一 Idempotency-Key 重放只累加一次，`markPrinted` 与幂等记录同事务，回滚不留下孤计次。
- **页面**（`route-detail.vue` 新增「配送打印」`a-tab-pane`，首次进入才拉取；`delivery-api.ts` 复用订单域
  幂等模式——`printKeys` 命中即复用、仅成功才 `delete` 换新键）：`a-segmented` 切订单 / 客户视角、
  客户视角支持「全部 / 仅未打印 / 仅已打印」筛选、两 `a-table` 各带行选择与打印状态 `a-tag`、
  「生成打印 · 登记 N」按钮受 `v-privilege="'scm:delivery:route:print'"` 且 `canPrint`（可打印状态）双门禁。
- **测试结果**：
  - 后端（真实 PostgreSQL）：新增 `DeliveryPrintTrackingIT` **3/3**（① 订单视角计数与登记：只读 GET 不计数、
    `printOrders` 纳入单 +1、同键重放仍 +1、换键再 +1、全程 `inventory_movement` 为 0、状态不变；② 客户视角聚合与
    PARTIAL：`printCustomers(c1, UNPRINTED)` 只纳入未打印单、兄弟单不受牵动；③ 负向：外线订单 `41101`、
    version 过期 `40921`、DRAFT 线路不可打印 `41101`），既有 `DeliveryRouteServiceIT` **2/2** 无回归。合计 **5/5**。
  - 前端：新增 `test/w5-delivery-print-contract.test.mjs` **4/4**（双视角只读 GET 命令带 Idempotency-Key 的 POST、
    打印仅门控在可打印状态 + `scm:delivery:route:print`、接口层无库存出库 / GPS / 签收 / 发车端点、三态标签一致）；
    合并 `npm run test` = **131/131**；改动文件 ESLint 0 错、`vue-tsc` 本 Wave 三文件 0 条诊断（仓库
    `system/role` / `business/oa` 存量诊断与本次无关）、`npm run build`（vite production）通过；迁移校验和守卫 PASS。
- **浏览器 / E2E**：新增 `e2e/scm-delivery-print.spec.ts`（双视角只读查询不改计次、正式 POST 计次 +1 且同键重放不重复 +1、
  打印后线路状态不变、页面「配送打印」标签只读浏览阶段无库存 / 出库写请求）。原一次性令牌门控（缺令牌即整组 skip）已在审计轮改为临时账号真实登录；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① 迁移选号 V47（计划文档若写其它号以 `db/migration/` 当前最大号 V46 之后为准、不改历史 Flyway）；
  ② 打印**不追踪内容版本**——`print_count` 只表「生成过打印」的历史次数，线路改版后不自动清零（本轮明确不做内容指纹）；
  ③ 严格守 L0–L2：负向契约只针对 **API 端点集合**（无库存出库 / GPS / 签收 / 发车入口），不因表头既有的
  `planned_departure_time`「计划发车」展示字段而误判为 L3。
- **未完成 / 遗留**：打印内容版本追踪（改版是否清零）留待业务裁决，本轮不做。

### 2026-09-22 业务待办与站内提醒（Wave 4，V46）：首页只读待办聚合 + 驳回站内信

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 4——
  A. 首页「业务待办」以**只读 Pull** 聚合当前登录人可见的待处理量（四张卡片：库存异常预警、
  待仓库确认入库、待审批报损报溢、草稿配送线路）；B. 报损报溢**驳回**时经 SmartAdmin 原生站内信
  通知录单人。**不新建第二套消息中心、不新增 SCM 消息表、不引入 Kafka / MQ / 事件总线 / 定时任务 /
  去重表**；待办接口不写任何业务表，卡片可见性与计数全由后端按权限决定，前端不重复判权限、不缓存数字。
- **Flyway**：**1 个 data-only 迁移 `V46__scm_todo_permission.sql`**（选号依据：扫描 `db/migration/`
  最大已应用为 V45，计划文档写的 V47 已过时；先 `migration_checksum_guard.py sync` 再 `check` 通过）。
  新增隐藏目录 `1100 业务待办`（`/scm-todo`，`visible_flag=false`）与功能点 `1101 待办查询`
  （`scm:todo:query`），仅授 `role_id=1` SUPER_ADMIN；`ON CONFLICT DO NOTHING` 幂等，末尾 `setval` 前进序列。
  **各领域查询 / 操作权限一律不扩大**——待办入口权限 `scm:todo:query` 只用于能否调用聚合接口，卡片是否出现
  取决于该领域既有权限（如 `scm:inventory:warning:query`、`scm:purchase:receipt:putaway`、
  `scm:inventory:loss-gain:approve|reject`、`scm:delivery:route:plan`），复用 `LoginManager` 现算的 permissionList。
- **API**：`GET /scm/dashboard/todo`（`ScmTodoController`，`@SaCheckPermission("scm:todo:query")`，
  无 `@OperateLog`、无幂等键、只读）。入参无；出参 `List<ScmTodoVO>`：`key / label / count(Long) / route`。
  `ScmTodoQueryService` 用 `SmartRequestUtil` 取当前员工 → `getUserPermission().getPermissionList()` →
  逐卡片 `visibleTo`（持全部 allPerms 且（无 anyPerms 或命中任一））判定；可见卡片 count 由**复用既有分页查询**
  （`queryWarningPage` / `receiptQuery` / 报损报溢 `queryPage` / 线路 `query`）以 `pageSize=1` 读 `total` 得到，
  不改任何领域算法。**无权卡片直接省略（不返回 0）**，有权且当前无任务返回 `count=0`。
- **站内信**：`InventoryLossGainService.reject(...)` 在 `markRejected==1` 之后调用私有 `notifyMakerRejected`，
  解析单据 `createdBy`（`"userType:userId"`）为收件人，用原生 `MessageService.sendMessage`（`MAIL` 站内信、
  `dataId=单据 id`）落 `t_message`，**在同一 `@Transactional` 内**：驳回回滚即不发消息；并发 / 重复驳回只有
  抢到 `markRejected==1` 的那次会通知，恰一条。`createdBy` 缺失或格式异常时跳过通知，不影响驳回本身。
- **页面**：新增 `src/api/business/scm/dashboard-api.ts`（只读 `getRequest('/scm/dashboard/todo', {})` +
  `ScmTodo` 类型）、`src/views/system/home/components/business-todo-card/home-business-todo.vue`
  （复用 `DefaultHomeCard`，`onMounted` 拉取、Badge 计数、点击 `router.push(todo.route)` 带条件跳转，
  计数 0 用中性灰避免误读为异常）；`src/views/system/home/index.vue` 右栏在更新日志下方挂卡片，
  外层 `a-col` 用 `v-privilege="'scm:todo:query'"` 门禁（无权整卡隐藏、不发请求）。
- **测试结果**：
  - 后端：`ScmTodoQueryServiceTest` **4/4**（无 DB，Mockito：仅有待办权限不取任何领域计数、全权限四张卡片
    各调一次、有权零任务 `count=0` 卡片保留、仅 query 无 approve/reject 时省略报损报溢卡）；
    `ScmInventoryLossGainIT`（真实 PostgreSQL）**15/15**（驳回用例新增断言：成功驳回后按 `data_id + receiver`
    查 `t_message` 恰 1 条，终态 41029 的重复驳回不追加消息）。合计 **19/19**。
  - 前端：新增 `test/w4-business-todo-contract.test.mjs` **4/4**（只读 GET 不引写命令 / 幂等 / sendMessage、
    卡片挂载即拉取且不做前端权限判断 / 本地缓存、点击直推后端 route 不前端拼状态、入口 `scm:todo:query`
    门禁 + 复用 `DefaultHomeCard`）；合并 `npm run test` = **127/127**；改动文件 ESLint 0 错、
    `vue-tsc` 本 Wave 文件 0 条诊断、`npm run build`（vite production）通过。
- **浏览器 / E2E**：新增 `e2e/scm-dashboard-todo.spec.ts`（待办接口只读数组契约 + route 自带条件、
  无 `scm:todo:query` 员工被守卫拒、首页点击卡片落到带条件列表页且全程无写请求）。原一次性令牌门控已在审计轮改为临时账号真实登录；端点行为同时由后端单元 / IT、前端契约由单测 / 类型 / Lint 覆盖。执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① 迁移选号 V46（非计划文档写的 V47），按「以当前 `db/migration/` 最大号为准、不改历史
  Flyway」执行；② 待办入口用**独立 `scm:todo:query`** 门控而非任一领域权限，且明确「不扩大领域权限」——
  卡片可见性 = 待办入口 ∩ 领域权限，无权卡片省略而非返回 0；③ 站内信同步写在驳回事务内（非异步 / 非事件），
  与「恰一条、事务原子」的验收一致。
- **未完成 / 遗留**：浏览器验收已于 2026-09-23 执行通过。

### 2026-09-22 订单录单效率（Wave 3）：草稿恢复 + 历史复用 + 最近已确认订单价

- **范围**：落地 `docs/plan/current-module-optimization-from-sdongpo-v17.4.md` Wave 3 §7.5——录单页补齐三项效率：
  未提交草稿的**本地恢复**、从历史订单「复用为新单」、明细行点开看**最近已确认订单价**旁证。全部接在既有
  `order-form-drawer` / `order-item-editable-table` / `order-list` 上，无新增路由 / 菜单。**不引入后端「复制订单」
  命令**（历史复用只读 `GET detail` 后按当前价重新解析，绝不沿用历史锁价），不改订单状态机、不改价格优先级、
  不新增第二套定价事实。
- **Flyway**：**0 迁移、0 新权限**。参考端点复用查询权 `scm:order:query`，「复用为新单」按钮复用录单权
  `scm:order:add`，历史复用走既有 `GET /scm/order/detail/{id}`；草稿为纯前端本地存储，无服务端草稿表。
- **API**：`GET /scm/order/reference/recent-prices`（`@SaCheckPermission("scm:order:query")`，**只读**、无幂等键、
  无 `@OperateLog`、不触碰写命令服务与 `PriceResolver`）。入参 `customerId + skuId + limit`（服务层裁剪到 `[1,10]`），
  出参 `List<OrderRecentPriceVO>`：`itemId / orderId / orderNo / createdAt / confirmedAt / orderSource /
  orderedQuantity / unitPrice(=locked_unit_price) / priceSource(=locked_price_source) / saleUnit(=sale_unit_snapshot)`，
  单价与数量按四位定点字符串下发。
- **§7.5 参考口径（按更新后的计划收紧）**：只取未删除的 **CONFIRMED** 单（排除 DRAFT / PENDING / CANCELLED，
  取代旧实现的 `status <> 'CANCELLED'`）；按 `confirmed_at DESC, order_id DESC` 稳定倒序；`limit` 约束的是
  **最近 N 张订单**而非行数（子查询先定 N 张单再回这些单的匹配明细），带 `itemId` 以便同单同 SKU 多行时逐行区分；
  锁定单价缺失时以 `null` 展示，**不用草稿价 / 当前价兜底**。前端历史单位与当前单位不一致时仅提示「不可直接比较」，
  不自动换算、不回写解析单价、不前端重算。
- **页面**：
  - `order-list.vue`：操作列加「复用为新单」（`v-privilege="scm:order:add"`，仅 CONFIRMED 行），点开抽屉走
    `openFromHistory(orderId)`。
  - `order-form-drawer.vue`：新建态进入时若有本地草稿提示「恢复 / 丢弃」（`promptRestoreDraft`）；创建成功
    （`isNew`）清草稿；统一走 `closeDrawer` 关闭；`defineExpose({open, openFromHistory})`。
  - `order-item-editable-table.vue`：解析单价列加「历史价」气泡，按「当前客户 + 当前行 SKU」现查现显，
    每行独立缓存到本次抽屉生命周期；`:key="p.itemId"`、`confirmedAt` 日期、单位后缀与不一致告警。
- **测试结果**：
  - 后端：`OrderWebTest` **7/7**（新增只读端点用例：固定定点序列化 + `verifyNoInteractions(service, prices)`
    证明不触写命令 / 定价）；`SalesOrderQueryRecentPriceTest` **1/1**（`limit` 裁剪到 `[1,10]`）；
    `SalesOrderRecentPriceIT`（真实 PostgreSQL）**2/2**（CONFIRMED-only 排除 DRAFT/PENDING/CANCELLED、
    客户维度隔离、订单分组 `limit` 截断）。
  - 前端：新增 `test/w3-order-entry-contract.test.mjs` **5/5**（只读 `getRequest`、不复用幂等命令封装、
    草稿恢复 / 历史复用接线 Drawer、创建成功清草稿、只读 `GET detail` 不引入复制命令、明细现查且**不回写
    `draftUnitPrice` / 不 `Decimal` 重算**、复用为新单沿用 `scm:order:add`）与 `order-form-model.test.mjs` **6/6**；
    合并 `npm run test` = **123/123**；改动文件 ESLint 0 错、`vue-tsc` 本 Wave 文件 0 条诊断、`npm run build` 通过。
- **浏览器 / E2E**：`e2e/scm-order.spec.ts` 用例 9（历史复用预填新草稿 + 最近已确认订单价气泡展示锁价与来源订单）
  与用例 10（未提交草稿本地留存并在重开时提示恢复）、用例 11（§12.3「切 SKU 不串 / 切客户不串」：自建两个合作中客户
  与三张已确认单，在真实页面上按「整箱 → 换散装 → 换回整箱 → 换客户」四步逐次开气泡，断言每次只出现当前
  (客户, SKU) 档的价与来源订单号、且不出现另一档的价与订单号）。用例 11 不借用其他用例留下的数据，单独执行亦成立。
  端点行为已由 3 条 IT / Web 用例、前端契约由单测 / 类型 / Lint 覆盖；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① §7.5 参考价从「排除 CANCELLED」收紧为「只取 CONFIRMED」，并按更新后的计划改为按 `confirmed_at`
  倒序、订单分组 `limit`、暴露 `itemId / saleUnit / confirmedAt`；② 因 `uk_sales_order_item_order_sku_active`（V13）
  约束每 `(order, sku)` 只有一条活动明细，「同单同 SKU 多行」在当前 schema 下不可能出现，故订单分组 `limit` 与
  行数在当前库等价（SQL 仍按逐行 `itemId` 返回以防未来放开该唯一键）。
- **未完成 / 遗留**：浏览器验收已于 2026-09-23 执行通过（用例 9 / 10 / 11）。

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
  四位定点数量、欠收与超收互斥、切页签渲染工作台表）、用例 14（§12.3「打印」：真实页面点行内打印与批量打印，
  只替换最末端的 `print()` 调用以采集隐藏 iframe 里已渲染的文档，断言文档带出该单的表头、商品名与计划量 / 单价、
  备注里的脚本标记只作为文本出现（文档内 `script` 元素数为 0）、打印全程不发任何写请求且采购状态一位不动）。
  端点行为已由 6 条 IT、前端契约由单测 / 类型 / Lint 覆盖；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：① §6.4 建议的 `GET /scm/purchase/{id}/print` 落为**纯前端打印**（隐藏 iframe + `contentWindow.print`），
  零写接口、零新权限，符合 AGENTS §23「不为单页各自造打印设施」；② §6.7 的可选权限迁移 `V46` 未启用，因三端点复用既有权限即达成 0 迁移。
- **未完成 / 遗留**：批量确认收货（§6.4 推迟项，需另开设计明确失败语义）不在本 Wave。

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
  每行状态在五值内且数量匹配四位定点、跑完需求表仍为 0、浏览器切页签渲染 + 截图）。端点行为已由 8 条 IT、前端契约由单测 / 类型 / Lint 覆盖；执行结果见「2026-09-23 Wave 1–8 审计修复与全栈验收」记录。
- **与计划的偏差**：无。计划 §6A.11 要求「0 迁移、并入既有页」，落地一致。
- **未完成 / 遗留**：预览的 Phase 2「一键把缺口转采购需求」不在本 Wave（只读阶段不写库）。
- **旁注（非本 Wave 引入）**：全量前端测试里 `w4-order-contract.test.mjs` 曾有一条**既有**正则用例因 `\r\n` 跨行匹配失败
  （`.` 不匹配换行），与本次改动无关；审计轮已按该原因修复，现该类 6/6 通过。

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
  - `V49`（结构 + 数据，审计 §7.1 修正）：V44 的 `PRIMARY`/`DETAIL` 与 `(image_type='PRIMARY') = is_primary`
    让「主图」同时存在两个事实源，最新计划改为 `image_type ∈ {GALLERY, DETAIL}`（图集 / 详情图），
    主图唯一事实只剩 `is_primary`。历史迁移不可修改，因此在这里新增一步：先移除 V44 两条 CHECK，
    把存量 `PRIMARY` 改写为 `GALLERY`，再加新 CHECK 与「每 SPU 至多一张主图」的唯一索引；
    建索引前先按「同 SPU 内 id 非最小的主图行」降级多余主图并 `RAISE NOTICE` 显性提示，不静默改账。
- **API**：
  - `GET /scm/product/import/template`（`scm:product:import`）真实 xlsx 模板；
    `POST /scm/product/import`（`scm:product:import`，≤10MiB `.xlsx`，经 `securityFileService` 校验）；
    两者均带 `mode=CREATE|UPDATE`（默认 CREATE），**UPDATE 额外要求 `scm:product:update`**（服务端 `StpUtil.checkPermission` 兜底，下载更新模板同样要求），因为导入权不等于编辑权；
    `POST /scm/product/export`（`scm:product:export`，复用查询条件 + `SmartExcelUtil`）。
  - 图片中心 `GET /scm/product/image/query`（`scm:product:image:query`）；
    `POST .../batch-bind`、`batch-remove`、`set-primary`、`reorder`（`scm:product:image:batch`，均 `@OperateLog`）。
    所有图片写接口经 `ProductImageSyncManager.sync`：只接受合法 `public/image/` fileKey、清旧主图、保证每 SPU 至多一张 PRIMARY。
- **导入语义**：整批事务——任一行有错则 0 行写入；错误定位到「Excel 行 + 列 + 原因码」，可准确指认 SPU/SKU 编码重复、
  分类 / 单位 / 标签不存在、停用单位用于新商品等；不把历史批次落库（第一版即时查看 / 下载失败明细）。
- **UPDATE 模式语义（审计 §7.2 要求逐条对齐）**：模板自带 `模板版本` 与四个定位键列（`SPU ID`/`SPU版本`/`SKU ID`/`SKU版本`），
  定位键缺失或版本不匹配即整批拒绝；**未在 Excel 中出现的 SKU 不等于删除 SKU**（按 SPU 分组只改出现的行）；
  **空白单元格 = 保持原值**，显式清空只能写 `(清空)` 且仅放开别名 / 助记码 / 品牌 / 产地 / 标签编码 / 条码六列
  （其余列写该串直接报错）；`SPU编码` / `SKU编码` 等系统派生与锁定列写入即 `FIELD_LOCKED`；
  SKU 不属于该 SPU 报 `SKU_NOT_OWNED`；写入复用既有商品更新服务的校验与乐观锁，任一行版本冲突则
  **整批 0 行写入**并返回 `VERSION_CONFLICT(40921)`，不做部分成功。
- **页面**：
  - 商品列表工具栏新增「导入 / 导出 / 图片中心」入口（按权限显示）；导入弹窗下载模板、选 `.xlsx`、
    loading + 未选文件禁止提交、失败逐行完整展示并可即时导出 CSV。
  - 新增图片中心页（路由 `/product/image-center`）：左列按关键字 / 「仅无主图」筛商品，右列单 SPU 图集维护
    （设主图 / 移除 / 拖动或按钮排序 / 上传绑定到当前 SPU），顶部「按文件名批量导入」先出命中·歧义·未匹配预览，
    仅对命中项写入，未匹配与歧义绝不静默丢弃；无 `image:batch` 权限时批量写入口隐藏。
- **测试结果**：
  - 后端：`ProductImageCenterPgIT` 全量 `@SpringBootTest` + `@Transactional` 打真实 PostgreSQL **11/11 通过**
    （含每 SPU 至多一张主图由唯一索引兜底、并发设主图不产生两张主图、`GALLERY`/`DETAIL` 分组语义、
    私有前缀 fileKey 拒绝、无权限 / 操作日志）。
    `ProductImportServiceTest` 单元级 **22/22 通过**（POI 解析 + 各类拒绝原因 + 错一行整批不写的判定 +
    UPDATE 模式的定位键 / 版本 / 锁定列 / `(清空)` 白名单判定）。
    `ProductImportUpdatePgIT`（审计轮新增，真实库）**4/4 通过**：更新落库且空白列原值保留、
    旧版本重放整批 `VERSION_CONFLICT` 且库里数字不变、未列出的 SKU 不被删、缺 `scm:product:update` 被拒。
  - 前端：`test/product-import-model.test.mjs`（按行分组、行 0 文件级错误、整批拒绝、忽略大小写与扩展名匹配、
    未匹配/歧义显性暴露）**5/5 通过**；`scm/product` 范围 `vue-tsc --noEmit` 与 ESLint 均无错；Vite 生产构建通过。
- **浏览器 / E2E**：`e2e/scm-product.spec.ts` 的 PCO-2 场景（导入入口 + 提交闸门、图片中心筛无图 + 单商品维护 +
  批量预览 0 命中禁止绑定、只读账号看不到导入 / 导出、**CREATE / UPDATE 导入各自真实往返**）已于 2026-09-23
  在全栈环境执行通过 **8/8、0 pageerror**。CREATE 用例自建三级分类链与新增模板文件：页面以新增模式上传
  「一个 SPU 两行 SKU」→ 详情接口见两个 SKU 且默认 SKU 只有一个 + 列表按编码只查到一条商品 → 同商品再加一行
  但该行分类编码不存在时必须整批 `importedProducts=0`、页面明示「本次没有任何商品写入」且既存商品不会多出第三个 SKU。
  UPDATE 用例是真实链路而非模拟：下载 UPDATE 模板 → 填入该商品真实的四个定位键且
  **只改「别名」一列、品牌列留空** → 页面以更新模式上传 → 详情接口断言新别名生效且留空的品牌原值仍在 →
  同一份文件重放必须 `updatedProducts=0` + `VERSION_CONFLICT` + 页面明示「本次没有任何商品写入」+ 库里版本不变。
- **与计划的偏差**：
  - 计划要求 `ProductImportIT`（真实库集成测试）；**审计轮已补 `ProductImportUpdatePgIT`**（真实库 4/4），
    与既有的单元级校验共同覆盖 CREATE / UPDATE 两条写路径。
  - 迁移号：计划把 PCO-2 排为 V44/V45，落地一致，未与配送 V42/V43 或 FA-0 的 V41 冲突；校验和守卫 `check` 通过。
    **图片类型模型与最新计划不一致已按「不改历史迁移」原则在 V49 追加修正**（见上），未回改 V44。
  - **审计发现（待业务裁决，对应修复计划 §17）**：商品列表的普通导出（24 列，含分类路径与标签**名称**）与
    可回导的 UPDATE 维护模板（25 列，含分类编码与标签**编码**）是两套刻意不同的列集合，
    把普通导出原样回传会得到逐列 `HEADER_INVALID`。当前行为安全（拒绝而非误写），但「导出即可回导」是否符合业务预期需要裁决。
- **未完成 / 遗留**：「查看历史导入批次」按需再建（第一版明确不做）；上述「普通导出 / 维护模板列集合差异」待业务裁决。

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

## Finance R0 报表中心（2026-09-23，V50–V51）

按 [`docs/plan/finance-reporting-r0-plan.md`](plan/finance-reporting-r0-plan.md) 实施只读报表地基。
基线核对：`git fetch` 后本地 HEAD 与 `origin/main` 同为 `c9f6954`，迁移最大号 V49，故选号 V50 / V51。

**交付边界**：新增后端只读域 `module.scm.report`（Controller 1 + Service 5 + DAO 1 + Mapper XML 1，
41 个只读端点、11 个 Excel 导出）、前端 5 张页面 + 共享报表组件、V50（菜单与权限 1200–1216，
仅授 SUPER_ADMIN）、V51（`sales_order.confirmed_at` / `order_refund.completed_at` /
`purchase_receipt.confirmed_at` 三条部分索引）。**新财务事实表数量 = 0**：不建
receivable / payable / payment / voucher / report_snapshot，不改任何写流程与状态机。

**守住的口径**（这些是本次的实现约束，不是文档承诺）：销售统计固定
`CONFIRMED + confirmed_at + settlement_*`，采购固定提交后四态按 `submitted_at`；
收货确认与库存入账分列（`putaway_status` 与 `PURCHASE_IN` 流水是两条生命周期）；
命名里没有营业收入 / 已收 / 未收 / 应收 / 应付 / 毛利；销售出库成本不猜归到订单；
数量不跨单位相加（多单位场景返回按单位分组的文本）；收发存不给期初 / 期末，因为流水存的是
本次 `unit_cost` 而非变动后的 `avg_cost`，且账本不从库存起点完整覆盖；
金额与数量保持 4 位事实精度；日界一律 Asia/Shanghai 半开区间，跨度上限 366 天。

**权限模型**：`scm:report:{overview,sales,purchase,inventory}:query` 管页面，
`scm:report:cost:query` 独立管成本（字段级失败关闭抹除为 `null`，前端渲染 `—`；
「当前库存价值」整页是成本视图故接口直接拦），`scm:report:export` 与对应查询权限 AND。
导出与列表调用同一查询方法，超过行数上限明确拒绝（41112）而不是静默截断。

验证结果：

- 迁移校验和守卫 `check`：51 条、drift 0 / missing 0 / renamed 0。
- `ScmReportPgIT` **9/9**（一次性临时库 V1→V51）：25 条报表 SQL 全部在真实 PostgreSQL 执行（空筛选与
  全筛选各一轮）、DRAFT/PENDING 不进统计、一单多行不放大退款、均价分母为 0 返回 null、
  366 天边界两侧、无成本权限时成本字段为 null、控制器权限码与 `t_menu.api_perms` 逐条对齐、
  11 个导出写出合法 xlsx。
- 项目闸门 `python tools/verify.py all`：migration-checksums / backend / ts-ratchet / frontend-lint /
  frontend-build / **e2e 全部 exit 0**；后端 **905 项，Failures 0、Errors 0、Skipped 5**（5 项为未配置
  对象存储环境的 cloud IT，按既有约定跳过）。全量 Playwright 同样 exit 0，其中 7 项是云端存储专用
  spec 在本地存储下的按设计 skip。闸门整体判 **INCOMPLETE** 而非 PASS，唯一原因就是这两组
  环境性排除（后端 5 + E2E 7），没有任何失败项。
- 前端：`npm run lint` 0 错误（3 条既有警告）、`npm run test` 全绿（报表模型 25 项 + 报表契约 22 项）、
  `npm run build` 通过、
  TS 棘轮 PASS 且新增 0；5 个菜单 `component` 路径逐条对应到真实 `.vue` 文件。
**契约测试的收口（同一轮）**：前端 `test/finance-report-contract.test.mjs` 首跑有 4 项红，逐条判定后
只有页面侧才改页面，其余是检查本身不成立：

- 它引用了一个未定义变量（写文件时被截断），且拿字面 `/scm/report/...` 去比对源码里的
  `` `${BASE}/...` `` —— 永远匹配不上，已按真实源文本形态匹配；
- 「每页都要有 `exportQuery()`」对概览页不成立：计划 §30 没有概览导出端点，概览页**有**导出反而是
  越界，因此改为四张有导出的页必须装配、概览页必须没有；
- 「导出装配不得带分页」原按「函数体恰好一行 return」匹配，多行写法直接被判成缺装配，改按函数体匹配；
- 「不得内联流水类型清单」原正则 `` =\s*\[[^\]]*PURCHASE_IN `` 会命中概览页 KPI 卡的提示文案
  （`hint: 'PURCHASE_IN 流水的 SUM(...)'`），只保留「把枚举名当数组元素」这一真实形态；
- 「报表页不得出现入库动作」原按词匹配 `确认入库` 会把 `WAREHOUSE_CONFIRM` 的正式中文名
  「仓库确认入库」说明文案判成违规，改成按 affordance 判（不得调用 `putaway(`、不得有入库按钮）。

- Playwright `e2e/scm-report.spec.ts` **11/11**（真实浏览器，共享夹具断言 0 pageerror）：
  五张报表页逐个真实渲染（非活动 Tab 面板会留在 DOM 里，因此可见性过滤是必需的）、
  概览页命名与默认本月、销售只计 CONFIRMED 且金额等于订单结算总额、采购排除 DRAFT/CANCELLED、
  `WAREHOUSE_CONFIRM` 收货后只出现在收货与待入库、putaway 后进入库明细且成本等于
  `quantity × unit_cost`、`DIRECT` 同事务即可见、流水方向由枚举派生、损耗只计盘亏与报损、
  收发存不伪造期初期末、导出为合法 xlsx 且文件名由服务端给出、无权限账号在接口层被拒。
- 索引：`EXPLAIN` 确认三条新索引谓词匹配且可被选中（含 Index Only Scan），
  `inventory_movement` 复用既有 `idx_inventory_movement_occurred` 未新增。

过程中由验证暴露并当场修掉的真实缺陷：

1. `salesByCustomer` 引用了 `cat/cat2/cat3` 却漏了分类 JOIN —— 一旦在销售分析里带分类筛选就会
   `missing FROM-clause entry`。首轮报表 IT 没设 `categoryId` 因而没发现，是「逐分支渲染全部 mapper
   语句」的门禁抓到的；已把报表 IT 扩为空筛选与全筛选两轮。
2. FastExcel 没有 `OffsetDateTime` 的 Converter：含时间列的导出在写出那一刻抛
   `ExcelWriteDataConvertException`，HTTP 200 已发出、异常只体现在下载字节里。已在共享写出层把时间
   归一化为北京时间字符串、`BigDecimal` 走 `toPlainString()`，11 个导出同时收口。

未覆盖（不得当成已完成）：

- 成本权限的**细粒度**负向（有 `inventory:query` 而无 `cost:query` 的真实角色）只在 IT 层以
  fail-closed 路径覆盖，浏览器层只验到「无角色被整体拒绝」。
- 计划 §42 的客户明细下钻原单、供应商 / 采购员抽屉、每日统计 deep-link 未写成浏览器用例；
  后端接口与前端组件已实现，交互实跑待补。
- 正式岗位数据范围（§33）未做：**功能权限已验证，正式岗位数据范围未宣称完成**。
  上线给财务 / 采购员 / 仓管 / 销售前，仍需处理数据范围与 F0 文件授权债（FA-1 / FA-2）。
- EXPLAIN 跑在数据量极小的一次性库上，只能证明索引「可被选中」，不能据此判断真实数据量下的
  代价取舍；上线前应在全量库复核一次。
- 规划稿里的 R0-B 采购价格波动接口已实现（`/scm/report/purchase/price-trend`），
  销售成交价波动按计划未做。
