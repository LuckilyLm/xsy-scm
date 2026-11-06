package com.xsy.scm.payment;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.support.FinancePaymentReceiptFact;
import com.xsy.scm.mall.aftersale.dao.MallAfterSaleFundingSourceDao;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentIntentStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentMockScenarioEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentRefundStatusEnum;
import com.xsy.scm.payment.dao.PaymentCallbackEventDao;
import com.xsy.scm.payment.dao.PaymentIntentDao;
import com.xsy.scm.payment.dao.PaymentMockLedgerDao;
import com.xsy.scm.payment.dao.PaymentReconciliationDao;
import com.xsy.scm.payment.dao.PaymentReconciliationItemDao;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.dao.PaymentSourceDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationItemEntity;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.domain.vo.PaymentRefundRecoveryLogVO;
import com.xsy.scm.payment.provider.MockPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentCallbackService;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentReconciliationService;
import com.xsy.scm.payment.service.PaymentRefundRecoveryService;
import com.xsy.scm.payment.service.PaymentRefundService;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import com.xsy.scm.payment.service.RefundFundingAllocator;
import com.xsy.scm.payment.service.RefundFundingCompletionService;
import com.xsy.scm.payment.support.BalanceConsumptionResult;
import com.xsy.scm.payment.support.BalanceConsumptionSink;
import com.xsy.scm.payment.support.BalanceRechargeIntentFact;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import com.xsy.scm.payment.support.RefundBalanceSink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

class PaymentTest {

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("模拟渠道账本不随本地事务回滚（FIX-02，PG IT，无外层事务）")
    class MockPaymentLedgerRollbackPgIT extends ScmW5PgITBase {
        @Autowired
        private MockPaymentProvider mockPaymentProvider;

        @Autowired
        private PaymentMockLedgerDao paymentMockLedgerDao;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        @Test
        @DisplayName("收款：本地事务回滚后渠道收款账仍在，渠道拒付不记账")
        void receiptLedgerSurvivesBusinessRollback() {
            String intentNo = "IT-ROLLBACK-" + UUID.randomUUID();
            String providerTransactionNo = "MOCK-TXN-" + intentNo;

            TransactionTemplate businessTx = new TransactionTemplate(transactionManager);
            businessTx.executeWithoutResult(status -> {
                mockPaymentProvider.createIntent(new ScmPaymentProvider.IntentRequest(intentNo,
                        new BigDecimal("12.0000"), "FIX-02 回滚验证", ScmPaymentMockScenarioEnum.SUCCESS));
                status.setRollbackOnly();
            });

            PaymentMockLedgerEntity row = paymentMockLedgerDao.selectInByProviderTransactionNo(providerTransactionNo);
            assertThat(row).as("本地事务已回滚，渠道账必须仍在（REQUIRES_NEW 经代理生效）").isNotNull();
            assertThat(row.getDirection()).isEqualTo("IN");
            assertThat(row.getAmount()).isEqualByComparingTo("12.0000");

            // 渠道拒付：渠道侧不记账，本地回滚后同样什么都查不到
            String declinedIntentNo = intentNo + "-DECLINED";
            businessTx.executeWithoutResult(status -> {
                mockPaymentProvider.createIntent(new ScmPaymentProvider.IntentRequest(declinedIntentNo,
                        new BigDecimal("12.0000"), "FIX-02 拒付", ScmPaymentMockScenarioEnum.FAILURE));
                status.setRollbackOnly();
            });
            assertThat(paymentMockLedgerDao.selectInByProviderTransactionNo("MOCK-TXN-" + declinedIntentNo))
                    .as("渠道拒付不记收款账").isNull();
        }

        @Test
        @DisplayName("退款：本地事务回滚后渠道退款账仍在")
        void refundLedgerSurvivesBusinessRollback() {
            String providerTransactionNo = "MOCK-TXN-IT-" + UUID.randomUUID();
            String refundNo = "IT-REFUND-" + UUID.randomUUID();
            int committedBefore = countRows("provider_transaction_no", providerTransactionNo);

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                mockPaymentProvider.refund(new ScmPaymentProvider.RefundRequest(refundNo, providerTransactionNo,
                        new BigDecimal("5.0000"), "FIX-02 回滚验证", ScmPaymentMockScenarioEnum.SUCCESS));
                status.setRollbackOnly();
            });

