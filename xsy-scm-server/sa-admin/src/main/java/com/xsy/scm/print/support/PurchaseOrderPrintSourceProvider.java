package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 采购单的打印数据源。
 *
 * <p>
 * 读的是 {@link PurchaseQueryService#orderDetail(Long)} —— 与采购单详情页**同一个**方法，
 * 因此数据范围（采购员范围）与「无权查看」的 30005 回答完全一致。打印域不写自己的 SQL、
 * 不绕过范围判定，也不因为「只是打印」就放宽可见性。
 *
 * <p>
 * 功能权限（{@link #queryPermission()}）由打印服务在取数前单独校验：查询服务只收窄数据范围，
 * 不判权限码，两者缺一不可。
 *
 * <p>
 * 字段集合与 {@code ScmPrintDocumentTypeEnum.PURCHASE_ORDER} 的白名单一一对应；
 * 这里 put 的 key 超出白名单没有意义（渲染层只按模板选的 key 取值）。
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
            row.put("skuName", ScmPrintText.text(item.getSkuName()));
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
}
