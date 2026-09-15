# W2 A Repository A B Audit

审计日期：2026-09-15

审计结论：采用方案 A。以当前 `v2/**` 作为主干，按领域吸收 `project-reference-examples/xsy-scm` 的业务资产；Legacy 继续作为业务规则事实源，C 只作为经过规则、数据库和测试改造后的候选实现来源。

本次已暂停 W2 Customer/Supplier 开发。审计只读取 A、B、C 三个信息源，并新增本报告；没有修改业务代码、没有复制 reference 项目代码、没有合并或切换分支。

## 1. 审计边界和证据口径

信息源如下：

| 信息源 | 路径 | 本报告中的职责 |
|---|---|---|
| A Legacy | `xsy-scm-server/**`、`xsy-scm-web/**`、`xsy-scm-miniapp/**` | 业务规则事实源，判断当前业务语义、异常码、订单/库存/商城行为 |
| B 当前 V2 | `v2/xsy-scm-v2-server/**`、`v2/xsy-scm-v2-web/**` | 当前正式技术基线和 W1 Product 已验收实现 |
| C SmartAdmin-old | `project-reference-examples/xsy-scm/**` | 已实现过的 SmartAdmin 业务资产候选来源 |

C 的文档、需求、数据库脚本和模块清单先于源码审阅。已读取：`AGENTS.md`、`README.md`、`XSY_SCM_INIT.md`、`XSY_SCM_JAVA17_AI_EXECUTION_GUIDE.md`、`docs/**`、`功能清单模块.docx` 和 `数据库SQL脚本/**`。C 的文档显示其业务目标覆盖商品、客户、营销、订单、采购、库存、分拣、配送、财务、商城、订单助手和溯源；其中一部分仍标为 P1/P2 或待确认，不能仅按源码数量视为交付完成。

验证状态说明：本次没有在 C 内运行 Maven/npm 构建和服务启动，因为这会在 reference 项目中写入 `target`、构建产物或运行状态，违反“只调查、不修改 reference 项目”的边界。因此“能否编译/启动”分为“静态可构建性”和“本次未运行实证”，不把存在源码或已有产物误报为启动验收。

## 2. C 技术基线审计

证据主要来自 `project-reference-examples/xsy-scm/xsy-scm-server/pom.xml`、其 `xsy-scm-base` 配置、`xsy-scm-web/package.json`、`xsy-app/package.json`、`数据库SQL脚本/mysql/**` 和 `deploy/docker/**`。

