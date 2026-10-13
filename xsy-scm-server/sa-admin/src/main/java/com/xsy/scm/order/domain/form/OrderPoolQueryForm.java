package com.xsy.scm.order.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPoolQueryForm extends PageParam {
    @Size(max = 150, message = "查询关键词不能超过150个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmOrderStatusEnum.class, message = "订单状态无效")
    private String status;
}
