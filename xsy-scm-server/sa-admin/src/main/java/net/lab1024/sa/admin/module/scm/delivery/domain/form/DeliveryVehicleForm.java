package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryVehicleForm {
    @Positive
    private Long id;
    @Min(0)
    private Integer version;
    @NotBlank
    @Size(max = 32)
    private String vehicleNo;
    @Size(max = 64)
    private String vehicleType;
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal loadWeight;
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal loadVolume;
    @NotNull
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status = "ENABLED";
    @Size(max = 500)
    private String remark;
}
