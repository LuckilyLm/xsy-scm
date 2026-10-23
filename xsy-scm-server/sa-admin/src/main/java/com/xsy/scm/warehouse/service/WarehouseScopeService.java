package com.xsy.scm.warehouse.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.dao.EmployeeWarehouseScopeDao;
import com.xsy.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import com.xsy.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 维护员工可管理的仓库范围，写入前校验员工和仓库存在。没有授权记录不等于默认有权查看。
 */
@Service
@RequiredArgsConstructor
public class WarehouseScopeService {

    private final EmployeeWarehouseScopeDao employeeWarehouseScopeDao;

    private final EmployeeDao employeeDao;

    /** 某仓库下被授权的员工。 */
    public List<WarehouseScopeEmployeeVO> listEmployees(Long warehouseId) {
        if (warehouseId == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return employeeWarehouseScopeDao.listEmployeesByWarehouse(warehouseId);
    }

    /** 某员工被授权的仓库。 */
    public List<WarehouseScopeWarehouseVO> listWarehouses(Long employeeId) {
        if (employeeId == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return employeeWarehouseScopeDao.listWarehousesByEmployee(employeeId);
    }

    /**
     * 用 {@code warehouseIds} 整体替换该员工的授权；空清单即全部回收。
     *
     * <p>
     * 回收与写入必须同事务：只成功一半会让员工看不到任何仓，调用方却以为成功了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(WarehouseScopeUpdateForm form) {
        requireEmployee(form.getEmployeeId());
        Set<Long> target = distinctIds(form.getWarehouseIds());
        requireWarehousesExist(target);
        employeeWarehouseScopeDao.deactivateByEmployee(form.getEmployeeId());
        if (!target.isEmpty()) {
            employeeWarehouseScopeDao.insertBatch(form.getEmployeeId(), target);
        }
    }

    private void requireEmployee(Long employeeId) {
        EmployeeEntity employee = employeeId == null ? null : employeeDao.selectById(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDeletedFlag())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    /** 批量存在性校验：一次查回已有的 id 再比集合，避免逐条查询。 */
    private void requireWarehousesExist(Set<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return;
        }
        List<Long> found = employeeWarehouseScopeDao.listExistingWarehouseIds(warehouseIds);
        Set<Long> existing = found == null ? Set.of() : new LinkedHashSet<>(found);
        if (!existing.containsAll(warehouseIds)) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND);
        }
    }

    /** 去重并剔除 null，否则会写入 {@code warehouse_id IS NULL} 的授权行，既查不到也删不掉。 */
    private static Set<Long> distinctIds(Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (ids == null) {
            return distinct;
        }
        ids.stream().filter(java.util.Objects::nonNull).forEach(distinct::add);
        return distinct;
    }
}
