package com.xsy.scm.device.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.device.constant.ScaleDeviceErrorCode;
import com.xsy.scm.device.dao.ScaleJobLeaseDao;
import com.xsy.scm.device.domain.entity.ScaleJobLeaseEntity;
import com.xsy.scm.device.domain.form.ScaleJobActionForm;
import com.xsy.scm.device.domain.vo.ScaleJobItemVO;
import com.xsy.scm.device.domain.vo.ScaleJobPageVO;
import com.xsy.scm.device.domain.vo.ScaleJobVO;
import com.xsy.scm.device.support.ScaleDeviceContext;
import com.xsy.scm.device.support.ScaleDevicePrincipal;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.form.SortingTaskQueryForm;
import com.xsy.scm.sorting.domain.vo.SortingTaskDetailVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskItemVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskVO;
import com.xsy.scm.sorting.service.SortingQueryService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秤端作业队列：列出员工可作业的任务、领取与释放租约。
 *
 * <p>
 * 可见范围永远是「设备绑定仓库 ∩ 员工数据范围 ∩ 员工受指派」，三者取交集。 秤端不能通过改请求参数越权：仓库来自设备绑定，员工来自服务端会话，两者都不接受客户端自报。
 *
 * <p>
 * 本服务只读分拣域并写自己的租约表，不修改任何分拣数量；正式分拣结果仍由员工在确认时 经 {@code SortingTaskService.enter} 写入。
 */
@Service
@RequiredArgsConstructor
public class ScaleDeviceJobService {

    /**
     * 租约有效期：一个班次内够用，又不能长到让离场设备长期占住任务。
     */
    private static final int LEASE_MINUTES = 120;

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 可作业状态：待分拣与分拣中。已完成或已取消的任务不再出现在秤端队列。
     */
    private static final List<String> OPEN_STATUSES = List.of(ScmSortingTaskStatusEnum.PENDING.name(),
        ScmSortingTaskStatusEnum.SORTING.name());

    private final SortingQueryService sortingQueryService;

    private final ScaleJobLeaseDao scaleJobLeaseDao;

