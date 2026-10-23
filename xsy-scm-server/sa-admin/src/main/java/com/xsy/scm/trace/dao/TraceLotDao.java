package com.xsy.scm.trace.dao;

import com.xsy.scm.trace.domain.vo.TraceLotVO;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 批次档案与收货行批次事实的读写。批次档案一经创建不改写，批次流水另表且只追加。SQL 见 mapper/scm/trace/TraceLotDao.xml。
 */
@Mapper
public interface TraceLotDao {

    int createLot(TraceLotVO.Lot lot);

    int linkReceiptItemLot(@Param("receiptItemId") Long receiptItemId, @Param("lotId") Long lotId,
        @Param("quantity") BigDecimal quantity, @Param("createdBy") String createdBy);

    List<TraceLotVO.ReceiptItemLot> listByReceiptItemIds(@Param("ids") List<Long> ids);

    TraceLotVO.Lot findById(@Param("lotId") Long lotId);
}
