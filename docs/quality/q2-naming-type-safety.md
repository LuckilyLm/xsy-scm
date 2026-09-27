# Q2.1 — Naming + Domain Type Safety Pilot（common + product）

> 阶段：Quality Q2.1 — 命名与领域类型安全 **试点**
> 分支：`refactor/q2-naming-type-safety`
> 基线提交：`da7103ee`（`origin/main` 与 HEAD 同点，工作树干净）
> 试点范围：**仅** `com.xsy.scm.common` 与 `com.xsy.scm.product` 两个域的生产与测试代码
> 门禁流程：`code → check → capture → check`（§15）；baseline 只允许**下降**，禁用 `--allow-growth`，不手改 baseline
> 审计工具：[`tools/quality/q2_audit.py`](../../tools/quality/q2_audit.py)（只读，不参与门禁）
> 改名工具：[`tools/quality/q2_rename_fields.py`](../../tools/quality/q2_rename_fields.py)（AST 感知，保护字符串/注解）
>
> **状态：Q2.1 NAMING + TYPE SAFETY PILOT COMPLETE**
> （Q2.1 首轮功能回归与门禁已通过，但命名判定**过宽**；经 Q2.1.1 Naming Standard Closure
> 收紧标准并补齐遗留缩写字段后，才在 §8 写下 COMPLETE。详见 §1.1 与 §5。）

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
| N6 | 禁止「类型业务名被裁剪」的缩写字段 | `ProductTagService tags` → `productTagService`；`ProductSpuDao spus` → `productSpuDao`；`ScmDataScopeDao scopeDao` → `dataScopeDao` |
| N7 | 不做形式主义改名 | 合法角色别名（`transactionManager` / `errorCode` / `readOnlyDataSource`）与类型名小写（`dataScopeService`）不动 |
| N8 | 标准缩写保留常规驼峰 | `SKU` / `SPU` / `UOM` / `ID` / `URL` / `API` / `DTO` / `VO` / `DAO` 不拆、不强制大写 |
| N9 | `var` 继续允许 | 不因改名禁用 `var`（§27 禁止项） |
| N10 | 改名不得用整文件正则 | 见 §1.2；Q2.1 已因 `query` / `batch` 的整文件替换造成权限串与 URL 损坏 |

**标准样例**：`ProductCategoryService` —— 字段 `dao` → `productCategoryDao`、`spuDao` → `productSpuDao`，
枚举使用 `ScmEnableStatusEnum.ENABLED.name().equals(field)`（常量在前，天然 null-safe），
不新增枚举 helper，不新增 `ScmAllConstants`。

### 1.1 注入依赖字段的默认命名规则（Q2.1.1 收紧）

Q2.1 首轮把「字段名 = 类型名 token 后缀」当缩写判据，判据本身有问题：
`ProductTagService tags` 的字段名与类型后缀 `Service` 毫无 token 关系，于是根本不被识别，
`product` 域报出 `abbreviated = 0` 的**假阴性**。Q2.1.1 起改用下列标准：

**默认规则**

```text
<Type Simple Name 去掉项目级冗余前缀后的 lowerCamelCase>
```

「项目级冗余前缀」**只指根命名空间 `Scm`**（每个类型都有，零信息量）：

```java
ScmDataScopeDao        -> dataScopeDao
ScmWarehouseScopeGuard -> warehouseScopeGuard
```

`Product` / `Purchase` / `Delivery` 是**领域词，不是冗余前缀**，必须保留，
否则字段名丢失领域信息（`ProductTagService` 写成 `tagService` 就分不清商品标签还是采购标签）：

```java
ProductTagService      -> productTagService
ProductCategoryService -> productCategoryService
FileService            -> fileService
FileRelationService    -> fileRelationService
```

**三档判定**

| 档位 | 形态 | 判定 | 例 |
| --- | --- | --- | --- |
| A | 裸技术角色名 | **必须改** | `ProductCategoryDao dao` |
| B | 类型业务名被明显裁剪 | **必须改** | `tags` / `categories` / `skus` / `spus` / `images` / `uom` / `scopeDao` |
| C | 合法角色别名 | 不算债 | `readOnlyDataSource` / `transactionManager` / `errorCode` |

档位 C 是**角色语义**而非偷懒：只读库 / 主库、事务管理器、错误码前缀表达的是
「同一类型在本类里扮演的角色」，属必要信息。判据不是「机械要求 `varName == typeName lowerCamel`」。

