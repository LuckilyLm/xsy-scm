package com.xianshuyuan.scm.mall.vo;

import java.util.List;

/**
 * 结算预览。价格指纹由服务端生成，提交时必须带回；价格变化以指纹不一致的方式暴露给客户。
 */
public record MallCheckoutPreviewResponse(List<MallCheckoutItemResponse> items, String totalQuantity,
                                          String totalAmount, String priceFingerprint, MallAddressResponse address,
                                          int unavailableCount) {
}
