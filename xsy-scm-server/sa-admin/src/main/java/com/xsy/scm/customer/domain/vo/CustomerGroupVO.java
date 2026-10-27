package com.xsy.scm.customer.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class CustomerGroupVO {

    private Long groupId;

    private Integer version;

    private String groupCode;

    private String groupName;

    private String status;

    /** 组内客户数；列表直接展示，避免逐行再查一次。 */
    private Long customerCount;

    /** 详情与编辑回显的成员；列表查询不返回。 */
    private List<CustomerGroupMemberVO> members;
}
