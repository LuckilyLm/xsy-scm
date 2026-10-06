package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 规格转换事实。
 *
 * <p>
 * 同一转换行分别产生转出与转入两条事实；<b>方向由调用的库存命令决定</b> （{@code postConvertOut} / {@code postConvertIn}），不在记录里放 boolean —— 方法名表达方向，
 * 「入方向才允许建余额行」这条差异才不会被一个传错的标志绕过。
 *
 * <p>
 * <b>单位来自单据声明</b>（不像出库 / 调拨那样由余额决定）：折算关系本身含单位，单据必须写清楚，命令服务会用它与余额记账单位比对，不一致直接失败。
 *
 * <p>
 * {@code occurredAt} 取<b>审核时刻</b>、{@code operator} 取<b>审核人</b>，不是创建时刻 / 创建人； {@code unitCost}
 * 按<b>本腿自己的单位</b>计（转入腿已按折算率换算过）。
 */
public record InventoryConversionFact(Long warehouseId, Long conversionId, Long conversionItemId, Long skuId,
        BigDecimal quantity, String unit, BigDecimal unitCost, OffsetDateTime occurredAt, String operator) {
}
