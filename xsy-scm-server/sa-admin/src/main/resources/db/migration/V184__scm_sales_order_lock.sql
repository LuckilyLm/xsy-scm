-- 锁单：发货出库后不立即锁死，先允许授权人员改单，再由财务/管理员手动锁单或 7 天后自动锁死。
--
-- 订单状态图不增加「已锁定」：锁只是修改权限的开关，状态仍是 CONFIRMED，
-- 配送、对账、商城展示都不需要重新判断一个状态值。

ALTER TABLE xsy_sales_order
    ADD COLUMN dispatched_at TIMESTAMPTZ,
    ADD COLUMN locked_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(64),
    ADD COLUMN lock_reason VARCHAR(32),
    ADD COLUMN auto_lock_at TIMESTAMPTZ;

COMMENT ON COLUMN xsy_sales_order.dispatched_at IS '最近一次发车出库时间；由此起算 7 天自动锁单';
COMMENT ON COLUMN xsy_sales_order.locked_flag IS '锁单标记：TRUE 后改单需要独立权限';
COMMENT ON COLUMN xsy_sales_order.lock_reason IS 'MANUAL 手动锁单；AUTO_7_DAYS 发车满 7 天自动锁单';

ALTER TABLE xsy_sales_order
    ADD CONSTRAINT ck_sales_order_lock_state CHECK (
        (locked_flag = FALSE AND locked_at IS NULL AND locked_by IS NULL)
            OR (locked_flag = TRUE AND locked_at IS NOT NULL AND locked_by IS NOT NULL)
        );

CREATE INDEX idx_sales_order_auto_lock ON xsy_sales_order (dispatched_at)
    WHERE deleted = FALSE
        AND locked_flag = FALSE
        AND dispatched_at IS NOT NULL;

ALTER TABLE xsy_order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE xsy_order_operation_log ADD CONSTRAINT ck_order_operation_log_type CHECK (operation_type IN (
    'CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY', 'CONFIRM', 'CANCEL',
    'RESERVE_STOCK', 'RETURN', 'REFUND', 'CREDIT_OVERRIDE', 'ASSIGN', 'REPRICE',
    'LOCK', 'UNLOCK', 'AUTO_LOCK', 'EDIT_AFTER_DISPATCH'));

-- 权限：锁单/解锁由财务与管理员办理；发车后改单、锁后改单是两条更窄的口子。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES
    (47605, '锁单', 3, 602, 47605, NULL, NULL, 1, 'scm:order:lock', 'scm:order:lock', 602, TRUE, 1),
    (47606, '解锁', 3, 602, 47606, NULL, NULL, 1, 'scm:order:unlock', 'scm:order:unlock', 602, TRUE, 1),
    (47607, '发车后改单', 3, 602, 47607, NULL, NULL, 1,
     'scm:order:edit-after-dispatch', 'scm:order:edit-after-dispatch', 602, TRUE, 1),
    (47608, '锁单后改单', 3, 602, 47608, NULL, NULL, 1,
     'scm:order:edit-locked', 'scm:order:edit-locked', 602, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM t_role r
    JOIN t_menu m ON m.menu_id BETWEEN 47605 AND 47608
WHERE r.role_code IN ('SUPER_ADMIN', 'SCM_FINANCE')
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 显式 menu_id 不推进 identity 序列；校正到当前最大值之后。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
