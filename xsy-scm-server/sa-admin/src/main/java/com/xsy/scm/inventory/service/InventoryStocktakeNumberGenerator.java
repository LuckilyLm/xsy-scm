package com.xsy.scm.inventory.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import org.springframework.stereotype.Service;

/**
 * 盘点单号生成。
 */
@Service
public class InventoryStocktakeNumberGenerator {

    /**
     * 历史单号前缀；新单号统一为 {@code XSY + 雪花 ID}，此常量仅用于识别旧数据。
     */
    public static final String PREFIX = "STK";

    public String next() {
        return ScmDocumentNumbers.next();
    }
}
