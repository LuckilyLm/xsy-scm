package net.lab1024.sa.admin.module.scm.warehouse.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.EmployeeWarehouseScopeEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 员工—仓库授权读写。
 *
 * <p>本表是 SCM 数据范围仓库维度的唯一入口；业务查询侧的取数走
 * {@code ScmDataScopeDao}（只读），这里只服务维护页与「整体替换」写入。
 */
@Mapper
public interface EmployeeWarehouseScopeDao extends BaseMapper<EmployeeWarehouseScopeEntity> {

    /**
     * 某仓库下被授权的员工（联 {@code t_employee} 取展示字段）。
     */
    List<WarehouseScopeEmployeeVO> listEmployeesByWarehouse(@Param("warehouseId") Long warehouseId);

    /**
     * 某员工被授权的仓库（联 {@code warehouse} 取展示字段）。
     */
    List<WarehouseScopeWarehouseVO> listWarehousesByEmployee(@Param("employeeId") Long employeeId);

    /**
     * 回收某员工的全部活动授权；活动行与部分唯一索引 {@code uk_employee_warehouse_scope_active}
     * 同一谓词，不能漏掉 {@code deleted_flag = FALSE}。
     *
     * @return 被回收的行数，0 是正常的（该员工本来没有授权）
     */
    int deactivateByEmployee(@Param("employeeId") Long employeeId);

    /**
     * 一次性批量授权：整批一条 INSERT，与 {@link #deactivateByEmployee} 同事务，
     * 使「替换」对任何并发读都只呈现替换前或替换后两种状态。
     */
    int insertBatch(@Param("employeeId") Long employeeId,
                    @Param("warehouseIds") Collection<Long> warehouseIds);

    /**
     * 回读存在的仓库 id，用于批量存在性校验（不建外键，故完整性只能在这里判）。
     */
    List<Long> listExistingWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);
}
