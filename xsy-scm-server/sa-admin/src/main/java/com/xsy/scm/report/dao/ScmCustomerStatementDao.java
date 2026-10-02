package com.xsy.scm.report.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;

@Mapper
public interface ScmCustomerStatementDao {
    List<ScmCustomerStatementItemVO> selectEvents(@Param("query") ScmCustomerStatementForm query,
            @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmDataScopeContext scope,
            @Param("asOfCreatedAt") OffsetDateTime asOfCreatedAt, @Param("limit") int limit);

    String selectSettlementName(@Param("id") Long id);

    void insertStatement(ScmCustomerStatementVO statement);

    void insertItem(ScmCustomerStatementItemVO item);

    void insertSource(@Param("statementId") Long statementId, @Param("factType") String factType,
            @Param("factId") Long factId);

    List<ScmCustomerStatementItemVO> selectSources(@Param("id") Long id);

    ScmCustomerStatementVO selectStatement(@Param("id") Long id, @Param("employeeId") Long employeeId);

    List<ScmCustomerStatementVO> selectHistory(@Param("settlementCustomerId") Long settlementCustomerId,
            @Param("employeeId") Long employeeId);

    List<ScmCustomerStatementItemVO> selectItems(@Param("id") Long id);
}