| 项目 | C 审计结果 | 对当前 V2 的影响 |
|---|---|---|
| SmartAdmin | 文档和注释标为 v3.30.0；SQL 更新脚本包含 v3.30.0 | 不是当前 v3.31 基线，不能直接复制系统层 |
| Java | 17，Maven compiler source/target 17 | 业务代码需迁移到 Java 21 约束后再吸收 |
| Spring Boot | 3.5.4 | 与 B 的版本相同，升级主要集中在 Java、基线包名和项目约定 |
| Sa-Token | 1.44.0，含 `sa-token-redis-jackson` | 认证依赖方向可复用，但权限命名和登录上下文要对齐 B |
| MyBatis-Plus | 3.5.12 | 与 B 相同；C 的 Dao/Manager/XML 结构可作为业务迁移参考 |
| 数据库 | MySQL 8 设计与初始化 SQL；POM 使用 MySQL connector；没有 C 业务 Flyway | 需要 PostgreSQL DDL/查询/分页/类型重写，不能把 C SQL 当 V2 migration |
| Redis | Spring Data Redis、Sa-Token Redis、Redisson | 可复用缓存/重复提交思路；连接、序列化和 key 需按 B 配置核对 |
| 文件上传 | SmartAdmin 文件模块、S3 SDK、前端 `FileUpload`/`FilePreview` | 业务可复用文件字段和调用方式，但必须接入 B 的文件抽象和权限 |
| 日志 | SmartAdmin `OperateLog`、`DataTracer`、登录/操作日志 | 可吸收业务审计字段和操作点；不能绕过 B 的日志/脱敏规则 |
| 权限 | 后端大量 `@SaCheckPermission`，前端 `v-privilege`；C 约定如 `customer:query`、`purchase:generate` | 需映射为当前 V2 的 `scm:<domain>:<action>`，并导入 B 菜单权限 migration |
| Vue | Vue 3.4.27 | 与 B 相同大版本，可较高复用页面交互语义 |
| UI 库 | Ant Design Vue 4.2.5 | 与 B 相同；页面可作为迁移参考，不复制系统 Layout |
| UniApp | `@dcloudio/uni-app` 3.0.0-3090920231225001，Vue 3.2.47，Pinia 2.0.36 | C 的 xsy-app 是 UniApp 资产，但当前 Legacy miniapp 是 Taro + React；不能直接判定为当前小程序实现 |
| 构建工具 | 后端 Maven；管理端 Vite 5.2.12；UniApp Vite 4.0.3 | V2 管理端与 C 管理端工具链相近；移动端仍需按当前 W6 方向决策 |
| migration/SQL 管理 | SmartAdmin 系统 SQL 按版本手工执行；业务设计 SQL 在 `docs/database`；未发现业务 Flyway migration | 迁移成本中到高，必须重建为 V2 Flyway，不得顺拷 C SQL |
| 是否能编译 | 静态上存在 Maven 多模块、Vue 和 UniApp build scripts；本次未运行 | 未形成“已编译”证据 |
| 是否能启动 | 有 Docker Compose、Dockerfile、dev/test/prod 配置；依赖 MySQL、Redis、环境变量和文件服务 | 本次未启动，不能宣称端到端可启动；需在隔离环境做后续验证 |

C 的后端业务资产规模明确：customer 63 个 Java 文件、product 58、purchase 79、stock 67、finance 56、order 43、supplier 41、trace 33；Vue 端有 customer 4、product 6、purchase 6、stock 8、finance 2、order 4、supplier 6 个相关页面。该规模证明 C 值得作为迁移来源，但不证明业务已被测试或可直接运行。

## 3. A/B/C 领域对比

完成度含义：源码/API/页面存在只能算“已实现资产”；涉及关键业务却缺少自动化或真实链路证据时标记 `FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED`。

