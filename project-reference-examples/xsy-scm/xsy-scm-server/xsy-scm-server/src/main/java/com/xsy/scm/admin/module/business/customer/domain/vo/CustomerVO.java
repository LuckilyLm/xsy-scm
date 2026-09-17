package com.xsy.scm.admin.module.business.customer.domain.vo;

import com.xsy.scm.admin.module.business.customer.constant.CustomerStatusEnum;
import com.xsy.scm.admin.module.business.customer.constant.CustomerTypeEnum;
import com.xsy.scm.admin.module.business.customer.constant.SettleModeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerVO {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户编码")
    private String customerNo;

    @Schema(description = "客户名称")
    private String customerName;

    @SchemaEnum(CustomerTypeEnum.class)
    private Integer customerType;

    @Schema(description = "客户分级ID")
    private Long customerLevelId;

    @Schema(description = "上级集团客户ID")
    private Long parentCustomerId;

    @SchemaEnum(SettleModeEnum.class)
    private Integer settleMode;

    @Schema(description = "归属业务员ID")
    private Long sellerId;

    @Schema(description = "绑定供应商ID")
    private Long supplierId;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "余额账户（不含税）")
    private BigDecimal balance;

    @Schema(description = "授信额度（不含税）")
    private BigDecimal creditAmount;

    @SchemaEnum(CustomerStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
