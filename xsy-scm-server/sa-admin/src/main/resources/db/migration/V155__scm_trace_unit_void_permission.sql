-- 包装码作废：码可以作废，但绑定事实不撤销。
--
-- 规则：已绑定分拣行的码不能作废 —— 它已经承诺给某个客户，作废会让回放自相矛盾。
-- 作废只把码标记为 VOID 并追加一条 VOID 事件，原码保留、永不复用。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2114, '包装码作废', 3, 2110, 2114, NULL, NULL, 1,
        'scm:inventory:unit:void', 'scm:inventory:unit:void', 2110, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id = 2114
  AND (r.role_id = 1 OR r.role_code IN ('SCM_STOREKEEPER', 'SCM_STOREKEEPER_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
