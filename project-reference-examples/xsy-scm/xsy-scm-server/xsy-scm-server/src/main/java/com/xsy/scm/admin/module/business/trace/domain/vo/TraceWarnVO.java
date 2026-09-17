package com.xsy.scm.admin.module.business.trace.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 厂商资质预警 返回对象
 *
 * @author xsy-scm
 */
@Data
public class TraceWarnVO {

    @Schema(description = "厂商ID")
    private Long manufacturerId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "厂商名称")
    private String manufacturerName;

    @Schema(description = "资质证件")
    private String qualificationFile;

    @Schema(description = "资质到期日期")
    private LocalDate qualificationExpireDate;

    @Schema(description = "距离到期天数（负数表示已过期）")
    private Long daysToExpire;
}
