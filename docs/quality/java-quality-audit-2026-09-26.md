# XSY-SCM Java 代码质量审计（Quality Q0）

> 阶段：Quality Q0 — Java Quality Baseline
> 基线提交：`main @ a93a7772b2f56107778e834559d427176e3af0da`（`git fetch` 后确认 `origin/main` 与之相同，且为 HEAD 的祖先）
> 测量时间：2026-09-26，本轮整改**之前**的代码状态；本轮自身新增的文件已在 §1.2 单列
> 整改基线文档：[`java-code-quality-remediation-plan.md`](./java-code-quality-remediation-plan.md)
> 业务状态：F1-1 / F1-2A / F1-2B / F1-2C / F1-3A / F1-3B 已完成；F1-3C / F1-4 / F1-5 / F1-6 / F1-8 暂停

本报告只报数字与事实，不报结论性评价；每一条数字都给出**扫描范围 / 排除项 / 检测规则**，
可以按 §附录 A 的命令复现。凡属启发式（非精确）测量的，都在 §7「检测局限」里写明它可能错在哪。

---

## 1. 本轮建立了什么

### 1.1 门禁

| 组件 | 位置 | 作用 | 是否阻断 |
| --- | --- | --- | --- |
| `.editorconfig` | 仓库根 | 字符集 / 换行 / 缩进 / 行尾空白策略 | 否（编辑器约定） |
| Checkstyle | `tools/quality/checkstyle.xml` + `maven-checkstyle-plugin 3.6.0` / `checkstyle 10.21.1` | 命名、import、行宽、制表符、文件末尾换行、单一顶层类 | 报告模式；由 guard 棘轮判定 |
| Spotless | `spotless-maven-plugin 2.44.3` | 变更文件的行尾空白与末尾换行 | 是（`mvn spotless:check`） |
| Quality Guard | `tools/quality/quality_guard.py` + `java_source.py` | Checkstyle 看不见的四类语义缺陷 + 迁包预算 | 是（新增 / 增长即失败） |
| ArchUnit | `sa-admin/src/test/.../scm/ScmArchitectureTest.java` + `archunit-junit5 1.4.1` | 分层方向、跨域边界、测试库泄漏 | 是（随 `mvn test` 执行） |
| 统一入口 | `python tools/verify.py quality` | Checkstyle → Spotless → Guard | 是 |

`backend` / `frontend` / `e2e` 三个既有入口的行为**一字未改**（整改计划 §27 的硬约束）；
`quality` 是新增 scope，并被 `all` 放在第一位（秒级、先失败）。

### 1.2 本轮自己新增的代码

| 文件 | 行数 | 性质 |
| --- | --- | --- |
| `tools/quality/java_source.py` | Java 源分段器（注释 / 代码 / 字符串字面量三种视图） | 质量基础设施 |
| `tools/quality/quality_guard.py` | 规则 + baseline 棘轮 + scan/capture/check 三模式 | 质量基础设施 |
| `tools/quality/scm_metrics.py` | 本报告全部数字的测量脚本 | 审计工具，不阻断 |
| `tools/quality/checkstyle.xml` | 规则集 | 质量基础设施 |
| `ScmArchitectureTest.java` | 7 条 ArchUnit 规则 | 测试 |
| `tools/quality/baseline/*.txt` | 6 个 baseline，共 1,221 行 | 历史债务账本 |

### 1.3 baseline 增长记录（工具要求写明理由）

`legacy-scm-package`：`846 → 847`（唯一一次增长，用 `--allow-growth` 显式确认）。
原因：Q0 必须把 `ScmArchitectureTest` 放进 SCM 测试包，才能与既有 SCM 测试同一入口执行。
该文件与其余 846 个文件一样，在 Q1 迁包后一并归零；除此之外本轮没有任何生产代码文件增加。

---

## 2. 体量（计划 §28 第 1、2、20、21、22 条）

| # | 指标 | 数值 |
| --- | --- | --- |
| 1 | SCM 生产 Java 文件 | **692** |
| 1 | SCM 生产 Java 行数 | **46,858** |
| 2 | SCM 测试 Java 文件 | **154**（本轮 +1 → 155） |
| 2 | SCM 测试 Java 行数 | **38,570** |
| — | mapper XML：引用旧 SCM 包名的文件 | **64**，全在 `sa-admin/src/main/resources/mapper/` 下 |
| — | ↳ 其中 `mapper/scm/**` | 46 |
| — | ↳ 其中 `mapper/business/scm/**` | 18 |
| — | `mapper/` 目录下 XML 总数 | 78 |

**检测规则**：`**/*.java` 递归；文件数按路径计数，行数按 `\n` 计数（`errors="replace"` 读入）。
**排除项**：`target/`、`project-reference-examples/**`、`xsy-scm-miniapp/**`、
`sa-base/**` 与 `module/system/**`、`module/support/**`（SmartAdmin 底座，非本团队所有）。

> **发现（仓库组织）**：自定义 SQL 落在**两套目录约定**下 —— Wave 1–3 的写进
> `mapper/business/scm/...`，Wave 4 之后的写进 `mapper/scm/...`。两者都合法（MyBatis 按
> `classpath*:mapper/**/*.xml` 扫），但同一层职责有两个去处会让 Q1 迁包和后续检索都多一倍心智。
> 归一属 Q4，不在本轮动。

按域的分布（生产文件数）：

```text
inventory 122   purchase  87   order 78   product 78   finance 57
delivery   46   pricing   46   customer 36   sorting 28   supplier 27
common     27   report    26   warehouse 21   screen 9   dashboard 4
```

---

## 3. Enum 与领域词汇表（计划 §8、§28 第 3 条）

| 指标 | 数值 |
| --- | --- |
| SCM 生产代码中的 enum 声明 | **67** |
| ↳ 排除 `*ErrorCode`（程序标识符，不是状态词汇） | **54** |
| enum 常量名去重后的词汇表规模 | **127** |
| 同名常量被 ≥ 2 个 enum 声明 | **28** |
| 除自身文件外没有被任何生产代码按简单名引用的 enum | **16** |

**检测规则**：在注释已抹除的代码视图上匹配 `enum <Name> ... {`，取深度 1 的常量段（到第一个 `;` 或
闭合括号为止），剥掉构造参数与常量体、剥掉注解，按逗号切分取首标识符。「未被引用」= 该 enum 的简单名
在其他任何生产文件的**代码视图**中不出现（注释与 Javadoc 内的提及不算引用）。

