# AGENTS.md

> **SmartAdmin V2 底座规则（2026-09-14 生效）：**SmartAdmin 是 V2 的**正式系统底座**，
> 不是只读参考。登录、认证、用户、员工、部门、角色、菜单、权限、数据权限、日志、字典、
> 文件、统一异常、统一响应、前端 Layout 与系统页面全部采用 SmartAdmin；旧 `auth` / `system`
> 实现不迁移。V2 只迁 xsy-scm 供应链业务域，管理后台统一 Vue3 + TypeScript。
> V2 正式工作区固定为根目录下的 `xsy-scm-server/` 与 `xsy-scm-web/`；正式工具和文档分别位于
> `tools/` 与 `docs/`。`xsy-scm-miniapp` 仍为冻结的 legacy 小程序目录。
> 禁止机械复制旧代码，禁止机械把 React 翻译成 Vue。
> 完整规则见 [`SMARTADMIN_REFERENCE_RULES.md`](./SMARTADMIN_REFERENCE_RULES.md)。

> Project: 鲜蔬源智慧供应链管理平台  
> Short name: 鲜蔬源智链  
> Code name: `xsy-scm`  
> Version: v2.0  
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

### Current delivery status (2026-09-17)

```text
W0   baseline                              COMPLETE
W1   Product                               COMPLETE
W2   Customer + Supplier                   COMPLETE
W3   Pricing implementation/verification   COMPLETE
W4   Sales Order                           COMPLETE
W5   Purchase                              COMPLETE
W5.5 SmartAdmin Native Feature Parity      COMPLETE
F0   Object Storage Activation             COMPLETE
W6   Inventory / Mini Program              NOT STARTED
```

W4 = Sales Order (COMPLETE, acceptance report 2026-09-16).
W5 = Purchase (COMPLETE, acceptance report 2026-09-16) — purchase demand, purchase order,
receiving and the minimal `warehouse` master data, with **no inventory implementation**.
W5.5 = SmartAdmin native feature parity sync (COMPLETE, report 2026-09-17) — zero new migrations,
zero menu changes, zero permission changes; the formal V2 workspace already carried the native
system and support capabilities.
F0 = Object Storage Activation (**COMPLETE**, acceptance report 2026-09-17) — strict fileKey
prefix policy, per-folder read guard with HTTP 403 + native 30005 envelope, S3/MinIO path-style
client and presigner, canned-ACL switch, short-TTL cache exclusion, four-profile cloud ENV
alignment, multipart 20/25 MB, data-only V17, and the `deploy/minio/` local development stack.
Verified end-to-end against a real MinIO + PostgreSQL + Redis: backend specialty tests
39/0/0/0, V17 applied with `maxUploadFileSizeMb` 30→20 and `fileDetectFlag` unchanged, cloud
Playwright 7/7, real-page read-guard matrix with zero mismatches, and full local regression
(backend unit 351/0/0/0, PG IT 187/0/0/5, frontend unit 50/50, Playwright 40/40).
F0 adds no SCM business domain and no business attachment tables.
**F0-DEBT-01 remains binding**: `FileKeyVoSerializer` → `FileService.getFileList()` expands
attachments server-side **without any per-user permission filtering**, so attachment URLs returned
through business VO fields bypass the Controller-level read guard. Before any non-administrator
business role is introduced, OA enterprise licences and similar COMMON private assets must move to
business-permission + ownership/relation + FileService reads.
W6 = Inventory / Mini Program — **NOT STARTED**; do not begin before F0 is accepted.

The PostgreSQL Closure restriction against V13+ applies only to that completed phase.
W4 adds V13/V14 and W5 adds V15/V16 normally; V1–V16 remain immutable, and F0 appended only the
data-only V17 (`t_config` file upload size alignment).

Architecture contracts for the completed waves:

```text
Frontend              = SmartAdmin Base + SCM Vue (Copy First + Adapt)
Backend Infrastructure = SmartAdmin Native First
Backend SCM Business   = SmartAdmin Structure + confirmed SCM business rules
```

---

## 2. Repository Structure

Actual top-level structure:

