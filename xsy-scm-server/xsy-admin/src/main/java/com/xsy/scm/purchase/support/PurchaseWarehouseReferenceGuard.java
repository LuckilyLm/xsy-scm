package com.xsy.scm.purchase.support;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Component;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_WAREHOUSE_DISABLED;

/**
 * 采购单引用仓库时由服务层检查仓库是否存在及可用；数据库不使用外键约束。
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
     * 用于需求生成与收货单创建：这两条路径都会把 {@code warehouse_id} 另存一份，一旦保存就不再回读主数据， 因此必须在写入前确认仓库当时可用。
     */
    public WarehouseEntity requireEnabled(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(PURCHASE_WAREHOUSE_DISABLED);
        }
        return warehouse;
    }
}
