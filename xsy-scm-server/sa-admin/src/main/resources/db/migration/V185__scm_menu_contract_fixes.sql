-- 菜单契约补齐：客户供应商分类入口的图标，以及电子秤设备管理权限的发布。
--
-- 两处都是既有菜单/权限门禁发现的缺口，不改变任何业务口径：
--   1. 47604「客户供应商分类」由 V181 创建时漏了 icon，侧边栏与其他二级菜单不齐；
--   2. ScaleDeviceAdminController 的登记/撤销与会话签发端点用了
--      scm:device:scale:bind / scm:device:scale:session:issue，但没有任何 migration
--      把这两个权限发布到 t_menu.api_perms，超管在真实环境里也无从授权。

UPDATE t_menu
SET icon = 'AppstoreOutlined', update_time = now()
WHERE menu_id = 47604
    AND (icon IS NULL OR icon = '');

-- 电子秤设备管理是后台员工操作（会记操作日志），挂在「分拣任务」下，
-- 与秤读数查询/上报/接受三枚按钮权限同级；设备端接口只认设备令牌，不走这里。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES
    (47609, '电子秤设备绑定', 3, 1401, 47609, NULL, NULL, 1,
     'scm:device:scale:bind', 'scm:device:scale:bind', 1401, TRUE, 1),
    (47610, '电子秤操作会话', 3, 1401, 47610, NULL, NULL, 1,
     'scm:device:scale:session:issue', 'scm:device:scale:session:issue', 1401, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (47609, 47610)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

-- 显式 menu_id 不推进 identity 序列；校正到当前最大值之后。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
