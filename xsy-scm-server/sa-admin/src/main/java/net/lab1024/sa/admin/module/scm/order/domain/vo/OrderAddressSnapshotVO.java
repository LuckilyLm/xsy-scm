package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data
public class OrderAddressSnapshotVO {
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal longitude;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal latitude;
    private String geomCrs;

    private Long id;
    private Long orderId;
    private Long customerId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private OffsetDateTime createdAt;
    private String createdBy;
}
