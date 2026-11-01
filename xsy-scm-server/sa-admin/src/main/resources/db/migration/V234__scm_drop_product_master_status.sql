-- 主档状态与上架 / 下架高度重叠，现场不使用「停止引用 / 归档」；商品退役统一用下架表达。
ALTER TABLE xsy_product_spu DROP CONSTRAINT IF EXISTS ck_product_spu_archived_off_shelf;
ALTER TABLE xsy_product_spu DROP CONSTRAINT IF EXISTS ck_product_spu_master_status;
ALTER TABLE xsy_product_spu DROP COLUMN IF EXISTS master_status;
