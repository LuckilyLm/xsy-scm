-- Backfill V27's additive columns for databases upgraded from earlier schemas.
-- Derive historical values before tightening constraints so existing rows remain valid.

UPDATE purchase_demand
SET original_required_quantity = required_quantity,
    inventory_deducted_quantity = 0
WHERE original_required_quantity IS DISTINCT FROM required_quantity
   OR inventory_deducted_quantity IS DISTINCT FROM 0;

UPDATE purchase_receipt_item ri
SET planned_quantity = poi.planned_quantity,
    cumulative_received_quantity = COALESCE(ri.received_quantity, 0),
    remaining_quantity = GREATEST(poi.planned_quantity - COALESCE(ri.received_quantity, 0), 0),
    over_receipt_quantity = GREATEST(COALESCE(ri.received_quantity, 0) - poi.planned_quantity, 0),
    receipt_difference = COALESCE(ri.received_quantity, 0) - poi.planned_quantity
FROM purchase_order_item poi
WHERE poi.id = ri.purchase_order_item_id
  AND (ri.planned_quantity IS NULL
    OR ri.cumulative_received_quantity IS DISTINCT FROM COALESCE(ri.received_quantity, 0)
    OR ri.remaining_quantity IS DISTINCT FROM GREATEST(poi.planned_quantity - COALESCE(ri.received_quantity, 0), 0)
    OR ri.over_receipt_quantity IS DISTINCT FROM GREATEST(COALESCE(ri.received_quantity, 0) - poi.planned_quantity, 0)
    OR ri.receipt_difference IS DISTINCT FROM COALESCE(ri.received_quantity, 0) - poi.planned_quantity);

-- Rows without a matching order item cannot be made semantically compatible.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM purchase_receipt_item WHERE planned_quantity IS NULL) THEN
        RAISE EXCEPTION 'Cannot backfill purchase_receipt_item.planned_quantity: missing purchase_order_item';
    END IF;
END $$;

ALTER TABLE purchase_receipt_item ALTER COLUMN planned_quantity SET NOT NULL;
ALTER TABLE purchase_receipt_item
    ADD CONSTRAINT ck_purchase_receipt_item_reconciliation CHECK (
        planned_quantity > 0
        AND cumulative_received_quantity >= 0
        AND remaining_quantity = GREATEST(planned_quantity - cumulative_received_quantity, 0)
        AND over_receipt_quantity = GREATEST(cumulative_received_quantity - planned_quantity, 0)
        AND receipt_difference = cumulative_received_quantity - planned_quantity
    );
