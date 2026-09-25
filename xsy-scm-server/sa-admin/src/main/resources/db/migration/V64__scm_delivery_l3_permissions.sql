-- ============================================================================
-- P2-C 配送 L3 履约权限（data-only）
--
-- 裁决依据：docs/decisions.md「P2 物流配送 L3 裁决（2026-09-25）」第 16、17 条。
--   * 三个动作各一条权限点：发车 / 签收 / 完成线路。发车会改库存，因此它与「确认规划」
--     分权，不复用 scm:delivery:route:plan —— 能排线的人不必然是能从仓库发货的人。
--   * 签收授司机，但**不放宽读侧**：司机仍受既有 driverScope「只看绑定到本人的司机档案所属
--     线路」约束，因此他能签的只有自己在的那条线。
--   * 发车走库存域既有的仓库范围守卫（P0 第二批第 8 条），所以调度岗需要与之匹配的
--     employee_warehouse_scope 授权行；那是配置要求，不是放宽守卫的理由。
--
-- 菜单号：配送段 1000–1032 已用，1017–1019 空闲（1033+ 属车辆段之后的空档，但按钮按父菜单
-- 分段更贴既有约定：101x = 线路动作）。与既有约定一致：menu_id == sort，
-- 按钮 context_menu_id == 父菜单 id，api_perms == web_perms，perms_type = 1。
-- ============================================================================
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, perms_type, api_perms, web_perms, context_menu_id,
                   visible_flag, create_user_id)
VALUES (1017, '线路发车', 3, 1001, 1017, 1, 'scm:delivery:route:dispatch', 'scm:delivery:route:dispatch', 1001, true,
        1),
       (1018, '订单签收', 3, 1001, 1018, 1, 'scm:delivery:order:sign', 'scm:delivery:order:sign', 1001, true, 1),
       (1019, '完成线路', 3, 1001, 1019, 1, 'scm:delivery:route:complete', 'scm:delivery:route:complete', 1001, true,
        1);

-- 超管兜底。正式岗位的授权按 role_code 种，不硬编码 role_id（V56 起同一口径）。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, menu_id
FROM t_menu
WHERE menu_id IN (1017, 1018, 1019)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t_menu.menu_id);

-- 授权矩阵：发车 = 调度 + 仓库主管；签收 = 调度 + 司机；完成线路 = 调度。
WITH grant_matrix(menu_id, role_code) AS (
    VALUES (1017::bigint, 'SCM_DISPATCHER'), (1018::bigint, 'SCM_DISPATCHER'), (1019::bigint, 'SCM_DISPATCHER'),
           (1017::bigint, 'SCM_STOREKEEPER_LEAD'), (1018::bigint, 'SCM_DRIVER')
)
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, g.menu_id
FROM grant_matrix g
         JOIN t_role r ON r.role_code = g.role_code
WHERE NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = g.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
