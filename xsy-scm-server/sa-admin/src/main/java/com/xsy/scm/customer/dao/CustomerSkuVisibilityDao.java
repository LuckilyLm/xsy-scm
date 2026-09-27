package com.xsy.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerSkuVisibilityEntity;
import com.xsy.scm.customer.domain.form.CustomerVisibilityQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerSkuVisibilityDao extends BaseMapper<CustomerSkuVisibilityEntity> {
    Long lockCustomer(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("customerId") Long customerId, @Param("operator") String operator);

    long customerReferences(@Param("id") Long id);

    long typeReferences(@Param("id") Long id);

    /** 反向白名单列表读；范围按客户归属（{@code customer.seller_id}）收窄，传 null 即恒假谓词。 */
    List<CustomerSkuVisibilityReverseVO> reverse(Page<?> page,
                                                 @Param("query") CustomerVisibilityQueryForm query,
                                                 @Param("scope") ScmValueScope scope);

    String policy(@Param("customerId") Long customerId);

    List<Long> visibleIds(@Param("customerId") Long customerId, @Param("ids") List<Long> ids);
}
