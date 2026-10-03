package com.xsy.scm.payment;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentNumberGenerator;
import com.xsy.scm.payment.support.BalanceConsumptionResult;
import com.xsy.scm.payment.support.BalanceConsumptionSink;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        var claim = new ScmIdempotencyService.Claim(null, false);
        when(idempotency.claim(anyString(), anyString(), eq(form))).thenReturn(claim);
        when(numbers.nextIntentNo()).thenReturn("PI1"); when(numbers.nextTransactionNo()).thenReturn("PT20");
        when(intents.insert(any(PaymentIntentEntity.class))).thenAnswer(invocation -> {
            PaymentIntentEntity row = invocation.getArgument(0); row.setId(1L); return 1;
        });
        when(transactions.insert(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity row = invocation.getArgument(0); row.setId(20L); return 1;
        });
        when(intents.updateStatus(1L, "CREATED", "PENDING", "1:1")).thenReturn(1);
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

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }
}
