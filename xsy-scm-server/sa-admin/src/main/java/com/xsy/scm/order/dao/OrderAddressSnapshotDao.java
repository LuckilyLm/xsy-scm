package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.order.domain.entity.OrderAddressSnapshotEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface OrderAddressSnapshotDao extends BaseMapper<OrderAddressSnapshotEntity> {
    List<OrderAddressSnapshotEntity> list(@Param("id") Long id);
}
