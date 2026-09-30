-- ============================================================================
-- V69 SCM Finance R1 F1-4: 核销、反向核销与手工红字应付权限（data-only）
--
-- 本阶段发布的能力点都对应已落地的受保护端点：
--   1515 scm:finance:write-off:query
--   1523 scm:finance:write-off:add
--   1524 scm:finance:write-off:reverse
--   1525 scm:finance:payable:red
-- 不新增页面菜单或范围放宽点；只授 SUPER_ADMIN 与正式财务岗位 SCM_FINANCE。
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
    (1515, '核销查询', 3, 1500, 1515, NULL, NULL, 1,
     'scm:finance:write-off:query', 'scm:finance:write-off:query', NULL, 1500, true, 1),
    (1523, '登记核销', 3, 1500, 1523, NULL, NULL, 1,
     'scm:finance:write-off:add', 'scm:finance:write-off:add', NULL, 1500, true, 1),
    (1524, '反向核销', 3, 1500, 1524, NULL, NULL, 1,
     'scm:finance:write-off:reverse', 'scm:finance:write-off:reverse', NULL, 1500, true, 1),
    (1525, '登记红字应付', 3, 1500, 1525, NULL, NULL, 1,
     'scm:finance:payable:red', 'scm:finance:payable:red', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1515, 1523, 1524, 1525)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
         CROSS JOIN LATERAL unnest(ARRAY[1515::bigint, 1523::bigint, 1524::bigint, 1525::bigint]) AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
