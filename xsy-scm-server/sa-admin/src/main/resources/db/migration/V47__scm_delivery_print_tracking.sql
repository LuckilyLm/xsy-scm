-- Wave 5 配送打印追踪：在既有 delivery_route_order 上扩展打印生成记录，不新建副本表。
-- 语义：print_count 表示“生成过打印预览 / 打印任务”的历史次数，不证明浏览器或打印机物理出纸成功；
-- 也不代表当前线路内容版本已打印（本轮不做内容版本追踪）。
ALTER TABLE delivery_route_order ADD COLUMN print_count INTEGER NOT NULL DEFAULT 0 CHECK(print_count >= 0);
ALTER TABLE delivery_route_order ADD COLUMN last_printed_at TIMESTAMPTZ;
ALTER TABLE delivery_route_order ADD COLUMN last_printed_by VARCHAR(64);
COMMENT ON COLUMN delivery_route_order.print_count IS '生成打印预览/打印任务的历史次数；重试幂等只计一次，不代表物理出纸';
COMMENT ON COLUMN delivery_route_order.last_printed_at IS '最后一次生成打印的时间';
COMMENT ON COLUMN delivery_route_order.last_printed_by IS '最后一次生成打印的操作者 userType:userId';
