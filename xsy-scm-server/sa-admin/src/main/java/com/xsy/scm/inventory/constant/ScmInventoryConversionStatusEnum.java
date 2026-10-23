package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 规格转换单创建即待审核，审批通过后同时写转出与转入流水，驳回不影响库存。完成和驳回均是终态，不能回滚流水。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryConversionStatusEnum {

    /**
     * 待审核：可改明细、可删除，未产生任何库存影响。
     */
    PENDING("待审核"),

    /**
     * 已完成：已写两条流水并调整两边余额，不可再改、不可删除。
     */
    COMPLETED("已完成"),

    /**
     * 已驳回：不产生任何库存影响，不可再改、不可删除。
     */
    REJECTED("已驳回");

    private final String desc;

    /**
     * 是否允许编辑明细（只有待审核可以）。
     */
    public boolean isEditable() {
        return this == PENDING;
    }

    /**
     * 是否允许审批 / 驳回（只有待审核可以）。
     */
    public boolean isAuditable() {
        return this == PENDING;
    }

    /**
     * 是否允许删除（只有待审核可以 —— 已审核的单据必须留痕）。
     */
    public boolean isDeletable() {
        return this == PENDING;
    }

    /**
     * 该值是否允许写入 {@code xsy_inventory_conversion.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String conversionStatus) {
        for (ScmInventoryConversionStatusEnum item : values()) {
            if (item.name().equals(conversionStatus)) {
                return true;
            }
        }
        return false;
    }
}
