-- 20:00 批量打印的两种客户级单据：客户分拣单和客户送货单。
--
-- 两种单据都以一张销售订单（一个客户）为单位，不复用线路级发货单，也不复用任务级分拣小票。
-- 允许按客户绑定专属模板；默认模板随迁移写入，模板字段必须与 ScmPrintDocumentTypeEnum 保持一致。

ALTER TABLE xsy_print_template DROP CONSTRAINT ck_scm_print_template_document_type;
ALTER TABLE xsy_print_template ADD CONSTRAINT ck_scm_print_template_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER', 'TRACE_UNIT',
                             'STATEMENT', 'CUSTOMER_SORTING_SHEET', 'CUSTOMER_DELIVERY_NOTE'));

ALTER TABLE xsy_print_record DROP CONSTRAINT ck_scm_print_record_document_type;
ALTER TABLE xsy_print_record ADD CONSTRAINT ck_scm_print_record_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER', 'TRACE_UNIT',
                             'STATEMENT', 'CUSTOMER_SORTING_SHEET', 'CUSTOMER_DELIVERY_NOTE'));

ALTER TABLE xsy_print_customer_template DROP CONSTRAINT ck_scm_print_customer_template_document_type;
ALTER TABLE xsy_print_customer_template ADD CONSTRAINT ck_scm_print_customer_template_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER', 'TRACE_UNIT',
                             'STATEMENT', 'CUSTOMER_SORTING_SHEET', 'CUSTOMER_DELIVERY_NOTE'));

INSERT INTO xsy_print_template (document_type, template_code, template_name, default_flag, enabled_flag, model,
                                remark, version, deleted, created_at, updated_at, created_by, updated_by)
VALUES ('CUSTOMER_SORTING_SHEET', 'CUSTOMER_SORTING_SHEET_DEFAULT', '客户分拣单默认模板', TRUE, TRUE,
        '{"title":"客户分拣单","paper":"A4","orientation":"PORTRAIT",
          "headerFields":["customerName","expectDeliveryTime","routeNo","receiverName","receiverPhone","address"],
          "columns":["rowNo","productName","specName","saleUnit","orderedQuantity","actualQuantity","remark"],
          "showTotals":false,
          "footerNote":"实数量以现场称重为准，分拣完成后打印送货单"}'::JSONB,
        '内置默认模板；无价格，实数量列留空供现场手写/核对', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'system', 'system')
ON CONFLICT DO NOTHING;

INSERT INTO xsy_print_template (document_type, template_code, template_name, default_flag, enabled_flag, model,
                                remark, version, deleted, created_at, updated_at, created_by, updated_by)
VALUES ('CUSTOMER_DELIVERY_NOTE', 'CUSTOMER_DELIVERY_NOTE_DEFAULT', '客户送货单默认模板', TRUE, TRUE,
        '{"title":"客户送货单","paper":"A4","orientation":"PORTRAIT",
          "headerFields":["customerName","orderNo","expectDeliveryTime","routeNo","receiverName","receiverPhone","address"],
          "columns":["rowNo","productName","specName","saleUnit","actualQuantity","lockedUnitPrice","settlementLineAmount","remark"],
          "showTotals":true,
          "footerNote":"销售方：________________    收货方：________________"}'::JSONB,
        '内置默认模板；含实数量、价格、金额合计和双方签字栏', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'system', 'system')
ON CONFLICT DO NOTHING;
