# Task 17 Sprint 3 Web Implementation Plan

> **Status (2026-09-07):** The Admin supplier, warehouse, purchase-demand,
> purchase-order, receipt, inventory-balance, and inventory-movement routes are implemented in the current branch. The historical checkboxes below are retained as the original execution plan rather than rewritten as evidence. Frontend command results belong in `docs/superpowers/verification/2026-09-07-sprint-3-verification.md`. Task 6 remains open because no Sprint 3 Playwright main-chain spec is checked in.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Admin purchase, receipt, inventory, supplier, and warehouse workflows with typed APIs, conflict recovery, and repeat-submit protection.

**Architecture:** Domain API modules own Axios calls and decimal-string types; pure form/grouping models own business transformations; pages use TanStack Query plus Ant Design/ProComponents. All routes remain lazy and use the existing two-level Admin navigation.

**Tech Stack:** React 19, TypeScript 6, Vite, Ant Design 5, ProComponents, TanStack Query, Axios, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md`

## Global Constraints

- Use the Admin theme and `Tab/Title → Search → Toolbar → Table → Summary → Pagination` pattern.
- Use existing Axios, TanStack Query, Ant Design Form/ProComponents, `StatusTag`, `AmountText`, and project tokens.
- All decimals remain strings and every mutable aggregate carries version fields.
- Commands generate stable per-attempt idempotency keys, disable duplicate submission, and show dedicated 409 reload guidance.
- Confirmed receipts are read-only; inventory pages provide no edit action.

---

### Task 1: Add Sprint 3 API contracts and pure models

**Files:**
- Create: `src/types/purchase.ts`, `src/api/purchases.ts`, `src/api/inventory.ts`
- Create: `src/pages/purchase/purchaseOrderFormModel.ts`, `purchaseDemandGroupModel.ts`, `receiptFormModel.ts`
- Test: `src/api/purchases.test.ts`, `src/api/inventory.test.ts`, `src/pages/purchase/purchaseOrderFormModel.test.ts`, `purchaseDemandGroupModel.test.ts`, `receiptFormModel.test.ts`

**Interfaces:**
- Produces: typed page/detail/payload types, decimal-string validation, retained ID/version mapping, demand grouping, and standard/non-standard receipt payload conversion.

- [ ] **Step 1: Add failing model/API tests** for decimal strings, demand allocation identity, receipt effective quantities, versions, and idempotency headers.
- [ ] **Step 2: Run** `npx vitest run src/api/purchases.test.ts src/api/inventory.test.ts src/pages/purchase/purchaseOrderFormModel.test.ts src/pages/purchase/purchaseDemandGroupModel.test.ts src/pages/purchase/receiptFormModel.test.ts` **and confirm RED**.
- [ ] **Step 3: Implement minimal types, Axios functions, and pure immutable models** without `any` or unchecked casts.
- [ ] **Step 4: Run** `npx vitest run src/api/purchases.test.ts src/api/inventory.test.ts src/pages/purchase/purchaseOrderFormModel.test.ts src/pages/purchase/purchaseDemandGroupModel.test.ts src/pages/purchase/receiptFormModel.test.ts` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(web): add sprint 3 purchase api models"`.

---

### Task 2: Implement supplier, warehouse, and purchase-demand pages

**Files:**
- Create: supplier/warehouse settings pages and `PurchaseDemandPage.tsx`
- Modify: `navigation.tsx`, `router/index.tsx`, `StatusTag.tsx`
- Test: `src/pages/purchase/PurchaseDemandPage.test.tsx`, `src/pages/supplier/SupplierPage.test.tsx`, `WarehousePage.test.tsx`, `src/layouts/AdminLayout/AdminLayout.test.tsx`

**Interfaces:**
- Produces: `/warehouses/suppliers`, `/warehouses/settings`, `/purchases/demands`; demand filters and grouping preview.

- [ ] **Step 1: Add failing route/navigation/page tests** for deep links, loading, empty, error/retry, and 409.
- [ ] **Step 2: Run** `npx vitest run src/pages/purchase/PurchaseDemandPage.test.tsx src/pages/supplier/SupplierPage.test.tsx src/pages/supplier/WarehousePage.test.tsx src/layouts/AdminLayout/AdminLayout.test.tsx` **and confirm RED**.
- [ ] **Step 3: Implement high-density ProTable pages and Ant Design forms**, requiring explicit supplier/warehouse assignment.
- [ ] **Step 4: Run** `npx vitest run src/pages/purchase/PurchaseDemandPage.test.tsx src/pages/supplier/SupplierPage.test.tsx src/pages/supplier/WarehousePage.test.tsx src/layouts/AdminLayout/AdminLayout.test.tsx` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(web): add purchase demand and master data pages"`.

---

### Task 3: Implement purchase-order list, editor, and detail

