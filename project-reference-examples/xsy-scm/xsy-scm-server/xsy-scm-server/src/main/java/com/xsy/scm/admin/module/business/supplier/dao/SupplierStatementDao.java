package com.xsy.scm.admin.module.business.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierStatementEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierStatementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商对账单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SupplierStatementDao extends BaseMapper<SupplierStatementEntity> {

    /**
     * 分页查询供应商对账单
     */
    List<SupplierStatementVO> queryPage(Page page, @Param("queryForm") SupplierStatementQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
