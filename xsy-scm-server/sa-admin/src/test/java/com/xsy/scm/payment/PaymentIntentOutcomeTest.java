package com.xsy.scm.payment;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.payment.dao.PaymentCallbackEventDao;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentCallbackService;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentNumberGenerator;
import com.xsy.scm.payment.support.BalanceConsumptionSink;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentIntentOutcomeTest {

    private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
    private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
    private final PaymentNumberGenerator numbers = mock(PaymentNumberGenerator.class);
    private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
    private final FinanceReceiptService receipts = mock(FinanceReceiptService.class);
    private final FinanceOrderFundingSettlementService settlements = mock(FinanceOrderFundingSettlementService.class);
    private final PaymentCallbackEventDao callbackEvents = mock(PaymentCallbackEventDao.class);
    private final ObjectProvider<BalanceRechargeSink> rechargeProvider = provider();
    private final ObjectProvider<BalanceConsumptionSink> consumptionProvider = provider();
    private final PaymentIntentService intentsService = new PaymentIntentService(intents, transactions, numbers,
            sources, scopes, idempotency, providers, receipts, rechargeProvider, consumptionProvider, settlements);
    private final ScmPaymentProvider provider = mock(ScmPaymentProvider.class);

    private final PaymentIntentEntity intent = new PaymentIntentEntity();
    private final PaymentTransactionEntity pendingTransaction = new PaymentTransactionEntity();
    private final PaymentTransactionEntity succeededTransaction = new PaymentTransactionEntity();

    @BeforeEach
    void setUp() {
        intent.setId(1L);
        intent.setCustomerId(2L);
        intent.setSourceType("SALES_ORDER");
        intent.setSourceId(10L);
        intent.setAmount(new BigDecimal("100.0000"));
        intent.setMethod("ONLINE");
        intent.setProvider("MOCK");
        intent.setStatus("PENDING");

        pendingTransaction.setId(20L);
        pendingTransaction.setIntentId(1L);
        pendingTransaction.setProvider("MOCK");
        pendingTransaction.setAmount(new BigDecimal("100.0000"));
        pendingTransaction.setStatus("PENDING");

        succeededTransaction.setId(20L);
        succeededTransaction.setIntentId(1L);
        succeededTransaction.setProvider("MOCK");
        succeededTransaction.setAmount(new BigDecimal("100.0000"));
        succeededTransaction.setProviderAmount(new BigDecimal("90.0000"));
        succeededTransaction.setStatus("SUCCEEDED");
        succeededTransaction.setPaidAt(OffsetDateTime.parse("2026-10-09T12:00:00+08:00"));

        PaymentOrderFact order = new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED");
        when(sources.lockOrder(10L)).thenReturn(order);
        when(sources.customerVisible(eq(2L), any())).thenReturn(true);
        ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(providers.require("MOCK")).thenReturn(provider);
        when(provider.provider()).thenReturn(com.xsy.scm.payment.constant.ScmPaymentProviderEnum.MOCK);
        when(intents.selectById(1L)).thenReturn(intent);
        when(intents.lockById(1L)).thenReturn(intent);
        when(transactions.lockById(20L)).thenReturn(pendingTransaction);
        when(transactions.markSucceeded(20L, new BigDecimal("90.0000"), "1:1")).thenReturn(1);
        when(transactions.selectById(20L)).thenReturn(succeededTransaction);
    }

    @Test
    void synchronousProviderAmountMismatchKeepsIntentPending() {
        PaymentIntentCreateForm form = salesOrderIntent();
        when(idempotency.claim(anyString(), anyString(), eq(form)))
                .thenReturn(new ScmIdempotencyService.Claim(null, false));
        when(numbers.nextIntentNo()).thenReturn("PI1");
        when(numbers.nextTransactionNo()).thenReturn("PT20");
        when(intents.insert(any(PaymentIntentEntity.class))).thenAnswer(invocation -> {
            PaymentIntentEntity row = invocation.getArgument(0);
            row.setId(1L);
            return 1;
        });
        when(transactions.insert(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity row = invocation.getArgument(0);
            row.setId(20L);
            return 1;
        });
        when(intents.updateStatus(1L, "CREATED", "PENDING", "1:1")).thenReturn(1);
        when(intents.updateStatus(1L, "PENDING", "SUCCEEDED", "1:1")).thenReturn(1);
        when(provider.createIntent(any())).thenReturn(new ScmPaymentProvider.IntentResult("EX1", "WX20",
                ScmPaymentProvider.Outcome.SUCCEEDED, new BigDecimal("90.0000"), null, null));

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            intentsService.create(form, "payment-key");
        }

        verify(transactions).markSucceeded(20L, new BigDecimal("90.0000"), "1:1");
        verify(intents, never()).updateStatus(1L, "PENDING", "SUCCEEDED", "1:1");
        verifyNoInteractions(receipts, settlements);
    }

    @Test
    void callbackAmountMismatchKeepsIntentPending() {
        when(transactions.selectByProviderTransactionNo("MOCK", "WX20")).thenReturn(pendingTransaction);
        when(callbackEvents.insertIgnoreDuplicate(any(PaymentCallbackEventEntity.class))).thenAnswer(invocation -> {
            PaymentCallbackEventEntity event = invocation.getArgument(0);
            event.setId(70L);
            return 1;
        });
        when(callbackEvents.selectById(70L)).thenReturn(new PaymentCallbackEventEntity());
        when(provider.parseCallback(any(), anyString())).thenReturn(new ScmPaymentProvider.Callback("EV1",
                com.xsy.scm.payment.constant.ScmPaymentCallbackEventTypeEnum.PAYMENT_SUCCEEDED, null, "WX20",
                new BigDecimal("90.0000"), null, Map.of(), true, null));

        PaymentCallbackService callbackService = new PaymentCallbackService(callbackEvents, transactions,
                intentsService, mock(com.xsy.scm.payment.service.PaymentRefundService.class), providers);
        when(callbackEvents.markProcessed(70L, "APPLIED", 20L, null)).thenReturn(1);

        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            callbackService.handle("MOCK", Map.of(), "{}");
        }

        verify(transactions).markSucceeded(20L, new BigDecimal("90.0000"), "1:1");
        verify(intents, never()).updateStatus(1L, "PENDING", "SUCCEEDED", "1:1");
        verify(callbackEvents).markProcessed(70L, "APPLIED", 20L, null);
        verifyNoInteractions(receipts, settlements);
    }

    private PaymentIntentCreateForm salesOrderIntent() {
        PaymentIntentCreateForm form = new PaymentIntentCreateForm();
        form.setCustomerId(2L);
        form.setSourceType("SALES_ORDER");
        form.setSourceId(10L);
        form.setMethod("ONLINE");
        form.setProvider("MOCK");
        form.setAmount(new BigDecimal("100.0000"));
        return form;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }
}
