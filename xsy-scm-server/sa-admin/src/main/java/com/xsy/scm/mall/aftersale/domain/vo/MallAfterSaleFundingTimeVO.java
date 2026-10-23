package com.xsy.scm.mall.aftersale.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallAfterSaleFundingTimeVO {
    private Long refundId;
    private Long allocationId;
    private OffsetDateTime succeededAt;
}
