package com.xsy.scm.sorting.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import com.xsy.scm.sorting.constant.ScmSortingScaleEventStatusEnum;
import com.xsy.scm.sorting.constant.SortingErrorCode;
import com.xsy.scm.sorting.dao.SortingScaleEventDao;
import com.xsy.scm.sorting.dao.SortingTaskDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.entity.SortingScaleEventEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryItemForm;
import com.xsy.scm.sorting.domain.form.SortingScaleAcceptForm;
import com.xsy.scm.sorting.domain.form.SortingScaleRejectForm;
import com.xsy.scm.sorting.domain.form.SortingScaleReportForm;
import com.xsy.scm.sorting.domain.vo.SortingScaleEventVO;
import com.xsy.scm.sorting.support.SortingAccess;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 电子秤事件：设备事件 → 稳定读数 → 人工接受。
 *
 * <p>
 * 三条不变量：
 * <ul>
 * <li><b>读数不等于分拣结果</b>：上报只落事实，只有 {@code accept} 才写分拣结果。 让设备直接决定发货数量，等于让一台未经校准的秤替人签字。</li>
 * <li><b>非标品必须人工裁定</b>：设备只提供稳定的原始重量与单位；人工选择正常或差异结果，非正常结果填写原因；单位不匹配则不得录入。</li>
 * <li><b>重复事件不重复写结果</b>：{@code event_key} 唯一挡住重复上报， 状态流转只认 {@code PENDING}，重复接受影响 0 行并回答「已被处理」。</li>
 * </ul>
 *
 * <p>
 * 接受写的是<b>分拣明细</b>（只写已分拣数量），不碰订单数量、不碰库存、不触发结算 —— 与手工录入走的是同一个 {@code enter} 入口，因此两条路径的约束完全一致。
 */
@Service
@RequiredArgsConstructor
public class SortingScaleEventService {

    private static final int LIST_LIMIT = 200;

    private final SortingScaleEventDao sortingScaleEventDao;

    private final SortingTaskDao sortingTaskDao;

    private final SortingTaskItemDao sortingTaskItemDao;

    private final SortingTaskService sortingTaskService;

    private final SortingAccess access;

    /**
     * 上报一个秤读数。重复上报返回既有记录并置 {@code duplicated}，不报错。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingScaleEventVO report(SortingScaleReportForm form) {
        SortingTaskEntity task = requireVisibleTask(form.getTaskId());
        SortingTaskItemEntity item = sortingTaskItemDao.selectById(form.getTaskItemId());
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !form.getTaskId().equals(item.getTaskId())) {
            throw new ScmBusinessException(SortingErrorCode.ITEM_NOT_IN_TASK);
        }
        if (form.getCapturedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }

        SortingScaleEventEntity row = new SortingScaleEventEntity();
        row.setEventKey(form.getEventKey().trim());
        row.setTaskId(form.getTaskId());
        row.setTaskItemId(form.getTaskItemId());
        row.setSkuId(item.getSkuId());
        row.setDeviceCode(form.getDeviceCode().trim());
        row.setRawReading(form.getRawReading());
        row.setUnit(form.getUnit().trim());
        row.setStableFlag(form.getStableFlag());
        row.setCapturedAt(form.getCapturedAt());
        row.setReceivedAt(OffsetDateTime.now());

        if (sortingScaleEventDao.insertIgnore(row) == 1) {
            return toVO(row, item, false);
        }
        SortingScaleEventEntity existing = sortingScaleEventDao.selectByEventKey(row.getEventKey());
        if (existing == null) {
            throw new IllegalStateException("秤读数插入冲突但重读为空，eventKey=" + row.getEventKey());
        }
        // 同键只有同一读数才能重放：设备重启、网关重试若复用 key 却换了任务/重量，
        // 必须显式报冲突，不能把另一条读数误认为本次上报成功。
        if (!Objects.equals(existing.getTaskId(), row.getTaskId())
                || !Objects.equals(existing.getTaskItemId(), row.getTaskItemId())
                || !Objects.equals(existing.getDeviceCode(), row.getDeviceCode())
                || existing.getRawReading().compareTo(row.getRawReading()) != 0
                || !Objects.equals(existing.getUnit(), row.getUnit())
                || !Objects.equals(existing.getStableFlag(), row.getStableFlag())
                || !existing.getCapturedAt().isEqual(row.getCapturedAt())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_KEY_CONFLICT);
        }
        return toVO(existing, item, true);
    }

    /**
     * 某任务的读数列表；{@code status} 为空返回全部。
     */
    @Transactional(readOnly = true)
    public List<SortingScaleEventVO> list(Long taskId, String status) {
        requireVisibleTask(taskId);
        List<SortingScaleEventEntity> rows = sortingScaleEventDao.listByTask(taskId, status, LIST_LIMIT);
        Map<Long, SortingTaskItemEntity> byId = itemsOf(rows);
        return rows.stream().map(row -> toVO(row, byId.get(row.getTaskItemId()), false)).toList();
    }

