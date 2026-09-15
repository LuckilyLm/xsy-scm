# W2 Customer + Supplier 批准记录

批准来源：2026-09-15 当前用户指令。仅执行 W2，按 T1 → T14 顺序，**不自动进入 W3**。

## 冻结基线唯一批准差异

冻结 HEAD：`95a54233586aa3c652e55cd833bb346d51f5a952`；分支：`feature/sprint5`。
原始清单：`D:\DevCaches\Codex\reviews\xsy-scm-legacy-freeze-2026-09-14\evidence\original-sha256.manifest`。

唯一批准差异仍是根 `.gitignore` 新增 `.workbuddy-ai` 与 `.runtime` 两条规则，保留、不还原。
`.gitignore` 当前 SHA-256 = `7a01b8ea6126557744f5e01696417de1d51944ab89557adf489534d05d0f2f3a`（与 W1 批准基线一致）。
`xsy-scm-server/**`、`xsy-scm-web/**`、`xsy-scm-miniapp/**`、根 `docs/**` 和其它冻结文件不允许变化。

W2 新增复验脚本 `tools/verify_w2_legacy.py` 与清单 `docs/architecture/w2-applied-migrations.sha256`，
把「已应用即不可回改」的保护范围从 V6/V7 扩到 **V6–V9**。除清单路径外判定规则与 W1 完全一致，未新增放行项。

## Q1–Q14

全部按 Target Design 推荐方案批准，**仅 Q5 修订**。

| # | 决策 |
|---|---|
| Q1 | 客户状态采用 **四态**（`POTENTIAL` / `COOPERATING` / `SUSPENDED` / `BLACKLIST`） |
| Q2 | 客户编码 **手工录入** + DB partial unique（不引入编号规则模块） |
| Q3 | 客户类型 **新增删除能力**，被客户引用则拒绝（40938） |
| Q4 | 供应商 **新增删除能力**，被活动 `supplier_sku` 引用则拒绝（40947） |
| **Q5** | **修订**：`warehouse` 不进入 W2，且**不要标记 "W3"**；后续随 Purchase / Receiving / Inventory 波次一起迁移 |
| Q6 | 账期 **内嵌** `customer` 基础字段（不建 `customer_period` 子表） |
| Q7 | 归属关系（`parent_customer_id` / `seller_id` / `supplier_id`）**纳入**为可空基础字段；不做结算逻辑与 `@DataScope` |
| Q8 | 客户搜索 **扩展**覆盖联系人 / 联系电话（标注为 V2 行为增强） |
| Q9 | 客户类型下拉 **只含 `ENABLED`**；管理列表显示全部 |
| Q10 | **不落** legacy 的 `visibility_policy` 列 |
| Q11 | `supplier_sku` **允许**同一供应商多条 `is_default = TRUE`（保持 legacy，不发明基数策略） |
| Q12 | 业务员 / 采购员引用 **SmartAdmin `t_employee.employee_id`** |
| Q13 | 新增客户默认状态 **`POTENTIAL`** |
| Q14 | **不提供**批量删除 |

## 永久锁定的 V2 架构原则（本次一并锁定）

- **Frontend** = SmartAdmin v3.31 Base + C SCM Vue **Copy First + Adapt**。
- **Backend Infrastructure** = **SmartAdmin Native First**。
- **Backend SCM Business** = SmartAdmin Structure + confirmed SCM business rules。
- **SCM 禁止重新实现第二套**：authentication / authorization / current employee-user context /
  role-dept-menu-permission / data-scope framework / operate-login log / dict / file /
  redis infrastructure / pagination / ResponseDTO / global exception handling /
  validation infrastructure / MyBatis-Plus infrastructure / Web-MVC infrastructure / Jackson infrastructure。
- **SCM 只保留真正的领域能力**：customer/supplier business rules、pricing、order state machine、
  purchase/receiving rules、inventory ledger、业务所需乐观/悲观锁、idempotency、domain audit、
  BigDecimal/quantity semantics。

## 强制约束

- 只新增 `V8__scm_customer_supplier.sql` 与 `V9__scm_customer_supplier_permissions.sql`；
  不修改 V6/V7；不修改 `module/scm/product/**`；legacy / reference 全部只读；正式代码只写根目录 V2 工作区。
- 前端禁止重新生成 C 已存在的同功能 SCM Vue 页面；C 已有页面若最终重写必须说明原因
  （W2 仅 1 例：`supplier-sku-drawer.vue`，因 C 是 SPU 级而 V2 是 SKU 级，见验收报告 §3.3）。
- 绝不复制 layout / login / system / router core / permission framework / request framework /
  SmartAdmin common-base。
- 验收报告必须包含 **Frontend Migration Provenance**（逐文件 C source → V2 target → 标记）。

## 执行进度

T1–T14 全部完成（2026-09-15 收尾）。验收结论见
`2026-09-15-w2-customer-supplier-验收报告.md`：**Go（附 2 项已修复缺陷 + 1 项遗留限制说明）**。

门禁全绿：后端单测 110/110、PG IT 86/86、前端单测 17/17、
`ts_baseline_ratchet check` PASS（SCM 0 / new 0）、ESLint 0 error、vite build ✓、
Customer Playwright 2/2、Supplier Playwright 2/2、legacy SHA-256 PASS。

**不进入 W3。**
