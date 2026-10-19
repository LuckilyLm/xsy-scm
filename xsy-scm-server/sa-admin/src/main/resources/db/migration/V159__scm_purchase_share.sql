-- 采购单二维码分享：采购员在产地用手机接任务、回填单价与实收重量。
--
-- 分享链接本身就是凭证：令牌不透明、只存哈希、有有效期、可撤销。
-- 回填是追加事实，不直接改采购单；是否采用由后台人工确认，避免手机端误填改写已提交的单据。

CREATE TABLE xsy_purchase_share_token (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    token_prefix VARCHAR(8) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    use_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_purchase_share_token_hash ON xsy_purchase_share_token (token_hash);

-- 一张采购单同一时刻只留一条有效链接：重新分享先撤销旧的，旧码立即失效。
CREATE UNIQUE INDEX uk_purchase_share_active_order ON xsy_purchase_share_token (purchase_order_id)
    WHERE revoked_at IS NULL;

CREATE TABLE xsy_purchase_share_feedback (
    id BIGSERIAL PRIMARY KEY,
    share_token_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    unit_price NUMERIC(18, 4),
    received_quantity NUMERIC(18, 4),
    remark VARCHAR(200),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator_ip VARCHAR(64),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_share_feedback_value
        CHECK (unit_price IS NOT NULL OR received_quantity IS NOT NULL),
    CONSTRAINT ck_purchase_share_feedback_price CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT ck_purchase_share_feedback_quantity
        CHECK (received_quantity IS NULL OR received_quantity >= 0)
);

CREATE INDEX idx_purchase_share_feedback_order ON xsy_purchase_share_feedback (purchase_order_id);
CREATE INDEX idx_purchase_share_feedback_item ON xsy_purchase_share_feedback (purchase_order_item_id);

-- 回填是事实，不修改也不删除。
CREATE OR REPLACE FUNCTION xsy_purchase_share_feedback_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'xsy_purchase_share_feedback is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_purchase_share_feedback_append_only
    BEFORE UPDATE OR DELETE ON xsy_purchase_share_feedback
    FOR EACH STATEMENT EXECUTE FUNCTION xsy_purchase_share_feedback_append_only();

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2118, '采购单分享', 3, 703, 2118, NULL, NULL, 1,
        'scm:purchase:share:create', 'scm:purchase:share:create', 703, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2119, '采购单分享撤销', 3, 703, 2119, NULL, NULL, 1,
        'scm:purchase:share:revoke', 'scm:purchase:share:revoke', 703, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id IN (2118, 2119)
  AND (r.role_id = 1 OR r.role_code IN ('SCM_PURCHASER', 'SCM_PURCHASER_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
