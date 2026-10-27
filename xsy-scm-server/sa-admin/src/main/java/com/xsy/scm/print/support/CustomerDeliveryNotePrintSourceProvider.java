package com.xsy.scm.print.support;

import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import org.springframework.stereotype.Component;

/** 客户送货单：一张纸一个客户，含实数量、单价、行金额和合计，供客户签字。 */
@Component
public class CustomerDeliveryNotePrintSourceProvider extends CustomerSheetPrintSourceProvider {

    public CustomerDeliveryNotePrintSourceProvider(SalesOrderQueryService orderQueryService) {
        super(orderQueryService);
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
