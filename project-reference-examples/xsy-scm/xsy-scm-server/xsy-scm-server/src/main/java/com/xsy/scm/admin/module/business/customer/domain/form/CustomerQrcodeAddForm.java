package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 业务员推广二维码 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerQrcodeAddForm {

    @Schema(description = "业务员ID")
    @NotNull(message = "业务员ID不能为空")
    private Long sellerId;

    @Schema(description = "二维码地址（文件服务，不硬编码公网 URL）")
    private String qrcodeUrl;

    @Schema(description = "扫描次数")
    private Integer scanCount;
}
