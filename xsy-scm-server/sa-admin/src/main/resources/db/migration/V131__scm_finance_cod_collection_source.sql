-- V131：货到付款收款的来源类型
--
-- 货到付款的资金事实是「交付时真正收到的钱」，由财务登记一笔收款表达。
-- 这笔钱必须能回答「是哪张单的」，因此给收款事实加一个系统来源类型。
--
-- 为什么不走人工收款（source_type IS NULL）：
--   人工收款没有来源键，唯一索引 uk_finance_receipt_source_active 管不到它 ——
--   同一张 COD 订单被重复登记两次收款不会在库上被拦下。
--   带来源的收款让「一张 COD 订单最多一笔收款」由唯一索引直接兜住，
--   而不是靠服务层「先查后写」那种有窗口的判定。
--
-- 收款方式仍只取 CASH / BANK_TRANSFER 等真实资金形式，不新增 COD 方式值：
--   COD 是「什么时候收钱」的结算时机，不是资金形式（finance-r1-design.md Q21 已裁决）。
--
-- 只扩白名单，不改任何既有行；PAYMENT_TRANSACTION 的语义与唯一索引不变。

ALTER TABLE xsy_finance_receipt DROP CONSTRAINT ck_finance_receipt_source_type;
ALTER TABLE xsy_finance_receipt ADD CONSTRAINT ck_finance_receipt_source_type CHECK (
    source_type IS NULL OR source_type IN ('PAYMENT_TRANSACTION', 'ORDER_COD_COLLECTION')
    );
