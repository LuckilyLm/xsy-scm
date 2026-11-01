package com.xsy.scm.print.support;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/** 客户送货单：一张纸一个客户，含实数量、单价、行金额和合计，供客户签字。 */
@Component
public class CustomerDeliveryNotePrintSourceProvider extends CustomerSheetPrintSourceProvider {

    public CustomerDeliveryNotePrintSourceProvider(SalesOrderQueryService orderQueryService) {
        super(orderQueryService);
    }

    /**
     * 送货单按实际分拣/发货结果打印，缺货 0 与标品少发同样落在纸面上；行还没分拣时由基类回落到订单实发量。
     */
    @Override
    protected Map<Long, BigDecimal> sortedQuantities(SalesOrderDetailVO order) {
        return orderQueryService.sortedQuantities(order.getOrderId());
    }

    @Override
    protected ScmPrintDocumentTypeEnum type() {
        return ScmPrintDocumentTypeEnum.CUSTOMER_DELIVERY_NOTE;
    }

    @Override
    protected boolean showPrices() {
        return true;
    }

    @Override
    protected boolean showActualQuantity() {
        return true;
    }

    @Override
    protected boolean showTotals() {
        return true;
    }
}
