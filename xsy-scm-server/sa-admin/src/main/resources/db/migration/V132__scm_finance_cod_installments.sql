-- 既有 ORDER_COD_COLLECTION 行保持原样：其 source_id 是订单 id。
-- 新的分笔收款以幂等命令记录为来源，cod_order_id 明确归属订单；
-- 同一命令至多一张收款单，订单可以有多笔实际收款。
ALTER TABLE xsy_finance_receipt ADD COLUMN cod_order_id BIGINT;
COMMENT ON COLUMN xsy_finance_receipt.cod_order_id IS
    '分笔 COD 收款归属的销售订单；V131 旧行以 source_id 记录订单，不回写';

ALTER TABLE xsy_finance_receipt DROP CONSTRAINT ck_finance_receipt_source_type;
ALTER TABLE xsy_finance_receipt ADD CONSTRAINT ck_finance_receipt_source_type CHECK (
    source_type IS NULL OR source_type IN
        ('PAYMENT_TRANSACTION', 'ORDER_COD_COLLECTION', 'ORDER_COD_INSTALLMENT')
);

ALTER TABLE xsy_finance_receipt ADD CONSTRAINT ck_finance_receipt_cod_order CHECK (
    (source_type = 'ORDER_COD_INSTALLMENT' AND cod_order_id IS NOT NULL AND cod_order_id > 0)
    OR (source_type IS DISTINCT FROM 'ORDER_COD_INSTALLMENT' AND cod_order_id IS NULL)
);

CREATE INDEX idx_finance_receipt_cod_order_active
    ON xsy_finance_receipt (cod_order_id)
    WHERE source_type = 'ORDER_COD_INSTALLMENT' AND entry_type = 'NORMAL' AND deleted = FALSE;
