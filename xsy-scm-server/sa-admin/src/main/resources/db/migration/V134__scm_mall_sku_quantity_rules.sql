-- 每个商品规格的商城购买量按销售单位配置；既有规格继承 1 + N × 1。
ALTER TABLE xsy_product_sku
    ADD COLUMN mall_min_quantity NUMERIC(18, 4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN mall_quantity_step NUMERIC(18, 4) NOT NULL DEFAULT 1.0000;

ALTER TABLE xsy_product_sku
    ADD CONSTRAINT ck_product_sku_mall_min_quantity CHECK (mall_min_quantity > 0),
    ADD CONSTRAINT ck_product_sku_mall_quantity_step CHECK (mall_quantity_step > 0);

COMMENT ON COLUMN xsy_product_sku.mall_min_quantity IS '商城起订量，单位同 sale_unit';
COMMENT ON COLUMN xsy_product_sku.mall_quantity_step IS '商城递增量，单位同 sale_unit';
