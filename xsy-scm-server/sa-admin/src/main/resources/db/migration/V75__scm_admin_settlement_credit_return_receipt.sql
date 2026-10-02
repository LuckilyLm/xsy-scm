ALTER TABLE customer ADD COLUMN settlement_customer_id BIGINT;
-- Existing documents retain the original debtor; group membership alone did not authorize consolidated settlement.
UPDATE customer SET settlement_customer_id = id;
ALTER TABLE customer ADD CONSTRAINT ck_customer_settlement_not_self_parent CHECK (settlement_customer_id > 0);
CREATE FUNCTION default_customer_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.settlement_customer_id = COALESCE(NEW.settlement_customer_id, NEW.id);
    RETURN NEW;
END $$;
CREATE TRIGGER trg_customer_default_settlement BEFORE INSERT ON customer
FOR EACH ROW EXECUTE FUNCTION default_customer_settlement();
ALTER TABLE customer ALTER COLUMN settlement_customer_id SET NOT NULL;
CREATE INDEX idx_customer_settlement_active ON customer (settlement_customer_id) WHERE deleted = FALSE;

ALTER TABLE sales_order ADD COLUMN settlement_customer_id BIGINT;
ALTER TABLE sales_order ADD COLUMN settlement_customer_name_snapshot VARCHAR(150);
ALTER TABLE sales_order ADD COLUMN credit_override_reason VARCHAR(500);
UPDATE sales_order o SET settlement_customer_id = COALESCE(c.settlement_customer_id, o.customer_id), settlement_customer_name_snapshot = COALESCE(sc.name, o.customer_name_snapshot)
FROM customer c LEFT JOIN customer sc ON sc.id = c.settlement_customer_id
WHERE c.id = o.customer_id;
ALTER TABLE sales_order ALTER COLUMN settlement_customer_id SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN settlement_customer_name_snapshot SET NOT NULL;

ALTER TABLE finance_receivable ADD COLUMN settlement_customer_id BIGINT;
ALTER TABLE finance_receivable ADD COLUMN settlement_customer_name_snapshot VARCHAR(150);
ALTER TABLE finance_receivable ADD COLUMN due_date DATE;
ALTER TABLE finance_receivable ADD COLUMN credit_rule_snapshot JSONB;
UPDATE finance_receivable r SET settlement_customer_id = o.settlement_customer_id, settlement_customer_name_snapshot = o.settlement_customer_name_snapshot
FROM sales_order o WHERE o.id = r.order_id;
ALTER TABLE finance_receivable ALTER COLUMN settlement_customer_id SET NOT NULL;
ALTER TABLE finance_receivable ALTER COLUMN settlement_customer_name_snapshot SET NOT NULL;
ALTER TABLE finance_receivable ADD CONSTRAINT ck_finance_receivable_credit_rule_snapshot CHECK (credit_rule_snapshot IS NULL OR jsonb_typeof(credit_rule_snapshot) = 'object');
-- Migration-time freeze for existing receivables: NORMAL rows use the settlement customer's
-- currently effective BY_TIME rule; RED rows inherit the already-frozen original receivable.
UPDATE finance_receivable r
SET credit_rule_snapshot = jsonb_build_object('type', c.credit_period_type, 'value', c.credit_period_value,
        'unit', c.credit_period_unit, 'settleDay', c.settle_day, 'amountThreshold', c.credit_amount_threshold),
    due_date = CASE
        WHEN c.credit_period_unit = 'DAY'
            THEN (r.event_at AT TIME ZONE 'Asia/Shanghai')::date + c.credit_period_value
        WHEN c.credit_period_unit = 'MONTH'
            THEN (date_trunc('month', (r.event_at AT TIME ZONE 'Asia/Shanghai')::date)
                    + make_interval(months => c.credit_period_value))::date
                 + (COALESCE(c.settle_day, 1) - 1)
        ELSE NULL
    END
FROM customer c
WHERE r.entry_type = 'NORMAL'
  AND c.id = r.settlement_customer_id
  AND c.credit_period_type = 'BY_TIME';
