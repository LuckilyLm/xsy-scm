package net.lab1024.sa.admin.module.scm.inventory.service;

import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryConversionDao;
import org.springframework.stereotype.Service;


/**
 * 规格转换单号生成。
 *
 * <pre>
 * 转换单：CVT + yyyyMMdd + 至少 6 位   （例 CVT20260920000001）
 * </pre>
 *
 * <p>与采购/收货/出库/盘点/报损报溢/调拨同口径：序列来自 PG sequence，
 * **全局单调递增、不按日 reset**；日期段只是可读性装饰，唯一性由序列保证。
 */
@Service
@RequiredArgsConstructor
public class InventoryConversionNumberGenerator {


    /**
     * 转换单号前缀（与 OUT / STK / LGR / TRF 互不相同）。
     */
    public static final String PREFIX = "CVT";

    private final InventoryConversionDao conversionDao;

    /**
     * 单据号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。
     */
    public String next() {
        return format(PREFIX, conversionDao.nextConversionNo());
    }

    /**
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
