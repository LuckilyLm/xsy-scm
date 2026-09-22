-- V48: 库存盘点导入权限（data-only）——盘点效率增强 Wave 6。
--
-- 沿用 V29 的盘点菜单编号段：页面 830、按钮 831-835。导入按钮取 830 下空闲位 837。
-- 语义：新增「导入盘点」按钮权限 scm:inventory:stocktake:import，用于导出实时快照模板
-- （GET /scm/inventory/stocktake/import/template）与整批导入生成草稿
-- （POST /scm/inventory/stocktake/import）。
-- 「复制到新建表单」是纯前端动作，复用既有的 :query（读上一张草稿明细）+ :add（保存新草稿），
-- 不为一个无独立写命令的入口新建 copy 权限。
-- 导入只产生 DRAFT，不写任何库存流水 —— 确认盘点仍是唯一的库存写入路径，Q7 不变。

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (837,'导入盘点',3,830,837,NULL,NULL,1,'scm:inventory:stocktake:import','scm:inventory:stocktake:import',NULL,830,true,1)
ON CONFLICT(menu_id) DO NOTHING;

-- 仅授 SUPER_ADMIN（role_id=1），后续正式业务角色确认后再拆。
INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (837)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
