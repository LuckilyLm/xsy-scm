-- V63: 配送 L3 履约数据地基（发车正式出库 + 订单级签收），2026-09-25。
--
-- 背景：L0–L2 只做 DRAFT / PLANNED / CANCELLED，`DISPATCHED` 与 `COMPLETED` 自 V42 起就只是
-- CHECK 里预留的取值，全库没有任何写入路径；`delivery_route.outbound_id` 同样只是占位。
-- 本迁移把这些占位变成可用，做四件事，全部是**新增列 / 新增约束 / 新增索引**，不改任何既有行：
--   1. `inventory_outbound_item` 补销售订单行来源 —— 主线计划要求的「订单 / 配送 / 出库互相追溯」
--      缺的就是这一段，P1 第 21 条「已产生真实出库则禁止 REOPEN」也是因此在 P1 不具备判据；
--   2. `inventory_outbound` 补来源单据，并在库上钉住「一条线路最多一张出库单」；
--   3. `delivery_route_order` 补订单级履约状态与签收事实；
--   4. `delivery_route` 补发车与完成时点，并把「状态到了就必须有时点」下沉成 CHECK。
--
-- 口径来源：`docs/decisions.md`「P2 物流配送 L3 裁决」第 3、6、7、9、12 条。

-- ---------------------------------------------------------------------------
-- 1. 出库明细：销售订单行来源
-- ---------------------------------------------------------------------------
-- 两列成对出现：手工出库单没有订单行来源（保持 NULL），发车生成的出库单一行必带一行。
-- **同 SKU 的不同订单行不合并**，因此这里是一行对一行，不是聚合关系；
-- 合并会让「哪张订单实发了多少」这件事在库里失去答案。
ALTER TABLE inventory_outbound_item ADD COLUMN sales_order_id      BIGINT;
ALTER TABLE inventory_outbound_item ADD COLUMN sales_order_item_id BIGINT;

ALTER TABLE inventory_outbound_item ADD CONSTRAINT ck_inventory_outbound_item_source_pair CHECK (
    (sales_order_id IS NULL AND sales_order_item_id IS NULL)
    OR (sales_order_id IS NOT NULL AND sales_order_item_id IS NOT NULL)
);

-- 追溯与「该行是否已真实出库」判定的查找路径。刻意**不建唯一索引**：
-- 一条订单行将来可能被手工出库或补单再出一行，唯一性由配送侧
-- `uk_delivery_order_active`（一张订单同时只能挂在一条线路上）保证，不在这里另设锚点。
CREATE INDEX idx_inventory_outbound_item_order_line
    ON inventory_outbound_item (sales_order_item_id)
    WHERE deleted = FALSE AND sales_order_item_id IS NOT NULL;

COMMENT ON COLUMN inventory_outbound_item.sales_order_id      IS '来源销售订单 id；手工出库单为 NULL，与 sales_order_item_id 成对';
COMMENT ON COLUMN inventory_outbound_item.sales_order_item_id IS '来源销售订单行 id，出库量即该行实发量；同 SKU 不同订单行不合并';

-- ---------------------------------------------------------------------------
-- 2. 出库单：来源单据
-- ---------------------------------------------------------------------------
-- 与 inventory_reservation / inventory_movement 的 source_document_* 同一套命名与语义。
ALTER TABLE inventory_outbound ADD COLUMN source_document_type VARCHAR(64);
ALTER TABLE inventory_outbound ADD COLUMN source_document_id   BIGINT;

ALTER TABLE inventory_outbound ADD CONSTRAINT ck_inventory_outbound_source_pair CHECK (
    (source_document_type IS NULL AND source_document_id IS NULL)
    OR (source_document_type IS NOT NULL AND btrim(source_document_type) <> '' AND source_document_id IS NOT NULL)
);

-- 一条来源单据只能有一张未删除的出库单。发车并发的最终兜底：
-- 线路行锁之外的任何路径想再生成第二张，都会在库里撞死。
CREATE UNIQUE INDEX uk_inventory_outbound_source_active
    ON inventory_outbound (source_document_type, source_document_id)
    WHERE deleted = FALSE AND source_document_id IS NOT NULL;

COMMENT ON COLUMN inventory_outbound.source_document_type IS '来源单据类型，发车生成为 DELIVERY_ROUTE；手工出库为 NULL';
COMMENT ON COLUMN inventory_outbound.source_document_id   IS '来源单据 id（如 delivery_route.id），用于反向跳转与防重';

