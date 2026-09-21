-- W5 approved menu/permissions; immutable after first application.
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (701, '采购管理', 1, 0, 701, '/purchase', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (702, '采购需求', 2, 701, 702, '/purchase/purchase-demand-list',
        '/business/scm/purchase/purchase-demand-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (703, '采购订单', 2, 701, 703, '/purchase/purchase-order-list', '/business/scm/purchase/purchase-order-list.vue',
        NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (704, '采购收货', 2, 701, 704, '/purchase/purchase-receipt-list',
        '/business/scm/purchase/purchase-receipt-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (705, '采购日志', 2, 701, 705, '/purchase/purchase-log-list', '/business/scm/purchase/purchase-log-list.vue',
        NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (706, '仓库管理', 2, 701, 706, '/purchase/warehouse-list', '/business/scm/purchase/warehouse-list.vue', NULL,
        NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (711, '查询', 3, 702, 711, NULL, NULL, 1, 'scm:purchase:demand:query', 'scm:purchase:demand:query', NULL, true,
        1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (712, '生成', 3, 702, 712, NULL, NULL, 1, 'scm:purchase:demand:generate', 'scm:purchase:demand:generate', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (713, '分配', 3, 702, 713, NULL, NULL, 1, 'scm:purchase:demand:allocate', 'scm:purchase:demand:allocate', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (721, '查询', 3, 703, 721, NULL, NULL, 1, 'scm:purchase:query', 'scm:purchase:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (722, '新建', 3, 703, 722, NULL, NULL, 1, 'scm:purchase:add', 'scm:purchase:add', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (723, '编辑', 3, 703, 723, NULL, NULL, 1, 'scm:purchase:update', 'scm:purchase:update', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (724, '提交', 3, 703, 724, NULL, NULL, 1, 'scm:purchase:submit', 'scm:purchase:submit', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (725, '取消', 3, 703, 725, NULL, NULL, 1, 'scm:purchase:cancel', 'scm:purchase:cancel', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (726, '少收关单', 3, 703, 726, NULL, NULL, 1, 'scm:purchase:short-close', 'scm:purchase:short-close', NULL, true,
        1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (727, '删除', 3, 703, 727, NULL, NULL, 1, 'scm:purchase:delete', 'scm:purchase:delete', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (731, '查询', 3, 704, 731, NULL, NULL, 1, 'scm:purchase:receipt:query', 'scm:purchase:receipt:query', NULL, true,
        1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (732, '新建', 3, 704, 732, NULL, NULL, 1, 'scm:purchase:receipt:add', 'scm:purchase:receipt:add', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (733, '编辑', 3, 704, 733, NULL, NULL, 1, 'scm:purchase:receipt:update', 'scm:purchase:receipt:update', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (734, '确认', 3, 704, 734, NULL, NULL, 1, 'scm:purchase:receipt:confirm', 'scm:purchase:receipt:confirm', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (735, '删除', 3, 704, 735, NULL, NULL, 1, 'scm:purchase:receipt:delete', 'scm:purchase:receipt:delete', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (741, '查询', 3, 705, 741, NULL, NULL, 1, 'scm:purchase:log:query', 'scm:purchase:log:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (751, '查询', 3, 706, 751, NULL, NULL, 1, 'scm:warehouse:query', 'scm:warehouse:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (752, '新建', 3, 706, 752, NULL, NULL, 1, 'scm:warehouse:add', 'scm:warehouse:add', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (753, '编辑', 3, 706, 753, NULL, NULL, 1, 'scm:warehouse:update', 'scm:warehouse:update', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN
      (701, 702, 703, 704, 705, 706, 711, 712, 713, 721, 722, 723, 724, 725, 726, 727, 731, 732, 733, 734, 735, 741,
       751, 752, 753)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
