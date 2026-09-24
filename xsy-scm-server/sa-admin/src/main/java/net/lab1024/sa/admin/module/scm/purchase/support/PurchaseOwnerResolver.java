package net.lab1024.sa.admin.module.scm.purchase.support;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.base.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 采购归属（{@code purchaser_id}）的服务端裁决（P0 基线收口裁决第 7 条）。
 *
 * <p>在 P0-F 之前 {@code purchaser_id} 取自表单，是一个可以任意填写的值，因此不能被当作
 * 数据范围依据。本类把口径收口成一条：<b>普通新建时归属一律是当前员工，表单值一律不采信</b>；
 * 只有持 {@code scm:purchase:assign} 的人才可以把归属写成别人（留空即「未分配」）。
 * 已存在的单据只能经 {@code /scm/purchase/reassign} 改派，见 {@link #canAssign()}。
 *
 * <p>{@code canAssign()} 与列表范围是两件事：分配权只回答「能不能指定别人」，
 * 「能不能看到全部」仍由 {@code scm:purchase:scope:all:query} 决定，两者不互相隐含。
 */
@Component
@RequiredArgsConstructor
public class PurchaseOwnerResolver {

    private final ScmDataScopeService scmDataScopeService;

    /**
     * 新建（采购单 / 采购需求）时的归属。
     *
     * @param requestedPurchaserId 表单值，只在调用方有分配权时被采纳
     * @return 落库的归属员工 id；{@code null} 只可能来自「有分配权且刻意留空」
     * @throws BusinessException 无分配权又解析不出当前员工 —— 归属不成立时宁可拒绝创建，
     *                           也不能退化成表单值或一条无人负责的记录
     */
    public Long resolveForCreate(Long requestedPurchaserId) {
        if (canAssign()) {
            return requestedPurchaserId;
        }
        ScmDataScopeContext context = scmDataScopeService.resolve();
        if (context.getEmployeeId() == null) {
            throw new ScmDataScopeException();
        }
        return context.getEmployeeId();
    }

    /**
     * 已存在单据的写侧归属守卫：单据归属不在调用者的采购范围内即拒绝（对外 30005，与读侧同一信封）。
     *
     * <p>读侧的范围只回答「看得见哪些行」，本方法回答「动得了哪些行」：采购功能权限在整个采购团队
     * 内共享，只有读侧范围时普通采购员只要握着按钮权限就能提交、取消甚至确认别人名下的单据。
     * 拒绝而不回答「不存在」，否则探测主键与探测权限可以分辨出来。
     *
     * <p>{@code purchaserId} 为 {@code null}（未分配）时只有全部范围可通过：未分配单据是分配权
     * 持有者认领的对象，普通采购员既看不到也不该改。
     */
    public void requireVisible(Long purchaserId) {
        if (!scmDataScopeService.resolve().getPurchaserScope().allows(purchaserId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 是否有权把采购归属指定 / 改派给别人。失败关闭：拿不到 Sa-Token 上下文即无权限。
     *
     * <p>本模块的编辑接口（{@code /scm/purchase/update}）<b>永远</b>不动归属，即使调用方有分配权：
     * 改派只有 {@code /scm/purchase/reassign} 一条路。两条路都能移动归属时，
     * 「一次整单编辑顺带换人」就会藏在一次普通保存里，审计与授权都失去着力点。
     */
    public boolean canAssign() {
        return ScmDataScopeService.hasPermission(ScmDataScopeService.PURCHASE_ASSIGN_PERM);
    }
}
