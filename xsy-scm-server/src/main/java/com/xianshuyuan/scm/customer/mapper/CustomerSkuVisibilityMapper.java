package com.xianshuyuan.scm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.customer.entity.CustomerSkuVisibilityEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CustomerSkuVisibilityMapper extends BaseMapper<CustomerSkuVisibilityEntity> {
    List<CustomerSkuVisibilityEntity> selectActiveByCustomerId(@Param("customerId") long customerId);

    List<Long> selectVisibleSkuIds(@Param("customerId") long customerId, @Param("skuIds") List<Long> skuIds);

    int softDeleteOwned(@Param("customerId") long customerId, @Param("ids") List<Long> ids);
}
