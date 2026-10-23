package com.xsy.scm.purchase.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseReceiptDao;
import org.springframework.stereotype.Service;

/**
 * 采购单号和收货单号由各自前缀、北京时间日期和数据库序列组成。使用序列保证并发下不重复，不按查询最大单号生成。
 */
@Service
@RequiredArgsConstructor
public class PurchaseNumberGenerator {

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    /**
     * 采购单号。必须在事务内调用（序列的 nextval 不回滚，跳号是可接受的）。
     */
    public String order() {
        return format("PO", purchaseOrderDao.nextOrderNo());
    }

    /**
     * 收货单号。同上。
     */
    public String receipt() {
        return format("PR", purchaseReceiptDao.nextReceiptNo());
    }

    /**
     * Formats a sequence value without accessing the database.
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
