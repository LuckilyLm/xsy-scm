package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNumberGenerator {
    private final SalesOrderDao dao;

    public String order() {
        return format("SO", dao.nextOrder());
    }

    public String returned() {
        return format("RT", dao.nextReturn());
    }

    public String refund() {
        return format("RF", dao.nextRefund());
    }

    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
