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

    /**
     * 加购前加锁读：同一账号同一规格的并发加购必须在行锁上排队，否则各自读到旧数量后覆盖写会丢数量。
     */
    MallCartItemEntity lockByAccountAndSku(@Param("accountId") Long accountId, @Param("skuId") Long skuId);

    int insert(MallCartItemEntity row);

    /**
     * 冲突时不报错：行不存在时插入；已存在（并发加购抢到先）返回 0，由调用方锁行后累加。
     */
    int insertOnConflictDoNothing(MallCartItemEntity row);

    int updateQuantity(@Param("accountId") Long accountId, @Param("skuId") Long skuId,
        @Param("quantity") BigDecimal quantity, @Param("operator") String operator);

    int removeBySku(@Param("accountId") Long accountId, @Param("skuId") Long skuId, @Param("operator") String operator);

    int removeBySkus(@Param("accountId") Long accountId, @Param("skuIds") List<Long> skuIds,
        @Param("operator") String operator);

    int clear(@Param("accountId") Long accountId, @Param("operator") String operator);
}
