# 项目状态

最后更新：2026-10-08。

已提交代码基线：`main @ 4331731`（首页工作台收尾与列表交互竞态保护）；FIX-01～FIX-09 修复仍在工作区，尚未提交。当前仓库 Flyway 源码最大版本为 **V115**；最近一次完整后端与主 E2E 基线仍是 2026-10-05 的 **V109** 验收，不能把“仓库已有 V115”写成“V115 已完成全量数据库验收或生产迁移”。本轮修复的 PG IT 已在干净空库上跑通 V1→V115 迁移，但那只是本批用例所需的迁移路径，不构成新的全量验收。

SCM V2 管理后台已经覆盖商品、客户、供应商、定价、销售订单、采购、库存、分拣、配送、财务、报表、营销、支付与客户余额主干。当前规则见[决策索引](decisions.md)，剩余工作见[后续开发路线图](plan/active/admin-development-roadmap.md)。

## 当前代码事实

- 首页已改为供应链工作台：权限裁剪的指标、经营趋势、业务待办、销售排行与库存健康均有实现。本次工作区补齐待办错误隔离、趋势切换数据匹配、SCM 快捷入口、通知公告合并卡、统一卡片样式与本地待办清理；代码收尾不等于运行验收。实现与待验收边界见[首页工作台设计](plan/active/home-workbench-design.md)。
- 工作台指标单位改为独立的 `ScmDashboardValueType`，VO 使用枚举，接口仍返回 `CNY / COUNT`；不依赖商品计量单位枚举。仓库校验类超长注释已缩短。本次未修改任何已发布 migration。
- 工作区已补齐四项只读字段并接入页面：收货确认人、四类财务单据表头审计信息、退货原订单号与客户快照、库存预留规格值；财务红字列表改用实际单据主键。退货查询按原订单主键联接，保留归属过滤并对空范围失败关闭；未新增数据库迁移。这些修改尚未运行测试、构建或浏览器验证。
- 前端交互收尾已写入工作区：四类财务详情统一防止旧响应覆盖，加载失败在抽屉内重试；退货处理与应付红字表单隔离加载/提交状态，关闭或离开页面后使旧请求失效。首页更新日志接入独立错误与刷新，权限裁剪后调整剩余区块宽度，初次加载不以库存零值冒充结果；财务详情窄屏描述区改为单列。仅源码核对，尚未运行验证。
- V110 仅调整一级菜单 `sort`，不改菜单层级、路由、权限或可见性；对应提交 `022a5c0d` 记录了 quality 与 frontend 验证通过。
- 2026-10-06～07 的前端收口已把 SCM 数字样式、操作列层级、Drawer 宽度、客户列表层级和客户 360 详情统一到共享 UI Foundation；长期规则见[SCM UI 规范](architecture/scm-ui-guidelines.md)。
- 支付与余额 c1～c3 已实现：余额支付、订单资金自动核销、纯余额售后返还均已有代码与自动化基线。充值回调后的钱包入账时点仍需收口；混合支付及多交易退款的来源分摊仍未实现。
- 配送线路详情已页面化（`/delivery/routes/:id?tab=`，隐藏菜单 V112）；客户 / 供应商 / 仓库编码改为后端生成（V111 三条序列 + `ScmBusinessNoService`），前端表单不再要求手工编码。规则见[决策索引](decisions.md)。
- 地图道路路线展示（高德驾车规划，失败降级点间连线）、电子秤事件链和 mock 支付均有应用侧实现，但真实地图授权/配额、实体秤协议与校准、真实支付渠道仍属于现场或目标环境验收。
- GitHub Actions 工作流已在 `main`，但没有把本地验证结果自动等同于 GitHub 托管运行、生产部署或生产数据库迁移。

## 本次交付的验证边界

### 2026-10-08 代码审查修复批次 A～C + 运行期新发现（FIX-01～FIX-09，工作区，未提交）

本轮只修已确认缺陷，不新增业务功能、不改架构、不接真实支付。所有改动在 `main @ 4331731` 工作区。

