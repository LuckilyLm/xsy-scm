# 项目文档

仓库只保留一份当前规则、一份状态和一份持续待办：

- [有效决策](decisions.md)：当前业务、架构与权限不变量，以及尚未裁决事项。
- [项目状态](status.md)：代码现状、验证证据、风险与待完成工作。
- [下一阶段开发计划](next-development-plan.md)：按原始 13 模块、44 条功能描述收敛为 MVP 主链验收、基础缺口补齐和轻量回归；蔬东坡只参考常规业务操作，不自动扩展需求。
- [需求基线](requirements/产品功能需求基线.md)及[负责人确认口径](requirements/2026-09-09-负责人确认口径.md)：产品需求和原始业务确认。
- [业务身份与追溯架构](architecture/business-identity-and-traceability.md)：L1 批次、L2 包装码的身份边界、已实施内容与后续目标模型；实施状态以状态文档为准。
- [参考项目目录](../project-reference-examples/xsy-scm/)：只读的旧系统及上游参考资料。
- [贡献与评审约定](../CONTRIBUTING.md)及[代理协作规则](../AGENTS.md)：工程实践、用户界面与 Git 约束。
- [工程技能配置](agents/)：`mattpocock/skills` 使用的 issue tracker 与领域文档约定；`.scratch/` 是本地工作区，不入库。
- [地图部署说明](map-deployment.md)：高德配置、坐标系、降级行为和现场核对项。
- [首衡电子秤接口联调交接](sohe-scale-integration-handoff.md)：跨系统接口草案、设备绑定、称重与打印回执、离线同步及验收；拟新增 API 不代表已经实现。

## 文档维护

- 新规则合并进 `decisions.md`，不再另建 ADR、设计副本或“最新版”文档。
- 进度和验证证据只维护在 `status.md`；完成的计划、一次性审计和过程日志从工作区删除，由 Git 历史追溯。
- `requirements/` 只保留当前仍用于裁定需求的基线。文档与代码不一致时，以本轮明确裁定、`AGENTS.md` 和 `decisions.md` 为准，并同步修正文档。
- 验证入口为 `tools/verify.ps1`、`tools/verify.sh` 和 `tools/verify.py`。记录结果时区分已通过、未执行、未覆盖和现场验收，不以本地代码或 mock 推断生产状态。
