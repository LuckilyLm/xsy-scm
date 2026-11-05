package com.xsy.scm.purchase.dao;

import com.xsy.scm.purchase.domain.vo.PurchaseAccountVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseAccountDao {
    Long nextNumber();
    Long accountId(@Param("orderId") Long orderId);
    boolean hasConfirmedReceipt(@Param("orderId") Long orderId);
    int insert(PurchaseAccountVO account);
    int insertItem(PurchaseAccountVO.Item item);
    PurchaseAccountVO byOrder(@Param("orderId") Long orderId);
    List<PurchaseAccountVO.Item> items(@Param("accountId") Long accountId);
}
