package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSupplierEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSupplierVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品-供应商关系 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductSupplierDao extends BaseMapper<ProductSupplierEntity> {

    /**
     * 分页查询商品-供应商关系
     */
    List<ProductSupplierVO> queryPage(Page page, @Param("queryForm") ProductSupplierQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