**自动规则无法高置信度判断 → `MANUAL_REVIEW`，绝不自动判 `CLEAN`。**

### 1.2 Java 符号改名禁用整文件正则 / 字符串替换（Q2.1.1 起强制）

**默认手段**：IDE Rename Symbol（Shift+F6）/ AST-aware refactor / JavaParser。

环境确实无法符号改名时，才退化为**「精确声明 + 精确引用」替换**：只改
`private final <Type> <old>;` 声明处与 `<old>.` 接收者位置；改完目标文件内
`\b<old>\b` 必须归零且必须能编译。

**禁止**改动：字符串字面量、注解参数、URL / 路由、权限码、`package` / `import` 路径、
与字段同名的其它方法——除非它们本身就是改名目标。

依据是 Q2.1 的**真实事故**：整文件正则改 `query` 时污染了权限字面量、`@PostMapping` URL
与同名方法；改 `batch` 时污染了权限码与端点。参考实现见
`tools/quality/q2_rename_fields.py`（把字符串字面量与注解区间标为保护区，残留引用不为 0 即报错退出）。

> 同样的规则已写入 [`java-code-quality-remediation-plan.md`](./java-code-quality-remediation-plan.md)
> §4.11 / §4.12，作为后续全仓库改名（supplier / customer / purchase / inventory / delivery / finance）的硬约束。

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
| `b842c6b5` `refactor(product): keep renamed lines within the 120-column limit` | 改名顶破 120 列 → 折行修复（见 3.5） |
| `4eff6795` `docs(quality): record the Q2.1 naming and type-safety pilot` | §28 本文档 |
| `c142a1e3` `docs(quality): add the Q2.1 verification results and line-length fix record` | 验证结果与折行记录 |
| —— 以下为 **Q2.1.1 Naming Standard Closure** —— | |
| `refactor(common,product): complete dependency naming cleanup` | 收紧标准后补齐 14 处遗留缩写字段（见 3.6） |
| `chore(quality): harden naming audit against semantic abbreviations` | 加固审计工具 + 新增自测（见 3.7） |

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

### 3.5 折行修复（`b842c6b5`）

改名与新枚举写法**变长了行**（`dao`→`productSpuDao`、`"ENABLED"`→`ScmEnableStatusEnum.ENABLED.name()`），
使 6 个 product 文件顶破 120 列 Checkstyle 上限，被 guard 判成 3 条 NEW + 3 条 GROWN `LineLength`。

修法是**只折这些行**，行为不变（单行 `if` 守卫改成块、包装器链在自己行断开）。
副产品：`ProductImportService` 的 28 条**存量** `LineLength` 一并修掉，
故 `checkstyle` family 由 867 → 839。

> **教训（写进后续域的操作清单）**：改名/换枚举提交后**必须重跑 `checkstyle` + guard**，
> 因为变长会当场变成阻断项；折行**必须手写**，脚本机械断点会切在 `foo.\n method(` 与字符串里
> （实测 10 处语法损坏），每轮折行后都要 `mvn compile` 验证。

### 3.6 Q2.1.1 命名标准收紧后的补齐（`refactor(common,product)`）

#### 3.6.1 §3 复扫表（严格标准，基于改动前真实代码）

按 §1.1 的标准对 `common` + `product` 的 `private final` / `protected final` 依赖字段**手工 + 工具**双向复扫，
输出 `file / type / currentName / recommendedName / decision`（`q2_audit.py --table` 同口径）：

