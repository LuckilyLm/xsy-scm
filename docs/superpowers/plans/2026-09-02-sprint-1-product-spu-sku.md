# Sprint 1 Product SPU/SKU Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real PostgreSQL-backed product archive vertical slice that manages category, SPU, and stable-identity SKU data through Spring Boot REST APIs and a React ProTable interface.

**Architecture:** Create a modular-monolith Spring Boot service and a separate React/Vite admin application. `ProductSpu` is the aggregate root; SKU writes occur only through the aggregate service in one transaction, while the UI renders one row per SPU with expandable SKU details.

**Tech Stack:** Java 21, Spring Boot 3.5.13, MyBatis-Plus Boot 3 Starter 3.5.17, Flyway, PostgreSQL 18, springdoc 2.8.17, React 19.2.8, Vite 8.2.2, TypeScript 6.0.3, Ant Design 5.29.3, ProComponents 2.8.10, TanStack Query 5.102.8, Axios 1.20.0, Vitest 4.1.11, Playwright 1.62.1.

**Spec:** `docs/superpowers/specs/2026-09-02-sprint-1-product-spu-sku-design.md`

## Global Constraints

- Keep the Sprint limited to product category and SPU/SKU management; do not add login, RBAC, Docker Compose, CI, supplier/warehouse pages, price systems, inventory, orders, or device integration.
- Every SPU must have at least one SKU and exactly one default SKU.
- Future order, price, purchase, inventory, and weighing records reference `sku_id`, never `spu_id` as a trading unit.
- Preserve SKU IDs during aggregate updates; do not delete and recreate retained SKUs.
- Use no database foreign-key constraints. Validate relationships in services and add explicit indexes.
- Do not write custom SQL in mapper annotations; keep mapper interfaces declarative and put custom SQL/result maps in mapper XML files.
- Use `NUMERIC(18,4)` / `BigDecimal` for unit prices and never floating-point values.
- Use PostgreSQL `JSONB` only as the Sprint 1 specification snapshot; do not add the full property-definition subsystem.
- Follow the Admin Theme and two-level sidebar. Project UI rules override generic design choices.
- Database credentials come only from environment variables and must never be committed or logged.
- Use test-first red-green-refactor for production behavior.

---

### Task 1: Bootstrap the backend and common API contract

**Files:**
- Create: `xsy-scm-server/pom.xml`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/XsyScmApplication.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/api/ApiResponse.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/api/PageData.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/exception/ErrorCode.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/exception/BusinessException.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/exception/GlobalExceptionHandler.java`
- Create: `xsy-scm-server/src/main/resources/application.yml`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/common/api/ApiResponseTest.java`

**Interfaces:**
- Produces: `ApiResponse<T>(int code, String message, T data)`, `ApiResponse.success(T)`, `PageData<T>(List<T> records, long page, long pageSize, long total)`.
- Produces: `BusinessException(ErrorCode)` mapped to a declared HTTP status without returning stack traces.

- [ ] **Step 1: Create Maven configuration and the failing response-contract test**

Use Spring Boot parent `3.5.13`, Java release `21`, `mybatis-plus-spring-boot3-starter` `3.5.17`, springdoc starter `2.8.17`, `flyway-core`, `flyway-database-postgresql`, PostgreSQL runtime driver, validation, web, and test starter. Write:

```java
@Test
void createsStandardSuccessEnvelope() {
    ApiResponse<String> response = ApiResponse.success("ok");
    assertThat(response).isEqualTo(new ApiResponse<>(0, "success", "ok"));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd xsy-scm-server && mvn.cmd -Dtest=ApiResponseTest test`

Expected: compilation fails because `ApiResponse` does not exist.

- [ ] **Step 3: Implement the common contract and exception mapping**

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }
}

public record PageData<T>(List<T> records, long page, long pageSize, long total) {}
```

Map validation errors to HTTP 400/code `40000`, business exceptions to their declared status/code, and unexpected exceptions to HTTP 500/code `50000` with message `系统内部错误`.

- [ ] **Step 4: Run the focused test**

Run: `mvn.cmd -Dtest=ApiResponseTest test`

Expected: one test passes with exit code `0`.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-server
git commit -m "feat(server): bootstrap common api contract"
```

---

### Task 2: Add the PostgreSQL schema and persistence configuration

