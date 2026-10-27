package com.xsy.scm.print.support;

import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import org.springframework.stereotype.Component;

/** 客户分拣单：一张纸一个客户，无价格、无合计，实数量留空。 */
@Component
public class CustomerSortingSheetPrintSourceProvider extends CustomerSheetPrintSourceProvider {

    public CustomerSortingSheetPrintSourceProvider(SalesOrderQueryService orderQueryService) {
        super(orderQueryService);
    }

    @Override
    protected ScmPrintDocumentTypeEnum type() {
        return ScmPrintDocumentTypeEnum.CUSTOMER_SORTING_SHEET;
    }

    @Override
    protected boolean showPrices() {
        return false;
    }

    @Override
    protected boolean showActualQuantity() {
        return false;
    }

    @Override
    protected boolean showTotals() {
        return false;
    }
}
