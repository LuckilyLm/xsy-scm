package com.xsy.scm.warehouse.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.warehouse.domain.entity.EmployeeWarehouseScopeEntity;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 员工—仓库授权读写。查询侧读数据范围走 {@code ScmDataScopeDao}，这里只服务维护页和整体替换。
 */
@Mapper
public interface EmployeeWarehouseScopeDao extends BaseMapper<EmployeeWarehouseScopeEntity> {

    /** 某仓库下被授权的员工（联 {@code t_employee} 取展示字段）。 */
    List<WarehouseScopeEmployeeVO> listEmployeesByWarehouse(@Param("warehouseId") Long warehouseId);

    /** 通知只发给仍在职且拥有该仓库授权的员工；停用员工的授权仍保留。 */
    List<Long> listEnabledEmployeeIdsByWarehouse(@Param("warehouseId") Long warehouseId);

    /** 某员工被授权的仓库（联 {@code xsy_warehouse} 取展示字段）。 */
    List<WarehouseScopeWarehouseVO> listWarehousesByEmployee(@Param("employeeId") Long employeeId);

    /**
     * 回收某员工的全部授权；条件必须带 {@code deleted_flag = FALSE}，与部分唯一索引 {@code uk_employee_warehouse_scope_active} 保持一致。
     *
     * @return 被回收的行数，0 表示该员工本来就没有授权
     */
    int deactivateByEmployee(@Param("employeeId") Long employeeId);

    /** 批量授权，与 {@link #deactivateByEmployee} 同事务，并发读只会看到替换前或替换后的状态。 */
    int insertBatch(@Param("employeeId") Long employeeId, @Param("warehouseIds") Collection<Long> warehouseIds);

    /** 回读存在的仓库 id，用于批量存在性校验（不建外键，只能在这里判）。 */
    List<Long> listExistingWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);
}
