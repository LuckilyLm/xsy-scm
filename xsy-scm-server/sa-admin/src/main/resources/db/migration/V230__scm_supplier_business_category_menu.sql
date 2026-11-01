-- 供应商采购分类独立成页，挂到「供应商管理」下的供应商分类子菜单；
-- 客户配送分类保留在「客户管理 > 客户分类」。两个页面复用同一套分类权限。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, icon,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES
    (47625, '供应商分类', 2, 461, 465, '/supplier/business-category-list',
     '/business/scm/supplier/business-category-list.vue', 'AppstoreOutlined', NULL, NULL, NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 有供应商管理入口的角色同步可见，保持与供应商档案、商品供应商一致。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT DISTINCT rm.role_id, 47625
FROM t_role_menu rm
WHERE rm.menu_id = 461
  AND NOT EXISTS (SELECT 1 FROM t_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 47625);

-- V216 建「检测报告」菜单时漏了图标，补齐后可见菜单才都符合图标契约。
UPDATE t_menu
SET icon = 'FileSearchOutlined',
    update_time = NOW()
WHERE menu_id = 2140
  AND (icon IS NULL OR btrim(icon) = '');
