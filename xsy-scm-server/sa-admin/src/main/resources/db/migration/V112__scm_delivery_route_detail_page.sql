-- 线路详情从抽屉改为独立页面：隐藏菜单行（不进侧栏），路径带 :id，Tab 由 ?tab= 控制。
-- 授权角色与「线路管理」(1001) 保持一致，沿用当时已有的角色授权集合，不在本迁移里新造口径。
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,context_menu_id,visible_flag,create_user_id)
VALUES (1004,'线路详情',2,1001,1004,'/delivery/routes/:id','/business/scm/delivery/route-detail-page.vue',NULL,NULL,NULL,NULL,false,1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, 1004 FROM t_role_menu rm WHERE rm.menu_id=1001
  AND NOT EXISTS (SELECT 1 FROM t_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=1004);
SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
