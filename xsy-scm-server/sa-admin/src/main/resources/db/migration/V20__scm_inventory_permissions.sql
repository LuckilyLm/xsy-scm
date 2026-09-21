-- W6 Inventory phase 1 permissions, approved 2026-09-18 (Q8).
-- See docs/architecture/2026-09-18-w6-inventory-approval.md.
-- Data-only: 5 t_menu rows (800 / 801 / 802 / 811 / 821) + role_id = 1 grants + sequence advance.
-- NO DDL, NO new role, NO permission change to any existing menu (W6-1 excludes non-admin role seeds).
--
-- Menu ids were re-verified against the live tree before choosing them (Q8): the largest existing
-- menu id is 753 (W5 warehouse), so the 800 block is free and does not collide.
-- Icons are written in the same INSERT (not patched afterwards) -- V18 proved the icon column
-- holds an Ant Design Vue component name, and a follow-up UPDATE would be a needless second pass.
--   InboxOutlined         库存管理
--   ProfileOutlined       库存余额
--   UnorderedListOutlined 库存流水
--
-- Idempotency: ON CONFLICT (menu_id) DO NOTHING for menus, NOT EXISTS for grants, and the
-- sequence is advanced with setval(..., max + 1, false) exactly like V16.

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (800, '库存管理', 1, 0, 800, '/inventory', NULL, NULL, NULL, NULL, 'InboxOutlined', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (801, '库存余额', 2, 800, 801, '/inventory/inventory-balance-list',
        '/business/scm/inventory/inventory-balance-list.vue', NULL, NULL, NULL, 'ProfileOutlined', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (802, '库存流水', 2, 800, 802, '/inventory/inventory-movement-list',
        '/business/scm/inventory/inventory-movement-list.vue', NULL, NULL, NULL, 'UnorderedListOutlined', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (811, '查询', 3, 801, 811, NULL, NULL, 1, 'scm:inventory:balance:query', 'scm:inventory:balance:query', NULL,
        NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (821, '查询', 3, 802, 821, NULL, NULL, 1, 'scm:inventory:movement:query', 'scm:inventory:movement:query', NULL,
        NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (800, 801, 802, 811, 821)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
