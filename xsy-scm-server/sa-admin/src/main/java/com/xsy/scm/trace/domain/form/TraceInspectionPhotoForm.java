package com.xsy.scm.trace.domain.form;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TraceInspectionPhotoForm {
    @Size(max = 200, message = "报告名称不能超过200字")
    private String reportName;
    @NotEmpty(message = "请上传报告照片")
    @Size(max = 20, message = "一次最多上传20张照片")
    private List<@NotBlank(message = "照片不能为空") String> fileKeys;
}
