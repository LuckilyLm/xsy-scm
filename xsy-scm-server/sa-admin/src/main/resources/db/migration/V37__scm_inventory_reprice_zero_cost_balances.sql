-- V37: 修正「调拨 / 规格转换把库存成本清零」的账务缺陷（重放流水重算 avg_cost），2026-09-20。
--
-- 背景：V34 上线后，三条入方向腿里只有采购入库会按加权公式更新 `inventory_balance.avg_cost`，
-- 调拨转入与转换转入按**目标行现有均价**入账。当目标行是本次调入 / 转入现建的
-- （`insertOnConflictDoNothing` 刚插出来，`avg_cost` 默认 0）时，取到的成本就是 0 ——
-- 后果不是「精度不精确」而是**成本被整体清零**：数量对、金额账全丢。
-- 实测（2026-09-20）：转出腿 unit_cost = 6.20 / 132.00 / 2.60，转入腿全部 0.0000。
--
-- 本迁移只修**派生状态**（`avg_cost` 是活状态，不是流水的净和，允许重算）：
--   * `inventory_movement` 一行都不改、不删（Q7 / V21 的 append-only 账本，
--     历史上那些 0 成本的转入腿是「该动作发生在成本核算修正之前」的忠实记录）；
--   * 重算口径与被修正后的应用代码**逐条同源**（见 `InventoryCommandService`）：
--     1. 出方向腿：均价不变；
--     2. `PURCHASE_IN`：入价取该腿自己的 `unit_cost`；
--     3. `TRANSFER_IN`：入价取**同一明细行转出腿**（`TRANSFER_OUT_ITEM`）的 `unit_cost`；
--     4. `CONVERT_IN`：入价 = 转出腿总成本 ÷ 转入腿数量（跨单位守恒的是总成本，不是单价）；
--     5. 其余入方向（`STOCKTAKE_GAIN` / `GAIN_REPORT`）：不带来新成本事实，按现有均价入账；
--     6. 加权公式 `newAvg = (旧量·旧均价 + 入量·入价) / 新量`，四舍五入到 4 位。
--   方向判定不写类型清单，用 `after_quantity = before_quantity + quantity` 判入 ——
--   该等式由 `ck_inventory_movement_snap` 按类型分组钉死，等于把方向口径接进既有契约守卫。
--   成本基准的 CASE 只显式列出「带来新成本事实」的三种入库，其余入方向落到默认分支按现有均价
--   入账；将来新增入方向类型时默认分支是**保守**的（不凭空造价格），不会静默少算。
--
-- 取整与 Java 侧一致：金额与均价恒为非负，PostgreSQL 的 `round(numeric, 4)` 与
-- `RoundingMode.HALF_UP` 对非负数是同一个「四舍五入远离零」。
--
-- 修复范围**刻意收窄**到 `quantity > 0 AND avg_cost = 0` 的行 —— 即缺陷的症状本身：
--   1. 「源仓均价 10 / 目标仓均价 8 时转入按 8 记」这类**非零但偏差**属 V34 登记的已知近似，
--      不在本缺陷内，改它等于用新口径重写全库金额账；
--   2. 更重要的是：不加收窄的话，本迁移要对**每一行**余额断言「重放数量 == 存量数量」，
--      而共享库里有别人留下的账实不一致（V19 Step 4 的全库对账已经因此挡下过 5 个 IT）。
--      别人的破行不该让本迁移起不来，但该被本迁移修的破行必须响亮地失败。
--
-- 幂等：同一行重放两次结果相同（派生值由账本唯一决定），可安全重跑。

