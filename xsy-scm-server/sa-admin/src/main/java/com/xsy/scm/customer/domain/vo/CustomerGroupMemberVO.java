package com.xsy.scm.customer.domain.vo;

import lombok.Data;

/** 客户分组成员的最小回显：只用于编辑与详情，不参与列表分页。 */
@Data
public class CustomerGroupMemberVO {

    private Long customerId;

    private String customerCode;

    private String customerName;
}
