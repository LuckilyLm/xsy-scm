package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceAutoWriteOffTargetDto;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAutoPlanVO;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("核销自动 FIFO：按账单日期分配、不超额、剩余留待下次")
class FinanceWriteOffAutoTest {

    private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
    private final FinanceOrderFundingSourceDao fundingSources = mock(FinanceOrderFundingSourceDao.class);
    private final FinanceCounterpartySourceDao counterparties = mock(FinanceCounterpartySourceDao.class);
    private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
    private final FinancePaymentDao payments = mock(FinancePaymentDao.class);
    private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
    private final FinancePayableDao payables = mock(FinancePayableDao.class);
    private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final FinanceWriteOffService service = new FinanceWriteOffService(writeOffs, fundingSources,
        counterparties, receipts, payments, receivables, payables, logs, scopes, idempotency);

    private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
    private final ScmIdempotencyService.Claim claim = new ScmIdempotencyService.Claim(null, false);
    private final FinanceWriteOffAutoForm form = new FinanceWriteOffAutoForm();

    @BeforeEach
    void receiptOf100() {
        form.setSourceType("RECEIPT");
        form.setSourceId(9L);
        when(scopes.resolve()).thenReturn(scope);
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        when(scope.getPurchaserScope()).thenReturn(ScmValueScope.all());

        var receipt = new FinanceReceiptEntity();
        receipt.setId(9L);
        receipt.setReceiptNo("RC9");
        receipt.setCustomerId(2L);
        receipt.setCustomerNameSnapshot("客户甲");
        receipt.setSettlementCustomerId(3L);
        receipt.setAmount(new BigDecimal("100"));
        receipt.setEntryType("NORMAL");
        when(receipts.selectByIdForUpdate(9L)).thenReturn(receipt);
        when(receipts.selectReversedAmount(9L)).thenReturn(BigDecimal.ZERO);
        when(fundingSources.isRechargeReceipt(9L)).thenReturn(false);

        var customer = new FinanceCustomerFactDto();
        customer.setCustomerId(2L);
        customer.setSellerId(7L);
        when(counterparties.selectCustomer(2L)).thenReturn(customer);

        when(writeOffs.selectSourceUsedAmount("RECEIPT", 9L)).thenReturn(BigDecimal.ZERO);
        when(idempotency.claim(any(), eq("key"), any())).thenReturn(claim);
    }

    @Test
    @DisplayName("100 元收款先结清 60 元旧账，再把剩余 40 元结到第二张单")
    void fillsOldestTargetsFirstAndCarriesTheRest() {
        when(receivables.selectAutoTargets(eq(3L), any(), anyInt()))
            .thenReturn(List.of(candidate(11L, "60"), candidate(12L, "80")));
        when(receivables.selectNormalTargetsForUpdate(List.of(11L, 12L)))
            .thenReturn(List.of(locked(11L, "60"), locked(12L, "80")));
        when(receivables.selectRedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
        when(writeOffs.selectTargetWrittenOffAmount(eq("RECEIVABLE"), anyLong())).thenReturn(BigDecimal.ZERO);
        when(writeOffs.nextWriteOffNo()).thenReturn(1L, 2L);
        var sequence = new java.util.concurrent.atomic.AtomicLong(1);
        when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
            FinanceWriteOffEntity row = invocation.getArgument(0);
            row.setId(sequence.getAndIncrement());
            return 1;
        });

        try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            var result = service.autoAdd(form, "key");
            assertThat(result.getItems()).hasSize(2);
        }

        var captured = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
        verify(writeOffs, org.mockito.Mockito.times(2)).insert(captured.capture());
        assertThat(captured.getAllValues().stream().map(FinanceWriteOffEntity::getTargetId).toList())
            .containsExactly(11L, 12L);
        assertThat(captured.getAllValues().stream().map(FinanceWriteOffEntity::getAmount).toList())
            .containsExactly(new BigDecimal("60.0000"), new BigDecimal("40.0000"));
        // 幂等 scope 与手工核销分开，避免同一收款两个入口互相重放
        verify(idempotency).claim(eq("FINANCE_WRITE_OFF_AUTO:RECEIPT:9"), eq("key"), any());
    }

    @Test
    @DisplayName("预览只读：给出顺序与金额，不写核销、不取号")
    void previewNeverWrites() {
        var view = new com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO();
        view.setReceiptId(9L);
        view.setReceiptNo("RC9");
        view.setCustomerId(2L);
        view.setCustomerName("客户甲");
        view.setSettlementCustomerId(3L);
        view.setEntryType("NORMAL");
        view.setEffectiveAmount(new BigDecimal("100"));
        when(receipts.selectQueryById(9L)).thenReturn(view);
        when(receivables.selectAutoTargets(eq(3L), any(), anyInt()))
            .thenReturn(List.of(candidate(11L, "60"), candidate(12L, "80")));

        List<FinanceWriteOffAutoPlanVO> plan = service.autoPreview(form);

        assertThat(plan).extracting(FinanceWriteOffAutoPlanVO::getTargetId).containsExactly(11L, 12L);
        assertThat(plan).extracting(FinanceWriteOffAutoPlanVO::getAllocatedAmount)
            .containsExactly(new BigDecimal("60.0000"), new BigDecimal("40.0000"));
        verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
        verifyNoInteractions(logs, idempotency);
    }

    @Test
    @DisplayName("对方没有未结单据时返回 41160，不写空核销")
    void emptyCandidatesRejected() {
        when(receivables.selectAutoTargets(eq(3L), any(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> service.autoAdd(form, "key"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.AUTO_WRITE_OFF_NO_OPEN_TARGET));
        verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
    }

    @Test
    @DisplayName("客户退款付款没有可冲的应付：不查应付、不写核销")
    void customerPaymentHasNoPayableTargets() {
        form.setSourceType("PAYMENT");
        var payment = new FinancePaymentEntity();
        payment.setId(5L);
        payment.setPaymentNo("PM5");
        payment.setCounterpartyType("CUSTOMER");
        payment.setCounterpartyId(2L);
        payment.setAmount(new BigDecimal("30"));
        payment.setEntryType("NORMAL");
        when(payments.selectByIdForUpdate(5L)).thenReturn(payment);
        when(payments.selectReversedAmount(5L)).thenReturn(BigDecimal.ZERO);
        when(writeOffs.selectSourceUsedAmount("PAYMENT", 5L)).thenReturn(BigDecimal.ZERO);
        form.setSourceId(5L);

        assertThatThrownBy(() -> service.autoAdd(form, "key"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.AUTO_WRITE_OFF_NO_OPEN_TARGET));
        verify(payables, never()).selectAutoTargets(anyLong(), any(), anyInt());
    }

    private static FinanceAutoWriteOffTargetDto candidate(Long id, String openAmount) {
        var row = new FinanceAutoWriteOffTargetDto();
        row.setTargetId(id);
        row.setTargetNo("AR" + id);
        row.setTargetName("客户甲");
        row.setEventAt(OffsetDateTime.parse("2026-10-01T00:00:00+08:00"));
        row.setOpenAmount(new BigDecimal(openAmount));
        row.setOwnerId(7L);
        return row;
    }

    private static FinanceReceivableTargetDto locked(Long id, String openAmount) {
        var row = new FinanceReceivableTargetDto();
        row.setReceivableId(id);
        row.setReceivableNo("AR" + id);
        row.setCustomerId(2L);
        row.setSettlementCustomerId(3L);
        row.setCustomerNameSnapshot("客户甲");
        row.setEntryType("NORMAL");
        row.setAmount(new BigDecimal(openAmount));
        row.setSellerId(7L);
        return row;
    }
}
