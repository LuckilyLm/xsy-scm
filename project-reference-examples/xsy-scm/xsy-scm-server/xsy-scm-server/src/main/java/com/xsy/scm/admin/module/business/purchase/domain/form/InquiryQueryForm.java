package com.xsy.scm.admin.module.business.purchase.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 询价单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryQueryForm extends PageParam {

    @Schema(description = "搜索词（询价单号/名称）")
    private String searchWord;

    @Schema(description = "状态：1 待报价，2 报价中，3 已完成，4 已取消")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
