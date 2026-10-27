-- 负库存出库守护：把「余额不得为负」从全局 CHECK 改为按仓库开关的触发器。
--
-- 原 ck_inventory_balance_quantity / ck_inventory_balance_available 会直接挡住现场
-- “先发货、后补采购入库”的流程。新规则：
--   * 仅当 xsy_warehouse.allow_negative_stock = TRUE 时允许 quantity < 0；
--   * quantity < 0 时 reserved_quantity 必须为 0（负库存不能被预留）；
--   * quantity >= 0 时仍要求 reserved_quantity <= quantity；
--   * reserved_quantity >= 0 继续由 ck_inventory_balance_reserved 保证。
-- 触发器不区分业务来源：销售出库、赠品出库、盘点、损溢、调拨都会经过这里，
-- 但只有销售出库服务会在开关打开时主动允许负数量，其他动作仍由各自服务拒绝。

ALTER TABLE xsy_inventory_balance DROP CONSTRAINT IF EXISTS ck_inventory_balance_quantity;
ALTER TABLE xsy_inventory_balance DROP CONSTRAINT IF EXISTS ck_inventory_balance_available;

CREATE OR REPLACE FUNCTION scm_inventory_balance_negative_guard()
RETURNS trigger AS $$
BEGIN
    IF NEW.quantity < 0 THEN
        IF NOT EXISTS (SELECT 1 FROM xsy_warehouse w
                       WHERE w.id = NEW.warehouse_id
                           AND w.deleted = FALSE
                           AND w.allow_negative_stock = TRUE) THEN
            RAISE EXCEPTION 'warehouse does not allow negative stock' USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reserved_quantity <> 0 THEN
            RAISE EXCEPTION 'negative stock cannot be reserved' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.reserved_quantity > NEW.quantity THEN
        RAISE EXCEPTION 'reserved quantity exceeds on-hand quantity' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_scm_inventory_balance_negative_guard ON xsy_inventory_balance;
CREATE TRIGGER trg_scm_inventory_balance_negative_guard
    BEFORE INSERT OR UPDATE OF quantity, reserved_quantity ON xsy_inventory_balance
    FOR EACH ROW EXECUTE FUNCTION scm_inventory_balance_negative_guard();

COMMENT ON FUNCTION scm_inventory_balance_negative_guard() IS
    '按仓库 allow_negative_stock 放行销售出库负库存；负余额不得有预留';
