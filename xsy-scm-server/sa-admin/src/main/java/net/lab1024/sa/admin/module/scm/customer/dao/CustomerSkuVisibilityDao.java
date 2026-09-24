package net.lab1024.sa.admin.module.scm.customer.dao;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CustomerSkuVisibilityDao extends com.baomidou.mybatisplus.core.mapper.BaseMapper<net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerSkuVisibilityEntity> {
    Long lockCustomer(@Param("id") Long id);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("customerId") Long customerId, @Param("operator") String operator);

    long customerReferences(@Param("id") Long id);

    long typeReferences(@Param("id") Long id);

    /** 反向白名单列表读；范围按客户归属（{@code customer.seller_id}）收窄，传 null 即恒假谓词。 */
    List<net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO> reverse(com.baomidou.mybatisplus.extension.plugins.pagination.Page<?> page, @Param("query") net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerVisibilityQueryForm query, @Param("scope") net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope scope);

    String policy(@Param("customerId") Long customerId);

    List<Long> visibleIds(@Param("customerId") Long customerId, @Param("ids") List<Long> ids);
}
