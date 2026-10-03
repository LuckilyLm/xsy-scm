package com.xsy.scm.finance.service;

import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult.Status;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 支付成功与正常签收共用编排；调用者必须在同一事务持有订单行锁。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceOrderFundingSettlementService {
    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceReceiptDao financeReceiptDao;
    private final FinanceOrderFundingSourceDao financeOrderFundingSourceDao;
    private final FinanceOrderFundingPolicy financeOrderFundingPolicy;
    private final FinanceOrderFundingWriteOffService financeOrderFundingWriteOffService;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<FinanceFundingAllocationResult> settleSalesOrderFunding(Long orderId) {
        var funding = financeOrderFundingPolicy.requireCompleteOrderFunding(orderId);
        var normal = financeReceivableDao.selectNormalByOrder(orderId);
        if (normal == null) {
            return funding.stream().map(f -> new FinanceFundingAllocationResult(f.getTransactionId(),
                    Status.NO_RECEIVABLE, BigDecimal.ZERO)).toList();
        }
        // 先锁全部来源再锁目标，避免下一笔来源与人工核销形成目标 → 来源反向锁链。
        for (var fact : funding) {
            if (ScmPaymentMethodEnum.BALANCE.name().equals(fact.getMethod())) {
                financeOrderFundingSourceDao.lockMovement(fact.getMovementId());
            } else {
                financeReceiptDao.selectByIdForUpdate(fact.getReceiptId());
            }
        }
        List<FinanceFundingAllocationResult> results = new ArrayList<>();
        for (var fact : funding) {
            var result = financeOrderFundingWriteOffService.register(fact.getTransactionId(), normal.getId());
            results.add(result);
            if (result.status() == Status.REVERSED || result.status() == Status.NO_AVAILABLE_FUNDS
                    || result.status() == Status.PARTIALLY_AVAILABLE || result.status() == Status.ALREADY_ALLOCATED) {
                log.info("订单资金自动分配保留既有处理结果: orderId={}, transactionId={}, reason={}",
                        orderId, fact.getTransactionId(), result.status());
            }
        }
        return List.copyOf(results);
    }
}
