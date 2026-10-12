package com.xsy.scm.mall.account.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCustomerAccountDao extends BaseMapper<MallCustomerAccountEntity> {
    MallCustomerAccountEntity selectByPhone(@Param("phone") String phone);
    MallCustomerAccountEntity selectByIdForUpdate(@Param("accountId") Long accountId);
    List<MallAccountVO> listByCustomerId(@Param("customerId") Long customerId);
    MallAccountVO detailById(@Param("accountId") Long accountId);
    List<MallAccountVO> listNamesByIds(@Param("accountIds") List<Long> accountIds);
    int updateStatus(@Param("accountId") Long accountId, @Param("status") String status,
            @Param("operator") String operator);
    int resetPassword(@Param("accountId") Long accountId, @Param("passwordHash") String passwordHash,
            @Param("operator") String operator);
    int changePassword(@Param("accountId") Long accountId, @Param("passwordHash") String passwordHash);
    int recordLogin(@Param("accountId") Long accountId, @Param("ip") String ip);
}