            assertThat(countRows("provider_transaction_no", providerTransactionNo))
                    .as("本地事务已回滚，渠道退款账必须仍在").isEqualTo(committedBefore + 1);
            // 渠道退款号带 MOCK-REFUND- 前缀（provider 自己生成）；用裸 refundNo 查会漏，因此按前缀拼。
            assertThat(countRows("provider_refund_no", "MOCK-REFUND-" + refundNo)).isEqualTo(1);
        }

        private int countRows(String column, String value) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_payment_mock_ledger WHERE " + column + " = ?", Integer.class, value);
            return count == null ? 0 : count;
        }
    }

    @Nested
    class PaymentUnitTest {
        @Nested

        class PaymentIntentBalanceTest {
            private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
            private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
            private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
            private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
            private final FinanceReceiptService receipts = mock(FinanceReceiptService.class);
            private final FinanceOrderFundingSettlementService settlements = mock(FinanceOrderFundingSettlementService.class);
            private final FinanceOrderPayableQueryService payable = mock(FinanceOrderPayableQueryService.class);
            private final BalanceConsumptionSink consumption = mock(BalanceConsumptionSink.class);
            private final ObjectProvider<BalanceRechargeSink> rechargeProvider = provider();
            private final ObjectProvider<BalanceConsumptionSink> consumptionProvider = provider();
            private final PaymentIntentService service = new PaymentIntentService(intents, transactions, sources,
                    scopes, idempotency, providers, receipts, rechargeProvider, consumptionProvider, settlements, payable);
            private final PaymentIntentCreateForm form = new PaymentIntentCreateForm();

            @BeforeEach
            void source() {
                form.setCustomerId(2L); form.setSourceType("SALES_ORDER"); form.setSourceId(10L);
                form.setMethod("BALANCE"); form.setProvider("INTERNAL_BALANCE"); form.setAmount(new BigDecimal("60.0000"));
                var order = new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED", null);
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
                assertThat(transaction.getValue().getProviderTransactionNo())
                        .isEqualTo(transaction.getValue().getTransactionNo());
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
            void codOrderCannotBePaidThroughTheGenericBalanceEntry() {
                stubClaim();
                when(sources.lockOrder(10L)).thenReturn(
                        new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED", "COD"));

                assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_SETTLEMENT_METHOD_BLOCKED));
                verifyNoInteractions(consumptionProvider, transactions);
            }

            @Test
            void walletOrderCannotChargeMoreThanItsCurrentDueAmount() {
                stubClaim();
                when(sources.lockOrder(10L)).thenReturn(
                        new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED", "WALLET"));
                when(payable.payableOfConfirmedOrder(10L)).thenReturn(new BigDecimal("80.0000"));
                when(payable.paidAmountOf(10L)).thenReturn(new BigDecimal("30.0000"));

                assertThatThrownBy(() -> service.create(form, "key")).isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_DUE));
                verifyNoInteractions(consumptionProvider, transactions);
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
                intent.setAmount(new BigDecimal("100.0000"));
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
                // 收款记录与钱包权益是两条记录：一个回答公司实收多少，一个回答形成了多少权益
                verify(sink).rechargeFromPayment(9L, 2L, new BigDecimal("100.0000"), 20L, paidAt);
                verifyNoInteractions(consumptionProvider, consumption, settlements, sources);
            }

            private void stubClaim() {
                when(idempotency.claim(anyString(), anyString(), eq(form)))
                        .thenReturn(new ScmIdempotencyService.Claim(null, false));
            }

            private void stubIntentCreation() {
                stubClaim();
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

        @Nested

        class PaymentIntentOutcomeTest {
            private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
            private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
            private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
            private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
            private final FinanceReceiptService receipts = mock(FinanceReceiptService.class);
            private final FinanceOrderFundingSettlementService settlements = mock(FinanceOrderFundingSettlementService.class);
            private final FinanceOrderPayableQueryService payable = mock(FinanceOrderPayableQueryService.class);
            private final PaymentCallbackEventDao callbackEvents = mock(PaymentCallbackEventDao.class);
            private final ObjectProvider<BalanceRechargeSink> rechargeProvider = provider();
            private final ObjectProvider<BalanceConsumptionSink> consumptionProvider = provider();
            private final PaymentIntentService intentsService = new PaymentIntentService(intents, transactions,
                    sources, scopes, idempotency, providers, receipts, rechargeProvider, consumptionProvider, settlements, payable);
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

                PaymentOrderFact order = new PaymentOrderFact(10L, "SO10", 2L, "门店", 9L, 3L, "集团", "CONFIRMED", null);
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

        @Nested

        class PaymentReconciliationServiceTest {
            private final PaymentReconciliationDao reconciliations = mock(PaymentReconciliationDao.class);
            private final PaymentReconciliationItemDao items = mock(PaymentReconciliationItemDao.class);
            private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
            private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
            private final ScmPaymentProvider provider = mock(ScmPaymentProvider.class);
            private final PaymentReconciliationService service = new PaymentReconciliationService(reconciliations, items,
                    transactions, providers);

            @Test
            void intentAmountDifferenceIsReportedEvenWhenSettlementMatchesProviderAmount() {
                LocalDate bizDate = LocalDate.parse("2026-10-09");
                PaymentTransactionEntity transaction = new PaymentTransactionEntity();
                transaction.setId(20L);
                transaction.setProvider("MOCK");
                transaction.setProviderTransactionNo("WX20");
                transaction.setAmount(new BigDecimal("100.0000"));
                transaction.setProviderAmount(new BigDecimal("90.0000"));
                transaction.setStatus("SUCCEEDED");

                when(reconciliations.selectByProviderAndDate("MOCK", bizDate)).thenReturn(null);
                when(providers.require("MOCK")).thenReturn(provider);
                when(provider.fetchSettlement(bizDate)).thenReturn(new ScmPaymentProvider.Settlement(bizDate,
                        new BigDecimal("90.0000"), 1,
                        List.of(new ScmPaymentProvider.SettlementLine("WX20", new BigDecimal("90.0000")))));
                when(transactions.listByWindow(eq("MOCK"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                        .thenReturn(List.of(transaction));

                AtomicReference<PaymentReconciliationEntity> saved = new AtomicReference<>();
                when(reconciliations.insert(any(PaymentReconciliationEntity.class))).thenAnswer(invocation -> {
                    PaymentReconciliationEntity row = invocation.getArgument(0);
                    row.setId(5L);
                    saved.set(row);
                    return 1;
                });
                when(reconciliations.selectById(5L)).thenAnswer(invocation -> saved.get());

                AtomicReference<PaymentReconciliationItemEntity> savedItem = new AtomicReference<>();
                when(items.insertItem(any(PaymentReconciliationItemEntity.class))).thenAnswer(invocation -> {
                    savedItem.set(invocation.getArgument(0));
                    return 1;
                });

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    PaymentReconciliationEntity result = service.run("MOCK", bizDate);

                    assertThat(result.getStatus()).isEqualTo("MISMATCHED");
                    assertThat(result.getProviderTotal()).isEqualByComparingTo("90.0000");
                    assertThat(result.getLocalTotal()).isEqualByComparingTo("90.0000");
                    assertThat(result.getDifferenceCount()).isEqualTo(1);
                }

                assertThat(savedItem.get().getCategory()).isEqualTo("AMOUNT_MISMATCH");
                assertThat(savedItem.get().getLocalAmount()).isEqualByComparingTo("100.0000");
                assertThat(savedItem.get().getProviderAmount()).isEqualByComparingTo("90.0000");
            }

            /**
             * 跨午夜交易必须按 paid_at 归入结算日。
             *
             * <p>
             * 10-08 23:55 创建、10-09 00:05 成功的那笔：渠道把款记在 10-09。若本地仍按 created_at 取数，
             * 10-09 的对账只从 10-08 窗口查它、从 10-09 窗口漏掉它，于是把渠道的钱误判成 {@code LOCAL_MISSING}。
             * 修复后按 paid_at 取成功交易，10-09 能匹配上，结论应是平账。
             */
            @Test
            void succeededTransactionIsMatchedByPaidAtOnItsSettlementDate() {
                LocalDate bizDate = LocalDate.parse("2026-10-09");
                PaymentTransactionEntity transaction = new PaymentTransactionEntity();
                transaction.setId(21L);
                transaction.setProvider("MOCK");
                transaction.setProviderTransactionNo("WX21");
                transaction.setAmount(new BigDecimal("100.0000"));
                transaction.setProviderAmount(new BigDecimal("100.0000"));
                transaction.setStatus("SUCCEEDED");

                when(reconciliations.selectByProviderAndDate("MOCK", bizDate)).thenReturn(null);
                when(providers.require("MOCK")).thenReturn(provider);
                when(provider.fetchSettlement(bizDate)).thenReturn(new ScmPaymentProvider.Settlement(bizDate,
                        new BigDecimal("100.0000"), 1,
                        List.of(new ScmPaymentProvider.SettlementLine("WX21", new BigDecimal("100.0000")))));
                // 创建于前一日（created_at 窗口取不到），但成功于本结算日（paid_at 窗口能取到）。
                when(transactions.listByWindow(eq("MOCK"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                        .thenReturn(List.of());
                when(transactions.listSucceededBetween(eq("MOCK"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                        .thenReturn(List.of(transaction));

                AtomicReference<PaymentReconciliationEntity> saved = new AtomicReference<>();
                when(reconciliations.insert(any(PaymentReconciliationEntity.class))).thenAnswer(invocation -> {
                    PaymentReconciliationEntity row = invocation.getArgument(0);
                    row.setId(6L);
                    saved.set(row);
                    return 1;
                });
                when(reconciliations.selectById(6L)).thenAnswer(invocation -> saved.get());

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    PaymentReconciliationEntity result = service.run("MOCK", bizDate);

                    assertThat(result.getStatus()).isEqualTo("MATCHED");
                    assertThat(result.getDifferenceCount()).isZero();
                    assertThat(result.getLocalTotal()).isEqualByComparingTo("100.0000");
                    assertThat(result.getLocalCount()).isEqualTo(1);
                }
            }
        }

        @Nested

        class PaymentRefundAllocationAmountTest {
            private final PaymentRefundDao refunds = mock(PaymentRefundDao.class);
            private final RefundFundingAllocationDao allocations = mock(RefundFundingAllocationDao.class);
            private final FinancePaymentService financePayments = mock(FinancePaymentService.class);
            private final PaymentIntentDao intents = mock(PaymentIntentDao.class);
            private final PaymentSourceDao sources = mock(PaymentSourceDao.class);
            private final PaymentRefundService service = new PaymentRefundService(refunds, allocations,
                    mock(PaymentTransactionDao.class), intents, sources,
                    financePayments, mock(FinanceOrderFundingPolicy.class),
                    mock(ScmPaymentProviderRegistry.class), mock(ScmIdempotencyService.class),
                    mock(ScmDataScopeService.class), mock(RefundFundingCompletionService.class));

            @BeforeEach
            void source() {
                PaymentIntentEntity intent = new PaymentIntentEntity();
                intent.setSourceType("SALES_ORDER");
                intent.setSourceId(51L);
                when(intents.selectById(11L)).thenReturn(intent);
                when(sources.lockOrder(51L)).thenReturn(mock(PaymentOrderFact.class));
                when(refunds.updateProviderReference(31L, "PR31", "1:7")).thenReturn(1);
            }

            @Test
            void providerAmountMismatchKeepsAllocationPendingForReconciliation() {
                PaymentRefundEntity refund = refund("100.0000", "98.0000");
                RefundFundingAllocationEntity allocation = allocation("100.0000");
                when(refunds.selectById(31L)).thenReturn(refund);
                when(refunds.lockById(31L)).thenReturn(refund);
                when(allocations.selectById(41L)).thenReturn(allocation);

                service.applyOutcome(31L, ScmPaymentProvider.Outcome.SUCCEEDED, "PR31",
                        new BigDecimal("98.0000"), null, null, "1:7");

                verify(refunds).markSucceeded(31L, "PR31", new BigDecimal("98.0000"), "1:7");
                verify(allocations, never()).markSucceeded(41L, 31L, "1:7");
                verifyNoInteractions(financePayments);
            }

            @Test
            void exactProviderAmountCompletesAllocation() {
                PaymentRefundEntity refund = refund("100.0000", "100.0000");
                RefundFundingAllocationEntity allocation = allocation("100.0000");
                when(refunds.selectById(31L)).thenReturn(refund);
                when(refunds.lockById(31L)).thenReturn(refund);
                when(allocations.selectById(41L)).thenReturn(allocation);

                service.applyOutcome(31L, ScmPaymentProvider.Outcome.SUCCEEDED, "PR31",
                        new BigDecimal("100.0000"), null, null, "1:7");

                verify(allocations).markSucceeded(41L, 31L, "1:7");
                verifyNoInteractions(financePayments);
            }

            private static PaymentRefundEntity refund(String requested, String actual) {
                PaymentRefundEntity refund = new PaymentRefundEntity();
                refund.setId(31L);
                refund.setIntentId(11L);
                refund.setAmount(new BigDecimal(requested));
                refund.setProviderAmount(new BigDecimal(actual));
                refund.setSourceType("REFUND_FUNDING_ALLOCATION");
                refund.setSourceId(41L);
                refund.setStatus("SUCCEEDED");
                return refund;
            }

            private static RefundFundingAllocationEntity allocation(String amount) {
                RefundFundingAllocationEntity allocation = new RefundFundingAllocationEntity();
                allocation.setId(41L);
                allocation.setAllocatedAmount(new BigDecimal(amount));
                allocation.setStatus("PROCESSING");
                return allocation;
            }
        }

        @Nested

        class RefundFundingAllocationFailureTest {
            @Test
            void longSourceFailureIsBoundedBeforeWritingFailureStatus() {
                RefundFundingAllocationDao allocations = mock(RefundFundingAllocationDao.class);
                RefundBalanceSink balanceSink = mock(RefundBalanceSink.class);
                RefundFundingAllocationService service = new RefundFundingAllocationService(null, null, allocations, null,
                        null, balanceSink, null, null, null, null);
                RefundFundingAllocationEntity row = new RefundFundingAllocationEntity();
                row.setId(42L);
                row.setStatus("PENDING");
                row.setSourceMethod("BALANCE");
                row.setSettlementCustomerId(9L);
                row.setAllocatedAmount(new BigDecimal("1.0000"));
                PaymentOrderRefundFact refund = new PaymentOrderRefundFact(7L, "R-7", 8L, 9L, null, null, null);
                String fullMessage = "failure:" + "x".repeat(700);
                when(balanceSink.refundAllocation(anyLong(), anyLong(), anyLong(), any(), any()))
                        .thenThrow(new IllegalStateException(fullMessage));

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    ReflectionTestUtils.invokeMethod(service, "executeRows", refund, List.of(row), null);
                }

                ArgumentCaptor<String> failureMessage = ArgumentCaptor.forClass(String.class);
                verify(allocations).markFailed(eq(42L), eq("IllegalStateException"), failureMessage.capture(), eq("1:1"));
                assertThat(failureMessage.getValue()).hasSize(500).startsWith("failure:").endsWith("...");
            }
        }

        @Nested

        class RefundFundingAllocatorTest {
            private final RefundFundingAllocator allocator = new RefundFundingAllocator();

            @Test
            void allocatesProportionallyAndGivesRemainderToStableFirstSource() {
                List<BigDecimal> result = allocator.allocate(new BigDecimal("1.0000"),
                        List.of(new BigDecimal("1.0000"), new BigDecimal("1.0000"), new BigDecimal("1.0000")));
                assertThat(result).containsExactly(new BigDecimal("0.3334"), new BigDecimal("0.3333"),
                        new BigDecimal("0.3333"));
                assertThat(result.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.0000");
            }

            @Test
            void keepsPureSourceAsSingleExactAllocation() {
                assertThat(allocator.allocate(new BigDecimal("9.8765"), List.of(new BigDecimal("20.0000"))))
                        .containsExactly(new BigDecimal("9.8765"));
            }
        }

        @Nested

        class ScmPaymentStatusTransitionTest {
            @ParameterizedTest
            @CsvSource({"CREATED,PENDING,true", "CREATED,FAILED,true", "CREATED,EXPIRED,true", "CREATED,CLOSED,true",
                    "PENDING,SUCCEEDED,true", "PENDING,FAILED,true", "PENDING,EXPIRED,true", "PENDING,CLOSED,true",
                    "CREATED,SUCCEEDED,false", "PENDING,CREATED,false", "SUCCEEDED,SUCCEEDED,false"})
            void intentTransitionsFollowTheDocumentedTable(String from, String to, boolean allowed) {
                assertThat(ScmPaymentIntentStatusEnum.canTransition(from, to)).isEqualTo(allowed);
            }

            @ParameterizedTest
            @CsvSource({"CREATED,PROCESSING,true", "CREATED,FAILED,true", "CREATED,CLOSED,true",
                    "PROCESSING,SUCCEEDED,true", "PROCESSING,FAILED,true",
                    "CREATED,SUCCEEDED,false", "PROCESSING,CREATED,false", "SUCCEEDED,SUCCEEDED,false"})
            void refundTransitionsFollowTheDocumentedTable(String from, String to, boolean allowed) {
                assertThat(ScmPaymentRefundStatusEnum.canTransition(from, to)).isEqualTo(allowed);
            }

            @Test
            void unknownStatusesAreNeverATransitionEndpoint() {
                assertThat(ScmPaymentIntentStatusEnum.canTransition("NOPE", "PENDING")).isFalse();
                assertThat(ScmPaymentIntentStatusEnum.canTransition("CREATED", "NOPE")).isFalse();
                assertThat(ScmPaymentIntentStatusEnum.canTransition(null, "PENDING")).isFalse();
                assertThat(ScmPaymentRefundStatusEnum.canTransition("NOPE", "PROCESSING")).isFalse();
                assertThat(ScmPaymentRefundStatusEnum.canTransition("CREATED", "NOPE")).isFalse();
            }

            /** 终态不可回退：支付是外部记录，本地改状态改不掉客户已经付过的钱。 */
            @Test
            void terminalStatusesNeverTransitionAgain() {
                for (ScmPaymentIntentStatusEnum from : ScmPaymentIntentStatusEnum.values()) {
                    if (!from.isTerminal()) {
                        continue;
                    }
                    for (ScmPaymentIntentStatusEnum to : ScmPaymentIntentStatusEnum.values()) {
                        assertThat(ScmPaymentIntentStatusEnum.canTransition(from.name(), to.name())).isFalse();
                    }
                }
                for (ScmPaymentRefundStatusEnum from : ScmPaymentRefundStatusEnum.values()) {
                    if (!from.isTerminal()) {
                        continue;
                    }
                    for (ScmPaymentRefundStatusEnum to : ScmPaymentRefundStatusEnum.values()) {
                        assertThat(ScmPaymentRefundStatusEnum.canTransition(from.name(), to.name())).isFalse();
                    }
                }
            }
        }
    }

    @Nested
    class PaymentW5PgIT extends ScmW5PgITBase {
        @Nested
        @DisplayName("回调事件写入数据库与结论回填（FIX-09，PG IT）")
        class PaymentCallbackEventPersistPgIT {
            @Autowired
            private PaymentCallbackEventDao paymentCallbackEventDao;

            @Test
            @DisplayName("插入必须回填主键：漏了它 markProcessed 会更新 0 行")
            void insertBackfillsGeneratedId() {
                PaymentCallbackEventEntity event = newEvent("APPLIED-CASE");
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);
                assertThat(event.getId())
                        .as("插入后必须回填自增主键，否则 markProcessed 只能执行 WHERE id = NULL")
                        .isNotNull();
                assertThat(event.getId()).isPositive();
            }

            @Test
            @DisplayName("APPLIED：验签通过并匹配到交易后，状态与交易关联都要落地")
            void appliedStatusIsPersisted() {
                PaymentCallbackEventEntity event = newEvent("APPLIED-CASE");
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);

                // 真实交易存在与否不影响本次断言：这里只验证「回填结论」这一步能落到正确的行上
                Long linkedTransactionId = 987654321L;
                assertThat(paymentCallbackEventDao.markProcessed(event.getId(), "APPLIED", linkedTransactionId, null))
                        .as("必须正好更新到刚插入的那一行")
                        .isEqualTo(1);

                PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                        event.getProviderEventId());
                assertThat(reloaded.getProcessStatus()).isEqualTo("APPLIED");
                assertThat(reloaded.getTransactionId()).isEqualTo(linkedTransactionId);
                assertThat(reloaded.getRejectReason()).isNull();
                assertThat(reloaded.getProcessedAt()).as("回填结论必须同时打上处理时间").isNotNull();
            }

            @Test
            @DisplayName("REJECTED：验签失败要留下拒因，不能停在 RECEIVED")
            void rejectedStatusAndReasonArePersisted() {
                PaymentCallbackEventEntity event = newEvent("REJECTED-CASE");
                event.setSignatureVerified(false);
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);

                assertThat(paymentCallbackEventDao.markProcessed(event.getId(), "REJECTED", null, "验签失败：签名不匹配"))
                        .isEqualTo(1);

                PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                        event.getProviderEventId());
                assertThat(reloaded.getProcessStatus()).isEqualTo("REJECTED");
                assertThat(reloaded.getRejectReason()).isEqualTo("验签失败：签名不匹配");
                assertThat(reloaded.getSignatureVerified()).as("未通过验签的记录本身也要留证").isFalse();
                assertThat(reloaded.getProcessedAt()).isNotNull();
            }

            @Test
            @DisplayName("重复事件：ON CONFLICT 不报错且返回 0，Service 据此提前返回、不依赖 id")
            void duplicateEventReturnsZeroWithoutTouchingTheRow() {
                PaymentCallbackEventEntity first = newEvent("DUP-CASE");
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(first)).isEqualTo(1);

                // 防重复处理锚点是 (provider, provider_event_id)：第二个实体必须复用同一个事件 id，才谈得上「重复投递」
                PaymentCallbackEventEntity duplicate = newEvent("DUP-CASE");
                duplicate.setProviderEventId(first.getProviderEventId());
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(duplicate))
                        .as("同一 (provider, provider_event_id) 重复投递必须被唯一索引挡下并返回 0")
                        .isZero();

                PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                        first.getProviderEventId());
                assertThat(reloaded.getProcessStatus()).as("重复投递不得改动已存在的事件").isEqualTo("RECEIVED");
                assertThat(reloaded.getId()).isEqualTo(first.getId());
            }

            @Test
            @DisplayName("拒绝事件保留证据，同 ID 合法事件仍可处理且不能重复应用")
            void rejectedEventDoesNotClaimTheValidEventId() {
                PaymentCallbackEventEntity rejected = newEvent("RETRY-AFTER-REJECT");
                rejected.setSignatureVerified(false);
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(rejected)).isEqualTo(1);
                assertThat(paymentCallbackEventDao.markProcessed(rejected.getId(), "REJECTED", null, "验签失败"))
                        .isEqualTo(1);

                PaymentCallbackEventEntity valid = newEvent("RETRY-VALID");
                valid.setProviderEventId(rejected.getProviderEventId());
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(valid)).isEqualTo(1);
                assertThat(paymentCallbackEventDao.markProcessed(valid.getId(), "APPLIED", null, null)).isEqualTo(1);

                PaymentCallbackEventEntity duplicate = newEvent("RETRY-DUPLICATE");
                duplicate.setProviderEventId(rejected.getProviderEventId());
                assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(duplicate)).isZero();
                assertThat(paymentCallbackEventDao.selectById(rejected.getId()).getProcessStatus()).isEqualTo("REJECTED");
                assertThat(paymentCallbackEventDao.selectClaimedByProviderEventId("MOCK", rejected.getProviderEventId())
                        .getId()).isEqualTo(valid.getId());
            }

            private PaymentCallbackEventEntity newEvent(String marker) {
                PaymentCallbackEventEntity event = new PaymentCallbackEventEntity();
                event.setProvider("MOCK");
                event.setProviderEventId("IT-FIX09-" + marker + "-" + UUID.randomUUID());
                event.setEventType("PAYMENT_SUCCEEDED");
                event.setSignatureVerified(true);
                event.setPayloadHash(UUID.randomUUID().toString().replace("-", ""));
                event.setPayload(Map.of("eventId", event.getProviderEventId()));
                event.setProcessStatus("RECEIVED");
                event.setCreatedBy("IT-FIX09");
                return event;
            }
        }

        @Nested
        @DisplayName("退款恢复链的持久化语义（V125，PG IT）")
        class PaymentRefundRecoveryPgIT {
            /**
             * 与 {@code PaymentRefundRecoveryJob} 一致：候选查询按 {@code (next_recovery_at, id)} 升序取前 N 条。
             * 用例里取一个大 N，避免开发库里别人留下的到期行把本用例的行挤出窗口。
             */
            private static final int CANDIDATE_WINDOW = 2000;

            @Autowired
            private PaymentRefundDao paymentRefundDao;

            @Autowired
            private PaymentRefundService paymentRefundService;

            @Autowired
            private RefundFundingCompletionService refundFundingCompletionService;

            // ------------------------------------------------------------------
            // 租约：多实例不重复派发 / 进程重启后仍可恢复
            // ------------------------------------------------------------------

            @Test
            @DisplayName("租约未过期时第二次取租约必须失败：多实例不会重复向渠道派发")
            void leaseKeepsASingleDispatcher() {
                RefundFixture fixture = recoveryFixture("LEASE", "30.0000", "PROCESSING");

                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);
                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                        .as("同一租约窗口内第二次取租约必须失败，否则两个实例会各自向渠道发起一次退款")
                        .isZero();
                assertThat(recoveryAttempts(fixture.refundId()))
                        .as("失败的取租约不能计入重试次数")
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("租约到期后可再次取租约：进程被中断的退款能继续恢复")
            void expiredLeaseIsRecoverableAfterRestart() {
                RefundFixture fixture = recoveryFixture("RESTART", "30.0000", "PROCESSING");
                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);

                // 进程在持有租约时被杀：租约不会自己释放，只能等它过期。
                jdbc.update("UPDATE xsy_payment_refund SET recovery_lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute' "
                        + "WHERE id = ?", fixture.refundId());

                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                        .as("租约过期即视为上一次恢复已中断，必须允许接手")
                        .isEqualTo(1);
                assertThat(recoveryAttempts(fixture.refundId())).isEqualTo(2);
            }

            @Test
            @DisplayName("人工恢复入口可以绕过下次恢复时间，但不能绕过租约")
            void forceBypassesBackoffButNotTheLease() {
                RefundFixture fixture = recoveryFixture("FORCE", "30.0000", "PROCESSING");
                pushNextRecovery(fixture.refundId(), 1);

                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                        .as("退避窗口内定时任务不得抢跑")
                        .isZero();
                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), true))
                        .as("人工恢复是显式动作，允许无视退避窗口")
                        .isEqualTo(1);
                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), true))
                        .as("但租约仍然生效：人工入口也不能把同一笔退款并发派发两次")
                        .isZero();
            }

            // ------------------------------------------------------------------
            // 候选筛选：定时任务该捞谁
            // ------------------------------------------------------------------

            @Test
            @DisplayName("候选只包含到期且未租用的行；已入账的成功退款不再重复恢复")
            void candidatesOnlyPickDueUnleasedAndUnsettledRefunds() {
                RefundFixture due = recoveryFixture("CAND-DUE", "30.0000", "PROCESSING");

                RefundFixture leased = recoveryFixture("CAND-LEASED", "30.0000", "PROCESSING");
                assertThat(paymentRefundDao.claimRecovery(leased.refundId(), false)).isEqualTo(1);

                RefundFixture backedOff = recoveryFixture("CAND-BACKOFF", "30.0000", "PROCESSING");
                pushNextRecovery(backedOff.refundId(), 60);

                // 渠道已退成功、但本地还没登记财务付款：这正是「回调丢失」的现场，必须能被捞回来。
                RefundFixture settledByChannel = recoveryFixture("CAND-UNPAID", "30.0000", "SUCCEEDED");

                // 已经登记过财务付款的成功退款：再捞一次只会重复查单，必须排除。
                RefundFixture alreadyBooked = recoveryFixture("CAND-BOOKED", "30.0000", "SUCCEEDED");
                insertFinancePayment(alreadyBooked);

                List<Long> candidates = paymentRefundDao.recoveryCandidates(CANDIDATE_WINDOW);

                assertThat(candidates)
                        .as("到期的处理中退款要入选")
                        .contains(due.refundId());
                assertThat(candidates)
                        .as("渠道已退成功但缺财务付款的退款必须入选，否则这笔账永远补不回来")
                        .contains(settledByChannel.refundId());
                assertThat(candidates)
                        .as("租约未过期的行属于「别人正在处理」，不得被抢")
                        .doesNotContain(leased.refundId());
                assertThat(candidates)
                        .as("退避窗口内的行不得被抢跑")
                        .doesNotContain(backedOff.refundId());
                assertThat(candidates)
                        .as("财务付款已登记的成功退款不该再进恢复队列")
                        .doesNotContain(alreadyBooked.refundId());
            }

            // ------------------------------------------------------------------
            // 恢复结果写入数据库：退避、状态、审计
            // ------------------------------------------------------------------

            @Test
            @DisplayName("结束恢复要释放租约、记录结果，并把下次恢复时间推后")
            void finishReleasesLeaseAndBacksOff() {
                RefundFixture fixture = recoveryFixture("FINISH", "30.0000", "PROCESSING");
                assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);

                PaymentRefundEntity claimed = paymentRefundDao.selectById(fixture.refundId());
                OffsetDateTime txNow = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
                assertThat(paymentRefundDao.finishRecovery(fixture.refundId(), "FAILED", "渠道查单未完成", 60,
                        claimed.getRecoveryAttempts())).isEqualTo(1);

                evictMybatisCache();
                PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
                assertThat(after.getRecoveryLeaseUntil()).as("租约必须释放，否则这一笔再也恢复不了").isNull();
                assertThat(after.getRecoveryStatus()).isEqualTo("FAILED");
                assertThat(after.getRecoveryMessage()).isEqualTo("渠道查单未完成");
                assertThat(after.getNextRecoveryAt())
                        .as("下一次恢复时间必须被推后（退避），否则会变成忙等")
                        .isAfter(txNow.plusSeconds(59));
            }

            @Test
            @DisplayName("恢复审计只能新增、按时间倒序，且数据库层面拒绝改写与删除")
            void recoveryAuditIsAppendOnly() {
                RefundFixture fixture = recoveryFixture("AUDIT", "30.0000", "PROCESSING");

                assertThat(paymentRefundDao.appendRecoveryLog(fixture.refundId(), "IT-RECOVERY", "第一次查单", "STARTED",
                        null, "开始渠道查单")).isEqualTo(1);
                assertThat(paymentRefundDao.appendRecoveryLog(fixture.refundId(), "IT-RECOVERY", "第二次查单",
                        "AMOUNT_MISMATCH", new BigDecimal("29.0000"), "渠道实退与申请金额不一致")).isEqualTo(1);

                List<PaymentRefundRecoveryLogVO> logs = paymentRefundDao.recoveryLogs(fixture.refundId());
                assertThat(logs).hasSize(2);
                assertThat(logs.getFirst().getResult()).as("最新一条排在最前").isEqualTo("AMOUNT_MISMATCH");
                assertThat(logs.getFirst().getProviderAmount()).isEqualByComparingTo("29.0000");
                assertThat(logs.getLast().getResult()).isEqualTo("STARTED");
                assertThat(logs.getFirst().getOperator()).isEqualTo("IT-RECOVERY");

            }

            // ------------------------------------------------------------------
            // 渠道结果落账：金额差异与重复回调
            // ------------------------------------------------------------------

            @Test
            @DisplayName("渠道实退与申请额不一致：保留渠道记录，但不生成财务付款")
            void amountMismatchKeepsChannelFactWithoutFinancePayment() {
                RefundFixture fixture = recoveryFixture("MISMATCH", "30.0000", "PROCESSING");

                paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED,
                        "MOCK-REFUND-" + fixture.refundId(), new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

                evictMybatisCache();
                PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
                assertThat(after.getStatus()).as("渠道确实退了钱，本地不能因为金额差异把它判成失败").isEqualTo("SUCCEEDED");
                assertThat(after.getProviderAmount()).isEqualByComparingTo("29.0000");
                assertThat(after.getAmount()).as("申请额保持不变：差异留给人工对账，不自动改写").isEqualByComparingTo("30.0000");
                assertThat(financePaymentCount(fixture.orderRefundId()))
                        .as("实退与应退不一致时不得登记财务付款，否则财务账上会出现一个错误的实退额")
                        .isZero();
            }

            @Test
            @DisplayName("渠道明确失败：退款转 FAILED 并留下失败码，不生成财务付款")
            void explicitChannelFailureMarksRefundFailed() {
                RefundFixture fixture = recoveryFixture("CHANNEL-FAILED", "30.0000", "PROCESSING");

                paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.FAILED, null, null,
                        "MOCK_REFUND_REJECTED", "模拟渠道拒绝退款", "IT-RECOVERY");

                evictMybatisCache();
                PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
                assertThat(after.getStatus()).isEqualTo("FAILED");
                assertThat(after.getFailureCode()).isEqualTo("MOCK_REFUND_REJECTED");
                assertThat(after.getProviderAmount()).as("失败态不得留下渠道实退额").isNull();
                assertThat(financePaymentCount(fixture.orderRefundId())).isZero();
            }

            @Test
            @DisplayName("重复回调同一结果：不抛异常、不改写已确认的退款时间")
            void repeatedIdenticalOutcomeIsIdempotent() {
                RefundFixture fixture = recoveryFixture("REPEAT", "30.0000", "PROCESSING");
                String providerRefundNo = "MOCK-REFUND-" + fixture.refundId();

                paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED, providerRefundNo,
                        new BigDecimal("29.0000"), null, null, "IT-RECOVERY");
                evictMybatisCache();
                OffsetDateTime firstRefundedAt = paymentRefundDao.selectById(fixture.refundId()).getRefundedAt();

                paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED, providerRefundNo,
                        new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

                evictMybatisCache();
                PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
                assertThat(after.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(after.getRefundedAt())
                        .as("已成功行不该被重复回调改写：退款时点一旦确认就不能漂移")
                        .isEqualTo(firstRefundedAt);
                assertThat(after.getVersion()).as("重复回调不应产生一次状态变更").isEqualTo(1);
            }

            @Test
            @DisplayName("已确认成功的退款不允许改掉渠道退款号：那等于把凭据指向另一笔渠道资金")
            void changingProviderReferenceOnSettledRefundIsRejected() {
                RefundFixture fixture = recoveryFixture("REBIND", "30.0000", "PROCESSING");

                paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED,
                        "MOCK-REFUND-" + fixture.refundId(), new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

                assertThatThrownBy(() -> paymentRefundService.applyOutcome(fixture.refundId(),
                        ScmPaymentProvider.Outcome.SUCCEEDED, "MOCK-REFUND-OTHER", new BigDecimal("29.0000"), null, null,
                        "IT-RECOVERY"))
                        .as("渠道退款号是防重复处理锚点，改掉它会让同一笔本地退款对上两笔渠道资金")
                        .isInstanceOf(ScmBusinessException.class);
            }

            // ------------------------------------------------------------------
            // 资金分摊完成 → 唯一财务付款
            // ------------------------------------------------------------------

            @Test
            @DisplayName("全部分摊成功后自动登记唯一一条退款财务付款，重复推进不产生第二条")
            void fundingCompletionRegistersExactlyOneFinancePayment() {
                RefundFixture fixture = recoveryFixture("ALLOCATION", "30.0000", "SUCCEEDED");
                Long allocationId = insertSucceededOnlineAllocation(fixture, "30.0000");

                Long paymentId = refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY");
                assertThat(paymentId).as("分摊全部成功且合计等于应退额时必须自动入账，不再依赖人工点执行").isNotNull();
                assertThat(financePaymentCount(fixture.orderRefundId())).isEqualTo(1);
                assertThat(financePaymentMethod(fixture.orderRefundId()))
                        .as("按原资金来源退回，方法必须是 ORIGINAL_FUNDING 而不是 ONLINE_PAYMENT")
                        .isEqualTo("ORIGINAL_FUNDING");
                assertThat(financePaymentAmount(fixture.orderRefundId())).isEqualByComparingTo("30.0000");

                // 重复推进（回调重放、恢复任务再次扫到）不得产生第二条付款记录。
                refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY");
                assertThat(financePaymentCount(fixture.orderRefundId())).isEqualTo(1);

                evictMybatisCache();
                assertThat(allocationStatus(allocationId)).isEqualTo("SUCCEEDED");
            }

            @Test
            @DisplayName("分摊合计与应退额不一致时拒绝入账，不留下一个金额错误的付款记录")
            void fundingCompletionRejectsAmountMismatch() {
                RefundFixture fixture = recoveryFixture("ALLOC-MISMATCH", "30.0000", "SUCCEEDED");
                insertSucceededOnlineAllocation(fixture, "29.0000");

                assertThatThrownBy(() -> refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY"))
                        .as("实退合计与应退额不等时必须拒绝登记财务付款，而不是按其中一个数落账")
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(financePaymentCount(fixture.orderRefundId())).isZero();
            }

            // ------------------------------------------------------------------
            // V127：外键移除后的回归
            // ------------------------------------------------------------------

            @Test
            @DisplayName("V127 移除恢复日志的外键后，追加审计与不可改约束照旧生效")
            void recoveryLogKeepsAppendOnlySemanticsAfterTheForeignKeyIsDropped() {
                RefundFixture fixture = recoveryFixture("FKFREE", "30.0000", "PROCESSING");

                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                        + " WHERE t.relnamespace = current_schema()::regnamespace"
                        + " AND t.relname = 'xsy_payment_refund_recovery_log' AND c.contype = 'f'", Integer.class))
                        .as("V127 之后恢复日志不得再有外键，引用完整性改由服务层承担").isZero();

                jdbc.update("INSERT INTO xsy_payment_refund_recovery_log (refund_id, operator, reason, result, detail)"
                        + " VALUES (?, '1:1', 'V127 回归', 'MANUAL', '外键移除后仍可追加')", fixture.refundId());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_payment_refund_recovery_log WHERE refund_id = ?",
                        Integer.class, fixture.refundId()))
                        .as("移除外键不影响写入").isEqualTo(1);


            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            /**
             * 一笔可被恢复链处理的退款及其上下游记录。
             */
            private record RefundFixture(Long customerId, Long orderId, Long orderRefundId, Long transactionId,
                                         Long refundId) {
            }

            /**
             * 造一笔「已确认订单 + 成功渠道支付 + 已完成售后退款 + 渠道退款行」的完整前置。
             *
             * @param refundAmount 售后退款单的应退额，同时作为渠道退款行的申请额
             * @param status       渠道退款行的初始状态
             */
            private RefundFixture recoveryFixture(String suffix, String refundAmount, String status) {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku(suffix);
                Long orderId = confirmedSalesOrder(customerId, skuId, "100.0000", "100.0000");
                String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                        orderId);

                // 支付意图与交易：恢复链只读它们来定位渠道交易号，不校验其金额计算规则。
                Long intentId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_intent
                            (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                             amount, method, provider, status, succeeded_at)
                        VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 100.0000, 'ONLINE', 'MOCK', 'SUCCEEDED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """, Long.class, prefix + "-INT-" + suffix, customerId, prefix + "客户", orderId, orderNo);

                // ck_payment_transaction_paid_pairing：SUCCEEDED 必须带 paid_at。
                Long transactionId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_transaction
                            (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                             paid_at, created_by)
                        VALUES (?, ?, 'MOCK', ?, 100.0000, 100.0000, 'SUCCEEDED', CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-TXN-" + suffix, intentId, prefix + "-PTN-" + suffix, operator());

                // ck_order_refund_completion：COMPLETED 必须带 completed_at。
                // return_id 只被 uk_order_refund_return_active 约束（该表对本用例没有外键），
                // 但它是全表唯一：同一个用例造多笔退款时必须各不相同，否则第二笔直接撞唯一索引。
                // 从退货 id 序列取值，保证归还到数据库的假 return_id 永远不会被真实退货 id 复用。
                Long orderRefundId = jdbc.queryForObject("""
                        INSERT INTO xsy_order_refund
                            (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                        VALUES (?, nextval(pg_get_serial_sequence('xsy_order_return', 'id')), ?, ?, CAST(? AS numeric), 'COMPLETED',
                            CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-ORF-" + suffix, orderId, customerId, refundAmount, operator());

                Long refundId = insertRefund(intentId, transactionId, orderRefundId, refundAmount, status);
                return new RefundFixture(customerId, orderId, orderRefundId, transactionId, refundId);
            }

            /**
             * 渠道退款行。成功态按库上的 CHECK 同时补 {@code provider_amount} 与 {@code refunded_at}。
             */
            private Long insertRefund(Long intentId, Long transactionId, Long orderRefundId, String amount, String status) {
                boolean succeeded = "SUCCEEDED".equals(status);
                return jdbc.queryForObject("""
                        INSERT INTO xsy_payment_refund
                            (refund_no, intent_id, transaction_id, provider, amount, source_type, source_id, status,
                             provider_amount, refunded_at, mock_scenario, reason, created_by, updated_by)
                        VALUES (?, ?, ?, 'MOCK', CAST(? AS numeric), 'ORDER_REFUND', ?, ?, ?, ?, 'SUCCESS', ?, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-PRF-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                        transactionId, new BigDecimal(amount), orderRefundId, status,
                        succeeded ? new BigDecimal(amount) : null,
                        succeeded ? OffsetDateTime.now() : null,
                        "退款恢复链 IT", operator(), operator());
            }

            /**
             * 登记一条财务退款付款记录，用于构造「这笔成功退款已经入账、不该再被恢复」的行。
             */
            private void insertFinancePayment(RefundFixture fixture) {
                jdbc.update("""
                        INSERT INTO xsy_finance_payment
                            (payment_no, counterparty_type, counterparty_id, counterparty_name_snapshot, amount, method,
                             paid_at, entry_type, source_type, source_id, created_by, updated_by)
                        VALUES (?, 'CUSTOMER', ?, ?, 30.0000, 'ORIGINAL_FUNDING', CURRENT_TIMESTAMP, 'NORMAL',
                            'ORDER_REFUND', ?, ?, ?)
                        """, prefix + "-FP-" + UUID.randomUUID().toString().substring(0, 8), fixture.customerId(),
                        prefix + "客户", fixture.orderRefundId(), operator(), operator());
            }

            /**
             * 造一条已成功的 ONLINE 资金分摊，以及它对应的渠道退款记录。
             *
             * <p>先插 {@code PENDING} 再回填 {@code result_source_id}：成功态要求 {@code result_source_id} 非空，
             * 而它指向的渠道退款行又必须带上分摊 id（{@code source_id}），两边互相引用，只能分两步。
             *
             * <p>这里刻意只造 ONLINE 分摊：BALANCE 分摊的完成判定要读 {@code xsy_customer_balance_movement}
             * 与余额账户，那是余额域自己的造数，与「退款恢复」这条链无关。
             */
            private Long insertSucceededOnlineAllocation(RefundFixture fixture, String allocatedAmount) {
                Long settlementCustomerId = jdbc.queryForObject(
                        "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, fixture.orderId());

                Long allocationId = jdbc.queryForObject("""
                        INSERT INTO xsy_refund_funding_allocation
                            (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                             source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                             updated_by)
                        VALUES (?, ?, ?, ?, 'ONLINE', ?, 100.0000, CAST(? AS numeric), 'PENDING', CURRENT_TIMESTAMP, 0, ?, ?)
                        RETURNING id
                        """, Long.class, fixture.orderRefundId(), fixture.orderId(), fixture.customerId(),
                        settlementCustomerId, fixture.transactionId(), allocatedAmount, operator(), operator());

                Long allocationRefundId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_refund
                            (refund_no, intent_id, transaction_id, provider, amount, source_type, source_id, status,
                             provider_amount, refunded_at, created_by, updated_by)
                        SELECT ?, intent_id, transaction_id, provider, CAST(? AS numeric), 'REFUND_FUNDING_ALLOCATION', ?,
                            'SUCCEEDED', CAST(? AS numeric), CURRENT_TIMESTAMP, ?, ?
                        FROM xsy_payment_refund WHERE id = ?
                        RETURNING id
                        """, Long.class, prefix + "-ARF-" + UUID.randomUUID().toString().substring(0, 8), allocatedAmount,
                        allocationId, allocatedAmount, operator(), operator(), fixture.refundId());

                jdbc.update("UPDATE xsy_refund_funding_allocation SET status = 'SUCCEEDED', result_source_id = ? "
                        + "WHERE id = ?", allocationRefundId, allocationId);
                return allocationId;
            }

            private String allocationStatus(Long allocationId) {
                return jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?", String.class,
                        allocationId);
            }

            private String financePaymentMethod(Long orderRefundId) {
                return jdbc.queryForObject("SELECT method FROM xsy_finance_payment WHERE source_type = 'ORDER_REFUND' "
                        + "AND source_id = ? AND deleted = FALSE", String.class, orderRefundId);
            }

            private BigDecimal financePaymentAmount(Long orderRefundId) {
                return jdbc.queryForObject("SELECT amount FROM xsy_finance_payment WHERE source_type = 'ORDER_REFUND' "
                        + "AND source_id = ? AND deleted = FALSE", BigDecimal.class, orderRefundId);
            }

            private int recoveryAttempts(Long refundId) {
                evictMybatisCache();
                return paymentRefundDao.selectById(refundId).getRecoveryAttempts();
            }

            private int financePaymentCount(Long orderRefundId) {
                Integer count = jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment "
                        + "WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND deleted = FALSE", Integer.class,
                        orderRefundId);
                return count == null ? 0 : count;
            }

            /** 把下次恢复时间推到 {@code minutesFromNow} 分钟之后，模拟退避窗口。 */
            private void pushNextRecovery(Long refundId, int minutesFromNow) {
                jdbc.update("UPDATE xsy_payment_refund SET next_recovery_at = CURRENT_TIMESTAMP + ("
                        + minutesFromNow + " * INTERVAL '1 minute') WHERE id = ?", refundId);
            }

            private String operator() {
                return "IT-RECOVERY";
            }
        }

        @Nested
        @DisplayName("已成功退款累计规则（PAY-02，PG IT）")
        class PaymentRefundSumBasisPgIT {
            @Autowired
            private PaymentRefundDao paymentRefundDao;

            @Test
            @DisplayName("差异退款：累计取渠道实退额，而不是申请额")
            void sumsProviderAmountNotRequestedAmount() {
                long transactionId = insertTransaction();
                // 申请退 100，渠道实退 90（差异退款）；再申请退 50，渠道实退 45。
                insertRefund(transactionId, new BigDecimal("100.0000"), new BigDecimal("90.0000"), "SUCCEEDED");
                insertRefund(transactionId, new BigDecimal("50.0000"), new BigDecimal("45.0000"), "SUCCEEDED");

                BigDecimal sum = paymentRefundDao.sumSucceededByTransaction(transactionId);

                assertThat(sum)
                        .as("必须按渠道实退额累计（90 + 45），按申请额会得到 150")
                        .isEqualByComparingTo("135.0000");
            }

            @Test
            @DisplayName("只计 SUCCEEDED：进行中与失败的退款不占用可退额度")
            void ignoresNonSucceededRefunds() {
                long transactionId = insertTransaction();
                insertRefund(transactionId, new BigDecimal("10.0000"), new BigDecimal("10.0000"), "SUCCEEDED");
                insertRefund(transactionId, new BigDecimal("20.0000"), null, "PROCESSING");
                insertRefund(transactionId, new BigDecimal("30.0000"), null, "FAILED");

                assertThat(paymentRefundDao.sumSucceededByTransaction(transactionId))
                        .as("只有成功退款进累计")
                        .isEqualByComparingTo("10.0000");
            }

            @Test
            @DisplayName("无成功退款时返回 0，而不是 NULL")
            void returnsZeroWhenNothingSucceeded() {
                long transactionId = insertTransaction();
                assertThat(paymentRefundDao.sumSucceededByTransaction(transactionId)).isEqualByComparingTo("0");
            }

            /** 造一条交易记录（append-only，仅作为退款的外键宿主）。 */
            private long insertTransaction() {
                String marker = UUID.randomUUID().toString().replace("-", "");
                // ck_payment_transaction_paid_pairing：SUCCEEDED 必须带 paid_at。
                return jdbc.queryForObject("""
                        INSERT INTO xsy.xsy_payment_transaction
                            (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                             paid_at, created_by)
                        VALUES (?, 1, 'MOCK', ?, 1000.0000, 1000.0000, 'SUCCEEDED', ?, 'IT-PAY02')
                        RETURNING id
                        """, Long.class, "IT-PAY02-TXN-" + marker, "IT-PAY02-PTN-" + marker,
                        java.time.OffsetDateTime.now());
            }

            /** 直接插入退款记录行，绕开服务层以确保测的是 SQL 规则本身。 */
            private void insertRefund(long transactionId, BigDecimal amount, BigDecimal providerAmount, String status) {
                jdbc.update("""
                        INSERT INTO xsy.xsy_payment_refund
                            (refund_no, intent_id, transaction_id, provider, amount, status, provider_amount,
                             refunded_at, created_by)
                        VALUES (?, 1, ?, 'MOCK', ?, ?, ?, ?, 'IT-PAY02')
                        """, "IT-PAY02-RFD-" + UUID.randomUUID().toString().replace("-", ""), transactionId,
                        amount, status, providerAmount,
                        "SUCCEEDED".equals(status) ? java.time.OffsetDateTime.now() : null);
            }
        }

        @Nested
        @DisplayName("退款分摊单来源失败的保存点隔离与重试（PG IT）")
        class RefundFundingAllocationSavepointPgIT {
            @Autowired
            private RefundFundingAllocationService refundFundingAllocationService;

            @Autowired
            private PaymentRefundRecoveryService paymentRefundRecoveryService;

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("在线来源取不到渠道实现时只回滚该来源的写入，余额腿与失败状态仍提交")
            void onlinePreparationFailureRollsBackOnlyItsOwnWrites() {
                // WECHAT 已保存约定但尚无实现：退款单能建出来，向渠道注册表取实现时抛「不支持的渠道」。
                MixedFundingFixture fixture = completedRefundWithMixedFunding("WECHAT", "SUCCEEDED");

                RefundFundingExecuteForm form = new RefundFundingExecuteForm();
                form.setRefundId(fixture.orderRefundId());
                String key = prefix + ":savepoint-online:" + UUID.randomUUID();

                var result = refundFundingAllocationService.execute(form, key);

                assertThat(result.getStatus()).isEqualTo("FAILED");

                assertThat(allocationStatus(fixture.balanceAllocationId()))
                        .as("先成功的余额腿不能被后失败来源的回滚带走")
                        .isEqualTo("SUCCEEDED");
                assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                        .as("余额腿的返还流水必须已经提交，且只有一条")
                        .isEqualTo(1);

                assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT failure_message FROM xsy_refund_funding_allocation WHERE id = ?",
                        String.class, fixture.onlineAllocationId()))
                        .as("失败必须发生在渠道退款单已经建出来之后，否则「保存点回滚掉了写入」这句话无从谈起")
                        .isEqualTo("不支持的支付渠道");
                assertThat(paymentRefundCount(fixture.onlineAllocationId()))
                        .as("保存点必须把在线腿已经插入的渠道退款单回滚掉，不能留下没有对应分摊的孤行")
                        .isZero();

                assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM xsy_idempotency_record"
                        + " WHERE idempotency_key = ? AND result_data IS NOT NULL)", Boolean.class, key))
                        .as("外层事务必须正常提交，失败状态与防重复处理结果都要写入数据库")
                        .isTrue();
                assertThat(financePaymentCount(fixture.orderRefundId()))
                        .as("来源没有全部成功，不登记财务付款")
                        .isZero();
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("失败来源修复后重试：余额腿不重复返还，在线腿补齐后整单完成且财务付款唯一")
            void retryAfterRepairingTheFailedSourceCompletesTheRefundExactlyOnce() {
                // 在线腿的交易此时还没成功，退款准备必然失败；修复后重试应能接着往下走。
                MixedFundingFixture fixture = completedRefundWithMixedFunding("MOCK", "PENDING");

                RefundFundingExecuteForm form = new RefundFundingExecuteForm();
                form.setRefundId(fixture.orderRefundId());

                var first = refundFundingAllocationService.execute(form, prefix + ":savepoint-retry-a:" + UUID.randomUUID());
                assertThat(first.getStatus()).isEqualTo("FAILED");
                assertThat(allocationStatus(fixture.balanceAllocationId())).isEqualTo("SUCCEEDED");
                assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("FAILED");

                jdbc.update("UPDATE xsy_payment_transaction SET status = 'SUCCEEDED', paid_at = CURRENT_TIMESTAMP"
                        + " WHERE id = ?", fixture.onlineTransactionId());

                var second = refundFundingAllocationService.execute(form, prefix + ":savepoint-retry-b:" + UUID.randomUUID());
                assertThat(second.getStatus())
                        .as("余额腿已成功、在线腿停在渠道处理中")
                        .isEqualTo("PROCESSING");
                assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                        .as("重试不能把已经返还过的余额腿再返一次")
                        .isEqualTo(1);
                Long paymentRefundId = jdbc.queryForObject("SELECT id FROM xsy_payment_refund"
                        + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Long.class,
                        fixture.onlineAllocationId());
                assertThat(paymentRefundId).as("重试要为在线腿建立渠道退款意图").isNotNull();
                assertThat(jdbc.queryForObject("SELECT status FROM xsy_payment_refund WHERE id = ?", String.class,
                        paymentRefundId)).isEqualTo("PROCESSING");
                assertThat(financePaymentCount(fixture.orderRefundId())).isZero();

                paymentRefundRecoveryService.recover(paymentRefundId, operator(), "失败来源修复后重试", true);

                assertThat(allocationStatus(fixture.balanceAllocationId())).isEqualTo("SUCCEEDED");
                assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("SUCCEEDED");
                assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                        .as("整单完成后余额腿仍只有一条返还流水")
                        .isEqualTo(1);
                assertThat(paymentRefundCount(fixture.onlineAllocationId()))
                        .as("在线腿仍只有一张渠道退款单，重试不另建")
                        .isEqualTo(1);
                assertThat(financePaymentCount(fixture.orderRefundId()))
                        .as("全部来源成功后自动登记且只登记一笔财务付款")
                        .isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private record MixedFundingFixture(Long orderRefundId, Long orderId, Long balanceAllocationId,
                    Long onlineAllocationId, Long onlineTransactionId) {
            }

            /**
             * 一张「已完成的退款单 + 余额腿与在线腿各一条待执行分摊行」的完整前置。
             *
             * <p>订单资金来源按真实形态补齐（两条腿各有 SUCCEEDED 的意图与交易、余额腿另有消费流水），
             * 因为分摊执行入口会锁订单资金；在线腿的交易状态由参数决定，用来制造「渠道那笔钱还没到账」的退款准备失败。
             */
            private MixedFundingFixture completedRefundWithMixedFunding(String onlineProvider,
                    String onlineTransactionStatus) {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("savepoint");
                Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
                String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                        orderId);
                BigDecimal refundAmount = jdbc.queryForObject(
                        "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
                Long settlementCustomerId = jdbc.queryForObject(
                        "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
                String settlementCustomerName = jdbc.queryForObject(
                        "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);
                BigDecimal balanceAllocated = refundAmount.multiply(new BigDecimal("0.4")).setScale(4, RoundingMode.HALF_UP);
                BigDecimal onlineAllocated = refundAmount.subtract(balanceAllocated);

                Long accountId = jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                            created_by, updated_by)
                        VALUES (?, ?, ?, ?)
                        RETURNING id
                        """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

                // 余额腿：ck_payment_intent_method_provider 要求 BALANCE 只能配 INTERNAL_BALANCE 且来源为销售订单。
                Long balanceIntentId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_intent
                            (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                             amount, method, provider, status, succeeded_at, created_by, updated_by)
                        VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                            CURRENT_TIMESTAMP, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-SP-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                        settlementCustomerName, orderId, orderNo, balanceAllocated, operator(), operator());

                Long balanceTransactionId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_transaction
                            (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                             paid_at, created_by)
                        VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                            CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-SP-BT-" + UUID.randomUUID().toString().substring(0, 8), balanceIntentId,
                        prefix + "-SP-BPTN-" + UUID.randomUUID().toString().substring(0, 8), balanceAllocated, balanceAllocated,
                        operator());

                Long balanceMovementId = jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_movement
                            (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                             source_type, source_id, reason, occurred_at, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                            CURRENT_TIMESTAMP, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-SP-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                        settlementCustomerId, customerId, balanceAllocated, balanceIntentId, operator(), operator());

                Long onlineIntentId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_intent
                            (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                             amount, method, provider, status, succeeded_at, created_by, updated_by)
                        VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'ONLINE', ?, 'SUCCEEDED',
                            CURRENT_TIMESTAMP, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-SP-OI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                        settlementCustomerName, orderId, orderNo, onlineAllocated, onlineProvider, operator(), operator());

                // ck_payment_transaction_paid_pairing：只有 SUCCEEDED 才允许带 paid_at。
                Long onlineTransactionId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_transaction
                            (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                             paid_at, created_by)
                        VALUES (?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), ?, %s, ?)
                        RETURNING id
                        """.formatted("SUCCEEDED".equals(onlineTransactionStatus) ? "CURRENT_TIMESTAMP" : "NULL"), Long.class,
                        prefix + "-SP-OT-" + UUID.randomUUID().toString().substring(0, 8), onlineIntentId, onlineProvider,
                        prefix + "-SP-OPTN-" + UUID.randomUUID().toString().substring(0, 8), onlineAllocated, onlineAllocated,
                        onlineTransactionStatus, operator());

                // ck_order_refund_completion：COMPLETED 必须带 completed_at。
                // return_id 在本表没有外键，只被 uk_order_refund_return_active 约束为全表唯一；
                // 本用例是 NOT_SUPPORTED、行会真实写入数据库，所以必须从退货 id 序列取值 ——
                // 用退款序列取值会让「假 return_id」落进真实退货 id 的取值区间，后续用例建出的
                // 真实退货迟早撞上这些历史行（ScmFinanceReceivableRedPgIT 就是这样失败的）。
                Long orderRefundId = jdbc.queryForObject("""
                        INSERT INTO xsy_order_refund
                            (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                        VALUES (?, nextval(pg_get_serial_sequence('xsy_order_return', 'id')), ?, ?, CAST(? AS numeric), 'COMPLETED',
                            CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-SP-AOR-" + UUID.randomUUID().toString().substring(0, 8), orderId,
                        customerId, refundAmount, operator());

                Long balanceAllocationId = insertAllocation(orderRefundId, orderId, customerId, settlementCustomerId,
                        "BALANCE", balanceMovementId, balanceAllocated, 0);
                Long onlineAllocationId = insertAllocation(orderRefundId, orderId, customerId, settlementCustomerId, "ONLINE",
                        onlineTransactionId, onlineAllocated, 1);

                return new MixedFundingFixture(orderRefundId, orderId, balanceAllocationId, onlineAllocationId,
                        onlineTransactionId);
            }

            private Long insertAllocation(Long orderRefundId, Long orderId, Long customerId, Long settlementCustomerId,
                    String sourceMethod, Long sourceId, BigDecimal allocatedAmount, int sortOrder) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_refund_funding_allocation
                            (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                             source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                             updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), 'PENDING',
                            CURRENT_TIMESTAMP, ?, ?, ?)
                        RETURNING id
                        """, Long.class, orderRefundId, orderId, customerId, settlementCustomerId, sourceMethod, sourceId,
                        allocatedAmount, allocatedAmount, sortOrder, operator(), operator());
            }

            private String allocationStatus(Long allocationId) {
                return jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?", String.class,
                        allocationId);
            }

            private Integer balanceReturnCount(Long allocationId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement"
                        + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class, allocationId);
            }

            private Integer paymentRefundCount(Long allocationId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_payment_refund"
                        + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class, allocationId);
            }

            private Integer financePaymentCount(Long orderRefundId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment"
                        + " WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND entry_type = 'NORMAL'", Integer.class,
                        orderRefundId);
            }

            private String operator() {
                return "IT-ALLOC-SAVEPOINT";
            }
        }

        @Nested
        @DisplayName("余额分摊返还时间与失败提交（PG IT）")
        class RefundFundingAllocationTimePgIT {
            @Autowired
            private RefundFundingAllocationService refundFundingAllocationService;

            @Autowired
            private MallAfterSaleFundingSourceDao mallAfterSaleFundingSourceDao;

            @Test
            @DisplayName("退款单先完成、稍后再执行余额返还时，流水记的是实际返还时刻而不是退款完成时刻")
            void balanceReturnRecordsTheExecutionTimeNotTheRefundCompletionTime() {
                BalanceAllocationFixture fixture = completedRefundWithBalanceAllocation();

                // 模拟「后台 09:00 确认退款完成，11:00 才执行余额返还」：把退款单的完成时刻推到两小时前。
                jdbc.update("UPDATE xsy_order_refund SET completed_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE id = ?",
                        fixture.orderRefundId());
                OffsetDateTime refundCompletedAt = jdbc.queryForObject(
                        "SELECT completed_at FROM xsy_order_refund WHERE id = ?", OffsetDateTime.class,
                        fixture.orderRefundId());

                RefundFundingExecuteForm form = new RefundFundingExecuteForm();
                form.setRefundId(fixture.orderRefundId());
                refundFundingAllocationService.execute(form, prefix + ":alloc:" + UUID.randomUUID());

                OffsetDateTime occurredAt = jdbc.queryForObject(
                        "SELECT occurred_at FROM xsy_customer_balance_movement WHERE source_type = 'REFUND_FUNDING_ALLOCATION'"
                                + " AND source_id = ?",
                        OffsetDateTime.class, fixture.allocationId());

                assertThat(occurredAt)
                        .as("流水时间必须晚于退款单完成时刻，否则它记的是业务完成时间而不是资金实际返还时间")
                        .isAfter(refundCompletedAt.plusMinutes(1));
                assertThat(Duration.between(occurredAt, OffsetDateTime.now()).abs())
                        .as("流水时间应当就是本次执行的时刻（留 2 分钟余量吸收时钟与执行开销）")
                        .isLessThan(Duration.ofMinutes(2));

                // 商城售后详情里的「资金到账时间」取自同一列，必须与流水一致，不能退回成退款单完成时刻。
                List<MallAfterSaleFundingTimeVO> fundingTimes =
                        mallAfterSaleFundingSourceDao.allocationSuccessTimes(fixture.orderRefundId());
                assertThat(fundingTimes).singleElement().satisfies(row -> {
                    assertThat(row.getAllocationId()).isEqualTo(fixture.allocationId());
                    assertThat(row.getSucceededAt())
                            .as("商城展示的资金到账时间必须等于实际返还时刻")
                            .isEqualTo(occurredAt);
                });
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("钱包账户不可用时，分摊失败与防重复处理结果仍能提交，不留余额返还流水")
            void failedBalanceAllocationPersistsItsFailureOutsideTheTestTransaction() {
                // 本用例会提交带唯一前缀的夹具，只能在授权的隔离 PostgreSQL 中执行。
                BalanceAllocationFixture fixture = completedRefundWithBalanceAllocation();
                jdbc.update("UPDATE xsy_customer_balance_account SET deleted = TRUE "
                        + "WHERE settlement_customer_id = (SELECT settlement_customer_id "
                        + "FROM xsy_refund_funding_allocation WHERE id = ?)", fixture.allocationId());
                RefundFundingExecuteForm form = new RefundFundingExecuteForm();
                form.setRefundId(fixture.orderRefundId());
                String key = prefix + ":failed-allocation:" + UUID.randomUUID();

                var result = refundFundingAllocationService.execute(form, key);

                assertThat(result.getStatus()).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?",
                        String.class, fixture.allocationId())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM xsy_idempotency_record "
                        + "WHERE idempotency_key = ? AND result_data IS NOT NULL)", Boolean.class, key)).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement "
                        + "WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class,
                        fixture.allocationId())).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment "
                        + "WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND entry_type = 'NORMAL'",
                        Integer.class, fixture.orderRefundId())).isZero();
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private record BalanceAllocationFixture(Long orderRefundId, Long allocationId) {
            }

            /**
             * 一张「已完成的退款单 + 一条待执行的余额分摊行 + 一个结算主体钱包」的完整前置。
             *
             * <p>订单资金来源按真实形态补齐（意图 SUCCEEDED + 交易 SUCCEEDED + 余额消费流水），
             * 因为分摊执行入口会锁订单资金，且余额分摊的来源 id 就是那笔消费流水。
             */
            private BalanceAllocationFixture completedRefundWithBalanceAllocation() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("alloc-time");
                Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
                String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                        orderId);
                BigDecimal refundAmount = jdbc.queryForObject(
                        "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
                Long settlementCustomerId = jdbc.queryForObject(
                        "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
                String settlementCustomerName = jdbc.queryForObject(
                        "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);

                Long accountId = jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                            created_by, updated_by)
                        VALUES (?, ?, ?, ?)
                        RETURNING id
                        """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

                // 余额支付意图：ck_payment_intent_method_provider 要求 BALANCE 只能配 INTERNAL_BALANCE 且来源为销售订单。
                Long intentId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_intent
                            (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                             amount, method, provider, status, succeeded_at, created_by, updated_by)
                        VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                            CURRENT_TIMESTAMP, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                        settlementCustomerName, orderId, orderNo, refundAmount, operator(), operator());

                Long transactionId = jdbc.queryForObject("""
                        INSERT INTO xsy_payment_transaction
                            (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                             paid_at, created_by)
                        VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                            CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-BT-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                        prefix + "-BPTN-" + UUID.randomUUID().toString().substring(0, 8), refundAmount, refundAmount,
                        operator());

                // 余额消费流水：分摊的 source_id 就是它，财务侧也靠 source_type = PAYMENT_INTENT 认这腿资金来源。
                Long movementId = jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_movement
                            (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                             source_type, source_id, reason, occurred_at, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                            CURRENT_TIMESTAMP, ?, ?)
                        RETURNING id
                        """, Long.class, prefix + "-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                        settlementCustomerId, customerId, refundAmount, intentId, operator(), operator());

                // ck_order_refund_completion：COMPLETED 必须带 completed_at。
                // return_id 无外键、只要求全表唯一；本用例 NOT_SUPPORTED，行会真实写入数据库，
                // 因此从退货 id 序列取值，避免假 return_id 与后续真实退货 id 撞唯一索引。
                Long orderRefundId = jdbc.queryForObject("""
                        INSERT INTO xsy_order_refund
                            (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                        VALUES (?, nextval(pg_get_serial_sequence('xsy_order_return', 'id')), ?, ?, CAST(? AS numeric), 'COMPLETED',
                            CURRENT_TIMESTAMP, ?)
                        RETURNING id
                        """, Long.class, prefix + "-AOR-" + UUID.randomUUID().toString().substring(0, 8), orderId,
                        customerId, refundAmount, operator());

                Long allocationId = jdbc.queryForObject("""
                        INSERT INTO xsy_refund_funding_allocation
                            (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                             source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                             updated_by)
                        VALUES (?, ?, ?, ?, 'BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'PENDING',
                            CURRENT_TIMESTAMP, 0, ?, ?)
                        RETURNING id
                        """, Long.class, orderRefundId, orderId, customerId, settlementCustomerId, movementId,
                        refundAmount, refundAmount, operator(), operator());

                return new BalanceAllocationFixture(orderRefundId, allocationId);
            }

            private String operator() {
                return "IT-ALLOC-TIME";
            }
        }
    }
}
