-- V111 建序列时假定「历史编码格式与新号不同，因此不会重号」，这个假设对已有同格式编码的环境不成立：
-- 存量里只要有一条 CUS000001，nextval 从 1 起就会撞唯一键，用户看到的是「客户编码重复」。
-- 这里不猜历史格式，直接按各表现存编码的「前缀 + 数字后缀」取最大后缀，把序列推进到它之后。
-- 无匹配存量时保持从 1 开始，与 V111 的行为一致（setval 的第三参决定 nextval 是否 +1）。
SELECT setval('customer_code_seq',
              COALESCE((SELECT MAX(SUBSTRING(customer_code FROM '^CUS([0-9]{1,12})$')::bigint)
                        FROM customer
                        WHERE customer_code ~ '^CUS[0-9]{1,12}$'), 1),
              EXISTS (SELECT 1 FROM customer WHERE customer_code ~ '^CUS[0-9]{1,12}$'));
SELECT setval('supplier_code_seq',
              COALESCE((SELECT MAX(SUBSTRING(supplier_code FROM '^SUP([0-9]{1,12})$')::bigint)
                        FROM supplier
                        WHERE supplier_code ~ '^SUP[0-9]{1,12}$'), 1),
              EXISTS (SELECT 1 FROM supplier WHERE supplier_code ~ '^SUP[0-9]{1,12}$'));
SELECT setval('warehouse_code_seq',
              COALESCE((SELECT MAX(SUBSTRING(warehouse_code FROM '^WH([0-9]{1,12})$')::bigint)
                        FROM warehouse
                        WHERE warehouse_code ~ '^WH[0-9]{1,12}$'), 1),
              EXISTS (SELECT 1 FROM warehouse WHERE warehouse_code ~ '^WH[0-9]{1,12}$'));
