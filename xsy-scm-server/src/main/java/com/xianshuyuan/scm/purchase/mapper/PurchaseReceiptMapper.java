package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PurchaseReceiptMapper extends BaseMapper<PurchaseReceiptEntity> {
    PurchaseReceiptEntity selectActiveById(@Param("id") long id);

    PurchaseReceiptEntity selectActiveByIdForUpdate(@Param("id") long id);

    PurchaseReceiptEntity selectActiveByOrderIdForUpdate(@Param("orderId") long orderId);

    List<PurchaseReceiptEntity> selectActiveList();
}
