package com.xsy.scm.report.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.report.domain.form.ScmFinanceProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitSummaryVO;

/** Read-only gross-profit projection over Finance, order and inventory facts. */
@Mapper
public interface ScmFinanceProfitDao {

    List<ScmFinanceProfitRowVO> query(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmFinanceProfitQueryForm query,
            @Param("scope") ScmDataScopeContext scope);

    ScmFinanceProfitSummaryVO summary(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmFinanceProfitQueryForm query,
            @Param("scope") ScmDataScopeContext scope);
}
