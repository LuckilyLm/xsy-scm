# Sprint 3 采购收货与库存验证记录

- 日期：2026-09-07
- 分支：`feature/sprint-3-purchase-inventory`
- 范围：当前工作区中的 Sprint 3 采购、收货、库存实现，以及保留的 Sprint 1/2 回归测试
- 结论：后端与前端自动化门禁通过；Sprint 3 浏览器主链路尚未验证

## 已交付能力

- 供应商和仓库基础资料。
- 从已确认销售订单生成、查询和分配采购需求。
- 采购订单创建、保留行身份的草稿编辑、提交、取消和查询。
- 一个采购订单最多一个活动收货单，一个收货单包含多个不可变确认批次。
- 标品数量收货与非标品人工实际重量收货。
- 严格超收限制和最大 200 字符收货确认幂等键。
- 收货确认、采购进度、库存余额、`PURCHASE_IN` 流水、日志和幂等结果的单事务提交。
- 仓库 + SKU 库存余额、确认来源唯一的入库流水以及数据库级追加只读保护。
- 采购、收货、库存管理端页面、API、类型、路由和导航。

## 后端验证

运行环境：Java 21、Maven、真实 PostgreSQL 测试数据库。

```powershell
cd xsy-scm-server
mvn.cmd clean verify
```

最近一次完整运行结果：

```text
Tests run: 189
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

本次后端覆盖包括单元测试和 PostgreSQL 集成测试，重点验证：

- 部分确认后最终收齐；
- 同键同请求重放不重复入库；
- 同键不同请求返回冲突；
- 严格超收拒绝；
- 收货事务晚期失败时采购和库存整体回滚；
- 库存写入必须参与既有事务；
- 并发争用同一剩余可收数量；
- 并发首次创建同一库存余额；
- 流水 `before + change = after`；
- Flyway 迁移、无外键、数量约束、幂等键长度和库存流水追加只读触发器。

## 前端验证

本次交付重新运行：

```powershell
cd xsy-scm-web
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

结果：

```text
lint: PASS
typecheck: PASS
Test Files: 13 passed (13)
Tests: 35 passed (35)
build: PASS
```

Vite 构建成功，共转换 9,940 个模块。构建报告保留一个既有提示：`LightWrapper` 产物压缩后约 540 kB，超过默认 500 kB 提示阈值；该提示不影响构建退出码，后续可单独评估代码分包。

## 未验证与延期项

- 当前仓库没有 `sprint3-purchase-receiving-inventory.spec.ts` 或等价 Sprint 3 Playwright 主链路，因此尚未完成“销售订单 → 采购需求 → 采购订单 → 两次收货确认 → 库存余额与两条流水”的浏览器验收。
- 本次未运行 Sprint 1/2 Playwright 回归；已有用例仍保留在 `xsy-scm-web/e2e/`。
- 登录/RBAC、真实支付、销售出库、库存预占/调整、采购退货和真实称重设备不属于本次范围。

## 交付审查

提交前执行并记录：

- `git diff --check`；
- 待提交文件和统计审查；
- 密钥、环境文件与生成物扫描；
- 确认 `project-reference-examples/**` 无修改；
- 本地提交后 `git status` 和 `git log -1` 检查。
