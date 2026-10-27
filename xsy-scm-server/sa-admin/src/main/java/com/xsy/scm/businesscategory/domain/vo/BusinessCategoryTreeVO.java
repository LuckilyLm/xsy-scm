package com.xsy.scm.businesscategory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessCategoryTreeVO extends BusinessCategoryVO {

    private List<BusinessCategoryTreeVO> children;
}
