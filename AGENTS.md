# AGENTS.md

> Project: 鲜蔬源智慧供应链管理平台  
> Short name: 鲜蔬源智链  
> Code name: `xsy-scm`  
> Version: v1.1  
> Scope: Global repository-level instructions for coding agents working on this project.

---

## 1. Project Mission

`xsy-scm` is a fresh-food supply-chain management platform covering:

- Product management
- Customer management
- Supplier management
- Marketing
- Sales orders
- Purchasing
- Receiving
- Inventory
- Sorting / weighing
- Delivery
- Finance and reports
- Mall / Mini Program
- Traceability
- Data screens
- Hardware integration, especially electronic scales

The core business chain is:

```text
Customer
→ Product / Customer Pricing
→ Mall Order / Admin Order Entry
→ Sales Order
→ Order Aggregation
→ Purchase Demand
→ Purchase Order
→ Actual-weight Receiving
→ Inventory
→ Sorting
→ Actual-weight Write-back
→ Shipment
→ Delivery
→ Sign-off
→ Receivable / Payable / Profit
```

All implementation decisions should preserve this end-to-end business chain.

---

## 2. Repository Structure

Expected top-level structure:

```text
xsy-scm/
├─ xsy-scm-web/
├─ xsy-scm-server/
├─ xsy-device-agent/
├─ docs/
├─ deploy/
├─ docker-compose.yml
└─ AGENTS.md
```

Responsibilities:

```text
xsy-scm-web
= React admin UI, dashboards, data screens

xsy-scm-server
= Spring Boot business API and core domain logic

xsy-device-agent
= Local Windows device integration for scales, printers, scanners, etc.

docs
= Product, architecture, database, API, UI, workflow and device integration documentation

deploy
= Docker, Nginx and deployment assets
```

Do not move responsibilities across these boundaries without a clear architectural reason.

---

## 3. Source-of-Truth Order

Before making non-trivial changes, read the relevant project documentation.

Priority:

```text
1. Current user request
2. AGENTS.md
3. Relevant docs under docs/
4. Existing implementation
5. Existing tests
6. Framework conventions
```

For UI work, read:

```text
docs/design/
.skills/xsy-scm-design/SKILL.md
```

For business workflow changes, read the corresponding design documents first.

For hardware work, read:

```text
docs/08-电子秤与设备接入设计.md
```

when that file exists.

Do not invent business rules that are not defined. If a workflow rule is unclear, preserve the existing behavior and clearly flag the ambiguity.

---

## 4. Default Technology Stack

### Frontend

```text
React
Vite
TypeScript
Ant Design
@ant-design/pro-components
React Router
TanStack Query
Axios
Zustand
React Hook Form
Zod
Apache ECharts
DataV-React where needed
AMap JS API where needed
```

### Backend

```text
Java 21
Spring Boot
Spring Security
MyBatis-Plus
Spring Validation
Flyway
OpenAPI 3
PostgreSQL
Redis only when justified
```

### Device Integration

```text
xsy-device-agent
Windows local service
WebSocket / HTTP
RS232 / USB virtual serial / TCP/IP
Vendor SDK or DLL when required
```

### Deployment

```text
Docker Compose first
Nginx
Containerized application
Managed or containerized PostgreSQL depending on environment
```

---

## 5. Architecture Principles

Use a modular monolith by default.

Do not introduce the following without an explicit, demonstrated requirement:

```text
Microservices
Kafka
Kubernetes
Distributed transactions
Independent search engine
Complex event infrastructure
```

Preferred rule:

```text
simple and testable
> fashionable and distributed
```

Domain modules should remain clearly separated even inside one Spring Boot application.

---

## 6. Backend Package Rules

Use domain-oriented packages.

Preferred:

```text
com.xianshuyuan.scm
├─ common
├─ auth
├─ system
├─ product
├─ customer
├─ supplier
├─ order
├─ purchase
├─ inventory
├─ sorting
├─ delivery
├─ finance
├─ marketing
├─ traceability
├─ report
├─ screen
└─ device
```

