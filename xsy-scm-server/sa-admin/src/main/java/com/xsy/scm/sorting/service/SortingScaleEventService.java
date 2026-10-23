package com.xsy.scm.sorting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import com.xsy.scm.sorting.constant.ScmSortingScaleEventStatusEnum;
import com.xsy.scm.sorting.constant.SortingErrorCode;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingScaleCompletionDao;
import com.xsy.scm.sorting.dao.SortingScaleEventDao;
import com.xsy.scm.sorting.dao.SortingTaskDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.entity.SortingScaleCompletionEntity;
import com.xsy.scm.sorting.domain.entity.SortingScaleEventEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryItemForm;
import com.xsy.scm.sorting.domain.form.SortingScaleAcceptForm;
import com.xsy.scm.sorting.domain.form.SortingScaleBasketForm;
import com.xsy.scm.sorting.domain.form.SortingScaleCompleteForm;
import com.xsy.scm.sorting.domain.form.SortingScaleRejectForm;
import com.xsy.scm.sorting.domain.form.SortingScaleReportForm;
import com.xsy.scm.sorting.domain.vo.SortingScaleBasketVO;
import com.xsy.scm.sorting.domain.vo.SortingScaleCompletionVO;
import com.xsy.scm.sorting.domain.vo.SortingScaleEventVO;
import com.xsy.scm.sorting.support.SortingAccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 电子秤上报只记录设备读数，人工确认后才写入实际分拣量。重复事件不能重复写入；异常分拣必须说明原因。
 */
@Service
@RequiredArgsConstructor
public class SortingScaleEventService {

    private static final int LIST_LIMIT = 200;

    private final SortingScaleEventDao sortingScaleEventDao;

    private final SortingTaskDao sortingTaskDao;

    private final SortingQueryDao sortingQueryDao;

    private final SortingTaskItemDao sortingTaskItemDao;

    private final SortingTaskService sortingTaskService;

    private final SortingScaleCompletionDao sortingScaleCompletionDao;

    private final SortingAccess access;

