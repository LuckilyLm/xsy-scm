-- V39: PCO-1 辅助资料菜单与商品批量/字典权限（data-only），2026-09-20。
--
-- 沿用 V7 的商品菜单编号段：页面 401-40x、商品档案按钮 41x、商品分类按钮 42x。
-- 新增「辅助资料」页（计量单位 + 商品标签两个 Tab）挂在 401 下，其按钮取 4xx 空闲段 488-495；
-- 商品批量维护是商品档案页上的动作，续在 41x 段（417）。
--
-- options 类下拉数据源不单独开权限：/scm/product/uom/options 与 /scm/product/tag/options
-- 复用 scm:product:query（411），否则只读角色连筛选条件都渲染不出来。

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (405,'辅助资料',2,401,405,'/product/assistant-list','/business/scm/product/assistant-list.vue',NULL,NULL,NULL,'BookOutlined',NULL,true,1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES (417,'批量维护',3,402,417,NULL,NULL,1,'scm:product:batch','scm:product:batch',NULL,NULL,true,1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id,menu_name,menu_type,parent_id,sort,path,component,perms_type,api_perms,web_perms,icon,context_menu_id,visible_flag,create_user_id)
VALUES
    (488,'计量单位查询',3,405,488,NULL,NULL,1,'scm:product:uom:query','scm:product:uom:query',NULL,405,true,1),
    (489,'计量单位新建',3,405,489,NULL,NULL,1,'scm:product:uom:add','scm:product:uom:add',NULL,405,true,1),
    (490,'计量单位编辑',3,405,490,NULL,NULL,1,'scm:product:uom:update','scm:product:uom:update',NULL,405,true,1),
    (491,'计量单位删除',3,405,491,NULL,NULL,1,'scm:product:uom:delete','scm:product:uom:delete',NULL,405,true,1),
    (492,'商品标签查询',3,405,492,NULL,NULL,1,'scm:product:tag:query','scm:product:tag:query',NULL,405,true,1),
    (493,'商品标签新建',3,405,493,NULL,NULL,1,'scm:product:tag:add','scm:product:tag:add',NULL,405,true,1),
    (494,'商品标签编辑',3,405,494,NULL,NULL,1,'scm:product:tag:update','scm:product:tag:update',NULL,405,true,1),
    (495,'商品标签删除',3,405,495,NULL,NULL,1,'scm:product:tag:delete','scm:product:tag:delete',NULL,405,true,1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id,menu_id)
SELECT 1, m.menu_id FROM t_menu m
WHERE m.menu_id IN (405,417,488,489,490,491,492,493,494,495)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'),(SELECT MAX(menu_id)+1 FROM t_menu),false);
