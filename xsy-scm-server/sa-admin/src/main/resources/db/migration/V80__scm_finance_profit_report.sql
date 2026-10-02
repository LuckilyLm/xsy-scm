INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1221, '销售毛利分析', 2, 1200, 1221, '/report/report-finance-profit',
        '/business/scm/report/report-finance-profit.vue', NULL, NULL, NULL, 'LineChartOutlined', NULL, TRUE, 1),
       (1222, '销售毛利查询', 3, 1221, 1222, NULL, NULL, 1,
        'scm:report:finance:profit:query', 'scm:report:finance:profit:query', NULL, 1221, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
CROSS JOIN LATERAL unnest(ARRAY[1221::bigint, 1222::bigint, 1216::bigint]) AS m(menu_id)
WHERE r.role_code IN ('SCM_FINANCE', 'SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), FALSE);
