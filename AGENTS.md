# AGENTS.md

鲜蔬源智慧供应链管理平台（`xsy-scm`）仓库协作规则。本文只保留当前有效、跨任务稳定的约束。

## 项目范围

- V2 正式项目位于 `xsy-scm-server/` 与 `xsy-scm-web/`；管理后台采用 Vue 3 + TypeScript。
- SmartAdmin 是登录、认证、用户、员工、部门、角色、菜单、权限、数据权限、日志、字典、文件、统一异常和后台 Layout 的正式底座。具体边界见 [SmartAdmin 底座规则](docs/architecture/smartadmin-foundation.md)。
- `xsy-scm-miniapp/` 是冻结的 legacy 目录。不要迁移旧 `auth` / `system` 实现，也不要机械复制旧代码或把 React 翻译成 Vue。
- 业务主线是客户与商品 → 销售订单 → 采购与收货 → 库存 → 分拣 → 配送 → 财务。当前交付状态和待办以 [项目状态](docs/status.md) 为准。

## 用户界面用词

- 用户可见文案统一使用：SPU → 商品，SKU → 商品规格，SPU 编码 → 商品编码，SKU 编码 → 商品规格编码，SKU 列表 → 商品规格列表，SKU 数量 → 商品规格数（表格列宽明显不足时可用“规格数”）；Spec / Specification → 规格项，Spec Value → 规格值。
- 商品、采购、销售、库存、调拨、盘点、售后、报表和导入导出页面均遵守上述用词；用户界面不得显示 `SPU`、`SKU`、`品规`、`货品` 或 `单品`。
- 技术标识符、API 字段和数据库字段保留现有 `spu` / `sku` 命名。新导入模板使用完整中文表头，并兼容已发布的旧表头。

## 规则来源

规则冲突时，依次遵循：用户本轮明确要求、本文、对应领域的正式决策与计划、现有实现。`docs/archive/` 是历史记录，不覆盖当前规则。

- 当前业务和工程决策索引：[docs/decisions.md](docs/decisions.md)
- 正式 ADR：[docs/adr/](docs/adr/)
- 质量基线：[docs/quality/java-code-quality-remediation-plan.md](docs/quality/java-code-quality-remediation-plan.md)
- SCM UI 长期规范：[docs/architecture/scm-ui-guidelines.md](docs/architecture/scm-ui-guidelines.md)
- 文档入口：[docs/README.md](docs/README.md)

## 协作与 Git

- 保持用户开始时的分支；没有明确要求时不切分支、不推送、不发布。
- 保留工作区中与任务无关的改动；提交时只暂存本任务明确修改的路径，不使用 `git add -A`。
- 不覆盖、重置或删除用户数据。已应用的 Flyway migration 不得修改；新增数据库变更使用新版本 migration。
- 默认不运行测试或构建；只有用户明确要求验证时才执行，并报告实际覆盖与未覆盖内容。不得为通过门禁修改测试来削弱业务约束。

## Java / Spring 约束

- SCM 正式业务包使用 `com.xsy.scm`。保持 Controller → Service → Manager / Policy → DAO 的依赖方向；Controller 不直接访问 DAO，领域服务不依赖 Controller，`common` 不反向依赖业务域。
- 跨域读使用明确的只读 DAO 或正式领域契约；不得直接写其他业务域的表。Finance 只读消费订单、采购、库存和配送事实，不修改这些业务表。
- Service 承担事务编排、状态转换、策略和 DAO 协作。超过 400 行时审查职责，但按业务职责决定是否拆分，不按行数机械拆类。
- 权限注解使用领域 Permission Catalog；新增表单校验需提供可读 message；业务状态、类型和来源使用现有 Enum，避免重复枚举和 magic string。
- 依赖字段名称应表达完整业务语义；禁止 wildcard import 和重复包装 JDK / Spring / Apache 工具类。
- 注释解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的；能从类名、方法名、参数名、类型和代码流程直接读出来的信息，不要再用 Javadoc 翻译一遍（如字段名翻译式 `@param`）。不写阶段编号、测试统计、提交过程或过期计划；表格与矩阵类说明移入 `docs/architecture/`。详见[源码注释规范](docs/architecture/code-comment-guidelines.md)。

## 业务不变量

- SCM 数据范围集中解析并显式传到 DAO；范围维度相交、不相互替代。空范围失败关闭；用户输入的仓库或归属筛选只能进一步收窄授权范围。SCM 不使用 SmartAdmin `@DataScope`。
- 附件由通用 `support/file` 层维护对象关系；服务端 URL 解析统一经过 `FileService` 与访问策略。商品图片只绑定 `public/` 文件。
- 库存流水是追加事实，不做 UPDATE / DELETE；余额按 `(warehouse_id, sku_id)` 锁定一个记账单位，单位不一致时拒绝整笔操作。
- 分拣只产生已分拣数量，不回写订单数量或库存；配送发车原子地产生销售出库，库存变更只经过库存域命令；签收不撤销出库。
- Finance 追加财务事实，不建立第二套订单状态机；更正追加反向事实，不修改原记录。金额方向由类型表达，金额本身为正数。
- 更完整的当前业务约束和决策见 `docs/adr/`，不要从归档进度记录推导新规则。

## 文档与验证

- 当前状态写入 `docs/status.md`；`docs/plan/active/` 只保留仍指导后续施工的计划或正式设计。已完成计划、一次性审计和执行日志直接删除并从 Git 历史追溯；只有仍被现行文档引用的历史决策才放 `docs/archive/`。
- 验证入口位于 `tools/verify.ps1`、`tools/verify.sh` 和 `tools/verify.py`。未运行的验证不得报告为通过。
- 完成任务时简要说明改动、提交或工作区状态，以及未执行的验证。
