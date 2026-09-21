package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 采购单号 / 收货单号生成（W5 Target Design §5.1 / §5.7）。
 *
 * <pre>
 * 采购单：PO + yyyyMMdd + 至少 6 位   （例 PO20260916000001）
 * 收货单：PR + yyyyMMdd + 至少 6 位   （例 PR20260916000001）
 * </pre>
 *
 * <p><b>两个关键口径</b>：
 * <ol>
 *   <li>序列来自 PG sequence，**全局单调递增、不按日 reset**（§5.1）。
 *       若按日 reset，跨日并发会撞号；全局递增则「日期段」只是可读性装饰，
 *       唯一性由序列本身保证；</li>
 *   <li>补零用 {@code %06d}，**超过 999999 自然扩位**（不截断、不报错）——
 *       与 W4 的 {@code OrderNumberGenerator} 完全一致。</li>
 * </ol>
 *
 * <p>日期取 {@link PurchaseSnapshotFactory#ASIA_SHANGHAI}，与 Q6a 的
 * `demand_date` 时区口径保持同一个 ZoneId 常量，避免「单号日期」与「需求日期」跨零点错位。
 */
@Service
@RequiredArgsConstructor
public class PurchaseNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

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
     * 单号拼接的纯函数（单测直接覆盖，不需要 DB）。
     */
    public static String format(String prefix, long number) {
        return prefix
                + LocalDate.now(PurchaseSnapshotFactory.ASIA_SHANGHAI).format(DATE)
                + String.format(Locale.ROOT, "%06d", number);
    }
}
