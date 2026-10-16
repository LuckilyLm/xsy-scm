package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 调拨发出时使用源仓和源仓单位；收货时使用目标仓和发出时保存的单位。目标仓已有余额时必须校验单位一致；操作人和发生时间分别取发出或收货事实。 */
public record InventoryTransferFact(Long warehouseId, Long skuId, Long transferId, Long transferItemId,
        BigDecimal quantity, String unitSnapshot, OffsetDateTime occurredAt, String operator) {
}