Inside a domain:

```text
product/
├─ controller
├─ service
├─ repository
├─ mapper
├─ entity
├─ dto
├─ vo
└─ converter
```

Avoid this global structure:

```text
controller/
service/
mapper/
entity/
```

for the entire application.

---

## 7. Domain Rules

### 7.1 Product

The product domain must be able to evolve toward:

- Three-level categories
- Standard and non-standard products
- Multiple specifications
- Multiple suppliers
- Default purchaser
- Default supplier
- Customer-specific visibility
- Customer-specific price
- Market/time-sensitive price
- Contract price
- Product images
- On/off shelf state

Do not collapse these concepts into a single `product` table if doing so blocks future rules.

---

### 7.2 Customer

Keep separate concepts for:

- Customer
- Customer type
- Customer group
- Group subsidiary
- Settlement entity
- Address
- Contact
- Credit term
- Salesperson
- Product visibility
- Customer-specific price

Group-level settlement must not be assumed to be the same as order ownership.

---

### 7.3 Orders

Keep order header and order items separate.

Typical order sources:

```text
MALL
ADMIN
MOBILE_ASSISTANT
IMPORT
```

Order state changes must be explicit and auditable.

Do not directly mutate order status from unrelated modules.

Important actions such as:

- price modification
- quantity modification
- actual-weight modification
- cancellation
- refund
- return

must leave an operation log.

---

### 7.4 Purchasing

Purchasing must support:

```text
Sales Order
→ Purchase Demand
→ Aggregation
→ Split by supplier / purchaser / category
→ Purchase Order
→ One or more Receipts
→ Actual-weight Receiving
→ Inventory
```

Do not assume one purchase order can only be received once.

---

### 7.5 Inventory

Every stock change must create an immutable or append-only inventory movement record.

Typical movement types:

```text
PURCHASE_IN
SALES_OUT
RETURN_IN
PURCHASE_RETURN_OUT
STOCKTAKE_IN
STOCKTAKE_OUT
LOSS
GAIN
TRANSFER_IN
TRANSFER_OUT
ADJUSTMENT
```

Never update inventory quantity silently without a movement record.

Do not allow inventory code to bypass transaction boundaries.

Cost calculation is expected to use weighted-average logic, but exact accounting rules must follow confirmed finance requirements.

---

### 7.6 Sorting and Weight

For non-standard fresh products:

```text
ordered quantity
!=
final actual weight
```

The final actual weight may be created during sorting and can affect final settlement.

Do not overwrite the original ordered quantity. Preserve both:

```text
ordered quantity
actual / settled quantity
```

Weight changes must be auditable.

---

### 7.7 Delivery

Delivery concepts should remain separate:

- Route
- Delivery task
- Order assignment
- Vehicle
- Driver
- Vehicle location
- Track
- Sign-off

Do not store GPS track points directly in unrelated sales-order tables.

---

### 7.8 Finance

Initial finance scope focuses on:

```text
Receivable
Receipt
Payable
Payment
Customer statement
Supplier statement
Sales income
Purchase cost
Product profit
Customer profit
```

Do not expand into a complete general-ledger accounting suite unless explicitly requested.

---

## 8. Database Rules

Default database: PostgreSQL.

Always consider:

- Primary keys
- Unique constraints
- Check constraints
- Proper numeric precision
- Timestamps
- Indexes
- Transaction boundaries
- Migration scripts
- Slow-query impact

### Foreign Key Policy

**Do not create database foreign-key constraints in this project.**

Relationship integrity is enforced through:

- application/domain validation
- service-layer checks
- transactional business logic
- unique/check constraints where appropriate
- explicit indexes on relationship columns
- audit and reconciliation jobs for critical data when needed

Relationship columns such as `customer_id`, `product_id`, `order_id`, `supplier_id` and `warehouse_id` should still be modeled clearly and indexed according to query patterns, but they must not use `FOREIGN KEY` constraints.

Reasons for this project-level rule include:

