# 源码注释规范

状态：当前长期规则。适用于 `xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/**` 与 `xsy-scm-web/src/**` 的生产源码。

本文件只保存**已经落地、需要长期维持**的注释约束。一次性的治理清单、逐文件映射与「当时还有哪些没改」不在这里维护 —— 那种内容放在 `docs/plan/active/`，清完即删。

## 1. 两条核心标准

> **注释解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的。**
>
> **能从类名、方法名、参数名、类型和代码流程直接读出来的信息，不要再用 Javadoc 翻译一遍。**

第二条是后端的主战场。即使把所有过程编号（`Wave` / `W5` / `R0` / `§14.3` / 参考项目）都清掉，后端仍可能显得啰嗦 —— 因为它有把类头写成半篇设计文档、给每个字段写 `@param` 翻译的习惯。

## 2. 删什么

| 类型 | 处理 |
| --- | --- |
| 来源 / 复制日期 / `Copy First + Adapt` | 删除。Git 已经是来源历史 |
| 剪枝 / 适配 / 验收记录（含 `Playwright` / `E2E` / 「单测已过」） | 删除 |
| `Sprint` / `Wave` / `W1`～`Wn` | 删除 |
| `R0` / `R1` / `R2` 阶段代号 | 删除 |
| `A-Dn` / `Qna` / `§x.x` 临时计划编号 | 删除（`§` 指向现存 `*.md` 时**保留**） |
| 「来源：**新写**」「为什么必须新增此文件」 | 删除 |
| 旧项目（C / V2 / legacy 项目）当时怎么做的考古说明 | 删除 |
| **字段名翻译式 `@param`**（`@param skuId SKU` / `@param quantity 数量`） | 删除。Java record / DTO / Entity 天然自描述 |
| **类头「这个类为什么存在」的长篇论证**（设计动机 / 替代方案 / 后果推演 / 使用示例） | 只留输入输出契约与隐藏约束，其余移 `docs/architecture/` 或 ADR |
| **表格 / 矩阵 / 完整流程说明**（如「快照矩阵」「冻结时点表」） | 移入 `docs/architecture/`，源码留职责 + 一句文档链接 |
| 与当前代码重复的 API / 字段 / 流程逐项复述 | 删除 |
| 「绝不能改」「不要乱动」「刻意这样」等指令式措辞 | 改写为陈述事实，或删除 |

「表格 / 矩阵」为什么要移出去：这类内容会随业务调整，而源码里的几十行 Javadoc 不会跟着改 —— 实现与注释迟早漂移，读者也无法判断哪一份是权威。`docs/architecture/purchase-snapshot.md` 就是从 `PurchaseSnapshotFactory` 的类头搬出来的。

## 3. 留什么

只有代码本身无法表达以下信息时才写注释：

1. 非显而易见的业务不变量；
2. 容易被「看似合理」的重构破坏的边界；
3. 精度、并发、时区、幂等、安全等隐藏风险；
4. 外部协议或兼容性约束；
5. 暂时无法消除的 workaround，且能说明原因；
6. public API / 复杂算法真正需要的契约说明。

实际代码里值得留的典型形态（都来自本仓库）：

- 「可空列必须显式声明 `FieldStrategy.ALWAYS`，否则清空联系人永远失败」；
- 「先归还预留、再扣减实物 —— 这是 `ck_inventory_balance_available` 逼出来的顺序，不是风格」；
- 「`null` 保持 JSON null 与 `0` 输出 `"0.0000"` 是两种不同事实，合并会掩盖数据问题」；
- 「审批必须带 `version`，否则审批人可能批准自己没看过的数量」；
- 「盘点差异施加到确认瞬间的账面量（`live`），不是把账面改写成实盘数」；
- 「同一供应商允许多条 `defaultFlag = true` —— 做成单选是凭空发明约束」。

## 4. 长度只是审计信号

作为**默认目标**：

| 形态 | 目标 |
| --- | --- |
| 普通行内注释 | 1 行 |
| 方法 / 函数说明 | 1～3 行 |
| 文件级说明 | ≤ 5 行 |

超过目标**不判违规**。具体规则：

- `> 8 行`进入人工复核列表，但**不因为超过 8 行自动要求压缩**；
- 只有存在**重复**、**过程记录**、**历史论证**、**逐行复述代码**时才压缩；
- 如果每一段都在解释当前仍成立、且代码本身无法表达的约束，可以保留（复杂序列化器、并发 / 事务边界、金额精度契约写 10～15 行是合理的）。

**不要**为了「看起来短」把 9 行有价值的说明压成 5 行 —— 那会降低代码质量。

