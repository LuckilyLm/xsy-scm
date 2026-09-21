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
public class SalesOrderItemForm {
    private Long itemId;
    private Integer version;
    @NotNull
    private Long skuId;
    @NotBlank
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String orderedQuantity;
    @NotNull
    private Boolean manualPriceOverride;
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @Size(max = 500)
    private String overrideReason;
    private Integer sortOrder;
}
