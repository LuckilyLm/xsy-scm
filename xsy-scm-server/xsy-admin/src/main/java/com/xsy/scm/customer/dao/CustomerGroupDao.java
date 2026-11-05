package com.xsy.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.customer.domain.entity.CustomerGroupEntity;
import com.xsy.scm.customer.domain.form.CustomerGroupQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerGroupVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerGroupDao extends BaseMapper<CustomerGroupEntity> {

    List<CustomerGroupVO> queryPage(Page<?> page, @Param("query") CustomerGroupQueryForm query);

    CustomerGroupVO detail(@Param("groupId") Long groupId);

    /** 加锁读取分组行，成员替换与版本校验在同一事务内完成。 */
    Long lock(@Param("groupId") Long groupId);

    int softDelete(@Param("groupId") Long groupId, @Param("version") Integer version,
        @Param("operator") String operator);
}
