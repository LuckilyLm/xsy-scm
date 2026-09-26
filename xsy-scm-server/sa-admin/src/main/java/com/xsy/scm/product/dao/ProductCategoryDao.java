package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.product.domain.entity.ProductCategoryEntity;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductCategoryDao extends BaseMapper<ProductCategoryEntity> {
}
