package com.xsy.scm.admin.module.business.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierProductApplyEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierProductApplyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商商品提报 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SupplierProductApplyDao extends BaseMapper<SupplierProductApplyEntity> {

    /**
     * 分页查询供应商商品提报
     */
    List<SupplierProductApplyVO> queryPage(Page page, @Param("queryForm") SupplierProductApplyQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
