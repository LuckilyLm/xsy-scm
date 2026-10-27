package com.xsy.scm.print.domain.vo;

import lombok.Data;

@Data
public class ScmPrintBatchItemVO {
    private Long orderId;
    private String orderNo;
    private String customerName;
    private boolean success;
    private Long recordId;
    private String message;
}
