package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderQueryForm extends PageParam {
    private Long orderId;
    private Long customerId;
    /**
     * 按业务员筛选：只是用户侧的**收窄**条件，与数据范围取交集，不能用来扩大可见行。
     */
    private Long sellerId;
    @Size(max = 150, message = "查询关键词不能超过150个字符")
    private String keyword;
    @Size(max = 30, message = "订单状态不能超过30个字符")
    @ScmEnumValue(enumClass = ScmOrderStatusEnum.class, message = "订单状态无效")
    private String status;
    @Size(max = 30, message = "订单来源不能超过30个字符")
    @ScmEnumValue(enumClass = ScmOrderSourceEnum.class, message = "订单来源无效")
    private String orderSource;
    @Size(max = 40, message = "操作类型不能超过40个字符")
    @ScmEnumValue(enumClass = ScmOrderOperationTypeEnum.class, message = "操作类型无效")
    private String operationType;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
