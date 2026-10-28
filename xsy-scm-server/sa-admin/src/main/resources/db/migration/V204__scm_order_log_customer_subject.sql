-- 独立退货没有订单，操作日志改挂客户（D-1028-15）。
-- 订单日志保持 order_id 不变；两类日志至少要有其中一个主体，避免出现无归属的孤立日志。
ALTER TABLE xsy_order_operation_log
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN customer_id bigint,
    ADD CONSTRAINT ck_order_operation_log_subject CHECK (order_id IS NOT NULL OR customer_id IS NOT NULL);

CREATE INDEX idx_order_operation_log_customer_created ON xsy_order_operation_log (customer_id, created_at DESC)
    WHERE customer_id IS NOT NULL;
