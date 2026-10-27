-- 电子秤设备管理页：把已有的设备接口落成一个可授权的后台入口。
--
-- 背景：设备登记/签发会话/撤销的接口与权限在 V171、V185 已经存在，但只有按钮级 api_perms，
-- 没有菜单页，现场只能在 Swagger 里调用。本迁移只补入口与查询权限，不改变任何设备身份口径：
--   * 查询与写入分开授权：能看设备台账不等于能给秤上的员工签发会话；
--   * 页面挂在「分拣管理 / 电子秤设备」，因为秤是分拣工位的设备，不属于系统管理；
--   * 负库存损耗报表沿用 scm:report:inventory:query，不需要新的权限行。
--
-- 菜单号：1400 段由分拣域占用（V61/V89），1403 与 1425 未被使用。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms,
                   web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1403, '电子秤设备', 2, 1400, 3, '/sorting/scale-devices',
        '/business/scm/sorting/scale-device-list.vue', NULL, NULL, NULL, 'DashboardOutlined', NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, perms_type, api_perms, web_perms, context_menu_id,
                   visible_flag, create_user_id)
VALUES (1425, '电子秤设备查询', 3, 1403, 1425, 1, 'scm:device:scale:query', 'scm:device:scale:query', 1403, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- V185 把绑定/会话两枚按钮挂在「分拣任务」下；它们实际操作的是设备页上的功能，随页迁移，权限码不变。
UPDATE t_menu
SET parent_id = 1403, context_menu_id = 1403, update_time = now()
WHERE menu_id IN (47609, 47610);

-- 超管兜底；正式岗位授权仍在角色管理里按需勾选。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1403, 1425)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

-- 显式 menu_id 不推进 identity 序列；校正到当前最大值之后。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
