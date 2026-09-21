package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryStopForm extends net.lab1024.sa.admin.module.scm.common.domain.ScmLocationForm {
    @NotNull
    @Min(0)
    private Integer version;
    private OffsetDateTime plannedArrivalTime;
    @Size(max = 500)
    private String remark;
}
