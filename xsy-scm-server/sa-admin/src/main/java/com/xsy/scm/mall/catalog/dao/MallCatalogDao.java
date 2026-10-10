package com.xsy.scm.mall.catalog.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCatalogDao {
    List<MallCategoryVO> categories(@Param("customerId") Long customerId);
    List<MallProductVO> products(Page<?> page, @Param("customerId") Long customerId,
            @Param("query") MallProductQueryForm query);
    MallProductVO product(@Param("customerId") Long customerId, @Param("skuId") Long skuId);
    List<MallProductVO> productsBySkuIds(@Param("customerId") Long customerId, @Param("skuIds") List<Long> skuIds);
}
