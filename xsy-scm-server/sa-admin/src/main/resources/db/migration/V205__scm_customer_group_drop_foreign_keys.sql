-- 客户分组沿用了建表时写 REFERENCES 的老习惯，V109 之后全库已改为零外键、引用由服务层校验。
-- 这里把 V198 带进来的两条外键撤掉，恢复全库约束口径；引用完整性仍由 CustomerGroupService /
-- CustomerGroupPriceService 在写库前校验，不依赖数据库级联。
ALTER TABLE xsy_customer
    DROP CONSTRAINT IF EXISTS xsy_customer_customer_group_id_fkey;

ALTER TABLE xsy_customer_group_price
    DROP CONSTRAINT IF EXISTS xsy_customer_group_price_group_id_fkey;
