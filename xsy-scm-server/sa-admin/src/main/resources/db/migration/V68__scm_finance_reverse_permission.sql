-- ============================================================================
-- V68 SCM Finance R1 F1-3C: 收款与付款反向权限（data-only）
--
-- 两条受保护命令在本阶段一同交付：
--   POST /scm/finance/receipt/reverse  -> scm:finance:receipt:reverse
--   POST /scm/finance/payment/reverse  -> scm:finance:payment:reverse
-- 两个动作都能改变财务有效额，权限保持独立，不能由 add 权限隐含获得。
--
-- 菜单号沿用 Finance R1 规划号段；V67 后财务段已占用 1500 / 1521 / 1522，
-- 1526 / 1527 尚未占用。仅授 SUPER_ADMIN 与正式财务岗位 SCM_FINANCE。
-- 不新增页面菜单、查询权限、核销权限或范围放宽点。
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1526, '反向收款', 3, 1500, 1526, NULL, NULL, 1,
        'scm:finance:receipt:reverse', 'scm:finance:receipt:reverse', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1527, '反向付款', 3, 1500, 1527, NULL, NULL, 1,
        'scm:finance:payment:reverse', 'scm:finance:payment:reverse', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1526, 1527)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
         CROSS JOIN LATERAL unnest(ARRAY[1526::bigint, 1527::bigint]) AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
