-- W3 approved menu and permissions. Immutable after first successful application.
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (425, '规格查询', 3, 402, 425, NULL, NULL, 1, 'scm:product:sku:query', 'scm:product:sku:query', 402, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (435, '客户 SKU 可见性', 2, 431, 435, '/customer/sku-visibility-list',
        '/business/scm/customer/customer-sku-visibility-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (486, '查询', 3, 435, 486, NULL, NULL, 1, 'scm:customer:visibility:query', 'scm:customer:visibility:query', 435,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (487, '维护', 3, 435, 487, NULL, NULL, 1, 'scm:customer:visibility:update', 'scm:customer:visibility:update',
        435, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (501, '价格中心', 1, 0, 501, '/pricing', NULL, NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (502, '客户协议价', 2, 501, 502, '/pricing/agreement-price-list',
        '/business/scm/pricing/agreement-price-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (503, '客户类型价', 2, 501, 503, '/pricing/customer-type-price-list',
        '/business/scm/pricing/customer-type-price-list.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (504, '批量调价', 2, 501, 504, '/pricing/customer-type-price-batch',
        '/business/scm/pricing/customer-type-price-batch.vue', NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (505, '价格历史', 2, 501, 505, '/pricing/price-history-list', '/business/scm/pricing/price-history-list.vue',
        NULL, NULL, NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (506, '取价试算', 2, 501, 506, '/pricing/price-preview', '/business/scm/pricing/price-preview.vue', NULL, NULL,
        NULL, NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (511, '查询', 3, 502, 511, NULL, NULL, 1, 'scm:pricing:agreement:query', 'scm:pricing:agreement:query', 502,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (512, '新建', 3, 502, 512, NULL, NULL, 1, 'scm:pricing:agreement:add', 'scm:pricing:agreement:add', 502, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (513, '编辑', 3, 502, 513, NULL, NULL, 1, 'scm:pricing:agreement:update', 'scm:pricing:agreement:update', 502,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (514, '删除', 3, 502, 514, NULL, NULL, 1, 'scm:pricing:agreement:delete', 'scm:pricing:agreement:delete', 502,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (521, '查询', 3, 503, 521, NULL, NULL, 1, 'scm:pricing:type-price:query', 'scm:pricing:type-price:query', 503,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (522, '新建', 3, 503, 522, NULL, NULL, 1, 'scm:pricing:type-price:add', 'scm:pricing:type-price:add', 503, true,
        1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (523, '编辑', 3, 503, 523, NULL, NULL, 1, 'scm:pricing:type-price:update', 'scm:pricing:type-price:update', 503,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (524, '删除', 3, 503, 524, NULL, NULL, 1, 'scm:pricing:type-price:delete', 'scm:pricing:type-price:delete', 503,
        true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (525, '批量调价', 3, 503, 525, NULL, NULL, 1, 'scm:pricing:type-price:batch', 'scm:pricing:type-price:batch',
        503, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (531, '查询', 3, 505, 531, NULL, NULL, 1, 'scm:pricing:history:query', 'scm:pricing:history:query', 505, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   context_menu_id, visible_flag, create_user_id)
VALUES (541, '试算', 3, 506, 541, NULL, NULL, 1, 'scm:pricing:resolve:query', 'scm:pricing:resolve:query', 506, true, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN
      (425, 435, 486, 487, 501, 502, 503, 504, 505, 506, 511, 512, 513, 514, 521, 522, 523, 524, 525, 531, 541)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
