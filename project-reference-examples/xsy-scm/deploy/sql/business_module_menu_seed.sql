-- ============================================================
-- XSY-SCM 供应链业务模块 菜单 + 权限 初始化（seed）
-- 适用库：supply_chain（dev）
-- 覆盖模块：客户管理 / 订单管理 / 产品管理 / 采购管理
-- 背景：库中原本没有任何 customer/order/product/purchase/supplier 权限。
--       已存在的基础菜单（category=8、goods=7、oa=20、stock=26）不重复生成。
-- ID 规划：600-699 客户 / 700-799 订单 / 800-899 产品 / 900-999 采购
-- 说明：
--   1) 结构：模块(type=1) -> 页面(type=2) -> 功能点(type=3)
--   2) 功能点 api_perms 与后端 @SaCheckPermission 一一对应。
--   3) INSERT IGNORE，可重复执行（已存在则跳过）。
--   4) component 路径按 PC 后台约定填写，请与 PC 前端实际组件路径对齐。
-- ============================================================

-- ================= 客户管理（600+） =================
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  (600, '客户管理', 1, 0, 20, '/customer', NULL, NULL, NULL, NULL, 'TeamOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (601, '客户列表', 2, 600, 1, '/customer/customer', 'customer/customer/customer-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (602, '客户账期', 2, 600, 2, '/customer/period', 'customer/period/customer-period-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (603, '客户商品可见', 2, 600, 3, '/customer/goods-visible', 'customer/goods-visible/customer-goods-visible-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (604, '客户收款码', 2, 600, 4, '/customer/qrcode', 'customer/qrcode/customer-qrcode-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 客户列表功能点
  (610, '查询', 3, 601, NULL, NULL, NULL, 1, 'customer:query', 'customer:query', NULL, 601, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (611, '新增', 3, 601, NULL, NULL, NULL, 1, 'customer:add', 'customer:add', NULL, 601, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (612, '修改', 3, 601, NULL, NULL, NULL, 1, 'customer:update', 'customer:update', NULL, 601, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (613, '删除', 3, 601, NULL, NULL, NULL, 1, 'customer:delete', 'customer:delete', NULL, 601, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (614, '批量删除', 3, 601, NULL, NULL, NULL, 1, 'customer:batchDelete', 'customer:batchDelete', NULL, 601, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 客户账期功能点
  (620, '查询', 3, 602, NULL, NULL, NULL, 1, 'customer:period:query', 'customer:period:query', NULL, 602, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (621, '新增', 3, 602, NULL, NULL, NULL, 1, 'customer:period:add', 'customer:period:add', NULL, 602, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (622, '修改', 3, 602, NULL, NULL, NULL, 1, 'customer:period:update', 'customer:period:update', NULL, 602, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (623, '删除', 3, 602, NULL, NULL, NULL, 1, 'customer:period:delete', 'customer:period:delete', NULL, 602, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (624, '批量删除', 3, 602, NULL, NULL, NULL, 1, 'customer:period:batchDelete', 'customer:period:batchDelete', NULL, 602, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 客户商品可见功能点
  (630, '查询', 3, 603, NULL, NULL, NULL, 1, 'customer:goodsVisible:query', 'customer:goodsVisible:query', NULL, 603, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (631, '新增', 3, 603, NULL, NULL, NULL, 1, 'customer:goodsVisible:add', 'customer:goodsVisible:add', NULL, 603, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (632, '修改', 3, 603, NULL, NULL, NULL, 1, 'customer:goodsVisible:update', 'customer:goodsVisible:update', NULL, 603, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (633, '删除', 3, 603, NULL, NULL, NULL, 1, 'customer:goodsVisible:delete', 'customer:goodsVisible:delete', NULL, 603, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (634, '批量删除', 3, 603, NULL, NULL, NULL, 1, 'customer:goodsVisible:batchDelete', 'customer:goodsVisible:batchDelete', NULL, 603, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 客户收款码功能点
  (640, '查询', 3, 604, NULL, NULL, NULL, 1, 'customer:qrcode:query', 'customer:qrcode:query', NULL, 604, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (641, '新增', 3, 604, NULL, NULL, NULL, 1, 'customer:qrcode:add', 'customer:qrcode:add', NULL, 604, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (642, '修改', 3, 604, NULL, NULL, NULL, 1, 'customer:qrcode:update', 'customer:qrcode:update', NULL, 604, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (643, '删除', 3, 604, NULL, NULL, NULL, 1, 'customer:qrcode:delete', 'customer:qrcode:delete', NULL, 604, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (644, '批量删除', 3, 604, NULL, NULL, NULL, 1, 'customer:qrcode:batchDelete', 'customer:qrcode:batchDelete', NULL, 604, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ================= 订单管理（700+） =================
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  (700, '订单管理', 1, 0, 30, '/order', NULL, NULL, NULL, NULL, 'ShoppingCartOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (701, '销售订单', 2, 700, 1, '/order/order', 'order/order/sale-order-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (702, '订单明细', 2, 700, 2, '/order/item', 'order/item/sale-order-item-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (703, '订单日志', 2, 700, 3, '/order/log', 'order/log/sale-order-log-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (704, '退款单', 2, 700, 4, '/order/refund', 'order/refund/sale-refund-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 销售订单功能点
  (710, '查询', 3, 701, NULL, NULL, NULL, 1, 'order:query', 'order:query', NULL, 701, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (711, '新增', 3, 701, NULL, NULL, NULL, 1, 'order:add', 'order:add', NULL, 701, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (712, '修改', 3, 701, NULL, NULL, NULL, 1, 'order:update', 'order:update', NULL, 701, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (713, '删除', 3, 701, NULL, NULL, NULL, 1, 'order:delete', 'order:delete', NULL, 701, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (714, '批量删除', 3, 701, NULL, NULL, NULL, 1, 'order:batchDelete', 'order:batchDelete', NULL, 701, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 订单明细功能点
  (720, '查询', 3, 702, NULL, NULL, NULL, 1, 'order:item:query', 'order:item:query', NULL, 702, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (721, '新增', 3, 702, NULL, NULL, NULL, 1, 'order:item:add', 'order:item:add', NULL, 702, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (722, '修改', 3, 702, NULL, NULL, NULL, 1, 'order:item:update', 'order:item:update', NULL, 702, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (723, '删除', 3, 702, NULL, NULL, NULL, 1, 'order:item:delete', 'order:item:delete', NULL, 702, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (724, '批量删除', 3, 702, NULL, NULL, NULL, 1, 'order:item:batchDelete', 'order:item:batchDelete', NULL, 702, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 订单日志功能点（仅查询/新增）
  (730, '查询', 3, 703, NULL, NULL, NULL, 1, 'order:log:query', 'order:log:query', NULL, 703, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (731, '新增', 3, 703, NULL, NULL, NULL, 1, 'order:log:add', 'order:log:add', NULL, 703, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 退款单功能点
  (740, '查询', 3, 704, NULL, NULL, NULL, 1, 'order:refund:query', 'order:refund:query', NULL, 704, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (741, '新增', 3, 704, NULL, NULL, NULL, 1, 'order:refund:add', 'order:refund:add', NULL, 704, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (742, '修改', 3, 704, NULL, NULL, NULL, 1, 'order:refund:update', 'order:refund:update', NULL, 704, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (743, '删除', 3, 704, NULL, NULL, NULL, 1, 'order:refund:delete', 'order:refund:delete', NULL, 704, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (744, '批量删除', 3, 704, NULL, NULL, NULL, 1, 'order:refund:batchDelete', 'order:refund:batchDelete', NULL, 704, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ================= 产品管理（800+） =================
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  (800, '产品管理', 1, 0, 40, '/product', NULL, NULL, NULL, NULL, 'TagsOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (801, '产品列表', 2, 800, 1, '/product/product', 'product/product/product-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (802, '产品规格', 2, 800, 2, '/product/sku', 'product/sku/product-sku-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (803, '产品价格', 2, 800, 3, '/product/price', 'product/price/product-price-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (804, '产品供应商', 2, 800, 4, '/product/supplier', 'product/supplier/product-supplier-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 产品列表功能点
  (810, '查询', 3, 801, NULL, NULL, NULL, 1, 'product:query', 'product:query', NULL, 801, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (811, '新增', 3, 801, NULL, NULL, NULL, 1, 'product:add', 'product:add', NULL, 801, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (812, '修改', 3, 801, NULL, NULL, NULL, 1, 'product:update', 'product:update', NULL, 801, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (813, '删除', 3, 801, NULL, NULL, NULL, 1, 'product:delete', 'product:delete', NULL, 801, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (814, '批量删除', 3, 801, NULL, NULL, NULL, 1, 'product:batchDelete', 'product:batchDelete', NULL, 801, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 产品规格功能点
  (820, '查询', 3, 802, NULL, NULL, NULL, 1, 'product:sku:query', 'product:sku:query', NULL, 802, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (821, '新增', 3, 802, NULL, NULL, NULL, 1, 'product:sku:add', 'product:sku:add', NULL, 802, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (822, '修改', 3, 802, NULL, NULL, NULL, 1, 'product:sku:update', 'product:sku:update', NULL, 802, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (823, '删除', 3, 802, NULL, NULL, NULL, 1, 'product:sku:delete', 'product:sku:delete', NULL, 802, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (824, '批量删除', 3, 802, NULL, NULL, NULL, 1, 'product:sku:batchDelete', 'product:sku:batchDelete', NULL, 802, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 产品价格功能点
  (830, '查询', 3, 803, NULL, NULL, NULL, 1, 'product:price:query', 'product:price:query', NULL, 803, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (831, '新增', 3, 803, NULL, NULL, NULL, 1, 'product:price:add', 'product:price:add', NULL, 803, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (832, '修改', 3, 803, NULL, NULL, NULL, 1, 'product:price:update', 'product:price:update', NULL, 803, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (833, '删除', 3, 803, NULL, NULL, NULL, 1, 'product:price:delete', 'product:price:delete', NULL, 803, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (834, '批量删除', 3, 803, NULL, NULL, NULL, 1, 'product:price:batchDelete', 'product:price:batchDelete', NULL, 803, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 产品供应商功能点
  (840, '查询', 3, 804, NULL, NULL, NULL, 1, 'product:supplier:query', 'product:supplier:query', NULL, 804, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (841, '新增', 3, 804, NULL, NULL, NULL, 1, 'product:supplier:add', 'product:supplier:add', NULL, 804, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (842, '修改', 3, 804, NULL, NULL, NULL, 1, 'product:supplier:update', 'product:supplier:update', NULL, 804, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (843, '删除', 3, 804, NULL, NULL, NULL, 1, 'product:supplier:delete', 'product:supplier:delete', NULL, 804, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (844, '批量删除', 3, 804, NULL, NULL, NULL, 1, 'product:supplier:batchDelete', 'product:supplier:batchDelete', NULL, 804, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ================= 采购管理（900+） =================
INSERT IGNORE INTO t_menu
  (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
   api_perms, web_perms, icon, context_menu_id, frame_flag, frame_url,
   cache_flag, visible_flag, disabled_flag, deleted_flag,
   create_user_id, create_time, update_user_id, update_time)
VALUES
  (900, '采购管理', 1, 0, 50, '/purchase', NULL, NULL, NULL, NULL, 'ShoppingOutlined', NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (901, '供应商', 2, 900, 1, '/purchase/supplier', 'purchase/supplier/supplier-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (902, '采购订单', 2, 900, 2, '/purchase/order', 'purchase/purchase-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (903, '采购明细', 2, 900, 3, '/purchase/item', 'purchase/item/purchase-item-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (904, '采购收货', 2, 900, 4, '/purchase/receive', 'purchase/receive/purchase-receive-list.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 供应商功能点
  (910, '查询', 3, 901, NULL, NULL, NULL, 1, 'supplier:query', 'supplier:query', NULL, 901, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (911, '新增', 3, 901, NULL, NULL, NULL, 1, 'supplier:add', 'supplier:add', NULL, 901, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (912, '修改', 3, 901, NULL, NULL, NULL, 1, 'supplier:update', 'supplier:update', NULL, 901, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (913, '删除', 3, 901, NULL, NULL, NULL, 1, 'supplier:delete', 'supplier:delete', NULL, 901, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (914, '批量删除', 3, 901, NULL, NULL, NULL, 1, 'supplier:batchDelete', 'supplier:batchDelete', NULL, 901, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 采购订单功能点
  (920, '查询', 3, 902, NULL, NULL, NULL, 1, 'purchase:query', 'purchase:query', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (921, '新增', 3, 902, NULL, NULL, NULL, 1, 'purchase:add', 'purchase:add', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (922, '修改', 3, 902, NULL, NULL, NULL, 1, 'purchase:update', 'purchase:update', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (923, '删除', 3, 902, NULL, NULL, NULL, 1, 'purchase:delete', 'purchase:delete', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (924, '批量删除', 3, 902, NULL, NULL, NULL, 1, 'purchase:batchDelete', 'purchase:batchDelete', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (925, '接单', 3, 902, NULL, NULL, NULL, 1, 'purchase:accept', 'purchase:accept', NULL, 902, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 采购明细功能点
  (930, '查询', 3, 903, NULL, NULL, NULL, 1, 'purchase:item:query', 'purchase:item:query', NULL, 903, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (931, '新增', 3, 903, NULL, NULL, NULL, 1, 'purchase:item:add', 'purchase:item:add', NULL, 903, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (932, '修改', 3, 903, NULL, NULL, NULL, 1, 'purchase:item:update', 'purchase:item:update', NULL, 903, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (933, '删除', 3, 903, NULL, NULL, NULL, 1, 'purchase:item:delete', 'purchase:item:delete', NULL, 903, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (934, '批量删除', 3, 903, NULL, NULL, NULL, 1, 'purchase:item:batchDelete', 'purchase:item:batchDelete', NULL, 903, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  -- 采购收货功能点
  (940, '查询', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:query', 'purchase:receive:query', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (941, '新增', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:add', 'purchase:receive:add', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (942, '修改', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:update', 'purchase:receive:update', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (943, '删除', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:delete', 'purchase:receive:delete', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (944, '批量删除', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:batchDelete', 'purchase:receive:batchDelete', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (945, '入库确认', 3, 904, NULL, NULL, NULL, 1, 'purchase:receive:confirmInbound', 'purchase:receive:confirmInbound', NULL, 904, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (905, '采购单生成', 2, 900, 5, '/purchase/generate', 'purchase/purchase-generate.vue', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (950, '预览', 3, 905, NULL, NULL, NULL, 1, 'purchase:generate:preview', 'purchase:generate:preview', NULL, 905, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW()),
  (951, '生成', 3, 905, NULL, NULL, NULL, 1, 'purchase:generate', 'purchase:generate', NULL, 905, 0, NULL, 0, 1, 0, 0, 1, NOW(), 1, NOW());

-- ================= 授权给所有角色（含非管理员） =================
INSERT INTO t_role_menu (role_id, menu_id, update_time, create_time)
SELECT r.role_id, m.menu_id, NOW(), NOW()
FROM t_role r, t_menu m
WHERE m.menu_id BETWEEN 600 AND 999
  AND NOT EXISTS (
    SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
