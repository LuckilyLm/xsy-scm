package com.xsy.scm.sorting;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.sorting.controller.SortingTaskController;
import com.xsy.scm.sorting.dao.SortingPartRecordDao;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingStandardConfirmForm;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.service.SortingStandardPolicy;
import com.xsy.scm.sorting.service.SortingStandardService;
import com.xsy.scm.sorting.service.SortingTaskService;
import com.xsy.scm.sorting.support.SortingAccess;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.service.TraceUnitService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SortingStandardGuardTest {
    @Test
    void previewAndConfirmationBothRespectPackagingBindings() {
        var queries = mock(SortingQueryDao.class);
        var rows = mock(SortingTaskItemDao.class);
        var parts = mock(SortingPartRecordDao.class);
        var access = mock(SortingAccess.class);
        var sorting = mock(SortingTaskService.class);
        var trace = mock(TraceUnitService.class);
        var idempotency = mock(ScmIdempotencyService.class);
        var service = new SortingStandardService(queries, rows, parts, access, new SortingStandardPolicy(),
                sorting, trace, idempotency);
        var task = new SortingTaskEntity();
        task.setId(1L);
        task.setAssigneeEmployeeId(7L);
        task.setStatus("PENDING");
        var row = new SortingTaskItemEntity();
        row.setId(2L);
        row.setTaskId(1L);
        row.setProductTypeSnapshot("STANDARD");
        row.setOccupationStatus("ACTIVE");
        row.setPlannedQuantitySnapshot(new BigDecimal("2.0000"));
        row.setSaleUnitSnapshot("箱");
        var scope = mock(ScmDataScopeContext.class);
        when(scope.getEmployeeId()).thenReturn(7L);
        when(access.scope()).thenReturn(scope);
        when(queries.lockTask(1L)).thenReturn(task);
        when(rows.selectList(any())).thenReturn(List.of(row));
        when(idempotency.claim(anyString(), anyString(), any())).thenReturn(new ScmIdempotencyService.Claim(null, false));
        when(trace.previewSortedQuantityRejection(2L, row.getPlannedQuantitySnapshot()))
                .thenReturn(TraceErrorCode.TRACE_UNIT_BIND_EXCEED.getMsg());
        try (var permissions = mockStatic(StpUtil.class)) {
            permissions.when(() -> StpUtil.hasPermission(SortingPermission.ITEM_UPDATE)).thenReturn(true);
            assertThat(service.preview(1L).items().getFirst().eligible()).isFalse();
            var form = new SortingStandardConfirmForm();
            form.setVersion(0);
            form.setItems(List.of(new SortingStandardConfirmForm.Item(2L, 0)));
            assertThatThrownBy(() -> service.confirm(1L, form, "bindings"))
                    .isInstanceOf(ScmBusinessException.class);
            verifyNoInteractions(sorting);
        }
    }

    @Test
    void routesUseExistingItemUpdatePermission() throws NoSuchMethodException {
        assertThat(SortingTaskController.class.getMethod("standardPreview", Long.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(SortingPermission.ITEM_UPDATE);
        assertThat(SortingTaskController.class.getMethod("standardConfirm", Long.class,
                SortingStandardConfirmForm.class, String.class).getAnnotation(SaCheckPermission.class).value())
                .containsExactly(SortingPermission.ITEM_UPDATE);
    }
}
