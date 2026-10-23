package com.xsy.scm.common.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 库存及相关单据写入前必须校验仓库权限，以已保存单据的仓库为准；权限不足时不泄露仓库是否存在。
 */
@Component
@RequiredArgsConstructor
public class ScmWarehouseScopeGuard {

    private final ScmDataScopeService dataScopeService;

    /**
     * 单个仓库必须落在授权范围内。
     *
     * <p>
     * {@code null} 只有全部范围可通过；各表单的 {@code warehouseId} 本就是 {@code @NotNull}。
     */
    public void require(Long warehouseId) {
        if (!dataScopeService.resolve().getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 涉及的每个仓库都要授权：调拨建单/改单的两端，以及改单时「行上的旧仓 + 表单的新仓」。
     *
     * <p>
     * 只授权一端时不能把货写进另一端，否则等于往自己读不到的仓里塞一张待发出的单。
     */
    public void requireAll(Long... warehouseIds) {
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        for (Long warehouseId : warehouseIds) {
            if (!scope.allows(warehouseId)) {
                throw new ScmDataScopeException();
            }
        }
    }

    /**
     * 任一端授权即可，和调拨查询同一个 OR 规则，只用于草稿取消 / 删除这种不改库存的动作。调拨三个动作判断不同：查询看任一端，发出只看 {@code from}，收货只看 {@code to}。
     */
    public void requireAny(Long... warehouseIds) {
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        for (Long warehouseId : warehouseIds) {
            if (scope.allows(warehouseId)) {
                return;
            }
        }
        throw new ScmDataScopeException();
    }
}
