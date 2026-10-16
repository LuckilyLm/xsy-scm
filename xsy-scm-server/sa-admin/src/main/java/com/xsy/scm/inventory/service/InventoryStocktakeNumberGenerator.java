package com.xsy.scm.inventory.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.dao.InventoryStocktakeDao;
import org.springframework.stereotype.Service;

/** 盘点单号用 STK、日期和数据库序列生成，数据库序列保证并发唯一。业务流水防重仍使用来源类型及来源明细 ID。 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeNumberGenerator {

    /**
     * 盘点单号前缀。
     */
    public static final String PREFIX = "STK";

    private final InventoryStocktakeDao inventoryStocktakeDao;

    /**
     * 盘点单号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。
     */
    public String next() {
        return format(PREFIX, inventoryStocktakeDao.nextStocktakeNo());
    }

    /**
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
