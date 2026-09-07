package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.*;
import com.xianshuyuan.scm.order.dto.*;
import com.xianshuyuan.scm.order.entity.OrderSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;

@Component
public class SalesOrderValidator {
    public void validateDraft(SalesOrderSaveRequest request) {
        if (request.source() == OrderSource.SUPPLEMENT) {
            if (blank(request.supplementReason())) fail(OrderErrorCodes.SUPPLEMENT_REASON_REQUIRED);
        } else if (request.originalOrderId() != null || !blank(request.supplementReason())) {
            fail(OrderErrorCodes.INVALID_SUPPLEMENT);
        }
        var skuIds = new HashSet<Long>();
        for (var item : request.items()) {
            if (!skuIds.add(item.skuId())) fail(OrderErrorCodes.DUPLICATE_SKU);
            if (new BigDecimal(item.orderedQuantity()).signum() <= 0) fail(OrderErrorCodes.INVALID_QUANTITY);
            if (item.manualPriceOverride() && (blank(item.unitPrice()) || blank(item.overrideReason())))
                fail(OrderErrorCodes.OVERRIDE_REASON_REQUIRED);
            if (!item.manualPriceOverride() && item.unitPrice() != null) fail(OrderErrorCodes.INVALID_PRICE_OVERRIDE);
            if (item.unitPrice() != null && new BigDecimal(item.unitPrice()).signum() < 0)
                fail(OrderErrorCodes.INVALID_PRICE);
        }
    }

    public void requireReason(String reason, ErrorCode code) {
        if (blank(reason)) fail(code);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void fail(ErrorCode code) {
        throw new BusinessException(code);
    }
}
