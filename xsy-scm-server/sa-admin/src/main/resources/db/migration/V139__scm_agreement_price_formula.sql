ALTER TABLE xsy_customer_agreement_price
    ADD COLUMN formula_type VARCHAR(32) NOT NULL DEFAULT 'FIXED',
    ADD COLUMN formula_parameter NUMERIC(18, 4),
    ALTER COLUMN unit_price DROP NOT NULL;

ALTER TABLE xsy_customer_agreement_price
    ADD CONSTRAINT ck_agreement_price_formula
        CHECK (
            (formula_type = 'FIXED' AND unit_price IS NOT NULL AND formula_parameter IS NULL)
            OR (formula_type IN ('MARKET_PLUS_AMOUNT', 'COST_MARKUP_RATE', 'MARKET_MARKUP_RATE')
                AND unit_price IS NULL AND formula_parameter IS NOT NULL AND formula_parameter >= 0)
        );

ALTER TABLE xsy_sales_order_item
    ADD COLUMN price_basis_snapshot JSONB;

ALTER TABLE xsy_sales_order_item
    ADD CONSTRAINT ck_order_item_price_basis_object
        CHECK (price_basis_snapshot IS NULL OR jsonb_typeof(price_basis_snapshot) = 'object');

COMMENT ON COLUMN xsy_sales_order_item.price_basis_snapshot IS
    '订单提交时冻结的协议公式、参数、基准价来源及版本；历史行为空';

ALTER TABLE xsy_order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE xsy_order_operation_log ADD CONSTRAINT ck_order_operation_log_type CHECK (operation_type IN (
    'CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY', 'CONFIRM', 'CANCEL',
    'RESERVE_STOCK', 'RETURN', 'REFUND', 'CREDIT_OVERRIDE', 'ASSIGN', 'REPRICE'));