```text
xsy-scm/
├─ xsy-scm-server/           ← V2 正式后端（Java 21 + PostgreSQL）
├─ xsy-scm-web/              ← V2 正式后台（SmartAdmin Vue3 + TypeScript）
├─ xsy-scm-miniapp/          ← LEGACY，冻结只读（待 W6 迁 uni-app）
├─ tools/                    ← V2 正式工具脚本
├─ project-reference-examples/
│  └─ xsy-scm/               ← 上游源码参考（只读，用于同步与比对）
├─ docs/
├─ deploy/
│  └─ minio/                 ← F0 本地开发与集成测试对象存储
└─ AGENTS.md
```

Responsibilities:

```text
xsy-scm-server
= SmartAdmin-based Spring Boot business API and core domain logic (Java 21 + PostgreSQL)

xsy-scm-web
= SmartAdmin-based Vue3 + TypeScript admin UI

project-reference-examples/xsy-scm
= Upstream xsy-scm source reference — read-only, used for diffing and design extraction

docs
= Product, architecture, database, API, UI, workflow and device integration documentation

deploy
= Docker, Nginx and deployment assets
```

Do not move responsibilities across these boundaries without a clear architectural reason.

当前不存在根目录 `docker-compose.yml` 和 `xsy-device-agent/`；设备集成章节描述的是未来职责，不代表已有实现。

**Frozen directory.** `xsy-scm-miniapp/` remains frozen and read-only. The root
`xsy-scm-server/` and `xsy-scm-web/` directories are the official V2 workspaces. Do not create
a second implementation or compatibility copy elsewhere in the repository.

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

V2 baseline is SmartAdmin v3.31. Do not upgrade Spring Boot / MyBatis-Plus / Sa-Token or other
upstream dependency versions unless a compile failure requires it — and report before doing so.

### Frontend (V2)

```text
Vue 3
Vite
TypeScript
Ant Design Vue
Pinia
Vue Router
Axios
Apache ECharts
v-privilege permission directive
```

The legacy React stack (React, @ant-design/pro-components, TanStack Query, Zustand,
React Hook Form, Zod, DataV-React) is **frozen** and must not be introduced into the root V2 frontend.

### Backend (V2)

```text
Java 21
Spring Boot 3.5.4
MyBatis-Plus 3.5.12
Sa-Token 1.44.0 + Redis (Bearer token)
Spring Validation
Flyway (sole schema-evolution mechanism)
OpenAPI 3 / knife4j
PostgreSQL
Lombok (no MapStruct)
```

`Spring Security` is retained only where SmartAdmin's own crypto/utility layer uses it
(e.g. `Argon2PasswordEncoder`); authentication itself is Sa-Token based.

### Mini Program

