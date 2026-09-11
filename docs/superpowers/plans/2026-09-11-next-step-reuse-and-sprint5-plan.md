# 下一步复用审计与 Sprint 5 收敛实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不盲目合并 `feature/sprint4-miniprogram` 的前提下，复用已验证的小程序与业务基础，并将项目推进到可验收的价格、商城和订单主链路基线。

**Architecture:** 保持当前 Java 21 + Spring Boot 模块化单体、React Web 和 Taro + React + TypeScript 小程序边界。以 `feature/sprint4` 为基线，在隔离工作区逐提交审计目标分支；只挑选小程序客户端、纯类型/服务合同和已确认的业务域改动，避免把分支上的删除、迁移重排或未批准扩展整体带入。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus/XML, Flyway, PostgreSQL, React/Vite/TypeScript, Taro/React/微信小程序, Vitest, Playwright.

**Spec:** `docs/roadmap/2026-09-11-Sprint5-12产品路线规划.md`、`docs/roadmap/商城与小程序规划.md`、`docs/requirements/产品功能需求基线.md`、`docs/requirements/2026-09-09-负责人确认口径.md`。

## Global Constraints

- 当前主检出保持 `feature/sprint4`，不得在主工作区切换分支。
- 复用前必须重新核对当前提交、目标提交、共同祖先、迁移顺序和测试证据；历史提交哈希不视为当前事实。
- 价格、库存、促销资格和订单金额以后端为唯一权威；客户端不得决定成交结果。
- 小程序与后台员工身份、权限和组件边界分离；不移植 Ant Design/ProComponents、后台路由或 Cookie 假设。
- 不新增支付、AI 语音、复杂营销、硬件直连、完整总账等未冻结范围。
- 每个阶段形成独立规格、独立验证证据和小而可审查的提交。

---

### Task 1: 建立目标分支可复用性清单

**Files:**
- Inspect: `xsy-scm-miniapp/`
- Inspect: `xsy-scm-server/`
- Inspect: `xsy-scm-web/`
- Inspect: `docs/roadmap/商城与小程序规划.md`
- Inspect: `docs/references/sdongpo/功能参考与借鉴记录.md`

**Interfaces:**
- Consumes: `feature/sprint4` and `feature/sprint4-miniprogram` commit graphs and diffs.
- Produces: a reviewed table classifying each changed area as `reuse`, `adapt`, `defer`, or `reject`.

- [ ] 在临时 worktree 中比较共同祖先到两分支的提交和文件变更，按提交拆分，不按整个分支合并。
- [ ] 对 `xsy-scm-miniapp` 核对登录、目录、价格展示、购物车、结算预览、订单列表/详情、地址和服务层是否与当前后端合同一致。
- [ ] 对服务端逐项检查领域包、Flyway 版本、权限种子和删除/改名变更，确认没有回退 Sprint 4 收货规则或破坏迁移链。
- [ ] 产出复用矩阵和风险清单；未经业务确认的营销、支付、供应商协同、硬件和 H5 扩展列为延后。
- [ ] 复用矩阵通过一次人工规格评审后，才允许进入实现。

### Task 2: Sprint 5 价格中心与客户可见性规格冻结

**Files:**
- Read/Update after approval: `docs/requirements/`
- Read: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/customer/`
- Read: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/mall/`
- Read: `xsy-scm-web/src/pages/customer/`
- Read: `xsy-scm-miniapp/src/services/catalog.ts`

**Interfaces:**
- Consumes: existing customer, product, mall, and order contracts.
- Produces: approved price-source precedence, effective-time rules, missing-price behavior, batch adjustment semantics, and audit requirements.

- [ ] 明确基础售价、客户类型价、客户协议价、活动价的唯一优先级及有效期重叠处理。
- [ ] 明确客户停用、商品下架、无价商品、规格和起订量在 Web 与小程序的统一响应。
- [ ] 明确批量调价/导入采用整批回滚还是逐行结果，以及失败行的错误码和审计记录。
- [ ] 明确订单项保存价格来源、锁定值、快照时间和人工改价操作历史。
- [ ] 规格批准前不实现结算层新规则。

