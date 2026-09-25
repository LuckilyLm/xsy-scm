-- ============================================================================
-- P1-C 分拣管理菜单与权限（data-only）
--
-- 裁决依据：docs/decisions.md「P1 分拣管理裁决（2026-09-24）」第 7、8、12、13 条与补充第 15、22 条。
--   * 权限点按动作分粒度：建单 / 指派 / 录入 / 完成 / 取消 / 重开 / 打印各自一条，
--     这样「谁能排队列」与「谁能干活」可以在授权矩阵里分开表达，不需要在代码里按角色名判断。
--   * 不设 scm:sorting:scope:all:query：跨指派人可见性由 scm:sorting:task:assign 隐含（补充第 15 条）。
--   * 候选订单行入口挂在建单权上，不另设只读权限（补充第 22 条）。
--
-- 菜单号 1400 段：V55 之前实际最大 menu_id 是 1342，V55–V58 用到 1349，本段整段空闲。
-- 与既有约定一致：menu_id == sort，按钮的 context_menu_id == 父菜单 id，api_perms == web_perms。
-- ============================================================================
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms,
                   web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1400, '分拣管理', 1, 0, 860, '/sorting', NULL, NULL, NULL, NULL, 'SlidersOutlined', NULL, true, 1),
       (1401, '分拣任务', 2, 1400, 1, '/sorting/tasks', '/business/scm/sorting/sorting-task-list.vue', NULL, NULL,
        NULL, 'ProfileOutlined', NULL, true, 1),
       (1402, '商品分拣汇总', 2, 1400, 2, '/sorting/summary', '/business/scm/sorting/sorting-summary.vue', NULL, NULL,
        NULL, 'BarChartOutlined', NULL, true, 1);
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, perms_type, api_perms, web_perms, context_menu_id,
                   visible_flag, create_user_id)
VALUES (1411, '分拣任务查询', 3, 1401, 1411, 1, 'scm:sorting:task:query', 'scm:sorting:task:query', 1401, true, 1),
       (1412, '分拣建单', 3, 1401, 1412, 1, 'scm:sorting:task:add', 'scm:sorting:task:add', 1401, true, 1),
       (1413, '分拣指派', 3, 1401, 1413, 1, 'scm:sorting:task:assign', 'scm:sorting:task:assign', 1401, true, 1),
       (1414, '录入分拣结果', 3, 1401, 1414, 1, 'scm:sorting:item:update', 'scm:sorting:item:update', 1401, true, 1),
       (1415, '完成分拣任务', 3, 1401, 1415, 1, 'scm:sorting:task:complete', 'scm:sorting:task:complete', 1401,
        true, 1),
       (1416, '取消分拣任务', 3, 1401, 1416, 1, 'scm:sorting:task:cancel', 'scm:sorting:task:cancel', 1401, true, 1),
       (1417, '重开分拣任务', 3, 1401, 1417, 1, 'scm:sorting:task:reopen', 'scm:sorting:task:reopen', 1401, true, 1),
       (1418, '分拣标签与小票打印', 3, 1401, 1418, 1, 'scm:sorting:task:print', 'scm:sorting:task:print', 1401, true,
        1),
       (1421, '商品分拣汇总查询', 3, 1402, 1421, 1, 'scm:sorting:summary:query', 'scm:sorting:summary:query', 1402,
        true, 1);
-- 超管兜底：正式岗位的授权在 V62 里按 role_code 种。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, menu_id
FROM t_menu
WHERE menu_id BETWEEN 1400 AND 1499
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t_menu.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
