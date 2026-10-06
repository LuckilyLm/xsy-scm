# 源码注释降噪

状态：活动计划。范围分四层：

| 层 | 本任务可以做什么 |
| --- | --- |
| 生产代码 | **不改** —— 可执行代码、类型、接口、DTO/VO、SQL 与 Flyway |
| 生产注释 | **治理对象** |
| 测试与质量门禁 | 可新增 / 调整，用于验证本次治理结果（见 P4.6） |
| 长期架构文档 | 必要时可新增 / 更新 `docs/architecture/**` 与 `docs/adr/**`，**仅用于承接从生产源码抽离、且仍有长期价值的设计约束** |

最后一条是必需的：本计划多处要求「大段设计说明移入 ADR / architecture」，若不同时开放 docs，执行时要么把值得保存的说明直接删掉，要么因为「本任务不允许改 docs」而无处承接。

来源：原「前端 UI 文案降噪与信息层级收口」评审中提出的 **P4**。该计划的 UI 部分已在 `1c564164` 完成、并按 `docs/README.md` §文档治理删除；P4 的作用域（生产源码注释）与 UI 文案不同，因此另立本文件并沿用 P4 批次号。

基线：本地 `main @ 1c564164`（2026-10-07，**尚未推送**；远端 `origin/main` 仍是 `57e479d7`）。执行前先确认当前 checkout 就是 `1c564164` 或其后代，否则 §4 的数字与文件清单对不上。扫描口径与复现方式见 §7。

边界：UI 侧规则见 [SCM UI 规范](../../architecture/scm-ui-guidelines.md)；本文件只管**生产源码注释**。已应用的 Flyway migration 属历史事实，**不得修改**（§6）。

治理对象是 **AI 工作日志式、历史考古式、重复解释式的注释**，不是「所有超过 8 行的注释」。长度只是审计信号（§3），后端 401 个长注释块是**审计池**而不是待改清单（P4.4）。

## 1. 问题

UI 侧的问题是「用户看到太多解释」，源码侧是「开发者看到太多 AI 工作日志」—— 本质是同一种过度解释。当前生产源码里的典型形态：

```ts
/*
 * 来源：project-reference-examples/xsy-scm/.../order-api.ts
 * 复制日期：2026-09-16。Copy First + Adapt
 * 剪枝：删掉了 C 的 xxx；适配：W4 改成 yyy
 * 验收：W4 单测、TS 棘轮与 Playwright
 */
```

```html
<!--
  * 来源：**新写**（宿主壳）。
  * C 的供货关系维护是 SPU 级的弹窗，行标识与写语义都不同……
  * 关键语义（与后端 `SupplierSkuService.replace` 一一对应，绝不能改）：
  * - 唯一写入口是**整表替换**……
  * - 同一供应商允许出现多条「默认来源」（legacy 不变量 R12）。
-->
```

这些不是帮助理解当前代码的注释，而是把**开发过程、参考来源、波次编号、设计争论与验收记录**永久留在源码里。Git 已经是来源与过程的历史，源码不必再记一遍。

注意：`supplier-sku-drawer.vue` 那段里 **「整表替换」「空数组 = 清空全部关联」「必须回传 id + version」是真不变量，要留**；要删的是「来源：**新写**（宿主壳）」「C 的……」「绝不能改」「legacy 不变量 R12」。**删的是过程，不是约束。**

## 2. 判定原则

**默认不写注释。** 只有代码本身无法表达以下信息时才保留：

1. 非显而易见的业务不变量；
2. 容易被「看似合理」的重构破坏的边界；
3. 精度、并发、时区、幂等、安全等隐藏风险；
4. 外部协议或兼容性约束；
5. 暂时无法消除的 workaround，且能说明原因；
6. public API / 复杂算法真正需要的契约说明。

> **注释解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的。**

### 2.1 删除

