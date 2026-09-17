package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 业务员推广二维码 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerQrcodeQueryForm extends PageParam {

    @Schema(description = "业务员ID")
    private Long sellerId;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