UPDATE finance_receivable red
SET credit_rule_snapshot = original.credit_rule_snapshot,
    due_date = original.due_date
FROM finance_receivable original
WHERE red.entry_type = 'RED' AND original.id = red.original_receivable_id;
CREATE INDEX idx_finance_receivable_settlement_due ON finance_receivable (settlement_customer_id, due_date) WHERE deleted = FALSE;

ALTER TABLE finance_receipt ADD COLUMN settlement_customer_id BIGINT;
ALTER TABLE finance_receipt ADD COLUMN settlement_customer_name_snapshot VARCHAR(150);
UPDATE finance_receipt r SET settlement_customer_id = c.settlement_customer_id, settlement_customer_name_snapshot = sc.name
FROM customer c JOIN customer sc ON sc.id = c.settlement_customer_id WHERE c.id = r.customer_id;
ALTER TABLE finance_receipt ALTER COLUMN settlement_customer_id SET NOT NULL;
ALTER TABLE finance_receipt ALTER COLUMN settlement_customer_name_snapshot SET NOT NULL;
CREATE INDEX idx_finance_receipt_settlement ON finance_receipt (settlement_customer_id) WHERE deleted = FALSE;

ALTER TABLE supplier ADD COLUMN payment_period_days INTEGER NOT NULL DEFAULT 0;
ALTER TABLE supplier ADD CONSTRAINT ck_supplier_payment_period_days CHECK (payment_period_days >= 0 AND payment_period_days <= 3650);
ALTER TABLE finance_payable ADD COLUMN due_date DATE;
ALTER TABLE finance_payable ADD COLUMN payment_rule_snapshot JSONB;
ALTER TABLE finance_payable ADD CONSTRAINT ck_finance_payable_payment_rule_snapshot CHECK (payment_rule_snapshot IS NULL OR jsonb_typeof(payment_rule_snapshot) = 'object');
-- Migration-time freeze for existing payables; RED rows keep the original payable's terms.
UPDATE finance_payable p
SET payment_rule_snapshot = jsonb_build_object('paymentPeriodDays', s.payment_period_days),
    due_date = (p.event_at AT TIME ZONE 'Asia/Shanghai')::date + s.payment_period_days
FROM supplier s
WHERE p.entry_type = 'NORMAL' AND s.id = p.supplier_id;
UPDATE finance_payable red
SET payment_rule_snapshot = original.payment_rule_snapshot,
    due_date = original.due_date
FROM finance_payable original
WHERE red.entry_type = 'RED' AND original.id = red.original_payable_id;

CREATE FUNCTION freeze_receivable_credit_rule() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c customer%ROWTYPE; base_date DATE; month_base DATE;
BEGIN
    IF NEW.entry_type = 'RED' THEN
        SELECT due_date, credit_rule_snapshot INTO NEW.due_date, NEW.credit_rule_snapshot
        FROM finance_receivable WHERE id = NEW.original_receivable_id;
        RETURN NEW;
    END IF;
    SELECT * INTO c FROM customer WHERE id = NEW.settlement_customer_id;
    NEW.credit_rule_snapshot = jsonb_build_object('type', c.credit_period_type, 'value', c.credit_period_value,
        'unit', c.credit_period_unit, 'settleDay', c.settle_day, 'amountThreshold', c.credit_amount_threshold);
    base_date = (NEW.event_at AT TIME ZONE 'Asia/Shanghai')::date;
    IF c.credit_period_type = 'BY_TIME' AND c.credit_period_unit = 'DAY' THEN
        NEW.due_date = base_date + c.credit_period_value;
    ELSIF c.credit_period_type = 'BY_TIME' AND c.credit_period_unit = 'MONTH' THEN
        month_base = (date_trunc('month', base_date) + make_interval(months => c.credit_period_value))::date;
        NEW.due_date = CASE WHEN c.settle_day IS NULL THEN month_base ELSE month_base + (c.settle_day - 1) END;
    ELSE
        NEW.due_date = NULL;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_receivable_credit_rule BEFORE INSERT ON finance_receivable FOR EACH ROW EXECUTE FUNCTION freeze_receivable_credit_rule();

