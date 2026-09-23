-- PCO-2 商品图片类型：给 product_image 增加 image_type（PRIMARY/DETAIL），与既有 is_primary 对齐。
-- 只加一列，不新建图片类型表；第一版仅 PRIMARY/DETAIL，避免提前引入视频/营销/证书等类型。
ALTER TABLE product_image
    ADD COLUMN image_type VARCHAR(16) NOT NULL DEFAULT 'DETAIL';

-- 存量主图回填为 PRIMARY；其余保持 DETAIL。
UPDATE product_image SET image_type = 'PRIMARY' WHERE is_primary = TRUE;

-- 取值域：第一版只允许两种。
ALTER TABLE product_image
    ADD CONSTRAINT ck_product_image_type CHECK (image_type IN ('PRIMARY', 'DETAIL'));

-- image_type 与 is_primary 必须一致，防止「两张主图 / 主图却标 DETAIL」这类第二套事实分叉。
-- 未来若引入更多类型需同步调整本约束，由当时的设计裁决。
ALTER TABLE product_image
    ADD CONSTRAINT ck_product_image_type_primary CHECK ((image_type = 'PRIMARY') = is_primary);

COMMENT ON COLUMN product_image.image_type IS '图片类型：PRIMARY 主图 / DETAIL 详情图；与 is_primary 同义受约束保证一致';