| file | type | currentName | recommendedName | decision |
| --- | --- | --- | --- | --- |
| `common/scope/ScmDataScopeService.java` | `ScmDataScopeDao` | `scopeDao` | `dataScopeDao` | **B 必须改** |
| `product/service/ProductBatchService.java` | `ProductSpuDao` | `spus` | `productSpuDao` | **B 必须改** |
| `product/service/ProductBatchService.java` | `ProductCategoryService` | `categories` | `productCategoryService` | **B 必须改** |
| `product/service/ProductBatchService.java` | `ProductTagService` | `tags` | `productTagService` | **B 必须改** |
| `product/service/ProductQueryService.java` | `ProductSpuDao` | `spus` | `productSpuDao` | **B 必须改** |
| `product/service/ProductQueryService.java` | `ProductSkuDao` | `skus` | `productSkuDao` | **B 必须改** |
| `product/service/ProductQueryService.java` | `ProductImageDao` | `images` | `productImageDao` | **B 必须改** |
| `product/service/ProductQueryService.java` | `ProductCategoryService` | `categories` | `productCategoryService` | **B 必须改** |
| `product/service/ProductQueryService.java` | `ProductTagService` | `tags` | `productTagService` | **B 必须改** |
| `product/service/ProductSpuService.java` | `ProductCategoryService` | `categories` | `productCategoryService` | **B 必须改** |
| `product/service/ProductSpuService.java` | `ProductSkuSyncManager` | `skus` | `productSkuSyncManager` | **B 必须改** |
| `product/service/ProductSpuService.java` | `ProductImageSyncManager` | `images` | `productImageSyncManager` | **B 必须改** |
| `product/service/ProductSpuService.java` | `ProductUomService` | `uom` | `productUomService` | **B 必须改** |
| `product/service/ProductSpuService.java` | `ProductTagService` | `tags` | `productTagService` | **B 必须改** |
| `common/scope/ScmWarehouseScopeGuard.java` | `ScmDataScopeService` | `dataScopeService` | —— | C 合法别名（已是类型名小写） |
| `common/exception/ScmBusinessException.java` | `ScmErrorCode` | `errorCode` | —— | C 合法别名（角色语义） |
| `common/.../ScmW5PgITBase.java`（测试） | `PlatformTransactionManager` | `transactionManager` | —— | C 合法别名（角色语义） |
| `common/.../ScmW5PgITBase.java`（测试） | `SqlSessionFactory` | `sqlSessionFactory` | —— | C（已是类型名小写） |
| `common/.../ScmW2/W3/W5PgITBase.java`（测试） | `ObjectMapper` | `json` | —— | **MANUAL_REVIEW**（单列，不计入 0） |
| `product/manager/ProductImageSyncManager.java` | `FileService` | `fileService` | —— | C（已是类型名小写） |
| `product/manager/ProductImageSyncManager.java` | `FileRelationService` | `fileRelationService` | —— | C（已是类型名小写） |
| `product/**` 各 Controller / Service / Manager | `Product*Service/Dao/Validator` | `product*Service/Dao/Validator` | —— | C（已是类型名小写） |

**结果**：A 档 0 处、B 档 **14 处**、C 档为合法命名、`MANUAL_REVIEW` 3 处（仅 common 测试的 `json`）。
B 档 14 处即下面的整改清单。

> 说明：`product` 域 `FileService files` / `FileRelationService relations` 在 Q2.1 首轮
> 已随 `45343732` 改为 `fileService` / `fileRelationService`（见 §3.3），本轮复扫已确认无残留。

#### 3.6.2 整改清单

Q2.1 首轮把「缩写」判据定得过窄（见 §1.1），漏掉 14 处真实缩写字段。Q2.1.1 按新标准补齐：

| 文件 | 旧字段名 | 新字段名 | 类型 |
| --- | --- | --- | --- |
| `common/scope/ScmDataScopeService` | `scopeDao` | `dataScopeDao` | `ScmDataScopeDao` |
| `product/service/ProductBatchService` | `spus` | `productSpuDao` | `ProductSpuDao` |
| `product/service/ProductBatchService` | `categories` | `productCategoryService` | `ProductCategoryService` |
| `product/service/ProductBatchService` | `tags` | `productTagService` | `ProductTagService` |
| `product/service/ProductQueryService` | `spus` | `productSpuDao` | `ProductSpuDao` |
| `product/service/ProductQueryService` | `skus` | `productSkuDao` | `ProductSkuDao` |
| `product/service/ProductQueryService` | `images` | `productImageDao` | `ProductImageDao` |
| `product/service/ProductQueryService` | `categories` | `productCategoryService` | `ProductCategoryService` |
| `product/service/ProductQueryService` | `tags` | `productTagService` | `ProductTagService` |
| `product/service/ProductSpuService` | `categories` | `productCategoryService` | `ProductCategoryService` |
| `product/service/ProductSpuService` | `skus` | `productSkuSyncManager` | `ProductSkuSyncManager` |
| `product/service/ProductSpuService` | `images` | `productImageSyncManager` | `ProductImageSyncManager` |
| `product/service/ProductSpuService` | `uom` | `productUomService` | `ProductUomService` |
| `product/service/ProductSpuService` | `tags` | `productTagService` | `ProductTagService` |

