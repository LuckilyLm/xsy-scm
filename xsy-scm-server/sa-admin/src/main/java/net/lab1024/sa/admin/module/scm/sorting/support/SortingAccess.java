package net.lab1024.sa.admin.module.scm.sorting.support;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 分拣的可见性与写侧守卫：范围永远是「授权仓 ∩ 可见指派人」，两维取交集且失败关闭
 * （裁决第 7 条与补充第 15 条）。
 *
 * <p>跨指派人可见性由 {@code scm:sorting:task:assign} 本身隐含，不另设
 * {@code scm:sorting:scope:all:query}：该权限表达的就是「谁的任务都由你排」，
 * 建单 / 指派 / 取消 / 重开在授权矩阵里同批发放，因此这里以它作为队列管理者的开关。
 * 分拣员恒等于「派给自己的 ∩ 授权仓」；未指派任务（{@code assignee} 为空）天然落在等值判断之外，
 * 因而只对队列管理者可见。
 *
 * <p>拒绝方式与 P0 其余范围守卫一致：统一 {@link ScmDataScopeException}（对外 30005），
 * 不回答「不存在」，否则探测主键与探测权限可以分辨出来。
 */
@Component
@RequiredArgsConstructor
public class SortingAccess {

    private final ScmDataScopeService dataScopeService;

    /**
     * 每次调用重新解析，调用方在自己的动作里取一次并向下传，避免同一动作查两遍授权行。
     */
    public ScmDataScopeContext scope() {
        return dataScopeService.resolve();
    }

    /**
     * 跨指派人可见 = 持队列管理权，或超管按 break-glass 放行。
     *
     * <p>读侧拼的是「未指派行落在等值判断之外」这条 SQL 谓词，因此超管位必须在这里一并生效：
     * 只在写侧放行会让同一个账号「能改单却看不到未指派队列」。仓库维度不受此影响 ——
     * 它已经通过 {@code resolve()} 的 {@code all()} 天然放行。
     */
    public boolean crossAssignee() {
        return ScmDataScopeService.hasPermission(SortingConstant.ASSIGN_PERM) || ScmDataScopeService.isAdministrator();
    }

    /**
     * 建单时还没有任务行，判的是「即将落库的那个仓归不归他管」。
     */
    public void requireWarehouse(Long warehouseId) {
        requireWarehouse(scope(), warehouseId);
    }

    /**
     * 队列管理动作：指派 / 取消 / 重开。仓库在授权内，且持队列管理权。
     */
    public void requireQueueManager(ScmDataScopeContext scope, SortingTaskEntity task) {
        requireWarehouse(scope, task.getWarehouseId());
        if (!crossAssignee()) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 干活动作：录入与完成只认受指派人本人；未指派任务没有人可录入。
     *
     * <p>超管按 break-glass 放行：「受指派人」这一维是直接比员工 id、不在范围值对象里，
     * 不同等放行就会出现「仓库看得见、人看不见」的半开口径。
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

    /**
     * 只读与打印：本人、队列管理者或超管（后两者已含在 {@link #crossAssignee()} 里）。
     */
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