## 5. 绝对不动

- **已应用的 Flyway migration**（`db/migration/V*.sql`）：一律不改。已应用的 migration 是历史事实，改字节会让校验和与已部署环境不一致。
- **指向 `docs/plan/active/frontend-ui-backend-gap-inventory.md` 的 `Bn` 溯源注释**：这是活契约，`p3-backend-gap-inventory-contract.test.mjs` 正向钉住它们。

  | 文件 | 引用 |
  | --- | --- |
  | `order/order-return-list.vue` | `frontend-ui-backend-gap-inventory.md` 的 `B6` |
  | `promotion/promotion-coupon-list.vue` | 同上 `B7` |
  | `inventory/inventory-reservation-list.vue` | 前端后端缺口盘点 `B8` |
  | `finance/finance-detail-drawer.vue` | 前端后端缺口盘点 `B3` |

- **被契约测试断言到的注释文本**：先 `grep -l <文件名> test/*.test.mjs`，再判断「删掉这句注释，测试还想保护什么」：
  1. 测试真正保护业务约束 → 保留约束，允许重写注释并同步更新测试；
  2. 测试只是偶然匹配到旧注释文本 → **改测试**，让它断言代码结构 / 行为；
  3. 注释本身就是契约（如上面的 `Bn` 溯源）→ 保留。

  测试不是坏注释的保护伞。

## 6. 防回归契约

`xsy-scm-web/test/scm-comment-noise-contract.test.mjs` 把上述规则钉成门禁：

- **过程标记必须为 0**：三范围（`xsy-scm-web/src`、`com/xsy/scm`、`sa-admin/resources`）的 `来源：project-reference-examples` / `来源：新写` / `来源：Wn 派生` / `复制日期` / `Copy First` / 剪枝适配验收 / 测试验收记录 / 参考项目对比 / AI 指令式措辞 / `Wave` / `Wn` / `Rn` / `A-Dn` / `Qna` / 无 `.md` 引用的 `§`，逐标记比较且基线全为 0。
- 需要保留的例外登记在测试内的 `WHITELIST`（`{file, marker, reason}`，`reason` 为空即失败）。**不要调高 `BASELINE` 的数字。**
- **指向现存文档的 `§` 链接不得减少**（`长期规则见 docs/architecture/xxx.md §5` 是有效引用）。
- **`longCommentCount` 只输出报告，不参与成败** —— 长度是审计信号，不是质量判定。
- 扫描**不含** `db/migration/`。

标记用组合 / 条件匹配，不能用裸词：裸 `来源：` 会命中业务含义（`来源：销售订单`），裸 `§` 会命中指向 `docs/architecture` 的有效链接，裸 `Playwright` 会命中「表格 DOM id —— 给 Playwright 定位用」，`A4` 是纸张尺寸不是计划编号，`legacy` / `DRAFT` 是中性词与枚举值。

## 7. 批量改注释时的注意事项

注释治理往往要一次动几十上百个文件，以下几条都踩过：

1. **同一文件内多个注释块必须按 `start` 降序替换**（或每批重新扫描取行号）。先替换的块改变行数后，后面按原行号 splice 会切错位置 —— 曾把一个 `validateOrder` 的函数签名吃掉。
2. **改注释前先 `grep -l <文件名> test/*.test.mjs`**。契约测试会断言注释原文，例如 `scm-drawer-width-contract.test.mjs` 要求 `scm-drawer.ts` 里出现「受限特殊档」与逐字类别名「分拣 / 称重工作台」（带空格）。
3. **每批过一遍「去注释与空白后与 HEAD 逐字比对」的检查**，确认只动了注释。
4. **机械移除 token 后要用宽模式扫破损**：`与 的` / `<b></b>` / `****：` / `（修）` / `以及 的` / `： 的` / `（最小主数据，）`。模式表要从实际 diff 的 `+` 行里归纳，不要凭想象列几条 —— 曾因此漏掉 38 处半句话。
5. **行尾**：前端 `xsy-scm-web/src` 是 CRLF，Java 源码是 LF。脚本写 Java 时若按「有 `\r\n` 就用 CRLF」判断会写成 CRLF，`spotless:check`（`lineEndings=UNIX`）会在 `validate` 阶段直接失败。后端批次以 `mvn -o spotless:apply` 收尾（eclipse formatter 还会重排 Javadoc、把短行合并填满行宽）。
6. **`.vue` 的文件头只能用 `<!-- -->`**：它不是 `<script>` 的一部分，写 `/* */` 会被 SFC 解析器当成块外文本。
