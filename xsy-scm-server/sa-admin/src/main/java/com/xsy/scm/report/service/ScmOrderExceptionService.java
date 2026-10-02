package com.xsy.scm.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.report.constant.ScmOrderExceptionTypeEnum;
import com.xsy.scm.report.dao.ScmOrderExceptionReadDao;
import com.xsy.scm.report.domain.form.ScmOrderExceptionQueryForm;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionRowVO;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionSummaryVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import com.xsy.scm.sorting.support.SortingAccess;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmOrderExceptionService {
    private final ScmOrderExceptionReadDao orderExceptionReadDao;
    private final ScmDataScopeService dataScopeService;
    private final SortingAccess sortingAccess;

    public PageResult<ScmOrderExceptionRowVO> query(ScmOrderExceptionQueryForm form) {
        var range = validate(form);
        var types = visibleTypes();
        if (types.isEmpty()) return ScmDataScopeService.emptyPage(form);
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, orderExceptionReadDao.query(page, form, range.startAt(),
                range.endAt(), dataScopeService.resolve(), types, sortingAccess.crossAssignee()));
    }

    public List<ScmOrderExceptionSummaryVO> summary(ScmOrderExceptionQueryForm form) {
        var range = validate(form);
        var types = visibleTypes();
        if (types.isEmpty()) return List.of();
        return orderExceptionReadDao.summary(form, range.startAt(), range.endAt(), dataScopeService.resolve(),
                types, sortingAccess.crossAssignee());
    }

    public List<ScmOrderExceptionRowVO> export(ScmOrderExceptionQueryForm form) {
        var range = validate(form);
        var types = visibleTypes();
        if (types.isEmpty()) return List.of();
        var scope = dataScopeService.resolve();
        boolean crossAssignee = sortingAccess.crossAssignee();
        return ScmReportExportGuard.exportRows(pageSize -> orderExceptionReadDao.query(
                new Page<>(1, pageSize, false), form, range.startAt(), range.endAt(), scope, types, crossAssignee));
    }

    private ScmReportTimeRange validate(ScmOrderExceptionQueryForm form) {
        SalesReportService.rejectClientSort(form);
        return ScmReportTimeRangeResolver.resolve(form);
    }

    private List<String> visibleTypes() {
        return Arrays.stream(ScmOrderExceptionTypeEnum.values())
                .filter(type -> ScmDataScopeService.hasPermission(type.getQueryPermission()))
                .map(Enum::name).toList();
    }
}
