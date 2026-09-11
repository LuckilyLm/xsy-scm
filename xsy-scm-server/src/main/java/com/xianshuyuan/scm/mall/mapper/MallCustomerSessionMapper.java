package com.xianshuyuan.scm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.mall.entity.MallCustomerSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallCustomerSessionMapper extends BaseMapper<MallCustomerSessionEntity> {

    MallCustomerSessionEntity selectActiveByTokenHash(@Param("tokenHash") String tokenHash);

    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    int revokeAllByAccountId(@Param("accountId") long accountId);

    int touch(@Param("id") long id);
}