-- ---------------------------------------------------------------------------
-- 3. 线路订单：订单级履约状态与签收事实
-- ---------------------------------------------------------------------------
-- fulfillment_status 与既有的 assignment_status 是**正交**两件事：
-- 前者是「货到没到手」，后者是「这单还在不在线路上」。不合并成一列，
-- 也不拿 RELEASED 表达签收 —— 取消线路会写 RELEASED，那与拒收不是同一个事实。
ALTER TABLE delivery_route_order ADD COLUMN fulfillment_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE delivery_route_order ADD COLUMN signed_at          TIMESTAMPTZ;
ALTER TABLE delivery_route_order ADD COLUMN signed_by          VARCHAR(64);
ALTER TABLE delivery_route_order ADD COLUMN sign_reason        VARCHAR(500);

ALTER TABLE delivery_route_order ADD CONSTRAINT ck_delivery_order_fulfillment CHECK (
    fulfillment_status IN ('PENDING', 'IN_TRANSIT', 'SIGNED', 'EXCEPTION')
);

-- 终态必须留下签收时刻与操作人；SIGNED 也允许填备注（例如「客户不在，邻居代收」），
-- 所以只有 EXCEPTION 强制要求原因，见下一条。
ALTER TABLE delivery_route_order ADD CONSTRAINT ck_delivery_order_sign_record CHECK (
    fulfillment_status IN ('PENDING', 'IN_TRANSIT')
    OR (signed_at IS NOT NULL AND signed_by IS NOT NULL AND btrim(signed_by) <> '')
);

-- 异常签收必填原因：拒收 / 破损 / 缺货争议在库里不能只有一个状态码。
ALTER TABLE delivery_route_order ADD CONSTRAINT ck_delivery_order_exception_reason CHECK (
    fulfillment_status <> 'EXCEPTION' OR btrim(COALESCE(sign_reason, '')) <> ''
);

COMMENT ON COLUMN delivery_route_order.fulfillment_status IS '订单级履约状态：PENDING 未发车 / IN_TRANSIT 已发车在途 / SIGNED 已签收 / EXCEPTION 异常签收（含拒收）';
COMMENT ON COLUMN delivery_route_order.signed_at          IS '签收或异常登记的时点，由服务端写入';
COMMENT ON COLUMN delivery_route_order.signed_by          IS '签收或异常登记的操作人';
COMMENT ON COLUMN delivery_route_order.sign_reason        IS '异常签收必填原因；正常签收可留空备注';

-- ---------------------------------------------------------------------------
-- 4. 线路：发车与完成时点
-- ---------------------------------------------------------------------------
-- 与 V47 打印计次同一口径：操作人 / 时点落在被操作的那一行上，不另建执行记录表。
ALTER TABLE delivery_route ADD COLUMN dispatched_at TIMESTAMPTZ;
ALTER TABLE delivery_route ADD COLUMN dispatched_by VARCHAR(64);
ALTER TABLE delivery_route ADD COLUMN completed_at  TIMESTAMPTZ;
ALTER TABLE delivery_route ADD COLUMN completed_by  VARCHAR(64);

-- 「状态到了却没留下时点」只能来自绕过服务端的写入，因此值得让库来拦：
-- 任何新增的状态迁移入口（含将来的批量作业）都必须成对写这四列之一。
ALTER TABLE delivery_route ADD CONSTRAINT ck_delivery_route_dispatched CHECK (
    status NOT IN ('DISPATCHED', 'COMPLETED')
    OR (dispatched_at IS NOT NULL AND dispatched_by IS NOT NULL AND btrim(dispatched_by) <> '')
);
ALTER TABLE delivery_route ADD CONSTRAINT ck_delivery_route_completed CHECK (
    status <> 'COMPLETED'
    OR (completed_at IS NOT NULL AND completed_by IS NOT NULL AND btrim(completed_by) <> '')
);

COMMENT ON COLUMN delivery_route.dispatched_at IS '发车时刻；出库单已在同一事务内产生';
COMMENT ON COLUMN delivery_route.dispatched_by IS '发车操作人';
COMMENT ON COLUMN delivery_route.completed_at  IS '线路完成时刻，要求全部活动订单已进入 SIGNED / EXCEPTION';
COMMENT ON COLUMN delivery_route.completed_by  IS '线路完成操作人';
