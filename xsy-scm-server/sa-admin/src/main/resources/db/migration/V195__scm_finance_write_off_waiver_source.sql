-- 抹零接入核销：把 WAIVER 作为一种核销来源，而不是在应收余额上另开一条计算路径。
--
-- 这样做的唯一理由是「只有一套余额」：未结额 = 应收净额 - 核销净额，
-- 账龄、对账单、收款待核销额都已经按这条公式算，抹零只要写一行核销就自动生效。
--
-- 约束仍然挡住方向错误：WAIVER 只能核应收，不能核应付。

ALTER TABLE xsy_finance_write_off DROP CONSTRAINT ck_finance_write_off_source_type;
ALTER TABLE xsy_finance_write_off ADD CONSTRAINT ck_finance_write_off_source_type
    CHECK (source_type IN ('RECEIPT', 'PAYMENT', 'BALANCE_MOVEMENT', 'WAIVER'));

ALTER TABLE xsy_finance_write_off DROP CONSTRAINT ck_finance_write_off_pairing;
ALTER TABLE xsy_finance_write_off ADD CONSTRAINT ck_finance_write_off_pairing
    CHECK ((source_type IN ('RECEIPT', 'BALANCE_MOVEMENT', 'WAIVER') AND target_type = 'RECEIVABLE')
        OR (source_type = 'PAYMENT' AND target_type = 'PAYABLE'));

-- 一条抹零事实只能产生一条 NORMAL 核销；重复提交由服务层幂等键 + 本索引双层兜底。
CREATE UNIQUE INDEX uk_finance_write_off_waiver
    ON xsy_finance_write_off (source_type, source_id)
    WHERE entry_type = 'NORMAL' AND source_type = 'WAIVER';

COMMENT ON COLUMN xsy_finance_write_off.source_type IS
    '核销来源：RECEIPT 收款 / PAYMENT 付款 / BALANCE_MOVEMENT 余额消费 / WAIVER 抹零';

-- 财务日志新增抹零动作；业务类型同样要放行 WAIVER。
ALTER TABLE xsy_finance_operation_log DROP CONSTRAINT ck_finance_operation_log_type;
ALTER TABLE xsy_finance_operation_log ADD CONSTRAINT ck_finance_operation_log_type
    CHECK (operation_type IN ('GENERATE', 'RED_GENERATE', 'RECEIVE', 'PAY',
                              'WRITE_OFF', 'WRITE_OFF_REVERSE',
                              'RECEIPT_REVERSE', 'PAYMENT_REVERSE',
                              'WAIVER_ADD', 'WAIVER_REVERSE'));

ALTER TABLE xsy_finance_operation_log DROP CONSTRAINT ck_finance_operation_log_business_type;
ALTER TABLE xsy_finance_operation_log ADD CONSTRAINT ck_finance_operation_log_business_type
    CHECK (business_type IN ('RECEIVABLE', 'PAYABLE', 'RECEIPT', 'PAYMENT', 'WRITE_OFF', 'WAIVER'));
