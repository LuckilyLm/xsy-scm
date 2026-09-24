-- ============================================================================
-- P0-F 数据范围权限点（data-only）
--
-- 三类点，都是「能看哪些行」而不是「能做什么」的补充：
--   1. 分配权（scm:customer:assign / scm:purchase:assign）：
--      新建时负责人由服务端强制为当前员工，只有持分配权的人能指定别人或改派；
--      同时隐含可以看到未分配（owner IS NULL）的数据。
--   2. 全量范围（*:scope:all:query）：放宽该维度的默认「仅本人」。
--      刻意用 :query 结尾——它是只读可见性权限，与 V50 的 scm:report:cost:query 同一取向。
--      没有任何角色因为「是财务」而自动获得全量，全量一律是显式授权。
--   3. 仓库授权维护（scm:warehouse:scope:*）：维护 employee_warehouse_scope 授权行。
--
-- 编号沿用新块 1300-1349，遵守既有约定：menu_id == sort、context_menu_id == parent_id、
-- api_perms == web_perms、perms_type = 1、仅授 SUPER_ADMIN(role_id=1)。
-- 正式业务角色的授权在 V56 单独种，权限点与角色不混在一条迁移里。
-- ============================================================================

INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1301, '客户归属分配', 3, 432, 1301, NULL, NULL, 1,
        'scm:customer:assign', 'scm:customer:assign', NULL, 432, true, 1),
       (1302, '全部客户数据查询', 3, 432, 1302, NULL, NULL, 1,
        'scm:customer:scope:all:query', 'scm:customer:scope:all:query', NULL, 432, true, 1),
       (1311, '全部订单数据查询', 3, 602, 1311, NULL, NULL, 1,
        'scm:order:scope:all:query', 'scm:order:scope:all:query', NULL, 602, true, 1),
       (1321, '采购归属分配', 3, 703, 1321, NULL, NULL, 1,
        'scm:purchase:assign', 'scm:purchase:assign', NULL, 703, true, 1),
       (1322, '全部采购数据查询', 3, 703, 1322, NULL, NULL, 1,
        'scm:purchase:scope:all:query', 'scm:purchase:scope:all:query', NULL, 703, true, 1),
       (1331, '全部仓库数据查询', 3, 801, 1331, NULL, NULL, 1,
        'scm:inventory:scope:all:query', 'scm:inventory:scope:all:query', NULL, 801, true, 1),
       -- 授权行维护挂在「仓库管理」页下：仓库主档与仓库可见范围是同一个岗位的两件事
       (1332, '仓库授权查询', 3, 706, 1332, NULL, NULL, 1,
        'scm:warehouse:scope:query', 'scm:warehouse:scope:query', NULL, 706, true, 1),
       (1333, '仓库授权维护', 3, 706, 1333, NULL, NULL, 1,
        'scm:warehouse:scope:update', 'scm:warehouse:scope:update', NULL, 706, true, 1),
       (1341, '全部配送数据查询', 3, 1001, 1341, NULL, NULL, 1,
        'scm:delivery:scope:all:query', 'scm:delivery:scope:all:query', NULL, 1001, true, 1),
       -- 司机默认隐藏订单金额；货到付款将来确实需要金额时开放本点，不再改代码
       (1342, '配送订单金额查询', 3, 1001, 1342, NULL, NULL, 1,
        'scm:delivery:amount:query', 'scm:delivery:amount:query', NULL, 1001, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id BETWEEN 1301 AND 1342
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'),
              (SELECT MAX(menu_id) + 1 FROM t_menu), false);