- simpler data migration and import
- lower coupling between large business tables
- easier batch operations and historical-data handling
- fewer deployment and schema-change constraints
- business consistency is handled explicitly by the application layer

Do not silently add foreign keys through ORM annotations, Flyway migrations or schema-generation tools.

Schema changes must use Flyway migrations.

Do not:

```text
manually alter production schema
edit old applied migrations
create database foreign-key constraints
store large images directly in relational tables
skip important unique/check constraints and rely only on Java validation
```

Use object storage for images and large attachments.

Money fields should normally use:

```text
NUMERIC / DECIMAL
```

Never use floating-point types for financial values.

Weight fields must use a precision that supports the real scale resolution.

---

### MyBatis / MyBatis-Plus SQL Rules

Do not write SQL in mapper annotations.

Forbidden:

```java
@Select("SELECT ...")
@Update("UPDATE ...")
@Delete("DELETE ...")
@Insert("INSERT ...")
```

When MyBatis-Plus built-in CRUD or wrappers cannot express a query clearly, place the custom SQL in a mapper XML file under:

```text
src/main/resources/mapper/
```

Mapper interfaces should contain only method signatures and necessary parameter/result declarations. Keep SQL, result maps, reusable fragments and database-specific statements in XML.

---

## 9. API Rules

Default API style:

```text
REST
JSON
OpenAPI 3
```

Base path:

```text
/api
```

Typical response:

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

Typical pagination:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 20,
    "total": 100
  }
}
```

Rules:

- Validate input at the API boundary.
- Return stable error codes.
- Do not expose stack traces to clients.
- Make mutation semantics explicit.
- Use idempotency where repeated external requests can create financial, stock or order side effects.
- Document public or shared API contracts.

---

## 10. Frontend Architecture

Suggested structure:

```text
src/
├─ api/
├─ assets/
├─ components/
│  ├─ common/
│  ├─ business/
│  └─ screen/
├─ layouts/
│  ├─ AdminLayout/
│  └─ ScreenLayout/
├─ pages/
├─ styles/
├─ router/
├─ stores/
├─ hooks/
├─ utils/
└─ types/
```

Do not create page-specific duplicates of shared selectors, status tags, search panels or table wrappers.

---

## 11. UI Design System

The project has three visual systems:

```text
Admin Theme
Screen Theme
Mall Theme
```

Do not mix them.

---

### 11.1 Admin Theme

Used by ERP / SCM management pages.

Style:

```text
professional
clean
restrained
high information density
efficient
modern Chinese enterprise SaaS / ERP
```

Primary colors:

```text
Primary          #00B96B
Primary Hover    #20C77A
Primary Active   #009A59

Background       #F5F7F9
Container        #FFFFFF
Text             #1F2329
Secondary Text   #4E5969
Border           #E5E6EB

Sidebar          #202631
Sidebar Hover    #2C3440
```

Avoid:

```text
glassmorphism
huge rounded corners
large marketing gradients
excessive shadows
landing-page style layouts
oversized typography
decorative animation
```

---

### 11.2 Admin Layout

Default layout:

```text
Top Header
+
Primary Sidebar
+
Secondary Sidebar
+
Page Content
```

Recommended dimensions:

```text
Header              56px
Primary Sidebar     80px
Secondary Sidebar   ~140px
Page Padding        24px
Button Height       32px
Input Height        32px
Table Row           48~52px
Body Font           14px
```

Preserve the two-level sidebar pattern unless a documented redesign is approved.

---

### 11.3 Standard Business Page

Default page order:

```text
Tab / Title
→ Search
→ Toolbar
→ Table
→ Summary
→ Pagination
```

Prefer:

```text
ProLayout
ProTable
ProForm
ModalForm
DrawerForm
ProDescriptions
ProCard
StatisticCard
```

Do not rebuild basic form + table + pagination mechanics manually if ProComponents already covers the use case.

---

## 12. Table Rules

Tables are the primary interaction pattern in this ERP.

Default behavior:

```text
Header        light gray
Row           white
Hover         light green
Selected      #E8F8F0
```

Alignment:

```text
Text / names     left
Quantity         right
Money            right
Status           center
Actions          right
```

Money:

```text
¥ 1,280.50
```

Use:

```css
font-variant-numeric: tabular-nums;
```

Status should use consistent `Tag` semantics.

Dangerous actions such as delete, void or cancel require confirmation.

---

## 13. Form Rules

Prefer:

```text
2 columns for standard forms
2~3 columns for complex forms
```

Use common selectors:

```text
ProductSelector
CustomerSelector
SupplierSelector
WarehouseSelector
DriverSelector
VehicleSelector
ScaleDeviceSelector
```

Use:

```text
InputNumber for money / numeric input
DatePicker / RangePicker for dates
```

Do not build multiple incompatible versions of the same business selector.

---

## 14. Shared Frontend Components

Prefer creating and reusing:

```text
PageContainer
PageTabs
SearchPanel
PageToolbar
SummaryBar
StatusTag
AmountText
EmptyState

