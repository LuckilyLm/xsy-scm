-- 配送线路取消「草稿」语义：新建线路直接进入「待排线」，可继续增删订单与站点，排线后进入「已排线」。

UPDATE xsy_delivery_route
SET status = 'PENDING'
WHERE status = 'DRAFT';

ALTER TABLE xsy_delivery_route ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE xsy_delivery_route DROP CONSTRAINT delivery_route_status_check;
ALTER TABLE xsy_delivery_route
    ADD CONSTRAINT delivery_route_status_check CHECK (status IN (
        'PENDING', 'PLANNED', 'DISPATCHED', 'COMPLETED', 'CANCELLED'));

COMMENT ON COLUMN xsy_delivery_route.status IS '线路状态：PENDING 待排线 / PLANNED 已排线 / DISPATCHED 已发车 / COMPLETED 已完成 / CANCELLED 已取消';
