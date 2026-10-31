-- 库存盘点单取消「草稿」语义：新建后直接进入「待确认」，仍可录入/修改实盘量，确认后才调整库存。

UPDATE xsy_inventory_stocktake
SET status = 'PENDING'
WHERE status = 'DRAFT';

ALTER TABLE xsy_inventory_stocktake ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE xsy_inventory_stocktake DROP CONSTRAINT ck_inventory_stocktake_status;
ALTER TABLE xsy_inventory_stocktake
    ADD CONSTRAINT ck_inventory_stocktake_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'));

COMMENT ON COLUMN xsy_inventory_stocktake.status IS '盘点单状态：PENDING 待确认 / CONFIRMED 已确认 / CANCELLED 已取消';