    /**
     * 当前员工在本设备仓库内可作业的任务，按任务主键顺序分页。
     *
     * <p>
     * 游标就是页码，不是业务标识；秤端只负责原样回传，不据此推断任务是否完整。
     */
    @Transactional(readOnly = true)
    public ScaleJobPageVO jobs(String cursor, int limit) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        int pageNum = parseCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        SortingTaskQueryForm form = new SortingTaskQueryForm();
        form.setWarehouseId(principal.warehouseId());
        form.setAssigneeEmployeeId(principal.employeeId());
        form.setStatusList(OPEN_STATUSES);
        form.setPageNum((long) pageNum);
        form.setPageSize((long) pageSize);
        PageResult<SortingTaskVO> page = sortingQueryService.query(form);
        OffsetDateTime now = OffsetDateTime.now();
        List<ScaleJobVO> items = page.getList().stream().map(task -> toJobVO(task.getId(), principal, now)).toList();
        String nextCursor = (long) pageNum * pageSize < page.getTotal() ? String.valueOf(pageNum + 1) : null;
        return new ScaleJobPageVO(principal.sessionExpiresAt(), items, nextCursor);
    }

    @Transactional(readOnly = true)
    public ScaleJobVO job(String jobId) {
        return toJobVO(parseId(jobId), ScaleDeviceContext.require(), OffsetDateTime.now());
    }

    /**
     * 领取作业租约。同一设备重复领取返回当前租约；被其他设备持有时明确拒绝。
     *
     * <p>
     * 并发仲裁靠 {@code uk_scale_job_lease_active_task} 这个部分唯一索引，不靠先查后插： 两台秤同时点「领取」时只有一笔能插入成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScaleJobVO claim(String jobId, ScaleJobActionForm form) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        Long taskId = parseId(jobId);
        SortingTaskEntity task = requireClaimable(taskId, form.getJobVersion(), principal);
        OffsetDateTime now = OffsetDateTime.now();
        ScaleJobLeaseEntity existing = scaleJobLeaseDao.selectActiveByTask(taskId, now);
        if (existing != null) {
            if (Objects.equals(existing.getDeviceId(), principal.deviceId())) {
                return toJobVO(task, existing, principal);
            }
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_LEASED_BY_OTHER);
        }
        // 过期但未释放的行仍占唯一索引，先收掉再插入。
        scaleJobLeaseDao.releaseExpired(taskId, now);
        ScaleJobLeaseEntity lease = new ScaleJobLeaseEntity();
        lease.setDeviceId(principal.deviceId());
        lease.setSessionId(principal.sessionId());
        lease.setEmployeeId(principal.employeeId());
        lease.setTaskId(taskId);
        lease.setTaskVersion(task.getVersion());
        lease.setLeasedAt(now);
        lease.setExpiresAt(now.plusMinutes(LEASE_MINUTES));
        try {
            scaleJobLeaseDao.insert(lease);
        } catch (DuplicateKeyException exception) {
            // 并发下另一台设备刚插入：回明确拒绝，不把冲突报成服务器错误。
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_LEASED_BY_OTHER);
        }
        return toJobVO(task, lease, principal);
    }

    /**
     * 释放租约。没有有效租约时按幂等处理：秤端重试不会报错，也不会影响其他设备的租约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScaleJobVO release(String jobId, ScaleJobActionForm form) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        Long taskId = parseId(jobId);
        OffsetDateTime now = OffsetDateTime.now();
        ScaleJobLeaseEntity lease = scaleJobLeaseDao.selectActiveByTaskAndDevice(taskId, principal.deviceId(), now);
        if (lease != null) {
            scaleJobLeaseDao.release(lease.getId(), trimToNull(form.getReason()), now);
        }
        return toJobVO(taskId, principal, now);
    }

    /**
     * 领取前置校验：可见、可作业、属于本设备仓库、由当前员工受指派，且版本与秤端看到的一致。
     */
    private SortingTaskEntity requireClaimable(Long taskId, String jobVersion, ScaleDevicePrincipal principal) {
        SortingTaskEntity task = sortingQueryService.requireTaskForDevice(taskId);
        if (!OPEN_STATUSES.contains(task.getStatus())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_STATE_INVALID);
        }
        if (!Objects.equals(task.getWarehouseId(), principal.warehouseId())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_WAREHOUSE_MISMATCH);
        }
        if (!Objects.equals(task.getAssigneeEmployeeId(), principal.employeeId())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_NOT_ASSIGNEE);
        }
        // 版本不一致说明任务在秤端看到它之后被改派或重开，必须重新拉取而不是继续作业。
        if (!String.valueOf(task.getVersion()).equals(jobVersion)) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_STATE_INVALID);
        }
        return task;
    }

    private ScaleJobVO toJobVO(Long taskId, ScaleDevicePrincipal principal, OffsetDateTime now) {
        SortingTaskEntity task = sortingQueryService.requireTaskForDevice(taskId);
        ScaleJobLeaseEntity lease = scaleJobLeaseDao.selectActiveByTaskAndDevice(taskId, principal.deviceId(), now);
        return toJobVO(task, lease, principal);
    }

    private ScaleJobVO toJobVO(SortingTaskEntity task, ScaleJobLeaseEntity lease, ScaleDevicePrincipal principal) {
        SortingTaskDetailVO detail = sortingQueryService.detail(task.getId());
        ScaleJobVO vo = new ScaleJobVO();
        String taskId = String.valueOf(task.getId());
        vo.setJobId(taskId);
        vo.setTaskId(taskId);
        vo.setTaskNo(task.getTaskNo());
        vo.setTaskVersion(String.valueOf(task.getVersion()));
        vo.setJobVersion(String.valueOf(task.getVersion()));
        vo.setStatus(task.getStatus());
        vo.setItems(detail.getItems().stream().map(this::toItemVO).toList());
        if (lease != null) {
            vo.setExpiresAt(lease.getExpiresAt());
            vo.setClaimedByDeviceCode(
                Objects.equals(lease.getDeviceId(), principal.deviceId()) ? principal.deviceCode() : null);
        }
        return vo;
    }

    private ScaleJobItemVO toItemVO(SortingTaskItemVO item) {
        ScaleJobItemVO vo = new ScaleJobItemVO();
        vo.setTaskItemId(String.valueOf(item.getId()));
        vo.setProductName(item.getProductNameSnapshot());
        vo.setSpecName(item.getSpecNameSnapshot());
        vo.setSaleUnit(item.getSaleUnitSnapshot());
        vo.setPlannedQuantity(plain(item.getPlannedQuantitySnapshot()));
        vo.setSortedQuantity(plain(item.getSortedQuantity()));
        vo.setProductType(item.getProductTypeSnapshot());
        vo.setActive(!"INACTIVE".equals(item.getOccupationStatus()));
        vo.setTaskItemVersion(item.getVersion() == null ? 0L : item.getVersion().longValue());
        vo.setCustomerName(item.getCustomerNameSnapshot());
        vo.setOrderNo(item.getOrderNoSnapshot());
        vo.setSkuId(item.getSkuId() == null ? null : String.valueOf(item.getSkuId()));
        vo.setCustomerId(item.getCustomerId() == null ? null : String.valueOf(item.getCustomerId()));
        return vo;
    }

    /**
     * 数量转字符串保留原始小数：业务数量不经过浮点，也不把 8.84 显示成 8.8400 之外的值。
     */
    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            int pageNum = Integer.parseInt(cursor.trim());
            return pageNum < 1 ? 1 : pageNum;
        } catch (NumberFormatException exception) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_NOT_FOUND);
        }
    }

    private static Long parseId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_NOT_FOUND);
        }
        try {
            return Long.parseLong(jobId.trim());
        } catch (NumberFormatException exception) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.JOB_NOT_FOUND);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
