package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.mall.dto.MallProductPageQuery;
import com.xianshuyuan.scm.mall.row.MallCategoryRow;
import com.xianshuyuan.scm.mall.row.MallProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MallCatalogMapper {

    /**
     * 仅返回当前客户可见且已上架的商品，分页由 MyBatis-Plus 拦截器处理。
     */
    List<MallProductRow> selectProductPage(IPage<?> page, @Param("query") MallProductPageQuery query);

    MallProductRow selectProduct(@Param("customerId") long customerId, @Param("skuId") long skuId,
                                 @Param("visibilityPolicy") String visibilityPolicy);

    List<MallCategoryRow> selectCategories(@Param("customerId") long customerId,
                                           @Param("visibilityPolicy") String visibilityPolicy);
}
