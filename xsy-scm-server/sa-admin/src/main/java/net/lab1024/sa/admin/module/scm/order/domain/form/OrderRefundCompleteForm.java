package net.lab1024.sa.admin.module.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class OrderRefundCompleteForm {
    @NotNull
    private Long refundId;
    @NotNull
    @Min(0)
    private Integer version;
    @Size(max = 128)
    private String externalReference;
}
