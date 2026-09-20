package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 规格转换事实（库存域自己的入参记录）。
 *
 * <p>同一条明细行会产生**两条**事实：一条转出（源 SKU）、一条转入（目标 SKU）。
 * 方向不放在这个记录里，而是由调用方选择
 * {@code InventoryCommandService#postConvertOut} 还是 {@code #postConvertIn} ——
 * 与调拨的 {@code postTransferOut} / {@code postTransferIn} 同一取向：
 * **方向由方法名表达，不靠一个 boolean 参数**，这样「入方向才允许建余额行」这条差异
 * 不会被一个传错的标志绕过。
 *
 * <p><b>单位来自单据声明</b>（不像出库 / 调拨那样由余额决定）：折算关系本身含单位，
 * 单据必须把它写清楚。命令服务会用它与余额记账单位比对，不一致直接失败。
 *
 * @param warehouseId      仓库（源与目标 SKU 必须在同一仓库）
 * @param conversionId     转换单 id（头级溯源，不参与防重）
 * @param conversionItemId 转换单行 id（防重锚点之一；另一维是来源类型）
 * @param skuId            本次动作作用的 SKU（转出 = 源 SKU，转入 = 目标 SKU）
 * @param quantity         数量，必须为正
 * @param unit             单据声明的单位（转出 = sourceUnit，转入 = targetUnit）
 * @param unitCost         本腿的单位成本基准，按**本腿自己的单位**计（转入腿已按折算率换算过）
 * @param occurredAt       发生时刻 —— 取**审核时刻**，不是创建时刻
 * @param operator         操作者 —— 取**审核人**，不是创建人
 */
public record InventoryConversionFact(
        Long warehouseId,
        Long conversionId,
        Long conversionItemId,
        Long skuId,
        BigDecimal quantity,
        String unit,
        BigDecimal unitCost,
        OffsetDateTime occurredAt,
        String operator) {
}
