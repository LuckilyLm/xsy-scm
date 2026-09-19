package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundDao;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 出库单号生成。
 *
 * <pre>
 * 出库单：OUT + yyyyMMdd + 至少 6 位   （例 OUT20260919000001）
 * </pre>
 *
 * <p>与采购/收货同口径：序列来自 PG sequence，**全局单调递增、不按日 reset**；
 * 日期段只是可读性装饰，唯一性由序列保证；补零用 {@code %06d}，超过 999999 自然扩位。
 *
 * <p>日期取 {@link PurchaseSnapshotFactory#ASIA_SHANGHAI}，与 Q6a 的 {@code demand_date}
 * 及采购单号共用同一个 ZoneId 常量，避免跨零点错位。
 */
@Service
@RequiredArgsConstructor
public class InventoryOutboundNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 出库单号前缀。 */
    public static final String PREFIX = "OUT";

    private final InventoryOutboundDao outboundDao;

    /** 出库单号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。 */
    public String next() {
        return format(PREFIX, outboundDao.nextOutboundNo());
    }

    /** 单号拼接的纯函数（单测直接覆盖，不需要 DB）。 */
    public static String format(String prefix, long number) {
        return prefix
                + LocalDate.now(PurchaseSnapshotFactory.ASIA_SHANGHAI).format(DATE)
                + String.format(Locale.ROOT, "%06d", number);
    }
}
