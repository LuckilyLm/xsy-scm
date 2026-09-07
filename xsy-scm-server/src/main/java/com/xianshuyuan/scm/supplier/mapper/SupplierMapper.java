package com.xianshuyuan.scm.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupplierMapper extends BaseMapper<SupplierEntity> {
    SupplierEntity selectActiveByIdForUpdate(@Param("id") long id);
}
