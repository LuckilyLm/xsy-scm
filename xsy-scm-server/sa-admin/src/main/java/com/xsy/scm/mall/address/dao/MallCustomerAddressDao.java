package com.xsy.scm.mall.address.dao;

import com.xsy.scm.mall.address.domain.entity.MallCustomerAddressEntity;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCustomerAddressDao {
    List<MallAddressVO> listByCustomer(@Param("customerId") Long customerId);
    MallAddressVO detailById(@Param("customerId") Long customerId, @Param("id") Long id);
    MallCustomerAddressEntity selectByIdForUpdate(@Param("id") Long id);
    int insert(MallCustomerAddressEntity row);
    int update(MallCustomerAddressEntity row);
    int remove(@Param("customerId") Long customerId, @Param("id") Long id, @Param("operator") String operator);
    int clearDefault(@Param("customerId") Long customerId, @Param("operator") String operator);
    int setDefault(@Param("customerId") Long customerId, @Param("id") Long id, @Param("operator") String operator);
    int countByCustomer(@Param("customerId") Long customerId);
    Long firstAddressId(@Param("customerId") Long customerId);
}
