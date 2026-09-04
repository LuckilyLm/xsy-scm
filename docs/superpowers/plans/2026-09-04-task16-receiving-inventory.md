# Task 16 Receiving and Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement multi-receipt purchasing with manual non-standard weight, atomic inventory balances, and append-only `PURCHASE_IN` movements.

**Architecture:** `PurchaseReceiptApplicationService` owns receipt commands and locks purchase order → purchase-order items → inventory rows. `InventoryApplicationService` exposes only a transaction-participating purchase-in command; query services expose typed read-only pages.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus/XML, PostgreSQL/Flyway, JUnit 5/AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md`

## Global Constraints

- One purchase order may have multiple confirmed receipts.
- Standard items use received quantity; non-standard items use confirmed manual actual weight as the effective inventory quantity.
- Strictly reject over-receipt; confirmed receipts and movements are immutable.
- Inventory balance and one movement per confirmed receipt line commit atomically.
- Lock order is purchase order → purchase-order items → inventory rows.
- No foreign keys, generic inventory update endpoint, batches, negative inventory, or formal cost accounting.

---

### Task 1: Define receipt and inventory persistence adapters

**Files:**
- Create: `purchase/entity/PurchaseReceiptEntity.java`, `PurchaseReceiptItemEntity.java`, `ReceiptWeighingRecordEntity.java`, status/source enums
- Create: purchase receipt mapper interfaces/XML
- Create: `inventory/entity/InventoryEntity.java`, `InventoryMovementEntity.java`, enums
- Create: inventory mapper interfaces/XML
- Modify: V8/V9 and `FlywayMigrationIT.java`

**Interfaces:**
- Produces: `selectActiveByIdForUpdate`, `selectActiveByOrderIdForUpdate`, `insertIfAbsent`, `selectByWarehouseAndSkuForUpdate`, and movement lookup by receipt item.

- [ ] **Step 1: Add failing migration/result-map tests** for all receipt/inventory tables, JSONB, numeric weight scale, indexes, sequences, and zero foreign keys.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=FlywayMigrationIT,PurchaseReceiptPersistenceIT test` **and confirm RED**.
- [ ] **Step 3: Implement entities and XML result maps**, keeping custom SQL out of annotations.
- [ ] **Step 4: Run** `mvn.cmd -q -Dtest=FlywayMigrationIT,PurchaseReceiptPersistenceIT test` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(inventory): add receipt and inventory persistence"`.

---

### Task 2: Implement receipt draft create, update, and detail

**Files:**
- Create: receipt DTOs/VOs, `PurchaseReceiptApplicationService.java`, `PurchaseReceiptQueryService.java`, controller
- Test: `PurchaseReceiptApplicationServiceTest.java`, `PurchaseReceiptControllerTest.java`

**Interfaces:**
- Produces: `POST/PUT/GET /api/purchase-receipts`, decimal-string response fields, retained receipt-line identity, and read-only confirmed detail.

- [ ] **Step 1: Add failing tests** for draft creation, multiple receipts per order, line ownership, retained versions, non-standard manual-weight reason, and confirmed-edit rejection.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=PurchaseReceiptApplicationServiceTest,PurchaseReceiptControllerTest test` **and confirm RED**.
- [ ] **Step 3: Implement request validation and server-owned snapshots**; reject client totals and derive warehouse/order snapshots from locked domain records.
- [ ] **Step 4: Run** `mvn.cmd -q -Dtest=PurchaseReceiptApplicationServiceTest,PurchaseReceiptControllerTest test` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(purchase): add receipt draft workflow"`.

---

### Task 3: Implement atomic confirmation and purchase-in movement

**Files:**
- Create: `inventory/service/InventoryApplicationService.java`, `InventoryErrorCodes.java`, `PurchaseInCommand.java`
- Modify: receipt application service and mappers
- Test: `PurchaseReceiptApplicationServiceTest.java`, `InventoryApplicationServiceTest.java`, `PurchaseReceiptInventoryIT.java`

**Interfaces:**
- Consumes: `PurchaseInCommand(receiptId, receiptItemId, warehouseId, skuId, quantity, unit, unitCost, snapshots)`.
- Produces: idempotent `POST /api/purchase-receipts/{id}/confirm` and one movement per confirmed receipt item.

- [ ] **Step 1: Add failing unit tests** for partial then complete receipt, standard/non-standard effective quantity, strict over-receipt, repeated confirm, fixed lock order, and movement creation.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=PurchaseReceiptApplicationServiceTest,InventoryApplicationServiceTest test` **and confirm RED**.
- [ ] **Step 3: Implement confirmation in one `@Transactional` method**; claim idempotency, lock order/items, validate all rows, ensure/lock inventory rows, apply balances, append movements, update purchase progress, confirm receipt, write audit, then complete idempotency.
- [ ] **Step 4: Use `INSERT ... ON CONFLICT (...) WHERE deleted = FALSE DO NOTHING` before inventory row locking** so concurrent first receipt does not leak a 500.
- [ ] **Step 5: Add PostgreSQL tests** for two concurrent receipts, first-inventory creation race, forced rollback, and exactly one movement on replay.
- [ ] **Step 6: Run** `mvn.cmd -q -Dtest=PurchaseReceiptApplicationServiceTest,InventoryApplicationServiceTest,PurchaseReceiptInventoryIT test` **and expect zero failures**.
- [ ] **Step 7: Commit** with `git commit -m "feat(inventory): confirm receipts atomically"`.

---

### Task 4: Add typed inventory and movement queries

**Files:**
- Create: inventory page DTOs/VOs/query service/controller
- Modify: inventory mapper XML
- Test: `InventoryQueryServiceTest.java`, `InventoryControllerTest.java`

**Interfaces:**
- Produces: paged `GET /api/inventories` and `GET /api/inventory-movements` with warehouse/SKU/source/date filters and decimal strings.

- [ ] **Step 1: Add failing pagination/filter/decimal-string tests**.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=InventoryQueryServiceTest,InventoryControllerTest test` **and confirm RED**.
- [ ] **Step 3: Implement read-only XML queries and typed responses**; expose no balance mutation endpoint.
- [ ] **Step 4: Run** `mvn.cmd -q -Dtest=InventoryQueryServiceTest,InventoryControllerTest test` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(inventory): expose balance and movement queries"`.

---

### Task 5: Verify Task 16 transaction invariants

- [ ] **Step 1: Run** `mvn.cmd -q -Dtest=PurchaseReceiptApplicationServiceTest,InventoryApplicationServiceTest,PurchaseReceiptControllerTest,InventoryControllerTest test`.
- [ ] **Step 2: Run** `mvn.cmd -q -Dtest=FlywayMigrationIT,PurchaseReceiptPersistenceIT,PurchaseReceiptInventoryIT test`.
- [ ] **Step 3: Query Surefire XML totals** and record failures/errors/skipped.
- [ ] **Step 4: Inspect transaction annotations and mapper lock SQL** against the fixed lock order.
- [ ] **Step 5: Run** `git diff --check`.
