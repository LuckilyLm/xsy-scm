package com.xsy.scm.warehouse.support;

import com.xsy.scm.warehouse.constant.WarehouseErrorCode;

/**
 * 仓库停用前检查库存和未完成采购。检查规则由相关业务模块提供，仓库模块只调用检查接口。
 */
public interface WarehouseDisableGuard {

    /**
     * 返回阻止停用的错误码，可停用返回 {@code null}。
     *
     * <p>
     * 实现方须在同一事务内被调用（{@code WarehouseService.disable}），且不得开启独立事务。
     */
    WarehouseErrorCode disableBlocker(Long warehouseId);
}
