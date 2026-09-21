package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 盘点单状态。
 *
 * <p>状态机与出库单**刻意同构**，保持全仓单据状态语义一致：
 *
 * <pre>
 * DRAFT ──confirm──▶ CONFIRMED        （差异转流水 + 调整余额）
 *   │
 *   └──cancel──▶ CANCELLED            （不产生任何库存影响）
 * </pre>
 *
 * <p>已确认的盘点单**不可回退**：库存流水是 append-only 账本，冲销必须走「新增反向流水」，
 * 而不是把单据改回草稿再把流水删掉（后者会被 {@code trg_inventory_movement_append_only}
 * 在数据库层直接拒绝）。
 *
 * <p><b>为什么没有独立的「盘点中」状态</b>：录入实盘数是草稿态的编辑动作，
 * 加一个状态只会让「改明细」与「改状态」两个动作需要保持同步，
 * 而它们并不携带不同的业务含义。DRAFT 即「盘点进行中」。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryStocktakeStatusEnum {

    /**
     * 草稿：可录/改实盘量，未产生任何库存影响。
     */
    DRAFT("草稿"),

    /**
     * 已确认：差异已转流水并调整余额，不可再改明细、不可取消。
     */
    CONFIRMED("已确认"),

    /**
     * 已取消：仅草稿可取消，未产生任何库存影响。
     */
    CANCELLED("已取消");

    private final String desc;

    /**
     * 是否允许编辑明细（只有草稿可以）。
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * 是否允许确认盘点（只有草稿可以）。
     */
    public boolean isConfirmable() {
        return this == DRAFT;
    }

    /**
     * 是否允许取消（只有草稿可以）。
     */
    public boolean isCancellable() {
        return this == DRAFT;
    }

    /**
     * 该值是否允许写入 {@code inventory_stocktake.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryStocktakeStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