| 类型 | 处理 |
| --- | --- |
| `来源：project-reference-examples…` / `来源：**新写**` / `复制日期` / `Copy First + Adapt` | 删除（`来源：` 按 §2.3 **组合匹配**，裸 `来源：` 会命中业务含义） |
| 剪枝 / 适配 / 验收记录（含 `Playwright` / `E2E` / 「单测已过」） | 删除 |
| `Sprint` / `Wave` / `W1`～`Wn` | 删除 |
| `R0` / `R1` / `R2` 阶段代号 | 删除 |
| `A-Dn` / `Qna` / `§x.x` 临时计划编号 | 删除（`§` 指向现存 `*.md` 时**保留**，见 §2.3） |
| 「来源：**新写**」「为什么必须新增此文件」 | 删除 |
| 旧项目（C / V2 / legacy 项目）当时怎么做的考古说明 | 删除 |
| 与当前代码重复的 API / 字段 / 流程逐项复述 | 删除 |
| 「绝不能改」「不要乱动」「刻意这样」等 AI 指令式措辞 | 改写为陈述事实，或删除 |
| 大段设计说明 | 移入 ADR / architecture，源码留一句 + 文档链接 |

### 2.2 保留（压缩后）

```ts
// 先递增 requestId，再清空 options，避免先前的异步请求覆盖当前搜索结果。
requestId++;
```

```ts
// 金额保持定点字符串，避免经过 JS Number 产生精度损失。
```

判断标准是「这条注释指向的风险今天还成立吗」。成立的留，只在讲述「当初怎么变成现在这样」的删。

### 2.3 标记必须是高精度组合形态（实测反例）

上一轮 UI 文案治理在「`商品规格` 里含 `品规` 子串」上误报过 58 处，这次同样有坑 —— 而且实测证明**两个最直觉的标记都是错的**：

| 词 / 形态 | 实测（前端 / 后端） | 结论 |
| --- | ---: | --- |
| 裸 `来源：` | 22 / 9 | **不能用**。后端 9 处**全是业务含义**（`来源：销售订单`、`来源：售后退款单`、`来源：{@code sales_order_item}`、`来源：{@link #ORDER_ITEM}`），前端还有 1 处 `来源：订单行 / 满赠赠品权益`。**零处是「代码来源」** |
| 裸 `Playwright` | 13 / 0 | **不能用**。其中 4 处是 `表格 DOM id —— **给 Playwright 定位用**`，在解释「为什么有两套 id」，属正常约束。要求 `验收/测试` 出现在前 20 字内 |
| 裸 `§` | 112 / 0 | **要加条件**。前端 112 处无 `.md` 引用（是过程标记），但有 **4 处**是 `长期规则见 docs/architecture/scm-ui-guidelines.md §5` 这类**有效长期文档链接**，必须保留 |
| `A4` | 1 / 2 | `ScmPrintPaperEnum.A4` 是**真实业务值**（纸张），不是计划编号 |
| 泛化 `A\d+` | 23 / 5 | 会把 `{@link #A4}`、`{@code A4}` 一起算进来 |
| `legacy` | 5 / 2 | 中性词。`ScmDecimalStrings` 的「legacy 对同一批字段存在两套解析规则」是**仍成立的历史约束** |
| `DRAFT` | 18 / 54 | 业务枚举值 |

因此标记规则是：

| 形态 | 判定 |
| --- | --- |
| `来源：project-reference-examples…` / `来源：**新写**` | 过程标记（**组合匹配**，不是裸 `来源：`） |
| `§14.5` / `根据 §9.4` / `按 §21 计划` / `见 Finance R1 §x` | 过程标记（**同一注释块里没有 `*.md` 引用**时） |
| `docs/architecture/xxx.md §5` / `docs/adr/xxx.md §3` | **合法，保留** |
| `复制日期` / `Copy First` / `剪枝：` / `适配：` / `验收：` / `Wave` / `Wn` / `R0`～`R2` / `A-Dn` / `Qna` | 过程标记（实测精度足够） |

一句话：**`§` 只有在没有指向当前存在的长期文档时才判为过程标记。** 同理，`来源：` 只在指向参考项目 / 声明「新写」时才判为过程标记。

另外扫描必须**只匹配注释体**（先跳过字符串字面量与模板字符串），否则字符串、URL 与枚举值都会误报。

**扫描器自身的坑：第一版漏了 208 处。** `.vue` 文件里 `<template>` **之前**的文件头 `<!-- ... -->` 不在任何 SFC 块内 —— 只扫 `template` / `script` / `style` 三个块会整段漏掉，而「来源：**新写**（宿主壳）」这类最典型的残留恰恰写在那里。修正后前端命中从 271 涨到 479。**扫描时必须把 SFC 块之外（首个块之前与末个块之后）的区域也按 HTML 注释扫一遍。**

