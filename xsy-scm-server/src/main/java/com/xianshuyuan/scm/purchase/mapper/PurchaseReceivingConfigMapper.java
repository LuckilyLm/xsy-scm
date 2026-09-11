package com.xianshuyuan.scm.purchase.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseReceivingConfigMapper {
    String selectEnabledValue(@Param("key") String key);
}
