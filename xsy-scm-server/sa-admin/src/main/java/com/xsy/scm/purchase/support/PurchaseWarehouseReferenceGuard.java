package com.xsy.scm.purchase.support;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Component;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_WAREHOUSE_DISABLED;

/**
 * 仓库引用的服务层完整性守卫。
 *
 * <p>
 * {@code AGENTS.md} 明文禁止数据库外键，因此 {@code purchase_order.warehouse_id} · {@code purchase_receipt.warehouse_id} ·
 * {@code purchase_demand.warehouse_id} 三处引用只能由服务层保证。 本类是三处引用的唯一入口，集中两条不变量：存在性 → {@code WAREHOUSE_NOT_FOUND(40485)}（由
 * warehouse 域抛出）； 启用态 → {@code PURCHASE_WAREHOUSE_DISABLED(40987)}（采购侧规则，码留在 {@code PurchaseErrorCode}）。
 *
 * <p>
 * 与 {@code PurchaseOrderValidator.requireEnabledWarehouse} 不共享实现：后者是采购单表单路径的校验，
 * 需要同时返回实体供快照装配；本类是其余写入路径（需求生成、收货单创建）的引用守卫，只需要通过或拒绝。
 *
 * <p>
 * 仓库停用的反向引用检查由 {@code WarehouseDisableGuard} 统一完成，本类只校验新采购引用的仓库是否存在且启用。
 */
@Component
@RequiredArgsConstructor
public class PurchaseWarehouseReferenceGuard {

    private final WarehouseService warehouseService;

    /**
     * 存在性检查（40485）。用于只需要「引用有效」的路径。
     */
    public WarehouseEntity requireExisting(Long warehouseId) {
        return warehouseService.require(warehouseId);
    }

    /**
     * 存在 + 启用（40485 / 40987）。
     *
     * <p>
     * 用于需求生成与收货单创建：这两条路径都会把 {@code warehouse_id} 落成<b>快照</b>，一旦落库就不会再回读主数据，因此必须在写入前确认仓库当时是可用的。
     */
    public WarehouseEntity requireEnabled(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(PURCHASE_WAREHOUSE_DISABLED);
        }
        return warehouse;
    }
}