### 3.1 最重要的发现：早期波次的 Enum 是「文档」，不是「类型」

16 个从未被生产代码按类型引用的 enum 包括
`ScmOrderStatusEnum`、`ScmPurchaseStatusEnum`、`ScmOrderReturnStatusEnum`、`ScmReceiptStatusEnum`、
`ScmShelfStatusEnum`、`ScmSettleModeEnum`、`ScmOrderSourceEnum`、`ScmWeighingSourceEnum` 等；
而**同一批状态在代码里以裸字符串出现**：

```text
purchase/ 包内： "DRAFT"×8  "SUBMITTED"×7  "PARTIALLY_RECEIVED"×6  "RECEIVED"×4  "SHORT_CLOSED"×4  "CONFIRMED"×3
全 SCM：       set(状态|类型|来源|方式|方向|分录类型)("...") 形式的调用  29 处
```

也就是说 `Scm*Enum` 与 DB CHECK 是齐的，**Java 侧却没用它们**。这正是计划 §8 要的
「Java Enum ↔ DB CHECK ↔ Contract Test 一致」链条上缺的那一环，属 Q2。

### 3.2 反向证据：Finance 已经做到了

按域统计本轮门禁命中的债务，最新域的表现明显更好：

| 域 | 裸角色名字段 | 已有 Enum 仍硬编码 | 裸权限串注解 |
| --- | --- | --- | --- |
| finance | **0** | **1** | **0**（走 `FinanceConstant.*_PERM`） |
| product | 17 | 25 | 有 |
| order | 7 | 55 | 有 |
| purchase | 0 | 46 | 有 |

`FinanceConstant` 已经是**权限目录常量类的在库范式**（19 个 `*_PERM` 常量），
F1-3A / F1-3B 的两个 Controller 也已经用 `@SaCheckPermission(FinanceConstant.RECEIPT_ADD_PERM)` 而非裸串。
所以 §6 的 Permission Catalog 不是新发明，是把已有做法推广到 12 个域。

### 3.3 「同名常量多 enum」需要人工裁决，不当缺陷

`PENDING` 由 7 个 enum 声明、`CANCELLED` 6 个、`DRAFT` 6 个 —— 这**多数是正确设计**：
每个聚合各自持有自己的状态机，不共享一个大 enum。
但 `NORMAL` 同时出现在 `ScmFinanceEntryTypeEnum`、`ScmFinanceReverseEntryTypeEnum`、
`ScmInventoryWarningStatusEnum`，`MANUAL` 出现在 5 个来源类 enum 里 —— 这两类是
「同一业务概念是否应该复用同一词汇」的候选，需要裁决，不能由工具判定。
28 条全量清单在 `scm_metrics.py` 的 `samples.constants_in_multiple_enums`。

---

## 4. Magic String（计划 §7、§19、§28 第 4、5 条）

| 指标 | 数值 |
| --- | --- |
| **A 类**：字面量命中已有 SCM enum 常量（**门禁项**） | **191** 处 / **42** 个字面量 / **46** 个文件 |
| **B 类**：全大写词汇字面量但仓库无对应 enum（仅报告） | **173** 处 / **103** 个字面量 |
| 合计 ALL-CAPS 词汇字面量命中 | 364 处 |

**检测规则**：只取「注释已抹除」位置的字符串字面量，**整串精确相等**才算命中 ——
`"WHERE status = 'ENABLED'"` 这类 SQL 片段的内容不是 `ENABLED`，不会计入；
text block（SQL / JSON / 文档）整体跳过。A 类命中后在明细里记下是**哪个 enum 声明了该常量**。

**为什么只把 A 类设成门禁**：B 类里 `ROW_LIMIT`、`FILE_EMPTY`、`HEADER_INVALID`、`CELL_INVALID`
是 Excel 导入的**错误码文本**，`SRT` / `STK` / `TRF` / `LGV` / `CVT` 是**单号前缀**，
`ALL` / `REQUIRED` 是筛选语义 —— 它们该不该建 enum 是需要逐条裁决的业务问题，
工具判成债务只会产出一份没人能执行的清单。B 类前 15 名：

```text
ALL 8   ROW_LIMIT 6   PLANNED 5   DISPATCHED 5   ALL_ENABLED 4   FILE_EMPTY 4
DEFAULT_SKU_INVALID 4   PURCHASE_ORDER 4   SHEET_COUNT 3   HEADER_INVALID 3
CELL_INVALID 3   COLUMN_UNEXPECTED 3   REQUIRED 3   TOO_LONG 3   DECIMAL_INVALID 3
```

A 类前 10 名：

```text
ENABLED 27 (ScmEnableStatusEnum / ScmWarehouseStatusEnum)   PENDING 16   DRAFT 16
CONFIRMED 14 (4 个 enum)                                    CANCELLED 13 (6 个 enum)
ON_SHELF 9   SUBMITTED 8   ACTIVE 7   PARTIALLY_RECEIVED 7   STANDARD 7
```

按域：order 55 · purchase 46 · delivery 31 · product 25 · pricing 14 · sorting 7 · dashboard 5 ·
inventory 4 · customer 3 · finance 1。

**测试源码未纳入扫描**（计划 §19 明确要求避开测试夹具误报）。可测的同名指标作参考：
SCM 测试源码里的 wildcard import 86 处（详见 §5.5）。是否把测试纳入门禁留 Q2 裁决。

---

## 5. 其余门禁项

### 5.1 Permission（计划 §9、§20、§28 第 6 条）

| 指标 | 数值 |
| --- | --- |
| `@SaCheckPermission(...)` 注解总数 | **269** |
| ↳ 内联字面量形式 | **267**（250 单值 + 16 双值 + 1 三值） |
| ↳ 引用目录常量形式 | **2**（均在 finance） |
| 权限字面量出现次数（**门禁项**） | **285** |
| 权限值去重 | **163** |
| 出现 > 1 次的权限值 | **51** |
| 涉及文件 | **43** |
| SCM Controller 总数 | **45** |

**检测规则**：在代码视图上匹配 `@SaCheckPermission\s*\(([^)]*)\)`，对每个匹配再抽出全部
`"scm:..."` 字面量，**每个字面量记一条**。所以一个
`@SaCheckPermission(value = {"a","b"}, mode = SaMode.AND)` 记 2 条。

