package com.xianshuyuan.scm.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.product.entity.ProductCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategoryEntity> {

    List<ProductCategoryEntity> selectActiveCategories();

    long countActiveChildren(@Param("parentId") long parentId);

    long countActiveProducts(@Param("categoryId") long categoryId);
}
