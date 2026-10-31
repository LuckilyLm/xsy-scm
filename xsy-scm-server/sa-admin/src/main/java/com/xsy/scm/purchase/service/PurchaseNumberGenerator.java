package com.xsy.scm.purchase.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import org.springframework.stereotype.Service;

/**
 * 采购单号和收货单号统一使用 {@code XSY + 雪花 ID}，雪花 ID 全局唯一，不再依赖数据库序列。
 */
@Service
public class PurchaseNumberGenerator {

    /**
     * 采购单号。
     */
    public String order() {
        return ScmDocumentNumbers.next();
    }

    /**
     * 收货单号。
     */
    public String receipt() {
        return ScmDocumentNumbers.next();
    }
}
