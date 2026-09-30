-- ============================================================================
-- V71 SCM Finance R1 F1-6: publish the five implemented finance pages.
-- F1-5 query/export permissions are already present in V70; this migration only
-- opens the existing finance directory and grants the real page components.
-- ============================================================================

UPDATE t_menu
SET visible_flag = TRUE
WHERE menu_id = 1500
  AND menu_type = 1
  AND deleted_flag = FALSE;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES
    (1501, '应收管理', 2, 1500, 1501, '/finance/receivables', '/business/scm/finance/finance-receivable-list.vue',
     NULL, NULL, NULL, NULL, NULL, TRUE, 1),
    (1502, '应付管理', 2, 1500, 1502, '/finance/payables', '/business/scm/finance/finance-payable-list.vue',
     NULL, NULL, NULL, NULL, NULL, TRUE, 1),
    (1503, '收款管理', 2, 1500, 1503, '/finance/receipts', '/business/scm/finance/finance-receipt-list.vue',
     NULL, NULL, NULL, NULL, NULL, TRUE, 1),
    (1504, '付款管理', 2, 1500, 1504, '/finance/payments', '/business/scm/finance/finance-payment-list.vue',
     NULL, NULL, NULL, NULL, NULL, TRUE, 1),
    (1505, '核销管理', 2, 1500, 1505, '/finance/write-offs', '/business/scm/finance/finance-write-off-list.vue',
     NULL, NULL, NULL, NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (1501, 1502, 1503, 1504, 1505)
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
         CROSS JOIN LATERAL unnest(ARRAY[1501::bigint, 1502::bigint, 1503::bigint, 1504::bigint, 1505::bigint]) AS m(menu_id)
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
