package com.xianshuyuan.scm.purchase.vo;

public record PurchaseReceiptConfirmResult(
        Long receiptId, Long confirmationId, String confirmationNo,
        String status, String purchaseOrderStatus, String confirmedQuantity
) {}
