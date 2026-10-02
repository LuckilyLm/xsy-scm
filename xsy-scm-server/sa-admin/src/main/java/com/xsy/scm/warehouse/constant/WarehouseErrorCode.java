package com.xsy.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 仓库域错误码。
 *
 * <p>
 * 错误码由仓库域定义，避免形成 {@code warehouse → purchase} 反向依赖；库存域可直接使用这些码。
 *
 */
@Getter
@RequiredArgsConstructor
public enum WarehouseErrorCode implements ScmErrorCode {

    /**
     * 40485：落在已占用段 40464–40499 的空档内。
     */
    WAREHOUSE_NOT_FOUND(40485, "仓库不存在"),

    /**
     * 40996：落在已占用段（最大 40970）之后的空档内。
     */
    WAREHOUSE_CODE_DUPLICATE(40996, "仓库编码已存在"),

    // 仓库启停及其前置条件。
    /**
     * 41004：启停方向非法（已 ENABLED 再 enable / 已 DISABLED 再 disable）。
     */
    WAREHOUSE_STATE_INVALID(41004, "仓库当前状态不允许该启停操作"),
    /**
     * 仓库仍有库存余额，不能停用。
     */
    WAREHOUSE_DISABLE_HAS_BALANCE(41005, "仓库仍有库存余额，不能停用"),
    /**
     * 仓库仍有在途采购单，不能停用。
     */
    WAREHOUSE_DISABLE_HAS_INBOUND(41006, "仓库存在在途采购单，不能停用"),
    /**
     * 仓库仍有待入库收货单，不能停用。
     */
    WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY(41007, "仓库存在待入库收货单，不能停用"),

    /**
     * 41018：当前没有唯一启用的仓库，无法解析「默认仓库」。
     *
     * <p>
     * 销售订单**没有仓库字段**，因此预留库存时必须有一个 明确的落点。启用仓库数为 0 或大于 1 时都不能猜 —— 猜错会把货占在错误的仓库上， 而且要到出库/盘点才会暴露。
     */
    WAREHOUSE_DEFAULT_AMBIGUOUS(41018, "当前启用仓库不是唯一一个，无法确定默认仓库"),

    /**
     * 41009：仓库存在**在途调拨单**（已发出未收货），不能停用。
     *
     * <p>
     * 放在仓库域而不是 {@code InventoryErrorCode}： 「停用阻塞条件」是仓库域自身的不变量，与 41005/41006/41007 同一族； 而
     * 41048（仓库停用不能用于调拨）是**调拨侧**的规则，因此留在库存域。 两者方向相反、归属不同，不能混为一谈。
     *
     * <p>
     * 源仓与目标仓**都算**：源仓的货已经出去了但账上还没落地到目标仓， 目标仓则还欠着一批要入库的货。任一被停用都会让在途调拨无处可收 / 无据可查。
     */
    WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER(41009, "仓库存在在途调拨单，不能停用");

    private final int code;
    private final String msg;
}
