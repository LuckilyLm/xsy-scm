# Q2.1 — Naming + Domain Type Safety Pilot（common + product）

> 阶段：Quality Q2.1 — 命名与领域类型安全 **试点**
> 分支：`refactor/q2-naming-type-safety`
> 基线提交：`da7103ee`（`origin/main` 与 HEAD 同点，工作树干净）
> 试点范围：**仅** `com.xsy.scm.common` 与 `com.xsy.scm.product` 两个域的生产与测试代码
> 门禁流程：`code → check → capture → check`（§15）；baseline 只允许**下降**，禁用 `--allow-growth`，不手改 baseline
> 审计工具：[`tools/quality/q2_audit.py`](../../tools/quality/q2_audit.py)（只读，不参与门禁）

本文件只记录 Q2.1 试点**做了什么、为什么、剩下什么**。质量体系的全景与数字口径见
[`java-quality-audit-2026-09-26.md`](./java-quality-audit-2026-09-26.md)。

---

## 1. Q2 命名规则（试点口径）

| # | 规则 | 说明 |
| --- | --- | --- |
| N1 | 类型 UpperCamelCase | 类 / 接口 / 枚举 / record |
| N2 | 字段 / 形参 / 局部变量 lowerCamelCase | |
| N3 | 常量 UPPER_SNAKE_CASE | `static final` |
| N4 | 名字必须表达**完整业务语义** | 不允许只表达角色 |
| N5 | 禁止依赖字段裸角色名 | `dao` / `service` / `query` / `manager` / `validator` / `repository` / `mapper` / `reader` / `writer` / `client` |
| N6 | 禁止「类型名去掉领域前缀」的缩写字段 | `ProductSpuDao spuDao` → `productSpuDao`；`ProductCategoryDao categoryDao` → `productCategoryDao` |
| N7 | 不做形式主义改名 | 字段名已是类型名小写（`dataScopeService`）或语义已完整（`errorCode`）时不动 |
| N8 | 标准缩写保留常规驼峰 | `SKU` / `SPU` / `UOM` / `ID` / `URL` / `API` / `DTO` / `VO` / `DAO` 不拆、不强制大写 |
| N9 | `var` 继续允许 | 不因改名禁用 `var`（§27 禁止项） |

**标准样例**：`ProductCategoryService` —— 字段 `dao` → `productCategoryDao`、`spuDao` → `productSpuDao`，
枚举使用 `ScmEnableStatusEnum.ENABLED.name().equals(field)`（常量在前，天然 null-safe），
不新增枚举 helper，不新增 `ScmAllConstants`。

---

## 2. 试点前数据（before）

同一工具、同一词汇表测得的确定性数字（`python tools/quality/q2_audit.py`）：

| 指标 | common | product |
| --- | ---: | ---: |
| 扫描文件数 | 41 | 89 |
| 依赖字段总数 | 67 | 445 |
| **裸角色字段** | 0 | 24 |
| **缩写型字段**（类型名去前缀） | 4 | 4 |
| **无信息局部变量**（`var c` 等） | 0 | 15 |
| magic string（枚举已存在） | 28 | 92 |
| —— 其中 **主代码** | 0 | 25 |
| —— 其中 测试代码 | 28 | 67 |

门禁口径（`quality_guard.py check`）before：

| family | 当前 |
| --- | ---: |
| `generic-dependency-field` | 71 |
| `magic-string-domain-literal` | 191 |
| `raw-permission-literal` | 285 |
| `stage-comment` | 695 |
| `legacy-scm-package` | 0 |
| `checkstyle` | 867 |

> 注：`generic-dependency-field` 的 71 与 `magic-string` 的 191 是**全 SCM**（历史遗留的 customer /
> pricing / purchase 等域仍在账上），product 只是其中一部分。本试点只减 product 那一部分。

---

## 3. 本轮改了什么

### 3.1 提交清单

| 提交 | 内容 |
| --- | --- |
| `8fd47d72` `docs(quality): align post-Q1 package guard documentation` | §3 两处 Q1 收口后的**过期文档**修正（见 3.2） |
| `45343732` `refactor(product): improve dependency and variable naming` | §5–§9 命名整改（见 3.3） |
| `8d588602` `refactor(product): replace domain magic strings with enums` | §10–§12 魔法串整改（见 3.4） |
| `bb762290` `chore(quality): shrink the naming and magic-string baselines` | §15 capture：记录收缩后的 baseline |
| `f3a0611b` `chore(quality): add the Q2.1 naming / type-safety audit tool` | §17 审计工具（只读） |

