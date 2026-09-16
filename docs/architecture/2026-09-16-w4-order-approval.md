# W4 Sales Order 审批记录

## 14. 最终审批（2026-09-16，全部已批准）

用户明确授权：先修订 Target Design，随后直接执行 T0 → T23，无需再次确认。以下裁决覆盖原推荐值。

| 编号 | 最终裁决 | 状态 |
| --- | --- | --- |
| Q1/Q2/U1 | W4=Sales Order；W5=Purchase；DRAFT/PENDING/CONFIRMED/CANCELLED 四状态 | APPROVED |
| Q3 | NUMERIC(18,4)，HALF_UP scale 4，API 四位定点字符串 | APPROVED |
| Q4 | submit 调 requireResolvable() 全部通过后写 locked_*；PENDING 后永不重算；草稿可重解析 | APPROVED |
| U2/U3 | 创建时快照 customer.settleMode → settle_mode_snapshot、customer.sellerId → seller_id（可空）；expect_delivery_time / remark 可空；历史永不实时回读 | APPROVED |
| U2 | 不增 pay_status / actual_weight；实重由 actual_quantity + sale_unit_snapshot 表达 | APPROVED |
| U4/U8 | PostgreSQL sequence 全局单调递增、不每日 reset；SO + yyyyMMdd + 至少补齐6位，超过999999自然扩位；Return/Refund 同理 | APPROVED |
| U5 | ADMIN / MALL / SUPPLEMENT | APPROVED |
| U6 | NULL=UNPRICED，0.0000=合法PRICED；draft price/source 同空；未定价行和含未定价行的订单金额为空；PriceSource 禁止 UNPRICED；人工价非空且 OVERRIDE + 非空原因 | APPROVED |
| U7 | W4 不实现业务员数据权限，后续独立能力；不自造 SCM DataScope | APPROVED |
| U9 | 同步 AGENTS.md 与相关 Roadmap：W4 Sales Order / W5 Purchase | APPROVED |
| U10 | 不新增 V13+ 仅约束 PostgreSQL Closure；W4 起正常追加迁移；V1–V12 零修改 | APPROVED |
| 履约 | V13 不建 fulfillment_status；后续 Fulfillment/Inventory 波次以新迁移新增；W4 无库存余额/占用/流水/出库 | APPROVED |

订单号序列允许事务回滚造成空号，不承诺提交顺序连续；sequence 的数值全局递增，与日期无关。

---

实施与验收结果：**GO**。后端单测 153/153、PG IT 107/107、前端单测 26/26、W1–W4 Playwright 14/14、W4 6/6、SmartAdmin 探针 53/53；TS 棘轮与冻结 A–I 通过。

本文件的 APPROVED 记录用户对设计与编码的授权；最终自动化执行证据见 [W4 验收报告](./2026-09-16-w4-order-验收报告.md)。V13/V14 已冻结，停止于 T23，不进入 W5。
