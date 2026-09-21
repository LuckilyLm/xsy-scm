-- W2 menu and permissions. menu_id 431-482; 425-500 verified free (base seed <= 300, W1 = 401-424).
-- component paths must match src/views/** exactly (router resolves ../views${component}).
-- Hidden detail routes (434 / 463) stay deep-linkable but are not rendered in the sidebar.
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (431, '客户管理', 1, 0, 431, '/customer', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (432, '客户档案', 2, 431, 432, '/customer/customer-list', '/business/scm/customer/customer-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (433, '客户类型', 2, 431, 433, '/customer/customer-type-list', '/business/scm/customer/customer-type-list.vue',
        NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (434, '客户详情', 2, 432, 434, '/customer/customer-detail', '/business/scm/customer/customer-detail.vue', NULL,
        NULL, NULL, NULL, false, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (441, '查询', 3, 432, 441, NULL, NULL, 1, 'scm:customer:query', 'scm:customer:query', 432, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (442, '新建', 3, 432, 442, NULL, NULL, 1, 'scm:customer:add', 'scm:customer:add', 432, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (443, '编辑', 3, 432, 443, NULL, NULL, 1, 'scm:customer:update', 'scm:customer:update', 432, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (444, '状态', 3, 432, 444, NULL, NULL, 1, 'scm:customer:status', 'scm:customer:status', 432, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (445, '删除', 3, 432, 445, NULL, NULL, 1, 'scm:customer:delete', 'scm:customer:delete', 432, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (451, '查询', 3, 433, 451, NULL, NULL, 1, 'scm:customer:type:query', 'scm:customer:type:query', 433, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (452, '新建', 3, 433, 452, NULL, NULL, 1, 'scm:customer:type:add', 'scm:customer:type:add', 433, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (453, '编辑', 3, 433, 453, NULL, NULL, 1, 'scm:customer:type:update', 'scm:customer:type:update', 433, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (454, '删除', 3, 433, 454, NULL, NULL, 1, 'scm:customer:type:delete', 'scm:customer:type:delete', 433, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (461, '供应商管理', 1, 0, 461, '/supplier', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (462, '供应商档案', 2, 461, 462, '/supplier/supplier-list', '/business/scm/supplier/supplier-list.vue', NULL,
        NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (463, '供应商详情', 2, 462, 463, '/supplier/supplier-detail', '/business/scm/supplier/supplier-detail.vue', NULL,
        NULL, NULL, NULL, false, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (464, '商品-供应商关系', 2, 461, 464, '/supplier/supplier-sku-list',
        '/business/scm/supplier/supplier-sku-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (471, '查询', 3, 462, 471, NULL, NULL, 1, 'scm:supplier:query', 'scm:supplier:query', 462, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (472, '新建', 3, 462, 472, NULL, NULL, 1, 'scm:supplier:add', 'scm:supplier:add', 462, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (473, '编辑', 3, 462, 473, NULL, NULL, 1, 'scm:supplier:update', 'scm:supplier:update', 462, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (474, '状态', 3, 462, 474, NULL, NULL, 1, 'scm:supplier:status', 'scm:supplier:status', 462, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (475, '删除', 3, 462, 475, NULL, NULL, 1, 'scm:supplier:delete', 'scm:supplier:delete', 462, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (481, '查询', 3, 464, 481, NULL, NULL, 1, 'scm:supplier:sku:query', 'scm:supplier:sku:query', 464, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (482, '维护', 3, 464, 482, NULL, NULL, 1, 'scm:supplier:sku:update', 'scm:supplier:sku:update', 464, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN
      (431, 432, 433, 434, 441, 442, 443, 444, 445, 451, 452, 453, 454, 461, 462, 463, 464, 471, 472, 473, 474, 475,
       481, 482)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
