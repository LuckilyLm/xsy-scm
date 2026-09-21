-- V34: 移动加权成本核算（inventory_balance.avg_cost），2026-09-20。
--
-- 背景：这是库存深化的**最后一项**。前面七波把「数量账」做完了，本波次补「金额账」。
--
-- Q3 的既有裁决是「成本事实由 movement 的 unit_cost 承载，余额表不加 avg_cost」。
-- 本波次**改变这条裁决**，理由是 movement 的 unit_cost 不足以表达移动加权：
--   * 移动加权是**顺序相关**的：均价取决于全部历史出入库的先后；
--   * 出库时的成本必须在**出库那一刻**确定，事后从流水反推需要重放整条历史，
--     而重放的前提是所有出库流水都已经带着成本 —— 那是本波次要建立的东西，鸡生蛋。
-- 因此新增 `avg_cost` 列，它由六条写入路径在**持有余额行锁之后**维护。
--
-- 口径（本波次定下，写进 docs/decisions.md）：
--   1. **入库（PURCHASE_IN）**：newAvg = (oldQty·oldAvg + inQty·inCost) / (oldQty + inQty)，
--      inCost 取采购单价（movement.unit_cost）。四舍五入到 4 位小数。
--   2. **出库**：**avg_cost 不变**（移动加权平均的性质：出库不改变均价），
--      但**流水必须写当时的 avg_cost** —— 这是本波次的核心语义变更：
--      出库 / 盘点 / 报损报溢 / 调拨转出 / 转换转出的流水，unit_cost 从 NULL 变成有值。
--   3. **除采购入库外的其它入库**（TRANSFER_IN / CONVERT_IN / GAIN_REPORT）：
--      按**该 (仓库, SKU) 当前的 avg_cost** 入账（等价于均价不变）。
--      理由：这些动作不带来新的「采购价格信息」，按现有均价入账是**最保守**的选择 ——
--      不凭空创造损益。
--      **已知近似**：调拨 / 转换的成本平移不精确（源仓均价 10、目标仓均价 8 时，
--      转入按 8 而不是 10 入账）。要精确平移需要在明细行上快照源成本，
--      属后续增强；已登记为未决事项。
--   4. **存量流水的 unit_cost 不回填**：Q7 append-only 禁止改历史行。
--      因此本波次上线后，历史出库流水的 unit_cost 仍是 NULL —— 那**不是缺陷**，
--      而是「该笔出库发生在成本核算上线之前」这一事实的忠实记录。
--      对账时要按此解释，不要试图补齐。
--
-- 期初 avg_cost 的取值（**本波次采用的默认口径**）：
--   取该 (仓库, SKU) **最近一次 PURCHASE_IN 流水的 unit_cost**；没有采购入库则为 0。
--   选它的理由：它是唯一一个「有真实采购价格事实支撑」的起点，
--   且不需要引入任何新的输入数据。副作用是「最近一次采购价」会覆盖更早的价格，
--   对历史库存的估价偏保守（不虚高）。若业务要求更精确的期初（例如按最近一次
--   采购价 × 存量、或按财务指定价），需要另开一次数据修正迁移。

-- ---------------------------------------------------------------------------
-- 1. 余额表加成本列
-- ---------------------------------------------------------------------------
-- NOT NULL DEFAULT 0：现有行先拿 0 占位，紧接着用下面的回填覆盖。
-- 不允许 NULL 是因为「没有均价」与「均价为 0」在本业务里无法区分，
-- 而让调用方每次都判空会让六条写入路径都多一层分支。
ALTER TABLE inventory_balance
    ADD COLUMN avg_cost NUMERIC(18, 4) NOT NULL DEFAULT 0;

-- 成本不为负（DB 层兜底；服务层在加权计算时也会保证）
ALTER TABLE inventory_balance
    ADD CONSTRAINT ck_inventory_balance_avg_cost
        CHECK (avg_cost >= 0);

COMMENT ON COLUMN inventory_balance.avg_cost IS
    '移动加权平均成本（每记账单位）。入库时按 (旧量·旧均价 + 入量·入价)/新量 重算；出库不变但会写入流水 unit_cost';

-- ---------------------------------------------------------------------------
-- 2. 期初回填：最近一次采购入库单价，无则 0
-- ---------------------------------------------------------------------------
-- 一次性步骤。只读 inventory_movement（不改历史行），只写 inventory_balance。
UPDATE inventory_balance b
SET avg_cost = COALESCE((SELECT m.unit_cost
                         FROM inventory_movement m
                         WHERE m.warehouse_id = b.warehouse_id
                           AND m.sku_id = b.sku_id
                           AND m.movement_type = 'PURCHASE_IN'
                           AND m.unit_cost IS NOT NULL
                           AND m.deleted = FALSE
                         ORDER BY m.occurred_at DESC, m.id DESC
                         LIMIT 1), 0);

-- ---------------------------------------------------------------------------
-- 3. 对账断言：回填后不得有负均价（与 CHECK 同义，但给出更可读的失败信息）
-- ---------------------------------------------------------------------------
DO
$$
    DECLARE
        bad_count integer;
    BEGIN
        SELECT count(*) INTO bad_count FROM inventory_balance WHERE avg_cost < 0;
        IF bad_count > 0 THEN
            RAISE EXCEPTION 'V34 backfill produced % rows with negative avg_cost', bad_count;
        END IF;
    END
$$;
