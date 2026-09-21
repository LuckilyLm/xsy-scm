package net.lab1024.sa.admin.module.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface SalesOrderDao extends BaseMapper<SalesOrderEntity> {
    Long customerReferences(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderEntity lock(@Param("id") Long id);

    List<SalesOrderEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query);

    Long nextOrder();

    Long nextReturn();

    Long nextRefund();
}
