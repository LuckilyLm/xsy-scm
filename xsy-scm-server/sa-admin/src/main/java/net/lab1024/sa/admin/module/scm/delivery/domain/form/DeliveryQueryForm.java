package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.math.BigDecimal;

import net.lab1024.sa.base.common.domain.PageParam;

@Data
public class DeliveryQueryForm extends PageParam {
    @Size(max = 100)
    private String keyword;
    @Size(max = 100)
    private String customerKeyword;
    @Pattern(regexp = "DRAFT|PLANNED|DISPATCHED|COMPLETED|CANCELLED|ENABLED|DISABLED")
    private String status;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate deliveryDate;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime deliveryTimeFrom;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime deliveryTimeTo;
    @Positive
    private Long warehouseId;
    @Positive
    private Long driverId;
    @Positive
    private Long vehicleId;
    @Positive
    private Long customerId;
    @Positive
    private Integer provinceCode;
    @Positive
    private Integer cityCode;
    @Positive
    private Integer districtCode;
    private Boolean locatedOnly;
    @DecimalMin("0")
    private BigDecimal minAmount;
    @DecimalMin("0")
    private BigDecimal maxAmount;
    @Min(0)
    private Integer minItemCount;
    @Min(0)
    private Integer maxItemCount;

    public DeliveryQueryForm() {
        setPageNum(1L);
        setPageSize(20L);
    }
}
