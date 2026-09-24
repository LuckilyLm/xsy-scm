package net.lab1024.sa.admin.module.scm.common.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 仓库维度的**写侧**守卫：库存族的每条命令在落库前都要回答一次「这个仓归不归他管」。
 *
 * <p>读侧已经把范围下传给 Mapper，但只收读侧会留下一个洞：只有 B 仓授权的人只要握着
 * {@code scm:inventory:outbound:confirm} 就能确认 A 仓的出库单。裁决要求的是
 * 「库存余额 / 流水 / 盘点 / 收货 / 出库的 {@code warehouse_id} 必须落在授权仓内」，
 * 即读写同一条边界（{@code docs/decisions.md}「P0 基线收口裁决」第 8 条）。
 *
 * <p><b>调用位置决定它是否成立</b>：必须在读到<b>持久化行</b>之后、任何写入之前调用。
 * 已存在的单据取行上的仓库，不取表单值——表单能填成任何人想要的 id，而库存事实按行上的仓库记账。
 * 新建单据没有库存行，此时表单值就是即将落库的值，判它等价。
 *
 * <p>拒绝方式与读侧一致：抛 {@link ScmDataScopeException}（对外 30005，与功能权限不足
 * 同一个信封），不回答「不存在」，否则探测主键与探测权限可以分辨出来。
 */
@Component
@RequiredArgsConstructor
public class ScmWarehouseScopeGuard {

    private final ScmDataScopeService dataScopeService;

    /**
     * 单个仓库必须落在授权范围内。
     *
     * <p>{@code null} 只有全部范围可通过：没有仓库就无从判定归属，失败关闭比「放过」安全，
     * 而 {@code warehouseId} 在各表单上本就是 {@code @NotNull}。
     */
    public void require(Long warehouseId) {
        if (!dataScopeService.resolve().getWarehouseScope().allows(warehouseId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 涉及的<b>每个</b>仓库都要授权：调拨建单/改单的两端、以及改单时「行上的旧仓 + 表单的新仓」。
     *
     * <p>只授权一端的调用者不能把货写进另一端：能建一张 {@code B→A} 的调拨单，
     * 等于往自己读不到的 A 仓里塞一张待发出的单。
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
     * 任一端授权即可：与调拨<b>查询</b>同一条 OR 判据，只用于不改动任何库存余额的单据动作
     * （草稿的取消 / 删除）。调拨三个动作的判据互不相同，实现处有说明：
     * <b>查询</b>任一端、<b>发出</b>只看 {@code from}、<b>收货</b>只看 {@code to}。
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
