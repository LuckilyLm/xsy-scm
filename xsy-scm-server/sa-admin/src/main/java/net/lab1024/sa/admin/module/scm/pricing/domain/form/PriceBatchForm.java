package net.lab1024.sa.admin.module.scm.pricing.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

@Data
public class PriceBatchForm {
    @NotBlank
    @Size(max = 100)
    private String batchKey;
    @NotEmpty
    @Size(max = 500)
    private List<PriceBatchRowForm> rows;
}
