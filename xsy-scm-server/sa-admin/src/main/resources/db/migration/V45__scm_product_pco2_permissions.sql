-- V45: PCO-2 商品运营维护效率权限（data-only）——导入 / 导出 / 图片中心。
--
-- 沿用 V7 / V39 的商品菜单编号段：页面 401-40x、商品档案按钮 41x、空闲位续用。
-- 导入 / 导出是商品档案页（402）上的动作，取 41x 段空闲的 418 / 419。
-- 图片中心是独立页面 406（挂在 401 商品管理下），其按钮取 4xx 空闲段 496 / 497。
-- 原有 scm:product:image（416，单商品编辑）保持不变，不删除。

-- 商品档案页动作：导入 / 导出
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES
    (418,'导入商品',3,402,418,NULL,NULL,1,'scm:product:import','scm:product:import',NULL,402,true,1),
    (419,'导出商品',3,402,419,NULL,NULL,1,'scm:product:export','scm:product:export',NULL,402,true,1)
ON CONFLICT(menu_id) DO NOTHING;

-- 图片中心页面
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (406,'图片中心',2,401,406,'/product/image-center','/business/scm/product/image-center.vue',NULL,NULL,NULL,'PictureOutlined',NULL,true,1)
ON CONFLICT(menu_id) DO NOTHING;

-- 图片中心按钮：查询 / 批量绑定·移除·主图·排序
INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES
    (496,'图片查询',3,406,496,NULL,NULL,1,'scm:product:image:query','scm:product:image:query',NULL,406,true,1),
    (497,'图片批量维护',3,406,497,NULL,NULL,1,'scm:product:image:batch','scm:product:image:batch',NULL,406,true,1)
ON CONFLICT(menu_id) DO NOTHING;

-- 仅授 SUPER_ADMIN（role_id=1），后续正式业务角色确认后再拆。
INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (418,419,406,496,497)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