| 领域 | Legacy 现状 | 当前 V2 现状 | SmartAdmin-old 现状 | 数据表 | 后端完成度 | Vue 页面完成度 | 小程序完成度 | 自动化测试 | 已知缺陷 | 与当前业务规格冲突 | PostgreSQL 改造难度 | 推荐来源 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Product | 业务字段、价格、SKU、图片与订单引用规则已存在；是 A 的业务事实源 | W1 已验收，含当前 Product 聚合、PostgreSQL/Flyway、权限和 Product 测试 | C 有 SPU/SKU/价格/供应商/分类 Controller、Service、Manager、Dao、Vue 页面和 MySQL 设计 | `t_product*`、分类、价格、供应商关系 | C 功能资产完整，但未见业务测试 | C 页面完整 | C app 不是商品管理主端 | C 仅基础应用测试，未见 Product 业务测试 | C 的规则弱于 W1，且 MySQL 设计 | C 未证明 W1 的 SKU 聚合约束 | 高 | B/W1 主体；C 仅选择性吸收交互/字段 |
| Customer | A 有客户、价格、可见性、商城身份等当前规则 | 尚未迁入，W2 Customer 已暂停 | Customer 档案、账期、可见性、二维码、折扣、别名后端和 4 个 Vue 页面 | `t_customer`、period、visible、qrcode、discount、alias | 功能代码较完整，未验证 | 有基础管理页，缺当前 V2 页面 | C app 偏员工端，无完整客户管理闭环 | 无领域自动化 | C 的客户价格/权限命名和表模型需对齐 Legacy/B | MySQL DDL、集团结算、价格规则要重审 | 高 | Legacy 规则 + C 业务骨架 + B 基线 |
| Supplier | A 有供应商、供应商商品关系及采购链规则 | 尚未迁入 | Supplier 与采购 Supplier 资产、供应商协同扩展规划，Vue 有供应商/厂商/申请/对账页 | `t_supplier`、supplier account/apply/manufacturer/statement | 功能代码中到高，未验证 | 基础页存在，协同扩展不等于完成 | 未见完整供应商小程序闭环 | 无领域自动化 | 供应商协同部分文档标 P1/P2 | 供应商绑定、采购拆分、权限/数据范围需按 A/B 重建 | 高 | Legacy + C 采购/供应商骨架 |
| Pricing | A 已有客户类型价格、价格历史和订单价格快照规则 | Product 基础价格能力已在 W1；客户定价尚未迁入 | C 有 product price、customer discount，客户价格设计资产 | `t_product_price`、customer discount/price 关系 | C 有能力但价格生效/审计和当前规则未证实 | 有价格列表/客户折扣相关页面资产 | 未见 | 无领域自动化 | C 价格模型不是当前 W1/V2 合同 | 价格快照、BigDecimal 4 位、客户层级覆盖需对齐 | 高 | Legacy 规则 + B W1 精度/审计约束；C 只吸收页面和字段 |
| Sales Order | A 有订单、实重回写、退款、操作日志、商城/后台来源 | 尚未迁入 | order Controller/Service/Manager/Dao、订单列表/明细/退款/日志页面 | `t_order`、item、log、refund | 功能资产完整但未验证状态机 | 有 4 个管理页面 | C app 有订单列表/详情，偏员工端 | 无领域自动化 | 没有当前 V2 幂等/权限/实重证据 | 订单状态、实重结算、退款审计必须按 A/B 重建 | 高 | Legacy 事实 + C 订单骨架 + B API/迁移规范 |
| Purchase | A 有采购需求聚合、拆供应商/采购员、采购单和多次收货 | 尚未迁入 | inquiry、purchase generate/order/item/receive，后端 79 文件，Vue 6 页 | `t_purchase_order`、item、receive、inquiry | 功能资产较完整，未验证跨模块事务 | 页面覆盖主流程 | C app 有采购/收货页面 | 无领域自动化 | 无真实采购到入库链路证据 | 采购需求来源、供应商拆分、幂等和事务需对齐 A/B | 高 | C 作为首选业务骨架来源，Legacy 作规则裁决 |
| Receiving | A 规则要求多次实重收货并生成库存流水 | 尚未迁入 | `ReceiveController/Service/Manager`、收货列表和收货 API | `t_receive`、purchase item、stock flow | 代码存在但未验证 | 有收货页面 | C app 有 receive list/form | 无 DB 集成/并发/幂等测试 | 设计 SQL 为 MySQL，实际入库一致性未证明 | 实重、部分收货、入库流水必须使用 B 事务边界 | 高 | C 收货流程 + A 实重规则 + B Inventory 约束 |
| Inventory / Stock | A 有库存余额、流水、盘点、调整，当前实现是业务事实源 | 尚未迁入 | stock balance/flow/check/adjust/convert 资产；Vue 8 页 | `t_stock_balance`、flow、check、adjust | 功能代码较完整但未验证 | 页面最完整之一 | 未见完整库存移动端 | 无库存并发/一致性测试 | 未证实所有余额变更都经过不可变流水 | B 禁止静默 UPDATE，需重建事务、事件 key、加权平均 | 高 | C 作为页面/流程资产；核心库存规则以 A/B 规格为准 |
| Finance | A 有应收/收款/利润等当前业务语义 | 尚未迁入 | receivable/payment/invoice/voucher/external config 资产，Vue 2 页 | receivable、payable、payment、voucher 等 | 功能资产中到高，未验证财务闭环 | 仅基础应收/收款页，凭证部分不等于完成 | C app 有 payment/receivable 页面 | 无财务集成、幂等、对账测试 | 凭证/外部系统部分是扩展规划，不能视为已验收 | 金额精度、收款幂等、订单/库存结算必须按 A/B | 高 | Legacy + C 财务骨架；先做内部闭环 |
| Traceability | A 有溯源业务规则/接口参照 | 尚未迁入 | trace code/batch/inspect/node 后端资产；未见对应 Vue 业务页 | trace code/batch/node/inspect | 后端有功能资产，未验证 | 未发现完整 C Vue 溯源页 | 未见 | 无 | C 的需求和 SQL 部分仍是补充规划 | 批次、收货、库存、扫码数据关联需按 PostgreSQL/B 规范设计 | 高 | Legacy 规则 + C 后端模型，暂不直接迁页面 |
| Mall / Customer Ordering | A legacy mall 是当前商城事实源 | V2 尚未建设商城 | C 文档有商城主题/首页/下单规划，但 C 管理端未发现完整 mall 页面；xsy-app 主要是员工端订单/采购/财务 | mall theme/home + 复用 product/order/customer 表 | C 业务闭环不足 | 不足 | 非完整客户商城；不能替代当前 Legacy miniapp | 无商城 E2E | 客户登录、可见性、价格、支付/幂等链路未验收 | 必须保留 A 的客户身份和订单规则，不能从 C 规划直接落地 | 高 | A Legacy mall 规则；C 只作页面/字段参考 |
| UniApp | 当前 miniapp 是 Legacy Taro + React，冻结只读 | V2 尚未迁移移动端 | C 有 UniApp + Vue3 + Pinia 的 xsy-app，员工端包含订单/采购/收货/财务 | 复用后端领域表，不应复制 admin 表 | C 移动端资产可运行性未实证 | 不适用 | 员工业务端资产中到高；客户商城不足 | 无可靠移动端业务自动化 | 与当前 Taro + React 方向不同；版本和 API 假设不同 | 当前 W6 方向需先决定 UniApp 迁移范围，不能直接替换 Legacy | 中到高 | 先保留 A 业务事实；C UniApp 作为 W6 候选资产 |

