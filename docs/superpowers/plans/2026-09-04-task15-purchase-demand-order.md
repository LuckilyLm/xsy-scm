# Task 15 Purchase Demand and Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the purchase-demand and purchase-order aggregate with replay-safe allocation, concurrency control, typed queries, atomic reconciliation, and real PostgreSQL evidence.

**Architecture:** Keep writes in `PurchaseDemandService` and `PurchaseOrderService`, queries in dedicated query services, and SQL in mapper XML. Serialize competing business operations by locking source sales-order rows and purchase-demand rows, while partial unique indexes remain the final race barrier and are translated to stable 409 errors.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis-Plus 3.5, PostgreSQL, Flyway, JUnit 5, AssertJ, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md`

## Global Constraints

- Do not create database foreign keys or mapper-annotation SQL.
- Quantities, prices, and amounts use `BigDecimal`/`NUMERIC(18,4)` internally and decimal strings at HTTP boundaries.
- Every write is transactional, version checked, auditable, and uses complete constructor injection without null fallbacks.
- Existing Sprint 1/2 behavior and user formatting must be preserved.
- Supplier and warehouse are explicitly supplied; no automatic supplier-selection rule is introduced.

---

### Task 0: Checkpoint Sprint 2 fixes and repository rules

**Files:**
- Modify: `AGENTS.md`
- Modify: `xsy-scm-web/src/pages/order/RefundCompleteModal.tsx`
- Modify: existing Sprint 2 files already changed in the working tree
- Test: `xsy-scm-web/src/pages/order/RefundCompleteModal.test.tsx`

**Interfaces:**
- Consumes: existing Sprint 2 API contracts.
- Produces: one clean `fix(sprint2)` commit that excludes every `purchase`, `receipt`, and `inventory` file.

- [ ] **Step 1: Run the focused Sprint 2 regression tests**

Run: `npx vitest run src/pages/order/RefundCompleteModal.test.tsx src/pages/order/orderFormModel.test.ts src/pages/order/returnApprovalModel.test.ts`

Expected: all tests pass without Ant Design deprecation warnings.

- [ ] **Step 2: Run frontend type and lint checks**

Run: `npm run typecheck`

Run: `npx eslint src/pages/order/RefundCompleteModal.tsx src/pages/order/RefundListPage.tsx src/pages/order/RefundDetailPage.tsx`

Expected: both commands exit 0.

- [ ] **Step 3: Stage only repository rules, formatting, and Sprint 2 repairs**

Stage tracked formatting/Sprint 2 files plus `RefundCompleteModal*`, `returnApprovalModel*`, agreement-price audit V7, and their tests. Explicitly exclude `docs/UI`, `.claude`, Sprint 3 spec/plan files, V8/V9, and all `supplier/purchase/inventory/receipt` files.

- [ ] **Step 4: Commit the checkpoint**

Run: `git commit -m "fix(sprint2): align workflows with updated project rules"`

Expected: commit succeeds without bypassing hooks.

---

### Task 1: Make allocation replay-safe and race-safe

**Files:**
- Modify: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/purchase/service/PurchaseDemandService.java`
- Modify: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/purchase/mapper/PurchaseDemandAllocationMapper.java`
- Modify: `xsy-scm-server/src/main/resources/mapper/purchase/PurchaseDemandAllocationMapper.xml`
- Modify: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/purchase/service/PurchaseDemandErrorCodes.java`
- Test: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/purchase/service/PurchaseDemandServiceTest.java`
- Test: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/purchase/service/PurchasePersistenceIT.java`

**Interfaces:**
- Consumes: `PurchaseDemandAllocationRequest(id, version, purchaseOrderItemId, quantity, supplierId, purchaserId, warehouseId)`.
- Produces: `PurchaseDemandAllocationMapper.selectActiveByOrderItemAndDemandForUpdate(long,long)` and a service result that treats exact replay as success and differing replay as `ALLOCATION_CONFLICT`/HTTP 409.

- [ ] **Step 1: Add failing unit tests for exact replay and conflicting replay**

Add assertions equivalent to:

```java
service.allocate(request("1.0000"));
service.allocate(request("1.0000"));
verify(allocations, times(1)).insert(any());
verify(demands, times(1)).updateById(any());

assertThatThrownBy(() -> service.allocate(request("2.0000")))
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("分配冲突");
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `mvn.cmd -q -Dtest=PurchaseDemandServiceTest test`

Expected: duplicate allocation currently inserts/updates twice or leaks a persistence exception.

- [ ] **Step 3: Implement locked replay comparison and exception translation**

Lock the demand first, then query the active pair with `FOR UPDATE`. Return the existing allocation when the normalized four-decimal quantity matches; throw `BusinessException(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT)` otherwise. Catch only the unique-index `DataIntegrityViolationException`, re-read the pair, and apply the same comparison; never expose the database exception.

- [ ] **Step 4: Add the PostgreSQL concurrent-allocation test**

Use two independently committed transactions synchronized by latches. Assert one active allocation, one demand increment, and either replay success or controlled 409 for the loser.

- [ ] **Step 5: Run unit and PostgreSQL tests**

Run: `mvn.cmd -q -Dtest=PurchaseDemandServiceTest,PurchasePersistenceIT test`

Expected: exact replay, conflict replay, and concurrency pass.

- [ ] **Step 6: Commit**

Run: `git commit -m "fix(purchase): make demand allocation replay safe"`

---

### Task 2: Protect demand generation across different idempotency keys

**Files:**
- Modify: `PurchaseDemandService.java`, `PurchaseDemandMapper.java`, `PurchaseDemandMapper.xml`, `PurchaseDemandErrorCodes.java`
- Test: `PurchaseDemandServiceTest.java`, `PurchasePersistenceIT.java`

**Interfaces:**
- Produces: `SalesOrderMapper.selectActiveByIdForUpdate(long)` use before item reads, and `PurchaseDemandMapper.selectActiveBySalesOrderItemIdForUpdate(long)` for business-key replay.

- [ ] **Step 1: Add a failing concurrent-generation test** that calls `generate` for the same confirmed order with two different keys and asserts one active demand per sales-order item and identical returned IDs.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=PurchaseDemandServiceTest,PurchasePersistenceIT test` **and confirm RED** from the source unique-index race.
- [ ] **Step 3: Lock the sales-order header and active items in stable ID order**, re-read each demand by source under lock, and translate a unique-index race by re-reading and returning the winner's ID.
- [ ] **Step 4: Run** `mvn.cmd -q -Dtest=PurchaseDemandServiceTest,PurchasePersistenceIT test` **and expect zero failures/errors**.
- [ ] **Step 5: Commit** with `git commit -m "fix(purchase): serialize demand generation by order source"`.