```text
Target: uni-app + Vue3
Current: xsy-scm-miniapp (Taro + React) — frozen, migrates at W6
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
├─ warehouse
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

Use object storage for images and large attachments. Production storage must be S3-compatible.
SCM uploads must go through SmartAdmin `FileService`; do not create a second upload facility,
write business binaries directly to disk, or bypass validation/metadata via `IFileStorageService`.
File access details and the mandatory pre-RBAC COMMON asset debt are maintained in the F0 Target Design.

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

**V2 response envelope (Q5 — SmartAdmin `ResponseDTO`, mandatory):**

```json
{
  "code": 0,
  "level": "",
  "msg": "操作成功",
  "ok": true,
  "data": {},
  "dataType": ""
}
```

`code = 0` means success (`OK_CODE`). The legacy `message` field and the legacy `PageData`
envelope are **not** supported in V2 — do not add compatibility shims for them.

**V2 pagination (SmartAdmin `PageResult`):**

```json
{
  "code": 0,
  "msg": "操作成功",
  "ok": true,
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 100,
    "pages": 5,
    "list": [],
    "emptyFlag": false
  }
}
```

Request side uses `PageParam{ pageNum, pageSize, searchCount, sortItemList[] }`,
converted via `SmartPageUtil.convert2PageQuery` / `convert2PageResult`.

Rules:

- Validate input at the API boundary.
- Return stable error codes. Preserve existing in-use SCM business error codes
  (e.g. `40921`, `40933`, `40926`, `40963`, `40970`, `40971`) — do not renumber them for
  tidiness. New V2 error codes use a planned unified SCM range.
- Do not expose stack traces to clients.
- Make mutation semantics explicit.
- Use idempotency (`Idempotency-Key` + `idempotency_record`) where repeated external requests
  can create financial, stock or order side effects.
- Document public or shared API contracts.
- Method-level authorization uses `@SaCheckPermission("scm:<domain>:<action>")`.

---

## 10. Frontend Architecture

> **V2 前端边界（2026-09-14）**
>
> §10–§16、§22、§27、§28、§35.2、§36、§38 中出现的 React / ProComponents /
> TanStack Query / Zustand / React Hook Form / Zod 约定，描述的是 **legacy React 管理后台**。
> 该前端已冻结只读，仅作字段与交互参考，**不得在根目录 V2 前端中复用其技术选型**。
>
> V2 管理后台为 `xsy-scm-web`，采用 **SmartAdmin Vue3 原生方案**：
> 目录、Layout、菜单、Tabs、权限指令、表格、表单、弹窗、上传、字典展示
> 全部沿用 SmartAdmin 既有结构与组件，不重新发明。
>
> 概念映射：
>
> ```text
> React 页面 (pages/)            → Vue3 视图 (src/views/**/index.vue)
> React Router 动态路由           → SmartAdmin buildRoutes（菜单驱动，name = menuId）
> Permission / AuthGuard / hook  → v-privilege 指令 + 路由守卫
> TanStack Query 缓存             → SmartAdmin 既有请求封装（不引入 React Query）
> Zustand store                  → Pinia store（SmartAdmin 既有 store）
> React Hook Form + Zod          → Ant Design Vue 表单 + SmartAdmin 校验范式
> @ant-design/pro-components     → Ant Design Vue + SmartAdmin 业务组件
> 双级侧栏布局                    → SmartAdmin 原生 Layout（废止双级侧栏）
> ```
>
> Vue3 侧的详细约定在 W0 的 Vue3 后台基线任务中固化到本节；
> 以根目录 `xsy-scm-web` 的既有实现为准。

Suggested structure (**legacy React reference only** — not the V2 layout):

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

**V2 structure** (SmartAdmin native):

```text
xsy-scm-web/src/
├─ api/            ← 接口定义（含 scm/ 业务域）
├─ views/          ← 页面（system/ 沿用 SmartAdmin；scm/ 为业务域）
├─ components/     ← 通用与业务组件
├─ layout/         ← SmartAdmin Layout
├─ router/         ← 静态路由 + buildRoutes 动态菜单
├─ store/          ← Pinia
├─ directives/     ← privilege 等指令
├─ constants/
├─ utils/
└─ types/
```

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

> **V2 认证与鉴权（Q4 — 采用 SmartAdmin Sa-Token，不迁 legacy Spring Security 会话方案）**
>
> ```text
> Header：Authorization: Bearer <token>
> Sa-Token token-style: simple-uuid，有效期 30 天，登录态存 Redis
> loginId 形如 "2:44"（userType:employeeId）
> 接口鉴权：@SaCheckPermission("scm:<domain>:<action>")
> 前端按钮权限：v-privilege="'scm:<domain>:<action>'"
> administratorFlag 绕过权限校验
> ```
>
> 禁止套用 legacy 的 Spring Session JDBC 方案；
> 禁止为适配 legacy 而修改 SmartAdmin 核心认证实现。
> 当前不是 SaaS，不引入 `tenant_id` 与多租户。

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

## 35.1 Java Productivity and Code Generation Tools

For Java backend development, prefer mature, compile-time or framework-native tools over repetitive handwritten boilerplate when they clearly improve maintainability.

The agent should actively inspect the current project dependencies and conventions before manually implementing repetitive Java code.

### General Rule

Before writing repetitive infrastructure or boilerplate code, ask:

```text
1. Does the project already include a tool that solves this?
2. Is there a mature compile-time library commonly used for this problem?
3. Will the dependency remove meaningful repetitive code?
4. Will the generated behavior remain explicit, predictable and easy to debug?
5. Does it fit the existing Spring Boot / MyBatis-Plus architecture?
```

If the answer is yes, prefer the established tool instead of manually reproducing the same functionality.

The agent may add a small, mature dependency without separate approval when:

- it solves a recurring engineering concern rather than a one-off convenience;
- no equivalent dependency already exists in the project;
- it is actively maintained and compatible with the project's Java / Spring Boot version;
- it does not introduce a new architectural paradigm;
- it materially reduces boilerplate or error-prone mapping code;
- its behavior is deterministic and understandable by developers;
- the change is limited in scope and documented in the dependency file.

Do not add a dependency merely to save a few trivial lines of code.

### Lombok

Prefer Lombok for routine Java boilerplate when Lombok is already present or when the project contains enough DTOs, entities, value objects or configuration classes to justify it.

Typical allowed uses:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
```

