package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface SalesOrderItemDao extends BaseMapper<SalesOrderItemEntity> {
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderItemEntity lock(@Param("id") Long id);

    List<SalesOrderItemEntity> list(@Param("id") Long id);
}