## 3. 长度只是审计信号，不是违规条件

作为**默认目标**：

| 形态 | 目标 |
| --- | --- |
| 普通行内注释 | 1 行 |
| 方法 / 函数说明 | 1～3 行 |
| 文件级说明 | ≤ 5 行 |

超过目标**不判违规**，需要有实际信息价值即可。具体规则：

- **> 8 行进入人工复核列表，但不因为超过 8 行自动要求压缩。**
- 只有存在以下情况才压缩：**重复**、**过程记录**、**历史论证**、**逐行复述代码**。
- 如果每一段都在解释**当前仍成立、且代码本身无法表达**的约束，可以保留。复杂 serializer、并发 / 事务边界、金额精度契约写 10～15 行是合理的。
- 超过 8 行时优先判断内容属于「**当前代码契约**」还是「**设计论证 / 历史过程**」：后者移到 ADR / architecture，前者允许保留。

反例（**不要**因为长度而改）：`common/json/ScmFixedScale4Serializer.java`、`ScmOffsetDateTimeSerializer.java`、`inventory/domain/Inventory*Fact.java` 这类定点精度 / 时区 / 快照语义的说明，长是因为约束本身复杂，逐句确认后大概率**整段保留**。

正例（该压缩）：`common/scm-diff.ts` 的 30 行头里，「**为什么需要它**」的三点论证与「与 W3 `price-history-list.vue` 是同一形态」属设计论证与过程引用，应删；「**刻意的取舍**」里「不翻译值，因为日志是审计证据」「不做值猜测性解析」「数组按行比较」是仍成立的设计约束，压缩成 3～5 行保留。

## 4. 实测基线

扫描于本地 `main @ 1c564164`（尚未推送，远端仍为 `57e479d7`），口径见 §7。

> 下表的「审计池文件」= 命中标记 ∪ 含 > 8 行注释块 ∪ 含 > 5 行文件头，是**需要人看一遍**的范围，**不是必须改动的清单**。真正的改动量由 P4.4 的三分类决定。

| 指标 | 前端 `xsy-scm-web/src` | 后端 `com/xsy/scm` | 后端 `sa-admin/resources` | 已应用 migration（不动） |
| --- | ---: | ---: | ---: | ---: |
| 扫描文件 | 609 | 1071 | 127 | 110 |
| 注释块 | 4406 | 3776 | 313 | 2301 |
| 过程标记（处 / 文件） | 229 / 54 | 12 / 12 | 0 / 0 | 12 / 5 |
| 计划编号（处 / 文件） | 250 / 102 | 12 / 12 | 33 / 15 | 68 / 36 |
| **标记合计（处）** | **479** | **24** | 33 | 80 |
| `§` 有 `.md` 引用（**保留**） | 4 | 0 | 4 | 15 |
| 注释块 > 8 行（块 / 文件） | 157 / 125 | **401 / 327** | 16 / 17 | 0 |
| 文件头 > 5 行 | 138 | 40 | 10 | 0 |
| 最长注释块（行） | 30 | 36 | 30 | 1 |
| **审计池文件（并集）** | **190** | **337** | **25** | 0 |

前端标记明细（`§` 按「无 `.md` 引用」计入）：

| 标记 | 命中 | 文件 |
| --- | ---: | ---: |
| `§` 无 `.md` 引用 | 112 | 51 |
| `Wn` 波次 | 81 | 68 |
| 参考项目 / 旧项目对比 | 44 | 44 |
| 剪枝 / 适配 / 验收记录 | 41 | 41 |
| `来源：project-reference-examples` | 38 | 38 |
| `Copy First + Adapt` | 35 | 35 |
| 测试 / 验收记录 | 32 | 32 |
| `复制日期` | 29 | 29 |
| `Sprint` / `Wave` | 28 | 20 |
| `Rn` 阶段代号 | 19 | 13 |
| `来源：新写` | 9 | 9 |
| `Qna` 编号 | 5 | 5 |
| `A-Dn` 编号 | 5 | 5 |
| AI 指令式措辞 | 1 | 1 |

后端标记明细：参考项目 12/12、`Rn` 8/8、`Wn` 3/3、`A-Dn` 1/1（合计 24 处 / 24 文件）。**裸 `来源：` 的 9 处全部是业务含义，已从标记集剔除**（§2.3）。

### 4.1 结论与预判不同，必须先说清

