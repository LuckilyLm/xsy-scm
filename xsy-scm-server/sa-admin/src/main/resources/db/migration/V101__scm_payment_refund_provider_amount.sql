-- ADM-12 3-11a.1：退款单补「渠道实际退款金额」。
--
-- 为什么必须补：`payment_refund.amount` 是**申请退款金额**（本地决定的），
-- 而渠道实际退了多少是另一回事（可能被渠道改、可能部分成功）。3-11b 要让 Finance 认
-- 渠道实际退款额，就必须先有这个字段 —— 否则「退款成功后 Finance 认渠道实退金额」
-- 这条口径落不了地。
--
-- 与 `payment_transaction.amount` / `provider_amount` 同一套纪律：本地申请与实际到账**分开两列**，
-- 渠道回报不覆盖本地申请额，不一致时由对账（退款对账，尚未实现）发现。
--
-- 不改任何既有行：新列可空，历史行保持 NULL（表示「渠道还没回报退款金额」）。

ALTER TABLE payment_refund ADD COLUMN provider_amount NUMERIC(18, 4);

ALTER TABLE payment_refund ADD CONSTRAINT ck_payment_refund_provider_amount CHECK (
    provider_amount IS NULL OR provider_amount > 0
    );

-- 成功态的退款必须有渠道实退金额：否则「退成功了多少」无从回答，
-- 3-11b 也无法据此登记资金反向事实。
ALTER TABLE payment_refund ADD CONSTRAINT ck_payment_refund_success_provider_amount CHECK (
    status <> 'SUCCEEDED' OR provider_amount IS NOT NULL
    );

COMMENT ON COLUMN payment_refund.provider_amount IS
    '渠道实际退款金额；申请额见 amount。成功态必有值（ADM-12 3-11a.1）';
