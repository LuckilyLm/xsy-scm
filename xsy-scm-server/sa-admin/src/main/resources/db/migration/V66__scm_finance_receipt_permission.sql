-- ============================================================================
-- V66 SCM Finance R1 F1-3A：收款登记能力（data-only）
--
-- 背景：scm:finance:receipt:add 是财务域**第一个用户可调用的受保护端点**
-- （POST /scm/finance/receipt/add）。F1-1 / F1-2 一条权限都没种 —— 那时既没有
-- Controller 也没有页面，种了就是「已授权但无端点使用它」的永久号段。
--
-- 发布形态照 V28（数据大屏 900 目录 + 901 查询权限）与 V46（业务待办 1100 + 1101）：
-- 1500 是**隐藏目录**（menu_type=1、component 为 NULL、visible_flag=false），
-- 1521 是挂在它下面的**能力点**（menu_type=3）。目录没有组件，因此不会产生
-- 「点开空白页」这一 F1-1 明令禁止的形态；能力点照常出现在角色管理界面里可被分配。
-- 页面菜单 1501–1505 与 1500 的可见性一起留给 F1-6（.vue 落地那一阶段）。
--
-- 号段重扫结论（本迁移编写时实测）：t_menu 现有最大 menu_id 为 1421（V61 分拣），
-- 1500 与 1521 均空闲；规划值取自设计稿 §16 的 1500–1531 段。
--
-- 只种 F1-3A 真实用到的这一条：
--   * 不种 scm:finance:receipt:query / payment:query —— 本阶段没有查询端点；
--   * 不种 payment:add / *reverse / write-off:* / payable:red / export —— 能力还不存在；
--   * 不种任何页面菜单；
--   * 不新增任何 *:scope:all:query（第三批 D-5：财务的全范围来自 V56 已授的
--     1302 / 1311 / 1322 显式授权，不靠角色名绕过范围判定）。
--
-- 授权按 role_code 种给正式岗位 SCM_FINANCE（V56 起同一口径，不硬编码 role_id），
-- 另给超管兜底；两条 t_role_menu 都带 NOT EXISTS，可重入。
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1500, '财务管理', 1, 0, 1500, '/scm-finance', NULL, NULL, NULL, NULL, 'AccountBookOutlined',
        NULL, false, 1) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1521, '收款登记', 3, 1500, 1521, NULL, NULL, 1,
        'scm:finance:receipt:add', 'scm:finance:receipt:add', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 超管兜底。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1500, 1521)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

-- 正式岗位：财务岗可登记收款。岗位不存在（未跑过 V56 的环境）时自然 0 行，不报错。
-- 与 V56 / V64 同形：按 role_code 种，不硬编码 role_id。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
         CROSS JOIN LATERAL unnest(ARRAY[1500::bigint, 1521::bigint]) AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 显式主键插入不会推进 menu_id 序列，必须补到 MAX+1，否则后续在管理界面新建菜单会撞主键。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
