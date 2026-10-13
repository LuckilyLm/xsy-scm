-- V2 起不引入数据库外键，引用完整性由服务层守卫与事务边界承担（见 smartadmin-foundation.md）。
-- V125 建 xsy_payment_refund_recovery_log 时把外键内联进了列定义，成了全库唯一一个外键，这里移除；
-- 恢复审计的归属校验仍由 PaymentRefundRecoveryService 在写入前完成，不依赖该约束。
-- 索引 idx_payment_refund_recovery_log_refund 由 V125 显式建立，不受本次改动影响。
ALTER TABLE xsy_payment_refund_recovery_log
    DROP CONSTRAINT IF EXISTS xsy_payment_refund_recovery_log_refund_id_fkey;
