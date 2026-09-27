package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.order.domain.entity.IdempotencyRecordEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface IdempotencyRecordDao extends BaseMapper<IdempotencyRecordEntity> {
    IdempotencyRecordEntity lock(@Param("id") Long id);

    int claim(IdempotencyRecordEntity row);

    IdempotencyRecordEntity find(@Param("scope") String scope, @Param("key") String key);
}
