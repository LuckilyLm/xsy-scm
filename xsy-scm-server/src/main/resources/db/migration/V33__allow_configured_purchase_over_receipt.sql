-- P1 允许在可配置容差内超收，不再由旧的“不得超过计划量”约束阻断。
ALTER TABLE purchase_order_item
    DROP CONSTRAINT IF EXISTS ck_purchase_order_item_received_not_over_planned;