**Files:**
- Create: `PurchaseOrderListPage.tsx`, `PurchaseOrderEditorPage.tsx`, `PurchaseOrderDetailPage.tsx`, module CSS
- Modify: router/navigation
- Test: `src/pages/purchase/PurchaseOrderListPage.test.tsx`, `PurchaseOrderEditorPage.test.tsx`, `PurchaseOrderDetailPage.test.tsx`, `purchaseOrderFormModel.test.ts`

**Interfaces:**
- Produces: `/purchases/orders`, `/new`, `/:id/edit`, `/:id`; detail shows snapshots, allocation sources, receipts, and logs.

- [ ] **Step 1: Add failing tests** for retained row identity, source-change rejection presentation, create/submit/cancel replay, read-only terminal states, and 409 reload.
- [ ] **Step 2: Run** `npx vitest run src/pages/purchase/PurchaseOrderListPage.test.tsx src/pages/purchase/PurchaseOrderEditorPage.test.tsx src/pages/purchase/PurchaseOrderDetailPage.test.tsx src/pages/purchase/purchaseOrderFormModel.test.ts` **and confirm RED**.
- [ ] **Step 3: Implement list and full-page editor with ProTable/Form**, server-derived totals, confirmation dialogs, and disabled duplicate commands.
- [ ] **Step 4: Run** `npx vitest run src/pages/purchase/PurchaseOrderListPage.test.tsx src/pages/purchase/PurchaseOrderEditorPage.test.tsx src/pages/purchase/PurchaseOrderDetailPage.test.tsx src/pages/purchase/purchaseOrderFormModel.test.ts` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(web): add purchase order workflow"`.

---

### Task 4: Implement receipt operation pages

**Files:**
- Create: receipt list/editor/detail pages and tests
- Modify: router/navigation/status labels
- Test: `src/pages/purchase/PurchaseReceiptListPage.test.tsx`, `PurchaseReceiptEditorPage.test.tsx`, `PurchaseReceiptDetailPage.test.tsx`, `receiptFormModel.test.ts`

**Interfaces:**
- Produces: `/purchases/receipts`, `/purchases/orders/:id/receipts/new`, `/purchases/receipts/:id`.

- [ ] **Step 1: Add failing tests** for two partial confirmation batches, non-standard manual weight/reason, remaining quantity, confirmation replay, read-only confirmed state, and 409.
- [ ] **Step 2: Run** `npx vitest run src/pages/purchase/PurchaseReceiptListPage.test.tsx src/pages/purchase/PurchaseReceiptEditorPage.test.tsx src/pages/purchase/PurchaseReceiptDetailPage.test.tsx src/pages/purchase/receiptFormModel.test.ts` **and confirm RED**.
- [ ] **Step 3: Implement the full-page operational receipt UI**, emphasizing non-standard weight and using string inputs rather than JavaScript numeric authority.
- [ ] **Step 4: Run** `npx vitest run src/pages/purchase/PurchaseReceiptListPage.test.tsx src/pages/purchase/PurchaseReceiptEditorPage.test.tsx src/pages/purchase/PurchaseReceiptDetailPage.test.tsx src/pages/purchase/receiptFormModel.test.ts` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(web): add purchase receipt workflow"`.

---

### Task 5: Implement inventory balance and movement pages

**Files:**
- Create: `InventoryPage.tsx`, `InventoryMovementPage.tsx` and tests
- Modify: router/navigation
- Test: `src/pages/inventory/InventoryPage.test.tsx`, `InventoryMovementPage.test.tsx`

**Interfaces:**
- Produces: `/warehouses/inventories` and `/warehouses/inventory-movements`, both read-only and paginated.

- [ ] **Step 1: Add failing tests** for filters, decimal rendering, receipt source links, empty/error/retry, and absence of mutation actions.
- [ ] **Step 2: Run** `npx vitest run src/pages/inventory/InventoryPage.test.tsx src/pages/inventory/InventoryMovementPage.test.tsx` **and confirm RED**.
- [ ] **Step 3: Implement ProTable pages using `AmountText`/tabular decimals and existing theme tokens**.
- [ ] **Step 4: Run** `npx vitest run src/pages/inventory/InventoryPage.test.tsx src/pages/inventory/InventoryMovementPage.test.tsx` **and expect pass**.
- [ ] **Step 5: Commit** with `git commit -m "feat(web): add inventory query pages"`.

---

### Task 6: Add the Sprint 3 browser acceptance flow

**Files:**
- Create: `e2e/sprint3-purchase-receiving-inventory.spec.ts`

- [ ] **Step 1: Write the E2E flow** confirming a sales order, generating demand, creating/submitting a purchase order, confirming two receipt batches, then checking balance and two movements.
- [ ] **Step 2: Run the test and confirm any failure reflects a real missing contract**, not selector timing.
- [ ] **Step 3: Fix only the exposed contract/UI issue and re-run**.
- [ ] **Step 4: Commit** with `git commit -m "test(web): cover sprint 3 purchase-in flow"`.
