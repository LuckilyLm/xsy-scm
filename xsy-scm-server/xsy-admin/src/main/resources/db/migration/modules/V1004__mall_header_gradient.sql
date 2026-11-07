-- 首页顶部配色随草稿发布及版本回滚；存量空值由服务端使用浅绿到白色默认值。
ALTER TABLE xsy_mall_home_draft
    ADD COLUMN header_start_color varchar(7),
    ADD COLUMN header_end_color varchar(7);

ALTER TABLE xsy_mall_home_release
    ADD COLUMN header_start_color varchar(7),
    ADD COLUMN header_end_color varchar(7);

COMMENT ON COLUMN xsy_mall_home_draft.header_start_color IS '首页顶部渐变起始色';
COMMENT ON COLUMN xsy_mall_home_draft.header_end_color IS '首页顶部渐变结束色';
COMMENT ON COLUMN xsy_mall_home_release.header_start_color IS '发布时的首页顶部渐变起始色';
COMMENT ON COLUMN xsy_mall_home_release.header_end_color IS '发布时的首页顶部渐变结束色';
