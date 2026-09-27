package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class OrderBatchDeleteForm {
    @Valid
    @NotEmpty(message = "订单列表不能为空")
    @Size(max = 100, message = "一次最多删除100个订单")
    private List<OrderVersionForm> orders;
}
