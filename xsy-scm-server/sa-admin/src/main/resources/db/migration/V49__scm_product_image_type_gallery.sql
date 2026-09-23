-- PCO-2 图片类型语义收口（审计 §7.1）：image_type 不再参与表达「主图」，主图唯一事实是 is_primary。
-- V44 的 PRIMARY/DETAIL 加上 (image_type = 'PRIMARY') = is_primary，让同一件事存在两个事实源；
-- 最新计划改为 image_type = GALLERY（图集）/ DETAIL（详情图）。历史迁移不可修改，故在此新增一步。
-- 涉及数据改写（存量值改名 + 重复主图降级），只影响 product_image。

-- 先移除 V44 的两条约束：改写取值的过程中它们会拒绝中间态。
ALTER TABLE product_image
    DROP CONSTRAINT IF EXISTS ck_product_image_type_primary;

ALTER TABLE product_image
    DROP CONSTRAINT IF EXISTS ck_product_image_type;

-- 存量主图转为图集图：这些行本来就是商品图集的一员，前端展示行为不变。
UPDATE product_image
SET image_type = 'GALLERY'
WHERE image_type = 'PRIMARY';

ALTER TABLE product_image
    ADD CONSTRAINT ck_product_image_type CHECK (image_type IN ('GALLERY', 'DETAIL'));

-- 主图唯一性此前只有 Java 校验，历史库可能已有同 SPU 多张主图；不先降级会让下面的唯一索引整体失败。
-- 判据：同 SPU 内 id 不是最小的主图行 = 「后设置的那几张」，保留最早一张并显式提示，不静默改账。
DO $do$
    DECLARE
        extra INTEGER;
    BEGIN
        SELECT count(*)
        INTO extra
        FROM product_image p
        WHERE p.deleted = FALSE
          AND p.is_primary
          AND EXISTS (SELECT 1
                      FROM product_image q
                      WHERE q.deleted = FALSE
                        AND q.is_primary
                        AND q.spu_id = p.spu_id
                        AND q.id < p.id);
        IF extra > 0 THEN
            RAISE NOTICE 'product_image 存在 % 张重复主图，已按「保留最早一张」降级为图集图，需人工复核来源', extra;
        END IF;
    END
$do$;

WITH duplicated AS (SELECT p.id
                    FROM product_image p
                    WHERE p.deleted = FALSE
                      AND p.is_primary
                      AND EXISTS (SELECT 1
                                  FROM product_image q
                                  WHERE q.deleted = FALSE
                                    AND q.is_primary
                                    AND q.spu_id = p.spu_id
                                    AND q.id < p.id))
UPDATE product_image
SET is_primary = FALSE
WHERE id IN (SELECT id FROM duplicated);

-- 一个商品最多一张主图，由数据库保证而不是调用顺序：GALLERY / DETAIL 都不参与该约束。
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_image_primary_spu
    ON product_image (spu_id)
    WHERE is_primary = TRUE AND deleted = FALSE;

COMMENT ON COLUMN product_image.image_type IS '图片类型：GALLERY 图集 / DETAIL 详情图；不表达主图，主图唯一事实是 is_primary';

COMMENT ON INDEX uq_product_image_primary_spu IS '每个未删除商品最多一张主图（is_primary 唯一事实）';
