package com.xsy.scm.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.form.SupplierQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SupplierDao extends BaseMapper<SupplierEntity> {

    /**
     * 分页查询；排序由 Service 的白名单校验后通过 {@link Page} 的 orders 传入。
     */
    List<SupplierEntity> queryPage(Page<?> page, @Param("query") SupplierQueryForm query);

    /**
     * 加行锁读取活动供应商。
     *
     * <p>
     * 这是 {@code supplier_sku} 写入路径的第一把锁：先锁 supplier 行，再按 id 锁关联行， 使供应商状态检查与关联写入串行，并保持稳定锁序。
     */
    SupplierEntity selectActiveByIdForUpdate(@Param("supplierId") Long supplierId);

    /**
     * 原子软删：{@code id + version} 双谓词，返回 0 即冲突。
     */
    int softDelete(@Param("supplierId") Long supplierId, @Param("version") Integer version,
            @Param("operator") String operator);
}
