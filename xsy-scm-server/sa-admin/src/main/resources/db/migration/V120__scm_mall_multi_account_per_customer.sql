-- 一个客户可绑定多个采购账号（2026-10-10 负责人确认）。
-- 放开 V119 的「一客一号」唯一索引，改为普通索引；登录手机号仍全局唯一。
DROP INDEX IF EXISTS uk_mall_customer_account_customer_active;

CREATE INDEX idx_mall_customer_account_customer_active
    ON mall_customer_account (customer_id) WHERE deleted = FALSE;

COMMENT ON INDEX idx_mall_customer_account_customer_active IS
    '同一客户可有多个采购账号；账号唯一性由登录手机号（uk_mall_customer_account_phone_active）保证';
