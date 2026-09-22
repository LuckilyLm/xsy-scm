-- B1 Warehouse + Receiving closure, approved 2026-09-18 (HD-B1-01 / HD-B1-02 / HD-B1-03).
-- See docs/decisions.md (receipt dual-mode, independent putaway lifecycle, warehouse enable/disable).
-- PostgreSQL; NO foreign keys (AGENTS.md). V1-V21 are untouched; this file only ADDs columns.
--
-- Scope:
--   1. purchase_receipt gains receipt_mode / putaway_status / putaway_at / putaway_by.
--   2. Historical backfill is a business-fact mapping (NOT a runtime default):
--        * CONFIRMED -> receipt_mode='DIRECT', putaway_status='COMPLETED' (W5 confirm already
--          produced PURCHASE_IN via V19 backfill, so putaway is by definition complete).
--        * DRAFT      -> receipt_mode='DIRECT', putaway_status='PENDING' (never put away).
--   3. CHECK constraints encode the dual-lifecycle invariants so that
--        purchase_receipt.status='CONFIRMED' is NOT read as "inventory already booked".
--
-- The temporary DEFAULTs exist only so ALTER succeeds on pre-existing rows; they are dropped at
-- the end. NO inventory_movement is written here; existing PURCHASE_IN history stays untouched.

ALTER TABLE purchase_receipt
    ADD COLUMN receipt_mode    VARCHAR(24) NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN putaway_status  VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN putaway_at      TIMESTAMPTZ,
    ADD COLUMN putaway_by      VARCHAR(64);

-- ---------------------------------------------------------------------------
-- Historical backfill (business-fact mapping, see above)
-- ---------------------------------------------------------------------------
UPDATE purchase_receipt
   SET receipt_mode   = 'DIRECT',
       putaway_status = 'COMPLETED',
       putaway_at     = confirmed_at,
       putaway_by     = operator
 WHERE status = 'CONFIRMED';

UPDATE purchase_receipt
   SET receipt_mode   = 'DIRECT',
       putaway_status = 'PENDING',
       putaway_at     = NULL,
       putaway_by     = NULL
 WHERE status = 'DRAFT';

-- Drop the temporary defaults: from now on receipt_mode / putaway_status are explicit only.
ALTER TABLE purchase_receipt
    ALTER COLUMN receipt_mode   DROP DEFAULT,
    ALTER COLUMN putaway_status DROP DEFAULT;

-- ---------------------------------------------------------------------------
-- Dual-lifecycle invariants
-- ---------------------------------------------------------------------------
ALTER TABLE purchase_receipt
    ADD CONSTRAINT ck_purchase_receipt_mode
        CHECK (receipt_mode IN ('DIRECT', 'WAREHOUSE_CONFIRM')),
    ADD CONSTRAINT ck_purchase_receipt_putaway_status
        CHECK (putaway_status IN ('PENDING', 'COMPLETED')),
    -- DRAFT 未入库：入库状态必须 PENDING，且无入库时间 / 入库人。
    ADD CONSTRAINT ck_purchase_receipt_draft_putaway CHECK (
        status <> 'DRAFT'
        OR (putaway_status = 'PENDING' AND putaway_at IS NULL AND putaway_by IS NULL)),
    -- PENDING 不得携带入库时间 / 入库人。
    ADD CONSTRAINT ck_purchase_receipt_putaway_pending CHECK (
        putaway_status <> 'PENDING' OR (putaway_at IS NULL AND putaway_by IS NULL)),
    -- COMPLETED 必须携带入库时间 / 入库人。
    ADD CONSTRAINT ck_purchase_receipt_putaway_completed CHECK (
        putaway_status <> 'COMPLETED'
        OR (putaway_at IS NOT NULL AND putaway_by IS NOT NULL AND btrim(putaway_by) <> '')),
    -- DIRECT：确认即入库，因此 CONFIRMED 时入库状态必须已是 COMPLETED。
    ADD CONSTRAINT ck_purchase_receipt_direct_completed CHECK (
        receipt_mode <> 'DIRECT' OR status <> 'CONFIRMED' OR putaway_status = 'COMPLETED');

-- 停用仓库时反查「该仓是否存在待入库收货单」用（HD-B1-01 第三条）。
CREATE INDEX idx_purchase_receipt_warehouse_putaway
    ON purchase_receipt (warehouse_id, putaway_status)
    WHERE deleted = FALSE AND putaway_status = 'PENDING';

-- ---------------------------------------------------------------------------
-- purchase_operation_log：新增 RECEIPT_PUTAWAY 操作类型（V15 的 CHECK 白名单扩展）。
-- V15 文件不可改，因此在新迁移里 DROP + 重建同名 CHECK，内容 = V15 原文 + RECEIPT_PUTAWAY。
-- ---------------------------------------------------------------------------
ALTER TABLE purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_type;
ALTER TABLE purchase_operation_log ADD CONSTRAINT ck_purchase_operation_log_type CHECK (operation_type IN (
    'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE',
    'DEMAND_GENERATE','DEMAND_ALLOCATE',
    'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY'));

ALTER TABLE purchase_operation_log DROP CONSTRAINT ck_purchase_operation_log_owner;
ALTER TABLE purchase_operation_log ADD CONSTRAINT ck_purchase_operation_log_owner CHECK (
    (operation_type = 'DEMAND_GENERATE'
         AND purchase_order_id IS NULL     AND purchase_receipt_id IS NULL)
 OR (operation_type = 'DEMAND_ALLOCATE'
         AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
 OR (operation_type IN ('CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE')
         AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NULL)
 OR (operation_type IN ('RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY')
         AND purchase_order_id IS NOT NULL AND purchase_receipt_id IS NOT NULL));

COMMENT ON COLUMN purchase_receipt.receipt_mode   IS '入库方式：DIRECT 确认即入库 / WAREHOUSE_CONFIRM 确认后由仓库二次入库';
COMMENT ON COLUMN purchase_receipt.putaway_status IS '入库状态：PENDING 待入库 / COMPLETED 已入库；与 status 解耦，CONFIRMED 不必然等于已入账';
COMMENT ON COLUMN purchase_receipt.putaway_at     IS '实际物理入库时间（DIRECT=confirmed_at；WAREHOUSE_CONFIRM=仓库确认时刻）';
COMMENT ON COLUMN purchase_receipt.putaway_by     IS '实际执行入库的操作人（DIRECT=confirm operator；WAREHOUSE_CONFIRM=仓库操作人）';
