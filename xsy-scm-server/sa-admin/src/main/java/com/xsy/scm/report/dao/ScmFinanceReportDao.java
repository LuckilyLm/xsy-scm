package com.xsy.scm.report.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.domain.form.ScmFinanceReportFilter;
import com.xsy.scm.report.domain.vo.ScmFinanceOverviewVO;
import com.xsy.scm.report.domain.vo.ScmFinancePayableDetailVO;
import com.xsy.scm.report.domain.vo.ScmFinanceReceivableDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** Read-only finance projection over immutable facts. */
@Mapper
public interface ScmFinanceReportDao {

    ScmFinanceOverviewVO overview(@Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
            @Param("orderScope") ScmValueScope orderScope, @Param("purchaserScope") ScmValueScope purchaserScope);

    List<ScmFinanceReceivableDetailVO> receivableDetails(Page<?> page, @Param("endAt") OffsetDateTime endAt,
            @Param("query") ScmFinanceReportFilter query, @Param("orderScope") ScmValueScope orderScope);

    List<ScmFinancePayableDetailVO> payableDetails(Page<?> page, @Param("endAt") OffsetDateTime endAt,
            @Param("query") ScmFinanceReportFilter query, @Param("purchaserScope") ScmValueScope purchaserScope);
}
