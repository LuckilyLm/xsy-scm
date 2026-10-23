package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 出库单可由草稿变为已确认，草稿可取消。已确认意味着库存已经扣减，不能回退状态或删除库存流水。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryOutboundStatusEnum {

    /**
     * 草稿：可改明细，未产生任何库存影响。
     */
    DRAFT("草稿"),

    /**
     * 已确认：已写流水并扣减余额，不可再改明细、不可取消。
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
     * 是否允许确认出库（只有草稿可以）。
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
     * 该值是否允许写入 {@code xsy_inventory_outbound.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String outboundStatus) {
        for (ScmInventoryOutboundStatusEnum item : values()) {
            if (item.name().equals(outboundStatus)) {
                return true;
            }
        }
        return false;
    }
}
