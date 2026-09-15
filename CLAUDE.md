# CLAUDE.md

This file provides guidance to coding agents working with this repository.

> **V2 baseline notice (2026-09-14).** This repository is migrating to **SmartAdmin V2**.
> - The system foundation is **SmartAdmin v3.31** (login/auth/user/employee/department/role/menu/permission/data-scope/log/dict/file/unified exception/unified response/frontend Layout and system pages).
> - `xsy-scm-server/` and `xsy-scm-web/` are the **official root V2 workspaces** (backend and admin frontend).
> - The current implementation remains under `v2/xsy-scm-v2-server` and `v2/xsy-scm-v2-web` only as a migration source until it is moved into the root workspaces.
> - `xsy-scm-miniapp/` remains **LEGACY and frozen read-only**. Do not add features or fix defects there.
> - Only SCM supply-chain business is migrated. Legacy `auth`/`system` modules are **not** migrated.
> - See [`SMARTADMIN_REFERENCE_RULES.md`](./SMARTADMIN_REFERENCE_RULES.md) and [`docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md`](./docs/architecture/2026-09-14-smartadmin-v2-迁移审计报告.md).

## Current scope

The pre-V2 checkout (the files formerly held in root `xsy-scm-server` + `xsy-scm-web`) implemented the Sprint 1 product catalog,
Sprint 2 customer-pricing/sales-order, and Sprint 3 supplier/purchasing/receiving/inventory vertical
slices of 鲜蔬源智慧供应链管理平台 as a modular monolith.

V2 re-implements that supply-chain business on the SmartAdmin foundation, wave by wave:

```text
W0  baseline (rules, transitional v2 workspace, clean baseline, Java 21, PG conversion, Flyway, Sa-Token, Vue3)
W1  product pilot
W2  customer + supplier
W3  order + inventory
W4  purchase
W5  mall (+ marketing, re-evaluated)
W6  mini program (uni-app Vue3)
```

`marketing` is **DEFERRED**: not deleted, not migrated during the Product Pilot.

## Commands

### V2 backend — `xsy-scm-server`

Maven multi-module (`sa-base` + `sa-admin`). There is no Maven wrapper; use the installed Maven.

```bash
# Build (Git Bash: direct `mvn` fails, use the classworlds launcher)
cd "xsy-scm-server" && java \
  -classpath "D:/Maven/apache-maven-3.9.16/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="D:/Maven/apache-maven-3.9.16/bin/m2.conf" \
  -Dmaven.home="D:/Maven/apache-maven-3.9.16" \
  -Dmaven.repo.local="D:/Maven/repository" \
  org.codehaus.plexus.classworlds.launcher.Launcher -B -DskipTests compile

# Run
... Launcher spring-boot:run -pl sa-admin
```

Runtime database config comes from environment variables:

```text
XSY_V2_DB_URL        default jdbc:p6spy:postgresql://127.0.0.1:15432/xsy_scm?currentSchema=xsy_v2
XSY_V2_DB_USERNAME   default xsy_scm_app
XSY_V2_DB_PASSWORD   (required)
XSY_V2_SERVER_PORT   default 18080 for the dev profile (see note below)
XSY_V2_UPLOAD_PATH   default xsy-scm-server/.runtime/upload/
XSY_V2_LOG_DIR       default xsy-scm-server/.runtime/logs/
```

- PostgreSQL is on port **15432** (not 5432). V2 uses a dedicated schema **`xsy_v2`** in database `xsy_scm`.
- Redis is required (Sa-Token session store).
- Flyway runs on startup; migrations live in `sa-admin/src/main/resources/db/migration/`.

**Port note.** SmartAdmin's upstream default is `1024`, but on this machine Windows' dynamic
port range starts at 1024 (`netsh int ipv4 show dynamicport tcp` → start 1024, count 13977),
so 1024 / 8080 / 8081 are all inside the ephemeral range and fail to bind with
`Port 1024 was already in use`. The **dev** profile therefore defaults to **18080**
(outside the dynamic range, overridable via `XSY_V2_SERVER_PORT`). `pre` / `test` / `prod`
keep their upstream values.

### V2 frontend — `xsy-scm-web`

```bash
cd xsy-scm-web
npm install
npm run dev      # dev server on port 8081
npm run build
```

### Transitional verification source

Until the move is complete, historical W1–W3 verification commands and artifacts refer to
`v2/xsy-scm-v2-server` and `v2/xsy-scm-v2-web`. After the move, run the same commands from the
root `xsy-scm-server` and `xsy-scm-web` workspaces. The only remaining frozen legacy application
directory is `xsy-scm-miniapp`.

## Architecture and data flow (V2)

```text
Browser
  -> Vue3 + Vite admin UI (xsy-scm-web)
  -> Axios client
  -> Sa-Token authenticated Spring MVC controllers (net.lab1024.sa.admin.module.scm.*)
  -> service / manager
  -> MyBatis-Plus mappers + mapper XML
  -> PostgreSQL (schema xsy_v2)
```

- **Unified response** is SmartAdmin `ResponseDTO{ code, level, msg, ok, data, dataType }` with `OK_CODE = 0`.
  Legacy `{ code, message, data }` and `PageData` are **not** supported in V2.
