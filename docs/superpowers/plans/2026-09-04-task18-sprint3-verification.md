# Task 18 Sprint 3 Verification and Documentation Plan

> **Status (2026-09-07):** Delivery documentation has been synchronized. Fresh command results and explicit unavailable checks are recorded in `docs/superpowers/verification/2026-09-07-sprint-3-verification.md`; the checklist below remains the original verification plan.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce fresh, reproducible evidence that Sprint 3 and retained Sprint 1/2 behavior compile, test, build, migrate, and pass the critical browser workflow.

**Architecture:** Verification is evidence-only: run clean backend/frontend gates, inspect reports rather than trusting console summaries, perform a final standards/spec review, and update documentation with exact results and explicit skips.

**Tech Stack:** Maven/Surefire, PostgreSQL/Flyway, npm/Vitest/ESLint/TypeScript/Vite, Playwright, Git.

**Spec:** `docs/superpowers/specs/2026-09-04-sprint-3-purchase-receiving-inventory-design.md`

## Global Constraints

- Do not claim completion from stale results.
- Do not hide failures, skip hooks, modify production data, or delete Docker volumes.
- Report exact test counts, failures, errors, skipped tests, and any unavailable verification.
- Preserve unrelated untracked user documents.

---

### Task 1: Run backend clean verification

- [ ] **Step 1:** `mvn.cmd -q clean -Dtest=PurchaseOrderServiceTest,PurchaseDemandServiceTest,PurchaseOrderControllerTest,PurchaseDemandControllerTest test`
- [ ] **Step 2:** `mvn.cmd -q -Dtest=FlywayMigrationIT,PurchasePersistenceIT,PurchaseReceiptPersistenceIT,PurchaseReceiptInventoryIT test`
- [ ] **Step 3:** `mvn.cmd -q test`
- [ ] **Step 4:** `mvn.cmd -q -DskipTests compile`
- [ ] **Step 5:** parse `target/surefire-reports/TEST-*.xml` and sum `tests`, `failures`, `errors`, and `skipped`.

Expected: every command exits 0; report totals match the files actually executed.

---

### Task 2: Run frontend verification

- [ ] **Step 1:** `npm run lint`
- [ ] **Step 2:** `npm run typecheck`
- [ ] **Step 3:** `npm run test`
- [ ] **Step 4:** `npm run build`

Expected: all commands exit 0 without new warnings attributable to Sprint 3.

---

### Task 3: Run browser and regression acceptance

- [ ] **Step 1:** start the verified local backend/frontend against the test PostgreSQL database.
- [ ] **Step 2:** `npm run e2e -- sprint3-purchase-receiving-inventory.spec.ts`.
- [ ] **Step 3:** run retained Sprint 1 product and Sprint 2 order/after-sales Playwright specs.
- [ ] **Step 4:** inspect browser console/network for errors and verify common desktop scrolling/loading/empty/error states.

Expected: the Sprint 3 main chain produces exactly two `PURCHASE_IN` movements after two receipts and replay does not add a third.

---

### Task 4: Perform final two-axis review

**Files:**
- Review fixed point: the Sprint 2 merge commit `006cbcb`
- Standards: `AGENTS.md`, project UI skill, repository conventions
- Spec: the frozen Sprint 3 design

- [ ] **Step 1:** inspect `git diff 006cbcb...HEAD` plus remaining working-tree changes.
- [ ] **Step 2:** review Standards and Spec independently, verifying every concurrency, transaction, audit, decimal-string, and UI invariant.
- [ ] **Step 3:** fix each confirmed finding with a focused red/green test and rerun affected gates.
- [ ] **Step 4:** run `git diff --check` and scan for secrets, mapper annotation SQL, foreign keys, null dependency constructors, and generated artifacts.

---

### Task 5: Update delivery documentation and finish the branch

**Files:**
- Modify: `README.md`, `CLAUDE.md`, `CONTEXT.md`
- Create: `docs/superpowers/verification/2026-09-04-sprint-3-verification.md`

- [ ] **Step 1: Record exact implemented scope and deferred rules** from the frozen decision table.
- [ ] **Step 2: Record commands and fresh counts** from backend, frontend, PostgreSQL, and Playwright verification.
- [ ] **Step 3: Record every skipped/unavailable item with reason**, without describing it as passed.
- [ ] **Step 4: Commit** with `git commit -m "docs(sprint3): record verification and delivery"`.
- [ ] **Step 5: Inspect final branch status and log**; do not push unless explicitly requested.
