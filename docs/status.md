# 项目状态

最后更新：2026-10-10。本次同步代码复审修复源码；下方历史验证数字仍按原记录日期阅读。

2026-10-08 的审查记录以 `main @ 058ff5a6`（FIX-01～FIX-09）为起点；此后仍有本地提交，尚未完成新一轮整体验收。当前仓库 Flyway 源码最大版本为 **V117**；V116/V117 已在新建隔离库验证空库全链与 V115→V117 升级，尚未在生产库演练。最近一次完整后端与主 E2E 基线仍是 2026-10-05 的 **V109** 验收；本轮定向验证不代替整体验收。

SCM V2 管理后台已经覆盖商品、客户、供应商、定价、销售订单、采购、库存、分拣、配送、财务、报表、营销、支付与客户余额主干。当前规则见[决策索引](decisions.md)，剩余工作见[后续开发路线图](plan/active/admin-development-roadmap.md)。

## 当前代码事实

- 2026-10-10 演示数据与全站页面遍历（本地提交，未推送）：按真实业务链路伪造覆盖全模块的演示数据（商品/客户/定价/订单/售后/采购/库存/分拣/配送/财务/营销/支付与余额/公告帮助等），并完成 82 个页面遍历与采购日志、客户对账单、帮助文档、线路详情等交互取证。同批修复：模板闭合标签被拆行（18 个文件，恢复后可重新通过配送页契约用例）、SCM 区 8 处类型错误（并收缩 TS 基线）、传统侧栏子菜单箭头口径、注释 markdown 粗体残迹 3 处。已运行：前端契约 442/442、`ts_baseline_ratchet` PASS；**后端全量回归、主 E2E 套件未重跑**。演示数据与工具为本机环境产物（脚本未入库）。
- 2026-10-10 复审修复已分阶段本地提交：`pre/prod` 不注册模拟支付渠道；客户写入、钱包人工更正、余额充值及采购收货重放补齐对应当前范围；支付退款列表和客户下拉按授权范围收窄；充值成功按创建时钱包快照入账；拒绝回调不再占后续合法事件 ID；客户列表关联数据改为每页取一次。
- **定向后端验证**：新建隔离库 `xsy_review_20261010_01` 空库迁移至 V117 后，余额、客户与回调相关 45/45 通过；新增真实 PG 客户/余额范围用例 2/2、退款列表交集范围用例 1/1、采购收货旧键重放范围用例 1/1 通过。另一隔离库 `xsy_review_upgrade_20261010_01` 先迁至 V115（客户 IT 19/19），再升级至 V117（回调 IT 5/5）；Flyway 记录中 V116/V117 均成功，菜单 435/464 分别为“客户商品管理”“商品供应商”。初次 Maven 执行因 Windows CRLF 被 Spotless 拦截，后续定向测试仅跳过该格式检查；完整质量门禁未运行。
- **前端契约**：本地复现 442 项中 439 通过、3 失败，断言分别为配送页“缺发车 / 完成 / 签收按钮”、注释扫描发现 3 处 markdown 粗体残迹、传统侧栏的子菜单箭头隐藏范围不符。相关前端和采购测试文件从 `41c46e3` 到本轮提交没有改动，均属本轮修复前已有的失败；尚未修复。主 E2E、支付与财务联合验收、浏览器人工验收和 CI 均未执行。

- 2026-10-09 前端界面调整已提交：登录与首页素材、工作台图标、侧边栏子菜单箭头及 SCM 表单说明和间距已更新；这些改动仅做静态核对，未运行构建、测试或浏览器验收。
- 新建 / 编辑抽屉已按字段类型收紧控件宽度和抽屉档位，并补齐与服务端一致的输入限制；客户等只读详情页已清理重复的编辑、刷新与通用日志按钮，失败重试和状态流转入口保留。本批改动仅做静态核对，尚未运行构建、测试或浏览器验收。
- 首页已改为供应链工作台：权限裁剪的指标、经营趋势、业务待办、销售排行与库存健康均有实现。近期源码补齐待办错误隔离、趋势切换数据匹配、SCM 快捷入口、通知公告合并卡、统一卡片样式与本地待办清理；代码收尾不等于运行验收。实现与待验收边界见[首页工作台设计](plan/active/home-workbench-design.md)。
- 工作台指标单位改为独立的 `ScmDashboardValueType`，VO 使用枚举，接口仍返回 `CNY / COUNT`；不依赖商品计量单位枚举。仓库校验类超长注释已缩短。本次未修改任何已发布 migration。
- 代码已补齐四项只读字段并接入页面：收货确认人、四类财务单据表头审计信息、退货原订单号与客户快照、库存预留规格值；财务红字列表改用实际单据主键。退货查询按原订单主键联接，保留归属过滤并对空范围失败关闭；未新增数据库迁移。这批修改尚未运行测试、构建或浏览器验证。
- 前端交互收尾已有源码：四类财务详情统一防止旧响应覆盖，加载失败在抽屉内重试；退货处理与应付红字表单隔离加载/提交状态，关闭或离开页面后使旧请求失效。首页更新日志接入独立错误与刷新，权限裁剪后调整剩余区块宽度，初次加载不以库存零值冒充结果；财务详情窄屏描述区改为单列。仅源码核对，尚未运行验证。
- V110 仅调整一级菜单 `sort`，不改菜单层级、路由、权限或可见性；对应提交 `022a5c0d` 记录了 quality 与 frontend 验证通过。
- 2026-10-06～07 的前端收口已把 SCM 数字样式、操作列层级、Drawer 宽度、客户列表层级和客户 360 详情统一到共享 UI Foundation；长期规则见[SCM UI 规范](architecture/scm-ui-guidelines.md)。
- 支付与余额 c1～c3 已实现：余额支付、订单资金自动核销、纯余额售后返还均已有代码与自动化基线。充值回调后的钱包入账时点仍需收口；混合支付及多交易退款的来源分摊仍未实现。
- 配送线路详情已页面化（`/delivery/routes/:id?tab=`，隐藏菜单 V112）；客户 / 供应商 / 仓库编码改为后端生成（V111 三条序列 + `ScmBusinessNoService`），前端表单不再要求手工编码。规则见[决策索引](decisions.md)。
- 地图道路路线展示（高德驾车规划，失败降级点间连线）、电子秤事件链和 mock 支付均有应用侧实现，但真实地图授权/配额、实体秤协议与校准、真实支付渠道仍属于现场或目标环境验收。
- GitHub Actions CI 工作流已删除；本地验证入口仍保留在 `tools/verify.py`、`tools/verify.ps1` 和 `tools/verify.sh`，运行结果仍需逐次记录，不能推定生产部署或数据库迁移。

