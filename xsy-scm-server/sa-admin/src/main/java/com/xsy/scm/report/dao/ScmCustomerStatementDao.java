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

    /**
     * 对账单可选的过账行：只有过账金额，三个资金变动列一律为 0。
     *
     * <p>
     * 读作独立查询而不是并进 {@code selectEvents} 的 UNION：过账不是财务事实， 混进同一条 SQL 会让人以为它也参与滚动余额的计算。合并与排序由服务层显式完成。
     */
    List<ScmCustomerStatementItemVO> selectPostingEvents(@Param("query") ScmCustomerStatementForm query,
        @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmDataScopeContext scope,
        @Param("asOfCreatedAt") OffsetDateTime asOfCreatedAt, @Param("limit") int limit);

    String selectSettlementName(@Param("id") Long id);

    void insertStatement(ScmCustomerStatementVO statement);

    void insertItem(ScmCustomerStatementItemVO item);

    void insertSource(@Param("statementId") Long statementId, @Param("factType") String factType,
        @Param("factId") Long factId);

    List<ScmCustomerStatementItemVO> selectSources(@Param("id") Long id);

    ScmCustomerStatementVO selectStatement(@Param("id") Long id, @Param("employeeId") Long employeeId);

    /** 打印专用：按 id 取冻结版本，不按生成人过滤；可见性由调用方按源事实重验。 */
    ScmCustomerStatementVO selectStatementById(@Param("id") Long id);

    List<ScmCustomerStatementVO> selectHistory(@Param("settlementCustomerId") Long settlementCustomerId,
        @Param("employeeId") Long employeeId);

    List<ScmCustomerStatementItemVO> selectItems(@Param("id") Long id);
}
