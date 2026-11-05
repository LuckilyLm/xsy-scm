package com.xsy.scm.customer.domain.form;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建客户分组。分组至少需要一个客户，成员在保存时同步到客户档案。
 */
@Data
public class CustomerGroupAddForm {

    @NotBlank(message = "分组编码不能为空")
    @Size(max = 32, message = "分组编码不能超过32个字符")
    private String groupCode;

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 64, message = "分组名称不能超过64个字符")
    private String groupName;

    @NotNull(message = "分组状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "分组状态无效")
    private String status;

    @NotEmpty(message = "客户分组至少需要一个客户")
    private List<@NotNull(message = "客户 ID 不能为空") @Positive(message = "客户 ID 必须大于0") Long> customerIds;
}