> 逐行 `grep` 会**少数 18 条**：它既看不见数组里的第二个值，也看不见跨行的那 1 个注解
> （实测：grep 267 行 vs 工具 285 处）。这正是「纯文本 grep 不可信」的一个具体样本。

163 个权限值按域：inventory 38 · product 23 · order 18 · purchase 18 · delivery 13 ·
customer 11 · pricing 11 · sorting 9 · supplier 7 · warehouse 7 · report 6 · todo 1 · screen 1。
**finance 为 0**（它已经有目录常量）。

权限链保持现状，**不新建第二套**：`t_role → t_role_menu → t_menu.api_perms → LoginManager →
Spring Cache → Redis/Caffeine`；权限 vocabulary 不进 `application.yml` / `sa-security.yml`
（计划 §6.1：权限码是稳定程序契约，不是环境配置）。

### 5.2 Bean Validation（计划 §10、§28 第 7、8 条）

| 指标 | 数值 |
| --- | --- |
| `domain/form` 类数量 | **146** |
| 约束注解总数 | **808** |
| 带 `message` | **20** |
| **不带 `message`（债务）** | **788（97.5%）** |
| `@Pattern` 用 `\|` 重复枚举值 | **62** |

按注解（total / 有 message）：

```text
NotNull      230 / 12        Size          178 / 1        Min           106 / 0
Positive      81 / 0         Pattern        71 / 5        NotBlank       61 / 0
NotEmpty      28 / 0         Max            21 / 0        DecimalMin     16 / 0
Digits        12 / 0         DecimalMax      2 / 0        AssertTrue      2 / 2
```

**检测规则**：扫描范围**只有** `src/main/java/**/module/scm/**/domain/form/**`（Spring 真正校验的
API 边界对象）；逐个注解名匹配后，用**括号配平**读出实参整段，再看该段里有没有 `message`。
`@Valid` / `@Validated` 不表达取值约束，已从注解表中排除。

**为什么「无 message」是正式债务而不是风格问题** —— 已复核 `GlobalExceptionHandler`：

```java
// sa-base/.../handler/GlobalExceptionHandler.java:57-60
if (e instanceof MethodArgumentNotValidException) {
    List<FieldError> fieldErrors = ...getFieldErrors();
    List<String> msgList = fieldErrors.stream().map(FieldError::getDefaultMessage)...
    return ResponseDTO.error(UserErrorCode.PARAM_ERROR, String.join(",", msgList));
}
```

`defaultMessage` 被**原样拼进响应**。所以缺 message 的 `@NotNull` 会让客户看到
`must not be null`，缺 message 的 `@Size` 会看到 `size must be between 0 and 500`。
这是用户可见文本，不是内部风格。

### 5.3 注释（计划 §12、§21、§28 第 10 条）

| 指标 | 数值 |
| --- | --- |
| 阶段流水注释出现次数（**门禁项**） | **695** |
| 涉及文件 | **198 / 692（28.6%）** |

命中构成：

```text
§<n> 设计稿小节引用 283     Q13 49   设计稿 30   Wave <n> 26   Q17 23   Q11 21
D-3 17   Q27 16   D-5 13   Q19 12   Q9/Q10/D-4/Q7 各 10-11
F1-4 9   F1-3C 8   F1-5 7   F1-3A 6   F1-3B 5   F1-1 4   F1-2x 4   本轮 2   （其余为长尾 Q/D 编号）
```

**检测规则**：只在**注释文本**上匹配（代码视图已抹除注释，两者互不污染）。`§<n>` 与 `Wave <n>`
的数字被折叠成占位符，因为设计稿重编小节号会让整批 identity 改名，制造「全修完 + 全新增」的假象；
`F1-3A` / `Q13` / `D-3` 保留原值，它们是具体裁决的指称。

**刻意排除的两个词**：`提交` 与 `测试`。逐条核对样本后发现它们在业务文本里是正常用语
（「提交预览时确认的订单集合」「APPROVED 提交前」「只对本次显式提交且仍 ACTIVE 的订单计次」），
纳入规则只会产出无法清理的误报。因此**门禁不查它们，计划 §12 的扫描清单里它们只是提示词**。

按 §12 的分类口径：695 条里绝大多数属 **D 类（开发历史，应迁 docs / Git）**；
`Q`/`D` 编号中有一部分同时承载 **B 类（不变量）** 语义 —— 那些不能只删不改写，
必须把编号换成业务陈述（例：`（Q21）` → `「收付款方式不允许运营增删」`），
这属 Q3 的逐条整理，本轮一条都没动。

### 5.4 Import 与格式（计划 §14、§17、§28 第 9 条）

Checkstyle 在 SCM 生产代码上报 **871 条 / 231 个文件**：

| Check | 数量 |
| --- | --- |
| `AvoidStarImport` | **360** |
| `UnusedImports` | **267** |
| `LineLength`（>120） | **243** |
| `RedundantImport` | 1 |
| TypeName / MethodName / MemberName / ParameterName / LocalVariableName / ConstantName | **0** |
| FileTabCharacter / NewlineAtEndOfFile / OneTopLevelClass / OuterTypeFilename / ArrayTypeStyle / UpperEll / ModifierOrder | **0** |
| 未解析 / 配置错误 | 0（692 个文件全部成功解析） |

> **这条 0 很重要**：Checkstyle 的命名检查**全绿**。也就是说本仓库的命名问题**不是驼峰格式问题，
> 而是语义问题** —— 恰好印证计划 §4.10 的判断：Checkstyle 只能验 `camelCase`，
> 判不出 `ProductCategoryDao dao` 语义过弱，所以必须有 §18 的自定义 guard。

SCM 测试源码另有 86 处 wildcard import（未纳入本轮门禁，同 §4 的测试口径）。

### 5.5 类复杂度（计划 §13、§28 第 11、12 条）

审查触发线：> 400 行 / > 10 个注入依赖 / > 15 个方法。**只是触发审查，不是判坏。**

| 指标 | 数值 |
| --- | --- |
| Service / Manager / Controller 类总数 | **127** |
| 命中 ≥ 1 条触发线 | **17** |
| ↳ > 400 行 | **9** |
| ↳ > 10 个注入依赖 | **5** |
| ↳ > 15 个方法 | **14** |

