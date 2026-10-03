-- ADM-12 3-11a：支付成功 → Finance 收款事实的唯一来源映射。
--
-- 两条口径（负责人 2026-10-03 确认，已核对现有 Finance 实现）：
--
-- 1. **COD 不进 `finance_receipt.method`**。这个字段表示「实际收款方式 / 资金渠道」，
--    而 COD 是「什么时候收钱」的**结算时机**。货到付款最终真正收到的仍然是
--    CASH / BANK_TRANSFER / ONLINE_PAYMENT 之一，所以 COD 属于订单侧的结算方式，
--    不属于这里。
--
-- 2. **只改收款的方式白名单，不动付款**。`ScmFinancePaymentMethodEnum` 目前被收款与
--    供应商付款**共用**；直接加 ONLINE_PAYMENT / BALANCE 会让供应商付款入口也拿到这些值，
--    等于让支付这个新业务域反向污染供应商付款语义。因此收款改用**自己的**
--    `ScmFinanceReceiptMethodEnum`，供应商付款的枚举与 `ck_finance_payment_method` 保持原样。
--
-- 本迁移**不改任何既有行**，只扩白名单 + 加两列 + 加约束与索引。

-- ---------------------------------------------------------------------------
-- 1. 收款方式白名单（只扩收款侧；付款侧 CHECK 刻意不动）
-- ---------------------------------------------------------------------------
ALTER TABLE finance_receipt DROP CONSTRAINT ck_finance_receipt_method;
ALTER TABLE finance_receipt ADD CONSTRAINT ck_finance_receipt_method
    CHECK (method IN ('CASH', 'BANK_TRANSFER', 'ONLINE_PAYMENT', 'OTHER'));

-- ---------------------------------------------------------------------------
-- 2. 来源键：系统生成的收款必须能回答「这笔钱是谁登记进来的」
-- ---------------------------------------------------------------------------
ALTER TABLE finance_receipt ADD COLUMN source_type VARCHAR(32);
ALTER TABLE finance_receipt ADD COLUMN source_id   BIGINT;

-- 成对：有来源类型就必须有来源 id，反之亦然。半个来源键等于没有来源键。
ALTER TABLE finance_receipt ADD CONSTRAINT ck_finance_receipt_source_pairing CHECK (
    (source_type IS NULL AND source_id IS NULL)
        OR (source_type IS NOT NULL AND source_id IS NOT NULL)
    );

-- 来源类型白名单：当前只有「支付交易」。刻意**不**允许自由填写 ——
-- 来源类型一旦可随意填，唯一索引就形同虚设（换个名字就能为同一笔钱再登记一次）。
ALTER TABLE finance_receipt ADD CONSTRAINT ck_finance_receipt_source_type CHECK (
    source_type IS NULL OR source_type = 'PAYMENT_TRANSACTION'
    );

-- 来源只属于 NORMAL：反向事实是**纠错**，不是「另一笔来源收款」。
-- 与既有的「REVERSE 专用列在 NORMAL 行上必须为空」是同一套配对纪律的反向表达。
ALTER TABLE finance_receipt ADD CONSTRAINT ck_finance_receipt_source_normal_only CHECK (
    entry_type = 'NORMAL' OR (source_type IS NULL AND source_id IS NULL)
    );

-- 唯一来源键：**第二层幂等**。
-- 第一层在支付域（payment_callback_event 按渠道事件 id 唯一）；
-- 这一层保证「同一笔支付交易无论被驱动多少次，Finance 只有一条正常收款事实」。
-- 只覆盖 NORMAL 且带来源的行：人工收款没有来源，不受影响。
CREATE UNIQUE INDEX uk_finance_receipt_source_active
    ON finance_receipt (source_type, source_id)
    WHERE deleted = FALSE
      AND entry_type = 'NORMAL'
      AND source_type IS NOT NULL;

COMMENT ON COLUMN finance_receipt.source_type IS
    '系统来源类型；人工登记的收款为空。当前仅 PAYMENT_TRANSACTION（ADM-12 3-11a）';
COMMENT ON COLUMN finance_receipt.source_id IS
    '系统来源主键；source_type = PAYMENT_TRANSACTION 时是 payment_transaction.id';
