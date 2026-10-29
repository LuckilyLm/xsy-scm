INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (1539, '财务确认采购', 3, 703, 1539, 1, 'scm:purchase:finance-confirm', 'scm:purchase:finance-confirm',
    703, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE (r.role_id = 1 OR r.role_code = 'SCM_FINANCE') AND m.menu_id = 1539
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);

