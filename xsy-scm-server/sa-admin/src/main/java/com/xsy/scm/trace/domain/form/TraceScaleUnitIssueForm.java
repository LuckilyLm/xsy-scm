package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 秤端为一筐称重结果发码。
 *
 * <p>
 * 以称重记录为入口而不是分拣行：一筐一码，码上的重量必须能对回那条设备读数，否则「这个码多重」就没有来源。
 */
@Data
public class TraceScaleUnitIssueForm {

    /** 设备上报 {@code /measurements} 时返回的 measurementId。 */
    @NotNull(message = "称重记录不能为空")
    private Long weighingEventId;

    @NotNull(message = "分拣任务行不能为空")
    private Long taskItemId;

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotBlank(message = "包装类型不能为空")
    @Pattern(regexp = "BAG|BOX|CRATE", message = "包装类型只允许袋、箱、筐")
    private String packageType;
}
