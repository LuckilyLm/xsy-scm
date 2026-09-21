-- D-1 approved fix (plan A). Immutable after first successful Flyway application.
-- PostgreSQL only. No foreign keys.
--
-- t_oa_enterprise_employee.enterprise_id / employee_id were created as VARCHAR(100)
-- by the upstream SmartAdmin v3.31 MySQL baseline, while the Java entity declares both
-- as Long and the referenced columns (t_oa_enterprise.enterprise_id, t_employee.employee_id)
-- are BIGINT. MySQL silently coerced varchar <-> bigint in comparisons and joins;
-- PostgreSQL does not, so every read/delete path of the OA enterprise-employee feature
-- failed with: operator does not exist: character varying = bigint.
--
-- Fixed at the schema root rather than by adding CAST() workarounds in the mapper:
-- CAST on a WHERE/JOIN column would make idx_* and the unique key unusable.
--
-- The table is empty in every environment (V5 seeds no rows), so the USING conversion
-- cannot lose data. The unique key and both indexes are rebuilt automatically with the
-- new column type.

ALTER TABLE t_oa_enterprise_employee
    ALTER COLUMN enterprise_id TYPE BIGINT
        USING enterprise_id::BIGINT,
    ALTER COLUMN employee_id TYPE BIGINT
        USING employee_id::BIGINT;

COMMENT ON COLUMN t_oa_enterprise_employee.enterprise_id IS '企业ID';
COMMENT ON COLUMN t_oa_enterprise_employee.employee_id IS '员工ID';
