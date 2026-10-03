-- ADM-12 3-6：限时特价。
--
-- 口径（docs/decisions.md）：
--   * **不改基础定价链**：协议价 → 客户类型价 → 市场价 保持不动，限时特价不参与这一层。
--   * 它作为 Promotion 在**基础价之后**作用：基础价 → 限时特价 → 满减/折扣 → 优惠券。
--   * 冻结事实要能回答「原基础价多少、特价让利多少、最后多少」，而不是把订单行的价格来源
--     改成「限时活动」——那会让「协议价本来是多少」永久丢失。
--   * 保护规则：特价只能把价格往下压，不能抬高（特价 ≥ 该行单价时让利为 0）。
--
-- 本 migration 做两件事，全部是新增/扩白名单，不改任何既有行：
--   1. 活动类型白名单加 SPECIAL_PRICE；
--   2. order_discount 加 special_discount_amount，并把不可变触发器一并覆盖到它。

ALTER TABLE promotion_activity DROP CONSTRAINT ck_promotion_activity_type;
ALTER TABLE promotion_activity ADD CONSTRAINT ck_promotion_activity_type
    CHECK (activity_type IN ('FULL_REDUCE', 'DISCOUNT', 'FULL_GIFT', 'SPECIAL_PRICE'));

-- 限时特价让利额（正数，表示减免）。它已经包含在 allocations 的逐行分摊里，
-- 这里单独落一列是为了能直接回答「这个月限时特价让利多少」，不必回读快照 JSON。
ALTER TABLE order_discount ADD COLUMN special_discount_amount NUMERIC(18, 4) NOT NULL DEFAULT 0;
ALTER TABLE order_discount ADD CONSTRAINT ck_order_discount_special
    CHECK (special_discount_amount >= 0 AND special_discount_amount <= discount_amount);

-- 冻结快照不可变：新增列必须一并纳入守卫，否则它就成了快照上唯一可被改写的一格。
CREATE OR REPLACE FUNCTION reject_order_discount_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.sales_order_id IS DISTINCT FROM OLD.sales_order_id
        OR NEW.discount_amount IS DISTINCT FROM OLD.discount_amount
        OR NEW.special_discount_amount IS DISTINCT FROM OLD.special_discount_amount
        OR NEW.allocations IS DISTINCT FROM OLD.allocations
        OR NEW.activity_snapshot IS DISTINCT FROM OLD.activity_snapshot
        OR NEW.coupon_snapshot IS DISTINCT FROM OLD.coupon_snapshot THEN
        RAISE EXCEPTION 'order_discount is an immutable snapshot';
    END IF;
    RETURN NEW;
END $$;
