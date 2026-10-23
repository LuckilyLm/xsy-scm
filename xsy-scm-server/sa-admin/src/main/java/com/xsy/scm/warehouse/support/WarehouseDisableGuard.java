package com.xsy.scm.warehouse.support;

import com.xsy.scm.warehouse.constant.WarehouseErrorCode;

/**
 * 仓库停用前检查库存和未完成采购；具体规则由相关业务模块实现。
 */
public interface WarehouseDisableGuard {

    /**
     * 返回阻止停用的错误码，可停用返回 {@code null}。 实现方在 {@code WarehouseService.disable} 的同一事务内执行，不得另开事务。
     */
    WarehouseErrorCode disableBlocker(Long warehouseId);
}
