ALTER TABLE xsy_sales_order DROP CONSTRAINT ck_sales_order_status;
ALTER TABLE xsy_sales_order ADD CONSTRAINT ck_sales_order_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'DISPATCHED', 'SIGNED', 'EXCEPTION', 'CANCELLED'));

-- 只提升已有已确认订单；发车和签收阶段以现存业务事实为准，不生成新的交易流水。
UPDATE xsy_sales_order o
SET status = CASE
    WHEN EXISTS (SELECT 1 FROM xsy_delivery_route_order d WHERE d.order_id = o.id AND NOT d.deleted
        AND d.fulfillment_status = 'SIGNED') THEN 'SIGNED'
    WHEN EXISTS (SELECT 1 FROM xsy_delivery_route_order d WHERE d.order_id = o.id AND NOT d.deleted
        AND d.fulfillment_status = 'EXCEPTION') THEN 'EXCEPTION'
    ELSE 'DISPATCHED' END,
    version = version + 1
WHERE o.status = 'CONFIRMED' AND (o.dispatched_at IS NOT NULL OR EXISTS (
    SELECT 1 FROM xsy_delivery_route_order d WHERE d.order_id = o.id AND NOT d.deleted
        AND d.fulfillment_status IN ('SIGNED', 'EXCEPTION')));

COMMENT ON COLUMN xsy_sales_order.status IS '订单唯一当前状态：待确认、已确认、已发车、已签收、签收异常、已取消';

ALTER TABLE xsy_order_tag_link ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN xsy_order_tag_link.active IS '是否为当前标签；每单最多一项生效，原关联保留';
-- 旧版多标签保留最近建立的一项；其余关联停用，保留原数据。
UPDATE xsy_order_tag_link l SET active = FALSE
FROM (
    SELECT order_id, tag_id, ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY created_at DESC, tag_id DESC) AS position
    FROM xsy_order_tag_link
) ranked
WHERE l.order_id = ranked.order_id AND l.tag_id = ranked.tag_id AND ranked.position > 1;
CREATE UNIQUE INDEX uk_order_single_tag ON xsy_order_tag_link(order_id) WHERE active = TRUE;

ALTER TABLE xsy_sales_order DROP CONSTRAINT ck_sales_order_mall_order_only_confirmation;
ALTER TABLE xsy_sales_order ADD CONSTRAINT ck_sales_order_mall_order_only_confirmation
    CHECK (NOT mall_order_only OR status NOT IN ('CONFIRMED', 'DISPATCHED', 'SIGNED', 'EXCEPTION')
        OR settlement_method IS NOT NULL);
COMMENT ON TABLE xsy_order_tag_link IS '订单标签关联：每单最多一个生效标签，停用关联保留';

-- 原报表索引只覆盖未发车的已确认状态，扩为同一已确认交易集合。
DROP INDEX idx_sales_order_status_confirmed;
CREATE INDEX idx_sales_order_status_confirmed ON xsy_sales_order(confirmed_at DESC, id DESC)
    WHERE deleted = FALSE AND status IN ('CONFIRMED', 'DISPATCHED', 'SIGNED', 'EXCEPTION');
COMMENT ON INDEX idx_sales_order_status_confirmed IS '销售报表按确认日期读取已确认及配送、签收阶段订单';
