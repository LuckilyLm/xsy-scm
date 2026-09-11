package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;

public class CustomerTypePriceBatchRowException extends BusinessException {
    private final int rowNumber;
    private final Long skuId;

    public CustomerTypePriceBatchRowException(int rowNumber, Long skuId, BusinessException cause) {
        super(cause.getErrorCode(), "第 " + rowNumber + " 行：" + cause.getMessage());
        this.rowNumber = rowNumber;
        this.skuId = skuId;
        initCause(cause);
    }

    public int getRowNumber() { return rowNumber; }
    public Long getSkuId() { return skuId; }
}