## 本次交付的验证边界

### 2026-10-08 后续审查修复（本地提交，未验证）

- **金额与类型**：`lineAmount` 显式排除空值后构造 Decimal，使用足够精度保留乘积并四舍五入到四位；舍入后金额必须大于零且符合金额字段范围，否则返回空串，由原有提交过滤排除。未修改 TypeScript 错误基线。
- **日志与历史 HTML**：DataTracer 字段名称、新旧值在拼接时转义；读取时产生纯文本 `contentText`，表格与时间轴使用 Vue 文本插值并保留换行，兼容 `content` 也只含转义文本与换行。公告管理/员工查看共用出口、帮助文档编辑与用户查看两个独立出口均清洗正文。历史数据库原文保留，不新增或改写 migration。
- **排行与查询成本**：客户/商品金额分别按业务 ID 聚合，再左联当前主数据名称；主数据缺失或已删除时显示对象编号（用户已确认）。排行只查询所选维度，销售卡片使用当日区间聚合；趋势按指标族裁剪，库存期末量改为期初净额加每日变动的窗口累计。范围谓词继续落在事实查询内部，空范围失败关闭。规则同步至[工作台设计](plan/active/home-workbench-design.md)。
- **模拟账本日期**：业务日期和发生时间来自同一个 `Asia/Shanghai` 时间值，与日对账时区一致。
- **历史 CI 记录**：此前读取的 [058ff5a6 运行日志](https://github.com/LuckilyLm/xsy-scm/actions/runs/37737582895)包含两个 Decimal 空值诊断及 418/419 的测试摘要。旧日志没有失败断言，也没有完整测试日志制品，因此**那一条失败用例尚未定位，不能声称已修复**。本地验证入口会在前端测试失败时输出完整日志；该远程记录不代表当前代码已验证。
- **验证边界**：开始时误启动一次前端测试，用户叫停后已终止，未形成完整结果；之后未运行测试、构建、类型检查、质量门禁、数据库迁移、浏览器验证或 EXPLAIN ANALYZE。现有单测桩仅随销售卡片调用接口同步，未执行。查询耗时、改名数值回归、时区边界、浏览器安全性及依赖兼容性均未验证。后续任何测试须先询问用户并获得同意。

**依赖审计处置（源码与锁文件层面）：**

修复前 `npm audit` 返回 31 个包级告警（2 Critical / 15 High / 12 Moderate / 2 Low）。按锁文件节点分类，14 个进入生产依赖树、17 个仅在开发依赖树；生产依赖树不等于全部代码都会打进浏览器，也不等于漏洞均可利用。

| 类别 | 已核对事实与处理 | 当前边界 |
| --- | --- | --- |
| 两项 Critical | `crypto-js` 4.1.1 → 4.2.0；`sm-crypto` 0.3.13 → 0.5.7，已更新 manifest 与 lockfile。现有调用为 AES/SM4 与编码转换，未找到 PBKDF2、SM2 签名或密钥生成调用 | 仅用 `--package-lock-only --ignore-scripts --no-audit` 更新依赖声明；本地 node_modules 未更新，未进行升级后复审或兼容性验证。依据：[CryptoJS 公告](https://github.com/brix/crypto-js/security/advisories/GHSA-xwcq-pm8m-c4vf)、[SM4 接口](https://github.com/JuneAndGreen/sm-crypto#sm4) |
| 其他生产依赖告警 | axios、lodash、Vue/服务端渲染器、vue-i18n/Intlify、ECharts、diff/diff2html、source-map-js | 尚未升级；需区分浏览器入口、仅 Node/SSR 路径和传递依赖。当前没有逐项利用验证，不能写成审计通过或风险清零 |
| 开发依赖告警 | Vite/esbuild、ESLint/Stylelint 及 glob/braces/selector-parser 等传递依赖 | 仍待处理；不因开发依赖就忽略。涉及工具链主版本迁移的升级另行安排，测试前需取得用户同意 |


### 上一轮记录：2026-10-08 代码审查修复批次 A～C + 运行期新发现（FIX-01～FIX-09，已提交）

以下是上一轮交付及当时验证的记录，已提交为 `058ff5a6`；不覆盖上节后续审查修复。

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
- 该批次未推送远程，质量门禁 `verify.py quality` 未跑。
- 未跑主 E2E 与支付/财务黄金链联合验收，也未做 V1→V115 的完整生产迁移演练（仅跑通了本批 IT 涉及的空库迁移）。
- 浏览器端人工验收未做（后端在 18080、前端容器 18081 已起，但本轮前端改动只到 lint / 类型比对层面）。
- 因此本轮结论是「缺陷已修 + 定向回归已过」，**不等于**「整体验收完成」。

- `b3df08d0` 已有的[历史远程运行记录](https://github.com/LuckilyLm/xsy-scm/actions/runs/37655734390)：Backend / Frontend 成功，Quality 失败。这只记录当时提交的基线，不覆盖后续修改。
- 2026-10-08 收尾时已补做前端验证：契约测试 419/419 通过、`ts_baseline_ratchet check` PASS（SCM 路径 0 错误，基线随 40 处修复收缩重捕获）、`verify.py frontend` 的 lint / typecheck:e2e / test 通过、生产构建成功（首次构建失败仅因本地安全删除守卫拦截清空 `dist-verify`，移走旧产物后构建通过，与源码无关）。修复了两处收尾问题：注释契约基线随 B6 缺口补齐从 4 个 § 链接调整为 2 个；`logout` 误写不存在的 `menuList` 改为清空 `menuTree / menuRouterList` 并重置初始化标记。
- 本轮新增的 FIX-03 清洗器、FIX-07 分组裁剪与 FIX-09 主键回填均已带定向测试；但仍以「最近一次完整验收 = V109」为准，这些修复未纳入一次新的全量验收。

## 最近一次完整验收证据

以下数字来自 2026-10-05 的隔离环境，不向后推定到未重跑的提交：

- **后端：** Docker Desktop 独立 PostgreSQL 从空库迁移至 V109，1,384 tests / 0 failures / 0 errors / 0 skipped；MinIO 云存储集成 5/5。
- **迁移：** 当次 checksum 覆盖 V1～V109，drift / missing / renamed / unbaked 均为 0。
- **主 E2E：** 全新隔离库 159 passed / 0 skipped / 0 unexpected / 0 flaky；支付/财务黄金链 36 passed / 1 skipped / 0 failed，唯一 skip 对应充值回调钱包入账。
- **质量：** 当次 `python tools/verify.py quality` 通过。

后续前端提交有各自的局部门禁记录：`6a51908b` 记录 `verify.py frontend` PASS（含 370 个 Web 单测与生产构建）。这些局部结果与上述历史远程记录均不覆盖 V110～V115 的完整空库迁移、主 E2E 与支付/财务黄金链联合验收，所以仍以“最近一次完整验收 = V109”表述。

## 待完成

- **首页与数据库验收：** 工作区确实有多批改动尚未纳入整体验收。本轮（FIX-01～FIX-09）已完成定向回归：FIX-01/02 的 9 例 PG IT、FIX-03 的 15 例、FIX-07 的 9 例、FIX-09 的 4 例均在隔离库通过。**仍未做**：`verify.py quality`、主 E2E、支付/财务黄金链联合验收、隔离空库 V1→V115 迁移的全量演练、浏览器人工首页复核。未完成这些之前不标记整体验收完成。
- **充值到账链：** 冻结“渠道确认才到账”还是其他时点，并收口充值 callback → wallet credit；详见[余额支付设计](plan/active/balance-payment-order-settlement-design.md)。
- **退款来源分摊：** 为混合支付、多笔成功交易定义可验证的退款来源分配契约；不得默认余额优先、在线优先、按比例或按时间顺序。
- **现场验收：** 真实地图服务、电子秤设备/协议、支付渠道沙箱或生产回调。
- **后续产品：** ADM-13～17（推广、订单助手、溯源、客户商城、员工移动端）按路线图推进。
- **后端字段缺口：** B1（收货/入库全量汇总）、B4（订单履约聚合）、B5（订单售后聚合）、B7（优惠券统计）继续按[字段缺口](plan/active/frontend-ui-backend-gap-inventory.md)处理；已补字段从开发缺口中移除，运行验收仍未执行。

## 数据与部署边界

自动化验收使用隔离的 PostgreSQL、Redis 与 MinIO 环境。生产数据库、生产对象存储、外部服务密钥与真实设备独立管理。未执行生产迁移、真实渠道调用或现场设备验证时，文档必须明确写“未覆盖”，不能从本地测试结果推定。
