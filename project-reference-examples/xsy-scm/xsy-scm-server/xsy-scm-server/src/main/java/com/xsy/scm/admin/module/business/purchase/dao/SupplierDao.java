package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.domain.entity.SupplierEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.SupplierVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商档案 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SupplierDao extends BaseMapper<SupplierEntity> {

    /**
     * 分页查询供应商
     */
    List<SupplierVO> queryPage(Page page, @Param("queryForm") SupplierQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("supplierIdList") List<Long> supplierIdList,
                            @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 查询所有未删除供应商
     */
    List<SupplierVO> queryAll();
}