命中清单（行数 / 依赖 / 方法）：

```text
 997 /  3 / 23   inventory/service/InventoryCommandService.java
 878 /  8 / 43   product/service/ProductImportService.java
 782 / 15 / 19   purchase/service/PurchaseReceiptService.java        ← 计划 §13 点名
 563 / 13 / 25   delivery/service/DeliveryRouteService.java          ← 计划 §13 点名
 511 /  8 / 27   purchase/service/PurchaseQueryService.java
 505 / 10 / 16   purchase/service/PurchaseOrderService.java
 462 /  5 / 39   report/controller/ScmReportController.java
 456 / 13 / 27   order/service/SalesOrderService.java                ← 计划 §13 点名
 409 /  6 / 14   inventory/service/InventoryConversionService.java
 374 / 10 / 17   sorting/service/SortingTaskService.java
 356 /  4 / 16   order/service/SalesOrderImportService.java
 333 / 12 /  6   purchase/service/PurchaseDemandService.java
 282 / 11 / 15   order/service/OrderReturnService.java
 201 /  2 / 17   product/service/ProductTagService.java
 200 /  6 / 23   delivery/controller/DeliveryRouteController.java
 200 /  2 / 16   product/service/ProductCategoryService.java
 160 /  2 / 16   order/controller/SalesOrderController.java
```

计划 §13 点名的四个里，`PurchaseReceiptService`（15 依赖）、`DeliveryRouteService`（13）、
`SalesOrderService`（13）都同时命中「行数 + 依赖数」两线，`FinancePaymentService` 未命中任何线。
`ProductImportService` 43 个方法、`ScmReportController` 39 个方法是另一种形态的过载（端点/解析堆积），
`PurchaseDemandService` 12 依赖 6 方法则是编排堆积 —— 三者要拆的东西不同，不能用一个模板处理。

### 5.6 重复造轮子（计划 §11）

| 指标 | 数值 |
| --- | --- |
| 自己重写框架工具方法的私有静态函数 | **5** |
| `ScmStringUtils` / `ScmObjectUtils` / `ScmCollectionUtils` 这类二次包装类 | **0** |

```text
finance/service/FinancePaymentService.java:243   trimToNull()
finance/service/FinanceReceiptService.java:150   trimToNull()
sorting/service/SortingTaskService.java:355      trimToNull()
screen/service/ScreenDataService.java:310,314    nullToZero()
```

计划 §11 的怀疑**逐字命中**：Finance 里确实有 `private static String trimToNull(...)`，
两处可直接换成已在依赖里的 `StringUtils.trimToNull(...)`（`commons-lang3 3.18.0`）。
`nullToZero` 是数值语义、带 null 与 0 的业务区分，属 XSY 语义工具该做的事，但要挪到有业务含义的
具名工具里（`ScmDecimalStrings` 一类），而不是留在 Service 里当私有方法。

### 5.7 单字母变量（计划 §6.3、§28 第 18 条）

| 指标 | 数值 |
| --- | --- |
| 单字母**成员变量** | **0** |
| 单字母局部变量 / lambda 形参（`i` `j` 除外） | **73** |

**检测规则**：注释抹除后的代码视图上匹配 `var <单字母> =`，以及 `(<单字母>) ->` 与 `<单字母> ->` 两种
lambda 形参；`i` / `j` 按 §4.8 例外放行。

样本（逐条回读原文确认，非误报）：

```java
customer/service/CustomerSkuVisibilityService.java:81   var e = byId.get(r.getId());
order/manager/OrderSnapshotFactory.java:22              var x = new SalesOrderItemEntity();
order/service/OrderRefundService.java:67                var r = refunds.selectById(id);
delivery/service/DeliveryRouteService.java:267          var d = drivers.selectById(route.getDriverId());
```

`var e = byId.get(...)` 尤其要改：`e` 在本仓库的约定位置是 catch 里的异常，
用它当实体局部变量会让「这段代码在读异常还是在读实体」需要回看上下文才能确定。

**不设门禁的理由**：`i` / `j` 的循环边界、以及极短 lambda 里的 `id -> name` 映射，
机器判不出「极短且无歧义」的例外（计划 §6.2 明确要求保留这种例外）。留 Q2 逐条改。

---

## 6. 命名：本轮重点（计划 §6、§6.5、§18、§28 第 13–17、19 条）

### 6.1 裸角色名依赖字段（**门禁项**）

| 指标 | 数值 |
| --- | --- |
| 命中总数 | **71** |
| 涉及文件 | **64** |
| ↳ `service` | **36** |
| ↳ `dao` | **24** |
| ↳ `query` | **6** |
| ↳ `validator` | **3** |
| ↳ `writer` | **2** |
| ↳ `manager` / `repository` / `mapper` / `reader` / `client` | **0** |

按域：product 17 · pricing 13 · inventory 7 · order 7 · customer 6 · delivery 5 · warehouse 4 ·
supplier 4 · sorting 1 · **finance / report / screen / dashboard / common 各 0**。

### 6.2 可完整化的缩写字段（启发式，不设门禁）

| 指标 | 数值 |
| --- | --- |
| 缩写字段（不含 §6.1 已单列的裸名） | **107** |
| ↳ 含裸名的合计 | **170** |

样本与建议写法：

```text
CustomerController#service                 -> customerService              （裸名）
CustomerService#dao                        -> customerDao
CustomerService#validator                  -> customerValidator
SalesOrderService#query                    -> salesOrderQueryService
PriceBatchService#writer                   -> priceBatchWriter
ScmTodoQueryService#warningQueryService    -> inventoryWarningQueryService
DeliveryCandidateOrderQueryService#dao     -> deliveryCandidateOrderDao
```

**为什么不设门禁（这是本轮一个明确的判断，不是遗漏）**：同一个启发式也会把
`ScmDataScopeService#scopeDao -> scmDataScopeDao`、`CustomerQueryService#scopeService ->
scmDataScopeService` 判成债务 —— 而被判「丢失」的 `Scm` 前缀恰恰是**噪音前缀**，
去掉它才是更好的写法。要把这个启发式变成门禁，必须逐字段人工裁决「丢掉的词是有意义的领域词，
还是系统级前缀」，107 条一次做完不是 Q0 的范围。它作为 Q2 的工作清单使用。