ProductSelector
CustomerSelector
SupplierSelector
WarehouseSelector
ScaleDeviceSelector

WeightDisplay
DeviceStatusBadge
WeightStatusIndicator
WeightActionBar
WeightHistoryDrawer

ScreenLayout
ScreenHeader
ScreenPanel
KpiCard
RankingChart
MapPanel
```

Before introducing a new shared component, search for an existing equivalent.

---

## 15. Design Tokens

Use project tokens.

Expected files:

```text
src/styles/
├─ tokens.ts
├─ admin-theme.ts
├─ screen-theme.ts
└─ mall-theme.ts
```

Do not scatter random hard-coded colors such as:

```css
color: #27ad73;
background: #03a95f;
```

Use:

```text
Ant Design theme token
CSS variable
project design token
```

---

## 16. Data Screen Rules

Data screens use a separate visual system.

Default colors:

```text
Background          #06152F
Panel               #071E42
Panel Secondary     #092851
Border              #1565B8
Primary Blue        #00A8FF
Cyan                #20E3FF
Primary Text        #EAF6FF
Secondary Text      #8FB7D9
Highlight           #FFD166
Danger              #FF5B5B
```

Reference structure:

```text
Header / Title / Time
│
├─ Left KPI / Progress
├─ Center Map / Main Chart
├─ Right Ranking / Messages
└─ Bottom Screen Navigation
```

Design base:

```text
1920 × 1080
```

Use overall scale for large-screen adaptation rather than freely reflowing the layout.

Data screens are read-only views of business data.

Never maintain independent copies of order counts, customer counts or financial values inside screen-specific storage.

---

## 17. Mall Rules

Mall / Mini Program UI may be visually richer than the admin system.

Allowed themes include:

```text
default fresh-food green
Spring Festival
618
summer
Dragon Boat Festival
Mid-Autumn Festival
New Year
opening promotion
seasonal campaigns
```

Admin configuration pages for mall themes still use the Admin Theme.

Do not let mall campaign styling leak into ERP pages.

---

## 18. Hardware Integration Rules

Electronic scales and other shop-floor devices are first-class project integrations.

Default boundary:

```text
Physical Device
→ xsy-device-agent
→ localhost WebSocket / HTTP
→ xsy-scm-web
→ xsy-scm-server
```

Do not make React business components depend directly on:

```text
COM3 / COM4
vendor binary protocol
baud rate
vendor DLL
raw serial frame
```

Those belong inside `xsy-device-agent`.

---

## 19. Device Agent Responsibilities

`xsy-device-agent` should own:

- Device discovery
- Serial / TCP connection
- Driver / SDK integration
- Reconnect
- Raw protocol parsing
- Stable-weight determination
- Unit normalization
- Device status
- Diagnostics
- Raw event logging where necessary

Prefer adapter-based design:

```text
ScaleAdapter
├─ SerialScaleAdapter
├─ TcpScaleAdapter
└─ VendorSdkScaleAdapter
```

Adding a new vendor should not require changing sales-order or sorting business logic.

---

## 20. Standard Weight Event

Frontend should consume a normalized event such as:

```json
{
  "deviceId": "SCALE-01",
  "status": "STABLE",
  "grossWeight": 12.56,
  "tareWeight": 0.35,
  "netWeight": 12.21,
  "unit": "kg",
  "timestamp": "2026-09-02T15:20:30"
}
```

Typical statuses:

```text
STABLE
UNSTABLE
DISCONNECTED
ERROR
STALE
```

Do not confirm stale cached weight after device disconnection.

---

## 21. Weight Audit Rules

Every confirmed weight should be traceable.

Preserve where applicable:

```text
device_id
business_type
business_id
business_item_id
gross_weight
tare_weight
net_weight
unit
stable
raw_data
operator_id
weighed_at
source
```

Weight source should distinguish at least:

```text
SCALE
MANUAL
IMPORT
SYSTEM
```

Manual override must preserve:

- original automatic weight
- modified weight
- reason
- operator
- timestamp

Never silently overwrite a scale-derived value.

---

## 22. Weight UI Rules

Weight pages are operational interfaces, not normal CRUD forms.

Priorities:

```text
large readable weight
clear device state
minimal clicks
safe confirmation
fast recovery
```

Recommended:

```text
Weight number    48~72px
Unit             18~24px
Primary buttons  40~48px height
```

Typical workflow:

```text
Live Weight
→ Stable
→ User Confirmation
→ Business Record
```

Do not automatically commit rapidly changing live weight into final business records.

---

## 23. Printing and QR Code Rules

Printing should be centralized.

Expected document types:

- Purchase order
- Receiving note
- Sorting slip
- Product label
- Shipment note
- Delivery note
- Statement

QR code use cases:

- Purchase-task QR
- Traceability QR
- Order QR
- Salesperson promotion QR

Prefer shared services such as:

```text
PrintTemplate
QrCodeService
```

Do not implement printing independently in each business module.

---

## 24. Security and Permission Rules

Use RBAC first.

Permission layers:

```text
User
→ Role
→ Menu
→ Button / Action
→ API
→ Data scope
```

Potential data scopes:

```text
Warehouse
Customer
Supplier
Purchaser
Salesperson
Department
```

Sensitive actions require authorization and audit logging.

Never expose secrets in:

- source code
- client bundle
- logs
- screenshots
- test fixtures
- committed config files

---

## 25. Audit Logging

Record important business mutations, including:

- Create
- Edit
- Delete
- Approve
- Cancel
- Void
- Order price modification
- Actual-weight modification
- Weight manual override
- Device bind / unbind
- Device configuration change
- Inventory adjustment
- Finance operation
- Permission change
- Login failure

Audit records should include enough context to identify:

```text
who
when
where
what object
what operation
before
after
```

---

## 26. Error Handling

Backend:

- Use structured domain exceptions.
- Map exceptions to stable API error responses.
- Avoid generic `catch (Exception)` unless rethrowing with context.
- Never swallow transaction failures.

Frontend:

Every network-backed page must consider:

```text
Loading
Empty
Error
Success
Retry
```

Hardware UI additionally considers:

```text
Device disconnected
Device stale
Unstable weight
Reconnect
Manual fallback
```

Avoid Toast storms for high-frequency device events.

---

## 27. State Management

Use the right state for the right scope.

Preferred:

```text
TanStack Query
= server state

