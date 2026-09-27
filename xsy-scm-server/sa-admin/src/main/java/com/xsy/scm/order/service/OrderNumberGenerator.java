package com.xsy.scm.order.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.order.dao.SalesOrderDao;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNumberGenerator {
    private final SalesOrderDao salesOrderDao;

    public String order() {
        return format("SO", salesOrderDao.nextOrder());
    }

    public String returned() {
        return format("RT", salesOrderDao.nextReturn());
    }

    public String refund() {
        return format("RF", salesOrderDao.nextRefund());
    }

    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