- **前端是「过程残留」问题**：479 处标记落在 154 个文件（过程 54 + 计划 102），`§` 无引用（112 处 / 51 文件）最多；叠加长注释块，审计池 **190 文件**（占 609 的 31%）。这一侧绝大多数是**真该删**的。
- **后端几乎不是「过程残留」问题**：1071 个文件里只有 **24 文件、24 处标记**。后端真正的负担是**长注释** —— 401 个注释块 > 8 行，最长 36 行，审计池 **337 文件**。
- 因此力气应按约 **1:1.9** 分给前后端（前端 190 文件 : 后端 337 + 25 文件）。但**后端绝大多数会归入「原样保留」** —— 把后端当成「和前端一样清标记」会做错方向。
- 后端 `resources`（XML mapper 等）有 33 处计划编号 / 15 文件，与 java 同批处理。
- 已应用 migration 里有 80 处标记，但那是历史事实，**不动**（§6）。

> 基线口径修正记录：第一版扫描漏掉了 `.vue` 文件里 **`<template>` 之前**的文件头 `<!-- -->` 块
> （SFC 块外区域），且 `测试 / 验收记录` 用了裸 `Playwright`。两处都已修正，数字从 271 涨到 479。
> 详见 §2.3。

## 5. 批次

按「收益 / 风险」排序，每批独立可提交。

### P4.1 前端：文件头的来源与波次（54 文件，收益最高）

删掉整个来源块：参考项目 / 旧项目对比（44）、剪枝 / 适配 / 验收记录（41）、`来源：project-reference-examples`（38）、`Copy First + Adapt`（35）、测试 / 验收记录（32）、`复制日期`（29）、`来源：新写`（9）、AI 指令式措辞（1），合计命中 **54 个文件、229 处**。这些块基本可以**整段删除**，只从里面抢救出仍成立的不变量。

优先级最高的文件（标记密度，含 P4.2 的编号类标记）：

```text
15  src/constants/business/scm/purchase-const.ts
13  src/api/business/scm/purchase-order-api.ts
12  src/api/business/scm/report-api.ts
12  src/views/business/scm/purchase/purchase-types.ts
11  src/api/business/scm/purchase-demand-api.ts
11  src/views/business/scm/report/report-types.ts
 9  src/api/business/scm/order-api.ts
 9  src/api/business/scm/purchase-receipt-api.ts
 7  src/api/business/scm/order-log-api.ts
 7  src/api/business/scm/order-refund-api.ts
 7  src/constants/business/scm/order-const.ts
 7  src/views/business/scm/order/order-form-model.ts
```

**API 文件尤其严重**：`order-api.ts` / `order-log-api.ts` / `purchase-demand-api.ts` / `purchase-receipt-api.ts` 这类文件头把「从哪个文件复制、哪天复制、删了什么、新增了什么、哪个 Wave、哪个编号、跑过什么测试」全写进去了，基本整段清掉。API 文件最多保留当前特殊契约，例如：

```ts
/** 写请求必须携带 Idempotency-Key。 */
```

若该约束已由公共 `purchaseCommand()` 统一实现，连这句也不必每个文件重复。

**执行时最容易踩的坑：`.vue` 文件的文件头只能用 `<!-- -->`。** 它不是 `<script>` 的一部分，写 `/* */` 会被 SFC 解析器当成块外文本 —— `scm-ui-copy-contract.test.mjs` 会直接报「解析不出 template」。改写 `.vue` 头部时一律用 HTML 注释；`.ts` 才用 `/* */`。

**行尾必须跟随文件现状**：工作区是 CRLF 检出，脚本替换时若写入 LF 会造成同文件混合行尾。改完跑一次「同一文件内 `\r\n` 与裸 `\n` 是否共存」的检查。

### P4.2 前端：`§` 章节号与计划编号（102 文件）

`§` 无 `.md` 引用 112 处 / 51 文件、`Wn` 81 处 / 68 文件、`Sprint`/`Wave` 28 处 / 20 文件、`Rn` 19 处 / 13 文件、`A-Dn` 5、`Qna` 5 —— 合计 250 处，命中 **102 个文件**（与 P4.1 的 54 个只部分重叠）。这些是对**已删除计划文档**的引用，读者无从追溯，全部删除；若该句本身讲的是仍成立的约束，改写为不依赖编号的陈述。

