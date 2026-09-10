INSERT INTO customer_type (type_code, name, status)
VALUES ('STANDARD', '标准客户', 'ENABLED');

INSERT INTO customer (customer_code, name, customer_type_id, status, visibility_policy)
SELECT 'CUST-DEMO-001', '鲜蔬源演示客户', id, 'ENABLED', 'ALL_ENABLED'
FROM customer_type
WHERE type_code = 'STANDARD'
  AND deleted = FALSE;
