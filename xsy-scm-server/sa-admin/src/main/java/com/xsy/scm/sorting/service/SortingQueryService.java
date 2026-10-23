package com.xsy.scm.sorting.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.sorting.dao.SortingPartRecordDao;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingTaskDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import org.springframework.transaction.annotation.Propagation;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.form.SortingCandidateQueryForm;
import com.xsy.scm.sorting.domain.form.SortingSummaryQueryForm;
import com.xsy.scm.sorting.domain.form.SortingTaskQueryForm;
import com.xsy.scm.sorting.domain.vo.SortingCandidateLineVO;
import com.xsy.scm.sorting.domain.vo.SortingCustomerLineVO;
import com.xsy.scm.sorting.domain.vo.SortingPartRecordVO;
import com.xsy.scm.sorting.domain.vo.SortingPartHistoryVO;
import com.xsy.scm.sorting.domain.vo.SortingPerformanceVO;
import com.xsy.scm.sorting.domain.vo.SortingPrintVO;
import com.xsy.scm.sorting.domain.vo.SortingSkuSummaryVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskDetailVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskVO;
import com.xsy.scm.sorting.support.SortingAccess;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.sorting.constant.SortingConstant.PRINTABLE;
import static com.xsy.scm.sorting.constant.SortingErrorCode.STATE_INVALID;
import static com.xsy.scm.sorting.constant.SortingErrorCode.TASK_NOT_FOUND;

/**
 * 分拣查询：任务列表、任务详情、按商品汇总和建单候选行。
 *
 * <p>
 * 排序分页都在 SQL 里，客户端不能传排序列；可见范围统一由 {@link SortingAccess} 解析， 页面和打印预览用同一套规则。
 */
@Service
@RequiredArgsConstructor
public class SortingQueryService {

    private final SortingQueryDao sortingQueryDao;
    private final SortingPartRecordDao sortingPartRecordDao;
    private final SortingTaskDao sortingTaskDao;
    private final SortingTaskItemDao sortingTaskItemDao;
    private final SortingAccess access;

