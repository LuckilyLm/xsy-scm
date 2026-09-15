package net.lab1024.sa.admin.module.scm.customer.dao;

import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface CustomerSkuVisibilityDao extends com.baomidou.mybatisplus.core.mapper.BaseMapper<net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerSkuVisibilityEntity> {
 Long lockCustomer(@Param("id") Long id);
 int softDelete(@Param("id") Long id,@Param("version") Integer version,@Param("customerId") Long customerId,@Param("operator") String operator);
 long customerReferences(@Param("id") Long id);
 long typeReferences(@Param("id") Long id);
 List<net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO> reverse(com.baomidou.mybatisplus.extension.plugins.pagination.Page<?> page,@Param("query") net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerVisibilityQueryForm query);

 String policy(@Param("customerId") Long customerId);
 List<Long> visibleIds(@Param("customerId") Long customerId,@Param("ids") List<Long> ids);
}