### 6.3 Top 30 典型文件

分数 = 裸角色名字段 + 缩写字段 + 已有 Enum 仍硬编码 的命中数。

```text
分  裸  缩  硬  文件
25   1   0  24   order/service/SalesOrderService.java
23   0   1  22   delivery/service/DeliveryRouteService.java
22   0   0  22   purchase/manager/PurchaseOrderStateMachine.java
15   1   0  14   product/service/ProductImportService.java
13   0   0  13   order/service/OrderReturnService.java
 8   0   0   8   order/manager/OrderStateMachine.java
 8   0   0   8   purchase/service/PurchaseReceiptService.java
 7   0   2   5   dashboard/service/ScmTodoQueryService.java
 6   0   0   6   purchase/service/PurchaseOrderService.java
 5   0   0   5   sorting/constant/SortingConstant.java
 4   0   1   3   delivery/service/DeliveryRouteQueryService.java
 4   0   0   4   inventory/support/WarehouseDisableGuardImpl.java
 4   2   0   2   order/controller/SalesOrderController.java
 4   1   0   3   pricing/service/AgreementPriceService.java
 4   1   0   3   pricing/service/CustomerTypePriceService.java
 4   2   0   2   product/service/ProductSpuService.java
 3   1   0   2   delivery/service/DeliveryDriverService.java
 3   1   2   0   inventory/controller/InventoryStocktakeController.java
 3   0   0   3   order/service/OrderRefundService.java
 3   0   0   3   order/service/SalesOrderImportService.java
 3   0   0   3   pricing/manager/PriceValidation.java
 3   1   0   2   pricing/service/PriceHistoryQueryService.java
 3   1   0   2   product/controller/ProductExcelController.java
 3   1   0   2   product/service/ProductCategoryService.java      ← 计划点名的整改案例，第 24 位
 3   1   0   2   product/service/ProductTagService.java
 3   1   0   2   product/service/ProductUomService.java
 3   0   0   3   purchase/manager/PurchaseReceiptQuantityCalculator.java
 2   1   1   0   customer/controller/CustomerController.java
 2   2   0   0   customer/service/CustomerService.java
 2   1   1   0   customer/service/CustomerSkuVisibilityService.java
```

计划 §4.9 的整改案例在仓库里逐字成立（`ProductCategoryService.java:20-21,38,45`）：

```java
private final ProductCategoryDao dao;      // → productCategoryDao
private final ProductSpuDao spuDao;        // → productSpuDao
if (category.getLevel() != 3 || !"ENABLED".equals(category.getStatus()))   // → ScmEnableStatusEnum
public ProductCategoryEntity require(Long id)                              // → require(Long categoryId)
var c = ...  /  rows.forEach(c -> map.put(c.getId(), c));                   // 单字母 lambda 形参
```

它同时命中**本轮四类门禁中的两类**（裸名 `dao`、`"ENABLED"` 硬编码），
外加两条不设门禁的启发式（`spuDao` 缩写、`require(Long id)`）—— 是说明「一个典型 Service 文件
为什么需要 Q2 逐条处理」的最好样本。

---

## 7. 检测局限（每条门禁都可能错在哪，必须先读）

诚实记录，避免后来者把这些工具当成比实际更强的东西。

1. **identity 不含行号**，所以 baseline 不会因无关编辑而失效 —— 代价是：
   同一个文件里「修掉一条 + 新写一条同规则缺陷」计数不变，会漏过。
   `check` 对这种情况既报 IMPROVEMENTS 又可能报 GROWN，靠 IMPROVEMENTS 段人工复核。
2. **Checkstyle 的 message 不参与 identity**，因为 Checkstyle 按 JVM 本机 locale 输出消息
   （本仓库实测：中文环境下消息是「不应使用 '.*' 形式的导入」）。
   若把 message 放进 identity，中文机器上 capture 的 baseline 在英文机器上会读成「全修完 + 全新增」。
   代价：同一文件同一 check 内换了另一个坏 import，工具不区分。
3. **A 类之外的字面量不判**（§4）。B 类需要人裁决，工具判了只会产出无人能执行的清单。
4. **guard 只扫生产代码**，SCM 测试源码不在门禁内（计划 §19 要求避开夹具误报）。
   所以「测试里新增一个裸权限串 / 一个 magic string」目前不会失败。这是**已知缺口**，留 Q2 裁决。
5. **ArchUnit 看不见被 javac 内联的 `static final String` 常量**（实测确认）：
   `ScmDataScopeService` 引用 `ScmReportAccess.COST_QUERY_PERM` 是真实的源码级跨域依赖，
   但字节码里没有对 `ScmReportAccess` 的引用，`commonDoesNotDependOnConcreteDomains` 不会失败。
   这条债务记在 §8.3，靠 Q2 权限目录清理，**不能当成已被门禁放过**。
6. **字段正则只认单行声明**：类型与字段名被拆到两行、或 `@Autowired` 与字段同行的写法会漏。
   实测当前代码里没有这种写法（71 条与逐行 `grep` 完全一致）。
7. **缩写判定是启发式**（§6.2），因此不设门禁。
8. **`legacy-scm-package` 是计数预算，不是文件清单**：先删一个再加一个可以绕过。
   接受这个取舍，因为路径清单会让 baseline 多出 847 行，而这条规则在 Q1 就该整体退役。
9. **Spotless 本轮不做格式化**（§9.2），所以「格式统一」这件事目前只有 `.editorconfig`
   与 Checkstyle 的行宽/制表符两条硬规则在管。
10. **所有 baseline 都是本机一次测量的快照**。别的机器若 classpath 或行尾不同，
    `check` 可能报 IMPROVEMENTS（例如某台机器 JDK 不产出某类引用）。
    改进方向是重新 `capture`，不是把 baseline 手工改大。

---

## 8. Q1 — Package Migration Checklist（`net.lab1024.sa.admin.module.scm.*` → `com.xsy.scm.*`）

**本轮一行代码都没有迁。** 以下是精确影响面，供 Q1 估工与验收。

### 8.1 必须同时改的位置

