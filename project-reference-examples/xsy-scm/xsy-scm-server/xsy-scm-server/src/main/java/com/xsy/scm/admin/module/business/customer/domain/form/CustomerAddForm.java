package com.xsy.scm.admin.module.business.customer.domain.form;

import com.xsy.scm.admin.module.business.customer.constant.CustomerTypeEnum;
import com.xsy.scm.admin.module.business.customer.constant.SettleModeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerAddForm {

    @Schema(description = "客户名称")
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @SchemaEnum(CustomerTypeEnum.class)
    @CheckEnum(message = "客户类型错误", value = CustomerTypeEnum.class, required = true)
    private Integer customerType;

    @Schema(description = "客户分级ID，影响取价")
    private Long customerLevelId;

    @Schema(description = "上级集团客户ID，独立客户为0")
    private Long parentCustomerId;

    @SchemaEnum(SettleModeEnum.class)
    @CheckEnum(message = "结算方式错误", value = SettleModeEnum.class, required = false)
    private Integer settleMode;

    @Schema(description = "归属业务员ID")
    private Long sellerId;

    @Schema(description = "绑定供应商ID")
    private Long supplierId;

    @Schema(description = "联系人")
    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @Schema(description = "联系电话")
    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @Schema(description = "地址，配送使用")
    private String address;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "授信额度（不含税），账期按金额时使用")
    private BigDecimal creditAmount;
}
