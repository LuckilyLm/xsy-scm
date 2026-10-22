package com.xsy.scm.trace.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TraceInspectionReportDao {
    List<TraceInspectionReportVO> listByLot(@Param("lotId") Long lotId, @Param("scope") ScmValueScope scope);
    TraceInspectionReportVO lockById(@Param("id") Long id, @Param("scope") ScmValueScope scope);
    int insert(TraceInspectionReportVO report);
    int updateDraft(TraceInspectionReportVO report);
    int publish(TraceInspectionReportVO report);
    int withdraw(TraceInspectionReportVO report);
    List<TraceInspectionReportVO> publishedByLot(@Param("lotId") Long lotId);
}
