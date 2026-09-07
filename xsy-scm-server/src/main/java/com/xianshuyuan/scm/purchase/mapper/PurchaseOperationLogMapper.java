package com.xianshuyuan.scm.purchase.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.xianshuyuan.scm.purchase.entity.PurchaseOperationLogEntity; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface PurchaseOperationLogMapper extends BaseMapper<PurchaseOperationLogEntity> { List<PurchaseOperationLogEntity> selectByOrderId(@Param("orderId") long orderId); }
