-- 采购价后置：当场只谈数量、价格稍后补。
--
-- 现场节奏是 14:00 采购当天及次日用量，供应商当天不给最终价，第二天才回价；
-- 若价格仍然必填，采购员只能随手填 0 或估一个数，后面再也没人分得清哪个是真价。
--
-- 口径（docs/decisions.md D-1028-06）：
--   * 待定价（price_pending=TRUE）⇔ purchase_price IS NULL，两者由 CHECK 绑定，不允许「有价但还是待定价」；
--   * 行金额在待定价期间记 0，合计随之少算，页面必须显式标出「待定价」，不能把缺价当成省钱；
--   * 价格窗口从采购单提交时刻起算，最多 7 天，由定时任务用最近已提交采购价回填并锁定；
--   * 已确认收货的库存成本不追改：收货确认前必须有价（接收货服务抛 PURCHASE_PRICE_PENDING）。
--
-- 历史的已提交行价格都是真价，新增列默认 FALSE 即可，不需要回填。

ALTER TABLE xsy_purchase_order_item
    ALTER COLUMN purchase_price DROP NOT NULL,
    ADD COLUMN price_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN price_window_started_at TIMESTAMPTZ,
    ADD COLUMN price_filled_at TIMESTAMPTZ,
    ADD COLUMN price_fill_source VARCHAR(24);

ALTER TABLE xsy_purchase_order_item
    ADD CONSTRAINT ck_purchase_order_item_price_pending
        CHECK (price_pending = (purchase_price IS NULL)),
    ADD CONSTRAINT ck_purchase_order_item_price_fill_source
        CHECK (price_fill_source IS NULL OR price_fill_source IN ('MANUAL', 'AUTO_LAST_PRICE'));

COMMENT ON COLUMN xsy_purchase_order_item.price_pending IS '待定价：TRUE 时 purchase_price 必须为空，行金额按 0 计';
COMMENT ON COLUMN xsy_purchase_order_item.price_window_started_at IS '价格窗口起点：采购单提交时刻（不是创建时刻）';
COMMENT ON COLUMN xsy_purchase_order_item.price_filled_at IS '补价完成时刻；为空表示仍待定价';
COMMENT ON COLUMN xsy_purchase_order_item.price_fill_source IS '补价来源：MANUAL 人工补价 / AUTO_LAST_PRICE 超期按最近已提交价回填';

-- 自动回填只关心「仍待定价且窗口已开」的行；部分索引避免扫描全部采购明细。
CREATE INDEX idx_purchase_order_item_price_pending
    ON xsy_purchase_order_item (price_window_started_at)
    WHERE deleted = FALSE AND price_pending = TRUE;

-- 操作日志增加补价/改价/锁单类型。锁单与补价都挂在采购单头，不涉及收货单。
ALTER TABLE xsy_purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_type;
ALTER TABLE xsy_purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_owner;

ALTER TABLE xsy_purchase_operation_log
    ADD CONSTRAINT ck_purchase_operation_log_type CHECK (operation_type IN (
        'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE',
        'DEMAND_GENERATE','DEMAND_ALLOCATE',
        'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE',
        'PRICE_FILL','PRICE_ADJUST','PRICE_AUTO_FILL','PRICE_LOCK','PRICE_UNLOCK','PRICE_AUTO_LOCK')),
    ADD CONSTRAINT ck_purchase_operation_log_owner CHECK (
        (operation_type = 'DEMAND_GENERATE'
             AND purchase_order_id IS NULL     AND purchase_receipt_id IS NULL)
     OR (operation_type = 'DEMAND_ALLOCATE'
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
     OR (operation_type IN ('CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE',
                            'PRICE_FILL','PRICE_ADJUST','PRICE_AUTO_FILL',
                            'PRICE_LOCK','PRICE_UNLOCK','PRICE_AUTO_LOCK')
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
     OR (operation_type IN ('RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE')
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NOT NULL));
