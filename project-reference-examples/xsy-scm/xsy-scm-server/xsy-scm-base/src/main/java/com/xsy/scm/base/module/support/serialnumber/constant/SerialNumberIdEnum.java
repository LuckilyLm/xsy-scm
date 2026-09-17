package com.xsy.scm.base.module.support.serialnumber.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.xsy.scm.base.common.enumeration.BaseEnum;

/**
 * 单据序列号 枚举
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-25 21:46:07
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@AllArgsConstructor
@Getter
public enum SerialNumberIdEnum implements BaseEnum {

    ORDER(1, "订单id"),

    CONTRACT(2, "合同id"),

    PRODUCT(3, "商品编码id"),

    CUSTOMER(4, "客户编码id"),

    SALE_ORDER(5, "销售订单id"),

    PURCHASE_ORDER(6, "采购单id"),

    RECEIVE(7, "收货单id"),

    REFUND(8, "退款单id"),

    STOCK_ADJUST(9, "库存调整单id"),

    RECEIVABLE(10, "应收单号id"),

    PAYMENT(11, "收款单号id"),

    SUPPLIER(12, "供应商编码id"),

    SUPPLIER_PRODUCT_APPLY(13, "供应商商品提报单id"),

    SUPPLIER_STATEMENT(14, "供应商对账单id"),

    INQUIRY(15, "询价单id"),

    PRODUCT_CONVERT(16, "商品转换单id"),

    FINANCE_VOUCHER(17, "会计凭证id"),

    TRACE_BATCH(18, "溯源批次id"),

    ;

    private final Integer serialNumberId;

    private final String desc;

    @Override
    public Integer getValue() {
        return serialNumberId;
    }

    @Override
    public String toString() {
        return "SerialNumberIdEnum{" +
                "serialNumberId=" + serialNumberId +
                ", desc='" + desc + '\'' +
                '}';
    }
}
