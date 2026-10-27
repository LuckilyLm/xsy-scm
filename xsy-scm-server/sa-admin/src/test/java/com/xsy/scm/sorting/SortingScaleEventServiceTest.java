package com.xsy.scm.sorting;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.sorting.constant.SortingErrorCode;
import com.xsy.scm.sorting.dao.SortingScaleEventDao;
import com.xsy.scm.sorting.dao.SortingTaskDao;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.entity.SortingScaleEventEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingScaleAcceptForm;
import com.xsy.scm.sorting.service.SortingScaleEventService;
import com.xsy.scm.sorting.service.SortingTaskService;
import com.xsy.scm.sorting.support.SortingAccess;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 非标品读数接受边界：设备不得替人工选择结果，单位必须与冻结销售单位一致。 */
@ExtendWith(MockitoExtension.class)
class SortingScaleEventServiceTest {

    private static final long EVENT_ID = 901L;
    private static final long TASK_ID = 801L;
    private static final long ITEM_ID = 701L;

    @Mock
    private SortingScaleEventDao eventDao;

    @Mock
    private SortingTaskDao taskDao;

    @Mock
    private SortingTaskItemDao itemDao;

    @Mock
    private SortingQueryDao sortingQueryDao;

    @Mock
    private SortingTaskService taskService;

    @Mock
    private SortingAccess access;

    @InjectMocks
    private SortingScaleEventService service;

    private SortingScaleEventEntity event;
    private SortingTaskItemEntity item;

    @BeforeEach
    void prepare() {
        event = new SortingScaleEventEntity();
        event.setId(EVENT_ID);
        event.setTaskId(TASK_ID);
        event.setTaskItemId(ITEM_ID);
        event.setStatus("PENDING");
        event.setStableFlag(true);
        event.setRawReading(new BigDecimal("2.3500"));
        event.setUnit("kg");
        event.setVersion(0);

        item = new SortingTaskItemEntity();
        item.setId(ITEM_ID);
        item.setTaskId(TASK_ID);
        item.setProductTypeSnapshot("NON_STANDARD");
        item.setSaleUnitSnapshot("kg");
        item.setVersion(3);

        SortingTaskEntity task = new SortingTaskEntity();
        task.setId(TASK_ID);
        // requirePending 先按 id 读一次拿到 taskId 才能锁任务，再 lockById 锁读数。
        when(eventDao.selectById(EVENT_ID)).thenReturn(event);
        when(sortingQueryDao.lockTask(TASK_ID)).thenReturn(task);
        when(eventDao.lockById(EVENT_ID)).thenReturn(event);
    }

    @Test
    void nonStandardCannotBeAcceptedWithoutHumanResult() {
        when(itemDao.selectById(ITEM_ID)).thenReturn(item);
        assertThatThrownBy(() -> service.accept(EVENT_ID, form(null, null)))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SortingErrorCode.SCALE_EVENT_RESULT_REQUIRED));
        verify(taskService, never()).enter(any(), any());
    }

    @Test
    void mismatchUnitNeverWritesSortingQuantity() {
        item.setSaleUnitSnapshot("斤");
        when(itemDao.selectById(ITEM_ID)).thenReturn(item);
        assertThatThrownBy(() -> service.accept(EVENT_ID, form("NORMAL", null)))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SortingErrorCode.SCALE_EVENT_UNIT_MISMATCH));
        verify(taskService, never()).enter(any(), any());
    }

    @Test
    void nonStandardShortageUsesUnmodifiedReadingAndOperatorReason() {
        when(itemDao.selectById(ITEM_ID)).thenReturn(item);
        when(eventDao.markAccepted(eq(EVENT_ID), eq(0), eq(new BigDecimal("2.3500")), eq("operator"))).thenReturn(1);
        try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("operator");
            service.accept(EVENT_ID, form("SHORT", "现场实重短少"));
        }
        ArgumentCaptor<SortingEntryForm> captured = ArgumentCaptor.forClass(SortingEntryForm.class);
        verify(taskService).enter(eq(TASK_ID), captured.capture());
        assertThat(captured.getValue().getItems()).hasSize(1);
        var entry = captured.getValue().getItems().getFirst();
        assertThat(entry.getId()).isEqualTo(ITEM_ID);
        assertThat(entry.getVersion()).isEqualTo(3);
        assertThat(entry.getSortedQuantity()).isEqualByComparingTo("2.3500");
        assertThat(entry.getResult()).isEqualTo("SHORT");
        assertThat(entry.getReason()).isEqualTo("现场实重短少");
    }

    @Test
    void unstableReadingCannotBeAccepted() {
        event.setStableFlag(false);
        assertThatThrownBy(() -> service.accept(EVENT_ID, form("NORMAL", null)))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SortingErrorCode.SCALE_EVENT_NOT_STABLE));
        verify(taskService, never()).enter(any(), any());
    }

    private static SortingScaleAcceptForm form(String result, String reason) {
        SortingScaleAcceptForm form = new SortingScaleAcceptForm();
        form.setVersion(0);
        form.setResult(result);
        form.setReason(reason);
        return form;
    }
}
