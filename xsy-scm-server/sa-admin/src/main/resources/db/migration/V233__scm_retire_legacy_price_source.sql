-- 客户类型价下线后，历史订单行不再保留已下线的来源枚举，统一记为 LEGACY（历史价格）。
-- 只改来源标记，不动成交单价、数量与金额；价格变更日志 append-only，历史日志由查询过滤。
ALTER TABLE xsy_sales_order_item DROP CONSTRAINT IF EXISTS ck_sales_order_item_draft_source;
ALTER TABLE xsy_sales_order_item DROP CONSTRAINT IF EXISTS ck_sales_order_item_locked_source;

UPDATE xsy_sales_order_item
SET draft_price_source = 'LEGACY',
    draft_price_source_id = NULL
WHERE draft_price_source = 'CUSTOMER_TYPE';

UPDATE xsy_sales_order_item
SET locked_price_source = 'LEGACY',
    locked_price_source_id = NULL
WHERE locked_price_source = 'CUSTOMER_TYPE';

ALTER TABLE xsy_sales_order_item
    ADD CONSTRAINT ck_sales_order_item_draft_source
        CHECK (draft_price_source IN
               ('AGREEMENT', 'CUSTOMER_GROUP', 'TIME_PRICE', 'MARKET', 'LEGACY', 'OVERRIDE'));

ALTER TABLE xsy_sales_order_item
    ADD CONSTRAINT ck_sales_order_item_locked_source
        CHECK (locked_price_source IS NULL
            OR locked_price_source IN
               ('AGREEMENT', 'CUSTOMER_GROUP', 'TIME_PRICE', 'MARKET', 'LEGACY', 'OVERRIDE'));

UPDATE xsy_order_return_item
SET price_source = 'LEGACY'
WHERE price_source = 'CUSTOMER_TYPE';
