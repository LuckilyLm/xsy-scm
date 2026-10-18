-- 绑定只能挂在有效包装码上：行锁之外的数据库兜底。
--
-- 服务层已经用「包装码行锁」把绑定与作废串行化，但锁只在应用路径上有效；
-- 任何旁路写入（手工 SQL、未来的批量导入）都必须被数据库拒绝，否则会出现「已作废的码挂着绑定」。

CREATE FUNCTION xsy_reject_binding_of_void_unit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    unit_status VARCHAR(16);
BEGIN
    SELECT status INTO unit_status FROM xsy_trace_unit WHERE id = NEW.unit_id;
    IF unit_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'cannot bind a void trace unit' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_trace_unit_binding_active
    BEFORE INSERT ON xsy_trace_unit_binding
    FOR EACH ROW EXECUTE FUNCTION xsy_reject_binding_of_void_unit();
