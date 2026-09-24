-- ============================================================================
-- Finance R0 报表中心的日期轴索引
--
-- R0 的三个主口径都按「业务日」范围扫描，而既有索引只覆盖到 created_at：
--   sales_order     既有 (status, created_at)   → 按 confirmed_at 的范围筛只能状态前缀 + 回表过滤
--   order_refund    既有 (status, created_at)   → 已完成退款按 completed_at 筛
--   purchase_receipt 既有 (status, created_at)  → 收货确认按 confirmed_at 筛
-- 三张表的这两个时间列此前没有任何报表/接口按范围查，所以缺索引不是历史遗漏，而是 R0 首次引入。
--
-- 部分索引的谓词与查询的等值条件逐字一致（status 固定 + deleted=FALSE），
-- 因此索引只装真正参与统计的行，既小又不会被无关状态稀释。
-- inventory_movement 已有 (occurred_at) 与 (warehouse_id, sku_id, occurred_at)，未新增。
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_sales_order_status_confirmed
    ON sales_order (confirmed_at DESC, id DESC)
    WHERE deleted = FALSE AND status = 'CONFIRMED';

CREATE INDEX IF NOT EXISTS idx_order_refund_status_completed
    ON order_refund (completed_at DESC, id DESC)
    WHERE deleted = FALSE AND status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_purchase_receipt_status_confirmed
    ON purchase_receipt (confirmed_at DESC, id DESC)
    WHERE deleted = FALSE AND status = 'CONFIRMED';

COMMENT ON INDEX idx_sales_order_status_confirmed IS '销售报表按确认日期范围统计已确认订单（Finance R0）';
COMMENT ON INDEX idx_order_refund_status_completed IS '按退款完成日期统计已完成退款金额（Finance R0）';
COMMENT ON INDEX idx_purchase_receipt_status_confirmed IS '收货报表按收货确认日期统计已确认收货单（Finance R0）';