- **FIX-01 财务结清状态派生**：应收 / 应付的 `settleState` 分支顺序修正——原实现先判 `writtenOffAmount = 0 → OPEN`，使「全额红冲（净额为零）且无核销」的单据被误判为未结清、永远挂在未结清列表。现先判「无未结净额且无超额核销 → SETTLED」，超额核销仍取 PARTIAL。改动 `FinanceReceivableDao.xml`、`FinancePayableDao.xml` 各两处（汇总列 + 列表筛选），并在 `plan/active/finance-r1-design.md` §7 同步派生公式与「净额为零」语义。无落库状态列，**未新增 migration**。
- **FIX-02 模拟渠道账本事务独立性**：`MockPaymentProvider.recordLedger` 的 `REQUIRES_NEW` 因同类自调用绕过代理而失效，渠道账并入本地事务一起回滚。现将记账拆至独立 Bean `MockPaymentLedgerRecorder`，`createIntent` / `refund` 跨 Bean 调用，使独立事务真正生效；未改支付状态机与钱包记账规则。
- **FIX-03 富文本 XSS**：公告 `NoticeService` 与帮助文档 `HelpDocService` 的**新增与更新**两条写入路径统一在落库前做白名单清洗；渲染侧有多处 `v-html`，正文被当作不可信 HTML。清洗器 `SmartHtmlSanitizeUtil` 放在 sa-base（jsoup 1.21.1 已是其依赖，**未新增依赖**），去脚本 / 事件属性 / 危险协议，保留排版标签与安全外链。**踩坑记录**：`Jsoup.clean` 的 baseUri 传空串时相对链接（`/admin/notice`、`#anchor`）会被判为非法协议整段删除——非恶意内容被误伤，必须传非空 baseUri。
- **FIX-04 前端用户状态清理**：`store/modules/system/user.ts` 的 `administratorFlag` 初值由 `true` 改为 `false`（它是权限总开关，被 6 处权限判定读取）；`logout()` 补齐重置全部身份 / 权限 / 界面状态字段与 6 个 localStorage 键（此前漏了 `employeeId`、`avatar`、`menuTree`、`menuRouterList`、`menuParentIdListMap`、`tagNav` 等）。
- **FIX-05 首页权限响应性**：`views/system/home/index.vue` 的 7 个权限判定位由普通 `const` 改为 `computed`——原写法在登录信息异步到达后不会重算，区块显隐停留在首帧。**注意与 FIX-04 的联动**：`administratorFlag` 默认改 `false` 后正是它暴露了这个既有缺陷，两者必须同批修，否则首页会退化。
- **FIX-06 应付红字金额**：`finance-payable-list.vue` 红字金额由手填改为按「数量 × 单价」用 `decimal.js` 以 4 位定标计算（`finance-form-model.ts` 新增 `lineAmount`），提交前按计算结果过滤空行，避免手填与单据口径不一致。
- **FIX-07 首页聚合按可见卡片裁剪**：`ScmDashboardService.overviewFor` 原实现对**全部**指标无条件取数，即使卡片被权限裁剪仍查库。现按卡片分组（新增 `ScmDashboardCardGroup` 枚举）只取可见组：不可见组不调用聚合甚至不解析数据范围（`verifyNoInteractions` 钉住）。`cardsFor` 入参由权限列表改为已裁剪卡片列表（无外部调用方）。
- **FIX-09 回调事件状态机不落地（运行期新发现）**：`PaymentCallbackEventDao.insertIgnoreDuplicate` 缺 `useGeneratedKeys`，`event.getId()` 恒为 null，两处 `markProcessed(event.getId(), ...)`（含伪造签名 `reject()` 分支）都执行 `WHERE id = NULL` → 更新 0 行，`process_status` 永远停在 `RECEIVED`。修复后「伪造回调记 REJECTED 留证」的设计意图才成立。经用户确认纳入本轮。

**已验证（本轮实跑，非推断）：**
- **FIX-01 / FIX-02 PG IT：** 干净空库 `xsy_fix08b` 从零跑通 Flyway 全链（116 条迁移落地至 V115）后，`ScmFinanceSettleStatePgIT` 7 例 + `MockPaymentLedgerRollbackPgIT` 2 例，`Tests run: 9, Failures: 0, Errors: 0` BUILD SUCCESS。
- **FIX-03 单测：** `SmartHtmlSanitizeUtilTest` `Tests run: 15, Failures: 0, Errors: 0` BUILD SUCCESS（已含「安全外链必须存活」的正面断言）。
- **FIX-07 单测：** `ScmDashboardServiceTest` `Tests run: 9, Failures: 0, Errors: 0` BUILD SUCCESS（含 5 个「不可见组不被查询」的 Mockito 断言）。
- **FIX-09 PG IT：** `PaymentCallbackEventPersistPgIT` 4 例（干净库 `xsy_scm_fix09b`）`Tests run: 4, Failures: 0, Errors: 0` BUILD SUCCESS，日志可见修好后的 `UPDATE ... WHERE id = 5`（修复前为 `WHERE id = NULL`）。
- **FIX-01 运行期补充证据：** 在开发库 `xsy_scm_b0` 造 `FIX01-*` 探针并经 `POST /scm/finance/receivable/query` 回读，5 种情形（全额红冲 / 部分红冲 / 部分核销 / 足额核销 / 超额核销）的派生状态与筛选命中均符合预期。
- **FIX-02 运行期补充证据：** 经 HTTP 造支付意图，`payment_mock_ledger` 留下 `created_by=MOCK_PROVIDER` 的独立事务行；同键不同内容重放被 40966 拦截且账本未被污染；回调验签通过后意图 PENDING → SUCCEEDED。
- **前端：** 4 个改动文件 eslint 通过；`vue-tsc --noEmit` 对 `user.ts` 的错误数由基线 41 降至 **40**（`logout` 重写顺带修掉一个既有错误），**未引入新错误**。

