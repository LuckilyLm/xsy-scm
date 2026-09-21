package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 规格转换单状态（与报损报溢单**刻意同构**，也与参考项目 `ConvertStatusEnum` 的三态一致）。
 *
 * <pre>
 * PENDING ──approve──▶ COMPLETED      （写 CONVERT_OUT + CONVERT_IN 流水并调整两边余额）
 *    │
 *    └──reject───▶ REJECTED            （不产生任何库存影响）
 * </pre>
 *
 * <p><b>为什么需要审批</b>：转换会把**两个 SKU** 的余额同时改掉，影响面比单纯报损更大；
 * 而且折算关系是人工声明的，没有审批就等于「录单人可以单方面决定一箱等于多少 kg」。
 *
 * <p>两个终态都**不可回退**：流水 append-only，修正靠**反向转换单**。
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
     * 该值是否允许写入 {@code inventory_conversion.status}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryConversionStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
