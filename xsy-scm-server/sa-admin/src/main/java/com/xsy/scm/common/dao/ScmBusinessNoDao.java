package com.xsy.scm.common.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 业务编号计数读取。序列名只来自 {@code ScmBusinessNoType} 的白名单取值。
 */
@Mapper
public interface ScmBusinessNoDao {

    @Select("SELECT nextval(#{sequence}::regclass)")
    long nextSequence(@Param("sequence") String sequence);
}