## 4. Product 特别审计

结论：C Product 不得覆盖 W1。C 的 Product 业务代码和 Vue 页面值得作为字段、交互、供应商关联和历史页面的参考，但当前证据没有证明 C 在任何关键 SKU 约束上优于 W1。

| W1 要求 | 当前 V2 W1 | C Product 证据 | 结论 |
|---|---|---|---|
| SPU 至少 1 个 SKU | 有 Product 聚合校验测试和控制器测试 | 有 SPU/SKU 页面与服务，但未见等价自动化证据 | 保留 W1 |
| 恰好 1 个默认 SKU | W1 migration/校验覆盖 | C 有默认相关字段/页面线索，未证实数据库与服务双重约束 | W1 更强 |
| 默认 SKU partial unique | PostgreSQL 约束已纳入 W1 | C 是 MySQL 设计，未证明等价 partial unique | 保留 W1 |
| SKU 差量同步 | `ProductSkuChangeSetTest` | C 有 SKU CRUD，但未见差量同步测试 | W1 更强 |
| 已存在 SKU 保留 id/version | W1 有 optimistic lock/变更集测试 | C 未见等价证明 | 保留 W1 |
| 禁止 delete-and-recreate | W1 变更集语义覆盖 | C 未见测试或明确约束 | 保留 W1 |
| 跨 SPU SKU id 拒绝 | W1 聚合校验覆盖 | C 未见等价测试 | 保留 W1 |
| SKU 编码大小写不敏感 | W1 合同/数据库测试覆盖 | C MySQL collation 可能提供隐式行为，不是显式业务契约 | W1 更可迁移 |
| 条码去重 | W1 约束/校验 | C 设计存在条码扩展规划，但未形成同等 Product 证据 | W1 更强 |
| 规格组合归一化 | W1 Product 规则测试 | C 有规格字段/页面，但未见归一化测试 | 保留 W1 |
| `@Version` | W1 optimistic lock 测试 | C 未证明实体与更新链路一致使用 | 保留 W1 |
| 软删除后编码复用 | W1 migration/测试口径明确 | C 未见等价 PostgreSQL/业务测试 | 保留 W1 |
| JSONB 规格 | W1 PostgreSQL JSONB | C 以 MySQL/文本或普通字段设计为主 | W1 更强 |
| BigDecimal 4 位契约 | W1 有 BigDecimal 语义测试 | C 文档规定 DECIMAL，但未见端到端 4 位测试 | W1 更强 |
| 商品图片 | W1 有图片变更集与文件字段 | C 有文件上传和商品图片页面资产 | 可选择性吸收 C 交互 |
| Vue 页面 | W1 有 V2 SmartAdmin Vue 页面 | C 页面较完整但路由/权限为旧命名 | 迁移交互，不覆盖 W1 |
| 权限 | W1 使用 V2 权限范围 | C 有 Product 权限注解和 `v-privilege` | 映射后吸收权限清单 |
| E2E | W1 有 `scm-product.spec.ts` | C 未发现 Product E2E | W1 明显更强 |