### 3.2 §3 过期文档修正（独立提交）

1. **`AdminApplication`**：删除「旧包归零后把 `COMPONENT_SCAN` 收缩到 `com.xsy`」的错误指示。
   `net.lab1024.sa` 是 SmartAdmin 底座的**长期**扫描根，与 `com.xsy` 并存；收缩会让底座本身不被扫描。
   代码常量一字未改，只改 javadoc。
2. **`quality_guard.py` 的 `legacy-scm-package` family 描述**：改为「迁移债务已归零，防回流检测保留」。
   `legacy_package_files()` **仍在逐文件真实扫描**旧包两个源根，任何旧 namespace 下重新出现的
   `.java` 都会当场记为 NEW DEFECT。旧包目录仍存在（空壳），判据是 `.java` 数而非目录是否存在。
   另修正了四段 family scope 字符串（迁移期为「两个包都扫」→ 收口后单根 `com/xsy/scm`）。

### 3.3 §5–§9 命名整改（`45343732`）

- **Controller**：`service` / `query` / `batch` → 具体语义名
  （`productCategoryService` / `productSpuService` / `productQueryService` / `productBatchService` /
  `productImportService` / `productImageCenterService` / `productSkuOptionQueryService` /
  `productTagService` / `productUomService`）。
- **Manager / Service**：`dao` → `productXxxDao`；`validator` → `productAggregateValidator`；
  `writer` → `productImportWriteService`；`files` → `fileService`；`relations` → `fileRelationService`；
  `syncManager` → `productImageSyncManager`；`spus` → `productSpuDao`；`relationDao` → `productTagRelationDao`。
- **局部变量 / 形参**：`n` → `rowNumber`、`r` → `result`、`v` → `trimmedValue`，lambda `r ->` → `row ->`；
  外层 `n` → `firstRowNumber`（避免与内层循环变量遮蔽）。
- **测试**：`ProductAggregateValidatorTest` / `ProductCategoryLevelTest` / `ProductImportServiceTest`
  的注入字段同步改名。

**踩坑与修正（供后续域参考）**：朴素 `\bword\b` 替换会污染三类位置 —— (a) `import` / `package` 路径段，
(b) 字符串字面量 / 注解 / URL 映射（`@PostMapping("/query")`、`@SaCheckPermission("scm:product:batch")`），
(c) 同名方法（`query(...)`）。`ProductMasterDataPgIT.everyProductEndpointPermissionIsSeededInMenuTree`
（反射收集控制器注解并与 `t_menu` 种子比对）当场抓出了被污染的权限串。改名前**必须先做多 bean 类型检查**
（每个被改名的 DAO / Service 类型在上下文里只有唯一 bean 定义，且无 `@Qualifier` / `@Resource`）。

### 3.4 §10–§12 魔法串整改（`8d588602`）

按 §10 三类处置，仅动 **A 类**（已有枚举却硬编码）：

| 文件 | 替换 |
| --- | --- |
| `ProductCategoryService` | `!"ENABLED".equals(x)` → `!ScmEnableStatusEnum.ENABLED.name().equals(x)`（2 处） |
| `ProductTagService` | `query.setStatus("ENABLED")` → `ScmEnableStatusEnum.ENABLED.name()`；`!"ENABLED".equals(x)` → 枚举式 |
| `ProductUomService` | 同上 |
| `ProductSpuService` | `setMasterStatus("ENABLED")` → 枚举；`"ON_SHELF".equals(s)` → `ScmShelfStatusEnum.ON_SHELF.name().equals(s)` |
| `ProductBatchService` | `"ON_SHELF".equals(...)` → 枚举式 |
| `ProductImportService` | `SHELF` / `PRODUCT_TYPE` 集合与模板样例单元格改用 `ScmShelfStatusEnum` / `ScmProductTypeEnum`；3 处 `!"ENABLED".equals` → 枚举式 |

**只使用既有枚举**（未新建任何枚举）：`ScmEnableStatusEnum` / `ScmShelfStatusEnum` / `ScmProductTypeEnum`。
**行为等价**：保持「常量在前」的 null-safe 语义与大小写、取值完全不变。

**有意保留（§21 行为等价 + 编译期常量约束）**：

- `ProductExcelController` 的 `@RequestParam(defaultValue = "CREATE")`（2 处）——
  注解默认值**必须是编译期常量**，无法写成 `Enum.name()`。属误报，不改。
- `ProductImportService` 的 `"CATEGORY_DISABLED"` —— 是错误消息的**文本 token**，不是状态判断，不改。

