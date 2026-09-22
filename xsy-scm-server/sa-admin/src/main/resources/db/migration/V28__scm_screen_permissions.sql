-- B7 数据大屏权限，2026-09-19。
--
-- 背景：数据大屏是只读聚合视图，不接业务写操作。前端 /screen 为静态路由，
-- 不占用菜单；但后端聚合接口必须有权限控制，因此新增一个隐藏目录 + 查询权限点，
-- 供角色管理界面分配。
--
-- Data-only：2 条 t_menu（900 目录 + 901 查询权限）+ role_id = 1 授权 + 序列推进。
-- 900 段已核对空闲（现有最大 menu_id 为 824）。

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (900,'数据大屏',1,0,900,'/screen',NULL,NULL,NULL,NULL,'DashboardOutlined',NULL,false,1) ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (901,'查询',3,900,901,NULL,NULL,1,'scm:screen:query','scm:screen:query',NULL,NULL,true,1) ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (900, 901)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), false);
