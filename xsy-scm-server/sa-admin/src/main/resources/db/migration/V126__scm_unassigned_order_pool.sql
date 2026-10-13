-- 未分配商城订单池：新权限和内部指派日志类型，不回填或改派历史订单。
ALTER TABLE xsy_order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE xsy_order_operation_log ADD CONSTRAINT ck_order_operation_log_type CHECK (operation_type IN (
    'CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY', 'CONFIRM', 'CANCEL',
    'RESERVE_STOCK', 'RETURN', 'REFUND', 'CREDIT_OVERRIDE', 'ASSIGN'));

CREATE INDEX idx_sales_order_unassigned_mall_pool
    ON xsy_sales_order(created_at DESC, id DESC)
    WHERE deleted = FALSE AND order_source = 'MALL' AND seller_id IS NULL
        AND status IN ('DRAFT', 'PENDING', 'CONFIRMED');

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES
    (1261, '待分配订单池', 2, 601, 606, '/order/unassigned-pool',
     '/business/scm/order/unassigned-pool.vue', NULL, NULL, NULL, NULL, TRUE, 1),
    (1262, '订单指派', 3, 1261, 1262, NULL, NULL, 1,
     'scm:order:assign', 'scm:order:assign', 1261, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id FROM t_menu m WHERE m.menu_id IN (1261, 1262)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

-- 正式销售主管角色按 role_code 授权；不猜测各环境的 role_id。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE r.role_code = 'SCM_SALES_LEAD' AND m.menu_id IN (1261, 1262)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu x
        WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

UPDATE t_role SET remark = CONCAT(remark, '+ 商城未分配订单指派'), update_time = CURRENT_TIMESTAMP
WHERE role_code = 'SCM_SALES_LEAD' AND remark NOT LIKE '%商城未分配订单指派%';