值得补回 W1 的 C Product 能力：供应商关联页的字段组织、Product/SKU/价格页面的成熟交互、旧业务中使用过的商品选择器/分类选择器、与采购页面对接时的显示字段。明显弱于 W1 的部分：MySQL 数据约束、默认 SKU、差量更新、乐观锁、跨 SPU 校验、规格归一化、条码/编码契约、JSONB、精度和 E2E 证据。C Product 只能逐项移植设计意图，不能替换 W1 聚合实现。

## 5. MySQL 到 PostgreSQL 专项

C 的业务设计 SQL 主要位于 `project-reference-examples/xsy-scm/docs/database/*.sql`，SmartAdmin 初始化 SQL 位于 `数据库SQL脚本/mysql/*.sql`。已对全部 C SQL 搜索用户指定的语法模式，结果如下：

| 分类 | 命中/判断 | 改造工作量 |
|---|---|---|
| 可直接兼容 | 常规 `CREATE TABLE`、`VARCHAR`、`DECIMAL`、普通索引和注释语义；业务关系本身不依赖物理外键 | 低，但仍要转为 Flyway 顺序 migration |
| 简单语法转换 | `AUTO_INCREMENT` 85 处；`tinyint` 45 处；`datetime` 112 处；`unsigned` 11 处；反引号、`KEY`/`UNIQUE KEY`、MySQL `ENGINE/CHARSET/COLLATE` | 中：identity/bigint、boolean/smallint、timestamp、去掉 unsigned/engine/collation，重写索引语法 |
| 强 MySQL 依赖 | C 的 SQL 结构大量使用反引号、MySQL 建表选项、`DROP TABLE IF EXISTS` 顺序和 MySQL 初始数据；业务 SQL 没有现成 PostgreSQL 版本 | 中到高：需逐表 migration、数据类型和约束评审 |
| 未命中但必须防回归 | `IFNULL`、`GROUP_CONCAT`、`FIND_IN_SET`、`DATE_FORMAT`、`LIMIT offset,count`、`JSON_EXTRACT`、`JSON_CONTAINS`、`ON DUPLICATE KEY`、MySQL trigger/procedure 本次未在 C SQL 命中 | 当前搜索未见这些方言依赖；迁移 C Java/XML 查询时仍须再次搜索并用 `COALESCE`、`string_agg`、`ON CONFLICT`、PostgreSQL JSONB 等替换 |
| PostgreSQL 可优化 | 客户/商品可见性、订单状态、采购需求、库存流水和溯源批次适合使用 partial unique、`jsonb`、复合/覆盖索引、`CHECK` 和事件 key | 中：设计收益高，但需与 B 的无外键项目规则和 Flyway 规范一致 |

