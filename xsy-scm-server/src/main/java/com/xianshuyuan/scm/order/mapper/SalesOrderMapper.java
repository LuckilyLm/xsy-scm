package com.xianshuyuan.scm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.order.dto.SalesOrderPageQuery;
import com.xianshuyuan.scm.order.entity.SalesOrderEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrderEntity> {
    SalesOrderEntity selectActiveById(@Param("id") long id);

    SalesOrderEntity selectActiveByIdForUpdate(@Param("id") long id);

    IPage<SalesOrderEntity> selectOrderPage(Page<SalesOrderEntity> page, @Param("query") SalesOrderPageQuery query);
}
