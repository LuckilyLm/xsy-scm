# 项目文档

本目录只保存当前项目必须维护的少量记录：

- [progress.md](progress.md)：按日期追加的交付进度、验证状态和待办。
- [decisions.md](decisions.md)：当前仍然有效的项目边界和技术决策。
- [requirements/产品功能需求基线.md](requirements/产品功能需求基线.md)：产品范围清单，作为需求输入保存。
- [requirements/2026-09-09-负责人确认口径.md](requirements/2026-09-09-负责人确认口径.md)：已确认的业务口径。
- [requirements/2026-09-19-需求覆盖与待办清单.md](requirements/2026-09-19-需求覆盖与待办清单.md)：需求基线的执行对照表，含「阶段排除项重新纳入范围」的范围变更与推进顺序。
- [requirements/2026-09-19-高德地图与数据大屏技术调研.md](requirements/2026-09-19-高德地图与数据大屏技术调研.md)：配送模块接高德地图与可视化大屏的依赖/方案调研及待决策清单。**其中的现状描述已过时**，实施口径以 09-21 方案为准。
- [requirements/2026-09-20-规格转换与成本核算设计.md](requirements/2026-09-20-规格转换与成本核算设计.md)：规格转换（V33）与移动加权成本（V34 / V37）的设计与口径。
- [requirements/2026-09-21-地图模块分期实施方案.md](requirements/2026-09-21-地图模块分期实施方案.md)：地图能力拆成 M0 地理数据地基 / M1 大屏真实地图 / M2 底图与选点 / M3 配送排线四期；M0 与 M1 已于 2026-09-21 完成，M2 底图路线 A/B/C 待裁决（含高德配额与商用授权的如实口径）。
- [plan/attachment-asset-grading-and-file-access-plan.md](plan/attachment-asset-grading-and-file-access-plan.md)：附件资产分级与文件读取权限（F0-DEBT-01）的分批方案 —— **FA-0 至 FA-3 已全部落地（V41、V52–V53、V58），该债已于 2026-09-24 关闭**；文件保留作方案与顺序约束（先搬对象再改库 key）的依据。
- [plan/logistics-delivery-static-route-plan.md](plan/logistics-delivery-static-route-plan.md)：物流配送 L0–L2 静态排线的实施方案（V42–V43、V47 已落地）。**L3 的实发量来源与角色授权已在 2026-09-25 裁决完毕**，见 `decisions.md`「P2 物流配送 L3 裁决」；该文件第 1385–1409 行的 L3 设想只是规划期草稿，其中「签收不在 L3 范围」的划分已被本次裁决取代。
- [plan/finance-reporting-r0-plan.md](plan/finance-reporting-r0-plan.md)：Finance R0 只读报表地基（V50–V51 已落地）；R1 应收 / 应付与 R2 利润的前置裁决清单见该文件末节，其中依赖配送事实的条目由 P2 提供。
- [plan/finance-r1-design.md](plan/finance-r1-design.md)：Finance R1（P3）的**唯一规划文档** —— 调研结论、裁决索引、正式设计、D-1…D-5 收口落点、R0 接轨与 F1-1~F1-8 实施计划。裁决权威全文在 `decisions.md`「P3 Finance R1 裁决」；**27 条 Q 裁决与 D-1…D-5 均已收口，本稿无待裁决项**。其中的 Flyway 版本号与菜单号段是规划期快照，开工前必须重扫。
- [plan/product-center-optimization-plan.md](plan/product-center-optimization-plan.md)：商品中心优化的多轮方案（**PCO-1 与 PCO-2 均已落地**：V38–V39、V44–V45、V49）。`plan/` 目录专门存放**面向未来的规划稿**：其中的 Flyway 版本号只是规划期快照，落地实施前必须按 `AGENTS.md` 从当前最大号之后整体重排。
- [test-report/](test-report/)：按版本归档的部署后验收报告。只写矩阵、计数与可复核结论（接口 / 数据 / 日志证据的**类别**），本机运行目录与脚本不作为引用对象。
- [quality/java-code-quality-remediation-plan.md](quality/java-code-quality-remediation-plan.md)：Java 工程规范整改的**正式基线**（Q0 门禁 → Q1 迁包 → Q2 类型/命名/权限 → Q3 注释/架构/Service → Q4 文档与仓库）。业务功能开发在其收口前暂停。
- [quality/java-quality-audit-2026-09-26.md](quality/java-quality-audit-2026-09-26.md)：Q0 全仓审计，含各门禁的检测规则与实测数字、baseline 增长记录、检测局限（§7）、Q1 迁包精确影响面与 Q2–Q4 backlog。

业务需求、旧系统语义、页面参考和历史方案以只读目录
[`project-reference-examples/xsy-scm/`](../project-reference-examples/xsy-scm/) 为主要参考来源。
当前代码行为以 `AGENTS.md`、`SMARTADMIN_REFERENCE_RULES.md` 和实现为准；参考项目不能覆盖当前项目规则。

## 维护规则

- 新进度追加到 `progress.md`，不为每个波次重复创建一组设计、审计、批准和验收文件。
- 需求清单和负责人确认口径保留为输入记录；新的业务裁决追加到 `decisions.md`，不再复制成多套专项文件。
- 新决策追加到 `decisions.md`，保留日期、范围和未决事项；不改写已经发生的进度记录。
- 不在本目录复制参考项目的需求、UI 指导、截图或旧技术方案。
- 迁移校验、生成物和本机运行信息不作为叙事文档维护；需要时从 Git、代码和部署配置重新核对。
  - **禁止写入**：端口号、容器名与镜像 tag、开发库与一次性临时库名、PID、jar / 日志 / 脚本的机器绝对路径，
    以及未入库脚本和被 `.gitignore` 的运行目录产物清单。
  - **改用角色级表述**：「本地开发栈」「日常开发库」「一次性临时库（用后删除）」「本次运行的后端日志」；
    本机拓扑（具体容器、库名、跑 IT 的环境变量口径）留在开发者的本机记录，不进仓库。
  - **允许引用**：仓库内相对路径（如 `e2e/scm-product.spec.ts`）、Flyway 版本号、业务错误码、
    测试类名与通过计数、commit SHA —— 这些可从仓库本身复核。
- 删除或移动文档后同步更新根目录说明、`AGENTS.md`、迁移注释和部署说明中的链接。

当前实现状态见 [progress.md](progress.md)。
