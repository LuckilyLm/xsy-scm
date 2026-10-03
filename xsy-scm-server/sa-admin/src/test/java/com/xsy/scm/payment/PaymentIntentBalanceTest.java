package com.xsy.scm.payment;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.support.FinancePaymentReceiptFact;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentNumberGenerator;
import com.xsy.scm.payment.support.BalanceConsumptionResult;
import com.xsy.scm.payment.support.BalanceConsumptionSink;
import com.xsy.scm.payment.support.BalanceRechargeIntentFact;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentIntentBalanceTest {
    private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
    private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
    private final PaymentNumberGenerator numbers = mock(PaymentNumberGenerator.class);
    private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
    private final FinanceReceiptService receipts = mock(FinanceReceiptService.class);
    private final FinanceOrderFundingSettlementService settlements = mock(FinanceOrderFundingSettlementService.class);
    private final BalanceConsumptionSink consumption = mock(BalanceConsumptionSink.class);
    private final ObjectProvider<BalanceRechargeSink> rechargeProvider = provider();
    private final ObjectProvider<BalanceConsumptionSink> consumptionProvider = provider();
    private final PaymentIntentService service = new PaymentIntentService(intents, transactions, numbers, sources,
            scopes, idempotency, providers, receipts, rechargeProvider, consumptionProvider, settlements);
    private final PaymentIntentCreateForm form = new PaymentIntentCreateForm();

    @BeforeEach
    void source() {
        form.setCustomerId(2L); form.setSourceType("SALES_ORDER"); form.setSourceId(10L);
        form.setMethod("BALANCE"); form.setProvider("INTERNAL_BALANCE"); form.setAmount(new BigDecimal("60.0000"));
        var order = new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED");
        when(sources.lockOrder(10L)).thenReturn(order); when(sources.selectOrder(10L)).thenReturn(order);
        var scope = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(sources.customerVisible(2L, ScmValueScope.all())).thenReturn(true);
    }

    @Test
    void internalPaymentCreatesNoReceiptAndUsesOneConsumptionTimestamp() {
        stubIntentCreation();
        when(consumptionProvider.getObject()).thenReturn(consumption);
        var at = OffsetDateTime.parse("2026-10-04T12:00:00+08:00");
        when(consumption.consumeForPayment(1L, 2L, 3L, form.getAmount()))
                .thenReturn(new BalanceConsumptionResult(30L, 1L, 2L, 3L, form.getAmount(), at));
        when(transactions.markBalanceSucceeded(20L, form.getAmount(), at, "1:1")).thenReturn(1);
        when(intents.markBalanceSucceeded(1L, at, "1:1")).thenReturn(1);
        var saved = new PaymentIntentEntity(); saved.setId(1L); saved.setStatus("SUCCEEDED");
        when(intents.selectById(1L)).thenReturn(saved);
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            assertThat(service.create(form, "key")).isSameAs(saved);
        }
        var transaction = ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactions).insert(transaction.capture());
        assertThat(transaction.getValue().getProviderTransactionNo()).isEqualTo("PT20");
        verify(intents).markBalanceSucceeded(1L, at, "1:1");
        verify(settlements).settleSalesOrderFunding(10L);
        verifyNoInteractions(receipts, providers, rechargeProvider);
    }

    @Test
    void requestReplayReturnsOriginalWithoutConsumingAgain() {
        var claim = new ScmIdempotencyService.Claim(null, true);
        var saved = new PaymentIntentEntity(); saved.setId(1L);
        when(idempotency.claim(anyString(), anyString(), eq(form))).thenReturn(claim);
        when(idempotency.replay(claim, PaymentIntentEntity.class)).thenReturn(saved);
        assertThat(service.create(form, "key")).isSameAs(saved);
        verifyNoInteractions(consumptionProvider, transactions, providers, receipts, settlements);
    }

    @Test
    void balancePaymentIsRefusedOnceTheOrderAlreadyHasRefundFunding() {
        stubClaim();
        when(sources.hasOrderRefundFunding(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.BALANCE_PAYMENT_AFTER_REFUND));
        // 退款已经占住订单资金，再用余额付会让同一笔钱被两处认领
        verify(sources).lockOrderRefunds(10L);
        verifyNoInteractions(intents, transactions, consumptionProvider);
    }

    @Test
    void methodAndProviderMustAgreeOnWhetherTheWalletPays() {
        stubClaim();
        for (String[] pair : Arrays.asList(new String[] {"BALANCE", "MOCK"},
                new String[] {"ONLINE", "INTERNAL_BALANCE"}, new String[] {"ONLINE", "ALIPAY"})) {
            form.setMethod(pair[0]); form.setProvider(pair[1]);
            assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED));
        }
        verifyNoInteractions(intents, transactions);
    }

    @Test
    void intentAmountMustBePositiveAndCarryAtMostFourDecimals() {
        stubClaim();
        for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-60"),
                new BigDecimal("60.00001"))) {
            form.setAmount(amount);
            assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_INTENT_SOURCE_INVALID));
        }
        verifyNoInteractions(intents, transactions);
    }

    @Test
    void consumptionResultMustEchoTheIntentItWasCreatedFor() {
        stubIntentCreation();
        when(consumptionProvider.getObject()).thenReturn(consumption);
        when(consumption.consumeForPayment(1L, 2L, 3L, form.getAmount())).thenReturn(new BalanceConsumptionResult(
                30L, 1L, 2L, 999L, form.getAmount(), OffsetDateTime.parse("2026-10-04T12:00:00+08:00")));
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.ORDER_FUNDING_INVALID));
        }
        verify(transactions, never()).markBalanceSucceeded(anyLong(), any(), any(), anyString());
        verifyNoInteractions(settlements, receipts);
    }

    @Test
    void externalOutcomeCannotDriveAnInternalBalanceIntent() {
        var internal = new PaymentIntentEntity();
        internal.setId(1L); internal.setMethod("BALANCE"); internal.setProvider("INTERNAL_BALANCE");
        when(intents.selectById(1L)).thenReturn(internal);
        assertThatThrownBy(() -> service.applyOutcome(1L, 20L, ScmPaymentProvider.Outcome.SUCCEEDED, null, null,
                new BigDecimal("60.0000"), "1:1")).isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_INTENT_SOURCE_INVALID));
        when(intents.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.applyOutcome(1L, 20L, ScmPaymentProvider.Outcome.SUCCEEDED, null, null,
                new BigDecimal("60.0000"), "1:1")).isInstanceOf(ScmBusinessException.class);
        verifyNoInteractions(transactions, receipts, settlements, rechargeProvider);
    }

    @Test
    void rechargeIntentIsAnOnlinePaymentThatCreditsTheWalletOnceTheProviderSucceeds() {
        var fact = new BalanceRechargeIntentFact(9L, "CBR20261004000009", 2L, "门店", new BigDecimal("100"),
                "MOCK", "SUCCESS", "首充");
        var claim = new ScmIdempotencyService.Claim(null, false);
        when(idempotency.claim("BALANCE_RECHARGE_INTENT_CREATE:9", "key", fact)).thenReturn(claim);
        var channel = mock(ScmPaymentProvider.class);
        when(providers.require("MOCK")).thenReturn(channel);
        when(channel.provider()).thenReturn(ScmPaymentProviderEnum.MOCK);
        when(numbers.nextIntentNo()).thenReturn("PI1"); when(numbers.nextTransactionNo()).thenReturn("PT20");
        when(intents.insert(any(PaymentIntentEntity.class))).thenAnswer(invocation -> {
            PaymentIntentEntity row = invocation.getArgument(0); row.setId(1L); return 1;
        });
        when(transactions.insert(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity row = invocation.getArgument(0); row.setId(20L); return 1;
        });
        when(channel.createIntent(any())).thenReturn(new ScmPaymentProvider.IntentResult("EX1", "WX20",
                ScmPaymentProvider.Outcome.SUCCEEDED, new BigDecimal("100"), null, null));
        when(intents.updateStatus(1L, "CREATED", "PENDING", "1:1")).thenReturn(1);
        when(intents.updateStatus(1L, "PENDING", "SUCCEEDED", "1:1")).thenReturn(1);
        var intent = new PaymentIntentEntity();
        intent.setId(1L); intent.setCustomerId(2L); intent.setMethod("ONLINE"); intent.setProvider("MOCK");
        intent.setSourceType("BALANCE_RECHARGE"); intent.setSourceId(9L); intent.setStatus("PENDING");
        when(intents.selectById(1L)).thenReturn(intent);
        when(intents.lockById(1L)).thenReturn(intent);
        var pending = new PaymentTransactionEntity();
        pending.setId(20L); pending.setIntentId(1L); pending.setProvider("MOCK"); pending.setStatus("PENDING");
        when(transactions.lockById(20L)).thenReturn(pending);
        when(transactions.markSucceeded(20L, new BigDecimal("100"), "1:1")).thenReturn(1);
        var paidAt = OffsetDateTime.parse("2026-10-04T12:00:00+08:00");
        var succeeded = new PaymentTransactionEntity();
        succeeded.setId(20L); succeeded.setStatus("SUCCEEDED"); succeeded.setProviderTransactionNo("WX20");
        succeeded.setProviderAmount(new BigDecimal("100.0000")); succeeded.setPaidAt(paidAt);
        when(transactions.selectById(20L)).thenReturn(succeeded);
        var sink = mock(BalanceRechargeSink.class);
        when(rechargeProvider.getObject()).thenReturn(sink);
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            assertThat(service.createForBalanceRecharge(fact, "key")).isSameAs(intent);
        }
        var draft = ArgumentCaptor.forClass(PaymentIntentEntity.class);
        verify(intents).insert(draft.capture());
        assertThat(draft.getValue().getSourceType()).isEqualTo("BALANCE_RECHARGE");
        assertThat(draft.getValue().getSourceId()).isEqualTo(9L);
        assertThat(draft.getValue().getSourceNoSnapshot()).isEqualTo("CBR20261004000009");
        assertThat(draft.getValue().getCustomerId()).isEqualTo(2L);
        assertThat(draft.getValue().getCustomerNameSnapshot()).isEqualTo("门店");
        // 充值是往钱包里加钱，资金来源仍是外部在线支付：method 恒为 ONLINE 而不是 BALANCE
        assertThat(draft.getValue().getMethod()).isEqualTo("ONLINE");
        assertThat(draft.getValue().getProvider()).isEqualTo("MOCK");
        assertThat(draft.getValue().getMockScenario()).isEqualTo("SUCCESS");
        assertThat(draft.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(draft.getValue().getAmount().scale()).isEqualTo(4);
        verify(intents).bindExternalIntent(1L, "EX1", "1:1");
        var receiptFact = ArgumentCaptor.forClass(FinancePaymentReceiptFact.class);
        verify(receipts).registerFromPaymentTransaction(receiptFact.capture());
        assertThat(receiptFact.getValue().transactionId()).isEqualTo(20L);
        assertThat(receiptFact.getValue().customerId()).isEqualTo(2L);
        assertThat(receiptFact.getValue().providerAmount()).isEqualByComparingTo("100");
        assertThat(receiptFact.getValue().succeededAt()).isEqualTo(paidAt);
        assertThat(receiptFact.getValue().providerTransactionNo()).isEqualTo("WX20");
        // 收款事实与钱包权益是两条事实：一个回答公司实收多少，一个回答形成了多少权益
        verify(sink).rechargeFromPayment(9L, 2L, new BigDecimal("100.0000"), 20L, paidAt);
        verifyNoInteractions(consumptionProvider, consumption, settlements, sources);
    }

    private void stubClaim() {
        when(idempotency.claim(anyString(), anyString(), eq(form)))
                .thenReturn(new ScmIdempotencyService.Claim(null, false));
    }

    private void stubIntentCreation() {
        stubClaim();
        when(numbers.nextIntentNo()).thenReturn("PI1");
        when(numbers.nextTransactionNo()).thenReturn("PT20");
        when(intents.insert(any(PaymentIntentEntity.class))).thenAnswer(invocation -> {
            PaymentIntentEntity row = invocation.getArgument(0); row.setId(1L); return 1;
        });
        when(transactions.insert(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity row = invocation.getArgument(0); row.setId(20L); return 1;
        });
        when(intents.updateStatus(1L, "CREATED", "PENDING", "1:1")).thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }
}
