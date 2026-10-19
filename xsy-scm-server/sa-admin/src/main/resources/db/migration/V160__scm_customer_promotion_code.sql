-- 客户专属推广二维码：扫码记录来源，后台建客户时把业务员预填成扫码归属人。
--
-- 码属于客户，归属业务员取该客户的 seller_id 快照：客户换业务员后，历史扫码仍能说清当时是谁的线索。
-- 扫码记录只追加，不修改也不删除。

CREATE TABLE xsy_customer_promotion_code (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_customer_promotion_code ON xsy_customer_promotion_code (code);

-- 一个客户同一时刻只有一条有效推广码：重新生成先撤销旧的，旧码立即失效。
CREATE UNIQUE INDEX uk_customer_promotion_active ON xsy_customer_promotion_code (customer_id)
    WHERE revoked_at IS NULL;

CREATE TABLE xsy_customer_promotion_scan (
    id BIGSERIAL PRIMARY KEY,
    code_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    seller_id BIGINT,
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_ip VARCHAR(64),
    user_agent VARCHAR(300)
);

CREATE INDEX idx_customer_promotion_scan_code ON xsy_customer_promotion_scan (code_id);
CREATE INDEX idx_customer_promotion_scan_time ON xsy_customer_promotion_scan (scanned_at DESC);

CREATE OR REPLACE FUNCTION xsy_customer_promotion_scan_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'xsy_customer_promotion_scan is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_promotion_scan_append_only
    BEFORE UPDATE OR DELETE ON xsy_customer_promotion_scan
    FOR EACH STATEMENT EXECUTE FUNCTION xsy_customer_promotion_scan_append_only();

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2120, '客户推广二维码', 3, 432, 2120, NULL, NULL, 1,
        'scm:customer:promotion:manage', 'scm:customer:promotion:manage', 432, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id = 2120
  AND (r.role_id = 1 OR r.role_code IN ('SCM_SALES', 'SCM_SALES_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
