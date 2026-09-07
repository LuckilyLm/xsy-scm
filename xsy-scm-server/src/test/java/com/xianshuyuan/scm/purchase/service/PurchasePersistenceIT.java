package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandAllocationEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandAllocationMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchasePersistenceIT {
    @Autowired
    private PurchaseDemandMapper demands;

    @Autowired
    private PurchaseOrderItemMapper orderItems;

    @Autowired
    private PurchaseDemandAllocationMapper allocations;

    @Test
    void readsPurchaseSnapshotsThroughExplicitJsonbResultMaps() {
        long seed = Math.abs(UUID.randomUUID().getMostSignificantBits());
        PurchaseDemandEntity demand = demand(seed);
        demands.insert(demand);

        PurchaseOrderItemEntity item = orderItem(seed);
        orderItems.insert(item);

        PurchaseDemandAllocationEntity allocation = allocation(demand, item);
        allocations.insert(allocation);

        PurchaseDemandEntity loadedDemand = demands.selectActiveByIdForUpdate(demand.getId());
        PurchaseOrderItemEntity loadedItem = orderItems.selectActiveById(item.getId());
        PurchaseDemandAllocationEntity loadedAllocation =
                allocations.selectActiveByOrderItemAndDemandForUpdate(item.getId(), demand.getId());

        assertThat(loadedDemand.getSpecValuesSnapshot()).containsEntry("规格", "大");
        assertThat(loadedItem.getSpuCodeSnapshot()).isEqualTo("SPU-" + seed);
        assertThat(loadedItem.getSpecValuesSnapshot()).containsEntry("等级", "A");
        assertThat(loadedAllocation.getDemandSnapshot().get("source").asText()).isEqualTo("integration");
    }

    private PurchaseDemandEntity demand(long seed) {
        PurchaseDemandEntity entity = new PurchaseDemandEntity();
        entity.setSalesOrderId(seed);
        entity.setSalesOrderItemId(seed);
        entity.setSpuId(seed);
        entity.setSkuId(seed);
        entity.setSalesOrderNoSnapshot("SO-" + seed);
        entity.setSpuCodeSnapshot("SPU-" + seed);
        entity.setProductNameSnapshot("集成测试商品");
        entity.setSkuCodeSnapshot("SKU-" + seed);
        entity.setSkuNameSnapshot("集成测试规格");
        entity.setSpecValuesSnapshot(Map.of("规格", "大"));
        entity.setPurchaseUnitSnapshot("箱");
        entity.setProductTypeSnapshot(ProductType.STANDARD);
        entity.setRequiredQuantity(new BigDecimal("2.0000"));
        entity.setAllocatedQuantity(BigDecimal.ZERO);
        entity.setFulfilledQuantity(BigDecimal.ZERO);
        entity.setStatus(PurchaseDemandStatus.PENDING);
        entity.setDemandDate(LocalDate.now());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }

    private PurchaseOrderItemEntity orderItem(long seed) {
        PurchaseOrderItemEntity entity = new PurchaseOrderItemEntity();
        entity.setPurchaseOrderId(seed);
        entity.setSpuId(seed);
        entity.setSkuId(seed);
        entity.setSpuCodeSnapshot("SPU-" + seed);
        entity.setProductNameSnapshot("集成测试商品");
        entity.setSkuCodeSnapshot("SKU-" + seed);
        entity.setSkuNameSnapshot("集成测试规格");
        entity.setSpecValuesSnapshot(Map.of("等级", "A"));
        entity.setPurchaseUnitSnapshot("箱");
        entity.setProductTypeSnapshot(ProductType.STANDARD);
        entity.setPlannedQuantity(new BigDecimal("2.0000"));
        entity.setReceivedQuantity(BigDecimal.ZERO);
        entity.setPurchasePrice(new BigDecimal("3.5000"));
        entity.setLineAmount(new BigDecimal("7.0000"));
        entity.setSortOrder(0);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }

    private PurchaseDemandAllocationEntity allocation(
            PurchaseDemandEntity demand,
            PurchaseOrderItemEntity item
    ) {
        PurchaseDemandAllocationEntity entity = new PurchaseDemandAllocationEntity();
        entity.setPurchaseDemandId(demand.getId());
        entity.setPurchaseOrderItemId(item.getId());
        entity.setSalesOrderId(demand.getSalesOrderId());
        entity.setSalesOrderItemId(demand.getSalesOrderItemId());
        entity.setSkuId(demand.getSkuId());
        entity.setAllocatedQuantity(new BigDecimal("1.0000"));
        entity.setDemandSnapshot(JsonNodeFactory.instance.objectNode().put("source", "integration"));
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }
}
