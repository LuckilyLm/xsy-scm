-- 扩展类型目录，保留已有模板及不可变打印记录。
ALTER TABLE scm_print_template DROP CONSTRAINT ck_scm_print_template_document_type;
ALTER TABLE scm_print_template ADD CONSTRAINT ck_scm_print_template_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET'));
ALTER TABLE scm_print_record DROP CONSTRAINT ck_scm_print_record_document_type;
ALTER TABLE scm_print_record ADD CONSTRAINT ck_scm_print_record_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET'));

INSERT INTO scm_print_template (document_type, template_code, template_name, default_flag, enabled_flag,
                                model, created_by, updated_by)
VALUES ('DELIVERY_NOTE', 'DELIVERY_NOTE_DEFAULT', '发货单默认模板', TRUE, TRUE,
        '{"title":"线路发货单","paper":"A4","orientation":"LANDSCAPE","headerFields":["routeName","deliveryDate","warehouseName","driverName","driverPhone","vehicleNo","remark"],"columns":["stopSeq","customerName","address","receiverName","receiverPhone","orderNo","productName","specName","saleUnit","orderedQuantity","actualQuantity","orderedLineAmount","settlementLineAmount"],"showTotals":true,"footerNote":"本单为配送计划；空白数量表示尚未填写。打印不代表发车，不扣减库存。"}'::JSONB,
        'system', 'system'),
       ('SORTING_TICKET', 'SORTING_TICKET_DEFAULT', '分拣小票默认模板', TRUE, TRUE,
        '{"title":"分拣小票","paper":"TICKET_80","orientation":"PORTRAIT","headerFields":["warehouseName","assigneeName"],"columns":["customerName","productName","saleUnit","plannedQuantity","sortedQuantity"],"showTotals":false,"footerNote":"空白实分量表示尚未录入；分拣不回写订单、不改库存。"}'::JSONB,
        'system', 'system');
