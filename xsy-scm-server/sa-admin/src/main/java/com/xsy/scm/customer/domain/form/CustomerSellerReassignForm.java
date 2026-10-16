package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 客户负责人必须通过独立改派接口修改，普通客户编辑不能修改归属。负责人可以留空，表示未分配；未分配客户只有有权分配或查询全部客户的人员可见。 */
@Data
public class CustomerSellerReassignForm {

    @NotNull(message = "客户 ID 不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    /**
     * 新负责人（员工 id）；{@code null} 表示收回为未分配。
     */
    @Positive(message = "业务员 ID 必须大于0")
    private Long sellerId;

    /**
     * 乐观锁版本：改派必须针对自己刚看到的那一行，不能覆盖别人的并发编辑。
     */
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
