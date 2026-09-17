package com.xsy.scm.admin.module.business.customer.domain.form;

import com.xsy.scm.admin.module.business.customer.constant.CustomerStatusEnum;
import com.xsy.scm.admin.module.business.customer.constant.CustomerTypeEnum;
import com.xsy.scm.admin.module.business.customer.constant.SettleModeEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerQueryForm extends PageParam {

    @Schema(description = "客户名称/编码 模糊搜索")
    @Size(max = 30, message = "搜索词最多30字符")
    private String customerName;

    @SchemaEnum(CustomerTypeEnum.class)
    @CheckEnum(message = "客户类型错误", value = CustomerTypeEnum.class, required = false)
    private Integer customerType;

    @SchemaEnum(SettleModeEnum.class)
    @CheckEnum(message = "结算方式错误", value = SettleModeEnum.class, required = false)
    private Integer settleMode;

    @SchemaEnum(CustomerStatusEnum.class)
    @CheckEnum(message = "客户状态错误", value = CustomerStatusEnum.class, required = false)
    private Integer status;

    @Schema(description = "上级集团客户ID（查下属单位时使用）")
    private Long parentCustomerId;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
