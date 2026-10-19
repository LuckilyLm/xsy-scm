-- 商城首页主题：一组受控配色，跟着装修发布版本走。
--
-- 主题只改主色/辅色，不换版式：版式由区块顺序决定，主题只负责观感。
-- 放在草稿与发布版本上而不是单独开关表，是为了让回滚把配色一起带回去——
-- 否则「回滚了版式却留着新配色」会成为说不清的状态。

ALTER TABLE xsy_mall_home_draft
    ADD COLUMN IF NOT EXISTS theme_code VARCHAR(16) NOT NULL DEFAULT 'FRESH';

ALTER TABLE xsy_mall_home_draft
    ADD CONSTRAINT ck_mall_home_draft_theme
        CHECK (theme_code IN ('FRESH', 'WARM', 'OCEAN'));

ALTER TABLE xsy_mall_home_release
    ADD COLUMN IF NOT EXISTS theme_code VARCHAR(16) NOT NULL DEFAULT 'FRESH';

ALTER TABLE xsy_mall_home_release
    ADD CONSTRAINT ck_mall_home_release_theme
        CHECK (theme_code IN ('FRESH', 'WARM', 'OCEAN'));
