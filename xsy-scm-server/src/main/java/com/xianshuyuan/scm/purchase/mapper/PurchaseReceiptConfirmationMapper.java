package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptConfirmationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PurchaseReceiptConfirmationMapper extends BaseMapper<PurchaseReceiptConfirmationEntity> {
    String nextNumber();

    List<PurchaseReceiptConfirmationEntity> selectByReceiptId(@Param("receiptId") long receiptId);
}
