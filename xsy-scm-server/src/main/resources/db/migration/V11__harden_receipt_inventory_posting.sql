ALTER TABLE purchase_receipt_confirmation
    ALTER COLUMN idempotency_key TYPE VARCHAR(200);

ALTER TABLE purchase_order_item
    ADD CONSTRAINT ck_purchase_order_item_received_not_over_planned
    CHECK (received_quantity <= planned_quantity);

CREATE OR REPLACE FUNCTION reject_inventory_movement_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'inventory movements are append-only';
END;
$$;

CREATE TRIGGER trg_inventory_movement_append_only
    BEFORE UPDATE OR DELETE ON inventory_movement
    FOR EACH ROW
    EXECUTE FUNCTION reject_inventory_movement_mutation();
