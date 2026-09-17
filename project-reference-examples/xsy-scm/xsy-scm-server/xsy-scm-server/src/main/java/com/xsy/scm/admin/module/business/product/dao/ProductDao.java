package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 SPU Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductDao extends BaseMapper<ProductEntity> {

    /**
     * 分页查询商品
     */
    List<ProductVO> queryPage(Page page, @Param("queryForm") ProductQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("productIdList") List<Long> productIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