    private final ObjectMapper objectMapper;

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
     * 上报一筐读数。只记录设备事实，不改分拣结果。
     *
     * <p>
     * 单位必须与明细销售单位一致：未知单位按公斤兜底会让斤与件混入同一累计。 毛重在未去皮时同样拒绝，因为把毛重当净重会系统性多记。
     *
     * <p>
     * 幂等分两层：{@code operationId} 标识一次人工操作，{@code eventKey} 标识一筐事实。 两者任一命中既有记录时返回原事实并置 {@code duplicated}；同一键换了内容则显式报冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingScaleBasketVO reportBasket(SortingScaleBasketForm form) {
        requireVisibleTask(form.getTaskId());
        SortingTaskItemEntity item = sortingTaskItemDao.selectById(form.getTaskItemId());
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !form.getTaskId().equals(item.getTaskId())) {
            throw new ScmBusinessException(SortingErrorCode.ITEM_NOT_IN_TASK);
        }
        if (form.getCapturedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }
        if (!Objects.equals(item.getSaleUnitSnapshot(), form.getBusinessUnit().trim())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_UNIT_MISMATCH);
        }
        if (!"NET".equals(form.getWeightKind())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_NOT_NET_WEIGHT);
        }

        SortingScaleEventEntity row = new SortingScaleEventEntity();
        row.setEventKey(form.getEventKey().trim());
        row.setOperationId(form.getOperationId().trim());
        row.setTaskId(form.getTaskId());
        row.setTaskItemId(form.getTaskItemId());
        row.setSkuId(item.getSkuId());
        row.setDeviceCode(form.getDeviceCode().trim());
        row.setDeviceSessionId(form.getDeviceSessionId());
        row.setBasketNo(form.getBasketNo());
        row.setRawReading(form.getRawReading());
        row.setUnit(form.getRawUnit().trim());
        row.setNetWeight(form.getNetWeight());
        row.setBusinessUnit(form.getBusinessUnit().trim());
        row.setWeightKind(form.getWeightKind());
        row.setConversionRule(StringUtils.trimToNull(form.getConversionRule()));
        row.setTareWeight(form.getTareWeight());
        row.setStableFlag(form.getStableFlag());
        row.setStabilitySource(StringUtils.trimToNull(form.getStabilitySource()));
        row.setJobVersion(StringUtils.trimToNull(form.getJobVersion()));
        row.setTaskItemVersion(form.getTaskItemVersion());
        row.setProtocolType(StringUtils.trimToNull(form.getProtocolType()));
        row.setAgentVersion(StringUtils.trimToNull(form.getAgentVersion()));
        row.setCapturedAt(form.getCapturedAt());
        row.setReceivedAt(OffsetDateTime.now());

        if (sortingScaleEventDao.insertBasket(row) == 1) {
            return toBasketVO(row, false);
        }
        // 两个唯一索引任一冲突都会走到这里：重查后判定是重放还是换了内容。
        SortingScaleEventEntity existing = sortingScaleEventDao.selectByOperationId(row.getOperationId());
        if (existing == null) {
            existing = sortingScaleEventDao.selectByEventKey(row.getEventKey());
        }
        if (existing == null) {
            throw new IllegalStateException("秤筐插入冲突但重读为空，eventKey=" + row.getEventKey());
        }
        if (!sameBasket(existing, row)) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_KEY_CONFLICT);
        }
        return toBasketVO(existing, true);
    }

    /**
     * 商品集合完成：把该明细当前全部待完成筐作为一个整体确认，并写入正式分拣结果。
     *
     * <p>
     * 总量由服务端按筐重算，设备提交的合计不参与判定；{@code manifestHash} 只用来确认 「设备看到的筐集合」与「服务端读到的筐集合」是同一批，避免漏掉并发新增的一筐。
     *
     * <p>
     * 正式写入仍经 {@link SortingTaskService#enter}，与手工录入共用同一条路径， 因此任务状态、明细版本、结果枚举、原因必填这些约束一条都不会被绕过。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingScaleCompletionVO completeBaskets(SortingScaleCompleteForm form) {
        // 与人工改量、改派及任务重开共用聚合锁，版本检查到正式写入之间不能被插入另一笔修改。
        SortingTaskEntity task = requireAssigneeTask(form.getTaskId());
        SortingScaleCompletionEntity replay = sortingScaleCompletionDao
            .selectByOperationId(form.getOperationId().trim());
        if (replay != null) {
            if (!Objects.equals(replay.getTaskId(), form.getTaskId())
                || !Objects.equals(replay.getTaskItemId(), form.getTaskItemId())
                || !Objects.equals(replay.getDeviceId(), form.getDeviceId())
                || !Objects.equals(replay.getEmployeeId(), form.getEmployeeId())
                || !replay.getManifestHash().equalsIgnoreCase(form.getManifestHash().trim())
                || !Objects.equals(replay.getResult(), form.getResult())
                || !Objects.equals(replay.getReason(), StringUtils.trimToNull(form.getReason()))) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_KEY_CONFLICT);
            }
            return toCompletionVO(replay, true);
        }
        SortingTaskItemEntity item = sortingTaskItemDao.selectById(form.getTaskItemId());
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !form.getTaskId().equals(item.getTaskId())) {
            throw new ScmBusinessException(SortingErrorCode.ITEM_NOT_IN_TASK);
        }
        if (!Objects.equals(String.valueOf(task.getVersion()), StringUtils.trimToNull(form.getJobVersion()))
            || form.getTaskItemVersion() == null || !Objects.equals(item.getVersion(), form.getTaskItemVersion())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_CONTEXT_CHANGED);
        }
        if (sortingScaleCompletionDao.selectByTaskItem(form.getTaskItemId()) != null) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_ALREADY_COMPLETED);
        }
        List<SortingScaleEventEntity> baskets = sortingScaleEventDao.lockPendingBaskets(form.getTaskItemId());
        if (baskets.isEmpty()) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_EMPTY);
        }
        for (SortingScaleEventEntity basket : baskets) {
            if (form.getJobVersion() != null && !Objects.equals(basket.getJobVersion(), form.getJobVersion().trim())) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_CONTEXT_CHANGED);
            }
            if (form.getTaskItemVersion() != null
                && !Objects.equals(basket.getTaskItemVersion(), form.getTaskItemVersion())) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_CONTEXT_CHANGED);
            }
            if (!Objects.equals(basket.getBusinessUnit(), item.getSaleUnitSnapshot())) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_UNIT_MISMATCH);
            }
            if (!"NET".equals(basket.getWeightKind())) {
                throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_NOT_NET_WEIGHT);
            }
        }
        List<String> eventKeys = baskets.stream().map(SortingScaleEventEntity::getEventKey).toList();
        if (!manifestDigest(eventKeys).equalsIgnoreCase(form.getManifestHash().trim())) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_MANIFEST_MISMATCH);
        }
        BigDecimal total = baskets.stream().map(SortingScaleEventEntity::getNetWeight).reduce(BigDecimal.ZERO,
            BigDecimal::add);

        SortingScaleCompletionEntity completion = new SortingScaleCompletionEntity();
        completion.setOperationId(form.getOperationId().trim());
        completion.setTaskId(form.getTaskId());
        completion.setTaskItemId(form.getTaskItemId());
        completion.setDeviceId(form.getDeviceId());
        completion.setDeviceSessionId(form.getDeviceSessionId());
        completion.setEmployeeId(form.getEmployeeId());
        completion.setBasketCount(baskets.size());
        completion.setTotalQuantity(total);
        completion.setBusinessUnit(item.getSaleUnitSnapshot());
        completion.setManifestHash(form.getManifestHash().trim().toUpperCase());
        completion.setResult(form.getResult());
        completion.setReason(StringUtils.trimToNull(form.getReason()));
        completion.setQuantityBefore(item.getSortedQuantity());
        completion.setCreatedBy(ScmOperator.current());
        sortingScaleCompletionDao.insert(completion);

        List<Long> basketIds = baskets.stream().map(SortingScaleEventEntity::getId).toList();
        if (sortingScaleEventDao.markBasketCompleted(basketIds, completion.getId(), ScmOperator.current()) != basketIds
            .size()) {
            throw new IllegalStateException("筐状态已被并发修改，taskItemId=" + form.getTaskItemId());
        }

        SortingEntryItemForm line = new SortingEntryItemForm();
        line.setId(item.getId());
        line.setVersion(form.getTaskItemVersion());
        line.setSortedQuantity(total);
        line.setResult(form.getResult());
        line.setReason(completion.getReason());
        SortingEntryForm entry = new SortingEntryForm();
        entry.setItems(List.of(line));
        sortingTaskService.enter(form.getTaskId(), entry);

        SortingScaleCompletionVO vo = toCompletionVO(completion, false);
        vo.setSortedQuantity(total);
        return vo;
    }

    /**
     * 有序事件键集合的摘要，与秤端 {@code WireJson.Digest} 保持同一算法： 紧凑 JSON 数组序列化后取 SHA-256，输出大写十六进制。
     */
    private String manifestDigest(List<String> eventKeys) {
        try {
            String json = objectMapper.writeValueAsString(eventKeys);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("集合摘要计算失败", exception);
        }
    }

    /**
     * 同一幂等键的重放必须内容一致：换了重量、筐号或任务就不是重试，而是另一笔事实。
     */
    private static boolean sameBasket(SortingScaleEventEntity existing, SortingScaleEventEntity row) {
        return Objects.equals(existing.getTaskId(), row.getTaskId())
            && Objects.equals(existing.getTaskItemId(), row.getTaskItemId())
            && Objects.equals(existing.getBasketNo(), row.getBasketNo())
            && Objects.equals(existing.getDeviceCode(), row.getDeviceCode())
            && existing.getRawReading().compareTo(row.getRawReading()) == 0 && existing.getNetWeight() != null
            && row.getNetWeight() != null && existing.getNetWeight().compareTo(row.getNetWeight()) == 0
            && Objects.equals(existing.getUnit(), row.getUnit())
            && Objects.equals(existing.getBusinessUnit(), row.getBusinessUnit())
            && Objects.equals(existing.getStableFlag(), row.getStableFlag())
            && existing.getCapturedAt().isEqual(row.getCapturedAt());
    }

    private static SortingScaleBasketVO toBasketVO(SortingScaleEventEntity row, boolean duplicated) {
        SortingScaleBasketVO vo = new SortingScaleBasketVO();
        vo.setMeasurementId(row.getId());
        vo.setEventKey(row.getEventKey());
        vo.setOperationId(row.getOperationId());
        vo.setBasketNo(row.getBasketNo());
        vo.setStatus(row.getStatus());
        vo.setDuplicated(duplicated);
        vo.setAccepted(false);
        vo.setReceivedAt(row.getReceivedAt());
        return vo;
    }

    private static SortingScaleCompletionVO toCompletionVO(SortingScaleCompletionEntity row, boolean duplicated) {
        SortingScaleCompletionVO vo = new SortingScaleCompletionVO();
        vo.setCompletionId(row.getId());
        vo.setOperationId(row.getOperationId());
        vo.setTaskItemId(row.getTaskItemId());
        vo.setBasketCount(row.getBasketCount());
        vo.setTotalQuantity(row.getTotalQuantity());
        vo.setBusinessUnit(row.getBusinessUnit());
        vo.setResult(row.getResult());
        vo.setStatus(ScmSortingScaleEventStatusEnum.ACCEPTED.name());
        vo.setDuplicated(duplicated);
        return vo;
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
            reason = StringUtils.trimToNull(form.getReason());
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
        SortingTaskEntity task = sortingQueryDao.lockTask(taskId);
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
        SortingScaleEventEntity source = sortingScaleEventDao.selectById(id);
        if (source == null) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_NOT_FOUND);
        }
        // 接受、驳回与集合完成统一先锁任务，再锁称重记录，避免相反锁序形成死锁。
        requireAssigneeTask(source.getTaskId());
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
