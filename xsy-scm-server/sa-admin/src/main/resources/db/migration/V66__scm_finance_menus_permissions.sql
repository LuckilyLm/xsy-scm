-- ============================================================================
-- P3 Finance R1 财务管理菜单与权限点（data-only）
--
-- 裁决依据 docs/decisions.md「P3 Finance R1 裁决」第二批 Q20 / Q24 与
-- 「第三批正式裁决」D-3；正式设计见 docs/plan/finance-r1-design.md §16。
--
-- 编号：实测迁移种子里 t_menu 最大 menu_id = 1421（V61 分拣段），1422-1499 与 1500+ 空闲，
-- 本段取 1500-1531，与分拣段留出整段间隔。动手前已重新 git fetch 并重扫，不假定号段空闲。
-- 遵守既有四条约定：menu_id == sort、context_menu_id == parent_id、api_perms == web_perms、
-- perms_type = 1；本迁移只授 SUPER_ADMIN(role_id=1)，正式角色授权在 V67 按 role_code 种。
--
-- 权限点粒度取向：
--   * 五个查询点各一条（应收 / 应付 / 收款 / 付款 / 核销），不合成一个 scm:finance:query ——
--     页面与导出端点都要求「对应 *:query AND scm:finance:export」，合成一个点就没法分开授。
--   * 破坏性动作各自独立成点（Q20）：反向核销、手工红字应付、反向收款、反向付款。
--     D-3 的两个反向点**不与 *:add 合并、也不合成一个 scm:finance:reverse** ——
--     能登一笔款的人不必然是能冲掉一笔款的人。
--   * 生成器（应收 / 应付 / 红字应收）**不挂权限点**：它们是业务事务内的派生写，
--     权限由触发命令（签收 / 收货确认 / 退货批准）的既有权限承担。
--   * D-1 不回填，因此**没有任何历史补生成权限点**。
--   * 不新增金额字段级权限与 masking（Q24）：有对应 *:query 即可见金额。
--     成本可见性仍由既有 scm:report:cost:query 管报表域，财务域不借用。
-- ============================================================================

-- 一级目录
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1500, '财务管理', 1, 0, 1500, '/finance', NULL, NULL, NULL, NULL, 'DollarOutlined', NULL, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 五个业务页
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1501, '应收管理', 2, 1500, 1501, '/finance/receivables',
        '/business/scm/finance/finance-receivable-list.vue', NULL, NULL, NULL, 'AccountBookOutlined', NULL, true, 1),
       (1502, '应付管理', 2, 1500, 1502, '/finance/payables',
        '/business/scm/finance/finance-payable-list.vue', NULL, NULL, NULL, 'BankOutlined', NULL, true, 1),
       (1503, '收款管理', 2, 1500, 1503, '/finance/receipts',
        '/business/scm/finance/finance-receipt-list.vue', NULL, NULL, NULL, 'InboxOutlined', NULL, true, 1),
       (1504, '付款管理', 2, 1500, 1504, '/finance/payments',
        '/business/scm/finance/finance-payment-list.vue', NULL, NULL, NULL, 'SwapOutlined', NULL, true, 1),
       (1505, '核销管理', 2, 1500, 1505, '/finance/write-offs',
        '/business/scm/finance/finance-write-off-list.vue', NULL, NULL, NULL, 'CheckSquareOutlined', NULL, true,
        1)
ON CONFLICT (menu_id) DO NOTHING;

-- 查询权限点（151x）
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1511, '应收查询', 3, 1501, 1511, NULL, NULL, 1,
        'scm:finance:receivable:query', 'scm:finance:receivable:query', NULL, 1501, true, 1),
       (1512, '应付查询', 3, 1502, 1512, NULL, NULL, 1,
        'scm:finance:payable:query', 'scm:finance:payable:query', NULL, 1502, true, 1),
       (1513, '收款查询', 3, 1503, 1513, NULL, NULL, 1,
        'scm:finance:receipt:query', 'scm:finance:receipt:query', NULL, 1503, true, 1),
       (1514, '付款查询', 3, 1504, 1514, NULL, NULL, 1,
        'scm:finance:payment:query', 'scm:finance:payment:query', NULL, 1504, true, 1),
       (1515, '核销查询', 3, 1505, 1515, NULL, NULL, 1,
        'scm:finance:write-off:query', 'scm:finance:write-off:query', NULL, 1505, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 写权限点（152x）：登记类与破坏性类分开，破坏性动作各自独立成点（Q20 / D-3）
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1521, '收款登记', 3, 1503, 1521, NULL, NULL, 1,
        'scm:finance:receipt:add', 'scm:finance:receipt:add', NULL, 1503, true, 1),
       (1522, '付款登记', 3, 1504, 1522, NULL, NULL, 1,
        'scm:finance:payment:add', 'scm:finance:payment:add', NULL, 1504, true, 1),
       (1523, '核销登记', 3, 1505, 1523, NULL, NULL, 1,
        'scm:finance:write-off:add', 'scm:finance:write-off:add', NULL, 1505, true, 1),
       (1524, '撤销核销', 3, 1505, 1524, NULL, NULL, 1,
        'scm:finance:write-off:reverse', 'scm:finance:write-off:reverse', NULL, 1505, true, 1),
       (1525, '登记红字应付', 3, 1502, 1525, NULL, NULL, 1,
        'scm:finance:payable:red', 'scm:finance:payable:red', NULL, 1502, true, 1),
       (1526, '反向收款', 3, 1503, 1526, NULL, NULL, 1,
        'scm:finance:receipt:reverse', 'scm:finance:receipt:reverse', NULL, 1503, true, 1),
       (1527, '反向付款', 3, 1504, 1527, NULL, NULL, 1,
        'scm:finance:payment:reverse', 'scm:finance:payment:reverse', NULL, 1504, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 导出权限点（1531）：跨五个页面，故挂在目录上（照 V50 的 1216 报表导出同一形态）。
-- 导出端点要求「对应 *:query AND scm:finance:export」，本点**绝不扩大查询范围**（P0 裁决 10）。
INSERT INTO t_menu (menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                    api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1531, '财务导出', 3, 1500, 1531, NULL, NULL, 1,
        'scm:finance:export', 'scm:finance:export', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 超管兜底。正式岗位的授权在 V67 里按 role_code 种，不硬编码 role_id。
INSERT INTO t_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id BETWEEN 1500 AND 1599
  AND NOT EXISTS(SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'),
              (SELECT MAX(menu_id) + 1 FROM t_menu), false);
