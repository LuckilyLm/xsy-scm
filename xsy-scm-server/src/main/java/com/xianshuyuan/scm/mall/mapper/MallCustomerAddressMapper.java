package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.mall.entity.MallCustomerAddressEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MallCustomerAddressMapper extends BaseMapper<MallCustomerAddressEntity> {

    List<MallCustomerAddressEntity> selectActiveByCustomerId(@Param("customerId") long customerId);

    MallCustomerAddressEntity selectActiveByIdAndCustomer(@Param("id") long id, @Param("customerId") long customerId);

    int clearDefault(@Param("customerId") long customerId, @Param("excludedId") Long excludedId);

    int softDeleteByIdAndCustomer(@Param("id") long id, @Param("customerId") long customerId);
}
