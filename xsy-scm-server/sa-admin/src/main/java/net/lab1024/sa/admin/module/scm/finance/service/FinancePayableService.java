package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePayableDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePayableItemDao;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 应付域服务（F1-1 骨架，尚无业务方法）。
 *
 * <p><b>F1-2 在此实现</b>：收货确认 → 正常应付（{@code DIRECT} 与 {@code WAREHOUSE_CONFIRM}
 * 都在 confirm 时生成，putaway 不决定应付时点，Q9）。金额 = 实际确认收货有效量 ×
 * {@code purchase_order_item.purchase_price}；少收未交部分不产生任何事实（Q12）。
 *
 * <p><b>F1-4 在此实现</b>：手工红字应付登记（{@code scm:finance:payable:red}）。
 * 这是 {@code FINANCE_RED_AMOUNT_EXCEEDED(41137)} 在本期**唯一**的使用者 ——
 * 手工红字是人工财务动作，拒绝它不会回滚任何业务域状态机，因此可以 fail-loud；
 * 自动红字应收则永不使用 41137（D-4）。
 *
 * <p><b>F1-1 的硬边界</b>：不得被 {@code PurchaseReceiptService.confirm} 调用（属 F1-2）；
 * 不得实现历史回填（D-1）；对 {@code purchase_*} 与 {@code inventory_*} 只读（全局不变量 4）。
 * 本期不为红字应付接任何自动业务来源 —— 采购退货全库不存在（Q13）。
 */
@Service
@RequiredArgsConstructor
public class FinancePayableService {

    private final FinancePayableDao payables;
    private final FinancePayableItemDao payableItems;
    private final FinanceOperationLogRecorder operationLogs;
}
