package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderReturnDetailVO extends OrderReturnVO {
    private java.util.List<OrderReturnItemVO> items;

}
