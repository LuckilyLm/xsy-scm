package com.xsy.scm.inventory;

import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.dao.TraceCorrectionDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.vo.TraceCorrectionVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.service.TraceCorrectionService;
import com.xsy.scm.trace.service.TraceUnitService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V158 风险回归：发码额度不能假设批次出库等于某枚包装码出库。 */
class ScmTraceUnitSafetyPolicyTest {
    private final TraceUnitDao units = mock(TraceUnitDao.class);
    private final ScmWarehouseScopeGuard warehouses = mock(ScmWarehouseScopeGuard.class);
    private final TraceUnitService trace = new TraceUnitService(units, mock(ScmBusinessNoDao.class),
            warehouses, mock(ScmDataScopeService.class));

    @Test
    void issuedLabelsDoNotRecoverQuotaFromUnattributedLotOutbounds() {
        var balance = new TraceUnitDao.LotBalanceRow();
        balance.setQuantity(new BigDecimal("6.0000"));
        when(units.findLot(51L)).thenReturn(new TraceUnitVO.Lot());
        when(units.lotWarehouseId(51L)).thenReturn(9L);
        when(units.lotUnit(51L)).thenReturn("kg");
        when(units.lockLotBalances(51L)).thenReturn(List.of(balance));
        when(units.sumIssuedByLot(51L)).thenReturn(new BigDecimal("6.0000"));

        var request = new TraceUnitIssueForm();
        request.setLotId(51L);
        request.setPackageType("BAG");
        request.setPackageCount(1);
        request.setQuantityPerPackage("1.0000");
        assertThatThrownBy(() -> trace.issue(request, "tester"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41504));
        verify(units, never()).createUnit(any());
    }

    @Test
    void cannotBindNewPackageToShippedSalesOrderItem() {
        var unit = new TraceUnitVO.Unit();
        unit.setStatus("ACTIVE");
        when(units.lockByCode("UNIT1")).thenReturn(unit);
        var sorting = new TraceUnitVO.SortingItem();
        sorting.setItemId(19L);
        sorting.setTaskStatus("COMPLETED");
        sorting.setWarehouseId(9L);
        when(units.lockSortingItem(19L)).thenReturn(sorting);
        when(units.countConfirmedOutboundBySortingItem(19L)).thenReturn(1);
        var request = new TraceUnitBindForm();
        request.setUnitCode("UNIT1");
        request.setSortingTaskItemId(19L);

        assertThatThrownBy(() -> trace.bind(request, "tester"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41526));
        verify(units, never()).insertBinding(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void correctionApprovedAfterFormalOutboundMustNotAppendUnbind() {
        var corrections = mock(TraceCorrectionDao.class);
        var service = new TraceCorrectionService(corrections, units, warehouses,
                mock(ScmDataScopeService.class));
        var record = new TraceCorrectionVO();
        record.setId(31L);
        record.setUnitId(11L);
        record.setLotId(51L);
        record.setBindingId(21L);
        record.setStatus("PENDING");
        record.setRequestedBy("applicant");
        when(corrections.lockById(31L)).thenReturn(record);
        var unit = new TraceUnitVO.Unit();
        unit.setId(11L);
        unit.setStatus("ACTIVE");
        when(units.lockById(11L)).thenReturn(unit);
        when(units.findActiveBindingId(11L)).thenReturn(21L);
        when(corrections.countConfirmedOutboundByBinding(21L)).thenReturn(1);
        var decision = new TraceCorrectionDecisionForm();
        decision.setId(31L);
        decision.setVersion(0);

        assertThatThrownBy(() -> service.approve(decision, "reviewer"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41525));
        verify(corrections, never()).insertUnbind(any(), any(), any(), any(), any(), any());
    }
}
