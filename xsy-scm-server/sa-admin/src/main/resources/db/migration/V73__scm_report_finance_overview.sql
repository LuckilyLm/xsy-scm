-- ============================================================================
-- Finance R1 -> R0 account overview (data-only): one read page and its query permission.
-- IDs 1217 / 1218 were re-scanned in the QA database before this migration; both were free.
-- The report reads Finance facts. It does not change the existing business-reference reports.
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1217, '往来概览', 2, 1200, 1217, '/report/report-finance-overview',
        '/business/scm/report/report-finance-overview.vue', NULL, NULL, NULL,
        'AccountBookOutlined', NULL, TRUE, 1),
       (1218, '往来概览查询', 3, 1217, 1218, NULL, NULL, 1,
        'scm:report:finance:query', 'scm:report:finance:query', NULL, 1217, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM t_role role
         CROSS JOIN LATERAL unnest(ARRAY[1217::bigint, 1218::bigint]) AS menu(menu_id)
WHERE role.role_code IN ('SCM_FINANCE', 'SUPER_ADMIN')
  AND NOT EXISTS(SELECT 1 FROM t_role_menu grant_row
                 WHERE grant_row.role_id = role.role_id AND grant_row.menu_id = menu.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), FALSE);
