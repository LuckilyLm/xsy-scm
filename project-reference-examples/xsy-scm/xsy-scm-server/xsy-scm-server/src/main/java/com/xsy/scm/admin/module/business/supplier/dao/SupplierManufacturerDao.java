package com.xsy.scm.admin.module.business.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierManufacturerEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierManufacturerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商厂商信息 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SupplierManufacturerDao extends BaseMapper<SupplierManufacturerEntity> {

    /**
     * 分页查询供应商厂商信息
     */
    List<SupplierManufacturerVO> queryPage(Page page, @Param("queryForm") SupplierManufacturerQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
