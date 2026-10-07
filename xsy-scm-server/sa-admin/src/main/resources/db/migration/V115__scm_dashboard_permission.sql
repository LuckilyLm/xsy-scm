-- 首页「供应链工作台」的入口权限 scm:dashboard:query。
--
-- 与业务待办（V46）同范式：入口权限只授权访问聚合接口本身，每张 KPI 卡片的可见性由「入口权限 ∩ 领域权限」逐卡决定，
-- 无权卡片整卡省略而不是返回 0 —— 0 会被读成「今天真的没有」，而真实原因是「你没有看这块的权限」。
--
-- 路径 /scm-dashboard 只是占位，不指向真实路由：工作台是内置首页（前端静态路由 /home），
-- 不能拿 /home 当菜单路径，否则动态菜单会再注册一条同路径、组件为 undefined 的空路由。
--
-- 2000 段已核对空闲（1200 段被报表中心占用：1200 报表中心 / 1201 经营概览 / 1211-1230 各报表权限点）。
-- Data-only：2 条 t_menu + role_id = 1 授权 + 序列推进。

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (2000,'供应链工作台',1,0,2000,'/scm-dashboard',NULL,NULL,NULL,NULL,'DashboardOutlined',NULL,false,1) ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (2001,'工作台查询',3,2000,2001,NULL,NULL,1,'scm:dashboard:query','scm:dashboard:query',NULL,NULL,true,1) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (2000, 2001)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), false);
