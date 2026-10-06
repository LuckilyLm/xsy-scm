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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在线充值（ADM-12 3-12b）。
 *
 * <p>
 * 链路：解析客户 → 解析结算主体 → 校验数据范围 → Idempotency-Key，落 {@code customer_balance_recharge}（谁往哪个钱包充多少），再创建
 * {@code PaymentIntent}（{@code method=ONLINE}、{@code source_type=BALANCE_RECHARGE}、
 * {@code source_id=recharge.id}）。支付成功后由 {@code PaymentTransaction SUCCEEDED} 派生两条事实：
 * {@code FinanceReceipt}（{@code ONLINE_PAYMENT + PAYMENT_TRANSACTION}，公司实际进账）与
 * {@code BalanceMovement}（{@code RECHARGE/CREDIT + PAYMENT_TRANSACTION}，钱包权益增加）。
 *
 * <p>
 * 余额域负责解释「为什么要支付」，支付域负责支付本身。因此充值意图由本服务内部创建， 而不是把公开的 {@code /scm/payment/intent/create} 放开给客户端拼 {@code sourceType} 与任意
 * {@code sourceId}。
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
     * 发起充值：先落充值事实，再创建支付意图。
     *
     * <p>
     * <b>幂等在外层先占</b>：如果等内部创建意图时才 claim，一次超时重试会先多出一条充值事实。同一个 {@code Idempotency-Key} 回放首次结果；换 key 再提就是一笔新充值（允许多次充值）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentIntentEntity create(BalanceRechargeCreateForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(CREATE_SCOPE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PaymentIntentEntity.class);
        }

        BigDecimal amount = form.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_RECHARGE_AMOUNT_INVALID);
        }
        CustomerEntity customer = customerService.require(form.getCustomerId());
        CustomerEntity settlement = customerBalanceService.settlementCustomerOf(form.getCustomerId());
        // 数据范围 fail-closed：越权与「客户不存在」共用 30005，避免把主键探测变成可用信号
        if (!dataScopeService.resolve().getCustomerSellerScope().allows(settlement.getSellerId())) {
            throw new ScmDataScopeException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceRechargeEntity recharge = new CustomerBalanceRechargeEntity();
        recharge.setRechargeNo(
                ScmDocumentNumbers.format(RECHARGE_NO_PREFIX, customerBalanceRechargeDao.nextRechargeNo()));
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
