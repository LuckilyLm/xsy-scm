-- 新商城订单只提交订货需求；历史订单的结算约定和资金事实不回写。
-- 合并时由 V181 顺延至此：V181 已被客户/供应商业务分类占用并应用。
ALTER TABLE xsy_sales_order
    ADD COLUMN mall_order_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE xsy_sales_order
    ADD CONSTRAINT ck_sales_order_mall_order_only_source
        CHECK (NOT mall_order_only OR order_source = 'MALL'),
    ADD CONSTRAINT ck_sales_order_mall_order_only_confirmation
        CHECK (NOT mall_order_only OR status <> 'CONFIRMED' OR settlement_method IS NOT NULL);

COMMENT ON COLUMN xsy_sales_order.mall_order_only IS
    '仅下单商城订单；创建时不约定支付方式，后台确认时确定结算方式，历史订单为false';
