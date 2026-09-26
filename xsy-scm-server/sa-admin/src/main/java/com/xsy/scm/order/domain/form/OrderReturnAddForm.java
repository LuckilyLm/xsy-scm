package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class OrderReturnAddForm {
    @NotNull
    private Long orderId;
    @NotBlank
    @Size(max = 500)
    private String reason;
    @Valid
    @NotEmpty
    @Size(max = 500)
    private List<OrderReturnItemForm> items;
}
