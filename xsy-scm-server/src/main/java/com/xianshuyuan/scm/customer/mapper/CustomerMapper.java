package com.xianshuyuan.scm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.customer.dto.CustomerPageQuery;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomerMapper extends BaseMapper<CustomerEntity> {
    IPage<CustomerEntity> selectCustomerPage(IPage<CustomerEntity> page, @Param("query") CustomerPageQuery query);

    int softDelete(@Param("id") long id, @Param("version") int version);
}