    /**
     * 接受读数：把该读数写进分拣结果。
     *
     * <p>
     * 写入经 {@link SortingTaskService#enter}（与手工录入同一入口），因此任务状态、明细版本、结果枚举、原因必填这些约束一条都不会被绕过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, SortingScaleAcceptForm form) {
        SortingScaleEventEntity event = requirePending(id, form.getVersion());
        requireAssigneeTask(event.getTaskId());
        if (!Boolean.TRUE.equals(event.getStableFlag())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_NOT_STABLE);
        }
        SortingTaskItemEntity item = sortingTaskItemDao.selectById(event.getTaskItemId());
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new ScmBusinessException(SortingErrorCode.ITEM_NOT_IN_TASK);
        }
        // 设备接入协议尚未确定。当前仅允许原始读数的单位与订单行冻结销售单位完全相同，
        // 禁止把 kg/斤/件等不同单位的数值直接写进实分量。
        if (!Objects.equals(event.getUnit(), item.getSaleUnitSnapshot())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_UNIT_MISMATCH);
        }
        boolean nonStandard = ScmProductTypeEnum.NON_STANDARD.name().equals(item.getProductTypeSnapshot());
        String result = ScmSortingResultEnum.NORMAL.name();
        String reason = null;
        if (nonStandard) {
            // 非标品以实重为准，但是否短少、超出容差属于人工业务裁定，不在设备网关猜测。
            // 非正常结果必须留下原因，并与手工 enter 命令保持同样的规则。
            if (form.getResult() == null) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_RESULT_REQUIRED);
            }
            result = form.getResult();
            reason = org.apache.commons.lang3.StringUtils.trimToNull(form.getReason());
            if (!ScmSortingResultEnum.NORMAL.name().equals(result) && reason == null) {
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
            }
        }

        SortingEntryItemForm entry = new SortingEntryItemForm();
        entry.setId(item.getId());
        // 使用当前行版本提交；enter 内部重验，行已被其他分拣员更新时整个事务回滚。
        entry.setVersion(item.getVersion());
        entry.setSortedQuantity(event.getRawReading());
        entry.setResult(result);
        entry.setReason(reason);
        SortingEntryForm entryForm = new SortingEntryForm();
        entryForm.setItems(List.of(entry));
        sortingTaskService.enter(event.getTaskId(), entryForm);

        if (sortingScaleEventDao.markAccepted(event.getId(), event.getVersion(), event.getRawReading(),
                ScmOperator.current()) != 1) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }
    }

    /**
     * 驳回读数：不写分拣结果，留原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, SortingScaleRejectForm form) {
        SortingScaleEventEntity event = requirePending(id, form.getVersion());
        requireAssigneeTask(event.getTaskId());
        if (sortingScaleEventDao.markRejected(event.getId(), event.getVersion(), form.getReason().trim(),
                ScmOperator.current()) != 1) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 上报与查看要求「看得见这个任务」：否则任何登录人都能往别人仓的任务里灌读数。
     */
    private SortingTaskEntity requireVisibleTask(Long taskId) {
        ScmDataScopeContext scope = access.scope();
        SortingTaskEntity task = sortingTaskDao.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ScmBusinessException(SortingErrorCode.TASK_NOT_FOUND);
        }
        access.requireVisible(scope, task);
        return task;
    }

    /**
     * 接受 / 驳回会写分拣结果或留下处理痕迹，因此要求与录入同样的权限：受指派本人或队列管理者。
     */
    private SortingTaskEntity requireAssigneeTask(Long taskId) {
        ScmDataScopeContext scope = access.scope();
        SortingTaskEntity task = sortingTaskDao.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ScmBusinessException(SortingErrorCode.TASK_NOT_FOUND);
        }
        access.requireAssignee(scope, task);
        return task;
    }

    private Map<Long, SortingTaskItemEntity> itemsOf(List<SortingScaleEventEntity> rows) {
        List<Long> itemIds = rows.stream().map(SortingScaleEventEntity::getTaskItemId).filter(Objects::nonNull)
                .distinct().toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SortingTaskItemEntity> byId = new HashMap<>();
        sortingTaskItemDao.selectBatchIds(itemIds).forEach(item -> byId.put(item.getId(), item));
        return byId;
    }

    private SortingScaleEventEntity requirePending(Long id, Integer version) {
        SortingScaleEventEntity event = sortingScaleEventDao.lockById(id);
        if (event == null) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_NOT_FOUND);
        }
        if (!ScmSortingScaleEventStatusEnum.PENDING.name().equals(event.getStatus())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }
        if (!Objects.equals(event.getVersion(), version)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        return event;
    }

    private static SortingScaleEventVO toVO(SortingScaleEventEntity row, SortingTaskItemEntity item,
            boolean duplicated) {
        SortingScaleEventVO vo = new SortingScaleEventVO();
        vo.setId(row.getId());
        vo.setEventKey(row.getEventKey());
        vo.setTaskId(row.getTaskId());
        vo.setTaskItemId(row.getTaskItemId());
        vo.setSkuId(row.getSkuId());
        vo.setDeviceCode(row.getDeviceCode());
        vo.setRawReading(row.getRawReading());
        vo.setUnit(row.getUnit());
        vo.setStableFlag(row.getStableFlag());
        vo.setCapturedAt(row.getCapturedAt());
        vo.setReceivedAt(row.getReceivedAt());
        vo.setStatus(row.getStatus());
        vo.setAcceptedQuantity(row.getAcceptedQuantity());
        vo.setAcceptedAt(row.getAcceptedAt());
        vo.setAcceptedBy(row.getAcceptedBy());
        vo.setRejectedAt(row.getRejectedAt());
        vo.setRejectedBy(row.getRejectedBy());
        vo.setRejectReason(row.getRejectReason());
        vo.setVersion(row.getVersion());
        vo.setDuplicated(duplicated);
        if (item != null) {
            vo.setSkuCodeSnapshot(item.getSkuCodeSnapshot());
            vo.setProductNameSnapshot(item.getProductNameSnapshot());
        }
        return vo;
    }
}