Zustand
= lightweight cross-page client state

React local state
= component-local state

URL/search params
= filters, tabs, pagination when useful
```

Do not duplicate server state into Zustand without a specific reason.

---

## 28. Validation

Validation should exist at multiple boundaries:

```text
Frontend UX validation
+
Backend authoritative validation
+
Database constraints
```

Do not rely only on frontend validation.

Important domains requiring strong backend validation:

- Price
- Weight
- Inventory
- Settlement
- Refund
- Payment
- Permissions
- Device-confirmed business actions

---

## 29. Transactions and Concurrency

Use database transactions for business operations that must remain atomic.

Examples:

```text
Purchase receipt + inventory movement
Sorting confirmation + actual-weight write-back
Shipment + inventory deduction
Refund + finance record
Payment + receivable update
```

When duplicate requests are realistic, add idempotency protection.

Do not solve concurrency by hiding buttons only in the UI.

---

## 30. Performance

Before optimizing, identify the actual bottleneck.

Always avoid:

- N+1 queries
- full-table scans on large operational tables
- unbounded list APIs
- loading all dashboard data in one huge transaction
- sending huge raw device event histories to pages

Use pagination for operational tables.

Use aggregate APIs for dashboards and screens.

---

## 31. Testing Strategy

Testing must be **risk-based and proportional**.

The goal is not maximum test count. The goal is sufficient confidence for important business behavior.

Preferred layers:

```text
Core domain unit tests
→ Critical database/API integration tests
→ A small number of key browser/E2E flows
→ Hardware integration verification where applicable
```

Backend tools:

```text
JUnit 5
AssertJ
Spring Boot Test
Testcontainers where it materially improves confidence
```

Frontend tools:

```text
Vitest
Playwright
```

### Avoid Over-Testing

Do not over-test routine implementation details.

Avoid:

- writing large test suites for trivial CRUD getters/setters
- testing framework behavior already covered by Spring, React or Ant Design
- duplicating the same business assertion at unit, integration and E2E layers without a clear reason
- creating snapshot tests for large unstable UI trees by default
- mocking every dependency when a simpler integration test is clearer
- adding E2E tests for every button, field and pagination case
- writing tests only to increase coverage percentage
- forcing 100% line or branch coverage
- adding Testcontainers to simple pure unit tests
- testing generated code, DTO boilerplate or library internals

Prefer tests around high-risk behavior:

- price calculation
- customer-specific pricing
- order state transitions
- purchase aggregation and split rules
- partial receiving
- inventory movement and concurrency
- actual-weight write-back
- refund / payment / settlement
- permissions and data scope
- device weight confirmation and manual override
- idempotency
- critical reporting calculations

For ordinary CRUD pages and APIs, a focused happy-path test plus important validation/error cases is usually enough.

Do not replace unit / integration tests with a large number of brittle E2E tests.

---

## 32. Required Verification Before Completion

For frontend changes, run when available:

```text
npm run lint
npm run typecheck
npm run test
npm run build
```

For backend changes, run the project’s equivalent:

```text
compile
unit tests
integration tests where affected
package/build
```

For browser-facing work, verify:

```text
Console
Network
Main user path
Loading state
Empty state
Error state
Scrolling
Common desktop resolutions
```

For hardware-facing work, verify at least:

```text
Disconnected
Connected
Unstable weight
Stable weight
Reconnect
Zero weight
Repeated submit
Manual override
```

Do not claim completion if required verification was skipped. State what was not run and why.

---

## 33. Hardware Mocking

Before real hardware arrives, provide a mock provider that can simulate:

```text
Disconnected
Connected idle
Rapid weight changes
Stable weight
Device error
Reconnect
Stale data
Zero weight
Over-threshold weight
```

Business UI should be testable without a physical scale.

Do not block frontend and backend development on hardware delivery.

---

## 34. Git and Change Discipline

Before editing:

1. Read relevant code.
2. Read relevant tests.
3. Check current conventions.
4. Confirm the requested scope.

While editing:

- Keep changes focused.
- Do not refactor unrelated code.
- Do not mass-format unrelated files.
- Do not replace frameworks or major libraries without approval.
- Preserve user changes.
- Avoid generated noise.

After editing:

- Review diff.
- Run relevant checks.
- Explain material behavior changes.

---

## 35. Dependency Rules

Before adding a dependency, ask:

1. Does the project already have an equivalent?
2. Is this needed in production or only for development?
3. Is it actively maintained?
4. What is the license?
5. Does it materially reduce complexity?

Do not add:

- a second major UI library
- a second state-management framework
- overlapping date libraries
- redundant HTTP clients
- large dependencies for trivial utility functions

---

## 36. UI Skill Guidance

When relevant and installed, use project skills with clear separation of responsibility.

Recommended UI flow:

```text
xsy-scm-design
→ project-specific constraints

