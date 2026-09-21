-- B1 warehouse enable/disable + receipt putaway permissions, approved 2026-09-18.
-- Data-only: 3 t_menu rows (822 / 823 / 824) + role_id = 1 grants + sequence advance.
-- NO DDL, NO new role, NO permission change to any existing menu.
--
-- Menu ids re-verified against V20 (which used the 800-821 block): 822-824 are free.
-- Icons are not needed for button menus (menu_type = 3, path/component/icon = NULL).
--   scm:purchase:receipt:putaway  确认入库   (parent 704 采购收货)
--   scm:warehouse:enable          启用       (parent 706 仓库管理)
--   scm:warehouse:disable         停用       (parent 706 仓库管理)
--
-- receipt confirm != warehouse putaway (two independent permissions) is intentional:
-- confirm is a purchasing action, putaway is a warehouse action.

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (822, '确认入库', 3, 704, 822, NULL, NULL, 1, 'scm:purchase:receipt:putaway', 'scm:purchase:receipt:putaway',
        NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (823, '启用', 3, 706, 823, NULL, NULL, 1, 'scm:warehouse:enable', 'scm:warehouse:enable', NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (824, '停用', 3, 706, 824, NULL, NULL, 1, 'scm:warehouse:disable', 'scm:warehouse:disable', NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (822, 823, 824)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
