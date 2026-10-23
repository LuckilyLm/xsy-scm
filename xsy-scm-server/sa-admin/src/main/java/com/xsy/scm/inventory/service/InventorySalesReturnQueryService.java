package com.xsy.scm.inventory.service;

import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.domain.entity.InventoryMovementEntity;
import com.xsy.scm.inventory.domain.vo.BundleSalesOutAllocationVO;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 销售出库的原始成本与数量；缺少成本的记录保持不完整。
 */
@Service
@RequiredArgsConstructor
public class InventorySalesReturnQueryService {
    private final InventoryMovementDao inventoryMovementDao;

    public List<InventoryMovementEntity> salesOutAllocations(Long orderItemId) {
        return inventoryMovementDao.listSalesOutAllocations(orderItemId);
    }

    public List<BundleSalesOutAllocationVO> bundleSalesOutAllocations(Long orderItemId) {
        return inventoryMovementDao.listBundleSalesOutAllocations(orderItemId);
    }
}
