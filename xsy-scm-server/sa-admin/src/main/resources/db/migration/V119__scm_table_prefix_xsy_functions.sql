-- V118 把 SCM 自有表统一成 xsy_ 前缀，但 PostgreSQL 重命名表不会改写 PL/pgSQL 函数体里的表名
-- （函数体是文本，运行时才解析），于是这两个触发器函数一执行就报 relation ... does not exist。
-- 这里按原实现重建它们，只把引用的表名换成新名；语义与顺序完全不变。
--
-- 其余 7 个触发器函数只在 RAISE EXCEPTION 的消息文本里提到表名（不是表引用），保持不动。

CREATE OR REPLACE FUNCTION xsy_v2.freeze_payable_payment_rule()
    RETURNS trigger
    LANGUAGE plpgsql
AS $function$
DECLARE days INTEGER;
BEGIN
    IF NEW.entry_type = 'RED' THEN
        SELECT due_date, payment_rule_snapshot INTO NEW.due_date, NEW.payment_rule_snapshot
        FROM xsy_finance_payable WHERE id = NEW.original_payable_id;
        RETURN NEW;
    END IF;
    SELECT payment_period_days INTO days FROM xsy_supplier WHERE id = NEW.supplier_id;
    NEW.payment_rule_snapshot = jsonb_build_object('paymentPeriodDays', COALESCE(days, 0));
    NEW.due_date = (NEW.event_at AT TIME ZONE 'Asia/Shanghai')::date + COALESCE(days, 0);
    RETURN NEW;
END $function$;

CREATE OR REPLACE FUNCTION xsy_v2.freeze_receivable_credit_rule()
    RETURNS trigger
    LANGUAGE plpgsql
AS $function$
DECLARE c xsy_customer%ROWTYPE; base_date DATE; month_base DATE;
BEGIN
    IF NEW.entry_type = 'RED' THEN
        SELECT due_date, credit_rule_snapshot INTO NEW.due_date, NEW.credit_rule_snapshot
        FROM xsy_finance_receivable WHERE id = NEW.original_receivable_id;
        RETURN NEW;
    END IF;
    SELECT * INTO c FROM xsy_customer WHERE id = NEW.settlement_customer_id;
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
END $function$;