---

### Task 3: Make purchase-order creation and reconciliation atomic

**Files:**
- Modify: `PurchaseOrderService.java`, purchase order/allocation/demand mapper interfaces and XML
- Modify: `PurchaseOrderErrorCodes.java`
- Test: `PurchaseOrderServiceTest.java`, `PurchasePersistenceIT.java`

**Interfaces:**
- Produces: retained rows keep ID/version/SKU/demand; omitted rows call `releaseAllocation`; new rows call `allocateDemand`; all demand deltas are aggregated before writes.

- [ ] **Step 1: Add failing tests** for rollback after item/allocation failure, idempotent create replay, two lines sharing one demand, retained allocation identity, rejected SKU/source replacement, and omitted-row release.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=PurchaseOrderServiceTest test` **and confirm the intended failures**.
- [ ] **Step 3: Refactor reconciliation into explicit `inserted`, `retained`, and `removed` change sets**; lock demand IDs in ascending order, calculate net deltas once, reject over-allocation, then persist rows/allocations/demands.
- [ ] **Step 4: Ensure all create operations and `idempotency.complete` remain in the same `@Transactional` method** and remove any compatibility constructor or null guard.
- [ ] **Step 5: Add PostgreSQL rollback assertions** for counts in `purchase_order`, `purchase_order_item`, `purchase_demand_allocation`, `purchase_demand`, and `idempotency_record` after an injected failure.
- [ ] **Step 6: Run** `mvn.cmd -q -Dtest=PurchaseOrderServiceTest,PurchasePersistenceIT test`.
- [ ] **Step 7: Commit** with `git commit -m "fix(purchase): reconcile order allocations atomically"`.

---

### Task 4: Add typed purchase-demand and purchase-order queries

**Files:**
- Create: `purchase/dto/PurchaseDemandPageQuery.java`
- Create: `purchase/vo/PurchaseDemandResponse.java`, `PurchaseDemandGroupPreviewResponse.java`
- Create: `purchase/service/PurchaseDemandQueryService.java`, `PurchaseOrderQueryService.java`
- Create: `common/api/DecimalStrings.java`
- Modify: purchase controllers, existing response records, mapper XML
- Test: purchase controller tests

**Interfaces:**
- Produces: `DecimalStrings.format(BigDecimal)`, paged `GET /api/purchase-demands`, typed detail, `GET /api/purchase-demands/group-preview`, and typed purchase-order detail containing header, snapshots, allocations, demand snapshots, and logs.

- [ ] **Step 1: Add failing MockMvc tests** asserting `{records,page,pageSize,total}`, decimal strings, filters (`supplierId`, `warehouseId`, `skuId`, `status`), and complete typed detail.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=PurchaseDemandControllerTest,PurchaseOrderControllerTest test` **and confirm RED**.
- [ ] **Step 3: Implement query records and XML pagination/group aggregation** without returning entities from controllers.
- [ ] **Step 4: Convert every quantity/price/amount using `DecimalStrings.format(BigDecimal)`**, not Jackson numeric serialization; migrate the existing sales and after-sales converters to the same method.
- [ ] **Step 5: Run** `mvn.cmd -q -Dtest=PurchaseDemandControllerTest,PurchaseOrderControllerTest test` **and expect pass**.
- [ ] **Step 6: Commit** with `git commit -m "feat(purchase): expose typed demand and order queries"`.

---

### Task 5: Complete Task 15 integration evidence and review

**Files:**
- Modify: `FlywayMigrationIT.java`, `PurchasePersistenceIT.java`, V8/V9 only if tests expose schema defects

**Interfaces:**
- Produces: verified JSONB result maps, snapshot persistence, zero foreign keys, controlled uniqueness, optimistic locking, and rollback evidence.

- [ ] **Step 1: Extend integration tests** for non-null `spu_code_snapshot`, all three JSONB snapshots, allocation ownership, unique replay, optimistic demand update, and transaction rollback.
- [ ] **Step 2: Run** `mvn.cmd -q clean -Dtest=PurchaseOrderServiceTest,PurchaseDemandServiceTest,PurchaseOrderControllerTest,PurchaseDemandControllerTest test`.
- [ ] **Step 3: Run** `mvn.cmd -q -Dtest=FlywayMigrationIT,PurchasePersistenceIT test`.
- [ ] **Step 4: Read Surefire XML reports** and record exact tests/failures/errors/skipped.
- [ ] **Step 5: Run** `git diff --check` and a static scan proving no mapper annotation SQL or foreign keys.
- [ ] **Step 6: Re-review Task 15 against the spec**; do not mark complete while any listed invariant lacks evidence.
