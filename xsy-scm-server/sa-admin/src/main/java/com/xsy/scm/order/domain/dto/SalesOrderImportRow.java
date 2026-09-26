package net.lab1024.sa.admin.module.scm.order.domain.dto;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.ExcelIgnore;
import lombok.Data;

@Data
public class SalesOrderImportRow {
    @ExcelIgnore
    private int rowNumber;
    @ExcelProperty("模板版本")
    private String templateVersion;
    @ExcelProperty("导入订单标识")
    private String orderKey;
    @ExcelProperty("客户编码")
    private String customerCode;
    @ExcelProperty("收货人")
    private String receiverName;
    @ExcelProperty("联系电话")
    private String receiverPhone;
    @ExcelProperty("收货地址")
    private String address;
    @ExcelProperty("期望配送时间")
    private String expectDeliveryTime;
    @ExcelProperty("SKU编码")
    private String skuCode;
    @ExcelProperty("下单数量")
    private String orderedQuantity;
    @ExcelProperty("人工单价")
    private String unitPrice;
    @ExcelProperty("改价原因")
    private String overrideReason;
    @ExcelProperty("订单备注")
    private String remark;
}
