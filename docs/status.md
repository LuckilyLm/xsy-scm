# 项目状态

最后更新：2026-10-05。

当前主线迁移至 V109。SCM V2 管理后台涵盖采购、订单、库存、分拣、配送、财务与营销接入；代码交付、自动化验收、现场验收和生产部署分别记录。详细范围见[开发规划](plan/active/admin-development-roadmap.md)，裁决和未闭环项见[ADM 验收决策登记](plan/active/adm-acceptance-decision-register.md)。

## 已交付与当前边界

- 本轮审查确认并收口了生产配置、空白菜单、迁移文档、数据库外键口径、质量门禁与 E2E 静态检查等问题。生产配置检查通过；V109 追加迁移将 schema 收敛到当前“SCM 不使用外键”规则，未修改已应用迁移。
- ADM-01～12 的实现范围和仍未覆盖的业务边界按开发规划逐项维护。支付与余额 c1～c3 已实现并纳入当前自动化验收；充值回调后钱包入账仍有 D-39/J-18 缺陷，混合及多交易退款分摊 d 尚未实现。
- 采购单提供 XLSX 导出；模板预览可打开浏览器打印窗口，用户可选择实体打印机或“另存为 PDF”。当前没有独立的 PDF 文件下载接口。
- 地图、电子秤和真实支付渠道的现场验收仍未完成。Docker Desktop 中的 mock、MinIO 容器或自动化测试不能替代真实地图授权/配额、实体秤协议与校准、支付渠道沙箱或生产回调。
- GitHub Actions 工作流已提交到本地 `main`；尚未推送，因此没有 GitHub 托管运行记录。当前本地验收不代表生产部署或生产数据库迁移。

## 当前验收证据

- **后端：** 2026-10-05 在 Docker Desktop 独立 PostgreSQL 数据库 `xsy_scm_backend_final_20261005` 从空库迁移至 V109 后，**1,384 tests / 0 failures / 0 errors / 0 skipped**；MinIO 云存储集成 **5/5**。迁移 checksum 覆盖 V1～V109，drift、missing、renamed、unbaked 均为 0。原 `xsy_scm` V76 未修改。
- **前端与浏览器：** 组件拆分后的 TS 棘轮、ESLint、E2E 类型检查、Web 单测与生产构建均通过；其后在 Docker Desktop 全新库 `xsy_scm_e2e_postfix_20261005` 运行 `python tools/verify.py e2e`，**158 passed / 0 skipped / 0 unexpected / 0 flaky**，包含 MinIO F0 的 8 项与数据范围夹具 6 项。隔离库由 Flyway 从空库迁移至 V109，原 `xsy_scm` 未修改。
- **质量：** `python tools/verify.py quality` 通过。前端 E2E 已纳入 ESLint 和 TypeScript 检查。TypeScript 历史基线为 **1,858**；SCM 错误和新增错误均为 0。全量直接 `vue-tsc` 仍会报告这批历史错误。ESLint 保留 3 条既有 warning；生产构建有既有图标路径与 bundle 体积提示。
- **支付/财务黄金链：** Docker Desktop 全新库 `xsy_scm_chain_current_20261005`：**36 passed / 1 skipped / 0 failed**。唯一 skip 为 D-39/J-18 充值回调钱包入账步骤，等待业务裁决。

## 待完成

- **J-18：** 冻结充值到账时点并修复回调/钱包入账链；当前建议“渠道确认后才到账”，仍需业务方明确选择。
- **ADM-12 d：** 先确定混合支付、多笔交易退款的来源分摊规则，再实施；不得默认余额优先、在线优先、按比例或按时间顺序。
- **现场验收：** 真实地图服务、电子秤设备与协议、支付渠道沙箱/生产回调。
- **其余路线图：** ADM-13～17 等未启动能力及 ADM-01～12 尚未闭环的范围，按[开发规划](plan/active/admin-development-roadmap.md)推进。
- **大文件维护：** 已从分拣列表和配送详情页抽出秤读数、打印预览、辅助排线三个独立业务面板。库存 `InventoryCommandService` 保持为唯一余额记账入口，以集中维护事务、余额锁与流水不变量；类注释已核对并记录十条写入路径。其余大页面仍按职责评估，不以行数机械拆分。

## 数据与部署边界

后端验收使用本地 Docker Desktop PostgreSQL、Redis 与 MinIO 隔离环境。自动化迁移只作用于新建验收库；原有本地开发库与生产库分别管理。生产部署、生产迁移、真实外部服务验收及 GitHub 托管 CI 运行结果须单独记录，不由本地测试推定。
