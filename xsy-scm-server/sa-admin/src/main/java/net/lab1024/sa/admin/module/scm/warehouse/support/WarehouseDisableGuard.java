package net.lab1024.sa.admin.module.scm.warehouse.support;

import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;

/**
 * 仓库停用前置守卫（B1，HD-B1-01）。
 *
 * <p>停用是仓库域动作，但「能否停用」取决于**跨域**业务事实（库存余额在 inventory 域、
 * 在途采购单与待入库收货单在 purchase 域）。仓库域不能反向依赖这两个域，因此这里只声明
 * 契约，由 inventory 域（它本身已依赖 purchase 与 warehouse）提供实现 ——
 * 与 {@code purchase.support.PurchaseInventoryContract} 是同一手法。
 */
public interface WarehouseDisableGuard {

    /**
     * 返回阻止停用的错误码，可停用返回 {@code null}。
     *
     * <p>实现方须在同一事务内被调用（{@code WarehouseService.disable}），
     * 且不得开启独立事务。
     */
    WarehouseErrorCode disableBlocker(Long warehouseId);
}
