-- 客户状态去掉「潜在」：新建客户直接进入合作中，存量潜在客户一并转为合作中。
UPDATE xsy_customer
SET status = 'COOPERATING'
WHERE status = 'POTENTIAL';

ALTER TABLE xsy_customer
    ALTER COLUMN status SET DEFAULT 'COOPERATING';

ALTER TABLE xsy_customer
    DROP CONSTRAINT ck_customer_status;

ALTER TABLE xsy_customer
    ADD CONSTRAINT ck_customer_status CHECK (status IN ('COOPERATING', 'SUSPENDED', 'BLACKLIST'));
