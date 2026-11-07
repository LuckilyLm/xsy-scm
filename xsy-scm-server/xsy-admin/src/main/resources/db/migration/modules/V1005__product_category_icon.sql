-- 分类图标使用文件中心的公开图片，空值由客户端显示通用图标。
ALTER TABLE xsy_product_category ADD COLUMN icon_file_key varchar(500);
COMMENT ON COLUMN xsy_product_category.icon_file_key IS '分类图标公开图片文件标识';
