-- 独立退货生成客户级红字应收：不关联订单，也不关联某一张原应收（D-1028-15）。
-- 红字仍然只能来自退货（source_type = ORDER_RETURN），原因仍然必填；
-- 唯一的放宽是 RED 可以同时没有 order_id 和 original_receivable_id。
ALTER TABLE xsy_finance_receivable
    ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE xsy_finance_receivable DROP CONSTRAINT ck_finance_receivable_entry_pairing;

ALTER TABLE xsy_finance_receivable
    ADD CONSTRAINT ck_finance_receivable_entry_pairing CHECK (
        (entry_type = 'NORMAL' AND original_receivable_id IS NULL)
            OR (entry_type = 'RED' AND btrim(COALESCE(reason, '')) <> ''
                AND (original_receivable_id IS NOT NULL OR order_id IS NULL)));

ALTER TABLE xsy_finance_receivable DROP CONSTRAINT ck_finance_receivable_source_pairing;

-- 正常应收必须挂在销售订单上；红字允许没有订单（独立退货）。
ALTER TABLE xsy_finance_receivable
    ADD CONSTRAINT ck_finance_receivable_source_pairing CHECK (
        (entry_type = 'NORMAL' AND source_type = 'SALES_ORDER' AND order_id IS NOT NULL)
            OR (entry_type = 'RED' AND source_type = 'ORDER_RETURN'));

-- 只有独立退货的红字明细没有订单明细可挂；原单退货红字与出入库来源的正常行都必须继续带订单明细。
ALTER TABLE xsy_finance_receivable_item
    ALTER COLUMN order_item_id DROP NOT NULL,
    ADD CONSTRAINT ck_finance_receivable_item_order_item CHECK (
        order_item_id IS NOT NULL OR source_type = 'ORDER_RETURN_ITEM');

CREATE INDEX idx_finance_receivable_customer_level_red ON xsy_finance_receivable
    (customer_id, settlement_customer_id)
    WHERE deleted = FALSE AND entry_type = 'RED' AND original_receivable_id IS NULL;
