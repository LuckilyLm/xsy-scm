-- ============================================================
-- XSY-SCM 库存模块 菜单 + 权限 初始化（seed）
-- 适用库：supply_chain（dev）
-- 背景：当前 supply_chain 库仅有 SmartAdmin 基础菜单，
--       缺少全部供应链业务菜单。本脚本补齐「库存管理」模块整棵树，
--       使前端能显示库存菜单、非管理员角色也能访问库存接口。
-- 说明：
--   1) 菜单结构：模块(库存管理) -> 5 个页面(余额/流水/报损报溢/盘点/盘点明细)
--      -> 各页面下功能点（共 20 个，含新增的 approve/reject/complete）。
--   2) 功能点 api_perms 与后端 @SaCheckPermission 一一对应。
--   3) 超级管理员(administratorFlag=1) 本就绕过权限校验，无需授权；
--      此处仍把菜单挂到所有角色，保证非管理员可用。
--   4) 使用 INSERT IGNORE，重复执行不会报错（已存在则跳过）。
--   5) component 路径按 PC 后台约定填写，请与你的 PC 前端实际组件路径对齐。
-- ============================================================

-- ---------------- 模块与页面 ----------------
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  -- 模块
  (500, '库存管理', 1, 0, 10, '/stock', NULL, NULL, NULL, NULL, 'AppstoreOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 页面：库存余额
  (501, '库存余额', 2, 500, 1, '/stock/balance', 'stock/balance/stock-balance-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 页面：库存流水
  (502, '库存流水', 2, 500, 2, '/stock/flow', 'stock/flow/stock-flow-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 页面：报损报溢
  (503, '报损报溢', 2, 500, 3, '/stock/adjust', 'stock/adjust/stock-adjust-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 页面：库存盘点
  (504, '库存盘点', 2, 500, 4, '/stock/check', 'stock/check/stock-check-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 页面：盘点明细
  (505, '盘点明细', 2, 500, 5, '/stock/check-item', 'stock/check/stock-check-item-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ---------------- 功能点（权限） ----------------
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  -- 库存余额
  (510, '查询', 3, 501, NULL, NULL, NULL, 1, 'stock:balance:query', 'stock:balance:query', NULL, 501, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 库存流水
  (511, '查询', 3, 502, NULL, NULL, NULL, 1, 'stock:flow:query', 'stock:flow:query', NULL, 502, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 报损报溢
  (520, '查询', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:query', 'stock:adjust:query', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (521, '新增', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:add', 'stock:adjust:add', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (522, '修改', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:update', 'stock:adjust:update', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (523, '删除', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:delete', 'stock:adjust:delete', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (524, '批量删除', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:batchDelete', 'stock:adjust:batchDelete', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (525, '审核通过', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:approve', 'stock:adjust:approve', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (526, '驳回', 3, 503, NULL, NULL, NULL, 1, 'stock:adjust:reject', 'stock:adjust:reject', NULL, 503, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 库存盘点
  (530, '查询', 3, 504, NULL, NULL, NULL, 1, 'stock:check:query', 'stock:check:query', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (531, '新增', 3, 504, NULL, NULL, NULL, 1, 'stock:check:add', 'stock:check:add', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (532, '修改', 3, 504, NULL, NULL, NULL, 1, 'stock:check:update', 'stock:check:update', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (533, '删除', 3, 504, NULL, NULL, NULL, 1, 'stock:check:delete', 'stock:check:delete', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (534, '批量删除', 3, 504, NULL, NULL, NULL, 1, 'stock:check:batchDelete', 'stock:check:batchDelete', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (535, '盘点完成', 3, 504, NULL, NULL, NULL, 1, 'stock:check:complete', 'stock:check:complete', NULL, 504, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 盘点明细
  (540, '查询', 3, 505, NULL, NULL, NULL, 1, 'stock:check:item:query', 'stock:check:item:query', NULL, 505, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (541, '新增', 3, 505, NULL, NULL, NULL, 1, 'stock:check:item:add', 'stock:check:item:add', NULL, 505, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (542, '修改', 3, 505, NULL, NULL, NULL, 1, 'stock:check:item:update', 'stock:check:item:update', NULL, 505, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (543, '删除', 3, 505, NULL, NULL, NULL, 1, 'stock:check:item:delete', 'stock:check:item:delete', NULL, 505, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (544, '批量删除', 3, 505, NULL, NULL, NULL, 1, 'stock:check:item:batchDelete', 'stock:check:item:batchDelete', NULL, 505, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ---------------- 授权给所有角色（含非管理员） ----------------
INSERT INTO t_role_menu (role_id, menu_id, update_time, create_time)
SELECT r.role_id, m.menu_id, NOW(), NOW()
FROM t_role r, t_menu m
WHERE m.menu_id BETWEEN 500 AND 544
  AND NOT EXISTS (
    SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
