-- 集团共享结算授权：集团主体显式允许子客户用它的钱包结算。
--
-- 默认关闭：商城过去只记录结算约定、不代扣共享资金，打开这个开关是有意的授权动作，
-- 不能因为客户类型是 GROUP 就默认生效。
-- 子客户仍然看不到集团的钱包余额与授信总额（decisions.md 的商城资金口径不变）。

ALTER TABLE xsy_customer
    ADD COLUMN IF NOT EXISTS shared_settlement_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 只有集团主体（结算模式 GROUP）才谈得上授权；其余客户的标记必须为假。
ALTER TABLE xsy_customer
    ADD CONSTRAINT ck_customer_shared_settlement_scope
        CHECK (NOT shared_settlement_enabled OR settle_mode = 'GROUP');

COMMENT ON COLUMN xsy_customer.shared_settlement_enabled IS
    '集团主体是否允许其子客户使用集团钱包结算；默认 FALSE，仅 GROUP 结算模式可置真';