**未验证（不得推定）：**
- 本次未推送远程（用户明确不推送），远程 CI、质量门禁 `verify.py quality` 未跑。
- 未跑主 E2E 与支付/财务黄金链联合验收，也未做 V1→V115 的完整生产迁移演练（仅跑通了本批 IT 涉及的空库迁移）。
- 浏览器端人工验收未做（后端在 18080、前端容器 18081 已起，但本轮前端改动只到 lint / 类型比对层面）。
- 因此本轮结论是「缺陷已修 + 定向回归已过」，**不等于**「整体验收完成」。

- `b3df08d0` 已有的[远程运行记录](https://github.com/LuckilyLm/xsy-scm/actions/runs/37655734390)：Backend / Frontend 成功，Quality 失败。这是已提交基线的记录，不是本次工作区修复的结果；本次不推送、不处理远程 CI。
- 2026-10-08 收尾时已补做前端验证：契约测试 419/419 通过、`ts_baseline_ratchet check` PASS（SCM 路径 0 错误，基线随 40 处修复收缩重捕获）、`verify.py frontend` 的 lint / typecheck:e2e / test 通过、生产构建成功（首次构建失败仅因本地安全删除守卫拦截清空 `dist-verify`，移走旧产物后构建通过，与源码无关）。修复了两处收尾问题：注释契约基线随 B6 缺口补齐从 4 个 § 链接调整为 2 个；`logout` 误写不存在的 `menuList` 改为清空 `menuTree / menuRouterList` 并重置初始化标记。
- 本轮新增的 FIX-03 清洗器、FIX-07 分组裁剪与 FIX-09 主键回填均已带定向测试；但仍以「最近一次完整验收 = V109」为准，这些修复未纳入一次新的全量验收。

## 最近一次完整验收证据

以下数字来自 2026-10-05 的隔离环境，不向后推定到未重跑的提交：

- **后端：** Docker Desktop 独立 PostgreSQL 从空库迁移至 V109，1,384 tests / 0 failures / 0 errors / 0 skipped；MinIO 云存储集成 5/5。
- **迁移：** 当次 checksum 覆盖 V1～V109，drift / missing / renamed / unbaked 均为 0。
- **主 E2E：** 全新隔离库 159 passed / 0 skipped / 0 unexpected / 0 flaky；支付/财务黄金链 36 passed / 1 skipped / 0 failed，唯一 skip 对应充值回调钱包入账。
- **质量：** 当次 `python tools/verify.py quality` 通过。

后续前端提交有各自的局部门禁记录：`6a51908b` 记录 `verify.py frontend` PASS（含 370 个 Web 单测与生产构建）。这些局部结果与上述远程 CI 均不覆盖 V110～V115 的完整空库迁移、主 E2E 与支付/财务黄金链联合验收，所以仍以“最近一次完整验收 = V109”表述。

## 待完成

- **首页与数据库验收：** 工作区确实有多批改动尚未纳入整体验收。本轮（FIX-01～FIX-09）已完成定向回归：FIX-01/02 的 9 例 PG IT、FIX-03 的 15 例、FIX-07 的 9 例、FIX-09 的 4 例均在隔离库通过。**仍未做**：`verify.py quality`、主 E2E、支付/财务黄金链联合验收、隔离空库 V1→V115 迁移的全量演练、浏览器人工首页复核。未完成这些之前不标记整体验收完成。
- **充值到账链：** 冻结“渠道确认才到账”还是其他时点，并收口充值 callback → wallet credit；详见[余额支付设计](plan/active/balance-payment-order-settlement-design.md)。
- **退款来源分摊：** 为混合支付、多笔成功交易定义可验证的退款来源分配契约；不得默认余额优先、在线优先、按比例或按时间顺序。
- **现场验收：** 真实地图服务、电子秤设备/协议、支付渠道沙箱或生产回调。
- **后续产品：** ADM-13～17（推广、订单助手、溯源、客户商城、员工移动端）按路线图推进。
- **后端字段缺口：** B1（收货/入库全量汇总）、B4（订单履约聚合）、B5（订单售后聚合）、B7（优惠券统计）继续按[字段缺口](plan/active/frontend-ui-backend-gap-inventory.md)处理；已补字段从开发缺口中移除，运行验收仍未执行。

## 数据与部署边界

自动化验收使用隔离的 PostgreSQL、Redis 与 MinIO 环境。生产数据库、生产对象存储、外部服务密钥与真实设备独立管理。未执行生产迁移、真实渠道调用或现场设备验证时，文档必须明确写“未覆盖”，不能从本地测试结果推定。
