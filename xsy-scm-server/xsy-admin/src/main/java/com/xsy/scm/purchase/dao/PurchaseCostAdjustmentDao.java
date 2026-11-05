package com.xsy.scm.purchase.dao;

import java.util.List;
import com.xsy.scm.purchase.domain.vo.PurchaseCostAdjustmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseCostAdjustmentDao {
    PurchaseCostAdjustmentVO accountSource(@Param("accountItemId") Long accountItemId);
    long nextNumber();
    PurchaseCostAdjustmentVO source(@Param("receiptItemId") Long receiptItemId);
    List<PurchaseCostAdjustmentVO> options(@Param("orderId") Long orderId);
    List<PurchaseCostAdjustmentVO> history(@Param("orderId") Long orderId);
    int insert(PurchaseCostAdjustmentVO adjustment);
}
