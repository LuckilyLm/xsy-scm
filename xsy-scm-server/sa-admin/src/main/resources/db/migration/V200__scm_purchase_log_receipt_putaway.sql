-- V190 重写采购操作日志的类型/归属约束时漏掉了 V22 引入的 RECEIPT_PUTAWAY，
-- 结果仓库二次入库（WAREHOUSE_CONFIRM）写日志必然撞 CHECK，接口 10001。
-- 约束只能追加修正：这里按 V190 的写法把 RECEIPT_PUTAWAY 补回两组允许值，
-- 归属规则与其它收货类日志一致（同属主采购单 + 收货单）。
ALTER TABLE xsy_purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_type;
ALTER TABLE xsy_purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_owner;

ALTER TABLE xsy_purchase_operation_log
    ADD CONSTRAINT ck_purchase_operation_log_type CHECK (operation_type IN (
        'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE',
        'DEMAND_GENERATE','DEMAND_ALLOCATE',
        'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY',
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
     OR (operation_type IN ('RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY')
             AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NOT NULL));
