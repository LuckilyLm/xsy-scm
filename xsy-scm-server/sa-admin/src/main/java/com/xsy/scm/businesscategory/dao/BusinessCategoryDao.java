package com.xsy.scm.businesscategory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessCategoryDao extends BaseMapper<BusinessCategoryEntity> {
}
