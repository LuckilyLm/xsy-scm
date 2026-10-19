-- V158：包装码绑定/解绑防线，保留 V157 已应用脚本不动。
-- 1) 数据库旁路并发 INSERT 也要争抢同一个包装码行锁，不能只靠应用锁。
CREATE OR REPLACE FUNCTION xsy_assert_unit_bindable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    unit_status VARCHAR(16);
    unit_lot_id BIGINT;
    active_bindings INT;
BEGIN
    SELECT status, lot_id INTO unit_status, unit_lot_id
    FROM xsy_trace_unit WHERE id = NEW.unit_id FOR UPDATE;
    IF unit_status IS DISTINCT FROM 'ACTIVE' OR unit_lot_id IS DISTINCT FROM NEW.lot_id THEN
        RAISE EXCEPTION 'cannot bind a void trace unit' USING ERRCODE = '23514';
    END IF;
    IF NEW.sales_order_item_id IS NULL THEN
        RAISE EXCEPTION 'binding must identify a sales order item' USING ERRCODE = '23514';
    END IF;
    -- 与正式出库 INSERT 共用订单行锁；发车后不能再补贴/改绑包装码。
    PERFORM 1 FROM xsy_sales_order_item WHERE id = NEW.sales_order_item_id FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS (
        SELECT 1 FROM xsy_sorting_task_item si
        WHERE si.id = NEW.sorting_task_item_id
          AND si.sales_order_item_id = NEW.sales_order_item_id
          AND si.task_id = NEW.sorting_task_id
    ) THEN
        RAISE EXCEPTION 'binding source does not match a sorting order item'
            USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM xsy_inventory_outbound_item oi
        JOIN xsy_inventory_outbound o ON o.id = oi.outbound_id
        WHERE oi.sales_order_item_id = NEW.sales_order_item_id
          AND (o.status = 'CONFIRMED' OR o.confirmed_at IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'cannot bind trace unit after sales order item shipped'
            USING ERRCODE = '23514';
    END IF;
    SELECT count(*) INTO active_bindings
    FROM xsy_trace_unit_binding b
    WHERE b.unit_id = NEW.unit_id
      AND NOT EXISTS (SELECT 1 FROM xsy_trace_unit_unbind u WHERE u.binding_id = b.id);
    IF active_bindings > 0 THEN
        RAISE EXCEPTION 'trace unit already has an active binding' USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END $$;

-- 2) 正式出库与更正解绑争抢原销售订单行锁，使申请后发车/审批竞态可以按提交顺序裁决。
-- 只锁定有订单行来源的出库；不改变库存单据的其余业务约束。
CREATE FUNCTION xsy_lock_trace_outbound_source_item() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.sales_order_item_id IS NOT NULL THEN
        PERFORM 1 FROM xsy_sales_order_item WHERE id = NEW.sales_order_item_id FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'outbound source sales order item missing' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_trace_lock_outbound_source_item
    BEFORE INSERT OR UPDATE OF sales_order_item_id ON xsy_inventory_outbound_item
    FOR EACH ROW EXECUTE FUNCTION xsy_lock_trace_outbound_source_item();

-- 存量出库明细也可能从草稿变成已确认：确认操作必须加入同一锁协议。
CREATE FUNCTION xsy_lock_trace_outbound_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.status = 'CONFIRMED' OR NEW.confirmed_at IS NOT NULL)
        AND OLD.status IS DISTINCT FROM 'CONFIRMED' AND OLD.confirmed_at IS NULL THEN
        PERFORM soi.id
        FROM xsy_sales_order_item soi
        JOIN (
            SELECT DISTINCT sales_order_item_id
            FROM xsy_inventory_outbound_item
            WHERE outbound_id = NEW.id AND sales_order_item_id IS NOT NULL
        ) src ON src.sales_order_item_id = soi.id
        ORDER BY soi.id
        FOR UPDATE OF soi;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_trace_lock_outbound_confirmation
    BEFORE UPDATE OF status, confirmed_at ON xsy_inventory_outbound
    FOR EACH ROW EXECUTE FUNCTION xsy_lock_trace_outbound_confirmation();

-- 3) 即使绕过服务层直接 INSERT UNBIND，也必须先锁包装码和原订单行，
--    再根据正式确认出库单判定；已出库只能走退货或补偿，不能解绑改绑。
CREATE FUNCTION xsy_guard_trace_unit_unbind_outbound() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    order_item_id BIGINT;
    already_shipped BOOLEAN;
BEGIN
    PERFORM 1 FROM xsy_trace_unit WHERE id = NEW.unit_id AND status = 'ACTIVE' FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'only an active trace unit can be unbound' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM xsy_trace_unit_correction c
        WHERE c.id = NEW.correction_id AND c.unit_id = NEW.unit_id
          AND c.binding_id = NEW.binding_id AND c.status = 'PENDING'
          AND c.requested_by IS DISTINCT FROM NEW.unbound_by
    ) THEN
        RAISE EXCEPTION 'unbind requires matching pending independent correction'
            USING ERRCODE = '23514';
    END IF;
    SELECT sales_order_item_id INTO order_item_id
    FROM xsy_trace_unit_binding
    WHERE id = NEW.binding_id AND unit_id = NEW.unit_id;
    IF NOT FOUND OR order_item_id IS NULL THEN
        RAISE EXCEPTION 'trace unit unbind has no valid sales order item'
            USING ERRCODE = '23514';
    END IF;
    PERFORM 1 FROM xsy_sales_order_item WHERE id = order_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'trace unit unbind source order item missing' USING ERRCODE = '23514';
    END IF;
    SELECT EXISTS (
        SELECT 1 FROM xsy_inventory_outbound_item oi
        JOIN xsy_inventory_outbound o ON o.id = oi.outbound_id
        WHERE oi.sales_order_item_id = order_item_id
          AND (o.status = 'CONFIRMED' OR o.confirmed_at IS NOT NULL)
    ) INTO already_shipped;
    IF already_shipped THEN
        RAISE EXCEPTION 'cannot unbind a unit after its sales order item shipped'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_trace_unbind_outbound_guard
    BEFORE INSERT ON xsy_trace_unit_unbind
    FOR EACH ROW EXECUTE FUNCTION xsy_guard_trace_unit_unbind_outbound();

-- 4) 作废是同一包装码行的 UPDATE；不能允许旁路 SQL 把已绑定码直接作废。
--    UPDATE 自身取得 unit 行锁，与绑定 INSERT 触发器的 FOR UPDATE 串行。
CREATE FUNCTION xsy_reject_void_of_bound_unit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'VOID' AND OLD.status IS DISTINCT FROM 'VOID'
        AND EXISTS (
            SELECT 1 FROM xsy_trace_unit_binding b
            WHERE b.unit_id = NEW.id
              AND NOT EXISTS (SELECT 1 FROM xsy_trace_unit_unbind ub WHERE ub.binding_id = b.id)
        ) THEN
        RAISE EXCEPTION 'cannot void a trace unit with an active binding'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_trace_unit_void_active_guard
    BEFORE UPDATE OF status ON xsy_trace_unit
    FOR EACH ROW EXECUTE FUNCTION xsy_reject_void_of_bound_unit();
