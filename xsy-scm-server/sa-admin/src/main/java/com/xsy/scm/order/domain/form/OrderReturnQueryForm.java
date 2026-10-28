package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderReturnQueryForm extends SalesOrderQueryForm {
    /**
     * ORDER / INDEPENDENT；留空表示两种退货一起查。
     */
    private String sourceType;
}
