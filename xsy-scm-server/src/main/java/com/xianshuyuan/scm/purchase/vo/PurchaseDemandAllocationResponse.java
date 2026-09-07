package com.xianshuyuan.scm.purchase.vo;
import com.fasterxml.jackson.databind.JsonNode; import java.math.BigDecimal;
public record PurchaseDemandAllocationResponse(Long id, Long purchaseDemandId, Long purchaseOrderItemId, Long salesOrderId, Long salesOrderItemId, Long skuId, BigDecimal allocatedQuantity, JsonNode demandSnapshot, Integer version) {}
