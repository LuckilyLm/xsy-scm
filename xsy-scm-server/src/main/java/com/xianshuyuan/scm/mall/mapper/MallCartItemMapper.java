package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.mall.entity.MallCartItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MallCartItemMapper extends BaseMapper<MallCartItemEntity> {

    List<MallCartItemEntity> selectActiveByCustomerId(@Param("customerId") long customerId);

    MallCartItemEntity selectActiveByCustomerAndSku(@Param("customerId") long customerId, @Param("skuId") long skuId);

    int softDeleteByCustomerAndSkus(@Param("customerId") long customerId, @Param("skuIds") List<Long> skuIds);

    int softDeleteByCustomer(@Param("customerId") long customerId);
}
