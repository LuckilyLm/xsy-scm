# W3 Pricing 人工设计批准与 Verification Closure 记录

日期：2026-09-15

用户已批准进入编码：Q1/Q3/Q4/Q5/Q6/Q7/Q9/Q10/Q11/Q12=A，Q2 按修订，Q8=B。

- Q2：价格与可售性独立；UNPRICED 仅表示当前无价格来源；0.0000 是合法 PRICED；不可售不抹去已有价格。严格模式任一不可售或缺价返回 40949。
- W1 Product 不修改，market_price 保持 NOT NULL；不为测试伪造缺价语义。
- Q4：父维度 FOR UPDATE → overlap query → write；真实 PostgreSQL 并发测试；不加 exclusion/effective_from 唯一约束。
- Q8：独立 ScmW3PgITBase，不修改 W2 基类。
- T0 → T16；V6–V9 冻结；V10/V11 首次成功应用后冻结。
- 完成全量回归与验收报告、Go/No-Go 后停止，不进入 W4。

Verification Closure 结论：**No-Go**。V2、真实 PG IT、Web Test、前端门禁和 W1/W2/W3 Playwright 均通过；legacy SHA-256 因用户已删除 legacy 文件而无法复验。未恢复这些文件。详见 [`2026-09-15-w3-pricing-验收报告.md`](./2026-09-15-w3-pricing-验收报告.md)。

本波次冻结，停止于 W3，不自动进入 W4。
