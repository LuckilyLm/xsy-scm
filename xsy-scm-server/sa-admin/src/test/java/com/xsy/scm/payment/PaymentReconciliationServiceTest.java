package com.xsy.scm.payment;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.payment.dao.PaymentReconciliationDao;
import com.xsy.scm.payment.dao.PaymentReconciliationItemDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationItemEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import com.xsy.scm.payment.service.PaymentNumberGenerator;
import com.xsy.scm.payment.service.PaymentReconciliationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PaymentReconciliationServiceTest {

    private final PaymentReconciliationDao reconciliations = mock(PaymentReconciliationDao.class);
    private final PaymentReconciliationItemDao items = mock(PaymentReconciliationItemDao.class);
    private final PaymentTransactionDao transactions = mock(PaymentTransactionDao.class);
    private final PaymentNumberGenerator numbers = mock(PaymentNumberGenerator.class);
    private final ScmPaymentProviderRegistry providers = mock(ScmPaymentProviderRegistry.class);
    private final ScmPaymentProvider provider = mock(ScmPaymentProvider.class);
    private final PaymentReconciliationService service = new PaymentReconciliationService(reconciliations, items,
            transactions, numbers, providers);

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
        when(numbers.nextReconciliationNo()).thenReturn("RECON-1");

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
}
