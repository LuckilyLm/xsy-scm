package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品分类 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductCategoryDao extends BaseMapper<ProductCategoryEntity> {

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("categoryIdList") List<Long> categoryIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