### Task 3: 按最小提交复用小程序基础

**Files:**
- Modify only after Task 2: `xsy-scm-miniapp/src/`
- Modify only after Task 2: matching server API files and migrations when a contract gap is proven.

**Interfaces:**
- Consumes: Task 1 reuse matrix and Task 2 approved contract.
- Produces: independently buildable M0/M1/M2 client slices using existing `/api` contracts.

- [ ] 先复用身份、地址、目录、购物车、结算预览和订单查询的纯客户端基础；不复制后台 DOM 或权限模型。
- [ ] 对每个接口核对会话失效、加载/空/错/重试状态和重复提交幂等键。
- [ ] 只在现有服务合同不足时补充后端聚合接口；不让小程序直接拼接后台管理接口。
- [ ] 保留原订购量与实际结算量语义，为后续分拣实重回写留接口，不在本阶段提前实现称重。

### Task 4: 完成质量门禁和关键浏览器验收

**Files:**
- Test: `xsy-scm-web/e2e/`
- Test: `xsy-scm-miniapp/` project scripts
- Verify: `xsy-scm-server/`

**Interfaces:**
- Consumes: Task 3 integrated slices and approved price contract.
- Produces: reproducible verification evidence for Sprint 5 completion or explicit blockers.

- [ ] 后端运行编译、单元测试、受影响集成测试、Flyway 空库/升级验证。
- [ ] Web 运行 `npm run lint`、`npm run typecheck`、`npm run test`、`npm run build`。
- [ ] 小程序运行 lint、typecheck、微信小程序构建和 H5 构建。
- [ ] Playwright 覆盖登录、客户可见商品、价格命中/缺价、购物车、订单预览、重复提交和订单查询。
- [ ] 若 Vitest 或 E2E 因环境挂起/后端未启动失败，记录实际错误和环境阻塞，不将其标记为通过。

### Task 5: 进入 Sprint 6，再按业务链推进 Sprint 7–12

**Files:**
- Create after approval: the Sprint-specific spec and plan under `docs/superpowers/specs/` and `docs/superpowers/plans/`.
- Modify only within the approved Sprint scope.

**Interfaces:**
- Consumes: Sprint 5 evidence and stable price/order contracts.
- Produces: sequentially testable delivery slices.

- [ ] Sprint 6 先冻结审核、截单、取消、库存预占/释放和异常订单状态。
- [ ] Sprint 7 深化采购询价、比价、在途和进度，但保留多次收货、少收关闭和来源追溯。
- [ ] Sprint 8 建立批次、库位、盘点、报损报溢和移动加权平均成本。
- [ ] Sprint 9 新建分拣、称重、缺货、容差、标签和人工更正审计。
- [ ] Sprint 10 完成出库、排线、配送、部分签收、拒收和异常。
- [ ] Sprint 11 完成应收应付、核销、对账和利润快照。
- [ ] Sprint 12 最后建设损耗、BI 下钻、溯源和受控 Open API。

## External Reference Handling

- 蔬东坡帮助中心可作为流程和信息架构参考，尤其是订单、采购、分拣称重、库存、配送、财务、商城和溯源的链路；其应用介绍不等于已验证行为。
- `https://www.yuque.com/sdongpo/news` 当前检索无法安全打开，不能把其中内容列为已确认规则；后续需由用户提供可访问页面、导出或截图后再补证据。
- 外部产品中的加工、积分、周转筐、完整 CRM、税务/会计凭证、AI 录单和复杂装修器不自动进入本项目范围。

## Completion Criteria

- 复用矩阵经评审，主工作区仍在原分支且无非计划文件变更。
- Sprint 5 业务规格已批准，价格来源、客户可见性、订单快照和重复提交语义可解释。
- 后端、Web、小程序各自门禁有真实输出；Playwright 关键主链路完成或明确记录阻塞。
- 只有达到上述基线后，才开始 Sprint 6 的库存预占；不以“代码已存在”替代端到端验收。
