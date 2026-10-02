package com.xsy.scm.inventory.service;

import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.domain.entity.InventoryMovementEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Original sales-out cost and quantity; missing costs must remain incomplete. */
@Service
@RequiredArgsConstructor
public class InventorySalesReturnQueryService {
    private final InventoryMovementDao inventoryMovementDao;

    public List<InventoryMovementEntity> salesOutAllocations(Long orderItemId) {
        return inventoryMovementDao.listSalesOutAllocations(orderItemId);
    }
}
