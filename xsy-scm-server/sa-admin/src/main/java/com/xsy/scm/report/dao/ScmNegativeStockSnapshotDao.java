package com.xsy.scm.report.dao;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.domain.form.ScmNegativeStockSnapshotForm;
import com.xsy.scm.report.domain.vo.ScmNegativeStockSnapshotVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScmNegativeStockSnapshotDao {
    long nextNumber();
    int insert(ScmNegativeStockSnapshotVO snapshot);
    ScmNegativeStockSnapshotVO find(@Param("id") Long id);
    List<ScmNegativeStockSnapshotVO> query(Page<?> page, @Param("query") ScmNegativeStockSnapshotForm.Query query,
        @Param("scope") ScmValueScope scope);
}
