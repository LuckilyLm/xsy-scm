package com.xsy.scm.delivery.service;

import java.util.List;
import java.util.function.Consumer;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.delivery.domain.vo.DeliveryCandidateVO;
import com.xsy.scm.delivery.domain.vo.DeliveryCustomerViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryDetailVO;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderViewVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintResultVO;
import com.xsy.scm.delivery.domain.vo.DeliveryRouteVO;
import com.xsy.scm.delivery.domain.vo.DeliveryPrintItemVO;

/** 配送金额只有具备对应权限时才返回。无权查看返回 null，不返回零；整次查询统一判断权限，避免列表中出现不同口径。 */
public final class DeliveryVisibility {

    private final boolean amountVisible;

    private DeliveryVisibility(boolean amountVisible) {
        this.amountVisible = amountVisible;
    }

    /** 按当前登录态解析；取不到登录态时按「不可见」处理。 */
    public static DeliveryVisibility current() {
        return new DeliveryVisibility(ScmDataScopeService.hasPermission(ScmDataScopeService.DELIVERY_AMOUNT_PERM));
    }

    public List<DeliveryRouteVO> routes(List<DeliveryRouteVO> rows) {
        return mask(rows, row -> row.setTotalAmount(null));
    }

    public List<DeliveryCandidateVO> candidates(List<DeliveryCandidateVO> rows) {
        return mask(rows, row -> row.setOrderAmount(null));
    }

    public List<DeliveryOrderViewVO> orderView(List<DeliveryOrderViewVO> rows) {
        return mask(rows, row -> row.setOrderAmount(null));
    }

    public List<DeliveryCustomerViewVO> customerView(List<DeliveryCustomerViewVO> rows) {
        return mask(rows, row -> row.setTotalAmount(null));
    }

    /** 线路详情的三个金额出口（线路合计、停靠点合计、订单金额快照）一次抹清。 */
    public DeliveryDetailVO detail(DeliveryDetailVO detail) {
        if (detail == null || amountVisible)
            return detail;
        if (detail.getRoute() != null)
            detail.getRoute().setTotalAmount(null);
        mask(detail.getStops(), row -> row.setTotalAmount(null));
        if (detail.getOrders() != null)
            detail.getOrders().forEach(order -> order.setOrderAmountSnapshot(null));
        return detail;
    }

    /**
     * 打印预览明细：发货单上的行金额与结算金额同样属于订单金额。清空的是本次查询新读出的实体字段，只影响响应体，不回写数据库。
     */
    public List<DeliveryPrintItemVO> printItems(List<DeliveryPrintItemVO> rows) {
        return mask(rows, item -> {
            item.setOrderedLineAmount(null);
            item.setSettlementLineAmount(null);
        });
    }

    /** 正式生成打印的返回体：计次结果里的金额走同一口径，否则一次打印就绕过了字段级收口。 */
    public DeliveryPrintResultVO printResult(DeliveryPrintResultVO result) {
        if (result == null || amountVisible)
            return result;
        result.setTotalAmount(null);
        orderView(result.getOrders());
        return result;
    }

    /** 无权限才逐行清空，有权限原样返回。 */
    private <T> List<T> mask(List<T> rows, Consumer<T> clear) {
        if (amountVisible || rows == null)
            return rows;
        rows.forEach(clear);
        return rows;
    }
}
