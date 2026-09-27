package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface OrderReturnItemDao extends BaseMapper<OrderReturnItemEntity> {
    OrderReturnItemEntity lock(@Param("id") Long id);

    List<OrderReturnItemEntity> list(@Param("id") Long id);

    java.math.BigDecimal reserved(@Param("id") Long id);
}
