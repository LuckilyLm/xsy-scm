# AGENTS.md

鲜蔬源智慧供应链管理平台（`xsy-scm`）仓库协作规则。本文只保留当前有效、跨任务稳定的约束。

## 项目范围

- V2 正式项目位于 `xsy-scm-server/`、`xsy-scm-web/` 与 `xsy-scm-miniprogram/`；管理后台采用 Vue 3 + TypeScript，微信小程序采用 uni-app + Vue 3。
- 系统管理与公共模块提供登录、认证、用户、员工、部门、角色、菜单、权限、数据权限、日志、字典、文件、统一异常和后台布局；供应链业务复用这些能力。
- 后端模块为xsy-admin和xsy-base，所有正式Java包以com.xsy.scm为根，系统管理放在admin，通用能力放在base，其余业务目录保持清楚的职责边界。
- `xsy-scm-miniprogram/` 是给用户使用的微信小程序，只承载用户端身份与页面；后续员工端使用独立工作区，不与小程序权限和路由混用。
- `xsy-scale-agent/` 是独立的 Windows 秤端与触屏工作台源码；正式分拣、库存与财务记录仍以 V2 服务端为准。平台设备接口 `/scm/device/scale/v1`（V171/V172）已接入；真实电子秤、打印机与现场标定仍须单独联调验收，不能将本地演练结果当作平台验收。
- 旧 Taro/React 小程序已从工作树移除，历史代码仅由 Git 追溯。不要迁移旧 `auth` / `system` 实现，也不要机械复制旧代码或把 React 翻译成 Vue。
- 业务主线是客户与商品 → 销售订单 → 采购与收货 → 库存 → 分拣 → 配送 → 财务。当前交付状态和待办以 [项目状态](docs/status.md) 为准。

## 用户界面用词

- 页面提示、接口错误、源码注释和说明文档都用通俗中文，直接说明何时保存资料、能否修改、重复请求如何处理。历史资料须说明不会随当前资料变化；英文类名、接口字段、数据库字段和已发布迁移脚本保持原样。

- 用户可见文案统一使用：SPU → 商品，SKU → 商品规格，SPU 编码 → 商品编码，SKU 编码 → 商品规格编码，SKU 列表 → 商品规格列表，SKU 数量 → 商品规格数（表格列宽明显不足时可用“规格数”）；Spec / Specification → 规格项，Spec Value → 规格值。
- 商品、采购、销售、库存、调拨、盘点、售后、报表和导入导出页面均遵守上述用词；用户界面不得显示 `SPU`、`SKU`、`品规`、`货品` 或 `单品`。
- 技术标识符、API 字段和数据库字段保留现有 `spu` / `sku` 命名。新导入模板使用完整中文表头，并兼容已发布的旧表头。
- 管理后台界面遵循根目录 `DESIGN.md` 的表格、状态、表单、抽屉与响应式约定；工程评审见 `CONTRIBUTING.md`。不伪造后端数据或把技术字段名直接展示给用户。

## 前端约束

- 前端源码、E2E、单测和配置文件统一由 `xsy-scm-web` 下的 `npm run format`（Prettier）维护，配置见 `.prettierrc.cjs`；授权验证时运行 `npm run format:check`，不要另建格式或手工规避。
- `test/` 的源码检查用例允许空白随格式化变化；只能放宽空白匹配，不能删除或弱化业务断言。

## 规则来源

规则冲突时，依次遵循：用户本轮明确要求、本文、`docs/decisions.md` 与正式需求基线、现有实现。历史过程由 Git 追溯，不作为当前规则来源。

- 当前业务和工程决策索引：[docs/decisions.md](docs/decisions.md)
- 当前交付状态：[docs/status.md](docs/status.md)
- 文档入口：[docs/README.md](docs/README.md)
- 代码评审与验证：[CONTRIBUTING.md](CONTRIBUTING.md)

## Agent skills

本仓库安装的工程技能（`mattpocock/skills`）按 `docs/agents/` 下的配置工作。

### Issue tracker

