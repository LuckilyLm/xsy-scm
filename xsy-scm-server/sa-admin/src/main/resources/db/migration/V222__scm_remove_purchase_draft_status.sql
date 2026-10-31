-- 采购单取消草稿状态：新建即提交，价格窗口与自动锁单从建单时刻起算。
--
-- 存量草稿原样转为已提交，不删除任何采购单；已提交时间缺失时用创建时间补，价格窗口从同一时点启用。

UPDATE xsy_purchase_order
SET status = 'SUBMITTED',
    submitted_at = COALESCE(submitted_at, created_at, CURRENT_TIMESTAMP),
    auto_lock_at = COALESCE(auto_lock_at, COALESCE(submitted_at, created_at, CURRENT_TIMESTAMP) + INTERVAL '7 days')
WHERE status = 'DRAFT';

UPDATE xsy_purchase_order_item i
SET price_window_started_at = o.submitted_at,
    updated_at = CURRENT_TIMESTAMP
FROM xsy_purchase_order o
WHERE i.purchase_order_id = o.id
    AND i.deleted = FALSE
    AND i.price_pending = TRUE
    AND i.price_window_started_at IS NULL;

ALTER TABLE xsy_purchase_order DROP CONSTRAINT ck_purchase_order_status;
ALTER TABLE xsy_purchase_order
    ADD CONSTRAINT ck_purchase_order_status CHECK (status IN (
        'SUBMITTED', 'ACCOUNTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED', 'CANCELLED'));

-- 「提交」动作随草稿一并取消：新建即为已提交。
UPDATE t_menu
SET visible_flag = FALSE,
    disabled_flag = TRUE,
    deleted_flag = TRUE
WHERE menu_id = 724 AND api_perms = 'scm:purchase:submit';

DELETE FROM t_role_menu rm
USING t_menu m
WHERE rm.menu_id = m.menu_id
  AND m.menu_id = 724
  AND m.deleted_flag = TRUE;
