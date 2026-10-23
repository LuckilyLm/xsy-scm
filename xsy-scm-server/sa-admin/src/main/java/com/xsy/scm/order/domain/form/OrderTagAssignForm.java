package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.Data;

@Data
public class OrderTagAssignForm {
    @NotNull(message = "标签不能为空")
    @Positive(message = "标签编号必须为正数")
    private Long tagId;

    @NotEmpty(message = "请选择订单")
    @Size(max = 20, message = "每次最多处理20张订单")
    private List<@NotNull @Positive Long> orderIds;

    @NotNull(message = "请选择打标操作")
    private Action action;

    public enum Action {
        ADD,
        REMOVE
    }
}
