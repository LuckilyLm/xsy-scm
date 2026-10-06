package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;

@Mapper
public interface SalesOrderDao extends BaseMapper<SalesOrderEntity> {
    Long customerReferences(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderEntity lock(@Param("id") Long id);

    /**
     * 列表读；{@code scope} 必须由 Service 用 {@code ScmDataScopeService#resolve()} 传入，传 {@code null} 在 SQL
     * 侧渲染成恒假谓词（失败关闭），不会被读成「不加限制」。 {@code lock} / {@code selectById} 等写路径入口刻意不带范围。
     */
    List<SalesOrderEntity> query(Page<?> page, @Param("query") SalesOrderQueryForm query,
            @Param("scope") ScmValueScope scope);

    Long nextOrder();

    Long nextReturn();

    Long nextRefund();
}
