package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderImportResultVO {
    private int totalRows;
    private int totalOrders;
    private int confirmedOrders;
    private int pendingOrders;
    private int totalErrors;
    private List<SalesOrderImportErrorVO> errors = new ArrayList<>();
    private List<SalesOrderDetailVO> orders = new ArrayList<>();
}
