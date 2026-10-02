package com.xsy.scm.report.dao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.domain.dto.PurchaseDailySnapshotRow;
import com.xsy.scm.report.domain.form.PurchaseDailyQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseDailyReportVO;

@Mapper
public interface PurchaseDailyReportDao {
    int claimDate(@Param("reportDate") LocalDate reportDate);

    void insertRows(@Param("reportDate") LocalDate reportDate, @Param("rows") List<PurchaseDailySnapshotRow> rows);

    OffsetDateTime findGeneratedAt(@Param("reportDate") LocalDate reportDate);

    List<PurchaseDailyReportVO.ProductRow> queryProducts(Page<?> page, @Param("query") PurchaseDailyQueryForm query,
            @Param("warehouseScope") ScmValueScope warehouseScope,
            @Param("purchaserScope") ScmValueScope purchaserScope);
}
