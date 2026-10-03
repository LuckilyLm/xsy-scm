-- 内部余额支付不产生收款；余额消费仅作为正常应收的系统核销来源。
ALTER TABLE payment_intent DROP CONSTRAINT ck_payment_intent_provider;
ALTER TABLE payment_intent ADD CONSTRAINT ck_payment_intent_provider
    CHECK (provider IN ('MOCK', 'WECHAT', 'INTERNAL_BALANCE'));
ALTER TABLE payment_intent ADD CONSTRAINT ck_payment_intent_method_provider
    CHECK ((method = 'ONLINE' AND provider IN ('MOCK', 'WECHAT'))
        OR (method = 'BALANCE' AND provider = 'INTERNAL_BALANCE' AND source_type = 'SALES_ORDER'
            AND mock_scenario IS NULL AND external_intent_id IS NULL));

ALTER TABLE customer_balance_movement DROP CONSTRAINT ck_customer_balance_movement_source_type;
ALTER TABLE customer_balance_movement ADD CONSTRAINT ck_customer_balance_movement_source_type
    CHECK (source_type IS NULL OR source_type IN ('PAYMENT_TRANSACTION', 'PAYMENT_INTENT', 'ORDER_REFUND'));
ALTER TABLE customer_balance_movement ADD CONSTRAINT ck_customer_balance_movement_business_source
    CHECK ((type = 'RECHARGE' AND direction = 'CREDIT' AND source_type IS NOT NULL
            AND source_type = 'PAYMENT_TRANSACTION' AND source_id IS NOT NULL)
        OR (type = 'CONSUME' AND direction = 'DEBIT' AND source_type IS NOT NULL
            AND source_type = 'PAYMENT_INTENT' AND source_id IS NOT NULL)
        OR (type = 'REFUND' AND direction = 'CREDIT' AND source_type IS NOT NULL
            AND source_type = 'ORDER_REFUND' AND source_id IS NOT NULL)
        OR (type = 'CORRECTION' AND source_type IS NULL AND source_id IS NULL
            AND btrim(COALESCE(reason, '')) <> ''));
-- 复用 uk_customer_balance_movement_source，不重复建立同义索引。

ALTER TABLE finance_write_off DROP CONSTRAINT ck_finance_write_off_source_type;
ALTER TABLE finance_write_off ADD CONSTRAINT ck_finance_write_off_source_type
    CHECK (source_type IN ('RECEIPT', 'PAYMENT', 'BALANCE_MOVEMENT'));
ALTER TABLE finance_write_off DROP CONSTRAINT ck_finance_write_off_pairing;
ALTER TABLE finance_write_off ADD CONSTRAINT ck_finance_write_off_pairing
    CHECK ((source_type IN ('RECEIPT', 'BALANCE_MOVEMENT') AND target_type = 'RECEIVABLE')
        OR (source_type = 'PAYMENT' AND target_type = 'PAYABLE'));
CREATE UNIQUE INDEX uk_finance_write_off_balance_movement
    ON finance_write_off (source_type, source_id)
    WHERE entry_type = 'NORMAL' AND source_type = 'BALANCE_MOVEMENT';

ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_transaction_provider
    CHECK (provider IN ('MOCK', 'WECHAT', 'INTERNAL_BALANCE'));
ALTER TABLE payment_callback_event ADD CONSTRAINT ck_payment_callback_external_provider
    CHECK (provider IN ('MOCK', 'WECHAT'));
ALTER TABLE payment_refund ADD CONSTRAINT ck_payment_refund_external_provider
    CHECK (provider IN ('MOCK', 'WECHAT'));
ALTER TABLE payment_reconciliation ADD CONSTRAINT ck_payment_reconciliation_external_provider
    CHECK (provider IN ('MOCK', 'WECHAT'));
