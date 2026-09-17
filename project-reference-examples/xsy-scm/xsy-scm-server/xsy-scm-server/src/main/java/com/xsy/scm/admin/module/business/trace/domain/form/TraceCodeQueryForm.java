package com.xsy.scm.admin.module.business.trace.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 溯源码 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class TraceCodeQueryForm extends PageParam {

    @Schema(description = "搜索词（溯源码）")
    private String searchWord;

    @Schema(description = "关联溯源批次ID")
    private Long batchId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "状态：1 未启用，2 已启用，3 已作废")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
