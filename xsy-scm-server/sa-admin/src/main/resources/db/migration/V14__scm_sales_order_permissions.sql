-- W4 approved menu/permissions; immutable after first application.
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (601, '销售订单', 1, 0, 601, '/order', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (602, '订单列表', 2, 601, 602, '/order/order-list', '/business/scm/order/order-list.vue', NULL, NULL, NULL, NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (603, '退货单', 2, 601, 603, '/order/order-return-list', '/business/scm/order/order-return-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (604, '退款单', 2, 601, 604, '/order/order-refund-list', '/business/scm/order/order-refund-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (605, '订单操作日志', 2, 601, 605, '/order/order-log-list', '/business/scm/order/order-log-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (611, '查询', 3, 602, 611, NULL, NULL, 1, 'scm:order:query', 'scm:order:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (612, '新建', 3, 602, 612, NULL, NULL, 1, 'scm:order:add', 'scm:order:add', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (613, '编辑', 3, 602, 613, NULL, NULL, 1, 'scm:order:update', 'scm:order:update', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (614, '提交', 3, 602, 614, NULL, NULL, 1, 'scm:order:submit', 'scm:order:submit', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (615, '确认', 3, 602, 615, NULL, NULL, 1, 'scm:order:confirm', 'scm:order:confirm', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (616, '取消', 3, 602, 616, NULL, NULL, 1, 'scm:order:cancel', 'scm:order:cancel', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (617, '实重录入', 3, 602, 617, NULL, NULL, 1, 'scm:order:actual-quantity', 'scm:order:actual-quantity', NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (618, '改价', 3, 602, 618, NULL, NULL, 1, 'scm:order:price-override', 'scm:order:price-override', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (619, '删除', 3, 602, 619, NULL, NULL, 1, 'scm:order:delete', 'scm:order:delete', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (621, '查询', 3, 603, 621, NULL, NULL, 1, 'scm:order:return:query', 'scm:order:return:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (622, '新建', 3, 603, 622, NULL, NULL, 1, 'scm:order:return:add', 'scm:order:return:add', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (623, '批准', 3, 603, 623, NULL, NULL, 1, 'scm:order:return:approve', 'scm:order:return:approve', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (624, '驳回', 3, 603, 624, NULL, NULL, 1, 'scm:order:return:reject', 'scm:order:return:reject', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (625, '取消', 3, 603, 625, NULL, NULL, 1, 'scm:order:return:cancel', 'scm:order:return:cancel', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (631, '查询', 3, 604, 631, NULL, NULL, 1, 'scm:order:refund:query', 'scm:order:refund:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (632, '完成', 3, 604, 632, NULL, NULL, 1, 'scm:order:refund:complete', 'scm:order:refund:complete', NULL, true,
        1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (641, '查询', 3, 605, 641, NULL, NULL, 1, 'scm:order:log:query', 'scm:order:log:query', NULL, true, 1)
ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN
      (601, 602, 603, 604, 605, 611, 612, 613, 614, 615, 616, 617, 618, 619, 621, 622, 623, 624, 625, 631, 632, 641)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
