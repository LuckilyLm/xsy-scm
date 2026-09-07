package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseReceiptItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PurchaseReceiptItemMapper extends BaseMapper<PurchaseReceiptItemEntity> {
    List<PurchaseReceiptItemEntity> selectActiveByReceiptId(@Param("receiptId") long receiptId);
    List<PurchaseReceiptItemEntity> selectActiveByReceiptIdForUpdate(@Param("receiptId") long receiptId);
}
