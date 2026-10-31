-- 当前本地单仓允许负库存；保留其他仓库已明确配置的开关。
UPDATE xsy_warehouse SET allow_negative_stock = TRUE, updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE warehouse_code = 'WH001' AND NOT deleted AND NOT allow_negative_stock;

CREATE OR REPLACE FUNCTION scm_inventory_balance_negative_guard()
RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM xsy_warehouse w WHERE w.id = NEW.warehouse_id
        AND NOT w.deleted AND w.allow_negative_stock) THEN
        IF NEW.quantity < 0 THEN
            RAISE EXCEPTION 'warehouse does not allow negative stock' USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reserved_quantity > NEW.quantity THEN
            RAISE EXCEPTION 'reserved quantity exceeds on-hand quantity' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION scm_inventory_balance_negative_guard() IS
    '允许负库存时不按账面余额阻断销售发货；预留仍非负且按原来源释放';
