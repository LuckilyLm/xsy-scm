# 项目文档入口

更新：2026-11-03，Codex。先看当前状态，再按问题查规则或操作说明。

## 阅读导航

| 目的 | 文档 |
| --- | --- |
| 了解工程、启动开发服务 | [根目录 README](../README.md) |
| 看已有能力、未完成工作与验证边界 | [项目状态](status.md) |
| 查当前业务、架构与权限规则 | [有效决策](decisions.md) |
| 查产品范围与原始确认 | [需求基线](requirements/产品功能需求基线.md)、[负责人原始答复](requirements/2026-09-09-负责人确认规则.md)、[客户现场记录](requirements/2026-10-28-客户现场确认.md) |
| 查后台视觉与交互规范 | [DESIGN.md](../DESIGN.md)、[产品上下文](../PRODUCT.md) |
| 查工程协作、代码评审与授权验证 | [AGENTS.md](../AGENTS.md)、[CONTRIBUTING.md](../CONTRIBUTING.md)、[工具入口](../tools/README.md) |
| 部署、升级或配置对象存储 | [部署说明](../deploy/README.md) |
| 配置地图 | [地图部署说明](map-deployment.md) |
| 理解单据号、批次和包装码 | [业务身份与追溯架构](architecture/business-identity-and-traceability.md) |
| 操作商城主题、标品分拣 | [操作说明](user-guide/mall-theme-and-standard-sorting.md) |
| 接入客户小程序或 Windows 秤端 | [小程序](../xsy-scm-miniprogram/README.md)、[秤端](../xsy-scale-agent/README.md)、[秤端交接清单](../xsy-scale-agent/FIRST-GENERATION-HANDOFF.md) |
| 使用工程技能的本地工单与领域文档 | [工单约定](agents/issue-tracker.md)、[领域文档约定](agents/domain.md) |

## 维护规则

- 当前规则只写 `decisions.md`，当前交付状态与持续待办只写 `status.md`；不另建 ADR、“最新版”或平行计划。
- 根 README 负责工程入口；部署细节放 `deploy/README.md`，模块细节放各模块 README。后台视觉规则只维护根 `DESIGN.md`；秤端有独立触屏设计规范。
- `requirements/` 保留原始需求和有日期的确认记录。后来裁定写入决策，保留原答复的日期与语境，不把历史未决问题直接当成当前缺口。
- 已完成计划、一次性审计和过程日志退出活动文档；仍有效的结论并入决策或状态，历史由 Git 追溯。
- 秤端本地 API 与 SQLite 增量说明、质量工具直接依赖的说明、第三方许可和上游组件文档按原路径保留，不能按日期批量清理。
- 测试结果保留在对应测试/验证记录中；项目文档不记录个人路径、本机运行状态、临时产物位置或逐次测试过程。源码能力与部署/验收状态分开表述。
