package com.xsy.scm.print.support;

import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.delivery.service.DeliveryRouteQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 发货单读正式线路快照，逐行保留客户、停靠点和订单来源。 */
@Component
@RequiredArgsConstructor
public class DeliveryNotePrintSourceProvider implements ScmPrintSourceProvider {

    private final DeliveryRouteQueryService deliveryRouteQueryService;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.DELIVERY_NOTE;
    }

    @Override
    public String queryPermission() {
        return DeliveryPermission.ROUTE_QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        deliveryRouteQueryService.detail(businessId);
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        var document = deliveryRouteQueryService.print(businessId);
        var detail = document.getDetail();
        var route = detail.getRoute();
        var header = ScmPrintSource.values();
        header.put("routeName", ScmPrintText.text(route.getRouteName()));
        header.put("deliveryDate", ScmPrintText.text(route.getDeliveryDate()));
        header.put("warehouseName", ScmPrintText.text(route.getWarehouseNameSnapshot()));
        header.put("driverName", ScmPrintText.text(route.getDriverNameSnapshot()));
        header.put("driverPhone", ScmPrintText.text(route.getDriverPhoneSnapshot()));
        header.put("vehicleNo", ScmPrintText.text(route.getVehicleNoSnapshot()));
        header.put("remark", ScmPrintText.text(route.getRemark()));
        var itemsByOrder = document.getItems().stream().collect(Collectors.groupingBy(item -> item.getOrderId()));
        var rows = new ArrayList<Map<String, String>>();
        var stops = detail.getStops().stream()
                .sorted(Comparator.comparing(stop -> stop.getStopSeq())).toList();
        for (var stop : stops) {
            for (var order : detail.getOrders()) {
                if (!Objects.equals(order.getStopId(), stop.getId())) {
                    continue;
                }
                for (var item : itemsByOrder.getOrDefault(order.getOrderId(), java.util.List.of())) {
                    var row = ScmPrintSource.row();
                    row.put("stopSeq", ScmPrintText.text(stop.getStopSeq()));
                    row.put("customerName", ScmPrintText.text(stop.getCustomerNameSnapshot()));
                    row.put("address", ScmPrintText.text(stop.getAddressSnapshot()));
                    row.put("receiverName", ScmPrintText.text(stop.getReceiverNameSnapshot()));
                    row.put("receiverPhone", ScmPrintText.text(stop.getReceiverPhoneSnapshot()));
                    row.put("stopRemark", ScmPrintText.text(stop.getRemark()));
                    row.put("orderNo", ScmPrintText.text(order.getOrderNoSnapshot()));
                    row.put("expectDeliveryTime", ScmPrintText.text(order.getExpectDeliveryTimeSnapshot()));
                    row.put("productName", ScmPrintText.text(item.getProductNameSnapshot()));
                    row.put("specName", ScmPrintText.text(item.getSpecNameSnapshot()));
                    row.put("saleUnit", ScmPrintText.text(item.getSaleUnitSnapshot()));
                    row.put("orderedQuantity", ScmPrintText.fixed(item.getOrderedQuantity()));
                    row.put("actualQuantity", ScmPrintText.fixed(item.getActualQuantity()));
                    row.put("orderedLineAmount", ScmPrintText.fixed(item.getOrderedLineAmount()));
                    row.put("settlementLineAmount", ScmPrintText.fixed(item.getSettlementLineAmount()));
                    rows.add(row);
                }
            }
        }
        var totals = ScmPrintSource.values();
        // 详情已剔除释放的订单；合计必须与本次打印的活动订单集合一致。
        BigDecimal totalAmount = detail.getOrders().stream().map(order -> order.getOrderAmountSnapshot())
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        totals.put("totalAmount", ScmPrintText.fixed(totalAmount));
        return new ScmPrintSource(route.getRouteNo(), header, rows, totals);
    }
}
