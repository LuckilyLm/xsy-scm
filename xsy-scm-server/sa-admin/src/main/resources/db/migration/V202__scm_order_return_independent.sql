-- 独立退货单：客户退货时找不到原订单，退货单不关联 xsy_sales_order；
-- 明细直接挂 SKU，并保存商品名称与销售单位快照，列表/详情不再依赖订单联接（D-1028-15）。
ALTER TABLE xsy_order_return
    ADD COLUMN source_type varchar(16) NOT NULL DEFAULT 'ORDER',
    ADD COLUMN customer_name_snapshot varchar(150);

UPDATE xsy_order_return r
SET customer_name_snapshot = so.customer_name_snapshot
FROM xsy_sales_order so
WHERE so.id = r.order_id;

-- 订单已软删除时上面的联接取不到名称，用客户档案兜底；客户也查不到才留空，由下面的非空约束暴露出来。
UPDATE xsy_order_return r
SET customer_name_snapshot = c.name
FROM xsy_customer c
WHERE c.id = r.customer_id
    AND r.customer_name_snapshot IS NULL;

ALTER TABLE xsy_order_return
    ALTER COLUMN customer_name_snapshot SET NOT NULL,
    ALTER COLUMN order_id DROP NOT NULL,
    ADD CONSTRAINT ck_order_return_source_type CHECK (source_type IN ('ORDER', 'INDEPENDENT')),
    ADD CONSTRAINT ck_order_return_source_order CHECK ((source_type = 'ORDER') = (order_id IS NOT NULL)),
    ADD CONSTRAINT ck_order_return_customer_name CHECK (btrim(customer_name_snapshot) <> '');

CREATE INDEX idx_order_return_customer_created ON xsy_order_return (customer_id, created_at DESC)
    WHERE deleted = FALSE;

-- 明细的 SKU、商品名称、销售单位：原单退货从订单行回填，独立退货建单时直接写入。
ALTER TABLE xsy_order_return_item
    ADD COLUMN sku_id bigint,
    ADD COLUMN sku_name_snapshot varchar(150),
    ADD COLUMN spec_name_snapshot varchar(150),
    ADD COLUMN sale_unit_snapshot varchar(32),
    ADD COLUMN price_source varchar(32);

UPDATE xsy_order_return_item i
SET sku_id = oi.sku_id,
    sku_name_snapshot = oi.product_name_snapshot,
    spec_name_snapshot = oi.spec_name_snapshot,
    sale_unit_snapshot = oi.sale_unit_snapshot
FROM xsy_sales_order_item oi
WHERE oi.id = i.order_item_id;

ALTER TABLE xsy_order_return_item
    ALTER COLUMN sku_id SET NOT NULL,
    ALTER COLUMN sku_name_snapshot SET NOT NULL,
    ALTER COLUMN spec_name_snapshot SET NOT NULL,
    ALTER COLUMN sale_unit_snapshot SET NOT NULL,
    ALTER COLUMN order_item_id DROP NOT NULL,
    ADD CONSTRAINT ck_order_return_item_source CHECK (order_item_id IS NOT NULL OR price_source IS NOT NULL);

-- 独立退货按 SKU 建行，同一退货单同一 SKU 同一单位只能出现一次；原单退货继续由 order_item_id 唯一约束把关。
CREATE UNIQUE INDEX uk_order_return_item_sku_active ON xsy_order_return_item (return_id, sku_id, sale_unit_snapshot)
    WHERE order_item_id IS NULL AND deleted = FALSE;

-- 独立退货的实物接收没有原销售出库行，两个来源列一起放开；有来源时仍必须成对。
ALTER TABLE xsy_order_return_receipt_item
    ALTER COLUMN source_sales_out_movement_id DROP NOT NULL,
    ALTER COLUMN source_outbound_item_id DROP NOT NULL,
    ADD CONSTRAINT ck_order_return_receipt_source_pair CHECK (
        (source_sales_out_movement_id IS NULL) = (source_outbound_item_id IS NULL));

CREATE UNIQUE INDEX uk_order_return_receipt_item_standalone
    ON xsy_order_return_receipt_item (receipt_id, return_item_id)
    WHERE source_sales_out_movement_id IS NULL;
