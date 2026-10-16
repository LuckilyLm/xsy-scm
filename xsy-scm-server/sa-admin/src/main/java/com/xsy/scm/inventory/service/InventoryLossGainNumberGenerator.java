package com.xsy.scm.inventory.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.dao.InventoryLossGainDao;
import org.springframework.stereotype.Service;

/** 报损报溢单号以 LGR、日期和数据库序列组成。使用数据库序列确保并发不重复，单号不作为库存流水的防重依据。 */
@Service
@RequiredArgsConstructor
public class InventoryLossGainNumberGenerator {

    /**
     * 报损报溢单号前缀。
     */
    public static final String PREFIX = "LGR";

    private final InventoryLossGainDao inventoryLossGainDao;

    /**
     * 单据号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。
     */
    public String next() {
        return format(PREFIX, inventoryLossGainDao.nextLossGainNo());
    }

    /**
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
