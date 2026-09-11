package com.xianshuyuan.scm.marketing.vo;

import java.math.BigDecimal;

/**
 * "再来一单"条目视图。数量与单价均为原订单快照，客户端需以当前价格重新确认。
 */
public record ReorderItemResponse(Long skuId, String productName, String skuCode, String specName,
                                  String saleUnit, BigDecimal quantity, BigDecimal unitPrice) {
}
