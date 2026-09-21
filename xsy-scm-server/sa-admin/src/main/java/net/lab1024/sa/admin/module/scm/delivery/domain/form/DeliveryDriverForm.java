package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryDriverForm {
    @Positive
    private Long id;
    @Min(0)
    private Integer version;
    @NotBlank
    @Size(max = 64)
    private String driverCode;
    @NotBlank
    @Size(max = 100)
    private String driverName;
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "[0-9+() -]{5,32}")
    private String phone;
    @NotNull
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status = "ENABLED";
    @Size(max = 500)
    private String remark;
}