Prefer:

```java
@Getter
@Setter
public class ProductDTO {
    private Long id;
    private String name;
}
```

over manually writing repetitive getters and setters.

For Spring dependency injection, prefer constructor injection, for example:

```java
@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductMapper productMapper;
}
```

Do not manually write constructors whose only purpose is Spring dependency injection when Lombok can generate them clearly.

Do not use Lombok indiscriminately.

Avoid or carefully evaluate:

```text
@Data on persistence entities
@EqualsAndHashCode on entities with mutable/database identity
@ToString on objects containing sensitive data or cyclic relationships
@Builder where framework construction semantics become unclear
```

Prefer explicit annotations such as `@Getter` and `@Setter` when `@Data` would generate more behavior than required.

Business behavior must never be hidden inside Lombok-generated mechanisms.

### MapStruct

Prefer MapStruct for structured mappings between:

```text
Entity
DTO
VO
Command
Query object
API response model
```

when mappings are repeated, contain multiple fields, or are likely to evolve.

For example:

```text
ProductEntity
→ ProductDTO

SalesOrderEntity
→ SalesOrderVO

CreateProductRequest
→ ProductEntity
```

should normally use a domain-local converter when the mapping is non-trivial or reused.

Preferred location:

```text
product/
└─ converter/
   └─ ProductConverter.java
```

Prefer MapStruct over:

```java
target.setId(source.getId());
target.setName(source.getName());
target.setCategoryId(source.getCategoryId());
target.setUnit(source.getUnit());
target.setPrice(source.getPrice());
```

repeated across services and controllers.

Typical pattern:

```java
@Mapper(componentModel = "spring")
public interface ProductConverter {

    ProductDTO toDTO(ProductEntity entity);

    ProductVO toVO(ProductEntity entity);

    ProductEntity toEntity(CreateProductRequest request);

    List<ProductVO> toVOList(List<ProductEntity> entities);
}
```

Use explicit mappings when field names or semantics differ:

```java
@Mapping(target = "customerName", source = "customer.name")
@Mapping(target = "settledQuantity", source = "actualQuantity")
OrderVO toVO(OrderEntity entity);
```

Do not force MapStruct for trivial one-field or one-off transformations where ordinary Java is clearer.

Do not hide business calculations in MapStruct.

The following normally belong in the service/domain layer rather than converters:

```text
price calculation
inventory calculation
settlement calculation
permission decisions
state transitions
database lookups
external API calls
```

Converters should primarily perform deterministic structural transformation.

### Bean Copy Utilities

Do not introduce or broadly use reflection-based property copying such as:

```java
BeanUtils.copyProperties(...)
```

as the default mapping strategy when MapStruct is available.

Avoid relying on implicit runtime copying for important business objects because:

- field mismatches are easier to miss;
- refactoring is less safe;
- mapping rules are less visible;
- runtime reflection provides weaker compile-time guarantees.

For repeated typed mappings, prefer MapStruct.

For extremely small local transformations, explicit Java mapping is acceptable.

### Object Construction

Use builders when objects contain many optional or clearly named fields and builder construction improves readability.

Example:

```java
OrderQuery.builder()
    .customerId(customerId)
    .status(status)
    .startTime(startTime)
    .endTime(endTime)
    .build();
```

