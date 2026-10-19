-- 时价的变更审计：谁在什么时候把哪个规格的时价改成了多少。
--
-- 协议价与客户类型价都有操作日志表，时价缺一份就会让「这个临时价是谁定的」查不到。
-- 结构对齐既有两张日志表，价格历史页用 UNION 一起查。

CREATE TABLE xsy_pricing_time_price_operation_log (
    id BIGSERIAL PRIMARY KEY,
    time_price_id BIGINT NOT NULL,
    operation_type VARCHAR(16) NOT NULL CHECK (operation_type IN ('CREATE', 'UPDATE', 'DELETE')),
    operator VARCHAR(64) NOT NULL,
    before_data JSONB,
    after_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)
);

CREATE INDEX idx_time_price_log_price ON xsy_pricing_time_price_operation_log (time_price_id);
CREATE INDEX idx_time_price_log_time ON xsy_pricing_time_price_operation_log (created_at DESC);

-- 日志只追加：审计记录能被改写就失去了意义。
CREATE OR REPLACE FUNCTION xsy_time_price_log_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'xsy_pricing_time_price_operation_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_time_price_log_append_only
    BEFORE UPDATE OR DELETE ON xsy_pricing_time_price_operation_log
    FOR EACH STATEMENT EXECUTE FUNCTION xsy_time_price_log_append_only();
