-- 退货申请与验收均按销售单位计数；退回库存必须按订单冻结的库存记账单位入账。
-- 历史数据无换算快照，旧入库/出库原本同单位，读侧 COALESCE 兼容。
ALTER TABLE xsy_order_return_receipt_item
    ADD COLUMN stock_quantity NUMERIC(18, 4),
    ADD COLUMN stock_unit_snapshot VARCHAR(32);

ALTER TABLE xsy_order_return_receipt_item
    ADD CONSTRAINT ck_order_return_receipt_stock_quantity
        CHECK (stock_quantity IS NULL OR stock_quantity > 0),
    ADD CONSTRAINT ck_order_return_receipt_stock_snapshot_pair
        CHECK ((stock_quantity IS NULL) = (stock_unit_snapshot IS NULL));

COMMENT ON COLUMN xsy_order_return_receipt_item.quantity
    IS '退货实收销售单位数量（参与订单可退量累计）';
COMMENT ON COLUMN xsy_order_return_receipt_item.stock_quantity
    IS '退货实收库存记账单位数量（按订单冻结比例换算，历史行为 NULL）';
COMMENT ON COLUMN xsy_order_return_receipt_item.stock_unit_snapshot
    IS '来源销售出库库存单位快照，历史行为 NULL';
