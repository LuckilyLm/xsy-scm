package com.xianshuyuan.scm.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuEntity> {

    List<ProductSkuEntity> selectActiveBySpuId(@Param("spuId") long spuId);

    List<ProductSkuEntity> selectActiveBySpuIds(@Param("spuIds") List<Long> spuIds);

    List<ProductSkuEntity> selectOrderableByIds(@Param("skuIds") List<Long> skuIds);

    int clearDefault(@Param("spuId") long spuId);
}
