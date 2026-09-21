package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryRouteForm {
    @Min(0)
    private Integer version;
    @NotBlank
    @Size(max = 100)
    private String routeName;
    @NotNull
    private LocalDate deliveryDate;
    @NotNull
    @Positive
    private Long warehouseId;
    @Positive
    private Long driverId;
    @Positive
    private Long vehicleId;
    private OffsetDateTime plannedDepartureTime;
    @Size(max = 500)
    private String remark;
}
