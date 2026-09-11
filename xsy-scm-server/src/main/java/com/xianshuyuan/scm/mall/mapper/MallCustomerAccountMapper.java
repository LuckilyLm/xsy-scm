package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.mall.entity.MallCustomerAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCustomerAccountMapper extends BaseMapper<MallCustomerAccountEntity> {

    MallCustomerAccountEntity selectActiveByUsername(@Param("username") String username);

    MallCustomerAccountEntity selectActiveByWechatOpenid(@Param("openid") String openid);

    int updateLastLoginAt(@Param("id") long id);
}
