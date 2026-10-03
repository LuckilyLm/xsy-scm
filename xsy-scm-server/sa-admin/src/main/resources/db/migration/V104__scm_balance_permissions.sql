-- ADM-12 3-12a：余额域 API 权限。
--
-- 与 V99（支付域）同一套纪律：**只落权限，不建指向页面的菜单**。
-- SmartAdmin 的 buildRoutes() 只判断 menuId / path / deletedFlag，不会因 visibleFlag = false
-- 跳过路由注册（visibleFlag 只转成 hideInMenu）；此刻写入指向尚不存在的页面，会留下一个
-- component = undefined 的动态路由。因此这里只建一个**隐藏的权限归属节点** + 按钮权限。
--
-- 刻意**不预占充值权限**（如 scm:balance:recharge）：3-12b 的充值由支付渠道成功驱动，
-- 是否需要人工充值入口还没定；先占一个权限点会让「有这个权限」被误读成「已支持人工充值」。
--
-- menu_id 已核对：1710–1717 属支付域（V99），1718 起空闲。
-- 权限码与 ScmBalancePermission 逐字一致。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1718, '客户余额', 1, 0, 61, NULL, NULL, NULL, NULL, NULL, NULL, NULL, FALSE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
    (1719, '余额查询', 3, 1718, 1719, NULL, NULL, 1, 'scm:balance:query', 'scm:balance:query', NULL, NULL, TRUE, 1),
    (1720, '余额流水查询', 3, 1718, 1720, NULL, NULL, 1, 'scm:balance:movement:query', 'scm:balance:movement:query', NULL, NULL, TRUE, 1),
    (1721, '余额人工更正', 3, 1718, 1721, NULL, NULL, 1, 'scm:balance:correction', 'scm:balance:correction', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, menu_id
FROM t_menu
WHERE menu_id BETWEEN 1718 AND 1721
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t_menu.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