| # | 项 | 数量 | 说明 |
| --- | --- | --- | --- |
| 1 | `src/main/java` 下 SCM 文件 | **692** | package 声明 + 相互 import |
| 2 | `src/test/java` 下 SCM 文件 | **155** | 同上 |
| 3 | SCM 目录**之外**引用旧包名的文件 | **3** | 漏了会编译失败，见下 |
| 4 | mapper XML namespace | **64** | `mapper/scm/**` 46 + `mapper/business/scm/**` 18 |
| 5 | XML 中旧全限定名出现次数 | **247** | namespace + `resultType` + `parameterType` + `<association>` 等 |
| 6 | ↳ 去重后的全限定名 | **176** | |
| 7 | 反射 / 扫描用的旧包名字符串 | **20 处 / 4 文件** | 全部在测试里 |
| 8 | 文档 / 工具脚本中的旧包名 | **16 处 / 6 文件** | 含本文件与 `AGENTS.md` |

第 3 项的三个文件（**最容易漏，因为它们在 SCM 目录之外**）：

```text
sa-admin/src/main/java/net/lab1024/sa/admin/config/ScmJsonConfig.java
sa-admin/src/test/java/net/lab1024/sa/admin/module/system/support/FileRelationPgIT.java
sa-admin/src/test/java/net/lab1024/sa/admin/module/system/support/FileScratchCleanupPgIT.java
```

第 7 项的 4 个文件（旧包名以**字符串**形式存在，IDE 重命名不会自动改）：

```text
.../module/scm/ScmArchitectureTest.java                       @AnalyzeClasses(packages = "...")
.../module/scm/common/json/ScmBigDecimalNullSemanticsTest.java  SCM_ROOT_PACKAGE 常量 + 类路径扫描
.../module/scm/inventory/ScmInventoryConstantTest.java          SCM_PACKAGE 常量 + 8 个错误码类全名清单
.../module/scm/purchase/PurchaseErrorCodeTest.java              SCM_PACKAGE 常量 + 8 个错误码类全名清单
```

### 8.2 Spring 扫描（必须先做，否则迁完启动即空 Bean）

`AdminApplication.java:27` 现在是：

```java
public static final String COMPONENT_SCAN = "net.lab1024.sa";
@ComponentScan(AdminApplication.COMPONENT_SCAN)
@MapperScan(value = AdminApplication.COMPONENT_SCAN, annotationClass = Mapper.class)
```

Q1 必须改成同时覆盖两个根，且**两个注解都要改**（只改 ComponentScan 会让 Mapper 全部注不进）：

```java
private static final String SMART_ADMIN_PACKAGE = "net.lab1024.sa";
private static final String XSY_PACKAGE = "com.xsy";

@ComponentScan({SMART_ADMIN_PACKAGE, XSY_PACKAGE})
@MapperScan(value = {SMART_ADMIN_PACKAGE, XSY_PACKAGE}, annotationClass = Mapper.class)
```

不得漏掉 Service / Controller / Component / Mapper 任何一类。
SmartAdmin 底座包（`net.lab1024.sa.base.*`、`module.system.*`、`module.support.*`）**不迁**，
也不得为了缩短 import 与业务代码混进同一次提交。

### 8.3 迁包时应顺手处理的边界（本审计发现的、与包名无关的分层债务）

```text
common/scope/ScmDataScopeService  ->  report/support/ScmReportAccess.COST_QUERY_PERM
    权限码常量应在 common；ArchUnit 因常量内联看不见这条（§7 第 5 条），必须人工移
finance/service/Finance{Receipt,Payment}Service  ->  order/service/OrderIdempotencyService
    幂等登记是通用能力，应落 common；届时可删掉 ArchUnit 里那条 belongToAnyOf 例外
```

### 8.4 迁包完成后必须同步改的工具与门禁

- `tools/quality/checkstyle.xml` 与 pom 里的 `<includes>**/module/scm/**/*.java</includes>`
  → 改指 `com/xsy/scm/**`；
- `tools/quality/quality_guard.py` 的 `SCM_MAIN_ROOT` / `SCM_TEST_ROOT` / `SCM_PACKAGE_PATH`；
- `tools/quality/scm_metrics.py` 的 `LEGACY_SCM_PACKAGE` 与 `_domain_of()` 的 `/module/scm/` 分隔符；
- **`legacy-scm-package` 规则此时退役**，由 ArchUnit 的
  `classes().should().resideInAPackage("com.xsy.scm..")` 接管（`ScmArchitectureTest` 类头已备好写法）；
- baseline 的 `path` 全部改名：`capture` 会因「全部 identity 消失 + 全部新增」而失败，
  这是设计行为，必须 `--allow-growth` 并在本报告追加一节说明，不能偷偷换文件。

### 8.5 验收（计划 §3.6）

```text
src/main/java 下 net.lab1024.sa.admin.module.scm   = 0
src/test/java  下 net.lab1024.sa.admin.module.scm  = 0
mvn -B -pl sa-admin -am test                        0 failures / 0 errors
`legacy-scm-package` 两条预算归零
迁包单独一次 commit，随后**另起一次** formatter commit（计划 §17 Q1）
```

**禁止**「全仓字符串替换后直接提交」（计划 §3.5）：第 7、8 两项都是字符串形态的包名，
正则替换会连带改坏注释与文档，而 IDE / AST 重命名不会碰它们 —— 必须分开处理。

---

## 9. Q2 / Q3 backlog（按依赖顺序）

### 9.1 Q2 — 类型安全、权限、校验、工具、命名

推荐顺序（计划 §17）：`common → product → customer → supplier → pricing → order → purchase →
inventory → sorting → delivery → finance → report`，每一项单独 commit。

| # | 工作项 | 数量 | 前置 |
| --- | --- | --- | --- |
| 1 | 裸角色名依赖字段 rename | 71 | 无；纯机械，编译器保证安全 |
| 2 | 缩写字段 rename | 107（需逐条裁决，见 §6.2） | 与 1 同批做，同一个文件只碰一次 |
| 3 | 单字母局部变量 / lambda 形参 | 73 | 无 |
| 4 | A 类 magic string → 引用 enum | 191 | 需先做 5（否则 enum 没人引用，改了也看不出收益） |
| 5 | 把「只当文档用」的 16 个 enum 真正变成参数/字段类型 | 16 enum | 与 4 同一批 |
| 6 | 建按域 Permission Catalog，替换裸串 | 285 处 / 163 值 / 43 文件 | 照 `FinanceConstant` 范式；同时解掉 §8.3 第一条 |
| 7 | 约束注解补 message | 788 | 文案需业务确认（客户可见文本），不能机器生成 |
| 8 | `@Pattern` 枚举正则改用 enum / 统一 validator | 62 | 与 4/5 同批 |
| 9 | `trimToNull` ×3 / `nullToZero` ×2 改框架或具名工具 | 5 | 无 |
| 10 | wildcard import 展开 | 360（生产） | 与 formatter commit 同批最省 diff |
| 11 | unused import 清理 | 267 | 与 10 同批 |
| 12 | 行宽 > 120 | 243 | 与 formatter commit 同批 |

