package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 负责人只能通过改派接口修改，普通编辑不能改归属。可以留空表示未分配；未分配的客户只有能分配或能查全部客户的人看得到。
 */
@Data
public class CustomerSellerReassignForm {

    @NotNull(message = "客户 ID 不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    /** 新负责人（员工 id）；{@code null} 表示收回为未分配。 */
    @Positive(message = "业务员 ID 必须大于0")
    private Long sellerId;

    /** 乐观锁版本：改派必须针对自己刚看到的那一行，不能覆盖别人的并发编辑。 */
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
