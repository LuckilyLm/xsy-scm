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
public class SalesOrderAddForm {
    @NotNull private Long customerId;
    @NotBlank @Pattern(regexp="ADMIN|MALL|SUPPLEMENT") private String orderSource;
    private Long originalOrderId;
    @Size(max=500) private String supplementReason;
    @Size(max=500) private String remark;
    private OffsetDateTime expectDeliveryTime;
    @Valid @NotNull private OrderAddressForm address;
    @Valid @NotEmpty @Size(max=500) private List<SalesOrderItemForm> items;
}
