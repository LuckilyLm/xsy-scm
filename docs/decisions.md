# 当前项目决策

最后更新：2026-09-18

这些是当前仓库继续有效的简要边界。详细业务语义优先查阅
[`project-reference-examples/xsy-scm/`](../project-reference-examples/xsy-scm/)；实现约束以根目录 `AGENTS.md` 为准。

## 工程边界

- `xsy-scm-server/` 和 `xsy-scm-web/` 是 V2 正式实现；`xsy-scm-miniapp/` 冻结，只读参考。
- SmartAdmin 是系统底座；SCM 只实现供应链业务域，不复制旧认证、组织和权限实现。
- 后端使用 Java 21、Spring Boot、MyBatis-Plus、PostgreSQL、Flyway 和 Sa-Token；前端使用 Vue 3 + TypeScript。
- PostgreSQL 不使用数据库外键；关系完整性由服务层、事务、唯一约束和检查约束保证。
- 金额使用 `BigDecimal`，库存变化必须有可追溯流水，敏感变更需要审计。
- 不引入微服务、消息总线、Kubernetes 或第二套 UI / 状态 / HTTP 框架，除非另有明确决策。

## 参考与变更

- `project-reference-examples/xsy-scm/` 是需求、旧业务语义和参考页面的主要来源，保持只读。
- 参考内容不能直接覆盖当前 V2 技术栈、权限命名、数据库规则或已批准的业务实现。
- 新需求先在参考项目中定位来源，再在代码和本文件中记录当前项目的取舍；不要重新建立大批镜像文档。
- 验证入口 `verify.ps1` / `verify.sh` 共用 `tools/verify.py`；仅验证工具及其测试纳入 Git 白名单，TS 历史基线保存在 `xsy-scm-web/quality/`。类型门禁要求 SCM 零错误且全局不新增，不代表上游全量类型检查零错误。
- 采购单拆分保留事务编排在 `PurchaseOrderService`；行装配与分配对账共享请求行上下文，暂不继续拆出 Assembler，也不扩展到收货服务重构。

## 未决事项

- 仓库启停和多仓默认选择的完整业务规则。
- 非管理员角色的数据范围与库存权限组合。
- F0-DEBT-01 的业务附件逐用户读取控制。
- W6-2 小程序的启动条件与迁移方案。
