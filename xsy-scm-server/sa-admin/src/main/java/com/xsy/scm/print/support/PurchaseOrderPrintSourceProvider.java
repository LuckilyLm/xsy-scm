package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseQueryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 采购单打印读取提交时保存的业务信息，商品或供应商资料后来修改不影响历史单据。
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderPrintSourceProvider implements ScmPrintSourceProvider {

    private final PurchaseQueryService purchaseQueryService;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.PURCHASE_ORDER;
    }

    @Override
    public String queryPermission() {
        return PurchasePermission.QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        purchaseQueryService.orderDetail(businessId);
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        PurchaseOrderVO order = purchaseQueryService.orderDetail(businessId);

        Map<String, String> header = ScmPrintSource.values();
        header.put("orderNo", ScmPrintText.text(order.getOrderNo()));
        header.put("supplierName", ScmPrintText.text(order.getSupplierName()));
        header.put("warehouseName", ScmPrintText.text(order.getWarehouseName()));
        header.put("purchaserName", ScmPrintText.text(order.getPurchaserName()));
        header.put("plannedArrivalDate", ScmPrintText.text(order.getPlannedArrivalDate()));
        header.put("remark", ScmPrintText.text(order.getRemark()));

        List<Map<String, String>> rows = new ArrayList<>();
        List<PurchaseOrderItemVO> items = order.getItems() == null ? List.of() : order.getItems();
        for (PurchaseOrderItemVO item : items) {
            Map<String, String> row = ScmPrintSource.row();
            row.put("productName", ScmPrintText.text(item.getProductName()));
            row.put("skuCode", ScmPrintText.text(item.getSkuCode()));
            // 采购明细的快照名称是商品名，模板里的「规格」取规格值，不能印成商品名
            row.put("skuName", specName(item));
            row.put("purchaseUnit", ScmPrintText.text(item.getPurchaseUnit()));
            row.put("plannedQuantity", ScmPrintText.fixed(item.getPlannedQuantity()));
            row.put("receivedQuantity", ScmPrintText.fixed(item.getReceivedQuantity()));
            row.put("remainingQuantity", ScmPrintText.fixed(item.getRemainingQuantity()));
            row.put("purchasePrice", ScmPrintText.fixed(item.getPurchasePrice()));
            row.put("lineAmount", ScmPrintText.fixed(item.getLineAmount()));
            rows.add(row);
        }

        Map<String, String> totals = ScmPrintSource.values();
        totals.put("totalAmount", ScmPrintText.fixed(order.getTotalAmount()));

        return new ScmPrintSource(order.getOrderNo(), header, rows, totals);
    }

    /** 规格取规格值（例如「散装」）；没有规格值时退回快照名称，避免打印空白。 */
    private static String specName(PurchaseOrderItemVO item) {
        Map<String, Object> values = item.getSpecValues();
        if (values == null || values.isEmpty()) {
            return ScmPrintText.text(item.getSkuName());
        }
        String text = values.values().stream().filter(value -> value != null).map(value -> String.valueOf(value).trim())
            .filter(value -> !value.isEmpty()).collect(Collectors.joining(" / "));
        return text.isEmpty() ? ScmPrintText.text(item.getSkuName()) : text;
    }
}
