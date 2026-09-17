package com.xsy.scm.admin.module.business.trace.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 溯源批次 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class TraceBatchQueryForm extends PageParam {

    @Schema(description = "搜索词（批次号 / 生产批号）")
    private String searchWord;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "状态：1 有效，2 已过期，3 已作废")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
