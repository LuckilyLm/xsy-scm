package com.xsy.scm.print.support;

import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 销售订单打印只读正式订单详情，沿用业务员的数据范围；不读当前商品价格，也不改变锁价、结算或库存。
 */
@Component
@RequiredArgsConstructor
public class SalesOrderPrintSourceProvider implements ScmPrintSourceProvider {
    private final SalesOrderQueryService orderQueryService;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.SALES_ORDER;
    }

    @Override
    public String queryPermission() {
        return OrderPermission.QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        orderQueryService.detail(businessId);
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        SalesOrderDetailVO order = orderQueryService.detail(businessId);
        var header = ScmPrintSource.values();
        header.put("orderNo", ScmPrintText.text(order.getOrderNo()));
        header.put("customerName", ScmPrintText.text(order.getCustomerNameSnapshot()));
        header.put("customerCode", ScmPrintText.text(order.getCustomerCodeSnapshot()));
        header.put("orderStatus", statusLabel(order.getStatus()));
        header.put("orderSource", sourceLabel(order.getOrderSource()));
        header.put("expectDeliveryTime", ScmPrintText.text(order.getExpectDeliveryTime()));
        header.put("remark", ScmPrintText.text(order.getRemark()));
        var address = order.getAddress();
        if (address != null) {
            header.put("receiverName", ScmPrintText.text(address.getReceiverName()));
            header.put("receiverPhone", ScmPrintText.text(address.getReceiverPhone()));
            header.put("address", ScmPrintText.text(address.getAddress()));
        }

        List<Map<String, String>> rows = new ArrayList<>();
        List<SalesOrderItemVO> items = order.getItems() == null ? List.of() : order.getItems();
        for (SalesOrderItemVO item : items) {
            var row = ScmPrintSource.row();
            row.put("productName", ScmPrintText.text(item.getProductNameSnapshot()));
            row.put("skuCode", ScmPrintText.text(item.getSkuCodeSnapshot()));
            row.put("specName", ScmPrintText.text(item.getSpecNameSnapshot()));
            row.put("saleUnit", ScmPrintText.text(item.getSaleUnitSnapshot()));
            row.put("orderedQuantity", ScmPrintText.fixed(item.getOrderedQuantity()));
            row.put("actualQuantity", ScmPrintText.fixed(item.getActualQuantity()));
            row.put("draftUnitPrice", ScmPrintText.fixed(item.getDraftUnitPrice()));
            row.put("lockedUnitPrice", ScmPrintText.fixed(item.getLockedUnitPrice()));
            row.put("orderedLineAmount", ScmPrintText.fixed(item.getOrderedLineAmount()));
            row.put("settlementLineAmount", ScmPrintText.fixed(item.getSettlementLineAmount()));
            rows.add(row);
        }
        var totals = ScmPrintSource.values();
        totals.put("orderedTotalAmount", ScmPrintText.fixed(order.getOrderedTotalAmount()));
        totals.put("settlementTotalAmount", ScmPrintText.fixed(order.getSettlementTotalAmount()));
        return new ScmPrintSource(order.getOrderNo(), header, rows, totals);
    }

    private static String statusLabel(String status) {
        if (status == null)
            return "";
        for (ScmOrderStatusEnum value : ScmOrderStatusEnum.values()) {
            if (value.name().equals(status)) {
                return value.getDesc();
            }
        }
        return status;
    }

    /** 打印件沿用仓库现场的叫法，与页面文案不同，因此按枚举逐项给文案。 */
    private static String sourceLabel(String source) {
        if (source == null)
            return "";
        for (ScmOrderSourceEnum value : ScmOrderSourceEnum.values()) {
            if (value.name().equals(source)) {
                return switch (value) {
                    case ADMIN -> "后台录单";
                    case MALL -> "微信小程序";
                    case SUPPLEMENT -> "补单";
                    case IMPORT -> "导入";
                };
            }
        }
        return source;
    }
}