本地 markdown：spec 与工单写在 `.scratch/<feature>/`，不入版本库。见 [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md)。

### Domain docs

单上下文：术语表用根目录 `GLOSSARY.md`（按需创建）；决策一律并入 [docs/decisions.md](docs/decisions.md)，不另建 ADR。见 [docs/agents/domain.md](docs/agents/domain.md)。

## 协作与 Git

- 保持用户开始时的分支；没有明确要求时不切分支、不推送、不发布。
- 保留工作区中与任务无关的改动；提交时只暂存本任务明确修改的路径，不使用 `git add -A`。
- 不覆盖、重置或删除用户数据。已应用的 Flyway migration 不得修改；新增数据库变更使用新版本 migration。
- 默认不运行测试或构建；只有用户明确要求验证时才执行，并报告实际覆盖与未覆盖内容。不得为通过门禁修改测试来削弱业务约束。

## Java / Spring 约束

- SCM 正式业务包使用 `com.xsy.scm`。保持 Controller → Service → Manager / Policy → DAO 的依赖方向；Controller 不直接访问 DAO，领域服务不依赖 Controller，`common` 不反向依赖业务域。
- 跨域读使用明确的只读 DAO 或正式领域约定；不得直接写其他业务域的表。Finance 只读消费订单、采购、库存和配送记录，不修改这些业务表。
- Service 承担事务内的操作顺序、状态转换、策略和 DAO 协作。超过 400 行时审查职责，但按业务职责决定是否拆分，不按行数机械拆类。
- 权限注解使用领域 Permission Catalog；新增表单校验需提供可读 message；业务状态、类型和来源使用现有 Enum，避免重复枚举和 magic string。
- 依赖字段名称应表达完整业务语义；禁止 wildcard import 和重复包装 JDK / Spring / Apache 工具类。
- Java 格式统一由 `mvn -pl xsy-admin spotless:apply` 维护，配置为 `tools/quality/eclipse-formatter.xml`（4 空格、120 列、续行 4 空格、LF）；不要另建一套格式或手工规避 `spotless:check`。
- 注释解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的；能从类名、方法名、参数名、类型和代码流程直接读出来的信息，不要再用 Javadoc 翻译一遍（如字段名翻译式 `@param`）。不写阶段编号、测试统计、提交过程或过期计划；跨域长期规则放入 `docs/decisions.md`，源码只保留必要说明。

## 业务不变量

- SCM 数据范围集中解析并显式传到 DAO；范围维度相交、不相互替代。范围为空时拒绝访问；用户输入的仓库或归属筛选只能进一步收窄授权范围。SCM 不使用 XSY `@DataScope`。
- 附件由通用 `support/file` 层维护对象关系；服务端 URL 解析统一经过 `FileService` 与访问策略。新上传统一公开，存量私有文件按原授权读取；商品图片只绑定 `public/image/` 文件。
- 库存流水是追加记录，不做 UPDATE / DELETE；余额按 `(warehouse_id, sku_id)` 锁定一个记账单位，单位不一致时拒绝整笔操作。
- 分拣不修改订购量或库存；已确认、未发车的非标行可按正式称重接受结果同步实际数量与结算额。配送发车原子地产生销售出库，库存变更只经过库存域命令；签收不撤销出库。
- Finance 追加财务记录，不建立第二套订单状态机；更正追加反向记录，不修改原记录。金额方向由类型表达，金额本身为正数。
- 当前业务约束和决策统一维护在 `docs/decisions.md`，不要从 Git 历史或旧进度记录推导新规则。

## 文档与验证

- 当前状态和未完成工作写入 `docs/status.md`；已完成计划、一次性审计和执行日志退出活动文档，从 Git 历史追溯。每项当前规则只维护一份依据。
- 项目文档不记录个人路径、本机运行状态、临时产物或逐次测试结果；测试结果由对应测试与验证记录保存。
- 验证入口位于 `tools/verify.ps1`、`tools/verify.sh` 和 `tools/verify.py`。未运行的验证不得报告为通过。
- 完成任务时简要说明改动、提交或工作区状态，以及未执行的验证。