Prefer constructors when the object has only a small number of mandatory arguments and the meaning remains obvious.

Do not create builders purely because Lombok provides `@Builder`.

### Utility Libraries

Before manually implementing common utility behavior, inspect existing project dependencies.

Examples include:

```text
string handling
collection handling
date/time handling
JSON serialization
validation
HTTP communication
object mapping
file handling
ID generation
retry
caching
```

Prefer, in order:

```text
JDK standard library
→ Spring Framework utilities
→ existing project dependency
→ small mature third-party dependency
→ custom implementation
```

Examples:

```text
java.time
instead of custom date arithmetic

Spring Validation / Jakarta Validation
instead of handwritten request validation

Jackson
instead of custom JSON serialization

Spring utilities
instead of introducing another utility library for a trivial helper
```

Do not introduce large "utility collections" only because one helper method is convenient.

### Annotation Processors and Compile-Time Generation

Compile-time generation is generally preferred over runtime magic when both solve the same problem cleanly.

Examples:

```text
Lombok
MapStruct
framework-supported annotation processors
```

are acceptable because generated behavior is established during compilation.

When adding annotation processors, ensure that:

```text
Maven / Gradle compilation works
IDE annotation processing works
CI build works
generated sources do not need to be manually edited
```

Never modify generated source files directly.

### Dependency Introduction Decision

The agent does not need to ask for confirmation before adding a lightweight Java productivity dependency such as Lombok or MapStruct when all of the following are true:

```text
the dependency is clearly appropriate
AND
the project does not already provide an equivalent
AND
the task would otherwise introduce substantial repetitive code
AND
the dependency is compatible with the current stack
AND
it does not alter system architecture or runtime infrastructure
```

The agent should explain the dependency addition in the final change summary.

Explicit approval is required before introducing dependencies that:

```text
change the persistence framework
change the web framework
introduce a new RPC framework
introduce a workflow engine
introduce a message broker
introduce a distributed framework
introduce a new ORM
replace MyBatis-Plus
significantly affect application runtime behavior
add large transitive dependency trees
```

### Preferred Java Development Principle

When implementing Java backend code, follow this preference:

```text
framework-native capability
> mature compile-time generation
> existing project utility
> explicit reusable abstraction
> repetitive handwritten boilerplate
```

But for business logic:

```text
explicit business code
> clever abstraction
> hidden framework magic
```

Use tools to remove mechanical code, not to hide domain behavior.

The goal is:

```text
less boilerplate
+
more compile-time safety
+
clearer domain code
+
consistent project conventions
```

not simply fewer lines of code.

---

## 35.2 Frontend Productivity and Framework Tools

> **V2 前端边界：**本节描述 **legacy React 前端** 的生产力工具约定（React Hook Form、Zod、
> TanStack Query、Zustand、ProComponents 等），该前端已冻结只读。
> V2 管理后台（`xsy-scm-web`）使用 **Vue3 + TypeScript + Ant Design Vue + Pinia**，
> 优先复用 SmartAdmin 既有组件、hooks（composables）、请求封装与表单/表格范式，
> 不引入本节中的 React 生态库。判断标准改为：
>
> ```text
> 1. Vue3 / Ant Design Vue 是否已提供该能力？
> 2. SmartAdmin 既有实现是否已解决该问题？
> 3. 是否存在 Vue3 生态的成熟等价方案？
> 4. 只有在以上都不满足时，才考虑自建。
> ```

For frontend development, prefer framework-native capabilities, existing project libraries and mature ecosystem tools over repetitive handwritten infrastructure.

The agent should actively inspect:

```text
package.json
existing components
existing hooks
existing utilities
existing API abstractions
existing form patterns
existing table patterns
existing state-management patterns
```

before implementing equivalent functionality manually.

### General Rule

Before writing reusable frontend infrastructure, ask:

```text
1. Does React already provide the required capability?
2. Does Ant Design / ProComponents already provide the required component?
3. Does TanStack Query already solve the server-state problem?
4. Does React Hook Form or the existing form system already solve the form problem?
5. Does Zod or the existing validation system already solve the validation problem?
6. Does the project already have a shared component, hook or utility for this?
7. Is there a mature small dependency that materially reduces complexity?
```