**Files:**
- Create: `xsy-scm-server/src/main/resources/db/migration/V1__create_sprint1_schema.sql`
- Create: `xsy-scm-server/src/main/resources/db/migration/V2__seed_sprint1_demo_data.sql`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/persistence/MybatisPlusConfig.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/migration/FlywayMigrationIT.java`
- Create: `xsy-scm-server/src/test/resources/application-test.yml`

**Interfaces:**
- Produces tables: `sys_user`, `supplier`, `warehouse`, `product_category`, `product_spu`, `product_sku`.
- Produces environment variables: `XSY_DB_URL`, `XSY_DB_USERNAME`, `XSY_DB_PASSWORD`.

- [ ] **Step 1: Prepare an isolated real PostgreSQL test database**

Start only the existing local PostgreSQL service. Create databases `xsy_scm` and `xsy_scm_test` if absent, using credentials supplied through `XSY_DB_USERNAME` and `XSY_DB_PASSWORD`. Do not alter or remove any existing database.

- [ ] **Step 2: Write the failing migration integration test**

```java
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsSixTablesWithoutForeignKeys() {
        Integer tables = jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema='public' and table_name in
            ('sys_user','supplier','warehouse','product_category','product_spu','product_sku')
            """, Integer.class);
        Integer foreignKeys = jdbc.queryForObject("""
            select count(*) from information_schema.table_constraints
            where constraint_schema='public' and constraint_type='FOREIGN KEY'
            """, Integer.class);
        assertThat(tables).isEqualTo(6);
        assertThat(foreignKeys).isZero();
    }
}
```

- [ ] **Step 3: Run the migration test and verify RED**

Run: `mvn.cmd -Dtest=FlywayMigrationIT test`

Expected: failure because the Flyway migrations do not exist.

- [ ] **Step 4: Implement migrations and MyBatis configuration**

Create all fields/checks from the spec. Critical indexes:

```sql
create unique index uk_product_spu_code_active
    on product_spu (spu_code) where deleted = false;
create unique index uk_product_sku_code_active
    on product_sku (sku_code) where deleted = false;
create unique index uk_product_sku_barcode_active
    on product_sku (barcode) where deleted = false and barcode is not null;
create unique index uk_product_sku_default_active
    on product_sku (spu_id) where deleted = false and is_default = true;
create index idx_product_sku_spu_id on product_sku (spu_id);
```

Configure PostgreSQL pagination and optimistic locking. Seed a three-level category and one SPU with two SKU rows using deterministic codes, not fixed identity IDs.

- [ ] **Step 5: Run the integration test**

Run: `mvn.cmd -Dtest=FlywayMigrationIT test`

Expected: test passes; two Flyway migrations are applied and no foreign keys exist.

- [ ] **Step 6: Commit**

```powershell
git add -- xsy-scm-server/src/main/resources xsy-scm-server/src/main/java/com/xianshuyuan/scm/common/persistence xsy-scm-server/src/test
git commit -m "feat(server): add sprint 1 postgres schema"
```

---

### Task 3: Implement product-category domain and API

**Files:**
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/entity/ProductCategoryEntity.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/mapper/ProductCategoryMapper.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/dto/ProductCategorySaveRequest.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/vo/ProductCategoryTreeNode.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/service/ProductCategoryService.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/controller/ProductCategoryController.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductCategoryServiceTest.java`

**Interfaces:**
- Produces: `List<ProductCategoryTreeNode> getTree()`.
- Produces: `long create(ProductCategorySaveRequest)`, `void update(long, ProductCategorySaveRequest)`, `void delete(long)`.
- Produces endpoints under `/api/product-categories` from the spec.

- [ ] **Step 1: Write category-validation tests**

Cover root level 1, child level derived as parent plus one, rejected fourth level, selectable enabled level 3, and delete rejection for active children/SPUs.

```java
@Test
void rejectsFourthLevelCategory() {
    given(mapper.selectById(30L)).willReturn(category(30L, 3));
    assertThatThrownBy(() -> service.create(request(30L)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("三级");
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn.cmd -Dtest=ProductCategoryServiceTest test`

Expected: compilation fails because the category service does not exist.

- [ ] **Step 3: Implement category service, tree assembly, and controller**

Sort siblings by `sortOrder`, then `id`. Derive level from the parent rather than trusting the request. Run service checks before mapper writes. Return `children: []`, never `null`.

- [ ] **Step 4: Run focused tests**

Run: `mvn.cmd -Dtest=ProductCategoryServiceTest test`

Expected: all category tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-server/src/main/java/com/xianshuyuan/scm/product xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductCategoryServiceTest.java
git commit -m "feat(product): add category api"
```

---

### Task 4: Define and validate the SPU/SKU aggregate

**Files:**
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/entity/ProductSpuEntity.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/entity/ProductSkuEntity.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/dto/ProductSaveRequest.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/dto/ProductSkuSaveRequest.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/service/ProductAggregateValidator.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductAggregateValidatorTest.java`

**Interfaces:**
- Produces: `void validate(ProductSaveRequest request)`.
- Produces enums: `ProductType { STANDARD, NON_STANDARD }`, `ShelfStatus { ON_SHELF, OFF_SHELF }`.
- `ProductSkuSaveRequest` contains nullable `id`, nullable `version`, `skuCode`, nullable `barcode`, `specName`, `Map<String,String> specValues`, `saleUnit`, `productType`, `marketPrice`, `status`, `defaultSku`, and `sortOrder`.

- [ ] **Step 1: Write aggregate invariant tests**

```java
@ParameterizedTest
@MethodSource("invalidSkuSets")
void rejectsInvalidSkuSets(List<ProductSkuSaveRequest> skus, String message) {
    ProductSaveRequest request = validProduct(skus);
    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(message);
}
```

Cases: empty list, zero defaults, two defaults, duplicate SKU codes, duplicate nonblank barcodes, negative price, blank unit, duplicate normalized specification maps.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn.cmd -Dtest=ProductAggregateValidatorTest test`

Expected: compilation fails because validator/request types do not exist.

- [ ] **Step 3: Implement validation and JSONB-safe entities**

Normalize codes with trim and uppercase; normalize blank barcodes to `null`; retain specification display text casing. Store `specValues` through a Jackson JSON type handler supported by MyBatis-Plus.

- [ ] **Step 4: Run focused tests**

Run: `mvn.cmd -Dtest=ProductAggregateValidatorTest test`

Expected: all invariant cases pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-server/src/main/java/com/xianshuyuan/scm/product xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductAggregateValidatorTest.java
git commit -m "feat(product): define spu sku aggregate"
```

---

### Task 5: Implement stable-ID aggregate persistence

**Files:**
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/mapper/ProductSpuMapper.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/mapper/ProductSkuMapper.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/service/ProductApplicationService.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/service/ProductSkuChangeSet.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/converter/ProductConverter.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductSkuChangeSetTest.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service/ProductApplicationServiceIT.java`

**Interfaces:**
- Produces: `long create(ProductSaveRequest request)`, `void update(long, ProductSaveRequest)`.
- Produces: `void updateStatus(long, int, ShelfStatus)` and `void delete(long, int)`.
- Produces: `ProductSkuChangeSet(inserted, updated, removedIds)` where retained requests keep IDs.

- [ ] **Step 1: Write pure change-set tests**

```java
@Test
void retainsExistingSkuIdsAndSeparatesInsertAndRemoval() {
    ProductSkuChangeSet result = ProductSkuChangeSet.between(
        List.of(existingSku(11L), existingSku(12L)),
        List.of(updateSku(11L), newSku("SKU-NEW")));
    assertThat(result.updated()).extracting(ProductSkuEntity::getId).containsExactly(11L);
    assertThat(result.inserted()).hasSize(1);
    assertThat(result.removedIds()).containsExactly(12L);
}
```

- [ ] **Step 2: Run and verify RED**

Run: `mvn.cmd -Dtest=ProductSkuChangeSetTest test`

Expected: compilation fails because the change-set type does not exist.

- [ ] **Step 3: Implement the pure difference algorithm**

Reject request IDs not present under the current SPU. Do not match by mutable specification text or list position. IDs are the only update identity; a `null` ID means insert.

- [ ] **Step 4: Write the failing PostgreSQL service integration test**

Create a SPU with two SKUs, update one retained SKU, remove the other, and add a third. Assert retained ID stability, removed-row soft deletion, a new ID for the insert, and transaction rollback when no default remains.

- [ ] **Step 5: Run and verify RED**

Run: `mvn.cmd -Dtest=ProductApplicationServiceIT test`

Expected: failure because aggregate persistence is absent.

- [ ] **Step 6: Implement transactional mutations**

Use `@Transactional(rollbackFor = Exception.class)`. Validate category, aggregate, ownership, uniqueness, and optimistic row counts. Soft-delete SPU/SKUs together. Log operation, aggregate ID, before/after summary, and operator `SYSTEM` without full request bodies.

- [ ] **Step 7: Run unit and integration tests**

Run: `mvn.cmd -Dtest=ProductSkuChangeSetTest,ProductApplicationServiceIT test`

Expected: both test classes pass.

- [ ] **Step 8: Commit**

```powershell
git add -- xsy-scm-server/src/main/java/com/xianshuyuan/scm/product xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/service
git commit -m "feat(product): persist stable sku aggregates"
```

---

### Task 6: Expose product query and mutation APIs

**Files:**
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/dto/ProductPageQuery.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/dto/ProductStatusRequest.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/vo/ProductSummaryResponse.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/vo/ProductDetailResponse.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/vo/ProductSkuResponse.java`
- Create: `xsy-scm-server/src/main/java/com/xianshuyuan/scm/product/controller/ProductController.java`
- Create: `xsy-scm-server/src/test/java/com/xianshuyuan/scm/product/controller/ProductControllerTest.java`

**Interfaces:**
- Produces all `/api/products` endpoints from the spec.
- Summary contains `id`, `version`, `spuCode`, `name`, `alias`, category path, default SKU, `skuCount`, price range, status, and `updatedAt`.

- [ ] **Step 1: Write failing MockMvc contract tests**

Cover pagination envelope, invalid `pageSize=0`, create with no SKUs, not found, duplicate code, optimistic conflict, and sanitized unexpected error.

```java
mockMvc.perform(get("/api/products").param("page", "1").param("pageSize", "20"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value(0))
    .andExpect(jsonPath("$.data.records").isArray())
    .andExpect(jsonPath("$.data.pageSize").value(20));
```

- [ ] **Step 2: Run and verify RED**

Run: `mvn.cmd -Dtest=ProductControllerTest test`

Expected: endpoint assertions return 404 or fail to compile.

- [ ] **Step 3: Implement controller, projection, and OpenAPI annotations**

Use one bounded query for paged SPUs and one bounded `IN` query for all SKUs in that page; avoid N+1. Derive price range/default SKU without persisting summary values. Cap `pageSize` at 100.

- [ ] **Step 4: Run the backend suite**

Run: `mvn.cmd test`

Expected: all backend tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-server
git commit -m "feat(product): expose spu sku rest api"
```

---

### Task 7: Bootstrap the React application and AdminLayout

**Files:**
- Create: `xsy-scm-web/package.json`
- Create: `xsy-scm-web/vite.config.ts`
- Create: `xsy-scm-web/tsconfig.json`
- Create: `xsy-scm-web/eslint.config.js`
- Create: `xsy-scm-web/src/main.tsx`
- Create: `xsy-scm-web/src/router/index.tsx`
- Create: `xsy-scm-web/src/styles/tokens.ts`
- Create: `xsy-scm-web/src/styles/admin-theme.ts`
- Create: `xsy-scm-web/src/styles/global.css`
- Create: `xsy-scm-web/src/layouts/AdminLayout/index.tsx`
- Create: `xsy-scm-web/src/layouts/AdminLayout/AdminLayout.module.css`
- Create: `xsy-scm-web/src/test/setup.ts`
- Create: `xsy-scm-web/src/layouts/AdminLayout/AdminLayout.test.tsx`

**Interfaces:**
- Produces route `/products` rendered within `AdminLayout`.
- Produces `adminTheme` consumed by the root Ant Design `ConfigProvider`.

- [ ] **Step 1: Create package configuration and a failing layout test**

Pin the compatible versions from the plan header. Define scripts `dev`, `lint`, `typecheck`, `test`, `build`, and `e2e`.

```tsx
it('renders the two-level product navigation', () => {
  render(<MemoryRouter><AdminLayout /></MemoryRouter>);
  expect(screen.getByRole('navigation', { name: '一级导航' })).toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '商品二级导航' })).toBeInTheDocument();
  expect(screen.getByText('商品档案')).toBeInTheDocument();
});
```

- [ ] **Step 2: Install dependencies and verify RED**

Run: `cd xsy-scm-web && npm install && npm test -- AdminLayout.test.tsx`

Expected: compilation fails because `AdminLayout` does not exist.

- [ ] **Step 3: Implement theme and layout**

Use primary `#00B96B`, layout background `#F5F7F9`, sidebar `#202631`, header 56px, primary sidebar 80px, secondary sidebar 140px, and page padding 24px. Do not create out-of-scope pages.

- [ ] **Step 4: Run focused test and typecheck**

Run: `npm test -- AdminLayout.test.tsx && npm run typecheck`

Expected: layout test and typecheck pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-web
git commit -m "feat(web): bootstrap admin product layout"
```

---

### Task 8: Add typed product API client and SKU form model

**Files:**
- Create: `xsy-scm-web/src/api/http.ts`
- Create: `xsy-scm-web/src/api/products.ts`
- Create: `xsy-scm-web/src/types/product.ts`
- Create: `xsy-scm-web/src/pages/product/productFormModel.ts`
- Create: `xsy-scm-web/src/pages/product/productFormModel.test.ts`

**Interfaces:**
- Produces: `fetchProducts`, `fetchProduct`, `createProduct`, `updateProduct`, `updateProductStatus`, `deleteProduct`, `fetchCategoryTree`.
- Produces: `createEmptyProductForm()`, `normalizeProductPayload(form)`, `removeSku(form, key)`, `selectDefaultSku(form, key)`.

- [ ] **Step 1: Write failing form-model tests**

```ts
it('creates exactly one default SKU', () => {
  const form = createEmptyProductForm();
  expect(form.skus).toHaveLength(1);
  expect(form.skus[0]).toMatchObject({ specName: '默认规格', defaultSku: true });
});

it('does not remove the final SKU', () => {
  const form = createEmptyProductForm();
  expect(() => removeSku(form, form.skus[0].key)).toThrow('至少保留一个 SKU');
});
```

- [ ] **Step 2: Run and verify RED**

Run: `npm test -- productFormModel.test.ts`

Expected: compilation fails because form-model functions do not exist.

- [ ] **Step 3: Implement types, Axios interceptor, and pure transformations**

Unwrap only `{code:0,data}`. Nonzero responses throw `ApiError` with the backend code and user-safe message. Do not duplicate server state into Zustand.

- [ ] **Step 4: Run tests and typecheck**

Run: `npm test -- productFormModel.test.ts && npm run typecheck`

Expected: all form-model tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-web/src/api xsy-scm-web/src/types xsy-scm-web/src/pages/product/productFormModel*
git commit -m "feat(web): add typed product api model"
```

---

### Task 9: Implement the SPU list with expandable SKU rows

**Files:**
- Create: `xsy-scm-web/src/pages/product/ProductPage.tsx`
- Create: `xsy-scm-web/src/pages/product/ProductPage.module.css`
- Create: `xsy-scm-web/src/pages/product/ProductPage.test.tsx`
- Create: `xsy-scm-web/src/components/common/PageContainer.tsx`
- Create: `xsy-scm-web/src/components/common/StatusTag.tsx`
- Create: `xsy-scm-web/src/components/common/AmountText.tsx`
- Modify: `xsy-scm-web/src/router/index.tsx`

**Interfaces:**
- Consumes typed API functions and `ProductSummary` from Task 8.
- Produces one ProTable row per SPU and an expanded nested SKU table.

- [ ] **Step 1: Write failing page behavior tests**

Stub only the typed API module with `vi.mock`. Assert that two SPUs render as two main rows, expanding reveals SKU codes, filters map to API query names, failure renders retry, and delete opens confirmation.

- [ ] **Step 2: Run and verify RED**

Run: `npm test -- ProductPage.test.tsx`

Expected: compilation fails because `ProductPage` does not exist.

- [ ] **Step 3: Implement product page and reusable display atoms**

Use ProTable request parameters without duplicate pagination state. Left-align names, right-align price/counts, center status, and fix actions right. Format decimal strings directly as `¥ 1,280.5000`; do not convert through JavaScript `Number` where precision could be lost.

- [ ] **Step 4: Run focused tests, lint, and typecheck**

Run: `npm test -- ProductPage.test.tsx && npm run lint && npm run typecheck`

Expected: page tests, lint, and typecheck pass.

- [ ] **Step 5: Commit**

```powershell
git add -- xsy-scm-web/src
git commit -m "feat(web): add expandable spu product table"
```

---

### Task 10: Implement the SPU/SKU DrawerForm mutations

**Files:**
- Create: `xsy-scm-web/src/pages/product/ProductDrawer.tsx`
- Create: `xsy-scm-web/src/pages/product/SkuEditableTable.tsx`
- Create: `xsy-scm-web/src/pages/product/ProductDrawer.test.tsx`
- Modify: `xsy-scm-web/src/pages/product/ProductPage.tsx`

**Interfaces:**
- Consumes form-model functions and mutation API clients from Task 8.
- Produces a create/edit drawer with one SPU section and one editable SKU section.

- [ ] **Step 1: Write failing create/edit interaction tests**

Cover automatic default SKU, adding a SKU, changing the sole default, rejecting removal of the final SKU, preserving existing IDs during edit submit, disabling submit while pending, and resetting after success.

```tsx
await user.click(screen.getByRole('button', { name: '新增商品' }));
expect(screen.getAllByLabelText('SKU 编码')).toHaveLength(1);
await user.click(screen.getByRole('button', { name: '新增 SKU' }));
expect(screen.getAllByLabelText('SKU 编码')).toHaveLength(2);
```

- [ ] **Step 2: Run and verify RED**

Run: `npm test -- ProductDrawer.test.tsx`

Expected: compilation fails because drawer components do not exist.

- [ ] **Step 3: Implement DrawerForm and editable SKU rows**

Use a cascader restricted to enabled level-3 categories. Represent `specValues` as repeatable key/value inputs converted to JSON at submit. Keep stable database `id`/`version` hidden for existing SKUs. Use one radio group for default SKU and Popconfirm for removal.

- [ ] **Step 4: Connect mutations and invalidation**

On success, close/reset and invalidate only product list/detail query keys. On 409, keep the drawer open and show the conflict. Delete and status changes require confirmation and prevent repeated requests.

- [ ] **Step 5: Run the frontend suite**

Run: `npm test && npm run lint && npm run typecheck && npm run build`

Expected: all commands exit `0` with no test failures or TypeScript errors.

- [ ] **Step 6: Commit**

```powershell
git add -- xsy-scm-web/src
git commit -m "feat(web): add spu sku product editor"
```

---

### Task 11: Add real browser acceptance coverage

**Files:**
- Create: `xsy-scm-web/playwright.config.ts`
- Create: `xsy-scm-web/e2e/product-flow.spec.ts`
- Modify: `xsy-scm-web/package.json`
- Create: `README.md`

**Interfaces:**
- Consumes running PostgreSQL on `5432`, backend on `8080`, frontend on `5173`.
- Produces reproducible setup and acceptance commands.

- [ ] **Step 1: Write the failing Playwright flow**

Create a uniquely coded SPU with two SKUs, reload, edit one retained SKU, add one SKU, confirm the retained ID through the detail API, remove one non-default SKU through aggregate edit, change SPU status, then soft-delete the SPU.

- [ ] **Step 2: Run and verify RED**

Run: `npm run e2e -- product-flow.spec.ts`

Expected: failure before services/configuration are complete or before the flow is wired.

- [ ] **Step 3: Complete Playwright configuration and README**

Document Java 21 selection, PostgreSQL service, creation of only `xsy_scm`/`xsy_scm_test`, environment variable names, migrations, start commands, and Sprint boundary. Include no passwords or example secrets.

- [ ] **Step 4: Run the browser flow against real services**

Run PostgreSQL, backend, and frontend in separate sessions, then: `npm run e2e -- product-flow.spec.ts`.

Expected: one real-data flow passes; browser console has no errors and API calls return expected 2xx responses except deliberate validation cases.

- [ ] **Step 5: Inspect desktop states**

Check 1920px, 1440px, and 1366px widths; verify two-level navigation, horizontal table scrolling, drawer scrolling, loading, empty, error/retry, expanded rows, and fixed actions.

- [ ] **Step 6: Commit**

```powershell
git add -- xsy-scm-web/playwright.config.ts xsy-scm-web/e2e xsy-scm-web/package.json README.md
git commit -m "test: verify sprint 1 product flow"
```

---

### Task 12: Final verification and scope audit

**Files:**
- Review: all Sprint 1 changes.
- Modify only if verification exposes a defect.

**Interfaces:**
- Produces final evidence for schema, backend, frontend, API, browser, and scope.

- [ ] **Step 1: Run full backend verification**

Run: `cd xsy-scm-server && mvn.cmd clean verify`

Expected: exit code `0`, zero failures, package created.

- [ ] **Step 2: Run full frontend verification**

Run: `cd xsy-scm-web && npm run lint && npm run typecheck && npm test && npm run build`

Expected: every command exits `0`.

- [ ] **Step 3: Run real PostgreSQL and browser verification again**

Run the real-data Playwright flow and query `information_schema` to confirm six tables, no foreign keys, partial unique indexes, and applied Flyway migrations.

- [ ] **Step 4: Audit scope and diff**

Confirm no login, RBAC, Docker Compose, CI, Redis, supplier/warehouse UI, price subsystem, inventory, order, or device code was added. Review `git diff` and report any skipped verification explicitly.
