-- L0-L2 delivery menus; no dispatch/complete permissions until L3 quantity rules are decided.
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES
 (1000,'物流配送',1,0,850,'/delivery',NULL,NULL,NULL,NULL,'CarOutlined',NULL,true,1),
 (1001,'线路管理',2,1000,1,'/delivery/routes','/business/scm/delivery/route-list.vue',NULL,NULL,NULL,'NodeIndexOutlined',NULL,true,1),
 (1002,'司机管理',2,1000,2,'/delivery/drivers','/business/scm/delivery/driver-list.vue',NULL,NULL,NULL,'UserOutlined',NULL,true,1),
 (1003,'车辆管理',2,1000,3,'/delivery/vehicles','/business/scm/delivery/vehicle-list.vue',NULL,NULL,NULL,'CarOutlined',NULL,true,1);
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,perms_type,api_perms,web_perms,context_menu_id,visible_flag,create_user_id) VALUES
 (1011,'线路查询',3,1001,1011,1,'scm:delivery:route:query','scm:delivery:route:query',1001,true,1),
 (1012,'新建线路',3,1001,1012,1,'scm:delivery:route:add','scm:delivery:route:add',1001,true,1),
 (1013,'编排线路',3,1001,1013,1,'scm:delivery:route:update','scm:delivery:route:update',1001,true,1),
 (1014,'确认规划',3,1001,1014,1,'scm:delivery:route:plan','scm:delivery:route:plan',1001,true,1),
 (1015,'取消线路',3,1001,1015,1,'scm:delivery:route:cancel','scm:delivery:route:cancel',1001,true,1),
 (1016,'打印发货单',3,1001,1016,1,'scm:delivery:route:print','scm:delivery:route:print',1001,true,1),
 (1021,'司机查询',3,1002,1021,1,'scm:delivery:driver:query','scm:delivery:driver:query',1002,true,1),
 (1022,'维护司机',3,1002,1022,1,'scm:delivery:driver:edit','scm:delivery:driver:edit',1002,true,1),
 (1031,'车辆查询',3,1003,1031,1,'scm:delivery:vehicle:query','scm:delivery:vehicle:query',1003,true,1),
 (1032,'维护车辆',3,1003,1032,1,'scm:delivery:vehicle:edit','scm:delivery:vehicle:edit',1003,true,1);
INSERT INTO t_role_menu(role_id,menu_id) SELECT 1,menu_id FROM t_menu WHERE menu_id IN (1000,1001,1002,1003,1011,1012,1013,1014,1015,1016,1021,1022,1031,1032);
SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
