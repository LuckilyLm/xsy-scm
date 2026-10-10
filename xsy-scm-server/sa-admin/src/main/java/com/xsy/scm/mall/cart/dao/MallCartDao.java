package com.xsy.scm.mall.cart.dao;

import com.xsy.scm.mall.cart.domain.entity.MallCartItemEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCartDao {
    List<MallCartItemEntity> listByAccount(@Param("accountId") Long accountId);
    MallCartItemEntity selectByAccountAndSku(@Param("accountId") Long accountId, @Param("skuId") Long skuId);
    int insert(MallCartItemEntity row);
    int updateQuantity(@Param("accountId") Long accountId, @Param("skuId") Long skuId,
            @Param("quantity") BigDecimal quantity, @Param("operator") String operator);
    int removeBySku(@Param("accountId") Long accountId, @Param("skuId") Long skuId, @Param("operator") String operator);
    int removeBySkus(@Param("accountId") Long accountId, @Param("skuIds") List<Long> skuIds,
            @Param("operator") String operator);
    int clear(@Param("accountId") Long accountId, @Param("operator") String operator);
}
