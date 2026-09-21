-- ============================================================================
-- V17: 为 SCM 业务菜单补充侧边栏图标
-- ============================================================================
-- 背景：W1–W5 建的业务菜单只写了 menu_name / path / component，icon 字段留空。
--       前端侧边栏用 `$antIcons[item.icon]` 渲染图标，icon 为空时：
--         - 展开态：菜单项左侧留白，观感不齐
--         - 收起态：因缺少图标，菜单会溢出显示文字而不是收成图标
--
-- 取值约束：icon 存的是 Ant Design Vue 组件名（与既有原生菜单一致，
--           如 133 缓存管理 = BorderInnerOutlined、221 定时任务 = ClockCircleOutlined）。
--           这些图标由 main.ts 全量注册：`Object.keys(antIcons).forEach(...)`，
--           因此直接写组件名字符串即可，无需改名或前缀。
--           本轮用到的 30 个名字均已对 @ant-design/icons-vue@7.0.1 校验存在。
--
-- 幂等性：全部用 WHERE icon IS NULL OR icon = '' 守卫，
--         若后续有人手工补过图标，本迁移不会覆盖。
-- 影响面：仅 t_menu.icon 字段，不新增/删除任何菜单，不触碰权限（web_perms/api_perms）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 一级目录（menu_type = 1）
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'ShoppingOutlined',
    update_time = now()
WHERE menu_id = 401
  AND (icon IS NULL OR icon = ''); -- 商品管理
UPDATE xsy_v2.t_menu
SET icon        = 'TeamOutlined',
    update_time = now()
WHERE menu_id = 431
  AND (icon IS NULL OR icon = ''); -- 客户管理
UPDATE xsy_v2.t_menu
SET icon        = 'ShopOutlined',
    update_time = now()
WHERE menu_id = 461
  AND (icon IS NULL OR icon = ''); -- 供应商管理
UPDATE xsy_v2.t_menu
SET icon        = 'DollarOutlined',
    update_time = now()
WHERE menu_id = 501
  AND (icon IS NULL OR icon = ''); -- 价格中心
UPDATE xsy_v2.t_menu
SET icon        = 'ProfileOutlined',
    update_time = now()
WHERE menu_id = 601
  AND (icon IS NULL OR icon = ''); -- 销售订单
UPDATE xsy_v2.t_menu
SET icon        = 'ContainerOutlined',
    update_time = now()
WHERE menu_id = 701
  AND (icon IS NULL OR icon = '');
-- 采购管理

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 商品管理
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'AppstoreOutlined',
    update_time = now()
WHERE menu_id = 402
  AND (icon IS NULL OR icon = ''); -- 商品档案
UPDATE xsy_v2.t_menu
SET icon        = 'TagsOutlined',
    update_time = now()
WHERE menu_id = 403
  AND (icon IS NULL OR icon = ''); -- 商品分类
UPDATE xsy_v2.t_menu
SET icon        = 'FileTextOutlined',
    update_time = now()
WHERE menu_id = 404
  AND (icon IS NULL OR icon = '');
-- 商品详情

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 客户管理
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'IdcardOutlined',
    update_time = now()
WHERE menu_id = 432
  AND (icon IS NULL OR icon = ''); -- 客户档案
UPDATE xsy_v2.t_menu
SET icon        = 'ApartmentOutlined',
    update_time = now()
WHERE menu_id = 433
  AND (icon IS NULL OR icon = ''); -- 客户类型
UPDATE xsy_v2.t_menu
SET icon        = 'FileTextOutlined',
    update_time = now()
WHERE menu_id = 434
  AND (icon IS NULL OR icon = ''); -- 客户详情
UPDATE xsy_v2.t_menu
SET icon        = 'EyeOutlined',
    update_time = now()
WHERE menu_id = 435
  AND (icon IS NULL OR icon = '');
-- 客户 SKU 可见性

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 供应商管理
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'ContactsOutlined',
    update_time = now()
WHERE menu_id = 462
  AND (icon IS NULL OR icon = ''); -- 供应商档案
UPDATE xsy_v2.t_menu
SET icon        = 'FileTextOutlined',
    update_time = now()
