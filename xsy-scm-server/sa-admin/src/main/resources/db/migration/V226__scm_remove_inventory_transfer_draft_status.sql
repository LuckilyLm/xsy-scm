-- 库存调拨单取消「草稿」语义：新建即进入「待发货」，仍可改明细、取消或删除，发出后才扣减源仓库存。

UPDATE xsy_inventory_transfer
SET status = 'PENDING'
WHERE status = 'DRAFT';

ALTER TABLE xsy_inventory_transfer ALTER COLUMN status SET DEFAULT 'PENDING';

-- 两个约束都引用 DRAFT，需一起重建：状态取值，以及「待发货/已取消不得带发出信息」的双侧判据。
ALTER TABLE xsy_inventory_transfer DROP CONSTRAINT ck_inventory_transfer_status;
ALTER TABLE xsy_inventory_transfer
    ADD CONSTRAINT ck_inventory_transfer_status CHECK (status IN (
        'PENDING', 'SHIPPED', 'RECEIVED', 'CANCELLED'));

ALTER TABLE xsy_inventory_transfer DROP CONSTRAINT ck_inventory_transfer_shipped;
ALTER TABLE xsy_inventory_transfer
    ADD CONSTRAINT ck_inventory_transfer_shipped CHECK (
        (status IN ('PENDING', 'CANCELLED') AND shipped_at IS NULL AND shipped_by IS NULL)
        OR (status IN ('SHIPPED', 'RECEIVED') AND shipped_at IS NOT NULL
            AND shipped_by IS NOT NULL AND btrim(shipped_by) <> ''));

COMMENT ON COLUMN xsy_inventory_transfer.status IS '调拨单状态：PENDING 待发货 / SHIPPED 在途 / RECEIVED 已完成 / CANCELLED 已取消';
