-- V113 把序列直接设成「现存编码最大后缀」。若序列因为失败事务等原因已经走在前面（nextval 不随事务回滚），
-- 这一步会把它拉回去，接着就会重用已经发出去的编号 —— 撞不到唯一键，但违反编码单调。
-- 这里取两者较大者作为地板：max(当前序列的下一值, 现存编码最大后缀 + 1)。
-- setval 第三参为 false 表示「下一个 nextval 就返回这个值」，因此无需再 ±1。
-- 读序列关系本身的 last_value / is_called 而不是 pg_sequences：前者在从未调用过时也有确定值（1 / false）。
SELECT setval('customer_code_seq',
              GREATEST(
                  (SELECT last_value FROM customer_code_seq)
                      + CASE WHEN (SELECT is_called FROM customer_code_seq) THEN 1 ELSE 0 END,
                  COALESCE((SELECT MAX(SUBSTRING(customer_code FROM '^CUS([0-9]{1,12})$')::bigint)
                            FROM customer
                            WHERE customer_code ~ '^CUS[0-9]{1,12}$'), 0) + 1
              ),
              false);
SELECT setval('supplier_code_seq',
              GREATEST(
                  (SELECT last_value FROM supplier_code_seq)
                      + CASE WHEN (SELECT is_called FROM supplier_code_seq) THEN 1 ELSE 0 END,
                  COALESCE((SELECT MAX(SUBSTRING(supplier_code FROM '^SUP([0-9]{1,12})$')::bigint)
                            FROM supplier
                            WHERE supplier_code ~ '^SUP[0-9]{1,12}$'), 0) + 1
              ),
              false);
SELECT setval('warehouse_code_seq',
              GREATEST(
                  (SELECT last_value FROM warehouse_code_seq)
                      + CASE WHEN (SELECT is_called FROM warehouse_code_seq) THEN 1 ELSE 0 END,
                  COALESCE((SELECT MAX(SUBSTRING(warehouse_code FROM '^WH([0-9]{1,12})$')::bigint)
                            FROM warehouse
                            WHERE warehouse_code ~ '^WH[0-9]{1,12}$'), 0) + 1
              ),
              false);
