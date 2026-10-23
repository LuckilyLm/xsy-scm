package com.xsy.scm.mall.catalog.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.order.domain.vo.MallSkuImageVO;

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

    List<Long> newestSkuIds(@Param("customerId") Long customerId, @Param("limit") int limit);

    /**
     * 按 SKU 批量取主图 file_key，供订单列表缩略图使用。
     *
     * <p>
     * 刻意<b>不带</b>上架与可见性条件：订单是历史事实，商品下架后仍要能显示它当时的图； 可见性只在「能不能再买」时判定，不影响「当时买过什么」的展示。
     */
    List<MallSkuImageVO> primaryImagesBySkuIds(@Param("skuIds") List<Long> skuIds);
}
