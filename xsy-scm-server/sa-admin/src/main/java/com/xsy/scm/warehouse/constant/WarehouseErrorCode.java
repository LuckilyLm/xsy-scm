package com.xsy.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 仓库域错误码。由仓库域定义，避免 {@code warehouse → purchase} 反向依赖；库存域可直接使用。
 */
@Getter
@RequiredArgsConstructor
public enum WarehouseErrorCode implements ScmErrorCode {

    /** 40485。 */
    WAREHOUSE_NOT_FOUND(40485, "仓库不存在"),

    /** 40996。 */
    WAREHOUSE_CODE_DUPLICATE(40996, "仓库编码已存在"),

    /** 41004：已启用再启用 / 已停用再停用。 */
    WAREHOUSE_STATE_INVALID(41004, "仓库当前状态不允许该启停操作"),

    /** 仓库仍有库存余额，不能停用。 */
    WAREHOUSE_DISABLE_HAS_BALANCE(41005, "仓库仍有库存余额，不能停用"),

    /** 仓库仍有在途采购单，不能停用。 */
    WAREHOUSE_DISABLE_HAS_INBOUND(41006, "仓库存在在途采购单，不能停用"),

    /** 仓库仍有待入库收货单，不能停用。 */
    WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY(41007, "仓库存在待入库收货单，不能停用"),

    /**
     * 41018：没有唯一启用的仓库，无法解析默认仓库。
     *
     * <p>
     * 销售订单没有仓库字段，预留库存必须有一个明确落点。启用仓库为 0 或大于 1 时都不能猜， 猜错会把货占在错误的仓库上，要到出库或盘点时才暴露。
     */
    WAREHOUSE_DEFAULT_AMBIGUOUS(41018, "当前启用仓库不是唯一一个，无法确定默认仓库"),

    /** 仓库存在未收货的在途调拨时不能停用，调入仓和调出仓都要检查。 */
    WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER(41009, "仓库存在在途调拨单，不能停用");

    private final int code;
    private final String msg;
}
