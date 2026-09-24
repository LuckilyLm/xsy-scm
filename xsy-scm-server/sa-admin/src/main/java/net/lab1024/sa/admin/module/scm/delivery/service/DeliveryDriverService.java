package net.lab1024.sa.admin.module.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryDriverDao;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryDriverEntity;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryDriverVO;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import java.util.*;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class DeliveryDriverService {
    /** V54 的「一个员工最多绑一个活动司机」部分唯一索引；冲突消息按索引名区分，避免把绑定冲突报成编码重复。 */
    private static final String EMPLOYEE_BINDING_INDEX = "uk_delivery_driver_active_employee";

    private final DeliveryDriverDao dao;
    private final EmployeeDao employees;

    public PageResult<DeliveryDriverVO> query(DeliveryQueryForm form) {
        var requested = DeliveryRouteQueryService.page(form);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryDriverEntity>(requested.getCurrent(), requested.getSize(), requested.searchCount());
        var wrapper = new LambdaQueryWrapper<DeliveryDriverEntity>();
        if (form.getKeyword() != null && !form.getKeyword().isBlank())
            wrapper.like(DeliveryDriverEntity::getDriverCode, form.getKeyword());
        if (form.getStatus() != null && !form.getStatus().isBlank())
            wrapper.eq(DeliveryDriverEntity::getStatus, form.getStatus());
        wrapper.orderByAsc(DeliveryDriverEntity::getDriverCode, DeliveryDriverEntity::getId);
        var rows = dao.selectList(page, wrapper);
        var names = employeeNames(rows.stream().map(DeliveryDriverEntity::getEmployeeId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return SmartPageUtil.convert2PageResult(page, rows.stream().map(row -> {
            var vo = new DeliveryDriverVO();
            BeanUtils.copyProperties(row, vo);
            vo.setEmployeeName(names.get(row.getEmployeeId()));
            return vo;
        }).toList());
    }

    public List<DeliveryDriverEntity> options() {
        return dao.selectList(new LambdaQueryWrapper<DeliveryDriverEntity>().eq(DeliveryDriverEntity::getStatus, "ENABLED").orderByAsc(DeliveryDriverEntity::getDriverCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(DeliveryDriverForm form) {
        var row = form.getId() == null ? new DeliveryDriverEntity() : dao.selectById(form.getId());
        if (row == null) throw new ScmBusinessException(NOT_FOUND);
        if (form.getId() != null && !Objects.equals(row.getVersion(), form.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        requireBindableEmployee(form.getEmployeeId(), form.getStatus());
        BeanUtils.copyProperties(form, row, "id", "version");
        row.setDriverCode(form.getDriverCode().trim().toUpperCase(Locale.ROOT));
        DeliveryRouteService.stamp(row, form.getId() == null);
        try {
            if (form.getId() == null) dao.insert(row);
            else if (dao.updateById(row) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(isBindingConflict(e) ? DRIVER_EMPLOYEE_BOUND : DUPLICATE);
        }
        return row.getId();
    }

    /**
     * 启用即要求绑定：未绑定的司机档案无法把登录人映射回 {@code delivery_route.driver_id}，
     * 数据范围收不出来，因此「正式司机」必须有员工归属（历史行可以留空，但要重新启用就得补）。
     */
    private void requireBindableEmployee(Long employeeId, String status) {
        if (employeeId == null) {
            if ("ENABLED".equals(status)) throw new ScmBusinessException(DRIVER_EMPLOYEE_REQUIRED);
            return;
        }
        EmployeeEntity employee = employees.selectById(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDeletedFlag()))
            throw new ScmBusinessException(DRIVER_EMPLOYEE_INVALID);
    }

    private static boolean isBindingConflict(DuplicateKeyException e) {
        return String.valueOf(e.getMostSpecificCause().getMessage()).contains(EMPLOYEE_BINDING_INDEX);
    }

    /** 绑定员工姓名；已删除的员工不显示名字（列表留空即提示这条绑定需要重新处理）。 */
    private Map<Long, String> employeeNames(Set<Long> employeeIds) {
        if (employeeIds.isEmpty()) return Map.of();
        var found = employees.selectBatchIds(employeeIds);
        if (found == null) return Map.of();
        return found.stream()
                .filter(e -> e != null && e.getEmployeeId() != null && !Boolean.TRUE.equals(e.getDeletedFlag()))
                .collect(Collectors.toMap(EmployeeEntity::getEmployeeId, EmployeeEntity::getActualName, (a, b) -> a));
    }
}
