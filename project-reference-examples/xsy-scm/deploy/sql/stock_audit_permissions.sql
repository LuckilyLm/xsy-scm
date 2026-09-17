-- ============================================================
-- 库存审核相关权限（功能点）初始化
-- 适用：xsy-scm 供应链系统（dev 库）
-- 依赖：库中已存在功能点 stock:adjust:query / stock:check:query
--       且其父菜单为「库存调整 / 库存盘点」页面菜单。
--       新功能点通过反查这两个功能点的 parent_id 自动挂到相同父菜单下，
--       避免硬编码 menu_id。
-- 说明：
--   1) 仅插入 3 个后端权限功能点（stock:adjust:approve / reject / stock:check:complete）
--   2) 默认授权给管理员角色 role_id = 1，如管理员角色 id 不同请自行调整
--   3) 幂等：已存在同 api_perms 的功能点则跳过
-- ============================================================

-- 1. 报损报溢：审核通过
INSERT INTO t_menu
  (menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
SELECT '审核通过', 3, m.parent_id, NULL, NULL, NULL, 1,
       'stock:adjust:approve', 'stock:adjust:approve', NULL, m.parent_id,
       0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()
FROM t_menu m
WHERE m.api_perms = 'stock:adjust:query'
  AND m.menu_type = 3
  AND NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.api_perms = 'stock:adjust:approve');

-- 2. 报损报溢：驳回
INSERT INTO t_menu
  (menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
SELECT '驳回', 3, m.parent_id, NULL, NULL, NULL, 1,
       'stock:adjust:reject', 'stock:adjust:reject', NULL, m.parent_id,
       0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()
FROM t_menu m
WHERE m.api_perms = 'stock:adjust:query'
  AND m.menu_type = 3
  AND NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.api_perms = 'stock:adjust:reject');

-- 3. 库存盘点：盘点完成
INSERT INTO t_menu
  (menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
SELECT '盘点完成', 3, m.parent_id, NULL, NULL, NULL, 1,
       'stock:check:complete', 'stock:check:complete', NULL, m.parent_id,
       0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()
FROM t_menu m
WHERE m.api_perms = 'stock:check:query'
  AND m.menu_type = 3
  AND NOT EXISTS (SELECT 1 FROM t_menu x WHERE x.api_perms = 'stock:check:complete');

-- 4. 将上述功能点授权给管理员角色（role_id = 1，请按实际角色 id 调整）
INSERT INTO t_role_menu (role_id, menu_id, update_time, create_time)
SELECT 1, m.menu_id, NOW(), NOW()
FROM t_menu m
WHERE m.api_perms IN ('stock:adjust:approve', 'stock:adjust:reject', 'stock:check:complete')
  AND NOT EXISTS (
    SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
