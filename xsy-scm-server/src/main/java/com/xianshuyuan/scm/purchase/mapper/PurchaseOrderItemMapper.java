package com.xianshuyuan.scm.purchase.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItemEntity> {
 List<PurchaseOrderItemEntity> selectActiveByOrderId(@Param("orderId") long orderId);
 List<PurchaseOrderItemEntity> selectActiveByOrderIdForUpdate(@Param("orderId") long orderId);
 PurchaseOrderItemEntity selectActiveById(@Param("id") long id);
 int softDeleteOwnedWithVersion(@Param("orderId") long orderId,@Param("id") long id,@Param("version") int version);
 int softDeleteByOrderId(@Param("orderId") long orderId);
}