### 9.2 Q2/Q3 需要先定的一个格式决定

本轮**刻意没有**启用完整 formatter，理由如下，需要负责人先裁决再落地：

- `google-java-format` 的行宽硬编码 100，与本项目 120 标准冲突（计划 §10 定的是 120）；
  启用会让两者互相打架，Checkstyle 说合法的行 Spotless 说要拆。
- 它在 JDK 21 上要求通过 `.mvn/jvm.config` 追加 `add-exports` 参数 —— 那会改变本项目
  **每一次** Maven 调用的 JVM 参数，超出「质量整改不改行为」的范围。
- Eclipse formatter 需要一份仓库内 XML 配置，且会把被触碰的文件整体重排。
- 因此 Q0 只落地 `trimTrailingWhitespace` + `endWithNewline`（零格式化引擎、零全文件重排）。
  `removeUnusedImports` / `importOrder` 留到 formatter 选型定了再加，避免先制造巨大 diff。

### 9.3 Q3 — 注释、架构、Service 职责

| # | 工作项 | 数量 |
| --- | --- | --- |
| 1 | 阶段流水注释按 §12 分类改写（D 类迁 docs，B 类保留不变量但换成业务陈述） | 695 处 / 198 文件 |
| 2 | `>400` 行且命中多线的类做职责拆分（Policy / Validator / Assembler / Calculator / Manager / SourceReader） | 17 命中，优先 4 个多线命中的 |
| 3 | 跨域直接引用别域 DAO | **24 处 / 15 个域对**（本轮实测，未设门禁） |
| 4 | 同名常量多 enum 的词汇表裁决 | 28 |
| 5 | `mapper/business/scm/**` 与 `mapper/scm/**` 两套约定归一 | 18 文件 |

第 3 项是当前**没有门禁**的最大一条架构敞口（计划 §12 要求「禁止 domain A 随意访问 domain B DAO」）。
它没进 Q0 是因为 24 处 / 15 对超出「显式例外清单」能承载的规模，
需要一个真正的跨域访问白名单机制才能既拦新增又解释存量 —— 属 Q3 设计，不该在 Q0 半做。

### 9.4 门禁自身要补的三件事

1. 测试源码是否纳入 guard 与 Checkstyle（§7 第 4 条）；
2. Checkstyle / Spotless 绑定到默认生命周期（现在只有 `verify.py quality` 会跑，
   直接 `mvn test` 不跑）。等 §9.1 第 10–12 项做完、违规数从 871 降到可控范围再绑。
3. **测试隔离**：`ScmStocktakeImportPgIT` 依赖「共享种子仓库的余额行数」，
   而快照凭证是逐行写进模板单元格的签名串、受 POI 32,767 字符上限约束
   （见 §11.2 的实测过程）。仓库行数一变，用例就会从 `SNAPSHOT_STALE` 掉到
   `CREDENTIAL_INVALID`。这类耦合不会在单跑时暴露，只在顺序变化时暴露。

---

## 10. Q4 — 文档与仓库治理计划（只出计划，本轮一个文件都没移动）

### 10.1 现状体量

```text
行数    字符数   文件
2574    89,799   AGENTS.md                                        ← 计划要求收到 ~500 行
2468   152,258   docs/progress.md                                 ← 计划要求收口为 docs/status.md
1589    15,835   docs/quality/java-code-quality-remediation-plan.md（本轮基线，保留）
1488    19,848   docs/xsy-scm-wave1-8-audit-fix-plan.md           ← 已被各波次实现取代的旧审计稿
1184    54,233   docs/decisions.md                                ← 计划要求逐步 ADR 化
 497    18,226   PROPOSAL-2026-09-18-团队技术提升方案.md
 379     7,956   SMARTADMIN_REFERENCE_RULES.md
 157     5,121   CONTRIBUTING.md
 126     5,057   README.md
  89     4,315   docs/delivery-static-route-implementation.md
  39     2,931   docs/README.md
  27       838   NOTICE.md
   7       333   CLAUDE.md
```

`docs/` 全树：25 个 markdown、24,208 行（不含本轮新增的本文件与整改计划）。
根目录 markdown：7 个。

### 10.2 目标结构（计划 §15 / §25）

```text
根目录只保留：README.md AGENTS.md CLAUDE.md CONTRIBUTING.md LICENSE NOTICE.md
              Docker / Git / env 必需配置 + docs/ tools/ deploy/ 与三个正式项目目录

SMARTADMIN_REFERENCE_RULES.md                 -> docs/architecture/smartadmin-foundation.md
PROPOSAL-2026-09-18-团队技术提升方案.md         -> docs/archive/proposals/
docs/xsy-scm-wave1-8-audit-fix-plan.md        -> docs/archive/（它已被逐波实现覆盖，不再指导任何决定）
docs/progress.md                              -> docs/status.md（当前状态/阻塞/风险/下一步）
                                                 历史进 docs/archive/progress/ 或直接依赖 Git
docs/decisions.md                             -> docs/adr/NNN-*.md + decisions.md 只做索引
docs/plan/**                                  -> docs/plan/active/**
tools/**                                      -> tools/{quality,migration,e2e,data}/ + tools/verify.py
```

### 10.3 一条必须在搬家前先定的规则（本轮实测发现）

`.editorconfig` 对 `*.md` 声明了 `trim_trailing_whitespace = true`（计划 §15 明确要求这条）。
但仓库内的 Markdown **依赖行尾双空格做硬换行** —— 整改计划自己的文件头就是
`> 状态：执行中␣␣` 这种写法。也就是说：谁在遵守 `.editorconfig` 的编辑器里保存这些文档，
就会把它们的重排变成一次内容修改。

