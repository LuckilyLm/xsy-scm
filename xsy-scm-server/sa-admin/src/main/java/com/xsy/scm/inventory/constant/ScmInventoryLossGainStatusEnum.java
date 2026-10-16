package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 报损报溢单创建后直接待审核，可修改或删除；批准后记库存流水，驳回不影响库存。批准和驳回都是终态，不能修改历史流水。 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryLossGainStatusEnum {

    /**
     * 待审核：可改明细、可删除，未产生任何库存影响。
     */
    PENDING("待审核"),

    /**
     * 已完成：已写流水并调整余额，不可再改、不可删除。
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
     * 是否允许删除（只有待审核可以 —— 已完成/已驳回的单据必须留痕）。
     */
    public boolean isDeletable() {
        return this == PENDING;
    }

    /**
     * 该值是否允许写入 {@code xsy_inventory_loss_gain.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String adjustmentStatus) {
        for (ScmInventoryLossGainStatusEnum item : values()) {
            if (item.name().equals(adjustmentStatus)) {
                return true;
            }
        }
        return false;
    }
}
