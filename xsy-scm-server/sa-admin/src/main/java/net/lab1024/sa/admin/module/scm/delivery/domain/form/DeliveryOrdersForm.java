package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryOrdersForm {
    @NotNull
    @Min(0)
    private Integer version;
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull @Positive Long> orderIds;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
