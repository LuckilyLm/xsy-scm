package com.xsy.scm.admin.module.business.trace.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 检测报告 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class TraceInspectQueryForm extends PageParam {

    @Schema(description = "搜索词（报告名称）")
    private String searchWord;

    @Schema(description = "关联溯源批次ID")
    private Long batchId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "匹配模式：1 绑定采购单，2 绑定生产批号")
    private Integer matchMode;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