    @Transactional(readOnly = true)
    public SortingTaskEntity requireTaskForDeviceItem(Long itemId) {
        var item = sortingTaskItemDao.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new ScmDataScopeException();
        }
        return requireTaskForDevice(item.getTaskId());
    }

    /** 设备写入与改派、取消共用任务聚合锁。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public SortingTaskEntity lockTaskForDevice(Long taskId) {
        var task = sortingQueryDao.lockTask(taskId);
        if (task == null) {
            throw new ScmDataScopeException();
        }
        access.requireVisible(access.scope(), task);
        return task;
    }

    private Page<?> page(PageParam form) {
        // 排序由 SQL 固定，客户端排序列一律不接受；分页上限与 SCM 其它列表页一致。
        if (form.getPageNum() == null || form.getPageNum() < 1 || form.getPageSize() == null || form.getPageSize() < 1
            || form.getPageSize() > 100 || form.getSortItemList() != null && !form.getSortItemList().isEmpty())
            throw new ScmBusinessException(VALIDATION_ERROR);
        return SmartPageUtil.convert2PageQuery(form);
    }

    public PageResult<SortingTaskVO> query(SortingTaskQueryForm form) {
        var page = page(form);
        ScmDataScopeContext scope = access.scope();
        // 没有授权仓时直接返回完整形状的空分页：不跑恒假条件，也不让空集合渲染成 IN ()。
        if (scope.getWarehouseScope().isEmpty())
            return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page, sortingQueryDao.tasks(page, form, scope, access.crossAssignee()));
    }

    /** 任务详情。读不到行统一按无权访问处理：任务是否存在不是公开信息，分开 404 和 30005 就能被拿来探测主键。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SortingTaskDetailVO detail(Long id) {
        var task = sortingQueryDao.task(id, access.scope(), access.crossAssignee());
        if (task == null)
            throw new ScmDataScopeException();
        var result = new SortingTaskDetailVO();
        result.setTask(task);
        // 明细挂在已经放行的任务下，不再各自收窄：子集收窄会破坏「按订单与按商品同一套数据」。
        // 满赠赠品是第二类来源：它不写 sorting_task_item（那张表必须挂订单行），
        // 但仓库要备货，追加在订单行之后并带 sourceType，界面据此标「赠品」。
        var items = new ArrayList<>(sortingQueryDao.items(id));
        items.addAll(sortingQueryDao.giftItems(id));
        result.setItems(items);
        return result;
    }

    /**
     * 设备接入需要的任务视图：秤端作业要用任务版本做乐观锁，而列表与详情 VO 不暴露 version。
     *
     * <p>
     * 返回实体而不扩宽 {@link SortingTaskVO}：设备端只取版本、状态、仓库和受指派人， 免得所有后台调用方都多看到内部版本字段。可见性仍按当前员工数据范围判定。
     */
    @Transactional(readOnly = true)
    public SortingTaskEntity requireTaskForDevice(Long taskId) {
        SortingTaskEntity task = sortingTaskDao.selectById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ScmBusinessException(TASK_NOT_FOUND);
        }
        access.requireVisible(access.scope(), task);
        return task;
    }

    /** 分次事件先判任务可见，再核对明细归属；列表按该明细游标分页，累计量取全部历史最新事件。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SortingPartHistoryVO parts(Long taskId, Long itemId, Long beforeId, int limit) {
        var task = detail(taskId);
        if (itemId == null || limit < 1 || limit > 100 || beforeId != null && beforeId <= 0) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (task.getItems().stream()
            .noneMatch(item -> itemId.equals(item.getId()) && item.getSalesOrderItemId() != null)) {
            throw new ScmDataScopeException();
        }
        SortingPartRecordVO latest = sortingPartRecordDao.latestInTask(taskId, itemId);
        var fetched = sortingPartRecordDao.byItem(taskId, itemId, beforeId, limit + 1);
        boolean hasMore = fetched.size() > limit;
        var records = hasMore ? fetched.subList(0, limit) : fetched;
        Long nextBeforeId = hasMore ? records.getLast().getId() : null;
        return new SortingPartHistoryVO(records, latest == null ? null : latest.getTotalQuantity(), hasMore,
            nextBeforeId);
    }

    /** 打印预览：固定版式的标签 / 小票内容，现算不留副本，也不计次。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SortingPrintVO printPreview(Long id) {
        var detail = detail(id);
        if (!PRINTABLE.contains(detail.getTask().getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var result = new SortingPrintVO();
        result.setTaskId(detail.getTask().getId());
        result.setTaskNo(detail.getTask().getTaskNo());
        result.setWarehouseNameSnapshot(detail.getTask().getWarehouseNameSnapshot());
        result.setAssigneeName(detail.getTask().getAssigneeName());
        result.setSupplierNameSnapshot(detail.getTask().getSupplierNameSnapshot());
        result.setStatus(detail.getTask().getStatus());
        result.setPrintCount(detail.getTask().getPrintCount());
        result.setGeneratedAt(OffsetDateTime.now());
        result.setItems(detail.getItems());
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SortingSkuSummaryVO> summary(SortingSummaryQueryForm form) {
        var page = page(form);
        ScmDataScopeContext scope = access.scope();
        if (scope.getWarehouseScope().isEmpty())
            return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page,
            sortingQueryDao.skuSummary(page, form, scope, access.crossAssignee()));
    }

    /** 按客户 / 订单明细展示同一份活动任务数据，不跨销售单位汇总，也不提供批量写操作。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SortingCustomerLineVO> customerLines(SortingSummaryQueryForm form) {
        var page = page(form);
        ScmDataScopeContext scope = access.scope();
        if (scope.getWarehouseScope().isEmpty())
            return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page,
            sortingQueryDao.customerLines(page, form, scope, access.crossAssignee()));
    }

    /** 仅聚合已完成任务中最后确认人登记的行，数量按原销售单位独立分组。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SortingPerformanceVO> confirmedPerformance(SortingSummaryQueryForm form) {
        var page = page(form);
        ScmDataScopeContext scope = access.scope();
        if (scope.getWarehouseScope().isEmpty())
            return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page,
            sortingQueryDao.confirmedPerformance(page, form, scope, access.crossAssignee()));
    }

    /** 建单用的候选订单行队列，不按订单业务员维度收窄：仓库岗位通过建单权限读取候选项，返回列不含价格或金额。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SortingCandidateLineVO> candidateLines(SortingCandidateQueryForm form) {
        var page = page(form);
        return SmartPageUtil.convert2PageResult(page, sortingQueryDao.candidateLines(page, form));
    }
}
