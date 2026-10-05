package com.xsy.scm.purchase.service;

import com.xsy.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 采购收货操作日志快照映射，不执行业务写入。 */
final class PurchaseReceiptSnapshotMapper {

    private PurchaseReceiptSnapshotMapper() {
    }

    static Map<String, Object> receiptItemSnapshot(PurchaseReceiptItemEntity row) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", row.getId());
        snapshot.put("purchaseOrderItemId", row.getPurchaseOrderItemId());
        snapshot.put("skuId", row.getSkuId());
        snapshot.put("plannedQuantity", PurchaseSnapshotFactory.fixed(row.getPlannedQuantity()));
        snapshot.put("receivedQuantity", PurchaseSnapshotFactory.fixed(row.getReceivedQuantity()));
        return snapshot;
    }

    /** 草稿收货单删除操作记录的完整前态。 */
    static Map<String, Object> receiptSnapshot(PurchaseReceiptVO vo) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", vo.getId());
        snapshot.put("receiptNo", vo.getReceiptNo());
        snapshot.put("purchaseOrderId", vo.getPurchaseOrderId());
        snapshot.put("status", vo.getStatus());
        snapshot.put("remark", vo.getRemark());
        snapshot.put("version", vo.getVersion());
        List<Map<String, Object>> items = new ArrayList<>();
        if (vo.getItems() != null) {
            for (PurchaseReceiptItemVO item : vo.getItems()) {
                Map<String, Object> one = PurchaseSnapshotFactory.snapshot();
                one.put("id", item.getId());
                one.put("purchaseOrderItemId", item.getPurchaseOrderItemId());
                one.put("receivedQuantity", PurchaseSnapshotFactory.fixed(item.getReceivedQuantity()));
                items.add(one);
            }
        }
        snapshot.put("items", items);
        return snapshot;
    }
}
