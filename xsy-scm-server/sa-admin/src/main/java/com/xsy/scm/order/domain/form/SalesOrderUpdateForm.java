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
@EqualsAndHashCode(callSuper = true)
public class SalesOrderUpdateForm extends SalesOrderAddForm {
    @NotNull
    private Long orderId;
    @NotNull
    @Min(0)
    private Integer version;
}
