package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesOrderImportErrorVO {
    private int rowNumber;
    private String orderKey;
    private String column;
    private String code;
    private String message;
}
