package net.lab1024.sa.admin.module.scm.delivery.service;

import java.util.List;
import java.util.function.Consumer;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryCandidateVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryCustomerViewVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryDetailVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryOrderViewVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryPrintResultVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryRouteVO;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;

/**
 * 配送读接口的金额可见性：缺 {@code scm:delivery:amount:query} 时由服务端把金额字段抹成 {@code null}，
 * 前端渲染 {@code —}。
 *
 * <p>用 {@code null} 而不是 {@code 0}：0 是「这笔金额确实是零」这一事实，null 才是「调用者无权知道」。
 * 只在前端隐藏列不构成管控——直接调接口仍会拿到数字。
 *
 * <p>一次请求只判定一次（{@link #current()}），因此它是值对象而不是静态工具：
 * 逐行、逐集合再问一遍 Sa-Token 既浪费也容易在不同集合上得出口径不一致的结果。
 */
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
        if (detail == null || amountVisible) return detail;
        if (detail.getRoute() != null) detail.getRoute().setTotalAmount(null);
        mask(detail.getStops(), row -> row.setTotalAmount(null));
        if (detail.getOrders() != null)
            detail.getOrders().forEach(order -> order.setOrderAmountSnapshot(null));
        return detail;
    }

    /**
     * 打印预览明细：发货单上的行金额与结算金额同样属于订单金额。
     * 清空的是本次查询新读出的实体字段，只影响响应体，不回写数据库。
     */
    public List<SalesOrderItemEntity> printItems(List<SalesOrderItemEntity> rows) {
        return mask(rows, item -> {
            item.setOrderedLineAmount(null);
            item.setSettlementLineAmount(null);
        });
    }

    /** 正式生成打印的返回体：计次结果里的金额走同一口径，否则一次打印就绕过了字段级收口。 */
    public DeliveryPrintResultVO printResult(DeliveryPrintResultVO result) {
        if (result == null || amountVisible) return result;
        result.setTotalAmount(null);
        orderView(result.getOrders());
        return result;
    }

    /** 无权限才逐行清空，有权限原样返回。 */
    private <T> List<T> mask(List<T> rows, Consumer<T> clear) {
        if (amountVisible || rows == null) return rows;
        rows.forEach(clear);
        return rows;
    }
}
