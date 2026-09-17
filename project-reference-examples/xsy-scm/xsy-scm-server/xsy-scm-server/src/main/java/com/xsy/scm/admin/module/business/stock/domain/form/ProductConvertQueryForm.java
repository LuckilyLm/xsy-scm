package com.xsy.scm.admin.module.business.stock.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品转换单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertQueryForm extends PageParam {

    @Schema(description = "搜索词（转换单号）")
    private String searchWord;

    @Schema(description = "转换类型：1 整件拆零，2 组合拆分")
    private Integer convertType;

    @Schema(description = "状态：1 待审核，2 已完成，3 已驳回")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
