-- 过账特殊单据的页面与权限（D-1028-16）。
--
-- 「隐藏」靠授权范围实现，不靠 visible_flag = FALSE：菜单只有授了权的岗位才看得见；
-- 把菜单对已授权的人也藏起来，现场财务就只能靠记 URL 进系统，等于没有入口。
-- 授权范围：超级管理员兜底 + SCM_FINANCE，与其它财务页一致，不新增角色。
--
-- 导出沿用 scm:finance:export，不另开权限串：导出只是同一份数据的另一种载体。
-- 菜单号 1506、1532–1535 未被占用（1500–1505、1511–1515、1521–1529、1531 已用）。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1506, '过账单据', 2, 1500, 1506, '/finance/postings',
        '/business/scm/finance/finance-posting-list.vue', NULL, NULL, NULL, 'AuditOutlined', 1500, TRUE, 1),
       (1532, '过账查询', 3, 1500, 1532, NULL, NULL, 1,
        'scm:finance:posting:query', 'scm:finance:posting:query', NULL, 1500, TRUE, 1),
       (1533, '登记过账', 3, 1500, 1533, NULL, NULL, 1,
        'scm:finance:posting:add', 'scm:finance:posting:add', NULL, 1500, TRUE, 1),
       (1534, '修改过账', 3, 1500, 1534, NULL, NULL, 1,
        'scm:finance:posting:update', 'scm:finance:posting:update', NULL, 1500, TRUE, 1),
       (1535, '作废过账', 3, 1500, 1535, NULL, NULL, 1,
        'scm:finance:posting:void', 'scm:finance:posting:void', NULL, 1500, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1506, 1532, 1533, 1534, 1535)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
    CROSS JOIN LATERAL unnest(ARRAY[1506::bigint, 1532::bigint, 1533::bigint, 1534::bigint, 1535::bigint])
        AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
