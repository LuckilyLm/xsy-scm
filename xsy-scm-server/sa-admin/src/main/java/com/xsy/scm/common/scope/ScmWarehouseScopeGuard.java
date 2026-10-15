package com.xsy.scm.common.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 库存及相关单据写入前必须校验仓库权限，以已保存单据的仓库为准；权限不足时不泄露仓库是否存在。 */
@Component
@RequiredArgsConstructor
public class ScmWarehouseScopeGuard {

    private final ScmDataScopeService dataScopeService;

    /**
     * 单个仓库必须落在授权范围内。
     *
     * <p>
     * {@code null} 只有全部范围可通过：没有仓库就无从判定归属，失败关闭比「放过」安全，而 {@code warehouseId} 在各表单上本就是 {@code @NotNull}。
     */
    public void require(Long warehouseId) {
        if (!dataScopeService.resolve().getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 涉及的<b>每个</b>仓库都要授权：调拨建单/改单的两端、以及改单时「行上的旧仓 + 表单的新仓」。
     *
     * <p>
     * 只授权一端的调用者不能把货写进另一端：能建一张 {@code B→A} 的调拨单，等于往自己读不到的 A 仓里塞一张待发出的单。
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
     * 任一端授权即可：与调拨<b>查询</b>同一条 OR 判据，只用于不改动任何库存余额的单据动作 （草稿的取消 / 删除）。调拨三个动作的判据互不相同，实现处有说明： <b>查询</b>任一端、<b>发出</b>只看
     * {@code from}、<b>收货</b>只看 {@code to}。
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
