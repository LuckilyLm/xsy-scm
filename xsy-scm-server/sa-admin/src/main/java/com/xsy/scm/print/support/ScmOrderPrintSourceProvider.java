package com.xsy.scm.print.support;

import com.xsy.scm.order.domain.vo.OrderAddressSnapshotVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.SalesOrderQueryService;

import java.util.Map;

/**
 * 以销售订单为数据源的打印取数基类：统一走正式订单详情，数据范围与订单详情一致，打印域不直连业务表。
 */
public abstract class ScmOrderPrintSourceProvider implements ScmPrintSourceProvider {

    private final SalesOrderQueryService orderQueryService;

    protected ScmOrderPrintSourceProvider(SalesOrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /** 读取正式订单详情；越权与不存在都由订单服务统一拒绝。 */
    protected final SalesOrderDetailVO orderDetail(Long businessId) {
        return orderQueryService.detail(businessId);
    }

    /** 收货地址三列；订单独有的下单时保存的地址为空时整组不输出。 */
    protected static void putAddress(Map<String, String> header, OrderAddressSnapshotVO address) {
        if (address == null) {
            return;
        }
        header.put("receiverName", ScmPrintText.text(address.getReceiverName()));
        header.put("receiverPhone", ScmPrintText.text(address.getReceiverPhone()));
        header.put("address", ScmPrintText.text(address.getAddress()));
    }

    @Override
    public String queryPermission() {
        return OrderPermission.QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        orderDetail(businessId);
    }

    @Override
    public Long customerId(Long businessId) {
        return orderDetail(businessId).getCustomerId();
    }
}
