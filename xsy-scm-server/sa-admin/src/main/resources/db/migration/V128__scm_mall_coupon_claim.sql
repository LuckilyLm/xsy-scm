-- 商城客户自助领券与下单预选券。
-- xsy_promotion_coupon：claimable 标记该模板是否开放客户在商城自助领取；per_customer_limit /
-- total_limit 为领取上限，NULL 表示不限。三列只约束商城领取入口，后台直接发券（issue）不受影响。
-- xsy_sales_order.mall_coupon_instance_id：仅记录商城客户下单时预选的券实例；正式占用（RESERVED）
-- 仍在订单确认命令内由 PromotionDiscountService.freeze 完成（ADR-009：预览与预选都不等于占用）。
ALTER TABLE xsy_promotion_coupon
    ADD COLUMN claimable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN per_customer_limit INTEGER,
    ADD COLUMN total_limit INTEGER,
    ADD CONSTRAINT ck_promotion_coupon_per_customer_limit CHECK (per_customer_limit IS NULL OR per_customer_limit > 0),
    ADD CONSTRAINT ck_promotion_coupon_total_limit CHECK (total_limit IS NULL OR total_limit > 0);

ALTER TABLE xsy_sales_order
    ADD COLUMN mall_coupon_instance_id BIGINT;

ALTER TABLE xsy_promotion_coupon_instance
    ADD COLUMN issue_source VARCHAR(16) NOT NULL DEFAULT 'ADMIN_ISSUE',
    ADD CONSTRAINT ck_promotion_coupon_instance_issue_source
        CHECK (issue_source IN ('ADMIN_ISSUE', 'MALL_CLAIM'));

CREATE INDEX idx_promotion_coupon_instance_mall_claim
    ON xsy_promotion_coupon_instance (coupon_id, customer_id)
    WHERE issue_source = 'MALL_CLAIM';
