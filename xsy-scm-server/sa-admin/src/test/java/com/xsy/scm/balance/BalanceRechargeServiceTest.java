package com.xsy.scm.balance;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.dao.CustomerBalanceRechargeDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceRechargeEntity;
import com.xsy.scm.balance.domain.form.BalanceRechargeCreateForm;
import com.xsy.scm.balance.service.BalanceRechargeService;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.support.BalanceRechargeIntentFact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 充值发起：先落充值事实、再让支付域去收钱，且范围与金额都失败关闭。 */
class BalanceRechargeServiceTest {
    private final CustomerBalanceRechargeDao recharges = mock(CustomerBalanceRechargeDao.class);
    private final CustomerBalanceService balances = mock(CustomerBalanceService.class);
    private final PaymentIntentService paymentIntents = mock(PaymentIntentService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final BalanceRechargeService service = new BalanceRechargeService(recharges, balances, paymentIntents,
            customers, scopes, idempotency);
    private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
    private final ScmIdempotencyService.Claim claim = new ScmIdempotencyService.Claim(null, false);
    private final BalanceRechargeCreateForm form = new BalanceRechargeCreateForm();

    @BeforeEach
    void wallet() {
        form.setCustomerId(2L); form.setAmount(new BigDecimal("100")); form.setProvider("MOCK");
        form.setMockScenario("SUCCESS"); form.setRemark("首充");
        var customer = new CustomerEntity(); customer.setId(2L); customer.setName("门店");
        var settlement = new CustomerEntity();
        settlement.setId(3L); settlement.setName("集团"); settlement.setSellerId(9L);
        when(customers.require(2L)).thenReturn(customer);
        when(balances.settlementCustomerOf(2L)).thenReturn(settlement);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(idempotency.claim(eq("BALANCE_RECHARGE_CREATE"), eq("key"), any())).thenReturn(claim);
    }

    @Test
    void createPersistsTheRechargeFactBeforeAskingThePaymentDomainToCollect() {
        when(recharges.nextRechargeNo()).thenReturn(9L);
        when(recharges.insert(any(CustomerBalanceRechargeEntity.class))).thenAnswer(invocation -> {
            CustomerBalanceRechargeEntity row = invocation.getArgument(0); row.setId(9L); return 1;
        });
        var intent = new PaymentIntentEntity(); intent.setId(1L);
        when(paymentIntents.createForBalanceRecharge(any(), eq("key"))).thenReturn(intent);
        PaymentIntentEntity created;
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            created = service.create(form, "key");
        }
        assertThat(created).isSameAs(intent);
        var row = ArgumentCaptor.forClass(CustomerBalanceRechargeEntity.class);
        verify(recharges).insert(row.capture());
        assertThat(row.getValue().getRechargeNo()).startsWith("CBR").endsWith("000009");
        assertThat(row.getValue().getSettlementCustomerId()).isEqualTo(3L);
        assertThat(row.getValue().getSettlementCustomerNameSnapshot()).isEqualTo("集团");
        assertThat(row.getValue().getCustomerId()).isEqualTo(2L);
        assertThat(row.getValue().getCustomerNameSnapshot()).isEqualTo("门店");
        assertThat(row.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(row.getValue().getAmount().scale()).isEqualTo(4);
        assertThat(row.getValue().getRemark()).isEqualTo("首充");
        assertThat(row.getValue().getCreatedBy()).isEqualTo("1:1");
        var fact = ArgumentCaptor.forClass(BalanceRechargeIntentFact.class);
        verify(paymentIntents).createForBalanceRecharge(fact.capture(), eq("key"));
        assertThat(fact.getValue().rechargeId()).isEqualTo(9L);
        assertThat(fact.getValue().rechargeNo()).isEqualTo(row.getValue().getRechargeNo());
        assertThat(fact.getValue().customerId()).isEqualTo(2L);
        assertThat(fact.getValue().customerName()).isEqualTo("门店");
        assertThat(fact.getValue().amount()).isEqualByComparingTo("100");
        assertThat(fact.getValue().provider()).isEqualTo("MOCK");
        assertThat(fact.getValue().mockScenario()).isEqualTo("SUCCESS");
        // 充值事实必须先落库：意图回放不能凭空造出一笔没有事实支撑的收款
        var sequence = inOrder(idempotency, recharges, paymentIntents);
        sequence.verify(idempotency).claim(eq("BALANCE_RECHARGE_CREATE"), eq("key"), any());
        sequence.verify(recharges).insert(any(CustomerBalanceRechargeEntity.class));
        sequence.verify(paymentIntents).createForBalanceRecharge(any(), eq("key"));
        sequence.verify(idempotency).complete(claim, "PAYMENT_INTENT", 1L, intent);
    }

    @Test
    void replayReturnsTheOriginalIntentWithoutASecondRechargeFact() {
        var replayed = new ScmIdempotencyService.Claim(null, true);
        when(idempotency.claim(eq("BALANCE_RECHARGE_CREATE"), eq("key"), any())).thenReturn(replayed);
        var intent = new PaymentIntentEntity(); intent.setId(1L);
        when(idempotency.replay(replayed, PaymentIntentEntity.class)).thenReturn(intent);
        assertThat(service.create(form, "key")).isSameAs(intent);
        verifyNoInteractions(recharges, paymentIntents);
    }

    @Test
    void nonPositiveAmountIsRejectedBeforeAnyFactIsWritten() {
        for (BigDecimal amount : List.of(BigDecimal.ZERO, new BigDecimal("-100"))) {
            form.setAmount(amount);
            assertThatThrownBy(() -> service.create(form, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(BalanceErrorCode.BALANCE_RECHARGE_AMOUNT_INVALID));
        }
        verifyNoInteractions(recharges, paymentIntents);
    }

    @Test
    void rechargeIsRefusedWhenTheWalletOwnerIsOutsideTheDataScope() {
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.of(List.of(999L)));
        assertThatThrownBy(() -> service.create(form, "key")).isInstanceOf(ScmDataScopeException.class);
        verifyNoInteractions(recharges, paymentIntents);
    }
}