**未发明的枚举**（B 类，只记录不新建）：本轮 product 无需新增枚举；命中 B 类的领域词留待后续域统一评估。

---

## 4. 试点后数据（after）

`python tools/quality/q2_audit.py`（after）：

| 指标 | common | product |
| --- | ---: | ---: |
| 裸角色字段 | 0 | **0**（24 → 0） |
| 缩写型字段 | 4 | **0**（4 → 0） |
| 无信息局部变量 | 0 | **1**（15 → 1，余 1 处在测试 `ProductPgIT`） |
| magic string 主代码 | 0 | **3**（25 → 3，余下即上面两处有意保留） |
| magic string 测试代码 | 28 | 67 |

门禁口径（`quality_guard.py check --checkstyle`，capture 后）：

| family | before | after | Δ |
| --- | ---: | ---: | ---: |
| `generic-dependency-field` | 71 | **52** | **−19** |
| `magic-string-domain-literal` | 191 | **169** | **−22** |
| `raw-permission-literal` | 285 | 285 | 0 |
| `stage-comment` | 695 | 695 | 0 |
| `legacy-scm-package` | 0 | 0 | 0 |
| `checkstyle` | 867 | 867 | 0 |

**无任何 family 增长**，`RESULT: PASS`。

---

## 5. common 域的判定：无必须改动（记录不硬改）

试点范围内的 `common` 复查结论：

- **裸角色字段 = 0**（本来就没有）。
- **缩写型字段 = 4，全部为诚实命名，不改**：
  - `ScmErrorCode errorCode` —— 叶子名词就是 `errorCode`，类型不是字段「是什么」。
  - `ScmDataScopeDao scopeDao` —— 这里的领域名词是「scope」（数据范围），去掉 `Data` 是正确简写。
  - `ScmDataScopeService dataScopeService` —— 它**本来就是类型名小写**，被标为「缩写」是 token 匹配假象。
  - `PlatformTransactionManager transactionManager`（测试）—— 同样是类型名小写。
- **magic string = 28，全部在测试源码**（主代码 0）。测试里的枚举字面量是**夹具**（fixture），
  不是裸露的业务逻辑；门禁只约束主代码，故不构成本轮 gated 债务。
- **无信息局部变量 = 0**。

因此按 §4 / §10 / §29 与「**如果 common 有独立变更：Commit 4**」的条件，
**common 没有产出独立的生产代码变更，Commit 4 不成立**。上述 4 + 28 条作为**已记录、待后续域统一治理**项处理，
而非本轮强行改名（§27 禁止形式主义）。

---

## 6. 遗留与后续

### 6.1 本轮明确不做（记录在案）

| 项 | 原因 |
| --- | --- |
| 实体 `private String status` → 枚举类型（§13） | 本轮不做大规模实体类型转换 |
| 测试侧 28（common）+ 67（product）条枚举字面量 | 夹具，非主代码门禁项 |
| `@RequestParam(defaultValue = ...)` 的常量（2 处） | 编译期常量约束，无法用 `Enum.name()` |
| `"CATEGORY_DISABLED"` 消息 token | 不是状态判断 |
| `stage-comment`（695） | Q3 范围，本轮不动 |
| 新增 `ScmAllConstants` / 大量 `XXXConstant` / 双份「枚举 + 字符串」 | §27 禁止项 |

### 6.2 重复枚举检查（§14，只记录不合并）

本轮未发现 product 域内需要合并的重复枚举。跨域词形相撞（如 `"NON_STANDARD"` 同时被
`ScmOrderProductTypeEnum` 与 `ScmProductTypeEnum` 声明、`"NORMAL"` 同时被三个 finance/inventory 枚举声明）
属**跨域词汇重合**，非重复定义，不合并，留待后续域评估。

### 6.3 试点外域的治理顺序（建议，**本轮不启动**）

`generic-dependency-field` 剩余 52 条与 `magic-string` 剩余 169 条主要落在历史遗留域。
建议后续按依赖方向由内向外推进：`customer` / `supplier` → `pricing` → `purchase` →
`inventory` / `sorting` / `delivery` → `finance`。**每完成一个域即 `capture` 一次**，让账本单调收缩。

---

## 附录 A：复现命令

```bash
# 命名 / 类型安全审计（只读，不阻断）
python tools/quality/q2_audit.py
python tools/quality/q2_audit.py --domain product --json

# 门禁
python tools/quality/quality_guard.py check --checkstyle
python tools/quality/quality_guard.py capture --checkstyle   # 仅在确认无增长后

# 工具单测
cd tools && python -m unittest discover -s quality -p 'test_*.py'
```
