package net.lab1024.sa.admin.module.scm.inventory.service;

import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryStocktakeDao;
import org.springframework.stereotype.Service;


/**
 * 盘点单号生成。
 *
 * <pre>
 * 盘点单：STK + yyyyMMdd + 至少 6 位   （例 STK20260919000001）
 * </pre>
 *
 * <p>与采购/收货/出库同口径：序列来自 PG sequence，**全局单调递增、不按日 reset**；
 * 日期段只是可读性装饰，唯一性由序列保证；补零用 {@code %06d}，超过 999999 自然扩位。
 *
 * <p>日期取 {@link ScmDocumentNumbers} 的 Asia/Shanghai 业务时区，与 Q6a 的 {@code demand_date}
 * 及采购单号共用同一个 ZoneId 常量，避免跨零点错位。
 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeNumberGenerator {


    /**
     * 盘点单号前缀。
     */
    public static final String PREFIX = "STK";

    private final InventoryStocktakeDao stocktakeDao;

    /**
     * 盘点单号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。
     */
    public String next() {
        return format(PREFIX, stocktakeDao.nextStocktakeNo());
    }

    /**
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return ScmDocumentNumbers.format(prefix, number);
    }
}
