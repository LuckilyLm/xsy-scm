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
public class OrderActualQuantityForm {
    @NotNull
    private Long orderId;
    @NotNull
    private Long itemId;
    @NotNull
    @Min(0)
    private Integer version;
    @NotBlank
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String actualQuantity;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