Prefer:

```text
framework capability
→ existing project abstraction
→ existing project dependency
→ mature lightweight dependency
→ custom implementation
```

Do not manually rebuild established framework functionality without a clear reason.

### Ant Design and ProComponents

Prefer Ant Design and `@ant-design/pro-components` for standard ERP interface patterns.

Use existing components such as:

```text
ProTable
ProForm
ModalForm
DrawerForm
ProDescriptions
ProCard
StatisticCard
ProLayout
```

instead of manually assembling equivalent infrastructure.

For example, do not manually implement:

```text
table loading
pagination
search form binding
reset behavior
column filtering
form submission state
modal form lifecycle
standard CRUD table mechanics
```

when ProComponents already provides an appropriate implementation.

Custom components are justified when:

```text
business interaction is genuinely specialized
OR
existing components cannot satisfy the workflow cleanly
OR
reuse would create more complexity than a focused implementation
```

Do not create custom UI infrastructure merely to avoid learning an existing component API.

### TanStack Query

Use TanStack Query as the default tool for server state.

Prefer it for:

```text
API querying
loading state
error state
cache management
request deduplication
refetch
query invalidation
pagination data
mutation lifecycle
optimistic updates where justified
```

Avoid patterns such as:

```tsx
const [loading, setLoading] = useState(false);
const [data, setData] = useState([]);

useEffect(() => {
  setLoading(true);

  api.getList()
    .then(setData)
    .finally(() => setLoading(false));
}, []);
```

when the same behavior is naturally expressed through TanStack Query.

Do not introduce custom global request caches when TanStack Query already owns server state.

Do not copy TanStack Query results into Zustand unless there is a demonstrated client-state requirement.

### Forms

Prefer the existing project form stack before manually creating form-state infrastructure.

Use:

```text
ProForm
Ant Design Form
React Hook Form
Zod
```

according to existing project conventions and the complexity of the form.

Do not manually maintain dozens of field states such as:

```tsx
const [name, setName] = useState('');
const [phone, setPhone] = useState('');
const [address, setAddress] = useState('');
const [remark, setRemark] = useState('');
```

for normal business forms when an existing form library can manage the form consistently.

Prefer schema-based validation when validation logic is reused or sufficiently complex.

Do not introduce another form library when the existing stack already covers the requirement.

### Validation

Prefer existing schema and framework validation tools.

Frontend validation should normally use:

```text
Ant Design form rules
Zod
React Hook Form integration
shared validation utilities
```

where appropriate.

Do not manually duplicate complex validation logic across pages.

Reusable validation such as:

```text
phone numbers
money ranges
weight ranges
date ranges
required identifiers
common text-length limits
```

should be centralized when repeated.

Frontend validation improves UX but does not replace backend authoritative validation.

### Shared Hooks

Extract reusable React behavior into hooks when the behavior is repeated and has a clear semantic purpose.

Examples:

```text
usePermission
usePagination
useTableColumns
useDebounce
useDeviceStatus
useWeightStream
useCustomerOptions
useWarehouseOptions
```

Do not create a hook merely to wrap one trivial line.

Before creating a new hook, search for an existing equivalent.

Prefer a descriptive domain hook over repeatedly copying:

```text
useEffect
useState
request
cleanup
subscription
permission checks
```

across multiple components.

### Utility Libraries

Before implementing generic utilities manually, inspect existing dependencies and platform APIs.

Common areas include:

```text
date/time
URL handling
query strings
deep object operations
debounce/throttle
number formatting
money formatting
file download
CSV/Excel processing
JSON handling
UUID generation
validation
```

Prefer:

```text
browser / ECMAScript standard API
→ React / framework capability
→ existing project utility
→ existing dependency
→ small mature dependency
→ custom implementation
```

Do not add a large utility library for one trivial function.

Do not add overlapping libraries for the same category.

For example:

```text
do not add another date library
if the project already has an established date solution

do not add another HTTP client
when Axios already exists

do not add another state-management library
when TanStack Query + Zustand already cover the required state
```

