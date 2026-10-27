package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceWaiverDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinanceWaiverEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWaiverAddForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceWaiverVO;
import com.xsy.scm.finance.service.FinanceWaiverService;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("抹零：写成 WAIVER 核销行、金额不超未结、反向只追加")
class FinanceWaiverServiceTest {

    private final FinanceWaiverDao waivers = mock(FinanceWaiverDao.class);
    private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
    private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
    private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
    private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
    private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
    private final FinanceWaiverService service = new FinanceWaiverService(waivers, receivables, writeOffs, logs, scopes,
        idempotency);

    private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
    private final ScmIdempotencyService.Claim claim = new ScmIdempotencyService.Claim(null, false);
    private final FinanceWaiverAddForm addForm = new FinanceWaiverAddForm();
    private final FinanceWaiverReverseForm reverseForm = new FinanceWaiverReverseForm();

    @BeforeEach
    void floatingOpenAmount() {
        addForm.setReceivableId(50L);
        addForm.setAmount("0.3000");
        addForm.setReason("对账抹零");
        reverseForm.setWaiverId(5L);
        reverseForm.setReason("客户要求补收");

        when(scopes.resolve()).thenReturn(scope);
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        var target = new FinanceReceivableTargetDto();
        target.setReceivableId(50L);
        target.setReceivableNo("AR50");
        target.setCustomerId(2L);
        target.setSettlementCustomerId(3L);
        target.setCustomerNameSnapshot("客户甲");
        target.setEntryType("NORMAL");
        target.setAmount(new BigDecimal("100.3000"));
        target.setSellerId(7L);
        when(receivables.selectNormalTargetForUpdate(50L)).thenReturn(target);
        when(receivables.selectRedAmount(50L)).thenReturn(BigDecimal.ZERO);
        when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100"));
        when(idempotency.claim(any(), eq("key"), any())).thenReturn(claim);
    }

    @Test
    @DisplayName("抹零 0.30：写一条 WAIVER 核销行冲减应收，抹零表只存金额与原因")
    void addWritesWaiverAndWriteOff() {
        when(waivers.nextWaiverNo()).thenReturn(1L);
        when(waivers.insert(any(FinanceWaiverEntity.class))).thenAnswer(invocation -> {
            FinanceWaiverEntity row = invocation.getArgument(0);
            row.setId(5L);
            return 1;
        });
        when(writeOffs.nextWriteOffNo()).thenReturn(3L);
        when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
            FinanceWriteOffEntity row = invocation.getArgument(0);
            row.setId(8L);
            return 1;
        });

        FinanceWaiverVO result;
        try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            result = service.add(addForm, "key");
        }

        assertThat(result.getStatus()).isEqualTo("NORMAL");
        assertThat(result.getAmount()).isEqualByComparingTo("0.30");
        var waiver = ArgumentCaptor.forClass(FinanceWaiverEntity.class);
        verify(waivers).insert(waiver.capture());
        assertThat(waiver.getValue().getReceivableId()).isEqualTo(50L);
        assertThat(waiver.getValue().getReason()).isEqualTo("对账抹零");

        var writeOff = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
        verify(writeOffs).insert(writeOff.capture());
        assertThat(writeOff.getValue().getSourceType()).isEqualTo("WAIVER");
        assertThat(writeOff.getValue().getSourceId()).isEqualTo(5L);
        assertThat(writeOff.getValue().getTargetType()).isEqualTo("RECEIVABLE");
        assertThat(writeOff.getValue().getAmount()).isEqualByComparingTo("0.30");
        assertThat(writeOff.getValue().getEntryType()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("抹零金额超过未结 0.30 时拒绝，不写任何事实")
    void addExceedingOpenAmountRejected() {
        when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100.2000"));

        assertThatThrownBy(() -> service.add(addForm, "key"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_AMOUNT_EXCEEDED));
        verify(waivers, never()).insert(any(FinanceWaiverEntity.class));
        verifyNoInteractions(logs);
    }

    @Test
    @DisplayName("没有原因的抹零直接判非法，不进入余额计算")
    void addWithoutReasonRejected() {
        addForm.setReason("   ");

        assertThatThrownBy(() -> service.add(addForm, "key"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_RECEIVABLE_INVALID));
        verifyNoInteractions(waivers, writeOffs);
    }

    @Test
    @DisplayName("反向抹零追加 REVERSE 核销行，状态由反向行推导")
    void reverseAppendsReverseWriteOff() {
        var waiver = new FinanceWaiverEntity();
        waiver.setId(5L);
        waiver.setWaiverNo("WV5");
        waiver.setReceivableId(50L);
        waiver.setCustomerId(2L);
        waiver.setSettlementCustomerId(3L);
        waiver.setAmount(new BigDecimal("0.3000"));
        waiver.setReason("对账抹零");
        when(waivers.selectById(5L)).thenReturn(waiver);
        when(waivers.selectByIdForUpdate(5L)).thenReturn(waiver);

        var normal = new FinanceWriteOffEntity();
        normal.setId(8L);
        normal.setWriteOffNo("WO8");
        normal.setSourceType("WAIVER");
        normal.setSourceId(5L);
        normal.setTargetType("RECEIVABLE");
        normal.setTargetId(50L);
        normal.setAmount(new BigDecimal("0.3000"));
        normal.setEntryType("NORMAL");
        when(writeOffs.selectNormalAllocation("WAIVER", 5L, null)).thenReturn(normal);
        when(writeOffs.hasReversal(8L)).thenReturn(false);
        when(writeOffs.nextWriteOffNo()).thenReturn(4L);
        when(writeOffs.insertReverseOnConflictDoNothing(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
            FinanceWriteOffEntity row = invocation.getArgument(0);
            row.setId(9L);
            return 1;
        });
        when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100.3000"));

        FinanceWaiverVO result;
        try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            result = service.reverse(reverseForm, "key");
        }

        assertThat(result.getStatus()).isEqualTo("REVERSED");
        assertThat(result.getReverseReason()).isEqualTo("客户要求补收");
        var reversal = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
        verify(writeOffs).insertReverseOnConflictDoNothing(reversal.capture());
        assertThat(reversal.getValue().getEntryType()).isEqualTo("REVERSE");
        assertThat(reversal.getValue().getReverseOfId()).isEqualTo(8L);
        assertThat(reversal.getValue().getReason()).isEqualTo("客户要求补收");
        // 抹零表不留状态：没有更新，只有插入
        verify(waivers, never()).updateById(any(FinanceWaiverEntity.class));
    }

    @Test
    @DisplayName("已反向的抹零不能再次反向")
    void reverseTwiceRejected() {
        var waiver = new FinanceWaiverEntity();
        waiver.setId(5L);
        waiver.setReceivableId(50L);
        when(waivers.selectById(5L)).thenReturn(waiver);
        when(waivers.selectByIdForUpdate(5L)).thenReturn(waiver);
        var normal = new FinanceWriteOffEntity();
        normal.setId(8L);
        normal.setEntryType("NORMAL");
        when(writeOffs.selectNormalAllocation("WAIVER", 5L, null)).thenReturn(normal);
        when(writeOffs.hasReversal(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.reverse(reverseForm, "key"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_ALREADY_REVERSED));
        verify(writeOffs, never()).insertReverseOnConflictDoNothing(any(FinanceWriteOffEntity.class));
    }
}
