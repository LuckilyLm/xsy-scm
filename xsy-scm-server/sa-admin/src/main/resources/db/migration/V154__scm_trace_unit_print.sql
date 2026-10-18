-- 包装码标签打印：沿用打印中心的受控模板与冻结记录，新增包装码标签单据类型。
--
-- 标签是贴在实物上的身份，用 80mm 热敏纸：只放码、批次、商品与包装量，以及绑定后的客户与订单号；
-- 不含价格与成本，模板也不能放宽白名单之外的字段。

ALTER TABLE xsy_print_template DROP CONSTRAINT ck_scm_print_template_document_type;
ALTER TABLE xsy_print_template ADD CONSTRAINT ck_scm_print_template_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER', 'TRACE_UNIT'));

ALTER TABLE xsy_print_record DROP CONSTRAINT ck_scm_print_record_document_type;
ALTER TABLE xsy_print_record ADD CONSTRAINT ck_scm_print_record_document_type
    CHECK (document_type IN ('PURCHASE_ORDER', 'DELIVERY_NOTE', 'SORTING_TICKET', 'SALES_ORDER', 'TRACE_UNIT'));

INSERT INTO xsy_print_template (document_type, template_code, template_name, default_flag,
                                enabled_flag, model, remark, created_by, updated_by)
VALUES ('TRACE_UNIT', 'TRACE_UNIT_LABEL', '包装码标签默认模板', TRUE, TRUE,
        '{"title":"包装码标签","paper":"TICKET_80","orientation":"PORTRAIT",
          "headerFields":["unitCode","lotNo","sourceLotNo","productName","skuName",
                          "packageType","quantity","unit","customerName","orderNo"],
          "columns":[],
          "showTotals":false,
          "footerNote":"码不透明且永不复用；未绑定分拣行的标签只表示实物身份，不表示已分配给任何客户。"}'::jsonb,
        '包装码标签：80mm 热敏纸，只含身份与去向，不含价格', 'system', 'system')
ON CONFLICT DO NOTHING;
