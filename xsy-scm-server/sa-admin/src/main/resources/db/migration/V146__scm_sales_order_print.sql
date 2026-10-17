-- SDP-14：沿用打印中心受控模板、冻结打印快照及记录；新增销售订单单据类型。
ALTER TABLE scm_print_template DROP CONSTRAINT ck_scm_print_template_document_type;
ALTER TABLE scm_print_template ADD CONSTRAINT ck_scm_print_template_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER'));

ALTER TABLE scm_print_record DROP CONSTRAINT ck_scm_print_record_document_type;
ALTER TABLE scm_print_record ADD CONSTRAINT ck_scm_print_record_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER'));

INSERT INTO scm_print_template (document_type, template_code, template_name, default_flag,
                                enabled_flag, model, remark, created_by, updated_by)
VALUES ('SALES_ORDER', 'SALES_ORDER_DEFAULT', '销售订单默认模板', TRUE, TRUE,
        '{"title":"销售订单","paper":"A4","orientation":"PORTRAIT",
          "headerFields":["orderNo","customerName","customerCode","orderStatus","expectDeliveryTime",
                          "receiverName","receiverPhone","address","remark"],
          "columns":["productName","skuCode","specName","saleUnit","orderedQuantity",
                     "actualQuantity","lockedUnitPrice","orderedLineAmount","settlementLineAmount"],
          "showTotals":true,
          "footerNote":"草稿金额仅为录单参考；未填写的实数量或结算金额不代表零。打印不代表确认订单、签收或收款。"}'::jsonb,
        '销售订单打印模板；采用订单当时已保存的快照，不重新定价', 'system', 'system')
ON CONFLICT DO NOTHING;
