package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCategoryDao
        extends
            BaseMapper<
                    ProductCategoryEntity> {
}
