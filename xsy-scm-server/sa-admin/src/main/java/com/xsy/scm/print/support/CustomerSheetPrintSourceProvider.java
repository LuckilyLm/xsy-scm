package com.xsy.scm.print.support;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户级分拣单/送货单的公共取数：一张订单就是一个客户，只读正式订单详情。
 *
 * <p>
 * 两种纸面共用表头和行序，差别只在是否印价格、实数量取不取值、有没有合计。取数统一走 {@link SalesOrderQueryService#detail(Long)}，数据范围与订单详情一致，打印域不直连业务表。
 */
public abstract class CustomerSheetPrintSourceProvider implements ScmPrintSourceProvider {

    private final SalesOrderQueryService orderQueryService;

    protected CustomerSheetPrintSourceProvider(SalesOrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /** 是否印销售单价和行金额。 */
    protected abstract boolean showPrices();

    /** 是否输出实数量；分拣单留空供现场手写。 */
    protected abstract boolean showActualQuantity();

    /** 是否输出合计金额。 */
    protected abstract boolean showTotals();

    @Override
    public String queryPermission() {
        return OrderPermission.QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        orderQueryService.detail(businessId);
    }

    @Override
    public Long customerId(Long businessId) {
        return orderQueryService.detail(businessId).getCustomerId();
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        SalesOrderDetailVO order = orderQueryService.detail(businessId);
        var header = ScmPrintSource.values();
        header.put("customerName", ScmPrintText.text(order.getCustomerNameSnapshot()));
        header.put("orderNo", ScmPrintText.text(order.getOrderNo()));
        header.put("expectDeliveryTime", ScmPrintText.date(order.getExpectDeliveryTime()));
        header.put("routeNo",
            order.getDeliveryProgress() == null ? "" : ScmPrintText.text(order.getDeliveryProgress().getRouteNo()));
        header.put("remark", ScmPrintText.text(order.getRemark()));
        var address = order.getAddress();
        if (address != null) {
            header.put("receiverName", ScmPrintText.text(address.getReceiverName()));
            header.put("receiverPhone", ScmPrintText.text(address.getReceiverPhone()));
            header.put("address", ScmPrintText.text(address.getAddress()));
        }

        List<Map<String, String>> rows = new ArrayList<>();
        List<SalesOrderItemVO> items = order.getItems() == null ? List.of() : order.getItems();
        for (int index = 0; index < items.size(); index++) {
            SalesOrderItemVO item = items.get(index);
            var row = ScmPrintSource.row();
            row.put("rowNo", String.valueOf(index + 1));
            row.put("productName", ScmPrintText.text(item.getProductNameSnapshot()));
            row.put("specName", ScmPrintText.text(item.getSpecNameSnapshot()));
            row.put("saleUnit", ScmPrintText.text(item.getSaleUnitSnapshot()));
            row.put("orderedQuantity", ScmPrintText.fixed(item.getOrderedQuantity()));
            row.put("actualQuantity", actualQuantity(item));
            if (showPrices()) {
                row.put("lockedUnitPrice", ScmPrintText.fixed(item.getLockedUnitPrice()));
                row.put("settlementLineAmount", ScmPrintText.fixed(item.getSettlementLineAmount()));
            }
            row.put("remark", ScmPrintText.text(item.getRemark()));
            rows.add(row);
        }
        var totals = ScmPrintSource.values();
        if (showTotals()) {
            totals.put("settlementTotalAmount", ScmPrintText.fixed(order.getSettlementTotalAmount()));
        }
        return new ScmPrintSource(order.getOrderNo(), header, rows, totals);
    }

    /**
     * 送货单在分拣前打印时还没有实数量，先按下单量占位；分拣完成后重印会显示真实称重结果。
     */
    private String actualQuantity(SalesOrderItemVO item) {
        if (!showActualQuantity()) {
            return "";
        }
        return item.getActualQuantity() == null
            ? ScmPrintText.fixed(item.getOrderedQuantity())
            : ScmPrintText.fixed(item.getActualQuantity());
    }

    /** 子类用来声明负责的单据类型，避免两个实现复制字段定义。 */
    protected abstract ScmPrintDocumentTypeEnum type();

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return type();
    }
}
