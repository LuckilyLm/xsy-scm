package com.xsy.scm.admin.module.business.finance.domain.form;

import com.xsy.scm.admin.module.business.finance.constant.ReceivableStatusEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应收单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ReceivableQueryForm extends PageParam {

    @Schema(description = "应收单号 模糊搜索")
    private String receivableNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @SchemaEnum(ReceivableStatusEnum.class)
    @CheckEnum(message = "应收状态错误", value = ReceivableStatusEnum.class, required = false)
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
