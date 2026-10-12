package com.xsy.scm.order.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

@Data
public class OrderAddressSnapshotVO {
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal longitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private BigDecimal latitude;
    private String geomCrs;

    private Long id;
    private Long orderId;
    private Long customerId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private OffsetDateTime createdAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
}
