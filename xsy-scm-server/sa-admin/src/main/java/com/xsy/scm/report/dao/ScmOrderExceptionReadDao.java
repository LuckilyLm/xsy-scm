package com.xsy.scm.report.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.report.domain.form.ScmOrderExceptionQueryForm;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionRowVO;
import com.xsy.scm.report.domain.vo.ScmOrderExceptionSummaryVO;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读源域事实，不持有更新或处理状态接口。 */
@Mapper
public interface ScmOrderExceptionReadDao {
    List<ScmOrderExceptionRowVO> query(Page<?> page, @Param("q") ScmOrderExceptionQueryForm query,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
            @Param("scope") ScmDataScopeContext scope, @Param("visibleTypes") List<String> visibleTypes,
            @Param("crossAssignee") boolean crossAssignee);

    List<ScmOrderExceptionSummaryVO> summary(@Param("q") ScmOrderExceptionQueryForm query,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
            @Param("scope") ScmDataScopeContext scope, @Param("visibleTypes") List<String> visibleTypes,
            @Param("crossAssignee") boolean crossAssignee);
}
