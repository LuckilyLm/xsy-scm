package com.xsy.scm.pricing.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 价格变更审计的唯一写入口：四种价格维度共用一张日志表，类型只决定 price_id 指向哪张价格表。
 */
@Mapper
public interface PriceOperationLogDao {

    void insert(@Param("priceType") String priceType, @Param("priceId") Long priceId,
        @Param("operation") String operationType, @Param("operator") String operator,
        @Param("before") String beforeSnapshot, @Param("after") String afterSnapshot);
}
