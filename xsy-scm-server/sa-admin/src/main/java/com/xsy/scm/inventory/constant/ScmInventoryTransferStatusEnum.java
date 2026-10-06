package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 调拨单状态（两步式：发出 → 在途 → 收货）。
 *
 * <p>
 * {@code DRAFT} 发出后经 {@code SHIPPED}（在途）到达 {@code RECEIVED}；草稿可取消为 {@code CANCELLED}。
 *
 * <p>
 * 之所以是两步而不是一步：货在卡车上时既不在源仓也不在目标仓，一步式会让「发出」那一刻目标仓就凭空多出库存； 且两步各自只锁一个仓库的余额行，既有的「按 {@code (warehouse_id, sku_id)}
 * 升序锁余额」纪律完全不用改， 一步式则要把锁序规则升级为跨仓排序。
 *
 * <p>
 * {@code SHIPPED} 不可取消：货已物理离开源仓，账上只能靠一张反向调拨单冲回， 改回草稿再删流水会被 {@code trg_inventory_movement_append_only} 拒绝。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryTransferStatusEnum {

    /**
     * 草稿：可改明细、可发出、可取消、可删除；未产生任何库存影响。
     */
    DRAFT("草稿"),

    /**
     * 在途：源仓已扣减、目标仓未增加。
     *
     * <p>
     * 此期间这批货不在任何余额行里（{@code inventory_balance} 只表达「在仓库里的货」，没有虚拟在途仓）， 因此全仓总库存会暂时减少。这是两步式的必然结果，不是缺陷。
     */
    SHIPPED("在途"),

    /**
     * 已收货：目标仓已增加，单据完成（终态）。
     */
    RECEIVED("已完成"),

    /**
     * 已取消：仅草稿可取消，未产生任何库存影响（终态）。
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
     * 是否允许发出（只有草稿可以）。
     */
    public boolean isShippable() {
        return this == DRAFT;
    }

    /**
     * 是否允许收货（只有在途可以）。
     */
    public boolean isReceivable() {
        return this == SHIPPED;
    }

    /**
     * 是否允许取消（只有草稿可以 —— 在途的货已经出库，只能反向调拨冲回）。
     */
    public boolean isCancellable() {
        return this == DRAFT;
    }

    /**
     * 是否允许删除（只有草稿可以；已发出/已收货必须留痕）。
     */
    public boolean isDeletable() {
        return this == DRAFT;
    }

    /**
     * 是否为在途（源仓已扣、目标仓未加）—— 仓库停用守卫据此阻塞。
     */
    public boolean isInTransit() {
        return this == SHIPPED;
    }

    /**
     * 该值是否允许写入 {@code inventory_transfer.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String transferStatus) {
        for (ScmInventoryTransferStatusEnum item : values()) {
            if (item.name().equals(transferStatus)) {
                return true;
            }
        }
        return false;
    }
}