**但 `§` 有 4 处指向现存长期文档，必须保留**（§2.3）：

```text
theme/scm/scm-drawer.ts:1    长期规则见 `docs/architecture/scm-ui-guidelines.md` §5
theme/scm/table.less:111     长期规则见 `docs/architecture/scm-ui-guidelines.md` §3
```

注意 `Rn` 里混着两类：`Finance R0 / R1` 是阶段代号（删），而 `(R7)` 这类是 legacy 项目的规则编号（同样删，但要看它旁边的约束是否要保留成文字）。

**同一个注释块里常常「该删的和必须留的」混在一起**，必须逐句处理，不能整块删。例：

```ts
// order-return-list.vue:140
 * 所以 §14.5 里「原订单/客户（若 VO 已有）」这一条当前无法满足 —— 属后端字段缺口
 * （登记于 docs/plan/active/frontend-ui-backend-gap-inventory.md 的 B6），
 * 不在这里用 `orderId` 冒充单号显示。
```

`§14.5` 要删，`B6` + 文档路径**必须留**（见 §6）。

### P4.3 前端：长注释人工审计（125 文件 / 157 块候选）

157 个注释块 > 8 行，138 个文件头 > 5 行，审计池 125 个文件。**逐块过一遍**，按 §3 与下面的三分类处置；**不设「必须压缩到 5 行」的指标**。

复核起点（最长的一批）：

```text
30 行  src/views/business/scm/common/scm-diff.ts:1
23 行  src/views/business/scm/report/report-model.ts:1
23 行  src/views/business/scm/inventory/inventory-model.ts:1
22 行  src/views/business/scm/common/scm-display.ts:1
21 行  src/views/business/scm/screen/components/inventory-health.vue:46
19 行  src/views/business/scm/report/report-types.ts:1
19 行  src/theme/scm/scm-drawer.ts:27
19 行  src/constants/business/scm/inventory-const.ts:1
```

### P4.4 后端：长注释审计（327 文件 / 401 块候选，不是 327 个都要改）

401 个 > 8 行的块是**审计池**，不是待改清单。逐块分三类：

| 类 | 判断依据 | 处理 |
| --- | --- | --- |
| **A 过程性说明** | 来源 / 波次 / 阶段号 / 历史比较 / 「为什么当时这样设计」 | 删除 |
| **B 当前约束但重复** | 约束仍成立，但与类型、签名或邻近注释重复，或同一段在多处复述 | 压缩 |
| **C 当前复杂契约** | 定点精度、并发 / 事务边界、幂等、外部协议、跨域只读 | **原样保留**，或仅轻微整理 |

**不预设最终修改文件数。** 按扫描经验推测可能落在 80～150 个文件，但**这不是目标，也不是验收指标** —— 写出来只为让你对工作量有量级预期，**不要为了凑数去改本该保留的 C 类注释**。**审计覆盖率要求 100%，改动率不设目标** —— 「看了但决定不改」是合格结论，需要在提交说明里写清理由（一句话即可）。

**内部按业务域拆成 6 个子批，每批一个提交**（否则一次改上百个 Java 文件，review 无法判断是否删错了某个幂等 / 精度 / 事务约束）：

| 子批 | 包 | 审计池文件 |
| --- | --- | ---: |
| P4.4a | `common`（含 `common/json`） | 15 |
| P4.4b | `product` / `customer` / `supplier` / `warehouse` | 24 |
| P4.4c | `purchase` | 54 |
| P4.4d | `inventory` | 94 |
| P4.4e | `order` / `delivery` / `sorting` / `pricing` | 23 |
| P4.4f | `finance` / `report` / `promotion` / `payment` / `balance` / `print` / `screen` / `notification` / `dashboard` | 117 |
| | **合计** | **327** |

每个子批仍执行同一套 A / B / C 分类，不做任何按批次的规则放宽。

复核起点（最长的一批；这几处**大概率属 C 类，长是合理的**，先确认再决定）：

```text
36 行  purchase/manager/PurchaseSnapshotFactory.java:28
36 行  common/json/ScmFixedScale4Serializer.java:11
34 行  purchase/support/PurchaseInventoryContract.java:20
33 行  common/json/ScmOffsetDateTimeSerializer.java:12
30 行  inventory/domain/InventoryConversionFact.java:6
29 行  inventory/domain/InventoryTransferFact.java:6
28 行  inventory/service/InventoryCommandService.java:62
28 行  inventory/domain/InventoryStocktakeFact.java:6
```

