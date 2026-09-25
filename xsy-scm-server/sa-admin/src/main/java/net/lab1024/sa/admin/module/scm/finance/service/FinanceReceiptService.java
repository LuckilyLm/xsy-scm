package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceiptDao;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 收款域服务（F1-1 骨架，尚无业务方法）。
 *
 * <p><b>F1-3 在此实现</b>：收款登记（{@code scm:finance:receipt:add}），含允许无应收的预收（Q16）。
 * <b>F1-4 在此实现</b>：反向收款（{@code scm:finance:receipt:reverse}，D-3）。
 *
 * <p>两类写命令都必须走请求级 {@code Idempotency-Key} + 三段式（{@code claim → 写 → complete}
 * 同一事务），并按 {@code FinanceConstant.LOCK_RANK_RECEIPT} 先锁行再校验（设计稿 §13 / §14）。
 *
 * <p><b>F1-1 的硬边界</b>：不得提供任何修改 / 作废 / 软删收款的入口 —— 纠错只有反向事实一条路
 * （D-3，全局不变量 1）。反向前该单已用额必须为 0，否则 {@code 41142} 拒绝。
 * 读侧范围按 {@code customer_id → customer.seller_id → customerSellerScope} 收窄（D-5），
 * 禁止 {@code if role == FINANCE then bypass}。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceiptService {

    private final FinanceReceiptDao receipts;
    private final FinanceOperationLogRecorder operationLogs;
}
