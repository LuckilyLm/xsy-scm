-- ============================================================================
-- V110: 重新排列一级导航（业务区在前、平台管理区沉底）
-- ============================================================================
-- 背景：一级菜单的先后**完全**由 t_menu.sort 决定 —— 后端菜单查询按 sort ASC 返回，
--       前端 recursion-menu.vue 直接消费 useUserStore().getMenuTree。
--       历史遗留的 SmartAdmin 工具菜单 sort 很小（功能Demo=1、组织架构=3、系统设置=6…），
--       于是它们插在供应链业务菜单前面和中间，侧边栏读起来是断裂的。
--
-- 做法：**只调整 parent_id = 0 的 sort**，不动任何菜单层级、路径、组件、权限、可见性。
--       因此菜单管理、角色菜单树、面包屑、顶部菜单等所有消费 t_menu 的地方口径一致，
--       不需要前端再维护一份「特殊排序名单」（那会是第二份真相）。
--
-- 分区与号位：
--   业务区        60 ~ 1310   （步长 100，留出插入空间）
--   平台管理区   9000 ~ 9060  （步长 10；与业务区留出 7000+ 个空号给未来新增业务菜单）
--
-- 隐藏菜单（visible_flag = false）也一并排进空位，**避免与可见菜单撞号**：
-- 业务区第 10 位正好落在 900，而「数据大屏」原本就是 900 —— 同值会让
-- 「日后把它打开」时的相对顺序不确定（ORDER BY sort 对同值不稳定）。
-- 它们仍然保持隐藏，这里只改 sort。
--
-- 幂等：全部按 menu_id 精确 UPDATE，重复执行结果相同。
-- 影响面：仅 t_menu.sort（以及 update_time），不新增/删除任何菜单，不触碰权限字段。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 业务区
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu SET sort = 60,   update_time = now() WHERE menu_id = 1100; -- 业务待办（隐藏）
UPDATE xsy_v2.t_menu SET sort = 100,  update_time = now() WHERE menu_id = 401;  -- 商品管理
UPDATE xsy_v2.t_menu SET sort = 200,  update_time = now() WHERE menu_id = 431;  -- 客户管理
UPDATE xsy_v2.t_menu SET sort = 300,  update_time = now() WHERE menu_id = 461;  -- 供应商管理
UPDATE xsy_v2.t_menu SET sort = 400,  update_time = now() WHERE menu_id = 501;  -- 价格中心
UPDATE xsy_v2.t_menu SET sort = 500,  update_time = now() WHERE menu_id = 1700; -- 营销中心
UPDATE xsy_v2.t_menu SET sort = 600,  update_time = now() WHERE menu_id = 601;  -- 销售订单
UPDATE xsy_v2.t_menu SET sort = 700,  update_time = now() WHERE menu_id = 701;  -- 采购管理
UPDATE xsy_v2.t_menu SET sort = 800,  update_time = now() WHERE menu_id = 800;  -- 库存管理
UPDATE xsy_v2.t_menu SET sort = 900,  update_time = now() WHERE menu_id = 1000; -- 物流配送
UPDATE xsy_v2.t_menu SET sort = 1000, update_time = now() WHERE menu_id = 1400; -- 分拣管理
UPDATE xsy_v2.t_menu SET sort = 1100, update_time = now() WHERE menu_id = 1500; -- 财务管理
-- 资金类两个隐藏入口紧贴财务管理，日后启用时仍落在它旁边
UPDATE xsy_v2.t_menu SET sort = 1150, update_time = now() WHERE menu_id = 1710; -- 支付管理（隐藏）
UPDATE xsy_v2.t_menu SET sort = 1160, update_time = now() WHERE menu_id = 1718; -- 客户余额（隐藏）
UPDATE xsy_v2.t_menu SET sort = 1200, update_time = now() WHERE menu_id = 1200; -- 报表中心
UPDATE xsy_v2.t_menu SET sort = 1300, update_time = now() WHERE menu_id = 1600; -- 打印中心
UPDATE xsy_v2.t_menu SET sort = 1310, update_time = now() WHERE menu_id = 900;  -- 数据大屏（隐藏）

-- ---------------------------------------------------------------------------
-- 平台管理区（SmartAdmin 支撑页，统一沉底）
-- ---------------------------------------------------------------------------
UPDATE xsy_v2.t_menu SET sort = 9000, update_time = now() WHERE menu_id = 45;  -- 组织架构
UPDATE xsy_v2.t_menu SET sort = 9010, update_time = now() WHERE menu_id = 50;  -- 系统设置
UPDATE xsy_v2.t_menu SET sort = 9020, update_time = now() WHERE menu_id = 213; -- 网络安全
UPDATE xsy_v2.t_menu SET sort = 9030, update_time = now() WHERE menu_id = 111; -- 监控服务
UPDATE xsy_v2.t_menu SET sort = 9040, update_time = now() WHERE menu_id = 218; -- 文档中心
UPDATE xsy_v2.t_menu SET sort = 9050, update_time = now() WHERE menu_id = 151; -- 代码生成
-- 功能Demo 排在最后；生产环境若不需要，把 visible_flag 置 false 即可，不必再改 sort
UPDATE xsy_v2.t_menu SET sort = 9060, update_time = now() WHERE menu_id = 138; -- 功能Demo
