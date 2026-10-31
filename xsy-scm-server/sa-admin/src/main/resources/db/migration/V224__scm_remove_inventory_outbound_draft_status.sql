-- 库存出库单取消「草稿」语义：新建后直接进入「待出库」，仍可编辑、取消或删除，确认后才扣减库存。

UPDATE xsy_inventory_outbound
SET status = 'PENDING'
WHERE status = 'DRAFT';

ALTER TABLE xsy_inventory_outbound ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE xsy_inventory_outbound DROP CONSTRAINT ck_inventory_outbound_status;
ALTER TABLE xsy_inventory_outbound
    ADD CONSTRAINT ck_inventory_outbound_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'));

COMMENT ON COLUMN xsy_inventory_outbound.status IS '出库单状态：PENDING 待出库 / CONFIRMED 已确认出库 / CANCELLED 已取消';
