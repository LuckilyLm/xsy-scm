package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 报损报溢单状态（与参考项目 `AdjustStatusEnum` 的三态一致）。
 *
 * <p><b>为什么这里没有 DRAFT</b>：出库单与盘点单用 {@code DRAFT → CONFIRMED}，
 * 但报损报溢**创建即提交**（参考项目的初始态就是「待审核」）。加一个草稿态只会让
 * 「提交」变成第二个动作，而它在业务上不产生任何新信息 —— 单据在待审核之前
 * 本来就不影响库存，改与删都已放开。
 *
 * <pre>
 * PENDING ──approve──▶ COMPLETED      （写 LOSS_REPORT / GAIN_REPORT 流水 + 调整余额）
 *    │
 *    └──reject───▶ REJECTED            （不产生任何库存影响）
 * </pre>
 *
 * <p>两个终态都**不可回退**：流水 append-only，冲销必须新增反向单据，
 * 而不是把单据改回待审核再删流水（后者会被 {@code trg_inventory_movement_append_only} 拒绝）。
 */
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
     * 该值是否允许写入 {@code inventory_loss_gain.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryLossGainStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
