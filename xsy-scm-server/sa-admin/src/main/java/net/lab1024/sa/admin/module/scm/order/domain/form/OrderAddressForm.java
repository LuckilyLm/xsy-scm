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
public class OrderAddressForm {
    @NotBlank
    @Size(max = 100)
    private String receiverName;
    @NotBlank
    @Size(max = 32)
    private String receiverPhone;
    @NotBlank
    @Size(max = 500)
    private String address;
}
