INSERT INTO product_category (parent_id, category_code, name, level, sort_order, status)
VALUES (NULL, 'FRESH', '生鲜食材', 1, 10, 'ENABLED');

INSERT INTO product_category (parent_id, category_code, name, level, sort_order, status)
SELECT id, 'FRESH-PRODUCE', '新鲜蔬果', 2, 10, 'ENABLED'
FROM product_category
WHERE category_code = 'FRESH'
  AND deleted = FALSE;

INSERT INTO product_category (parent_id, category_code, name, level, sort_order, status)
SELECT id, 'FRESH-FRUIT', '新鲜水果', 3, 10, 'ENABLED'
FROM product_category
WHERE category_code = 'FRESH-PRODUCE'
  AND deleted = FALSE;

INSERT INTO product_spu (spu_code, name, alias, category_id, description, status)
SELECT 'SPU-APPLE-001', '红富士苹果', '苹果', id, 'Sprint 1 演示商品', 'ON_SHELF'
FROM product_category
WHERE category_code = 'FRESH-FRUIT'
  AND deleted = FALSE;

INSERT INTO product_sku (spu_id, sku_code, barcode, spec_name, spec_values, sale_unit,
                         product_type, market_price, status, is_default, sort_order)
SELECT id,
       'SKU-APPLE-JIN',
       '6900000000011',
       '按斤散装',
       '{"计价方式":"按斤"}'::JSONB, '斤',
       'NON_STANDARD',
       6.9800,
       'ON_SHELF',
       TRUE,
       10
FROM product_spu
WHERE spu_code = 'SPU-APPLE-001'
  AND deleted = FALSE;

INSERT INTO product_sku (spu_id, sku_code, barcode, spec_name, spec_values, sale_unit,
                         product_type, market_price, status, is_default, sort_order)
SELECT id,
       'SKU-APPLE-BOX5',
       '6900000000028',
       '5斤礼盒',
       '{"包装":"礼盒","重量":"5斤"}'::JSONB, '盒',
       'STANDARD',
       39.9000,
       'ON_SHELF',
       FALSE,
       20
FROM product_spu
WHERE spu_code = 'SPU-APPLE-001'
  AND deleted = FALSE;
