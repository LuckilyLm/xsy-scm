package net.lab1024.sa.admin.module.scm.sorting.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.sorting.dao.SortingQueryDao;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingCandidateQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingSummaryQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingCandidateLineVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingPrintVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingSkuSummaryVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskDetailVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskVO;
import net.lab1024.sa.admin.module.scm.sorting.support.SortingAccess;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.PRINTABLE;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.STATE_INVALID;

/**
 * 分拣读侧：任务列表、任务详情、按商品汇总（只读）与建单用的候选订单行。
 *
 * <p>SQL 负责排序与分页，客户端不能传排序列；范围一律由 {@link SortingAccess} 解析后下传，
 * 页面能看到的行与打印预览看到的行是同一套口径。
 */
@Service
@RequiredArgsConstructor
public class SortingQueryService {

    private final SortingQueryDao queries;
    private final SortingAccess access;

    private Page<?> page(PageParam form) {
        // 排序由 SQL 固定，客户端排序列一律不接受；分页上限与 SCM 其它列表页一致。
        if (form.getPageNum() == null || form.getPageNum() < 1 || form.getPageSize() == null
                || form.getPageSize() < 1 || form.getPageSize() > 100
                || form.getSortItemList() != null && !form.getSortItemList().isEmpty())
            throw new ScmBusinessException(VALIDATION_ERROR);
        return SmartPageUtil.convert2PageQuery(form);
    }

    public PageResult<SortingTaskVO> query(SortingTaskQueryForm form) {
        var page = page(form);
        ScmDataScopeContext scope = access.scope();
        // 无授权仓时给形状完整的空分页：不跑恒假谓词，也不让空集合渲染成 IN ()。
        if (scope.getWarehouseScope().isEmpty()) return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page, queries.tasks(page, form, scope, access.crossAssignee()));
    }

    /**
     * 任务详情。读不到行时统一按无权访问处理：分拣任务的存在性本身不是公开信息，
     * 分成 404 与 30005 会让探测主键与探测权限可分辨。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SortingTaskDetailVO detail(Long id) {
        var task = queries.task(id, access.scope(), access.crossAssignee());
        if (task == null) throw new ScmDataScopeException();
        var result = new SortingTaskDetailVO();
        result.setTask(task);
        // 明细挂在已经放行的任务下，不再各自收窄：子集收窄会破坏「按订单与按商品同一套事实」。
        result.setItems(queries.items(id));
        return result;
    }

    /**
     * 打印预览：固定版式的标签 / 小票内容，现算不留副本，也不计次。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SortingPrintVO printPreview(Long id) {
        var detail = detail(id);
        if (!PRINTABLE.contains(detail.getTask().getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var result = new SortingPrintVO();
        result.setTaskId(detail.getTask().getId());
        result.setTaskNo(detail.getTask().getTaskNo());
        result.setWarehouseNameSnapshot(detail.getTask().getWarehouseNameSnapshot());
        result.setAssigneeName(detail.getTask().getAssigneeName());
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
        if (scope.getWarehouseScope().isEmpty()) return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page, queries.skuSummary(page, form, scope, access.crossAssignee()));
    }

    /**
     * 建单用的候选订单行队列。刻意不按订单业务员维度收窄（裁决补充第 22 条）：
     * 仓库岗位默认不持任何订单范围，收窄后建单无从发生；这一侧的入口权限是建单权，
     * 且返回列不含任何价格与金额。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SortingCandidateLineVO> candidateLines(SortingCandidateQueryForm form) {
        var page = page(form);
        return SmartPageUtil.convert2PageResult(page, queries.candidateLines(page, form));
    }
}
