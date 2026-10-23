package com.xsy.scm.sorting.support;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 分拣权限取授权仓库和任务指派人的交集。未指派任务仅队列管理者可见，越权操作统一拒绝，不透露任务是否存在。
 */
@Component
@RequiredArgsConstructor
public class SortingAccess {

    private final ScmDataScopeService dataScopeService;

    /** 每次调用重新解析，调用方在自己的动作里取一次并向下传，避免同一动作查两遍授权行。 */
    public ScmDataScopeContext scope() {
        return dataScopeService.resolve();
    }

    /** 队列管理权限或超级管理员权限可查看未分配与跨指派任务。仓库数据范围仍需独立校验，不能因为管理队列就拥有全部仓库。 */
    public boolean crossAssignee() {
        return ScmDataScopeService.hasPermission(SortingPermission.TASK_ASSIGN)
            || ScmDataScopeService.isAdministrator();
    }

    /** 建单时还没有任务行，判的是「即将写入的那个仓归不归他管」。 */
    public void requireWarehouse(Long warehouseId) {
        requireWarehouse(scope(), warehouseId);
    }

    /** 队列管理动作：指派 / 取消 / 重开。仓库在授权内，且持队列管理权。 */
    public void requireQueueManager(ScmDataScopeContext scope, SortingTaskEntity task) {
        requireWarehouse(scope, task.getWarehouseId());
        if (!crossAssignee()) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 录入和完成只认受指派人本人；未指派任务没人能录入。
     *
     * <p>
     * 超管直接放行：指派人这一维是直接比员工 id、不在范围值对象里， 不放行会出现「仓库看得见、人看不见」的漏洞。
     */
    public void requireAssignee(ScmDataScopeContext scope, SortingTaskEntity task) {
        requireWarehouse(scope, task.getWarehouseId());
        if (ScmDataScopeService.isAdministrator()) {
            return;
        }
        if (task.getAssigneeEmployeeId() == null
            || !Objects.equals(scope.getEmployeeId(), task.getAssigneeEmployeeId())) {
            throw new ScmDataScopeException();
        }
    }

    /** 只读与打印：本人、队列管理者或超管（后两者已含在 {@link #crossAssignee()} 里）。 */
    public void requireVisible(ScmDataScopeContext scope, SortingTaskEntity task) {
        requireWarehouse(scope, task.getWarehouseId());
        if (crossAssignee()) {
            return;
        }
        if (!Objects.equals(scope.getEmployeeId(), task.getAssigneeEmployeeId())) {
            throw new ScmDataScopeException();
        }
    }

    private void requireWarehouse(ScmDataScopeContext scope, Long warehouseId) {
        if (!scope.getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
    }
}
