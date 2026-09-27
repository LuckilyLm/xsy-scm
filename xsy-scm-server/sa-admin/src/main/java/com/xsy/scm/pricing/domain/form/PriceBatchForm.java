package com.xsy.scm.pricing.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class PriceBatchForm {
    @NotBlank(message = "批次键不能为空")
    @Size(max = 100, message = "批次键不能超过100个字符")
    private String batchKey;
    @NotEmpty(message = "批量调价行不能为空")
    @Size(max = 500, message = "批量调价不能超过500行")
    private List<PriceBatchRowForm> rows;
}
