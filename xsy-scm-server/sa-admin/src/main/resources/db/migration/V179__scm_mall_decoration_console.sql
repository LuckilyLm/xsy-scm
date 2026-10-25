-- 商城装修后台：自定义主题色、首页弹窗、富文本单页与店铺配置。
-- 主题色为空时沿用原有 theme_code 配色，历史发布记录不受影响。

ALTER TABLE xsy_mall_home_draft ADD COLUMN IF NOT EXISTS theme_color VARCHAR(7);
ALTER TABLE xsy_mall_home_release ADD COLUMN IF NOT EXISTS theme_color VARCHAR(7);

ALTER TABLE xsy_mall_home_draft
    ADD CONSTRAINT ck_mall_home_draft_theme_color CHECK (theme_color IS NULL OR theme_color ~ '^#[0-9A-F]{6}$');
ALTER TABLE xsy_mall_home_release
    ADD CONSTRAINT ck_mall_home_release_theme_color CHECK (theme_color IS NULL OR theme_color ~ '^#[0-9A-F]{6}$');

-- 首页弹窗是单行配置：保存即生效，靠生效时间控制展示窗口。
CREATE TABLE xsy_mall_home_popup (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    display_rule VARCHAR(16) NOT NULL DEFAULT 'DAILY' CHECK (display_rule IN ('DAILY', 'EVERY_ENTRY')),
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    content JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(content) = 'array'),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
);
INSERT INTO xsy_mall_home_popup(id) VALUES (1);

-- 富文本单页：草稿与发布内容分开，法律文本发布后仍可回看上一版。
CREATE TABLE xsy_mall_page (
    page_code VARCHAR(32) PRIMARY KEY,
    title VARCHAR(80) NOT NULL,
    draft_content TEXT NOT NULL DEFAULT '',
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    published_content TEXT,
    published_version INTEGER,
    published_at TIMESTAMPTZ,
    published_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

INSERT INTO xsy_mall_page(page_code, title) VALUES
    ('ANNOUNCEMENT', '商城公告'),
    ('TERMS', '服务条款'),
    ('ABOUT', '关于我们'),
    ('AFTER_SALE', '售后规则')
ON CONFLICT (page_code) DO NOTHING;

-- 店铺配置：名称、可分享链接；二维码由后台按链接生成。
CREATE TABLE xsy_mall_store (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    store_name VARCHAR(80) NOT NULL DEFAULT '',
    store_link VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);
INSERT INTO xsy_mall_store(id, store_name) VALUES (1, '商城');

UPDATE t_menu SET menu_name = '商城装修' WHERE menu_id = 1850;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES
    (1854, '商城弹窗查询', 3, 1850, 1854, NULL, NULL, 1, 'scm:mall:popup:query', 'scm:mall:popup:query', 1850, TRUE, 1),
    (1855, '商城弹窗维护', 3, 1850, 1855, NULL, NULL, 1, 'scm:mall:popup:edit', 'scm:mall:popup:edit', 1850, TRUE, 1),
    (1856, '商城页面查询', 3, 1850, 1856, NULL, NULL, 1, 'scm:mall:page:query', 'scm:mall:page:query', 1850, TRUE, 1),
    (1857, '商城页面维护', 3, 1850, 1857, NULL, NULL, 1, 'scm:mall:page:edit', 'scm:mall:page:edit', 1850, TRUE, 1),
    (1858, '店铺配置查询', 3, 1850, 1858, NULL, NULL, 1, 'scm:mall:store:query', 'scm:mall:store:query', 1850, TRUE, 1),
    (1859, '店铺配置维护', 3, 1850, 1859, NULL, NULL, 1, 'scm:mall:store:edit', 'scm:mall:store:edit', 1850, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id FROM t_menu m WHERE m.menu_id BETWEEN 1854 AND 1859
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r JOIN t_menu m ON m.menu_id BETWEEN 1854 AND 1859
WHERE r.role_code = 'SCM_SALES_LEAD'
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);