本轮**没有**擅自把 `*.md` 改成 `trim_trailing_whitespace = false`（那是删减已确认规则），
而是把冲突记录在这里。**Q4 搬家前需要先裁决**，可选：

```text
A. *.md 关行尾空白修剪（最省事，代价是不再满足计划 §15 的「至少」清单）
B. 先把所有依赖行尾双空格的 Markdown 改写成空行分段，再保留 trim = true
C. 保持现状，靠 review 兜（不推荐：这是一次会在别人手里静默发生的内容改动）
```

### 10.4 AGENTS.md 瘦身口径

保留：稳定的工程规则、不可回退的业务不变量、架构边界。
移出（历史细节，不进代码注释）：每阶段测试数量、每次提交记录、已完成流水、
Finance 每一步的历史。
计划给的目标是 ~500 行，且明确「不是硬性 CI 数字，但必须显著瘦身」。

---

## 11. 验证结果

逐条命令与实际输出在 §11.1；一次未复现的失败如实记在 §11.2；
本轮**没有执行**的验证在 §11.3（复现命令见附录 A）。

**棘轮不是摆设，已实测它真的会拦**：临时放一个探针类，内含
`private final ProductCategoryDao dao;`、`"ENABLED".equals(...)`、一个 wildcard import、
一行含 `本轮 / F1-9 / Wave 3 / Q42 / D-9 / 设计稿 §99` 的注释，结果：
`RESULT: FAIL`，9 条 NEW DEFECTS + 1 条 GROWN（`legacy-scm-package` 692→693），
Checkstyle 侧也报出 `AvoidStarImport x1`。探针已删除，工作区回到干净状态。

### 11.1 逐条结果

```text
git diff --check                                    干净，0 处 whitespace error
migration_checksum_guard.py check                   PASS：missing 0 / renamed 0 / unbaked 0
                                                    （本轮没有触碰 db/migration 任何一个字节）
mvn -N checkstyle:check                             871 条，全部落在 baseline 内；692 文件全部解析成功
mvn spotless:check                                  PASS
quality_guard.py check --checkstyle                 PASS，6 个 family 全部 +0
mvn test -Dtest=ScmArchitectureTest                 7 tests / 0 failures / 0 errors / 0 skipped，3.1s
python tools/verify.py quality                      RESULT: PASS (exit 0)
python tools/verify.py backend                      1200 tests / 0 failures / 0 errors / 5 skipped
                                                    mvn exit 0；verify 报 INCOMPLETE(exit 2)
```

`verify.py backend` 的 `exit 2` 是**既有基线**，不是本轮引入的失败：那 5 条
`F0FileStorageCloudIT` 用例带云端门控，本地按设计跳过，`verify.py` 把任何 skip 都记为
INCOMPLETE。判据是 `failures` 与 `errors` 均为 0，且 5 与整改前记的「5 cloud skips」一致 ——
**没有扩大 skip、没有删测试、没有降低断言强度**（计划 §32 的红线）。

### 11.2 一次未复现的失败，如实记录

第一次 `verify.py backend`（本轮改动全部就位后）：**1200 tests / 1 failure**，
失败用例 `ScmStocktakeImportPgIT.versionDriftRejectsWholeBatch` ——
断言实际拿到 `CREDENTIAL_INVALID`，期望 `SNAPSHOT_STALE`。

排查过程与结论：

| 检查 | 结果 |
| --- | --- |
| 单跑该测试类 | 7 tests / 0 failures（通过） |
| 该链路是否跨用例累积状态 | 否。基类 `ScmW5PgITBase` 带 `@Transactional`，逐方法回滚 |
| `CREDENTIAL_INVALID` 的触发面 | 只有 4 条：验签失败 / 格式非法 / 已过期 / 模板版本不受支持（`InventoryStocktakeImportService.java:132-146`） |
| 凭证的形态 | 签名串**逐行写进模板每个单元格**；`StocktakeSnapshotSigner.sign()` 的注释记着 POI 单元格 32,767 字符上限，并为此专门加了一层 DEFLATE（未压缩时约 200 多个 SKU 就会顶破） |
| 复现尝试 | 随后**两次完整全量**（含本轮全部改动）均为 sa-admin 1192 / 0 / 0 / 5 + sa-base，BUILD SUCCESS；再跑一次 `verify.py backend` 亦为 1200 / 0 / 0 / 5 |

因此「快照行数变化 → 凭证长度触顶 → 验签失败」与该用例的表现一致，
根因是**测试夹具对共享种子仓库行数的顺序敏感耦合**，已记入 §9.4 第 3 条。

本轮没有改过任何业务代码、SQL、测试断言或依赖作用域（唯一的新依赖是
test 作用域的 `archunit-junit5`），不存在把该用例改坏的路径；可能的影响面只有
新增一个 JUnit 引擎导致 surefire 执行顺序变化，从而**暴露**（而非制造）上述既有耦合。
按 §32 的红线，本轮**没有为了变绿去动这个测试**。

### 11.3 未在本轮执行的验证

- **前端 / 浏览器 E2E 未跑**：本轮没有触碰 `xsy-scm-web/` 的任何文件，
  整改计划 §0 也禁止新页面与新 API。
- **5 条云端门控用例未执行**：需要真实对象存储环境，与既往各轮同口径。
- **Checkstyle 未覆盖测试源码与 SmartAdmin 底座**（§5.4 的扫描范围就是 SCM 生产代码），
  这是范围选择，不是遗漏。


---

## 附录 A — 复现全部数字

```bash
# 1. Checkstyle 报告（先跑，guard 的 --checkstyle 要读它的产物）
cd xsy-scm-server && mvn -B -N checkstyle:check && cd ..

# 2. 门禁计数（6 个 family 的当前值 vs baseline）
python tools/verify.py quality
python tools/quality/quality_guard.py check --checkstyle
python tools/quality/quality_guard.py scan --checkstyle --verbose --limit 400

# 3. 审计数字（本报告 §2–§6 全部表格的来源）
python tools/quality/scm_metrics.py --json

# 4. 架构规则
cd xsy-scm-server && mvn -B -pl sa-admin -am test \
  -Dtest=ScmArchitectureTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
```

`scan` 与 `scm_metrics.py` 都只读，不改任何文件；写 baseline 只有 `capture` 一条路径，
且默认拒绝让任何 family 变大（§1.3 那次是显式 `--allow-growth`）。
