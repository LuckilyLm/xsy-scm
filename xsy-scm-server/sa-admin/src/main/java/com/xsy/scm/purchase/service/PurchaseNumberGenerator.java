package com.xsy.scm.purchase.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseReceiptDao;
import org.springframework.stereotype.Service;

/**
 * Generates purchase order and receipt numbers.
 *
 * <p>
 * Numbers combine a PO / PR prefix, the Asia/Shanghai business date, and a global PostgreSQL sequence. The sequence is
 * never reset daily. Values are padded to six digits and grow naturally beyond 999999. The date uses
 * {@link ScmDocumentNumbers} so it matches the purchase-demand business date.
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

    /** Formats a sequence value without accessing the database. */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
