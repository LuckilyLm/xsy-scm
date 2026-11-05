package com.xsy.scm.finance.domain.dto;

import lombok.Data;

/**
 * 收付款读取客户当前归属用于权限校验，客户名称在财务记录生成时保存，防止历史账随改名改变。
 */
@Data
public class FinanceCustomerFactDto {

    private Long customerId;

    /**
     * {@code xsy_customer.name}；非空白由数据库约束和列定义共同保证。
     */
    private String customerName;

    /**
     * 客户归属业务员；{@code xsy_customer.seller_id} 允许为空（尚未分配），空值只有全部范围能通过，按拒绝处理。
     */
    private Long sellerId;

    private Long settlementCustomerId;

    private String settlementCustomerName;
}
