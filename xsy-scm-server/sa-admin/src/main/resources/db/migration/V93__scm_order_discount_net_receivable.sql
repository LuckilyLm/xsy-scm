-- ADM-12 3-4：订单优惠落到应收净额。
--
-- 背景：优惠在订单确认时冻结（order_discount），但 Finance 的应收仍按出库行毛额生成、
-- 红字按退货行毛额生成 —— 账上「应收」比客户真实欠款多，退款也没有可反向的分摊依据。
--
-- 边界（ADR-004 / ADR-009）：
--   * 不建第二套财务账：应收仍是唯一债权事实，只是 `amount` 改为**净额**（行毛额 − 该行优惠分摊）。
--   * 追加事实、不改原记录：红字仍是另一张 entry_type='RED' 的单，不修改原正常应收。
--   * 行级优惠分摊随明细一起落库，退款反向按同一份分摊算，不用退款时的当前活动重算。
--   * 授信占用口径不变（负责人 2026-10-03 确认：仍按未扣优惠的结算金额判定）。
--
-- 刻意**不新增** gross_amount 列：毛额可由 `amount + discount_amount` 精确还原，
-- 落一列派生态就会与既有「不落余额/结清状态列」的纪律冲突。超额优惠由既有的
-- ck_finance_receivable_item_amount（amount >= 0）兜住 —— 减多了金额就会变负而被拒。
-- 存量行全部是毛额口径（本 migration 之前没有优惠净额概念），discount_amount 默认 0 正好正确。

ALTER TABLE finance_receivable_item
    ADD COLUMN discount_amount NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE finance_receivable_item
    ADD CONSTRAINT ck_finance_receivable_item_discount CHECK (discount_amount >= 0);
