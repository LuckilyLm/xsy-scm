-- ADM-12 3-10a：支付域 API 权限。
--
-- 本 migration **只落权限**，不建指向页面的菜单。
--
-- 原因（负责人 2026-10-03 指出，已核对前端实现）：SmartAdmin 的 buildRoutes() 构建动态路由时
-- 只判断 menuId / path / deletedFlag，**不会因为 visibleFlag = false 跳过注册** ——
-- visibleFlag 只是转成 hideInMenu。因此若此刻写入指向尚不存在的 payment/*.vue 的菜单，
-- 得到的是一个 component = undefined 的动态路由：菜单看不见，但数据库里躺着一个坏路由。
--
-- 所以：
--   * 这里只建一个**隐藏的权限归属节点**（支付管理），不配 path / component，不进 buildRoutes()；
--   * 七个权限以**按钮权限**（menu_type = 3、path = NULL、component = NULL）落库，本来也不进路由。
--   * 等 3-13 页面真正存在，再用后续 migration 新增 支付交易 / 支付退款 / 回调记录 / 支付对账
--     四个页面菜单并把可见性打开。
--
-- menu_id 已核对：1700–1709 属营销中心（V90/V92），1710 起空闲。
-- 权限码与 ScmPaymentPermission 逐字一致。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1710, '支付管理', 1, 0, 60, NULL, NULL, NULL, NULL, NULL, NULL, NULL, FALSE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
    (1711, '发起支付', 3, 1710, 1711, NULL, NULL, 1, 'scm:payment:intent:create', 'scm:payment:intent:create', NULL, NULL, TRUE, 1),
    (1712, '支付交易查询', 3, 1710, 1712, NULL, NULL, 1, 'scm:payment:transaction:query', 'scm:payment:transaction:query', NULL, NULL, TRUE, 1),
    (1713, '发起退款', 3, 1710, 1713, NULL, NULL, 1, 'scm:payment:refund:create', 'scm:payment:refund:create', NULL, NULL, TRUE, 1),
    (1714, '退款查询', 3, 1710, 1714, NULL, NULL, 1, 'scm:payment:refund:query', 'scm:payment:refund:query', NULL, NULL, TRUE, 1),
    (1715, '回调记录查询', 3, 1710, 1715, NULL, NULL, 1, 'scm:payment:callback:query', 'scm:payment:callback:query', NULL, NULL, TRUE, 1),
    (1716, '执行对账', 3, 1710, 1716, NULL, NULL, 1, 'scm:payment:reconciliation:run', 'scm:payment:reconciliation:run', NULL, NULL, TRUE, 1),
    (1717, '对账结果查询', 3, 1710, 1717, NULL, NULL, 1, 'scm:payment:reconciliation:query', 'scm:payment:reconciliation:query', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 授权：财务/出纳岗位全量；超管角色 1 兜底。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, menu_id
FROM t_menu
WHERE menu_id BETWEEN 1710 AND 1717
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t_menu.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
