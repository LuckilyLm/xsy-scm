INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1219, '应收应付账龄', 2, 1200, 1219, '/report/report-finance-aging',
        '/business/scm/report/report-finance-aging.vue', NULL, NULL, NULL, 'AccountBookOutlined', NULL, TRUE, 1),
       (1220, '账龄查询', 3, 1219, 1220, NULL, NULL, 1,
        'scm:report:finance:aging:query', 'scm:report:finance:aging:query', NULL, 1219, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r
CROSS JOIN LATERAL unnest(ARRAY[1219::bigint,1220::bigint]) AS m(menu_id)
WHERE r.role_code IN ('SCM_FINANCE','SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), FALSE);