- **Pagination** is `PageParam` → `PageResult{ pageNum, pageSize, total, pages, list, emptyFlag }`.
- **Auth** is Sa-Token: `Authorization: Bearer <token>`, loginId `"<userType>:<employeeId>"`, state in Redis.
  Method authorization via `@SaCheckPermission("scm:<domain>:<action>")`; buttons via `v-privilege`.
- SCM business code lives under `net.lab1024.sa.admin.module.scm.<domain>` with SmartAdmin layering
  (`controller / service / manager / dao / constant / domain/{entity,form,vo,dto}`).
  Do not introduce global controller/service/mapper/entity package trees.
- Flyway migrations are the only schema-evolution mechanism. **Never edit an already-applied migration.**

## Domain and persistence invariants (business assets — must be preserved in V2)

- An SPU is the aggregate root; an SKU is the trading unit used by orders, pricing, purchasing, inventory, and weighing flows.
- Every SPU has at least one SKU and exactly one default SKU.
- Products must reference an enabled third-level category.
- During edits, preserve IDs and versions for retained SPUs/SKUs. Insert new SKUs, soft-delete removed SKUs, and never rebuild all SKUs by delete-and-recreate (**SKU differential sync**).
- Carry optimistic-lock `version` values through DTOs and frontend payloads. HTTP 409 represents a concurrency conflict and receives dedicated UI handling.
- Soft deletion is the default. Active-only **partial unique indexes** allow business codes to be reused after deletion.
- This project deliberately uses **no database foreign keys**. Enforce relationships in services/transactions and retain appropriate constraints and indexes.
- Prices cross the frontend boundary as decimal strings, use `BigDecimal` in Java, and use `NUMERIC(18,4)` in PostgreSQL. Never use floating point for money.
- **`null`/`UNPRICED` must be strictly distinguished from `0` yuan.** SmartAdmin's `BigDecimalNullZeroSerializer` must not be applied to SCM payloads; an automated test guards this.
- SKU specification snapshots are PostgreSQL `JSONB`, represented as `Map<String, String>` in Java.
- Customer visibility is either `ALL_ENABLED` or `ALLOWLIST`; agreement-price intervals are half-open and must not overlap for the same customer/SKU (`CustomerPriceResolver`).
- Order prices are server-resolved and locked at submit. Standard actual quantity equals ordered quantity; non-standard actual quantity must be recorded while pending before confirmation.
- `DRAFT`/`PENDING` orders may be cancelled with a reason; `CONFIRMED`/`CANCELLED` are terminal for editing. Use explicit command endpoints and require `Idempotency-Key` on document creation and transitions (`idempotency_record`).
- Pending and approved returns reserve against confirmed actual quantity. Return approval creates one pending refund from locked prices; refunds currently record state only and do not execute payment or inventory movement.
- A purchase order may be received **incrementally** (multiple immutable confirmation batches). Do not assume a purchase order can only be received once; do not assume the receipt document is immutable after the first confirmation.
- Standard SKUs post `receivedQuantity`; non-standard SKUs retain declared quantity separately and post manually confirmed `actualWeight`.
- Putaway supports **DIRECT** and **DEFERRED** modes.
- Receipt confirmation, purchase progress, inventory balance, `PURCHASE_IN` movement, operation log, and idempotency completion commit in one transaction. Inventory writes participate in the caller transaction; do not add asynchronous posting or a generic balance mutation endpoint.
- Inventory balance grain is warehouse + SKU. Each movement records exact before/change/after quantities and source confirmation identity; **inventory movements are append-only** (enforced by trigger).
- Persist operation logs append-only, and use PostgreSQL sequences rather than `MAX + 1` for document numbers.
- Audit actor comes from `CurrentOperator.username()` (SmartAdmin Sa-Token session); never hardcode `"SYSTEM"` in new code.

## Error codes

Preserve existing in-use SCM business error code values (e.g. `40921`, `40933`, `40926`, `40963`,
`40970`, `40971`). Do not renumber them for tidiness. New V2 error codes use a planned unified SCM range.
Error semantics and their tests must not be lost.

## UI conventions (V2)

V2 admin UI is **SmartAdmin Vue3 native**: Layout, menu, tabs, tables, forms, modals, uploads and
permission directives all follow SmartAdmin's existing structure and components. The legacy React
dual-level sidebar layout is **abolished**. Do not mix legacy Admin/Screen/Mall React themes into V2 pages.
Legacy React pages are a reference for **fields and interaction only** — never translate them mechanically.

## Documentation precedence

For non-trivial changes, use this order:

1. The current user request.
2. `AGENTS.md`.
3. `SMARTADMIN_REFERENCE_RULES.md`.
4. The relevant file under `docs/` (Sprint-specific specs override older design drafts where they differ).
5. Legacy implementation and tests (business semantics and behaviour contract reference).
6. SmartAdmin framework conventions.

Treat the broader architecture documents as a roadmap, not evidence that planned modules already exist.
Keep the modular monolith simple and testable; do not introduce microservices, Kafka, Kubernetes,
distributed transactions, a search engine, or complex event infrastructure without an explicit
demonstrated requirement.
