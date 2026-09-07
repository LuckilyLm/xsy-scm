package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptConfirmationItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PurchaseReceiptConfirmationItemMapper extends BaseMapper<PurchaseReceiptConfirmationItemEntity> {
    List<PurchaseReceiptConfirmationItemEntity> selectByReceiptId(@Param("receiptId") long receiptId);
}
