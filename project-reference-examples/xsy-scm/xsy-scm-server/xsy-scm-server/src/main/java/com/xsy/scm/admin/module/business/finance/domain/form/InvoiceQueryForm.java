package com.xsy.scm.admin.module.business.finance.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发票 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class InvoiceQueryForm extends PageParam {

    @Schema(description = "搜索词（发票号）")
    private String searchWord;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "状态：1 可开票，2 已开票，3 已红冲")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