-- ---------------------------------------------------------------------------
-- Step 1: 重放候选行的流水并回写 avg_cost
-- ---------------------------------------------------------------------------
DO $$
    DECLARE
        target      RECORD;
        leg         RECORD;
        v_quantity  NUMERIC(18,4);
        v_avg       NUMERIC(18,4);
        v_in_cost   NUMERIC(18,4);
        v_repaired  integer := 0;
    BEGIN
        -- 候选：有货、零成本。锁定顺序固定为 (warehouse_id, sku_id) 升序，
        -- 与其它库存写入路径的锁序同一取向（迁移在启动早期执行，通常无人并发）。
        FOR target IN
            SELECT b.id, b.warehouse_id, b.sku_id, b.quantity
              FROM inventory_balance b
             WHERE b.deleted = FALSE
               AND b.quantity > 0
               AND b.avg_cost = 0
             ORDER BY b.warehouse_id, b.sku_id
        LOOP
            v_quantity := 0;
            v_avg      := 0;

            FOR leg IN
                SELECT m.movement_type, m.quantity, m.unit_cost,
                       m.before_quantity, m.after_quantity,
                       m.source_document_type, m.source_document_item_id
                  FROM inventory_movement m
                 WHERE m.deleted = FALSE
                   AND m.warehouse_id = target.warehouse_id
                   AND m.sku_id = target.sku_id
                 ORDER BY m.occurred_at, m.id
            LOOP
                IF leg.after_quantity = leg.before_quantity + leg.quantity THEN
                    -- 入方向：先按旧量·旧均价加权，再累加数量（顺序不能反，基数是入量之前的账面）
                    IF leg.movement_type = 'PURCHASE_IN' THEN
                        v_in_cost := COALESCE(leg.unit_cost, 0);
                    ELSIF leg.movement_type IN ('TRANSFER_IN', 'CONVERT_IN') THEN
                        -- 成本事实住在**同一明细行的转出腿**上（与 transferInCost /
                        -- convertedUnitCost 同一口径）；跨单位守恒的是总成本，不是单价。
                        -- 源身份唯一索引保证至多一行，因此不聚合。
                        SELECT CASE
                                   WHEN leg.movement_type = 'TRANSFER_IN'
                                       THEN COALESCE(o.unit_cost, 0)
                                   ELSE round(o.quantity * COALESCE(o.unit_cost, 0) / leg.quantity, 4)
                               END
                          INTO v_in_cost
                          FROM inventory_movement o
                         WHERE o.deleted = FALSE
                           AND o.source_document_type =
                               CASE WHEN leg.movement_type = 'TRANSFER_IN'
                                    THEN 'TRANSFER_OUT_ITEM' ELSE 'CONVERT_OUT_ITEM' END
                           AND o.source_document_item_id = leg.source_document_item_id;
                        IF v_in_cost IS NULL THEN
                            -- 转入腿找不到转出腿 = 账本本身残缺。按 0 定价正是本迁移要消除的缺陷，
                            -- 因此这里响亮失败而不是给一个看起来合理的均价。
                            RAISE EXCEPTION
                                'V37 cannot price inventory_balance % (warehouse %, sku %): % of source item % has no paired outbound movement',
                                target.id, target.warehouse_id, target.sku_id,
                                leg.movement_type, leg.source_document_item_id;
                        END IF;
                    ELSE
                        -- 其余入方向（盘盈 / 报溢）不带来新成本事实：按现有均价入账，等价于均价不变。
                        v_in_cost := v_avg;
                    END IF;
                    v_avg      := round((v_quantity * v_avg + leg.quantity * v_in_cost)
                                        / (v_quantity + leg.quantity), 4);
                    v_quantity := v_quantity + leg.quantity;
                ELSE
                    -- 出方向：移动加权平均的性质 —— 出库不改变均价。
                    v_quantity := v_quantity - leg.quantity;
                END IF;
            END LOOP;

            -- 本行专属的对账：账本加总与本行存量数量不一致时，这行已经坏到不能用账本定价。
            -- 必须响亮失败而不是写入一个看起来合理的均价 —— 那会把「账实不符」伪装成「成本已修好」。
            IF v_quantity <> target.quantity THEN
                RAISE EXCEPTION
                    'V37 cannot price inventory_balance % (warehouse %, sku %): ledger replays to % but balance holds %',
                    target.id, target.warehouse_id, target.sku_id, v_quantity, target.quantity;
            END IF;

            UPDATE inventory_balance
               SET avg_cost = v_avg
             WHERE id = target.id;

            v_repaired := v_repaired + 1;
        END LOOP;

        RAISE NOTICE 'V37 repriced % zero-cost balance row(s) from the movement ledger', v_repaired;
    END
$$;

-- ---------------------------------------------------------------------------
-- Step 2: 收尾断言 —— 候选集合内不得残留「账本有成本、余额零均价」的行
-- ---------------------------------------------------------------------------
-- 只校验 Step 1 的处理范围（不引入全库断言，理由见文件头）。
DO $$
    DECLARE
        leftovers integer;
    BEGIN
        SELECT count(*) INTO leftovers
          FROM inventory_balance b
         WHERE b.deleted = FALSE
           AND b.quantity > 0
           AND b.avg_cost = 0
           AND EXISTS (SELECT 1
                         FROM inventory_movement m
                        WHERE m.deleted = FALSE
                          AND m.warehouse_id = b.warehouse_id
                          AND m.sku_id = b.sku_id
                          AND m.unit_cost > 0
                          AND m.after_quantity = m.before_quantity + m.quantity);

        IF leftovers > 0 THEN
            RAISE EXCEPTION
                'V37 left % zero-cost balance row(s) whose ledger carries a non-zero inbound cost', leftovers;
        END IF;
    END
$$;
