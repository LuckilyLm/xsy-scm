package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseOperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 采购操作日志只能新增和查询，不能更新或删除。日志关联的采购单、收货单由调用方明确给出，数据库校验归属。 */
@Mapper
public interface PurchaseOperationLogDao extends BaseMapper<PurchaseOperationLogEntity> {

    /**
     * 追加一条操作日志。
     */
    int append(@Param("row") PurchaseOperationLogEntity row);

    /**
     * 某采购单的全部日志，按时间倒序（{@code GET /scm/purchase/log/{orderId}}）。
     */
    List<PurchaseOperationLogVO> listByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 某收货单的全部日志，按时间倒序。
     */
    List<PurchaseOperationLogVO> listByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 某需求生成批次的相关日志（按 {@code after_data.demandIds} 反查；验收与排查用）。
     */
    List<PurchaseOperationLogVO> listDemandGenerate();

    /**
     * 按操作类型统计条数（验收断言 12 种类型全部落库）。
     */
    int countByType(@Param("operationType") String operationType);
}
