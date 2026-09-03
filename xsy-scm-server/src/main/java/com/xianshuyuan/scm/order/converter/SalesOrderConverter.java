package com.xianshuyuan.scm.order.converter;
import com.xianshuyuan.scm.order.entity.*;
import com.xianshuyuan.scm.order.vo.*;
import java.math.BigDecimal;
import java.util.List;
public final class SalesOrderConverter {
 private SalesOrderConverter(){}
 public static SalesOrderResponse toResponse(SalesOrderEntity o,List<SalesOrderItemEntity> rows){return new SalesOrderResponse(o.getId(),o.getVersion(),o.getOrderNo(),o.getCustomerId(),o.getCustomerNameSnapshot(),o.getStatus(),o.getOrderSource(),o.getSupplementReason(),o.getOriginalOrderId(),rows.stream().map(SalesOrderConverter::toItem).toList(),decimal(o.getStatus()==OrderStatus.CONFIRMED?o.getSettlementTotalAmount():o.getOrderedTotalAmount()),o.getCreatedAt(),o.getUpdatedAt());}
 public static SalesOrderItemResponse toItem(SalesOrderItemEntity i){boolean locked=i.getLockedUnitPrice()!=null;return new SalesOrderItemResponse(i.getId(),i.getVersion(),i.getSkuId(),i.getSpuId(),i.getSkuCodeSnapshot(),i.getProductNameSnapshot(),i.getSpecNameSnapshot(),i.getSpecValuesSnapshot(),i.getSaleUnitSnapshot(),i.getProductTypeSnapshot(),decimal(i.getOrderedQuantity()),decimal(i.getActualQuantity()),decimal(i.getDraftUnitPrice()),decimal(i.getLockedUnitPrice()),locked?i.getLockedPriceSource():i.getDraftPriceSource(),locked?i.getLockedPriceSourceId():i.getDraftPriceSourceId(),decimal(i.getSettlementLineAmount()!=null?i.getSettlementLineAmount():i.getOrderedLineAmount()),i.getManualPriceReason());}
 public static String decimal(BigDecimal v){return v==null?null:v.setScale(4).toPlainString();}
}
