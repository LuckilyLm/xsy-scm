# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

## Current scope

This checkout implements the Sprint 1 product catalog and Sprint 2 customer-pricing/sales-order vertical slices of 鲜蔬源智慧供应链管理平台. The working system is a modular monolith with two runnable projects:

- `xsy-scm-web/`: React 19, TypeScript, Vite, Ant Design/ProComponents, React Router, TanStack Query, and Axios.
- `xsy-scm-server/`: Java 21 Spring Boot REST API using Validation, MyBatis-Plus, Flyway, and PostgreSQL.

Implemented domains are product categories, the SPU/SKU aggregate, customer types/customers, customer-SKU visibility, agreement prices, sales orders, actual-weight settlement, supplementary orders, returns, refunds, command idempotency, and order operation logs. Authentication/RBAC, suppliers, purchasing, warehouses/inventory movement, real payment execution, device integration, Redis, Docker Compose, and CI remain deferred.

## Commands

### Backend

Run from `xsy-scm-server/`. There is no Maven wrapper; use the installed `mvn`/`mvn.cmd`.

```powershell
# Start the API (PowerShell example for selecting Java 21)
$env:JAVA_HOME='D:\Java\JDK21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd spring-boot:run

# Run all unit and PostgreSQL integration tests
mvn.cmd test

# Clean, test, and package
mvn.cmd clean verify

# Run one test class or one method
mvn.cmd -Dtest=ProductAggregateValidatorTest test
mvn.cmd -Dtest=ApiResponseTest#createsStandardSuccessEnvelope test

# Run selected classes
mvn.cmd -Dtest=ProductSkuChangeSetTest,ProductApplicationServiceIT test
```

Runtime database configuration comes from `XSY_DB_URL`, `XSY_DB_USERNAME`, and `XSY_DB_PASSWORD`; the server defaults to port `8080`. Integration tests (`*IT`) use a real PostgreSQL database through `XSY_TEST_DB_URL` plus the database username/password variables. A full `test` or `verify` therefore requires that database to be available.

### Frontend

Run from `xsy-scm-web/`:

```powershell
npm install
npm run dev

npm run lint
npm run typecheck
npm test
npm run build

# Run one Vitest file
npm test -- productFormModel.test.ts
npm test -- ProductDrawer.test.tsx
```

Vite listens on port `5173`, proxies `/api` to `http://127.0.0.1:8080`, and exposes product, customer, agreement-price, order, return, and refund routes.

### Browser acceptance

PostgreSQL and the backend must already be running. Playwright starts only the Vite frontend.

```powershell
cd xsy-scm-web
npx playwright install chromium
npm run e2e -- product-flow.spec.ts
npm run e2e -- sprint2-order-pricing.spec.ts
npm run e2e -- sprint2-after-sales.spec.ts
```

The product flow validates retained SKU identity, specification changes, shelf status, and soft deletion. Sprint 2 flows cover customer visibility and pricing through order settlement, then supplementary orders, partial returns, generated refunds, and over-return rejection. Order and after-sales documents are retained for audit rather than hard-deleted during cleanup.

## Architecture and data flow

```text
Browser
  -> React/Vite admin UI
  -> Axios client (`/api`)
  -> Spring MVC controllers
  -> application/query services
  -> MyBatis-Plus mappers + mapper XML
  -> PostgreSQL
```

- `/` redirects to `/products`; `AdminLayout` supplies route-aware primary and secondary sidebars for product, customer/pricing, order, return, and refund pages.
- Product editing uses `ProductPage`/`ProductDrawer`; sales orders use a full-page editor backed by immutable form-model helpers. TanStack Query is used for shared reads while table and command mutations explicitly refresh affected views.
- `src/api/http.ts` is the frontend HTTP boundary. The backend always returns `{ code, message, data }`; the Axios interceptor unwraps `data` and normalizes failures as `ApiError`.
- Backend code is grouped by domain under `com.xianshuyuan.scm`. Customer/pricing lives under `customer`; sales orders and after-sales live under `order`. Do not introduce global controller/service/mapper/entity package trees.
- Transactional application services own writes and state transitions; query services own paginated/detail reads. Aggregate validators and child change sets preserve retained row IDs/versions and reconcile inserts/removals without rebuilding collections.
- MyBatis-Plus handles straightforward CRUD, pagination, optimistic locking, and logical deletion. Put custom SQL and result mappings in `src/main/resources/mapper/`, not mapper annotations.
- Flyway migrations under `xsy-scm-server/src/main/resources/db/migration/` are the only schema-evolution mechanism. Never edit an already-applied migration.

## Domain and persistence invariants

- An SPU is the aggregate root; an SKU is the trading unit used by orders, pricing, purchasing, inventory, and weighing flows.
- Every SPU has at least one SKU and exactly one default SKU.
- Products must reference an enabled third-level category.
- During edits, preserve IDs and versions for retained SPUs/SKUs. Insert new SKUs, soft-delete removed SKUs, and never rebuild all SKUs by delete-and-recreate.
- Carry optimistic-lock `version` values through DTOs and frontend payloads. HTTP 409 represents a concurrency conflict and receives dedicated UI handling.
- Soft deletion is the default. Active-only partial unique indexes allow business codes to be reused after deletion.
- This project deliberately uses no database foreign keys. Enforce relationships in services/transactions and retain appropriate constraints and indexes.
- Prices cross the frontend boundary as decimal strings, use `BigDecimal` in Java, and use `NUMERIC(18,4)` in PostgreSQL. Never use floating point for money.
- SKU specification snapshots are PostgreSQL `JSONB`, represented as `Map<String, String>` in Java.
- Customer visibility is either `ALL_ENABLED` or `ALLOWLIST`; agreement-price intervals are half-open and must not overlap for the same customer/SKU.
- Order prices are server-resolved and locked at submit. Standard actual quantity equals ordered quantity; non-standard actual quantity must be recorded while pending before confirmation.
- `DRAFT`/`PENDING` orders may be cancelled with a reason; `CONFIRMED`/`CANCELLED` are terminal for editing. Use explicit command endpoints and require `Idempotency-Key` on document creation and transitions.
- Pending and approved returns reserve against confirmed actual quantity. Return approval creates one pending refund from locked prices; Sprint 2 records no inventory movement or payment execution.
- Persist order operation logs append-only with the temporary actor `SYSTEM`, and use PostgreSQL sequences rather than `MAX + 1` for document numbers.

## UI conventions

The current ERP interface uses a restrained, dense Admin theme. Preserve the header plus primary/secondary sidebar layout, centralized tokens in `src/styles/`, CSS Modules, dense tables, right-aligned/tabular money, consistent status tags, confirmations for destructive actions, and explicit loading/empty/error/retry states. Do not mix future Screen or Mall visual themes into Admin pages.

## Documentation precedence

For non-trivial changes, use this order:

1. The current user request.
2. `AGENTS.md`.
3. The relevant file under `docs/` (Sprint-specific specs override older design drafts where they differ).
4. Existing implementation.
5. Existing tests.
6. Framework conventions.

Treat the broader architecture documents as a roadmap, not evidence that planned modules already exist. Keep the modular monolith simple and testable; do not introduce microservices, Kafka, Kubernetes, distributed transactions, a search engine, or complex event infrastructure without an explicit demonstrated requirement.
