package com.xianshuyuan.scm.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.product.dto.ProductPageQuery;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpuEntity> {

    IPage<ProductSpuEntity> selectProductPage(
        Page<ProductSpuEntity> page,
        @Param("query") ProductPageQuery query
    );
}