**改名方式**：未走整文件正则（§1.2）。环境无 IDE 符号改名，故退化为
**精确声明 + 精确接收者**替换，由 `tools/quality/q2_rename_fields.py` 执行：
它把行内字符串字面量与注解区间标为保护区，只在保护区之外替换 `name.` 接收者。
首次 dry-run 就当场拦下 `ProductBatchService:71`
（`if (!"REMOVE".equals(...)) tags.assertUsable(...)` —— 同一行既有字面量又有接收者），
正是 Q2.1 事故的同一类位置；确认字面量 `"REMOVE"` / `"ADD"` 与
`ProductUomController` 的 `/scm/product/uom`、`scm:product:uom:*` 全部**原样未动**。

**顺带处理**：新增字段名变长，使 `ProductBatchService:88`、`ProductSpuService:67/99/100`
共 4 行**新**顶破 120 列（其余超长行均为存量、已在 baseline 上）。按「baseline 只降不升」
手写折行后，4 行全部归零；`ProductQueryService` 的存量超长行未动
（checkstyle baseline identity 不含行号，行移动不让 baseline 失效）。

### 3.7 审计工具加固（`chore(quality)`）

`tools/quality/q2_audit.py` 的命名检测被重写：

- 新增协作方过滤（只检查 `*Service` / `*Dao` / `*Manager` / `*Validator` 等注入类型，
  值对象 / 表单 / 实体的业务字段名不在范围内）。
- 三档判定 A / B / C（§1.1），并新增 `MANUAL_REVIEW` 兜底——**自动规则判不了的一律交人工，
  绝不自动判 CLEAN**。
- 输出 `file / type / currentName / recommendedName / decision` 明细表（`--table`）。
- 新增 `tools/quality/test_q2_audit_naming.py`：21 条用例钉住三档边界与
  「unknown 不得判 CLEAN」的回归守卫。

---

## 4. 试点后数据（after）

`python tools/quality/q2_audit.py`（Q2.1.1 加固后重测）：

| 指标 | common | product |
| --- | ---: | ---: |
| 协作方字段（判定范围） | 35 | 62 |
| **裸角色字段（A，必须改）** | **0** | **0** |
| **缩写型字段（B，必须改）** | **0**（4 → 0） | **0**（13 → 0） |
| 合法角色别名（C，不算债） | 32 | 62 |
| `MANUAL_REVIEW`（不计入 0） | 3（`ObjectMapper json` ×3） | 0 |
| 无信息局部变量 | 0 | **1**（余 1 处在测试 `ProductPgIT`） |
| magic string 主代码 | 0 | **3**（25 → 3，余下即 §3.4 有意保留） |
| magic string 测试代码 | 28 | 67 |

> **验收口径（§8）**：`common` + `product` 的**高置信度语义缩写依赖字段必须为 0**。
> 上表两域 B 档均为 0 → 满足。`MANUAL_REVIEW` 单列，**不计入 0**：本轮的 3 条都是测试里的
> `ObjectMapper json`（既非类型名、也非已知角色别名，工具按约定交人工，不自动放行）。

> before 口径见 §2。Q2.1 首轮用旧算法测得 product「缩写 = 0」是**假阴性**（§1.1），
> 真实 before 是 13 处；Q2.1.1 补齐后归零。

门禁口径（`quality_guard.py check --checkstyle`，Q2.1.1 capture 后）：

| family | before | Q2.1 after | Q2.1.1 after | Δ(总) |
| --- | ---: | ---: | ---: | ---: |
| `generic-dependency-field` | 71 | 52 | **52** | **−19** |
| `magic-string-domain-literal` | 191 | 169 | **169** | **−22** |
| `raw-permission-literal` | 285 | 285 | 285 | 0 |
| `stage-comment` | 695 | 695 | 695 | 0 |
| `legacy-scm-package` | 0 | 0 | 0 | 0 |
| `checkstyle` | 867 | 839 | **839** | **−28** |

**无任何 family 增长（全部持平或下降）**，`RESULT: PASS`，**未使用 `--allow-growth`**。

> Q2.1.1 的这些改名不改变 `generic-dependency-field` 计数：该 family 只抓**裸角色名**
> （档位 A），而本轮清理的是档位 B（业务名被裁剪）——它从来不在 guard 的账上，
> 只在 `q2_audit.py` 的审计里可见。这也是为什么 §1.1 要单独收紧审计规则。

> 期间 guard **确实**拦下过一次增长：Q2.1 改名/换枚举使 6 个文件顶破 120 列，
> 报 3 条 NEW + 3 条 GROWN `LineLength`；折行修复后归零（见 3.5）。这条链
> 证明了「baseline 只降不升、`--allow-growth` 未使用」的棘轮真的在起作用。

