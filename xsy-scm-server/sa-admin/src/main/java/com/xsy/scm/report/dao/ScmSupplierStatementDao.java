package com.xsy.scm.report.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.report.domain.form.ScmSupplierStatementForm;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementVO;

@Mapper
public interface ScmSupplierStatementDao {
    OffsetDateTime selectSnapshotAt();

    List<ScmSupplierStatementItemVO> selectEvents(@Param("query") ScmSupplierStatementForm query,
            @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmDataScopeContext scope,
            @Param("asOfCreatedAt") OffsetDateTime asOfCreatedAt, @Param("limit") int limit);

    String selectSupplierName(@Param("id") Long id);

    void insertStatement(ScmSupplierStatementVO statement);

    void insertItem(ScmSupplierStatementItemVO item);

    void insertSource(@Param("statementId") Long statementId, @Param("factType") String factType,
            @Param("factId") Long factId);

    List<ScmSupplierStatementItemVO> selectSources(@Param("id") Long id);

    ScmSupplierStatementVO selectStatement(@Param("id") Long id, @Param("employeeId") Long employeeId);

    List<ScmSupplierStatementVO> selectHistory(@Param("supplierId") Long supplierId,
            @Param("employeeId") Long employeeId, @Param("offset") long offset);

    List<ScmSupplierStatementItemVO> selectItems(@Param("id") Long id);
}
