package com.xsy.scm.balance.service;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.balance.dao.CustomerBalanceRechargeDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceRechargeEntity;
import com.xsy.scm.balance.domain.form.BalanceRechargeCreateForm;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.support.BalanceRechargeIntentFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * 充值和支付结果分开处理：只按确认到账的收款入账，重复通知不会重复充值。
 */
@Service
@RequiredArgsConstructor
public class BalanceRechargeService {

    private static final int SCALE = 4;

    private static final String RECHARGE_NO_PREFIX = "CBR";

    private static final String CREATE_SCOPE = "BALANCE_RECHARGE_CREATE";

    private final CustomerBalanceRechargeDao customerBalanceRechargeDao;

    private final CustomerBalanceService customerBalanceService;

    private final PaymentIntentService paymentIntentService;

    private final CustomerService customerService;

    private final ScmDataScopeService dataScopeService;

    private final ScmIdempotencyService idempotencyService;

    /**
     * 发起充值：先保存充值记录，再创建支付意图。
     *
     * <p>
     * 幂等键先占住：否则超时重试会先多出一条充值记录。同一个 {@code Idempotency-Key} 返回首次结果，换 key 再提就是新充值。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentIntentEntity create(BalanceRechargeCreateForm form, String idempotencyKey) {
        CustomerEntity customer = customerService.require(form.getCustomerId());
        CustomerEntity settlement = customerBalanceService.settlementCustomerOf(form.getCustomerId());
        var customerScope = dataScopeService.resolve().getCustomerSellerScope();
        if (!customerScope.allows(customer.getSellerId()) || !customerScope.allows(settlement.getSellerId())) {
            throw new ScmDataScopeException();
        }

        var claim = idempotencyService.claim(CREATE_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PaymentIntentEntity.class);
        }

        BigDecimal amount = form.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_RECHARGE_AMOUNT_INVALID);
        }
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceRechargeEntity recharge = new CustomerBalanceRechargeEntity();
        recharge
            .setRechargeNo(ScmDocumentNumbers.format(RECHARGE_NO_PREFIX, customerBalanceRechargeDao.nextRechargeNo()));
        recharge.setSettlementCustomerId(settlement.getId());
        recharge.setSettlementCustomerNameSnapshot(settlement.getName());
        recharge.setCustomerId(customer.getId());
        recharge.setCustomerNameSnapshot(customer.getName());
        recharge.setAmount(amount);
        recharge.setRemark(form.getRemark());
        recharge.setCreatedAt(now);
        recharge.setUpdatedAt(now);
        recharge.setCreatedBy(operator);
        recharge.setUpdatedBy(operator);
        customerBalanceRechargeDao.insert(recharge);

        PaymentIntentEntity intent = paymentIntentService.createForBalanceRecharge(
            new BalanceRechargeIntentFact(recharge.getId(), recharge.getRechargeNo(), customer.getId(),
                customer.getName(), amount, form.getProvider(), form.getMockScenario(), form.getRemark()),
            idempotencyKey);
        idempotencyService.complete(claim, ScmBalanceSourceTypeEnum.PAYMENT_INTENT.name(), intent.getId(), intent);
        return intent;
    }
}
