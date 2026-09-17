package com.xsy.scm.admin.module.business.print.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 打印模板 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class PrintTemplateQueryForm extends PageParam {

    @Schema(description = "搜索词（模板编码 / 名称）")
    private String searchWord;

    @Schema(description = "业务类型：1 采购单，2 发货单，3 分拣小票，4 询价报价单")
    private Integer bizType;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
