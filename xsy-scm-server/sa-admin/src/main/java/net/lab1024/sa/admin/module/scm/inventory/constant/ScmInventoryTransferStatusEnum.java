package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 调拨单状态（两步式：发出 → 在途 → 收货）。
 *
 * <pre>
 * DRAFT ──ship──▶ SHIPPED（在途）──receive──▶ RECEIVED
 *   │
 *   └──cancel──▶ CANCELLED
 * </pre>
 *
 * <p><b>为什么是两步而不是一步</b>：
 * <ol>
 *   <li><b>语义正确</b>：货在卡车上时既不在源仓也不在目标仓。一步式会让「发出」那一刻
 *       目标仓就凭空多出库存，而货还没到 —— 那正是仓库最不能接受的账实不符；</li>
 *   <li><b>并发安全</b>：两步各自只锁**一个仓库**的余额行，因此既有的
 *       「按 {@code (warehouse_id, sku_id)} 升序锁余额」纪律完全不用改。
 *       一步式要在同一事务里锁两个仓库的行，锁序规则就得升级为跨仓排序 ——
 *       而那是四条既有写入路径都要跟着改的事。</li>
 * </ol>
 *
 * <p><b>{@code SHIPPED} 不可取消</b>：货已经物理离开了源仓，账上只能靠一张反向调拨单冲回，
 * 不能把状态改回草稿再删流水（那会被 {@code trg_inventory_movement_append_only} 拒绝）。
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
     * <p>此期间这批货**不在任何余额行里**（`inventory_balance` 只表达「在仓库里的货」，
     * 没有虚拟在途仓），因此全仓总库存会暂时减少。这是两步式的必然结果，不是缺陷。
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
     * 是否为**在途**（源仓已扣、目标仓未加）—— 仓库停用守卫据此阻塞。
     */
    public boolean isInTransit() {
        return this == SHIPPED;
    }

    /**
     * 该值是否允许写入 {@code inventory_transfer.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryTransferStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
