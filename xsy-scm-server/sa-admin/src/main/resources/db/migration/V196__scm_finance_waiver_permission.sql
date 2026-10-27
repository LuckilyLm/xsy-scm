-- 抹零与反向抹零权限。
--
-- 两个动作分开授权：抹零是日常对账动作，反向抹零会把已经放掉的余额重新变回欠款，
-- 影响客户对账单，因此不能由「会抹零」隐含「会撤销」。
-- 自动 FIFO 核销不新增权限：它沿用 scm:finance:write-off:add，只是把目标选择交给系统按顺序排。
--
-- 菜单号 1528 / 1529 未被占用（V67–V71 用了 1500–1531 中的 1501–1505、1511–1515、1521–1527、1531）。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1528, '抹零', 3, 1500, 1528, NULL, NULL, 1,
        'scm:finance:waiver:add', 'scm:finance:waiver:add', NULL, 1500, TRUE, 1),
       (1529, '反向抹零', 3, 1500, 1529, NULL, NULL, 1,
        'scm:finance:waiver:reverse', 'scm:finance:waiver:reverse', NULL, 1500, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1528, 1529)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
    CROSS JOIN LATERAL unnest(ARRAY[1528::bigint, 1529::bigint]) AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
