package net.lab1024.sa.admin.module.scm.warehouse.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.dao.EmployeeWarehouseScopeDao;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeEmployeeVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseScopeWarehouseVO;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 员工—仓库授权维护（{@code employee_warehouse_scope}）。
 *
 * <p>这是「能看哪些仓」的唯一写入口：库存族的数据范围全部由 {@code ScmDataScopeService}
 * 读本表得出，没有任何旁路默认值，因此这里写下的行就是运行时的可见范围。
 *
 * <p>不建数据库外键（项目约定），所以员工与仓库的存在性必须在这里判掉：
 * 一条指向已删除员工或不存在仓库的授权行既不会报错，也永远不会命中任何数据，
 * 只会变成没人看得懂的垃圾行。
 */
@Service
@RequiredArgsConstructor
public class WarehouseScopeService {

    private final EmployeeWarehouseScopeDao scopeDao;

    private final EmployeeDao employees;

    /**
     * 某仓库下被授权的员工。
     */
    public List<WarehouseScopeEmployeeVO> listEmployees(Long warehouseId) {
        if (warehouseId == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return scopeDao.listEmployeesByWarehouse(warehouseId);
    }

    /**
     * 某员工被授权的仓库。
     */
    public List<WarehouseScopeWarehouseVO> listWarehouses(Long employeeId) {
        if (employeeId == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return scopeDao.listWarehousesByEmployee(employeeId);
    }

    /**
     * 用 {@code warehouseIds} 整体替换该员工的活动授权；空清单即全部回收。
     *
     * <p>回收与写入必须同事务：只成功一半会把员工留在「看不到任何仓」的状态，
     * 而这是失败关闭的状态，调用方看到成功却没人会立刻发现。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(WarehouseScopeUpdateForm form) {
        requireEmployee(form.getEmployeeId());
        Set<Long> target = distinctIds(form.getWarehouseIds());
        requireWarehousesExist(target);
        scopeDao.deactivateByEmployee(form.getEmployeeId());
        if (!target.isEmpty()) {
            scopeDao.insertBatch(form.getEmployeeId(), target);
        }
    }

    private void requireEmployee(Long employeeId) {
        EmployeeEntity employee = employeeId == null ? null : employees.selectById(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDeletedFlag())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    /**
     * 批量存在性校验：一次查回真实存在的 id 再比集合，
     * 逐个 require 会把一次维护变成 N 次查询，而且报错时说不清是哪几个 id。
     */
    private void requireWarehousesExist(Set<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return;
        }
        List<Long> found = scopeDao.listExistingWarehouseIds(warehouseIds);
        Set<Long> existing = found == null ? Set.of() : new LinkedHashSet<>(found);
        if (!existing.containsAll(warehouseIds)) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND);
        }
    }

    /**
     * 去重并剔除 null：清单里的 {@code null} 不是「未填」而是会写成一行 {@code warehouse_id IS NULL}
     * 的授权，而部分唯一索引不收 NULL 之外的重复值，这类行既查不到数据也删不掉。
     */
    private static Set<Long> distinctIds(Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (ids == null) {
            return distinct;
        }
        ids.stream().filter(java.util.Objects::nonNull).forEach(distinct::add);
        return distinct;
    }
}
