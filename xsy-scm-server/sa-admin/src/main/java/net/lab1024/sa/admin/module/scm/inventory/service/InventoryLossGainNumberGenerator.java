package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryLossGainDao;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 报损报溢单号生成。
 *
 * <pre>
 * 报损报溢单：LGR + yyyyMMdd + 至少 6 位   （例 LGR20260919000001）
 * </pre>
 *
 * <p>与采购/收货/出库/盘点同口径：序列来自 PG sequence，**全局单调递增、不按日 reset**；
 * 日期段只是可读性装饰，唯一性由序列保证；补零用 {@code %06d}，超过 999999 自然扩位。
 *
 * <p><b>为什么不像参考项目那样用 BSD / BYD 两个前缀</b>：参考项目的单号前缀同时编码了
 * 「报损还是报溢」，而本表的 {@code adjust_type} 已经承载了这个信息，再编进单号等于
 * 把同一个事实存两遍 —— 一旦有人改类型而没改单号，两个字段就互相矛盾。
 * 一个前缀 + 一个类型字段，只有一个来源。
 */
@Service
@RequiredArgsConstructor
public class InventoryLossGainNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 报损报溢单号前缀。 */
    public static final String PREFIX = "LGR";

    private final InventoryLossGainDao lossGainDao;

    /** 单据号。必须在事务内调用（序列 nextval 不回滚，跳号可接受）。 */
    public String next() {
        return format(PREFIX, lossGainDao.nextLossGainNo());
    }

    /** 单号拼接的纯函数（单测直接覆盖，不需要 DB）。 */
    public static String format(String prefix, long number) {
        return prefix
                + LocalDate.now(PurchaseSnapshotFactory.ASIA_SHANGHAI).format(DATE)
                + String.format(Locale.ROOT, "%06d", number);
    }
}
