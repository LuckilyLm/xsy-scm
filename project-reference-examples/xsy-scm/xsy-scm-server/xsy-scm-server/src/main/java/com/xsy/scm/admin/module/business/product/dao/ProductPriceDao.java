package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductPriceEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductPriceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品价格 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductPriceDao extends BaseMapper<ProductPriceEntity> {

    /**
     * 分页查询商品价格
     */
    List<ProductPriceVO> queryPage(Page page, @Param("queryForm") ProductPriceQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("priceIdList") List<Long> priceIdList,
                            @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 按优先级取价：协议价（指定客户 + 商品/规格，优先规格级）
     */
    ProductPriceEntity selectAgreementPrice(@Param("productId") Long productId,
                                             @Param("skuId") Long skuId,
                                             @Param("customerId") Long customerId);

    /**
     * 按优先级取价：客户分级价（客户分级 + 商品/规格，优先规格级）
     */
    ProductPriceEntity selectLevelPrice(@Param("productId") Long productId,
                                        @Param("skuId") Long skuId,
                                        @Param("customerLevelId") Long customerLevelId);

    /**
     * 按优先级取价：时价（当前生效窗口内 + 商品/规格，优先规格级）
     */
    ProductPriceEntity selectMarketPrice(@Param("productId") Long productId,
                                         @Param("skuId") Long skuId,
                                         @Param("now") java.time.LocalDateTime now);

    /**
     * 按优先级取价：基础价（商品/规格兜底，优先规格级）
     */
    ProductPriceEntity selectBasePrice(@Param("productId") Long productId,
                                       @Param("skuId") Long skuId);
}
