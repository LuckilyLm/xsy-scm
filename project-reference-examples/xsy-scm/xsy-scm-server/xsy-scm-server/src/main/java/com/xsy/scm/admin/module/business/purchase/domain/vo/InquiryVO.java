package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 询价单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryVO {

    @Schema(description = "主键ID")
    private Long inquiryId;

    @Schema(description = "询价单号")
    private String inquiryNo;

    @Schema(description = "询价单名称")
    private String inquiryName;

    @Schema(description = "询价有效开始时间")
    private LocalDateTime validStart;

    @Schema(description = "询价有效结束时间")
    private LocalDateTime validEnd;

    @Schema(description = "状态：1 待报价，2 报价中，3 已完成，4 已取消")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
