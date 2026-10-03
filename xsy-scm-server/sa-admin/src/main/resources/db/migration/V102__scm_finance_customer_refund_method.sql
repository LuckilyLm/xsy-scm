-- ADM-12 3-11b：客户退款的付款方式 —— **只扩客户侧，不动供应商**。
--
-- 背景：线上退款成功要落成 Finance 付款事实（复用 `finance_payment` 的 `CUSTOMER + ORDER_REFUND`），
-- 它的方式是 ONLINE_PAYMENT。但 `ck_finance_payment_method` 目前是一条不分对手方的三值白名单，
-- 直接加 ONLINE_PAYMENT 会让**供应商付款**也拿到这个值 —— 那是支付域反向污染供应商付款语义
-- （与 3-11a 收款方式拆分同一个理由）。
--
-- 因此把 CHECK 改成**按对手方分别约束**：
--   SUPPLIER → CASH / BANK_TRANSFER / OTHER          （保持原样，一个值都不多）
--   CUSTOMER → CASH / BANK_TRANSFER / ONLINE_PAYMENT / OTHER
--
-- 不改任何既有行：现有付款要么是 SUPPLIER（三值内），要么是 CUSTOMER 人工退款（也是三值内），
-- 两组都落在新约束允许的范围内。

ALTER TABLE finance_payment DROP CONSTRAINT ck_finance_payment_method;
ALTER TABLE finance_payment ADD CONSTRAINT ck_finance_payment_method CHECK (
    (counterparty_type = 'SUPPLIER' AND method IN ('CASH', 'BANK_TRANSFER', 'OTHER'))
        OR (counterparty_type = 'CUSTOMER'
        AND method IN ('CASH', 'BANK_TRANSFER', 'ONLINE_PAYMENT', 'OTHER'))
    );

COMMENT ON CONSTRAINT ck_finance_payment_method ON finance_payment IS
    '付款方式按对手方分别约束：供应商保持三值；客户退款允许 ONLINE_PAYMENT（ADM-12 3-11b）';
