package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 盘点单由草稿确认后写入库存差异流水并成为终态；草稿可取消。确认后不能回到草稿或删除流水，需另建调整业务更正。
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
     * 该值是否允许写入 {@code xsy_inventory_stocktake.status}（与数据库 CHECK 约束同源判定）。
     */
    public static boolean isSupported(String stocktakeStatus) {
        for (ScmInventoryStocktakeStatusEnum item : values()) {
            if (item.name().equals(stocktakeStatus)) {
                return true;
            }
        }
        return false;
    }
}
