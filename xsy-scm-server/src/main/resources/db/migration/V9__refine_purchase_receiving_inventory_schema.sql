ALTER TABLE purchase_operation_log
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by VARCHAR(64),
    ADD CONSTRAINT ck_purchase_operation_log_version CHECK (version >= 0);

ALTER TABLE purchase_receipt_item
ALTER
COLUMN actual_weight TYPE NUMERIC,
    DROP
CONSTRAINT ck_purchase_receipt_item_weight_source,
    ADD CONSTRAINT ck_purchase_receipt_item_weight_source CHECK (
        weighing_source IS NULL OR weighing_source = 'MANUAL'
    );

ALTER TABLE receipt_weighing_record
ALTER
COLUMN raw_reading TYPE NUMERIC,
    ALTER
COLUMN confirmed_reading TYPE NUMERIC,
    ALTER
COLUMN scale_precision TYPE NUMERIC,
    DROP
CONSTRAINT ck_receipt_weighing_record_source,
    ADD CONSTRAINT ck_receipt_weighing_record_source CHECK (source = 'MANUAL');

ALTER TABLE inventory
DROP
CONSTRAINT ck_inventory_quantity;

ALTER TABLE inventory_movement
DROP
CONSTRAINT ck_inventory_movement_source_type,
    DROP
CONSTRAINT ck_inventory_movement_quantities,
    ADD CONSTRAINT ck_inventory_movement_source_pair CHECK (
        movement_type <> 'PURCHASE_IN' OR source_document_type = 'PURCHASE_RECEIPT'
    ),
    ADD CONSTRAINT ck_inventory_movement_quantities CHECK (
        quantity_after = quantity_before + quantity_change
    );
