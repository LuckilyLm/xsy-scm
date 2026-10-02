package com.xsy.scm.report.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.report.domain.form.ScmFinanceAgingQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface ScmFinanceAgingDao {
    List<ScmFinanceAgingRowVO> query(Page<?> page, @Param("query") ScmFinanceAgingQueryForm query,
            @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmDataScopeContext scope);
    List<ScmFinanceAgingSummaryVO> summary(@Param("query") ScmFinanceAgingQueryForm query,
            @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmDataScopeContext scope);
}
