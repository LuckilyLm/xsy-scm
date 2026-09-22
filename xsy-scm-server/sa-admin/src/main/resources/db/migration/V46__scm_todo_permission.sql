-- Wave 4 业务待办权限（data-only），2026-09-22。
--
-- 待办聚合只读入口权限 scm:todo:query，仅授入口不隐含任何领域权限；
-- 领域计数由后端按待办权限 ∩ 领域查询/操作权限交集计算，无权卡片直接省略。
-- 沿用 V28 隐藏目录 + 权限点范式；1100 段已核对空闲（当前最大 menu_id 为 1032）。

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (1100,'业务待办',1,0,1100,'/scm-todo',NULL,NULL,NULL,NULL,'CheckSquareOutlined',NULL,false,1) ON CONFLICT(menu_id) DO NOTHING;
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (1101,'待办查询',3,1100,1101,NULL,NULL,1,'scm:todo:query','scm:todo:query',NULL,NULL,true,1) ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (1100, 1101)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), false);
