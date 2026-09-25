package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePaymentDao;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;

/**
 * 付款域服务（F1-1 骨架，尚无业务方法）。
 *
 * <p><b>F1-3 在此实现</b>：付款登记（{@code scm:finance:payment:add}），含预付与退款付款。
 * 退款付款必须校验该 {@code order_refund} 已 {@code COMPLETED}、金额等于
 * {@code refund_amount}、对方类型为客户且与该退款的客户一致，否则 {@code 41139}（Q19）。
 * <b>F1-4 在此实现</b>：反向付款（{@code scm:finance:payment:reverse}，D-3）。
 *
 * <p>退款付款的防重**同时**依赖请求级 {@code Idempotency-Key} 与
 * {@code uk_finance_payment_source_active}（Q26）：幂等键防重复请求，来源唯一索引防重复事实。
 *
 * <p><b>付款不冲减应收</b>（Q27）：Return 负责红冲，Refund Payment 只负责真实资金退付，
 * 两者互不替代，避免双重冲减。
 *
 * <p><b>F1-1 的硬边界</b>：不得提供修改 / 作废 / 软删入口；反向行来源必须为 NULL（D-3）。
 * 读侧范围：{@code SUPPLIER} 侧本期不收窄，{@code CUSTOMER} 侧按 customerSellerScope（D-5）。
 */
@Service
@RequiredArgsConstructor
public class FinancePaymentService {

    private final FinancePaymentDao payments;
    private final FinanceOperationLogRecorder operationLogs;
}
