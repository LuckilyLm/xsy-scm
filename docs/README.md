# 项目文档

日常开发优先看三个入口：

- [项目状态](status.md)：当前代码事实、最近一次验证证据、部署边界。
- [后续开发路线图](plan/active/admin-development-roadmap.md)：只记录仍未完成的工作。
- [有效决策](decisions.md)：当前业务规则、正式 ADR 与仍未裁决事项。

按主题深入时再看：

- 需求：[产品功能需求基线](requirements/产品功能需求基线.md)、[负责人确认口径](requirements/2026-09-09-负责人确认口径.md)。
- 架构：[SmartAdmin 底座](architecture/smartadmin-foundation.md)、[SCM UI 规范](architecture/scm-ui-guidelines.md)、[源码注释规范](architecture/code-comment-guidelines.md)、[地图部署](architecture/map-deployment.md)、[采购快照矩阵](architecture/purchase-snapshot.md)。
- 正式设计：[Finance R1](plan/active/finance-r1-design.md)、[余额支付、订单资金核销与售后返还](plan/active/balance-payment-order-settlement-design.md)。
- 尚未补齐的接口字段：[前端所需后端字段缺口](plan/active/frontend-ui-backend-gap-inventory.md)。
- 工程规则：[Java 工程规范与质量基线](quality/java-code-quality-remediation-plan.md)、[贡献与验证指南](../CONTRIBUTING.md)。

## 文档治理

- `status.md` 只写“现在是什么状态”，不堆开发过程。
- `plan/active/` 只放仍会指导后续施工的计划或正式设计；已经完成的实施计划、一次性审计、验收流水直接删除，从 Git 历史追溯。
- `adr/` 保存长期有效的业务与架构决策；`archive/` 只保存仍有明确追溯价值、且被现行文档引用的历史决策。
- 需求、状态、规则、路线图各维护一份事实源，禁止为同一主题再建第二份“最新版”。
- 测试记录必须区分“已通过 / 未执行 / 未覆盖”；本地验证、Git 提交、生产部署与现场验收不能互相替代。
