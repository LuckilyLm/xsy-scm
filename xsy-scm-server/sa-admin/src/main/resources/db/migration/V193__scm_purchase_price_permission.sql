-- 采购补价 / 改价 / 锁单解锁权限。
--
-- 拆成四枚按钮而不是一枚「价格管理」：
--   * 补价是日常动作（采购员），改价会改变应付与成本（财务/负责人）；
--   * 锁单是对账完成的确认，解锁是把已确认的单重新打开，风险不对称，不能合并授权；
--   * 自动回填不需要权限点：它由系统任务执行，日志记系统操作人。
--
-- 菜单号 47611–47614 未被使用（47605–47610 由销售锁单与设备权限占用）。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, perms_type, api_perms, web_perms, context_menu_id,
                   visible_flag, create_user_id)
VALUES
    (47611, '采购补价', 3, 703, 47611, 1, 'scm:purchase:price:fill', 'scm:purchase:price:fill', 703, TRUE, 1),
    (47612, '采购改价', 3, 703, 47612, 1, 'scm:purchase:price:adjust', 'scm:purchase:price:adjust', 703, TRUE, 1),
    (47613, '采购锁单', 3, 703, 47613, 1, 'scm:purchase:lock', 'scm:purchase:lock', 703, TRUE, 1),
    (47614, '采购解锁', 3, 703, 47614, 1, 'scm:purchase:unlock', 'scm:purchase:unlock', 703, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 默认给采购负责人、财务与超管；其他岗位在角色管理里按需勾选。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
    JOIN t_menu m ON m.menu_id BETWEEN 47611 AND 47614
WHERE r.role_code IN ('SUPER_ADMIN', 'SCM_PURCHASER', 'SCM_PURCHASER_LEAD', 'SCM_FINANCE')
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 显式 menu_id 不推进 identity 序列；校正到当前最大值之后。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
