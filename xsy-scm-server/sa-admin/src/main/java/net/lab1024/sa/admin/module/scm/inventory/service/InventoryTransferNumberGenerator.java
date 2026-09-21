package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferDao;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 调拨单号生成。
 *
 * <pre>
 * 调拨单：TRF + yyyyMMdd + 至少 6 位   （例 TRF20260919000001）
 * </pre>
 *
 * <p>与采购/收货/出库/盘点/报损报溢同口径：序列来自 PG sequence，**全局单调递增、不按日 reset**；
 * 日期段只是可读性装饰，唯一性由序列保证；补零用 {@code %06d}，超过 999999 自然扩位。
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 调拨单号前缀。
     */
    public static final String PREFIX = "TRF";

    private final InventoryTransferDao transferDao;

    /**
     * 单据号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。
     */
    public String next() {
        return format(PREFIX, transferDao.nextTransferNo());
    }

    /**
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return prefix
                + LocalDate.now(PurchaseSnapshotFactory.ASIA_SHANGHAI).format(DATE)
                + String.format(Locale.ROOT, "%06d", number);
    }
}
