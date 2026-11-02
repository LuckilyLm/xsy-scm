package com.xsy.scm.print.support;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.promotion.support.PromotionDiscountAllocator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户级分拣单/送货单的公共取数：一张订单就是一个客户，只读正式订单详情。
 *
 * <p>
 * 两种纸面共用表头和行序，差别只在是否印价格、实数量取不取值、有没有合计。
 */
public abstract class CustomerSheetPrintSourceProvider extends ScmOrderPrintSourceProvider {

    /** 打印取数服务；子类可用它读取分拣实发结果，但仍只读正式订单详情。 */
    protected final SalesOrderQueryService orderQueryService;

    protected CustomerSheetPrintSourceProvider(SalesOrderQueryService orderQueryService) {
        super(orderQueryService);
        this.orderQueryService = orderQueryService;
    }

    /** 是否印销售单价和行金额。 */
    protected abstract boolean showPrices();

    /** 是否输出实数量；分拣单留空供现场手写。 */
    protected abstract boolean showActualQuantity();

    /** 是否输出合计金额。 */
    protected abstract boolean showTotals();

    @Override
    public ScmPrintSource load(Long businessId) {
        SalesOrderDetailVO order = orderDetail(businessId);
        var header = ScmPrintSource.values();
        header.put("customerName", ScmPrintText.text(order.getCustomerNameSnapshot()));
        header.put("orderNo", ScmPrintText.text(order.getOrderNo()));
        header.put("expectDeliveryTime", ScmPrintText.date(order.getExpectDeliveryTime()));
        header.put("routeNo",
            order.getDeliveryProgress() == null ? "" : ScmPrintText.text(order.getDeliveryProgress().getRouteNo()));
        header.put("remark", ScmPrintText.text(order.getRemark()));
        putAddress(header, order.getAddress());

        List<Map<String, String>> rows = new ArrayList<>();
        List<SalesOrderItemVO> items = order.getItems() == null ? List.of() : order.getItems();
        Map<Long, BigDecimal> sorted = showActualQuantity() ? sortedQuantities(order) : Map.of();
        Map<Long, BigDecimal> discounts = new HashMap<>();
        if (order.getDiscount() != null && order.getDiscount().getAllocations() != null) {
            order.getDiscount().getAllocations()
                .forEach(line -> discounts.put(line.getOrderItemId(), line.getDiscountAmount()));
        }
        List<BigDecimal> printedAmounts = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            SalesOrderItemVO item = items.get(index);
            BigDecimal sortedQuantity = sorted.get(item.getItemId());
            var row = ScmPrintSource.row();
            row.put("rowNo", String.valueOf(index + 1));
            row.put("productName", ScmPrintText.text(item.getProductNameSnapshot()));
            row.put("specName", ScmPrintText.text(item.getSpecNameSnapshot()));
            row.put("saleUnit", ScmPrintText.text(item.getSaleUnitSnapshot()));
            row.put("orderedQuantity", ScmPrintText.fixed(item.getOrderedQuantity()));
            row.put("actualQuantity", actualQuantity(item, sortedQuantity));
            if (showPrices()) {
                row.put("lockedUnitPrice", ScmPrintText.fixed(item.getLockedUnitPrice()));
                BigDecimal amount = lineAmount(item, sortedQuantity, discounts.get(item.getItemId()));
                row.put("settlementLineAmount", ScmPrintText.fixed(amount));
                printedAmounts.add(amount);
            }
            row.put("remark", ScmPrintText.text(item.getRemark()));
            rows.add(row);
        }
        var totals = ScmPrintSource.values();
        if (showTotals()) {
            totals.put("settlementTotalAmount",
                ScmPrintText.fixed(printedAmounts.stream().anyMatch(java.util.Objects::isNull)
                    ? null
                    : OrderAmountCalculator.orderAmount(printedAmounts)));
        }
        return new ScmPrintSource(order.getOrderNo(), header, rows, totals);
    }

    /**
     * 已完成分拣的实发数量（订单行 ID → 数量）。默认没有，分拣单不需要实数量；送货单覆盖它读取正式分拣结果。
     */
    protected Map<Long, BigDecimal> sortedQuantities(SalesOrderDetailVO order) {
        return Map.of();
    }

    /**
     * 送货单在分拣前打印时还没有实数量，先按下单量占位；分拣完成后（含缺货 0）重印会显示真实结果。
     */
    private String actualQuantity(SalesOrderItemVO item, BigDecimal sortedQuantity) {
        if (!showActualQuantity()) {
            return "";
        }
        if (sortedQuantity != null)
            return ScmPrintText.fixed(sortedQuantity);
        return item.getActualQuantity() == null
            ? ScmPrintText.fixed(item.getOrderedQuantity())
            : ScmPrintText.fixed(item.getActualQuantity());
    }

    private BigDecimal lineAmount(SalesOrderItemVO item, BigDecimal sortedQuantity, BigDecimal allocatedDiscount) {
        BigDecimal gross = sortedQuantity == null
            ? item.getSettlementLineAmount()
            : OrderAmountCalculator.lineAmount(sortedQuantity, item.getLockedUnitPrice());
        if (gross == null) {
            return null;
        }
        return gross
            .subtract(PromotionDiscountAllocator.proratedShare(gross, item.getOrderedLineAmount(), allocatedDiscount));
    }

    /** 子类用来声明负责的单据类型，避免两个实现复制字段定义。 */
    protected abstract ScmPrintDocumentTypeEnum type();

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return type();
    }
}