CREATE FUNCTION freeze_payable_payment_rule() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE days INTEGER;
BEGIN
    IF NEW.entry_type = 'RED' THEN
        SELECT due_date, payment_rule_snapshot INTO NEW.due_date, NEW.payment_rule_snapshot
        FROM finance_payable WHERE id = NEW.original_payable_id;
        RETURN NEW;
    END IF;
    SELECT payment_period_days INTO days FROM supplier WHERE id = NEW.supplier_id;
    NEW.payment_rule_snapshot = jsonb_build_object('paymentPeriodDays', COALESCE(days, 0));
    NEW.due_date = (NEW.event_at AT TIME ZONE 'Asia/Shanghai')::date + COALESCE(days, 0);
    RETURN NEW;
END $$;
CREATE TRIGGER trg_finance_payable_payment_rule BEFORE INSERT ON finance_payable FOR EACH ROW EXECUTE FUNCTION freeze_payable_payment_rule();

CREATE TABLE order_return_receipt (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    operator VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)
);
CREATE UNIQUE INDEX uk_order_return_receipt_key ON order_return_receipt (return_id, idempotency_key);

CREATE TABLE order_return_receipt_item (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    return_item_id BIGINT NOT NULL,
    source_sales_out_movement_id BIGINT NOT NULL,
    source_outbound_item_id BIGINT NOT NULL,
    disposition VARCHAR(32) NOT NULL,
    quantity NUMERIC(18,4) NOT NULL,
    unit_snapshot VARCHAR(32) NOT NULL,
    unit_cost NUMERIC(18,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT ck_order_return_receipt_item_disposition CHECK (disposition IN ('RETURN_TO_STOCK','DAMAGE')),
    CONSTRAINT ck_order_return_receipt_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_return_receipt_item_cost CHECK (unit_cost >= 0)
);
CREATE UNIQUE INDEX uk_order_return_receipt_item_allocation
    ON order_return_receipt_item (receipt_id, return_item_id, source_sales_out_movement_id);
CREATE INDEX idx_order_return_receipt_item_return ON order_return_receipt_item (return_item_id);
CREATE INDEX idx_order_return_receipt_item_sales_out ON order_return_receipt_item (source_sales_out_movement_id);

ALTER TABLE inventory_movement DROP CONSTRAINT ck_inventory_movement_type;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN ('PURCHASE_IN','SALES_OUT','SALES_RETURN_IN','STOCKTAKE_GAIN','STOCKTAKE_LOSS','LOSS_REPORT','GAIN_REPORT','TRANSFER_OUT','TRANSFER_IN','CONVERT_OUT','CONVERT_IN'));
ALTER TABLE inventory_movement DROP CONSTRAINT ck_inventory_movement_snap;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_snap CHECK (
    (movement_type IN ('PURCHASE_IN','SALES_RETURN_IN','STOCKTAKE_GAIN','GAIN_REPORT','TRANSFER_IN','CONVERT_IN') AND after_quantity = before_quantity + quantity)
    OR (movement_type IN ('SALES_OUT','STOCKTAKE_LOSS','LOSS_REPORT','TRANSFER_OUT','CONVERT_OUT') AND after_quantity = before_quantity - quantity)
);

ALTER TABLE order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE order_operation_log ADD CONSTRAINT ck_order_operation_log_type CHECK (operation_type IN ('CREATE','UPDATE','SUBMIT','ACTUAL_QUANTITY','CONFIRM','CANCEL','RESERVE_STOCK','RETURN','REFUND','CREDIT_OVERRIDE'));

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
(47501, '退货实物接收', 3, 603, 80, NULL, NULL, 1, 'scm:order:return:receive', NULL, NULL, NULL, FALSE, 1),
(47502, '授信例外确认', 3, 601, 90, NULL, NULL, 1, 'scm:order:credit:override', NULL, NULL, NULL, FALSE, 1)
ON CONFLICT (menu_id) DO NOTHING;
