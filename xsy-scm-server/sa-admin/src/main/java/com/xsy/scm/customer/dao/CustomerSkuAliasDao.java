package com.xsy.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.customer.domain.entity.CustomerSkuAliasEntity;
import com.xsy.scm.customer.domain.vo.CustomerSkuAliasVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerSkuAliasDao extends BaseMapper<CustomerSkuAliasEntity> {
    List<CustomerSkuAliasVO> listByCustomer(@Param("customerId") Long customerId);

    int softDelete(@Param("id") Long id, @Param("customerId") Long customerId, @Param("version") Integer version,
        @Param("operator") String operator);
}
