-- 供应商商品货号是外部标识，不自动生成；不能混用系统 SKU、采收/生产批次号。
-- 使用既有供应商-SKU 关系，避免制造另一套映射与重复采购来源。
ALTER TABLE xsy_supplier_sku
    ADD COLUMN supplier_item_code VARCHAR(100);

ALTER TABLE xsy_supplier_sku
    ADD CONSTRAINT ck_supplier_sku_item_code_nonblank
    CHECK (supplier_item_code IS NULL OR length(btrim(supplier_item_code)) > 0);

-- 同一供应商同一货号只对应一个活动 SKU；不同供应商可以使用同一货号。
-- 保留大小写敏感和前导零，不擅自把供应商外部标识转换为系统编码。
CREATE UNIQUE INDEX uk_supplier_sku_item_code_active
    ON xsy_supplier_sku (supplier_id, supplier_item_code)
    WHERE deleted = FALSE AND supplier_item_code IS NOT NULL;