估算：仅把 C 的业务表和 SQL 机械转成 PostgreSQL 属于中等工作量；连同实体、XML 查询、分页、枚举、唯一性、并发、幂等、测试和数据迁移，整体为高工作量。预计每个核心领域至少需要一次 schema/API 评审，不能把 MySQL DDL 直接放进 V2。

## 6. SmartAdmin 3.30 到当前 3.31 的业务适配面

不建议复制 C 的系统层。业务迁移时需逐项检查以下依赖：

| 依赖 | C 使用情况 | V2 适配判断 |
|---|---|---|
| `ResponseDTO` / `PageResult` | C 业务 Controller 大量使用 | B 保留同名 SmartAdmin envelope；迁移时只改业务 DTO、分页参数和错误码，不引入 legacy Result |
| `SmartRequestUtil` | C 有 40 处调用 | B 仍有同一能力；核对分页排序和请求上下文，不直接复制 C 工具类 |
| `Sa-Token` / `@SaCheckPermission` | C 后端大量使用，权限命名偏 `customer:*`、`purchase:*` | B 仍使用 Sa-Token；业务权限改为 `scm:<domain>:<action>`，并将管理员绕过、数据范围和菜单 seed 统一到 B |
| `@OperateLog` / `DataTracer` | C 有操作日志和数据追踪 | B 已有日志/追踪基础；迁移时补前后值、业务对象、幂等 key 和敏感字段脱敏 |
| File | C 使用 SmartAdmin 文件服务、S3 SDK 和 `FileUpload` | B 已有文件抽象；只迁业务文件字段和展示行为，不能为每个模块新增存储实现 |
| Dict | C 页面和后端使用字典 | B 有 Dict 基础；迁移枚举时区分字典展示和业务状态机，不能把状态值散落为数字 |
| `TableOperator` | C 页面大量使用 SmartAdmin TableOperator，代码搜索未命中 `TableOperator` 后端 API | B 页面可复用 SmartAdmin 前端组件约定；不把 C 的页面直接覆盖 V2 Layout |
| v-privilege | C Vue 页面广泛使用 | B 同为 Vue + Ant Design Vue，可迁移按钮权限意图，但权限字符串必须重命名并由后端强校验 |

## 7. 测试质量与完成度判断

C 的源码规模大于其可验证性。C 后端只发现基础 `AdminApplicationTest`，未发现按 customer/product/order/purchase/stock/finance/supplier/trace 划分的业务测试；未发现 DB 集成、并发、幂等或关键浏览器 E2E 证据。C 页面和 API 因此统一标注为 `FUNCTIONALLY IMPLEMENTED BUT UNVERIFIED`，尤其是订单状态、采购聚合、部分收货、库存流水、财务核销和溯源关联。

B 的 W1 Product 测试质量显著高于 C：已发现 Product 聚合校验、SKU change set、图片 change set、Category level、Controller、PostgreSQL 集成、migration、optimistic lock、BigDecimal 语义和 Product E2E 测试。B 目前的问题不是 Product 证据不足，而是 Customer/Supplier/Order/Purchase/Stock 等后续领域尚未迁入。

后续领域最低质量门槛：

- 单元测试：状态转换、价格/重量/精度、聚合拆分和业务校验。
- DB 集成测试：Flyway、唯一约束、partial unique、库存流水和关键查询。
- 并发测试：库存扣减、收货确认、订单/采购状态竞争、乐观锁。
- 幂等测试：外部下单、收货确认、库存入账、收款/退款。
- E2E：只覆盖关键主链路，不以页面文件数量代替业务验收。

## 8. 方案评分

评分 1～10 分，分数越高越有利。对“迁移风险、数据库迁移成本、SmartAdmin 升级成本、测试补齐成本”采用“成本越低分越高”的方向。