后端 Javadoc 里大量 `<p>` 分段的「为什么这样设计」属 A 类，应移入 `docs/architecture/` 或 ADR；类 / 方法上只留一句职责 + 仍成立的约束。**但后端注释里确有真约束**（定点精度、乐观锁、幂等、跨域只读），压缩时逐条确认，**不能按长度机械截断**。

### P4.5 后端：过程残留（小，24 + 15 文件）

`com/xsy/scm` 只有 **24 文件 / 24 处**：参考项目 12（如「与参考项目 `ConvertStatusEnum` 的三态一致」）、`Rn` 8、`Wn` 3、`A-Dn` 1。逐条删或改写为业务陈述。

**裸 `来源：` 的 9 处全部是业务含义，已从标记集剔除**（`来源：销售订单`、`来源：售后退款单`、`来源：{@code sales_order_item}`、`来源：{@link #ORDER_ITEM}` 等）—— 这些是「这条数据的来源是什么」，属正常业务注释，**不要删**。

`sa-admin/src/main/resources`（XML mapper / 配置）另有 33 处 / 15 文件，以 `§` 无引用（16）与 `Wn`（5）为主，同批处理。

### P4.6 防回归契约（分两步落地）

新增 `xsy-scm-web/test/scm-comment-noise-contract.test.mjs`（与 `p3-backend-gap-inventory-contract.test.mjs` 同样放在前端 `test/` 下跨模块读后端源码，复用现有 `npm test` 门禁，不新增 gate）。

**契约分两阶段，断言内容不同 —— 不要一上来就写 `= 0`，否则 P4.6a 建好当天就有 271 处失败，执行者会为了「让测试绿」开始乱删。**

#### P4.6a（先建，随各批收紧）

```text
markerCount <= currentBaseline      # 以当前实测值为基线，见下表
禁止新增过程标记                     # 新引入的标记一律失败，即使总数没超基线
每完成一批 → 同步下调对应 baseline   # 基线只降不升
```

当前基线（本地 `1c564164` 实测，migration 排除在扫描范围外）：

| 范围 | 过程标记 | 计划编号 |
| --- | ---: | ---: |
| `xsy-scm-web/src` | 229 | 250 |
| `com/xsy/scm` | 12 | 12 |
| `sa-admin/resources` | 0 | 33 |

#### P4.6b（P4.4 完成后收紧）

```text
markerCount = 0
仅显式白名单允许非零              # 白名单条目形如 {file, marker, reason}，reason 为空即失败
```

- **扫描范围**：`xsy-scm-web/src/**`、`xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/**`、`xsy-scm-server/sa-admin/src/main/resources/**`。
- **排除**：`db/migration/**`（Flyway 不可改）、`node_modules`、`dist*`、`target`、`.runtime`、`project-reference-examples`、`docs/archive`。
- **方法**：先抽注释再匹配（跳过字符串字面量与模板字符串；`.vue` 按 SFC 块切分，避免模板里的 `http://` 被当成行注释）。**不要用 `source.includes()` 整串匹配** —— 会命中字符串、URL 与枚举值。
- **标记集**：§2.3 的表格。特别注意是**组合 / 条件匹配**：`来源：` 只在指向 `project-reference-examples` 或声明「新写」时算标记；`§` 只在同一注释块**没有** `*.md` 引用时算标记（指向 `docs/architecture/**` / `docs/adr/**` 的 `§` 必须放过）。
- **长度只输出报告，不参与测试成败**：

  ```text
  longCommentCount:  前端 116  后端 401
  ```

  允许因为新增复杂协议、并发约束等**合理上涨**；Code Review 时关注异常增长，**不设 CI 阈值**。**不要**断言「单个注释块 ≤ 5 行」「≤ 8 行」，也**不要**给它设棘轮 —— 否则开发者为了过测试会把 9 行有价值的说明硬压成 5 行，反而降低代码质量。

> 契约里的标记集必须与 §2.3 的反例表一致：裸 `来源：` / 裸 `§` / `A4` / `legacy` / `DRAFT` / 泛化 `A\d+` 不得进入，否则契约本身会变成误报源。

## 6. 边界：什么绝对不动，什么不要当成不动理由

