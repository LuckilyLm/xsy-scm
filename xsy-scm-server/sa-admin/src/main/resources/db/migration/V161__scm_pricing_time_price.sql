-- 时价：商品规格级、按时间段生效的临时价格。
--
-- 优先级固定在「客户类型价之后、市场价之前」：协议价是逐客约定，客户类型价是分组约定，
-- 时价是面向所有人的短期调整，市场价是兜底。时价不能覆盖前两者，否则签了协议的客户会被临时价顶掉。
--
-- 同一规格的时间段不允许重叠：重叠会让「当前时价是哪一条」没有唯一答案。

CREATE TABLE xsy_pricing_time_price (
    id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    unit_price NUMERIC(18, 4) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    remark VARCHAR(200),
    version INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT ck_pricing_time_price_amount CHECK (unit_price >= 0),
    CONSTRAINT ck_pricing_time_price_window CHECK (start_at < end_at)
);

CREATE INDEX idx_pricing_time_price_sku_window ON xsy_pricing_time_price (sku_id, start_at, end_at)
    WHERE deleted = FALSE;

-- 同一规格同一开始时刻只允许一条：重叠由应用层在事务内判定，这里兜住重复录入。
CREATE UNIQUE INDEX uk_pricing_time_price_start ON xsy_pricing_time_price (sku_id, start_at)
    WHERE deleted = FALSE;

-- 价格来源放开到 TIME_PRICE：下单与锁价都要能记录这个来源。
ALTER TABLE xsy_sales_order_item DROP CONSTRAINT IF EXISTS ck_sales_order_item_draft_source;
ALTER TABLE xsy_sales_order_item
    ADD CONSTRAINT ck_sales_order_item_draft_source
        CHECK (draft_price_source IN ('AGREEMENT', 'CUSTOMER_TYPE', 'TIME_PRICE', 'MARKET', 'OVERRIDE'));

ALTER TABLE xsy_sales_order_item DROP CONSTRAINT IF EXISTS ck_sales_order_item_locked_source;
ALTER TABLE xsy_sales_order_item
    ADD CONSTRAINT ck_sales_order_item_locked_source
        CHECK (locked_price_source IS NULL
            OR locked_price_source IN ('AGREEMENT', 'CUSTOMER_TYPE', 'TIME_PRICE', 'MARKET', 'OVERRIDE'));

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2121, '时价管理', 2, 501, 2121, '/pricing/time-price-list',
        '/business/scm/pricing/time-price-list.vue', 1, NULL, NULL, NULL, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2122, '时价维护', 3, 2121, 2122, NULL, NULL, 1,
        'scm:pricing:time-price:manage', 'scm:pricing:time-price:manage', 2121, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id IN (2121, 2122)
  AND (r.role_id = 1 OR r.role_code IN ('SCM_SALES', 'SCM_SALES_LEAD', 'SCM_PURCHASER_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
