-- V2 has no database foreign keys; services and transactional rules own relationship validation.
-- Remove the nine constraints added by the later reconciliation, delivery, sorting, and promotion migrations.
ALTER TABLE scm_customer_statement_item
    DROP CONSTRAINT IF EXISTS scm_customer_statement_item_statement_id_fkey;
ALTER TABLE scm_customer_statement_source
    DROP CONSTRAINT IF EXISTS scm_customer_statement_source_statement_id_fkey;
ALTER TABLE scm_supplier_statement_item
    DROP CONSTRAINT IF EXISTS scm_supplier_statement_item_statement_id_fkey;
ALTER TABLE scm_supplier_statement_source
    DROP CONSTRAINT IF EXISTS scm_supplier_statement_source_statement_id_fkey;
ALTER TABLE delivery_gps_event
    DROP CONSTRAINT IF EXISTS delivery_gps_event_route_id_fkey;
ALTER TABLE delivery_plan_proposal
    DROP CONSTRAINT IF EXISTS delivery_plan_proposal_route_id_fkey;
ALTER TABLE sorting_scale_event
    DROP CONSTRAINT IF EXISTS sorting_scale_event_task_id_fkey;
ALTER TABLE sorting_scale_event
    DROP CONSTRAINT IF EXISTS sorting_scale_event_task_item_id_fkey;
ALTER TABLE promotion_coupon_instance
    DROP CONSTRAINT IF EXISTS promotion_coupon_instance_coupon_id_fkey;
