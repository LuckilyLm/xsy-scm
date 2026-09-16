package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class SalesOrderDetailVO extends SalesOrderVO {
    private java.util.List<SalesOrderItemVO> items;
    private OrderAddressSnapshotVO address;
}
