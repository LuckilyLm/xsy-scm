package net.lab1024.sa.admin.module.scm.pricing.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

@Data
public class AgreementPriceDeleteForm {
    @NotNull
    private Long agreementPriceId;
    @NotNull
    @Min(0)
    private Integer version;
}