Impeccable
→ ERP / Dashboard UI quality

vercel-react-best-practices
→ React implementation quality

vercel-composition-patterns
→ shared component architecture

frontend-design / web-design-guidelines
→ UI review

Playwright
→ final browser verification
```

Do not let multiple design skills independently redesign the same page.

For ERP pages, project design rules win over generic visual creativity.

---

## 37. Agent Workflow

For a non-trivial task, use this sequence:

```text
1. Understand request
2. Read relevant docs
3. Inspect existing code
4. Inspect existing tests
5. Identify affected domain boundaries
6. Make a short implementation plan
7. Implement the smallest coherent change
8. Add / update tests
9. Run verification
10. Review diff
11. Report result and remaining risks
```

Do not start coding a complex feature before reading the existing implementation.

---

## 38. New Page Workflow

Before implementing a new ERP page, identify:

```text
Page purpose
User role
Tabs
Filters
Table fields
Actions
Status values
Summary data
Pagination
Create method
Edit method
Detail method
Required permissions
API dependencies
```

Then identify reusable components.

Default structure:

```text
Tab
→ Search
→ Toolbar
→ Table
→ Summary
→ Pagination
```

---

## 39. Feature Delivery Priority

Default delivery order:

```text
Foundation
→ Product
→ Customer
→ Supplier
→ Order
→ Purchase
→ Actual-weight Receiving
→ Inventory
→ Actual-weight Sorting
→ Shipment
→ Delivery
→ Finance / Reports
→ Data Screens
→ Mall / Mini Program
→ Traceability / Advanced Features
```

Hardware protocol research and device-agent PoC should begin early, before receiving and sorting are finalized.

---

## 40. Do Not Guess Critical Business Rules

Stop and flag ambiguity for rules involving:

- final settlement quantity
- customer contract pricing
- price-effective time
- group settlement
- purchase split logic
- partial receiving closure
- negative inventory
- stock costing
- sorting tolerance
- refund accounting
- payment allocation
- route optimization
- hardware protocol semantics

When the requirement is not explicit, do not silently invent a permanent rule.

---

## 41. Production Safety

Never execute destructive production operations without explicit approval.

Examples:

```text
DROP DATABASE
TRUNCATE production tables
delete Docker volumes
delete PostgreSQL data
destroy namespaces / PVCs
overwrite production secrets
change production DNS
force-push protected branches
```

For risky migrations:

- back up first
- provide rollback strategy
- verify row counts
- verify constraints
- test in non-production environment

---

## 42. Definition of Done

A task is not complete only because code was written.

A change is complete when applicable items are satisfied:

```text
Requirement implemented
Architecture boundaries respected
Types compile
Relevant tests pass
No unnecessary or duplicate tests were added
Build passes
No new console errors
No obvious network errors
UI follows project design system
Permissions are enforced
Audit logging added where required
Database migration included where required
Critical business action is transactional
Hardware state is handled where applicable
Documentation updated for material design changes
```

If any item was not verified, state it clearly.

---

## 43. Project Principle Summary

The project should optimize for:

```text
Correct business flow
Clear domain boundaries
Auditability
Reliable inventory and weight data
Consistent ERP UX
Testability
Maintainability
Recoverability
```

Prefer:

```text
clear over clever
modular over distributed
auditable over implicit
reusable over duplicated
stable over fashionable
proportional testing over coverage chasing
application-managed relationships over database foreign keys
```

The main goal is not to build the most technically complex system.

The goal is to build a reliable fresh-food supply-chain system whose:

```text
orders
prices
weights
inventory
purchases
deliveries
and settlements
```

can always be understood, verified and traced.
