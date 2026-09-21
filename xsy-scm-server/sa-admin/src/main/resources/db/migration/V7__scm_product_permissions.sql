-- W1 menu and permissions; SmartAdmin menu-driven hidden detail route.
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (401, '商品管理', 1, 0, 401, '/product', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (402, '商品档案', 2, 401, 402, '/product/product-list', '/business/scm/product/product-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (403, '商品分类', 2, 401, 403, '/product/category-list', '/business/scm/product/category-list.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (404, '商品详情', 2, 402, 404, '/product/product-detail', '/business/scm/product/product-detail.vue', NULL, NULL,
        NULL, NULL, false, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (411, '查询', 3, 402, 411, NULL, NULL, 1, 'scm:product:query', 'scm:product:query', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (412, '新建', 3, 402, 412, NULL, NULL, 1, 'scm:product:add', 'scm:product:add', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (413, '编辑', 3, 402, 413, NULL, NULL, 1, 'scm:product:update', 'scm:product:update', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (414, '上下架', 3, 402, 414, NULL, NULL, 1, 'scm:product:status', 'scm:product:status', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (415, '删除', 3, 402, 415, NULL, NULL, 1, 'scm:product:delete', 'scm:product:delete', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (416, '图片管理', 3, 402, 416, NULL, NULL, 1, 'scm:product:image', 'scm:product:image', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (421, '查询', 3, 403, 421, NULL, NULL, 1, 'scm:product:category:query', 'scm:product:category:query', 403, true,
        1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (422, '新建', 3, 403, 422, NULL, NULL, 1, 'scm:product:category:add', 'scm:product:category:add', 403, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (423, '编辑', 3, 403, 423, NULL, NULL, 1, 'scm:product:category:update', 'scm:product:category:update', 403,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (424, '删除', 3, 403, 424, NULL, NULL, 1, 'scm:product:category:delete', 'scm:product:category:delete', 403,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (401, 402, 403, 404, 411, 412, 413, 414, 415, 416, 421, 422, 423, 424)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
