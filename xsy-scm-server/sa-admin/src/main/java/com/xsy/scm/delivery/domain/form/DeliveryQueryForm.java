package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.delivery.constant.ScmDeliveryRouteStatusEnum;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

import net.lab1024.sa.base.common.domain.PageParam;

@Data
public class DeliveryQueryForm extends PageParam {
    @Size(max = 100, message = "线路关键词长度不能超过100")
    private String keyword;
    @Size(max = 100, message = "客户关键词长度不能超过100")
    private String customerKeyword;
    @ScmEnumValue(enumClass = ScmDeliveryRouteStatusEnum.class, message = "线路状态无效")
    private String status;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate deliveryDate;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime deliveryTimeFrom;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime deliveryTimeTo;
    @Positive(message = "仓库编号必须为正数")
    private Long warehouseId;
    @Positive(message = "司机编号必须为正数")
    private Long driverId;
    @Positive(message = "车辆编号必须为正数")
    private Long vehicleId;
    @Positive(message = "客户编号必须为正数")
    private Long customerId;
    @Positive(message = "省份编码必须为正数")
    private Integer provinceCode;
    @Positive(message = "城市编码必须为正数")
    private Integer cityCode;
    @Positive(message = "区县编码必须为正数")
    private Integer districtCode;
    private Boolean locatedOnly;
    @DecimalMin(value = "0", message = "最小金额不能小于0")
    private BigDecimal minAmount;
    @DecimalMin(value = "0", message = "最大金额不能小于0")
    private BigDecimal maxAmount;
    @Min(value = 0, message = "最小商品件数不能小于0")
    private Integer minItemCount;
    @Min(value = 0, message = "最大商品件数不能小于0")
    private Integer maxItemCount;

    public DeliveryQueryForm() {
        setPageNum(1L);
        setPageSize(20L);
    }
}
