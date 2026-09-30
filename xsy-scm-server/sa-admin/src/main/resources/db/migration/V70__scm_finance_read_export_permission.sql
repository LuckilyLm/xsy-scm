-- ============================================================================
-- V70 SCM Finance R1 F1-5: 四类只读权限与财务导出权限（data-only）
--
-- 只发布本阶段真实端点使用的 action 权限：
--   1511 receivable:query  1512 payable:query  1513 receipt:query
--   1514 payment:query    1531 finance:export
-- 核销查询 1515 已随 F1-4 的 V69 发布。无页面菜单、无数据范围扩展。
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
    (1511, '应收查询', 3, 1500, 1511, NULL, NULL, 1,
     'scm:finance:receivable:query', 'scm:finance:receivable:query', NULL, 1500, true, 1),
    (1512, '应付查询', 3, 1500, 1512, NULL, NULL, 1,
     'scm:finance:payable:query', 'scm:finance:payable:query', NULL, 1500, true, 1),
    (1513, '收款查询', 3, 1500, 1513, NULL, NULL, 1,
     'scm:finance:receipt:query', 'scm:finance:receipt:query', NULL, 1500, true, 1),
    (1514, '付款查询', 3, 1500, 1514, NULL, NULL, 1,
     'scm:finance:payment:query', 'scm:finance:payment:query', NULL, 1500, true, 1),
    (1531, '财务导出', 3, 1500, 1531, NULL, NULL, 1,
     'scm:finance:export', 'scm:finance:export', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1511, 1512, 1513, 1514, 1531)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
         CROSS JOIN LATERAL unnest(ARRAY[1511::bigint, 1512::bigint, 1513::bigint, 1514::bigint, 1531::bigint])
             AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
