package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderEntity;
import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderEntity> {
    PurchaseOrderEntity selectActiveById(@Param("id") long id);

    PurchaseOrderEntity selectActiveByIdForUpdate(@Param("id") long id);

    List<PurchaseOrderEntity> selectActiveList();
}
