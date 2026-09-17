package com.xsy.scm.admin.module.business.customer.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务员推广二维码 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerQrcodeVO {

    @Schema(description = "二维码ID")
    private Long qrcodeId;

    @Schema(description = "业务员ID")
    private Long sellerId;

    @Schema(description = "二维码地址")
    private String qrcodeUrl;

    @Schema(description = "扫描次数")
    private Integer scanCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
