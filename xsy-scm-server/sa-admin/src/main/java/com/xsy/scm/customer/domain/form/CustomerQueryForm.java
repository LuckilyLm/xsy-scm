package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 客户列表查询条件。
 *
 * <p>分页边界在这里收紧到 1–100（legacy 与 SmartAdmin 基线只保证 1–500）。边界在 DTO 上声明，
 * 避免每个调用方各写一遍。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQueryForm extends PageParam {

    /**
     * 关键字：客户编码 / 客户名称 / 联系人 / 联系电话。
     */
    @Size(max = 150, message = "关键字不能超过150个字符")
    private String keyword;

    @Positive(message = "客户类型必须大于0")
    private Long customerTypeId;

    @ScmEnumValue(enumClass = ScmCustomerStatusEnum.class, message = "客户状态无效")
    private String status;

    @ScmEnumValue(enumClass = ScmSettleModeEnum.class, message = "结算模式无效")
    private String settleMode;

    /**
     * 按上级集团客户反查下属单位。
     */
    @Positive(message = "上级客户 ID 必须大于0")
    private Long parentCustomerId;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须在1至100之间")
    @Max(value = 100, message = "每页条数必须在1至100之间")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
