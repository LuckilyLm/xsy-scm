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
- [plan/product-center-optimization-plan.md](plan/product-center-optimization-plan.md)：商品中心优化的多轮方案（PCO-1 已落地、PCO-2 待开工）。`plan/` 目录专门存放**面向未来的规划稿**：其中的 Flyway 版本号只是规划期快照，落地实施前必须按 `AGENTS.md` 从当前最大号之后整体重排。

业务需求、旧系统语义、页面参考和历史方案以只读目录
[`project-reference-examples/xsy-scm/`](../project-reference-examples/xsy-scm/) 为主要参考来源。
当前代码行为以 `AGENTS.md`、`SMARTADMIN_REFERENCE_RULES.md` 和实现为准；参考项目不能覆盖当前项目规则。

## 维护规则

- 新进度追加到 `progress.md`，不为每个波次重复创建一组设计、审计、批准和验收文件。
- 需求清单和负责人确认口径保留为输入记录；新的业务裁决追加到 `decisions.md`，不再复制成多套专项文件。
- 新决策追加到 `decisions.md`，保留日期、范围和未决事项；不改写已经发生的进度记录。
- 不在本目录复制参考项目的需求、UI 指导、截图或旧技术方案。
- 迁移校验、生成物和本机运行信息不作为叙事文档维护；需要时从 Git、代码和部署配置重新核对。
- 删除或移动文档后同步更新根目录说明、`AGENTS.md`、迁移注释和部署说明中的链接。

当前实现状态见 [progress.md](progress.md)。