- **已应用的 Flyway migration**（`sa-admin/src/main/resources/db/migration/V*.sql`，110 个文件里有 80 处标记）：**一律不改**。已应用的 migration 是历史事实，改字节会让校验和与已部署环境不一致。即使注释难看也保留。
- **指向 `docs/plan/active/frontend-ui-backend-gap-inventory.md` 的 Bn 溯源注释**：这是**活契约**，不是考古。`p3-backend-gap-inventory-contract.test.mjs` 正向钉住它们，删了测试立刻红：

  | 文件 | 引用 |
  | --- | --- |
  | `order/order-return-list.vue:142` | `frontend-ui-backend-gap-inventory.md` 的 `B6` |
  | `promotion/promotion-coupon-list.vue:324` | `frontend-ui-backend-gap-inventory.md` 的 `B7` |
  | `inventory/inventory-reservation-list.vue:81` | 前端后端缺口盘点 `B8` |
  | `finance/finance-detail-drawer.vue:98` | 前端后端缺口盘点 `B3` |

  形态可以是全路径或「缺口盘点 Bn」简写，但**必须保留「指向哪份文档 + 哪个编号」**，只删同一块里的 `§x.x` 之类过程编号。
- **不要把「被测试断言到」当成保留理由 —— 测试不能成为坏注释的保护伞。** 先 `grep -l "<文件名>" test/*.test.mjs` 找出在读这个文件的测试（本仓库有大量「读源码做正则断言」的测试：`p1-sorting-contract`、`w6-inventory-contract`、`finance-report-contract`、`w2a-demand-summary-preview-contract` 等），再按三种情况分流：

  | 情况 | 处理 |
  | --- | --- |
  | 测试真正保护业务约束 | **保留约束**，允许重写注释，并同步更新测试的断言 |
  | 测试只是偶然匹配到旧注释文本（断言里带 `W5` / `A-D3` 之类） | **改测试**，让它断言代码结构 / 行为，而不是保留旧注释 |
  | 注释本身就是契约（如上面的 `Bn` 缺口溯源） | 保留 |

  判断依据是「删掉这句注释，测试还想保护什么」：能落到代码结构或行为上的，就让测试去断言那个；只能靠注释文本表达的，才把注释留下来。
- 任何**生产**可执行代码、类型定义、接口签名、DTO/VO 字段、SQL 语句。测试代码与质量门禁不在此列（见开头「状态」与 P4.6）。
- 权限、错误码、状态机的**当前业务约束不得削弱**；但其中夹杂的阶段号、来源、历史比较、设计过程仍应删除。例：

  ```java
  /**
   * W6 新增。                    ← 删
   * 与参考项目 XX 一致。           ← 删
   * 根据 §14.3……                 ← 删
   * 状态只能 DRAFT -> CONFIRMED。 ← 唯一要留的一句
   */
  ```

  同理，精度 / 并发 / 时区 / 幂等 / 安全的注释是「**不删约束**」，不是「不允许整理」：约束句子保留，围绕它的论证过程与历史可移入 ADR。
- `docs/archive/**`（历史文档）。
- `project-reference-examples/**`（参考项目，只读素材）。

## 7. 扫描口径（可复现）

1. 遍历 §P4.6 的范围（排除表同）。
2. 抽注释：JS/TS/LESS/Java 取 `//` 与 `/* */`，HTML/XML 取 `<!-- -->`，SQL 取 `--`，YAML/properties 取 `#`；**抽之前先跳过字符串字面量、模板字符串与 Java 文本块**。
3. `.vue` 按顶层 SFC 块切分：`<template>` 走 HTML 注释，`<script>` / `<style>` 走 JS/CSS 注释；**块外区域（首个块之前 / 末个块之后）也要按 HTML 注释扫**，文件头来源块就在那里。
4. 逐注释块匹配 §2.1 的标记；同时统计块长度与文件头长度。
5. 一次性扫描脚本在 `.runtime/tmp/`（均 gitignored）：`scan_comments.py` 抽注释、`scan_comments2.py` 收紧标记 + 找最差文件、`scan_comments3.py` 把 `来源：` / `§` 改成组合与条件匹配、`scan_comments_doc.py` 产出 §4 的全部数字。结果 dump 到 `comment_*.json`。P4.6 的长期契约不要沿用这几个脚本的正则实现，按 §P4.6 的「方法」重写。

