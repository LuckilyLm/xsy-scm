package com.xsy.scm.order.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRefundQueryForm extends PageParam {
    private Long orderId;
    private Long customerId;
    @Size(max = 150, message = "查询关键词不能超过150个字符")
    private String keyword;
    @ScmEnumValue(enumClass = ScmOrderRefundStatusEnum.class, message = "退款状态无效")
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