---

## 5. common 域的判定（**Q2.1.1 已推翻首轮结论**）

> **⚠️ 首轮结论已作废。** Q2.1 曾判定 `common` 「无必须改动」，并把 4 条
> 缩写字段全部当作「诚实命名」放行。Q2.1.1 收紧标准后，其中
> **`ScmDataScopeDao scopeDao` 被确认是真实缩写，已改名 `dataScopeDao`**。
> 作废理由：首轮把「去掉 `Data` 是正确简写」当作领域判断，但按 §1.1 的默认规则，
> `ScmDataScopeDao` 去 `Scm` 前缀后就是 `dataScopeDao`——`scopeDao` 丢掉了
> 类型里那个 `Data` 业务词，属**档位 B，必须改**。

Q2.1.1 复查后 `common` 的结论：

- **裸角色字段（A）= 0**。
- **缩写型字段（B）= 0**（原 1 处 `scopeDao` 已改名为 `dataScopeDao`）。
- **合法角色别名（C）保留不改**：
  - `ScmErrorCode errorCode` —— 角色语义（错误码），保留。
  - `ScmDataScopeService dataScopeService` —— 已是类型名小写（`ScmWarehouseScopeGuard` 内），保留。
  - `PlatformTransactionManager transactionManager`（测试）—— 角色语义，保留。
- **`MANUAL_REVIEW` = 3**：均为测试里的 `ObjectMapper json`（`ScmW2/W3/W5PgITBase`）。
  既不等于类型名也不属白名单角色别名，工具按约定交人工、不自动判 CLEAN；本轮不改名。
- **magic string = 28，全部在测试源码**（主代码 0）。测试里的枚举字面量是**夹具**（fixture），
  不是裸露的业务逻辑；门禁只约束主代码，故不构成本轮 gated 债务。
- **无信息局部变量 = 0**。

因此 Q2.1.1 为 `common` 产出了**1 处独立生产变更**（`ScmDataScopeService.scopeDao` → `dataScopeDao`），
并随 `refactor(common,product)` 提交。

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
| `MANUAL_REVIEW` 的 `ObjectMapper json`（3 处，测试） | 非类型名、非白名单角色别名；工具交人工，本轮不改 |

### 6.2 重复枚举检查（§14，只记录不合并）

本轮未发现 product 域内需要合并的重复枚举。跨域词形相撞（如 `"NON_STANDARD"` 同时被
`ScmOrderProductTypeEnum` 与 `ScmProductTypeEnum` 声明、`"NORMAL"` 同时被三个 finance/inventory 枚举声明）
属**跨域词汇重合**，非重复定义，不合并，留待后续域评估。

### 6.3 试点外域的治理顺序（建议，**本轮不启动**）

`generic-dependency-field` 剩余 52 条与 `magic-string` 剩余 169 条主要落在历史遗留域。
建议后续按依赖方向由内向外推进：`customer` / `supplier` → `pricing` → `purchase` →
`inventory` / `sorting` / `delivery` → `finance`。**每完成一个域即 `capture` 一次**，让账本单调收缩。

> 后续域推进时**必须沿用 §1.1 的收紧标准与 §1.2 的改名方式**：把 `q2_audit.py`
> 指向目标域（`--domain ...`）先出 `MANUAL_REVIEW` 清单，再用符号级改名清理，
> **不要**用整文件正则。历史遗留域里的缩写字段预计远多于 product（本轮 product 一域就有 13 处）。

---

## 7. 本轮验证结果（§26）

| 验证 | 结果 |
| --- | --- |
| 工具单测 `python -m unittest discover -s quality -p 'test_*.py'` | **85 / 85 OK**（Q2.1 为 64；Q2.1.1 新增 21 条命名判定用例） |
| `python tools/quality/q2_audit.py` | common B=0 / product B=0；`MANUAL_REVIEW` 3（common 测试） |
| `quality_guard.py check --checkstyle` | PASS（无 NEW / GROWN，未用 `--allow-growth`） |
| `mvn -pl sa-admin -am -o compile` | 成功（改名后语法有效） |
| `mvn -pl sa-admin spotless:check` | PASS |
| 定向 `mvn test -Dtest='Product*'` | **95 tests / 0 failures / 0 errors / 0 skipped** |
| 定向 `mvn test`（common 显式类列表） | **29 tests / 0 failures / 0 errors / 0 skipped** |
| `python tools/migration_checksum_guard.py check` | PASS（drift 0 / missing 0 / renamed 0 / unbaked 0） |
| `python tools/verify.py quality` | PASS |
| `python tools/verify.py backend`（一次性干净库） | **sa-base 8 + sa-admin 1194 = 1202 tests / 0 failures / 0 errors / 5 skipped** |

