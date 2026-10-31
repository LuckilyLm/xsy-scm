-- 采购收货单取消「草稿」语义：新建即进入「待确认」，可改备注、删除或确认；确认后才写入应付与库存。

UPDATE xsy_purchase_receipt
SET status = 'PENDING'
WHERE status = 'DRAFT';

ALTER TABLE xsy_purchase_receipt ALTER COLUMN status SET DEFAULT 'PENDING';

-- 两个约束都引用 DRAFT，需一起重建：状态取值，以及「待确认不得带确认信息」的双侧判据。
ALTER TABLE xsy_purchase_receipt DROP CONSTRAINT ck_purchase_receipt_status;
ALTER TABLE xsy_purchase_receipt
    ADD CONSTRAINT ck_purchase_receipt_status CHECK (status IN ('PENDING', 'CONFIRMED'));

ALTER TABLE xsy_purchase_receipt DROP CONSTRAINT ck_purchase_receipt_confirmation;
ALTER TABLE xsy_purchase_receipt
    ADD CONSTRAINT ck_purchase_receipt_confirmation CHECK (
        (status = 'PENDING' AND confirmed_at IS NULL AND operator IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND operator IS NOT NULL AND btrim(operator) <> ''));

COMMENT ON COLUMN xsy_purchase_receipt.status IS '收货单状态：PENDING 待确认 / CONFIRMED 已确认';
