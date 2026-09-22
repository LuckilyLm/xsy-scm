-- W6-1 static review correction: Q7 requires immutable historical movements.
-- V19's CHECK (deleted = FALSE) only rejects soft deletion; it cannot reject
-- updates to other columns, DELETE or TRUNCATE. Keep V19/V20 immutable.
CREATE FUNCTION scm_reject_inventory_movement_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'inventory_movement is append-only; % is forbidden. Append a reverse movement instead.', TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_inventory_movement_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON inventory_movement
    FOR EACH STATEMENT
    EXECUTE FUNCTION scm_reject_inventory_movement_mutation();

COMMENT ON FUNCTION scm_reject_inventory_movement_mutation()
    IS 'Q7: reject historical inventory movement mutation; corrections require new movements';
