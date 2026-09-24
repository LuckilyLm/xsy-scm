-- ============================================================================
-- Finance R0 报表中心：菜单与权限（data-only）
--
-- 报表中心是只读分析层，不引入任何财务事实表；本迁移只种菜单与权限点。
-- 编号沿用 V28（数据大屏）与 V46（业务待办）之后的新块 1200-1216，
-- 且遵守既有约定：menu_id == sort、context_menu_id == 自身 parent_id、
-- api_perms 与 web_perms 同值、perms_type = 1、仅授 SUPER_ADMIN(role_id=1)。
--
-- 成本权限 scm:report:cost:query 单独存在：普通仓管可以看数量流水，
-- 但不必然可以看采购成本与库存账面金额。导出必须同时具备对应查询权限与本权限。
-- ============================================================================

-- 一级目录
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1200, '报表中心', 1, 0, 1200, '/report', NULL, NULL, NULL, NULL, 'FundOutlined', NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 五个只读分析页
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1201, '经营概览', 2, 1200, 1201, '/report/report-overview-list',
        '/business/scm/report/report-overview-list.vue', NULL, NULL, NULL, 'ProfileOutlined', NULL, true, 1),
       (1202, '销售分析', 2, 1200, 1202, '/report/report-sales-list',
        '/business/scm/report/report-sales-list.vue', NULL, NULL, NULL, 'PieChartOutlined', NULL, true, 1),
       (1203, '采购分析', 2, 1200, 1203, '/report/report-purchase-list',
        '/business/scm/report/report-purchase-list.vue', NULL, NULL, NULL, 'ShoppingCartOutlined', NULL, true, 1),
       (1204, '收货与入库', 2, 1200, 1204, '/report/report-receipt-list',
        '/business/scm/report/report-receipt-list.vue', NULL, NULL, NULL, 'InboxOutlined', NULL, true, 1),
       (1205, '库存分析', 2, 1200, 1205, '/report/report-inventory-list',
        '/business/scm/report/report-inventory-list.vue', NULL, NULL, NULL, 'BarChartOutlined', NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 权限点
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1211, '经营概览查询', 3, 1201, 1211, NULL, NULL, 1,
        'scm:report:overview:query', 'scm:report:overview:query', NULL, 1201, true, 1),
       (1212, '销售分析查询', 3, 1202, 1212, NULL, NULL, 1,
        'scm:report:sales:query', 'scm:report:sales:query', NULL, 1202, true, 1),
       (1213, '采购分析查询', 3, 1203, 1213, NULL, NULL, 1,
        'scm:report:purchase:query', 'scm:report:purchase:query', NULL, 1203, true, 1),
       (1214, '库存分析查询', 3, 1205, 1214, NULL, NULL, 1,
        'scm:report:inventory:query', 'scm:report:inventory:query', NULL, 1205, true, 1),
       -- 成本字段是数据敏感权限，单独一个点，不隐含在任何查询权限里
       (1215, '成本数据查询', 3, 1205, 1215, NULL, NULL, 1,
        'scm:report:cost:query', 'scm:report:cost:query', NULL, 1205, true, 1),
       -- 收货与入库页复用采购/库存查询权限，不重复建点（与 V28 复用权限码同一取向）
       (1216, '报表导出', 3, 1200, 1216, NULL, NULL, 1,
        'scm:report:export', 'scm:report:export', NULL, 1200, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1200, 1201, 1202, 1203, 1204, 1205, 1211, 1212, 1213, 1214, 1215, 1216)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'),
              (SELECT MAX(menu_id) + 1 FROM t_menu), false);
