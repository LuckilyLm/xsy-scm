package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 规格转换分别形成转出和转入流水。单位来自转换单据，须与对应余额单位一致；操作人及时间取审核事实，转入成本由转出总成本折算。 */
public record InventoryConversionFact(Long warehouseId, Long conversionId, Long conversionItemId, Long skuId,
        BigDecimal quantity, String unit, BigDecimal unitCost, OffsetDateTime occurredAt, String operator) {
}
