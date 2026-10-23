package com.xsy.scm.warehouse.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.domain.form.WarehouseQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 仓库读写。仓库是主数据，不做逻辑删除，因此没有删除方法。
 */
@Mapper
public interface WarehouseDao extends BaseMapper<WarehouseEntity> {

    /**
     * 分页查询；排序经 Service 校验后通过 {@link Page} 的 orders 传入。 {@code scope} 是当前员工的仓库授权范围，传 {@code null} 按 0 行处理。
     */
    List<WarehouseEntity> queryPage(Page<?> page, @Param("query") WarehouseQueryForm query,
        @Param("scope") ScmValueScope scope);
}
