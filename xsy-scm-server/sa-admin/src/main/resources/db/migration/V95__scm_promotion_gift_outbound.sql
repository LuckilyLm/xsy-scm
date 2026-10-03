-- ADM-12 3-5b：促销赠品出库的库存流水类型。
--
-- 口径（docs/decisions.md）：赠品走**正式库存出库**，来源标为促销赠品，**不伪装成普通销售行**。
-- 因此新增独立流水类型 PROMOTION_GIFT_OUT（方向 = 出），而不是复用 SALES_OUT ——
-- 复用会让「这个月营销活动送掉多少成本」无法从流水直接回答，只能回头拼订单。
--
-- 纪律（ScmInventoryMovementTypeEnum 注释里写明）：加类型必须**同时**扩
-- ck_inventory_movement_type 白名单与**方向感知**的 ck_inventory_movement_snap，
-- 否则新类型的流水根本插不进去。基线取 V75 的最后一次重定义。
--
-- 来源类型（source_document_type）不加库级白名单：该列没有 CHECK，
-- 新值 ORDER_PROMOTION_GIFT 由 ScmInventorySourceDocumentTypeEnum 表达，
-- 防重仍走既有的 uk_inventory_movement_source_active (source_document_type, source_document_item_id)，
-- 赠品用 source_document_item_id = order_promotion_gift.id 作为稳定源键。

ALTER TABLE inventory_movement DROP CONSTRAINT ck_inventory_movement_type;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_type
    CHECK (movement_type IN ('PURCHASE_IN', 'SALES_OUT', 'SALES_RETURN_IN', 'STOCKTAKE_GAIN', 'STOCKTAKE_LOSS',
                             'LOSS_REPORT', 'GAIN_REPORT', 'TRANSFER_OUT', 'TRANSFER_IN', 'CONVERT_OUT',
                             'CONVERT_IN', 'PROMOTION_GIFT_OUT'));

ALTER TABLE inventory_movement DROP CONSTRAINT ck_inventory_movement_snap;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_snap CHECK (
    (movement_type IN ('PURCHASE_IN', 'SALES_RETURN_IN', 'STOCKTAKE_GAIN', 'GAIN_REPORT', 'TRANSFER_IN',
                       'CONVERT_IN')
        AND after_quantity = before_quantity + quantity)
    OR (movement_type IN ('SALES_OUT', 'STOCKTAKE_LOSS', 'LOSS_REPORT', 'TRANSFER_OUT', 'CONVERT_OUT',
                          'PROMOTION_GIFT_OUT')
        AND after_quantity = before_quantity - quantity)
);
