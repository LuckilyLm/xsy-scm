-- 采购锁单：供应商对账完成后，不再让任何人顺手改价改量。
--
-- 与销售锁单（V184）同一形态：锁不是采购状态，status 仍是 SUBMITTED / 收货中，
-- 收货、入库、应付都不受影响；它只关掉补价与改价两个入口。
--
-- 起算点：提交时刻 + 7 天（与价格窗口同一锚点）。窗口内允许补价，窗口外自动补价并锁单，
-- 让「待定价」不会永久挂着。手工锁/解锁是独立权限，解锁必须填原因。

ALTER TABLE xsy_purchase_order
    ADD COLUMN locked_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(64),
    ADD COLUMN lock_reason VARCHAR(32),
    ADD COLUMN auto_lock_at TIMESTAMPTZ;

ALTER TABLE xsy_purchase_order
    ADD CONSTRAINT ck_purchase_order_lock_state CHECK (
        (locked_flag = FALSE AND locked_at IS NULL AND locked_by IS NULL)
            OR (locked_flag = TRUE AND locked_at IS NOT NULL AND locked_by IS NOT NULL));

COMMENT ON COLUMN xsy_purchase_order.locked_flag IS '锁单标记：TRUE 后补价/改价需要独立权限';
COMMENT ON COLUMN xsy_purchase_order.lock_reason IS 'MANUAL 手动锁单；AUTO_7_DAYS 提交满 7 天自动锁单';
COMMENT ON COLUMN xsy_purchase_order.auto_lock_at IS '自动锁单时点：提交时刻 + 7 天；草稿单为空';

CREATE INDEX idx_purchase_order_auto_lock
    ON xsy_purchase_order (auto_lock_at)
    WHERE deleted = FALSE
        AND locked_flag = FALSE
        AND auto_lock_at IS NOT NULL;
