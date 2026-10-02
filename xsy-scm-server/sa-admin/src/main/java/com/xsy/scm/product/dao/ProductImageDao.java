package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductImageDao extends BaseMapper<ProductImageEntity> {
    int clearPrimary(@Param("spuId") Long spuId);
}
