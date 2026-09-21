-- 销售订单 Excel 导入来源。
-- 导入仍写入正式 sales_order 聚合，不建立平行订单或同步表。
ALTER TABLE sales_order
    DROP CONSTRAINT ck_sales_order_source;
ALTER TABLE sales_order
    ADD CONSTRAINT ck_sales_order_source
        CHECK (order_source IN ('ADMIN', 'MALL', 'SUPPLEMENT', 'IMPORT'));
