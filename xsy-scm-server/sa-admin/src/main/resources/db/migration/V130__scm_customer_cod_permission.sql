-- 货到付款是客户级交易授权，不等同于授信额度。
-- 历史客户默认关闭，由后台客户编辑明确开通；已存在的 V129 订单保留原结算约定。
ALTER TABLE xsy_customer
    ADD COLUMN cod_allowed BOOLEAN NOT NULL DEFAULT FALSE;
