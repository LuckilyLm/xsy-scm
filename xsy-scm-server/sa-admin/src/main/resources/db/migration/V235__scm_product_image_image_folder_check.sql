-- 2026-11-02 起所有新上传都落在 public/ 前缀，商品图与其它公开附件不再能靠
-- public/ 一级前缀区分。商品图仍只能从图片中心上传的 public/image/ 目录绑定，
-- 否则公告附件、反馈截图等公开文件也能被直接写成商品图。

ALTER TABLE xsy_product_image
    DROP CONSTRAINT ck_product_image_public_file_key;

ALTER TABLE xsy_product_image
    ADD CONSTRAINT ck_product_image_public_file_key
        CHECK (file_key LIKE 'public/image/%' OR deleted = TRUE);

COMMENT ON CONSTRAINT ck_product_image_public_file_key ON xsy_product_image IS
    '商品图只能引用 public/image/ 前缀；软删行保留搬运前的原 key 作为历史事实';
