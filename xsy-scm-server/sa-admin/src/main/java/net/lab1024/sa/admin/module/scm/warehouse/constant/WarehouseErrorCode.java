package net.lab1024.sa.admin.module.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 仓库域错误码（6 个）。
 *
 * <p>设计依据：W5 Target Design §7.7（Q11 / F5）。**必须**独立于 {@code PurchaseErrorCode}：
 * {@code module/scm/warehouse/**} 要能独立抛错，否则会形成 {@code warehouse → purchase}
 * 的反向依赖 —— 而把 warehouse 独立成域的唯一理由就是避免这种反向依赖（W6 库存域要直接依赖它）。
 *
 * <p>与 {@code PurchaseErrorCode}（38 个）合计 **40** 个，与 W1–W4 全部错误码零交集
 * （由 {@code PurchaseErrorCodeTest} 门禁强制）。
 */
@Getter
@RequiredArgsConstructor
public enum WarehouseErrorCode implements ScmErrorCode {

    /** 40485：落在已占用段 40464–40499 的空档内。 */
    WAREHOUSE_NOT_FOUND(40485, "仓库不存在"),

    /** 40996：落在已占用段（最大 40970）之后的空档内。 */
    WAREHOUSE_CODE_DUPLICATE(40996, "仓库编码已存在"),

    // ---- B1 仓库启停（41004–41007，410xx 新段）----
    /** 41004：启停方向非法（已 ENABLED 再 enable / 已 DISABLED 再 disable）。 */
    WAREHOUSE_STATE_INVALID(41004, "仓库当前状态不允许该启停操作"),
    /** 41005：停用被库存余额阻塞（HD-B1-01 第一条）。 */
    WAREHOUSE_DISABLE_HAS_BALANCE(41005, "仓库仍有库存余额，不能停用"),
    /** 41006：停用被在途采购单阻塞（HD-B1-01 第二条）。 */
    WAREHOUSE_DISABLE_HAS_INBOUND(41006, "仓库存在在途采购单，不能停用"),
    /** 41007：停用被待入库收货单阻塞（HD-B1-01 第三条）。 */
    WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY(41007, "仓库存在待入库收货单，不能停用");

    private final int code;
    private final String msg;
}
