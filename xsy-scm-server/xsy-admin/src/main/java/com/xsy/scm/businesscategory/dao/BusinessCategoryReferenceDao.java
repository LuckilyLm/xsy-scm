package com.xsy.scm.businesscategory.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 分类删除前的跨域只读引用检查。只统计客户与供应商，不修改两张业务表。
 */
@Mapper
public interface BusinessCategoryReferenceDao {

    long countCustomers(@Param("categoryId") Long categoryId);

    long countSuppliers(@Param("categoryId") Long categoryId);
}
