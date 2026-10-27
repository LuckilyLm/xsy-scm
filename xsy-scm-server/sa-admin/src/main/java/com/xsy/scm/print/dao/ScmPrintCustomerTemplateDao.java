package com.xsy.scm.print.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.print.domain.entity.ScmPrintCustomerTemplateEntity;
import com.xsy.scm.print.domain.vo.ScmPrintCustomerTemplateVO;

/**
 * 客户专属模板绑定。一个客户对一种单据类型只保留一条有效记录，换模板走更新而不是再插一行。
 */
@Mapper
public interface ScmPrintCustomerTemplateDao {

    /** 某客户在某单据类型下的有效绑定。 */
    ScmPrintCustomerTemplateEntity selectBinding(@Param("customerId") Long customerId,
        @Param("documentType") String documentType);

    /** 绑定列表；单据类型、客户为空表示不限，客户归属范围必须显式下传。 */
    List<ScmPrintCustomerTemplateVO> queryBindings(@Param("documentType") String documentType,
        @Param("customerId") Long customerId, @Param("scope") ScmValueScope scope);

    /** 客户是否存在且未删除，避免绑定到不存在的客户。 */
    boolean customerExists(@Param("customerId") Long customerId);

    int insertBinding(@Param("row") ScmPrintCustomerTemplateEntity row);

    /** 换绑模板；同一行乐观版本递增。 */
    int updateBinding(@Param("id") Long id, @Param("templateId") Long templateId, @Param("operator") String operator);

    /** 解除绑定（软删）；没有有效绑定时返回 0，调用方按幂等处理。 */
    int softDelete(@Param("customerId") Long customerId, @Param("documentType") String documentType,
        @Param("operator") String operator);
}