| 维度 | 方案 A：V2 主干吸收 C | 方案 B：C 主干反向迁 V2/W0/W1 |
|---|---:|---:|
| 剩余开发量 | 8 | 4 |
| 迁移风险 | 8 | 3 |
| 业务完整度 | 5 | 8 |
| 业务正确性 | 8 | 5 |
| 数据库迁移成本 | 8 | 3 |
| SmartAdmin 升级成本 | 9 | 4 |
| Vue 复用率 | 7 | 8 |
| UniApp 复用率 | 6 | 7 |
| 测试补齐成本 | 7 | 3 |
| 长期维护性 | 9 | 5 |
| **总分** | **75** | **50** |

方案 B 的业务完整度和 Vue/UniApp 资产复用率较高，但它把已验收的 W1 Product、PostgreSQL/Flyway、SmartAdmin v3.31、Java 21 和测试体系重新降级为待迁移对象；同时 C 的 MySQL 设计、Java 17、v3.30、未验证业务链路和与 Legacy 的规则差异会扩大返工面。唯一推荐方案：方案 A。

## 9. 模块处置建议

### 直接复用或优先吸收 C 的模块

- Customer：吸收客户、账期、可见性、二维码、别名和页面字段组织；业务规则以 Legacy 为准，权限改为 V2 命名。
- Supplier/Purchase：优先吸收采购需求、询价、供应商拆分、采购生成、采购明细和收货页面/DTO/流程骨架。
- Stock：吸收库存余额、流水、盘点、调整和规格转换的页面交互与业务对象；库存写入必须重建为 V2 不可变 movement 事务。
- Order：吸收订单列表、明细、退款、日志的交互和状态对象候选；订单状态、实重和幂等以 Legacy/V2 规则裁决。
- Finance：吸收应收、收款、发票和凭证字段/页面骨架，先限定内部业务闭环，不把外部凭证同步当作已完成能力。
- Product 辅助能力：供应商关联、旧页面交互、选择器字段和图片/价格展示，不吸收 W1 聚合实现。

### 必须保留当前 V2 的模块

- SmartAdmin 系统底座、认证、用户/员工/部门/角色/菜单/权限、统一响应、文件、日志、字典和 Layout。
- W1 Product 的 PostgreSQL/Flyway schema、SKU 聚合规则、乐观锁、JSONB、BigDecimal 4 位契约、Product 权限和 E2E。
- V2 的 Java 21、PostgreSQL、Flyway、Sa-Token 权限命名、无外键规则、Mapper XML 规则和测试门槛。

### 暂不迁移或应废弃的 C 资产

- C 的 SmartAdmin 系统层、登录/认证/菜单/基础 Layout：不复制、不覆盖 V2。
- C 的 MySQL 初始化 SQL、手工 SQL 更新日志和 MySQL 专属建表选项：废弃为 V2 schema 来源，仅保留设计参考。
- C 的 Product 主聚合实现：不替换 W1；只选择性吸收字段和交互。
- C 的商城规划直接实现：不作为当前 V2 订单商城来源；商城以 Legacy 规则和当前 miniapp 路线重新设计。
- C 的 trace Vue 页面（未发现完整页面）和未验证的移动端业务闭环：不以“有后端类”视为完成。

## 10. 下一波只做什么

下一波只做“C Purchase/Receiving/Stock 资产可迁移性验证与 W2 迁移设计”，不立即编码 Customer/Supplier：

1. 选定 Purchase → Receiving → Inventory 这一条主链，逐接口建立 Legacy/C/V2 规则映射表。
2. 逐表把 C 的 MySQL 设计转换为 PostgreSQL/Flyway 草案，先处理 `t_purchase_order`、`t_purchase_item`、`t_receive`、`t_stock_balance`、`t_stock_flow`。
3. 对 C 的关键 Service/Manager/Dao 做业务审查清单：部分收货、实重、供应商拆分、重复确认、库存 movement、审计日志和权限。
4. 只补迁移设计和测试矩阵，不写业务实现；待设计评审通过后，按领域切片迁移并保持 W1 Product 不变。

本报告完成后停止。没有编码、没有合并、没有修改 `project-reference-examples/xsy-scm/**`。