> 5 skipped 全部来自 `F0FileStorageCloudIT` —— **设计内的云存储跳过**（无云凭据），
> 与历史基线一致。1202 与 `da7103ee` 记录的参考值完全相符。
>
> **环境备注**：`verify.py backend` 必须注入三件套（`XSY_V2_DB_URL` 带 `jdbc:p6spy:` 前缀、
> `XSY_V2_DB_PASSWORD`、`SPRING_DATA_REDIS_PASSWORD`），且**要用一次性干净库**。
> 长跑共享库 `xsy_scm` 累积的 `inventory_reservation` ACTIVE 行会让
> `PurchaseDemandSummaryPreviewIT` 在全量中偶发红、单跑绿 —— 这是已定性的跨测试数据污染，
> 不是本轮回归（本轮未触碰 purchase / inventory 任何文件）。配方见
> [`package-migration-readiness.md`](./package-migration-readiness.md) §7.5。
>
> **建库注意（本轮新踩到）**：一次性库**不要预建 `xsy_v2` schema**。
> 必须只 `CREATE DATABASE`，让 Flyway 自己建 schema —— 否则
> `flyway_schema_history` 里不会出现那条 `version IS NULL` 的
> `<< Flyway Schema Creation >>` 基线行，
> `ScmPurchaseMigrationIT.flywayHistoryIsAppendOnly` 的 `isEqualTo(1)` 会失败。
> 该失败与命名改动无关（本轮未触碰 purchase / 任何迁移文件），是建库方式导致的。
> 另外：DB 容器超级用户是 **`xsy_scm_app`**（不是 `postgres`），建库要
> `docker exec xsy-scm-postgres-1 psql -U xsy_scm_app -d postgres -c "CREATE DATABASE <db> OWNER xsy_scm_app;"`。

---

## 8. 阶段判定

```text
Q2.1 NAMING + TYPE SAFETY PILOT COMPLETE
```

依据：

1. `common` + `product` 的**高置信度语义缩写依赖字段 = 0**（A 档 0、B 档 0，§4）。
2. 命名标准已**收紧成文**（§1.1）并同时写入整改计划 §4.11，后续域有可执行的判据。
3. **整文件正则改名被明令禁止**（§1.2 + 整改计划 §4.12），并有参考实现与自测兜底。
4. 门禁无任何 family 增长，`--allow-growth` 未使用。
5. 功能回归（定向 + 后端全量）全绿。

**停止条件**：本节写定后即停止。**不自动进入 `supplier` / `customer`，不继续 Finance，不 push。**

---

## 附录 A：复现命令

```bash
# 命名 / 类型安全审计（只读，不阻断）
python tools/quality/q2_audit.py
python tools/quality/q2_audit.py --table            # file / type / currentName / recommendedName / decision
python tools/quality/q2_audit.py --domain product --json

# 依赖字段改名（默认 dry-run；只改声明 + 接收者，保护字符串/注解）
python tools/quality/q2_rename_fields.py            # 预览
python tools/quality/q2_rename_fields.py --apply    # 写回

# 门禁
python tools/quality/quality_guard.py check --checkstyle
python tools/quality/quality_guard.py capture --checkstyle   # 仅在确认无增长后
# 注意：check 读的是已生成的 checkstyle result.xml，改了 Java 要先重跑：
#   cd xsy-scm-server && mvn -N checkstyle:check

# 工具单测
cd tools && python -m unittest discover -s quality -p 'test_*.py'

# 后端全量（一次性干净库 + 三件套）
export XSY_V2_DB_URL='jdbc:p6spy:postgresql://127.0.0.1:15432/<fresh_db>?currentSchema=xsy_v2&ApplicationName=xsy-scm-v2-test'
export XSY_V2_DB_USERNAME='xsy_scm_app'
export XSY_V2_DB_PASSWORD="$(grep -m1 '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)"
export SPRING_DATA_REDIS_PASSWORD="$(grep -m1 '^REDIS_PASSWORD=' .env | cut -d= -f2-)"
python tools/verify.py backend
```
