package com.xsy.scm.purchase.manager;

import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采购单审计日志载荷；主数据到实体的业务快照由 {@link PurchaseSnapshotFactory} 负责。数量与金额沿用定点字符串格式，保留 null 与零的区别。
 */
public final class PurchaseOrderAuditSnapshotFactory {

    private PurchaseOrderAuditSnapshotFactory() {
    }

    /**
     * {@code SUBMIT} / {@code CANCEL} / {@code SHORT_CLOSE} 的轻量状态快照。
     */
    public static Map<String, Object> orderStateSnapshot(PurchaseOrderEntity order) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("status", order.getStatus());
        snapshot.put("version", order.getVersion());
        return snapshot;
    }

    /**
     * {@code CREATE} / {@code UPDATE} / {@code DELETE} 的「全量 header + items」快照。
     */
    public static Map<String, Object> orderAuditSnapshot(PurchaseOrderVO vo) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", vo.getId());
        snapshot.put("orderNo", vo.getOrderNo());
        snapshot.put("supplierId", vo.getSupplierId());
        snapshot.put("warehouseId", vo.getWarehouseId());
        snapshot.put("purchaserId", vo.getPurchaserId());
        snapshot.put("status", vo.getStatus());
        snapshot.put("plannedArrivalDate",
                vo.getPlannedArrivalDate() == null ? null : vo.getPlannedArrivalDate().toString());
        snapshot.put("totalAmount", PurchaseSnapshotFactory.fixed(vo.getTotalAmount()));
        snapshot.put("version", vo.getVersion());
        snapshot.put("items", orderItemAuditSnapshots(vo.getItems()));
        return snapshot;
    }

    /**
     * 全量快照里的行数组；{@code null} 入参退化为空数组（不返回 {@code null}，避免下游判空）。
     */
    public static List<Map<String, Object>> orderItemAuditSnapshots(List<PurchaseOrderItemVO> items) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        if (items == null) {
            return snapshots;
        }
        for (PurchaseOrderItemVO item : items) {
            Map<String, Object> row = PurchaseSnapshotFactory.snapshot();
            row.put("id", item.getId());
            row.put("skuId", item.getSkuId());
            row.put("plannedQuantity", PurchaseSnapshotFactory.fixed(item.getPlannedQuantity()));
            row.put("receivedQuantity", PurchaseSnapshotFactory.fixed(item.getReceivedQuantity()));
            row.put("purchasePrice", PurchaseSnapshotFactory.fixed(item.getPurchasePrice()));
            row.put("lineAmount", PurchaseSnapshotFactory.fixed(item.getLineAmount()));
            row.put("sortOrder", item.getSortOrder());
            row.put("version", item.getVersion());
            List<Map<String, Object>> allocations = new ArrayList<>();
            if (item.getAllocations() != null) {
                for (PurchaseOrderAllocationVO allocation : item.getAllocations()) {
                    Map<String, Object> one = PurchaseSnapshotFactory.snapshot();
                    one.put("allocationId", allocation.getAllocationId());
                    one.put("demandId", allocation.getDemandId());
                    one.put("quantity", PurchaseSnapshotFactory.fixed(allocation.getQuantity()));
                    allocations.add(one);
                }
            }
            row.put("allocations", allocations);
            snapshots.add(row);
        }
        return snapshots;
    }
}
