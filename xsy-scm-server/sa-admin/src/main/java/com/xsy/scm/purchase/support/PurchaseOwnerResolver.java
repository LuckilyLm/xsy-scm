package com.xsy.scm.purchase.support;

import com.xsy.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.permission.PurchasePermission;
import net.lab1024.sa.base.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/** 普通员工新建采购单时，采购员固定为当前员工；只有具备分配权限者可以指定其他采购员。已有采购单的负责人只能通过独立改派接口变更，分配权限不代表全部数据查看权。 */
@Component
@RequiredArgsConstructor
public class PurchaseOwnerResolver {

    private final ScmDataScopeService dataScopeService;

    /**
     * 新建（采购单 / 采购需求）时的归属。
     *
     * @param requestedPurchaserId
     *            表单值，只在调用方有分配权时被采纳
     * @return 落库的归属员工 id；{@code null} 只可能来自「有分配权且刻意留空」
     * @throws BusinessException
     *             无分配权又解析不出当前员工 —— 归属不成立时宁可拒绝创建，也不能退化成表单值或一条无人负责的记录
     */
    public Long resolveForCreate(Long requestedPurchaserId) {
        if (canAssign()) {
            return requestedPurchaserId;
        }
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.getEmployeeId() == null) {
            throw new ScmDataScopeException();
        }
        return context.getEmployeeId();
    }

    /** 采购单写操作除功能权限外，还必须检查采购员数据范围。查询权限不能替代写入前的归属校验，避免凭 ID 改别人的单据。 */
    public void requireVisible(Long purchaserId) {
        if (!dataScopeService.resolve().getPurchaserScope().allows(purchaserId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 是否有权把采购归属指定 / 改派给别人。失败关闭：拿不到 Sa-Token 上下文即无权限。
     *
     * <p>
     * 本模块的编辑接口（{@code /scm/purchase/update}）<b>永远</b>不动归属，即使调用方有分配权：改派只有 {@code /scm/purchase/reassign} 一条路。两条路都能移动归属时，
     * 「一次整单编辑顺带换人」就会藏在一次普通保存里，审计与授权都失去着力点。
     */
    public boolean canAssign() {
        return ScmDataScopeService.hasPermission(PurchasePermission.ASSIGN);
    }
}
