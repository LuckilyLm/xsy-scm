-- 销售订单取消草稿状态：新建即进入待确认，创建时即锁定报价。
--
-- 存量草稿按创建时的草稿报价补齐锁定单价、行金额与标品实数量（此前提交时才会做），
-- 然后原地转为待确认，不删除任何订单。

UPDATE xsy_sales_order_item i
SET locked_unit_price = i.draft_unit_price,
    locked_price_source = i.draft_price_source,
    locked_price_source_id = i.draft_price_source_id,
    ordered_line_amount = ROUND(i.ordered_quantity * i.draft_unit_price, 4)
FROM xsy_sales_order o
WHERE i.order_id = o.id
    AND o.status = 'DRAFT'
    AND i.deleted = FALSE
    AND i.locked_unit_price IS NULL;

UPDATE xsy_sales_order_item i
SET actual_quantity = i.ordered_quantity,
    actual_quantity_source = 'SYSTEM'
FROM xsy_sales_order o
WHERE i.order_id = o.id
    AND o.status = 'DRAFT'
    AND i.deleted = FALSE
    AND i.product_type_snapshot = 'STANDARD'
    AND i.actual_quantity IS NULL;

UPDATE xsy_sales_order o
SET ordered_total_amount = COALESCE(
        (SELECT SUM(i.ordered_line_amount)
         FROM xsy_sales_order_item i
         WHERE i.order_id = o.id AND i.deleted = FALSE), 0)
WHERE o.status = 'DRAFT';

UPDATE xsy_sales_order
SET status = 'PENDING',
    submitted_at = COALESCE(submitted_at, CURRENT_TIMESTAMP)
WHERE status = 'DRAFT';

ALTER TABLE xsy_sales_order DROP CONSTRAINT ck_sales_order_status;
ALTER TABLE xsy_sales_order
    ADD CONSTRAINT ck_sales_order_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'));

-- 「提交」动作随草稿一并取消：新建即为待确认，确认入口独立保留。
UPDATE t_menu
SET visible_flag = FALSE,
    disabled_flag = TRUE,
    deleted_flag = TRUE
WHERE menu_id = 614 AND api_perms = 'scm:order:submit';

DELETE FROM t_role_menu rm
USING t_menu m
WHERE rm.menu_id = m.menu_id
  AND m.menu_id = 614
  AND m.deleted_flag = TRUE;
