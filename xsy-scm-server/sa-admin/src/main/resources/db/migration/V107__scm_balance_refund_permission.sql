-- 纯余额售后返还重试入口。业务退款完成时自动派生不要求此人工重试权限。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1723, '售后余额返还', 3, 1718, 1723, NULL, NULL, 1,
    'scm:balance:refund', 'scm:balance:refund', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, 1723 WHERE NOT EXISTS (SELECT 1 FROM t_role_menu WHERE role_id = 1 AND menu_id = 1723);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
