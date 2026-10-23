package com.xsy.scm.sorting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.sorting.dao.SortingPartRecordDao;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryItemForm;
import com.xsy.scm.sorting.domain.form.SortingStandardConfirmForm;
import com.xsy.scm.sorting.domain.vo.SortingStandardPreviewVO;
import com.xsy.scm.sorting.domain.vo.SortingStandardResultVO;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import com.xsy.scm.sorting.support.SortingAccess;
import com.xsy.scm.trace.service.TraceUnitService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.sorting.constant.SortingConstant.WORKING;
import static com.xsy.scm.sorting.constant.SortingErrorCode.ITEM_NOT_IN_TASK;
import static com.xsy.scm.sorting.constant.SortingErrorCode.STANDARD_CONFIRM_INVALID;
import static com.xsy.scm.sorting.constant.SortingErrorCode.STATE_INVALID;

@Service
@RequiredArgsConstructor
public class SortingStandardService {
    private final SortingQueryDao sortingQueryDao;
    private final SortingTaskItemDao sortingTaskItemDao;
    private final SortingPartRecordDao sortingPartRecordDao;
    private final SortingAccess sortingAccess;
    private final SortingStandardPolicy sortingStandardPolicy;
    private final SortingTaskService sortingTaskService;
    private final TraceUnitService traceUnitService;
    private final ScmIdempotencyService idempotencyService;

    // 与录入、分次追加、取消和改派共用任务锁，预览不返回交错读取的版本与数量。
    @Transactional(rollbackFor = Exception.class)
    public SortingStandardPreviewVO preview(Long taskId) {
        var task = lockAuthorizedTask(taskId);
        requireWorking(task);
        var items = rows(taskId).stream().map(row -> {
            String reason = rejection(row);
            return new SortingStandardPreviewVO.Item(row.getId(), row.getVersion(), row.getOrderNoSnapshot(),
                row.getCustomerNameSnapshot(), row.getProductNameSnapshot(), row.getSpecNameSnapshot(),
                row.getPlannedQuantitySnapshot() == null ? null : row.getPlannedQuantitySnapshot().toPlainString(),
                row.getSaleUnitSnapshot(), reason == null, reason);
        }).toList();
        return new SortingStandardPreviewVO(taskId, task.getVersion(), items);
    }

    @Transactional(rollbackFor = Exception.class)
    public SortingStandardResultVO confirm(Long taskId, SortingStandardConfirmForm form, String key) {
        var task = lockAuthorizedTask(taskId);
        validate(form);
        var claim = idempotencyService.claim("SORTING_STANDARD_CONFIRM:" + taskId, key, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, SortingStandardResultVO.class);
        }
        requireWorking(task);
        if (!Objects.equals(task.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        var rows = rows(taskId).stream().collect(Collectors.toMap(SortingTaskItemEntity::getId, Function.identity()));
        var entries = new ArrayList<SortingEntryItemForm>();
        for (var selected : form.getItems().stream()
            .sorted(java.util.Comparator.comparing(SortingStandardConfirmForm.Item::id)).toList()) {
            var row = rows.get(selected.id());
            if (row == null) {
                throw new ScmBusinessException(ITEM_NOT_IN_TASK);
            }
            if (!Objects.equals(row.getVersion(), selected.version())) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            if (rejection(row) != null) {
                throw new ScmBusinessException(STANDARD_CONFIRM_INVALID);
            }
            var entry = new SortingEntryItemForm();
            entry.setId(row.getId());
            entry.setVersion(row.getVersion());
            entry.setSortedQuantity(row.getPlannedQuantitySnapshot());
            entry.setResult(ScmSortingResultEnum.NORMAL.name());
            entries.add(entry);
        }
        var entryForm = new SortingEntryForm();
        entryForm.setItems(entries);
        // 跨 Spring Service 调用加入当前事务；保留正式录入的包装码守卫、行版本和追加审计。
        sortingTaskService.enter(taskId, entryForm);
        var result = new SortingStandardResultVO(taskId, entries.stream().map(SortingEntryItemForm::getId).toList());
        idempotencyService.complete(claim, "SORTING_TASK", taskId, result);
        return result;
    }

    private SortingTaskEntity lockAuthorizedTask(Long taskId) {
        if (!ScmDataScopeService.hasPermission(SortingPermission.ITEM_UPDATE)) {
            throw new ScmDataScopeException();
        }
        var scope = sortingAccess.scope();
        var task = sortingQueryDao.lockTask(taskId);
        if (task == null) {
            throw new ScmDataScopeException();
        }
        sortingAccess.requireAssignee(scope, task);
        // 快捷确认只允许本人，不能借队列管理或超级管理员身份替他人确认。
        if (task.getAssigneeEmployeeId() == null
            || !Objects.equals(scope.getEmployeeId(), task.getAssigneeEmployeeId())) {
            throw new ScmDataScopeException();
        }
        return task;
    }

    private void requireWorking(SortingTaskEntity task) {
        if (!WORKING.contains(task.getStatus())) {
            throw new ScmBusinessException(STATE_INVALID);
        }
    }

    private List<SortingTaskItemEntity> rows(Long taskId) {
        return sortingTaskItemDao.selectList(new LambdaQueryWrapper<SortingTaskItemEntity>()
            .eq(SortingTaskItemEntity::getTaskId, taskId).orderByAsc(SortingTaskItemEntity::getId));
    }

    private String rejection(SortingTaskItemEntity row) {
        String reason = sortingStandardPolicy.rejection(row, sortingPartRecordDao.latest(row.getId()));
        if (reason != null) {
            return reason;
        }
        return traceUnitService.previewSortedQuantityRejection(row.getId(), row.getPlannedQuantitySnapshot());
    }

    private void validate(SortingStandardConfirmForm form) {
        if (form == null || form.getVersion() == null || form.getVersion() < 0 || form.getItems() == null
            || form.getItems().isEmpty() || form.getItems().size() > 500
            || form.getItems().stream()
                .anyMatch(item -> item == null || item.id() == null || item.id() <= 0 || item.version() == null
                    || item.version() < 0)
            || form.getItems().stream().map(SortingStandardConfirmForm.Item::id).distinct().count() != form.getItems()
                .size()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}
