-- ADM-12 营销子阶段：券启停权限。
--
-- 券新建后是 DRAFT，只有生效中的券才允许发出。启停原本复用「券维护」，会让「能改券内容的人」
-- 顺手把券上线（活动侧已用独立权限，券侧保持一致）；本 migration 只补一个权限入口，
-- 不改 V90 已建的表、状态约束或已有授权。
--
-- menu_id 已核对：1700-1708 属营销中心（V90），1709 空闲。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1709, '券启停', 3, 1705, 1709, NULL, NULL, 1,
        'scm:promotion:coupon:status', 'scm:promotion:coupon:status', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, menu_id
FROM t_menu
WHERE menu_id = 1709
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t_menu.menu_id);

-- 与 V90 一致：销售主管维护券（含启停），销售只读券。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, 1709
FROM t_role r
WHERE r.role_code = 'SCM_SALES_LEAD'
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 1709);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