WHERE menu_id = 463
  AND (icon IS NULL OR icon = ''); -- 供应商详情
UPDATE xsy_v2.t_menu
SET icon        = 'SwapOutlined',
    update_time = now()
WHERE menu_id = 464
  AND (icon IS NULL OR icon = '');
-- 商品-供应商关系

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 价格中心
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'AccountBookOutlined',
    update_time = now()
WHERE menu_id = 502
  AND (icon IS NULL OR icon = ''); -- 客户协议价
UPDATE xsy_v2.t_menu
SET icon        = 'ClusterOutlined',
    update_time = now()
WHERE menu_id = 503
  AND (icon IS NULL OR icon = ''); -- 客户类型价
UPDATE xsy_v2.t_menu
SET icon        = 'SwapOutlined',
    update_time = now()
WHERE menu_id = 504
  AND (icon IS NULL OR icon = ''); -- 批量调价
UPDATE xsy_v2.t_menu
SET icon        = 'HistoryOutlined',
    update_time = now()
WHERE menu_id = 505
  AND (icon IS NULL OR icon = ''); -- 价格历史
UPDATE xsy_v2.t_menu
SET icon        = 'CalculatorOutlined',
    update_time = now()
WHERE menu_id = 506
  AND (icon IS NULL OR icon = '');
-- 取价试算

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 销售订单
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'ProfileOutlined',
    update_time = now()
WHERE menu_id = 602
  AND (icon IS NULL OR icon = ''); -- 订单列表
UPDATE xsy_v2.t_menu
SET icon        = 'RollbackOutlined',
    update_time = now()
WHERE menu_id = 603
  AND (icon IS NULL OR icon = ''); -- 退货单
UPDATE xsy_v2.t_menu
SET icon        = 'UndoOutlined',
    update_time = now()
WHERE menu_id = 604
  AND (icon IS NULL OR icon = ''); -- 退款单
UPDATE xsy_v2.t_menu
SET icon        = 'AuditOutlined',
    update_time = now()
WHERE menu_id = 605
  AND (icon IS NULL OR icon = '');
-- 订单操作日志

-- ---------------------------------------------------------------------------
-- 二级菜单 —— 采购管理
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'FileSearchOutlined',
    update_time = now()
WHERE menu_id = 702
  AND (icon IS NULL OR icon = ''); -- 采购需求
UPDATE xsy_v2.t_menu
SET icon        = 'ContainerOutlined',
    update_time = now()
WHERE menu_id = 703
  AND (icon IS NULL OR icon = ''); -- 采购订单
UPDATE xsy_v2.t_menu
SET icon        = 'ImportOutlined',
    update_time = now()
WHERE menu_id = 704
  AND (icon IS NULL OR icon = ''); -- 采购收货
UPDATE xsy_v2.t_menu
SET icon        = 'AuditOutlined',
    update_time = now()
WHERE menu_id = 705
  AND (icon IS NULL OR icon = ''); -- 采购日志
UPDATE xsy_v2.t_menu
SET icon        = 'DatabaseOutlined',
    update_time = now()
WHERE menu_id = 706
  AND (icon IS NULL OR icon = '');
-- 仓库管理

-- ---------------------------------------------------------------------------
-- 顺带补齐 4 个 OA 域的隐藏详情页（无图标会让面包屑/标签页观感不齐）
-- 142 公告详情 / 145 企业详情 / 149 我的通知 / 150 我的通知公告详情
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu
SET icon        = 'InfoCircleOutlined',
    update_time = now()
WHERE menu_id = 142
  AND (icon IS NULL OR icon = ''); -- 公告详情
UPDATE xsy_v2.t_menu
SET icon        = 'InfoCircleOutlined',
    update_time = now()
WHERE menu_id = 145
  AND (icon IS NULL OR icon = ''); -- 企业详情
UPDATE xsy_v2.t_menu
SET icon        = 'BellOutlined',
    update_time = now()
WHERE menu_id = 149
  AND (icon IS NULL OR icon = ''); -- 我的通知
UPDATE xsy_v2.t_menu
SET icon        = 'InfoCircleOutlined',
    update_time = now()
WHERE menu_id = 150
  AND (icon IS NULL OR icon = ''); -- 我的通知公告详情
