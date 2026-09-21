package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 出库单状态。
 *
 * <p>状态机刻意保持最小：{@code DRAFT → CONFIRMED}，以及草稿态的 {@code CANCELLED}。
 * 已确认的出库单**不可回退**——库存流水是 append-only 账本，冲销必须走「新增反向流水」，
 * 而不是把单据改回草稿再把流水删掉（后者在 DB 层会被
 * {@code ck_inventory_movement_append_only} 直接拒绝）。
 *
 * <pre>
 * DRAFT ──confirm──▶ CONFIRMED        （写 SALES_OUT 流水 + 扣减余额）
 *   │
 *   └──cancel──▶ CANCELLED            （不产生任何库存影响）
 * </pre>
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
     * 该值是否允许写入 {@code inventory_outbound.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryOutboundStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
