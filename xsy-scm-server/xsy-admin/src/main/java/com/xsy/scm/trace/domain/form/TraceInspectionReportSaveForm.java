package com.xsy.scm.trace.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.trace.constant.ScmTraceInspectionConclusionEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

@Data
public class TraceInspectionReportSaveForm {
    @Positive(message = "报告标识必须大于零")
    private Long id;

    @PositiveOrZero(message = "报告版本不能小于零")
    private Integer version;

    @NotNull(message = "请选择收货批次")
    @Positive(message = "批次标识必须大于零")
    private Long lotId;

    @NotBlank(message = "请填写报告编号")
    @Size(max = 100, message = "报告编号最多100个字符")
    private String reportNo;

    @NotBlank(message = "请填写报告名称")
    @Size(max = 200, message = "报告名称最多200个字符")
    private String reportName;

    @NotNull(message = "请选择检测日期")
    private LocalDate inspectionDate;

    @NotBlank(message = "请填写检测机构")
    @Size(max = 200, message = "检测机构最多200个字符")
    private String inspectionOrg;

    @NotBlank(message = "请选择检测结论")
    @ScmEnumValue(enumClass = ScmTraceInspectionConclusionEnum.class, message = "检测结论无效")
    private String conclusion;

    private OffsetDateTime expiresAt;

    @NotNull(message = "请提供附件列表")
    @Size(max = 20, message = "一份报告最多关联20个附件")
    private List<@NotBlank(message = "附件标识不能为空") String> fileKeys;
}