## 8. 验收

### 必跑

1. `node --experimental-strip-types --test test/scm-comment-noise-contract.test.mjs`
2. `npm test`（前端全量单测）。**重点看 `p3-backend-gap-inventory-contract`** —— 它断言的是源码注释里的 Bn 溯源（§6），是 P4 最容易误伤的一条；其余读源码做正则断言的测试同理。
3. `npm run typecheck`（就是 `vue-tsc --noEmit`，不带参数、无内联阈值）
4. `npm run lint`
5. `npm run build`

> **关于类型检查**：本任务不得使结果**恶化**。仓库当前存在历史遗留的类型错误（`src/views/system/role/**` 等 SmartAdmin 老代码），由 `tools/verify.py frontend` 的棘轮单独管理，**不要**把它的数字当成「允许的错误数」写进本任务，也**不要**顺手去修与本计划无关的类型错误 —— 只确认你改过的文件没有引入新的 `error TS`。

### 后端

生产代码只改注释、不改语义，但后端必须确认**编译通过、既有测试结果不变**：

- `mvn -o -q compile`（或 `python tools/verify.py backend`，按实际需要）；
- 注意 `mvn package` 会因残留 Maven JVM 占用 jar 失败，必要时 `taskkill` 残留 `java.exe` 重试。

### 人工抽查

按「文件 + 原文」抽查，不看数字对账。重点确认：

- `supplier-sku-drawer.vue` 的最终形态只剩整表替换语义（空集合 = 清空、`id` + `version` 必回传），来源/新写/绝不能改/R12 全部消失；
- `common/scm-diff.ts` 保留「不翻译值（审计证据）」「不做值猜测性解析」「数组按行比较」，删掉「为什么需要它」与 W3 引用；
- 后端定点精度、乐观锁、幂等、跨域只读相关注释**未被削弱**；
- **审计覆盖率**：P4.3 / P4.4 的每一块都过了一遍，提交说明里写清「看了多少块 / 改了多少 / 其余为什么决定不改」（一句话即可）。**「看了但决定不改」是合格结论**，改动率不作为验收项。

## 9. 不做的事

- 不改已应用的 Flyway migration（§6）。
- 不修改**生产**可执行代码；确需改代码才能说清的地方，登记为独立任务，不混在本计划里。（测试代码与质量门禁按开头「状态」可以改。）
- **不把「注释超过 N 行」当成违规**，也不设「压缩到 N 行」的指标。长度只是审计信号（§3）。
- **不因为某条注释被测试断言到就保留它** —— 测试不是坏注释的保护伞，按 §6 三种情况分流。
- 不把大段设计说明**搬进**源码注释，方向相反。
- 不用「注释行数下降」当成功指标 —— 该留的约束被删掉就是退步。
- 不为了过契约把有价值的 9 行说明硬压成 5 行。
- 不删**业务含义**的 `来源：`（`来源：销售订单` / `来源：售后退款单`）与指向现存文档的 `§`（`scm-ui-guidelines.md §5`）—— 见 §2.3。
- 不为了契约测试通过而把标记集收窄成几乎匹配不到东西。

## 10. 批次顺序

**先建门禁，再清低风险的，最后做后端 327 文件的审计。**

```text
P4.6a 建立「过程标记」当前基线（契约先落地，后面每批都有保护）
→ P4.1 前端：删除来源 / 复制日期 / 验收记录（54 文件，低风险高收益）
→ P4.2 前端：删除阶段号 / 计划编号（102 文件）
→ P4.3 前端：长注释人工审计（125 文件 / 157 块候选，改动量不设目标）
→ P4.5 后端：过程残留（24 + 15 文件，量小）
→ P4.4 后端：长 Javadoc 人工审计，内部再拆 6 个子批（327 文件 / 401 块候选，放最后）
     P4.4a common 15 → P4.4b product/customer/supplier/warehouse 24
     → P4.4c purchase 54 → P4.4d inventory 94
     → P4.4e order/delivery/sorting/pricing 23 → P4.4f finance 等 117
→ P4.6b 把过程标记基线收紧到 0
```

把后端 327 文件的审计放最后，是因为它风险、review 成本与 merge conflict 都最大，且改动量不确定（可能只有 80～150 个文件真的需要动）。前面几批做完后，后端剩余的问题面也更容易看清。