### Data Transformation

For simple transformations, prefer TypeScript-native code:

```tsx
records.map(...)
records.filter(...)
Object.fromEntries(...)
```

Do not introduce a transformation library for straightforward operations.

When transformation logic becomes:

```text
complex
repeated
business-specific
or independently testable
```

extract it into a named utility or domain function.

Keep business transformation logic outside React rendering code when it becomes substantial.

### API Client

Use the project's existing Axios abstraction.

Do not call `fetch`, create additional Axios instances or introduce another HTTP client unless there is a demonstrated technical requirement.

Prefer centralized handling for:

```text
base URL
authentication headers
error normalization
request timeout
response format
token expiration
request tracing
```

Pages and components should consume domain/API functions rather than repeatedly configuring raw HTTP requests.

### Code Generation

Use code generation when it removes substantial repetitive, mechanically derived code and the generated output has a reliable source of truth.

Reasonable uses include:

```text
OpenAPI-generated TypeScript API types
OpenAPI-generated clients where project conventions support them
schema-generated types
route/type generation provided by an adopted framework
```

Do not manually maintain large duplicated API type definitions when they can reliably be generated from a stable API contract.

Generated files must:

```text
have a clear source of truth
be reproducible
not contain handwritten business logic
not be manually edited unless explicitly designed for extension
```

Do not introduce code generation for small amounts of ordinary code.

### TypeScript

Use TypeScript to eliminate preventable runtime uncertainty.

Prefer:

```text
explicit domain types
discriminated unions
generic reusable types
inferred schema types
type-safe API responses
type-safe component props
```

over:

```text
any
unchecked casts
duplicated interfaces
stringly-typed state
```

Avoid using:

```tsx
as SomeType
```

merely to silence compiler errors.

Use `as` only when runtime knowledge genuinely exceeds what TypeScript can infer and the assumption is safe.

Prefer fixing the type model over suppressing the type system.

### Component Libraries

Do not introduce another general-purpose component library when Ant Design already covers the application.

For example, do not add:

```text
Material UI
Element Plus
Arco Design
Semi Design
another enterprise UI framework
```

for isolated components.

A specialized library may be introduced when Ant Design does not address the domain, for example:

```text
charting
maps
rich-text editing
specialized file processing
specialized visualization
```

and the requirement is substantial enough to justify the dependency.

### Specialized Libraries

The agent may add a mature specialized frontend dependency without separate approval when:

```text
the project has no equivalent
AND
the requirement is non-trivial
AND
implementing it manually would create substantial complexity
AND
the library has a focused responsibility
AND
the dependency is maintained
AND
bundle/runtime impact is reasonable
```

Examples may include libraries for:

```text
specialized visualization
drag and drop
rich-text editing
Excel/CSV parsing
QR/barcode processing
virtualized large lists
complex geometry
device protocols exposed to the browser
```

The agent must still prefer existing project dependencies first.

### Dependency Introduction Decision

The agent does not need separate approval for a small frontend productivity dependency when all conditions are true:

```text
no equivalent exists in the project
AND
the capability is genuinely reusable or complex
AND
the dependency materially reduces custom infrastructure
AND
bundle impact is reasonable
AND
it does not replace an existing architectural choice
```

The dependency addition must be mentioned in the final change summary.

Explicit approval is required before introducing:

```text
another UI framework
another state-management framework
another routing framework
another HTTP client
another form framework that overlaps the existing stack
a new frontend meta-framework
a new build system
a micro-frontend framework
a large runtime dependency
a library that materially changes application architecture
```

### Preferred Frontend Development Principle

For frontend infrastructure:

```text
platform capability
> framework capability
> existing project component/hook
> existing dependency
> mature specialized tool
> handwritten infrastructure
```

For business behavior:

```text
explicit domain logic
> reusable domain abstraction
> clever generic abstraction
> hidden library magic
```

Use libraries to remove mechanical implementation work, not to hide business behavior.

The goal is:

```text
less duplicated infrastructure
+
better type safety
+
consistent UX
+
smaller maintenance burden
+
clear business code
```

not simply fewer lines of frontend code.


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
