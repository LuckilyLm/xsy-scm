-- 商品、规格、分类、标签属于内部主数据身份；用户提交值不参与发号。
-- 已有业务编号的 PG sequence + 前缀机制保持不变，旧商品编码不重编。
CREATE SEQUENCE product_spu_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE product_sku_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE product_category_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE product_tag_code_seq START WITH 1 INCREMENT BY 1;

-- 全表（含软删除）取历史序号下界，不能仅检查活跃索引。
-- 此处新建序列不涉及已发放序列倒退；输出为至少六位流水。
SELECT setval('product_spu_code_seq',
    COALESCE((SELECT MAX(substring(spu_code FROM '^SPU([0-9]{1,12})$')::bigint) + 1
              FROM xsy_product_spu WHERE spu_code ~ '^SPU[0-9]{1,12}$'), 1), false);
SELECT setval('product_sku_code_seq',
    COALESCE((SELECT MAX(substring(sku_code FROM '^SKU([0-9]{1,12})$')::bigint) + 1
              FROM xsy_product_sku WHERE sku_code ~ '^SKU[0-9]{1,12}$'), 1), false);
SELECT setval('product_category_code_seq',
    COALESCE((SELECT MAX(substring(category_code FROM '^CAT([0-9]{1,12})$')::bigint) + 1
              FROM xsy_product_category WHERE category_code ~ '^CAT[0-9]{1,12}$'), 1), false);
SELECT setval('product_tag_code_seq',
    COALESCE((SELECT MAX(substring(tag_code FROM '^TAG([0-9]{1,12})$')::bigint) + 1
              FROM xsy_product_tag WHERE tag_code ~ '^TAG[0-9]{1,12}$'), 1), false);
